package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.entity.Product
import com.cakeshop.manager.data.entity.isSellable
import com.cakeshop.manager.ui.*
import kotlinx.coroutines.launch

private val PAY_METHODS = listOf("Cash", "UPI", "Card")

@Composable
fun PosScreen(vm: com.cakeshop.manager.data.AppViewModel) {
    val products by vm.products.collectAsState()
    val bills by vm.bills.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val returns by vm.returns.collectAsState()
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()

    val today = todayStr()
    val ledger = remember(products, bills, purchases, returns) { buildLedger(today, products, purchases, bills, returns) }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val cart = remember { mutableStateMapOf<String, Double>() }
    var customer by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pay by remember { mutableStateOf("Cash") }
    var discount by remember { mutableStateOf("") }
    var discountIsPct by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<com.cakeshop.manager.data.entity.Bill?>(null) }

    val inStock = remember(products, ledger) { products.filter { it.isSellable() && (ledger[it.id]?.close ?: 0.0) > 0 } }
    val categories = remember(inStock) { listOf("All") + inStock.map { it.category }.distinct().sorted() }
    val gridItems = remember(inStock, query, category) {
        inStock.filter {
            (category == "All" || it.category == category) &&
                (query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true))
        }.sortedBy { it.name }
    }

    val cartLines = cart.entries.mapNotNull { (id, q) -> products.find { it.id == id }?.let { it to q } }
    val sub = cartLines.sumOf { (p, q) -> p.price * q }
    val discVal = (discount.toDoubleOrNull() ?: 0.0).let { d -> if (discountIsPct) sub * minOf(d, 100.0) / 100.0 else minOf(d, sub) }.coerceAtLeast(0.0)
    val taxable = sub - discVal
    val tax = taxable * settings.taxPct / 100.0
    val rawTotal = taxable + tax
    val total = Math.round(rawTotal).toDouble()

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(12.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search cakes, pastries, breads") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            Spacer(Modifier.height(8.dp))
            if (gridItems.isEmpty()) {
                Text("Nothing in stock right now. Add stock in the Stock in tab.", modifier = Modifier.padding(16.dp))
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(gridItems, key = { it.id }) { p ->
                        val st = ledger[p.id]?.close ?: 0.0
                        val inCart = cart[p.id] ?: 0.0
                        ElevatedCard(
                            onClick = {
                                val step = if (p.unit == "kg" || p.unit == "litre") 0.5 else 1.0
                                val next = inCart + step
                                if (next <= st + 0.0001) cart[p.id] = next
                            }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                if (p.code.isNotBlank()) Text(p.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(p.name, fontWeight = FontWeight.Medium, minLines = 2)
                                Text(money(p.price), color = Raspberry, fontWeight = FontWeight.Bold)
                                Text("${qty(st)} ${p.unit} left", style = MaterialTheme.typography.bodySmall)
                                if (inCart > 0) Text("In cart: ${qty(inCart)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(Modifier.fillMaxHeight())

        Column(Modifier.width(340.dp).padding(12.dp)) {
            Text("Current bill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 240.dp)) {
                items(cartLines, key = { it.first.id }) { (p, q) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${money(p.price)} each", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButtonText("-") {
                            val step = if (p.unit == "kg" || p.unit == "litre") 0.5 else 1.0
                            val next = q - step
                            if (next <= 0) cart.remove(p.id) else cart[p.id] = next
                        }
                        Text(qty(q), modifier = Modifier.padding(horizontal = 6.dp))
                        IconButtonText("+") {
                            val st = ledger[p.id]?.close ?: 0.0
                            val step = if (p.unit == "kg" || p.unit == "litre") 0.5 else 1.0
                            val next = q + step
                            if (next <= st + 0.0001) cart[p.id] = next
                        }
                        Text(money(p.price * q), modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (cartLines.isEmpty()) Text("Tap an item to start a bill.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(customer, { customer = it }, label = { Text("Customer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedTextField(
                    discount, { discount = it }, label = { Text("Discount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = !discountIsPct, onClick = { discountIsPct = false }, label = { Text("\u20B9") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = discountIsPct, onClick = { discountIsPct = true }, label = { Text("%") })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PAY_METHODS.forEach { m -> FilterChip(selected = pay == m, onClick = { pay = m }, label = { Text(m) }) }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth()) {
                TotalRow("Subtotal", money(sub))
                if (discVal > 0) TotalRow("Discount", "-" + money(discVal))
                TotalRow("Tax (${qty(settings.taxPct)}%)", money(tax))
                if (kotlin.math.abs(total - rawTotal) > 0.004) TotalRow("Round off", money(total - rawTotal))
                TotalRow("Total", money(total), bold = true)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = cartLines.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val lines = cartLines.map { (p, q) -> com.cakeshop.manager.data.AppViewModel.CartLine(p, q) }
                        val bill = vm.createBill(lines, customer, phone, pay, discount.toDoubleOrNull() ?: 0.0, discountIsPct, settings.taxPct)
                        cart.clear(); customer = ""; phone = ""; discount = ""
                        receipt = bill
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (cartLines.isEmpty()) "Create bill" else "Create bill for ${money(total)}") }
        }
    }

    if (receipt != null) {
        ReceiptDialog(vm, receipt!!, settings.shopName, settings.reviewUrl, settings.countryCode) { receipt = null }
    }
}

@Composable
private fun IconButtonText(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
        Text(label)
    }
}

@Composable
private fun TotalRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = if (bold) 18.sp else 14.sp)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = if (bold) 18.sp else 14.sp)
    }
}

@Composable
private fun ReceiptDialog(
    vm: com.cakeshop.manager.data.AppViewModel,
    bill: com.cakeshop.manager.data.entity.Bill,
    shopName: String, reviewUrl: String, countryCode: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bills by vm.bills.collectAsState()
    val lines = bills.find { it.bill.id == bill.id }?.lines ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bill ${bill.no}") },
        text = {
            Column {
                Text("$shopName \u2014 ${formatDateShort(bill.date)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                lines.forEach { l -> TotalRow("${l.name} x${qty(l.qty)}", money(l.qty * l.price)) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                TotalRow("Subtotal", money(bill.subtotal))
                if (bill.discount > 0) TotalRow("Discount", "-" + money(bill.discount))
                TotalRow("Tax", money(bill.tax))
                TotalRow("Total", money(bill.total), bold = true)
                Spacer(Modifier.height(8.dp))
                Text("Paid by ${bill.pay}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val digits = bill.phone.filter { it.isDigit() }
                val number = if (digits.length == 10) countryCode + digits else digits
                val msg = "Hi${if (bill.customer.isNotBlank()) " " + bill.customer else ""}, thank you for shopping at $shopName!\n\n" +
                    "Bill ${bill.no}, ${formatDateShort(bill.date)}\nTotal ${money(bill.total)}, paid by ${bill.pay}.\n\n" +
                    "Enjoyed your order? A Google review helps us a lot: $reviewUrl"
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, msg)
                        if (number.isNotBlank()) setPackage("com.whatsapp")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallback = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, msg)
                    }
                    context.startActivity(android.content.Intent.createChooser(fallback, "Send bill"))
                }
            }) { Text("Send to customer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
