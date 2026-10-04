package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.entity.Product
import com.cakeshop.manager.data.entity.unitCost
import com.cakeshop.manager.ui.money
import com.cakeshop.manager.ui.qty

private val RETURN_TYPES = listOf("customer" to "Customer returned it", "waste" to "Wastage / expired", "kitchen" to "Used in the kitchen")
private val REASONS = listOf("Stale or expired", "Quality complaint", "Wrong order", "Damaged or dropped", "Other")

@Composable
fun ReturnsScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val returns by vm.returns.collectAsState()
    val products by vm.products.collectAsState()
    var range by remember { mutableStateOf("day") }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val filtered = remember(returns, date, range) {
        returns.filter { if (range == "day") it.date == date else it.date <= date && it.date >= addDaysStr(date, -29) }
            .sortedByDescending { it.ts }
    }
    val refunds = filtered.filter { it.type == "customer" }.sumOf { it.refund }
    val loss = filtered.filter { it.type == "waste" }.sumOf { it.loss }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add entry") }
    }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = range == "day", onClick = { range = "day" }, label = { Text("This day") })
                FilterChip(selected = range == "month", onClick = { range = "month" }, label = { Text("Last 30 days") })
            }
            Spacer(Modifier.height(6.dp))
            Text("Refunds ${money(refunds)}, wastage at cost ${money(loss)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Text("No returns or wastage recorded.", modifier = Modifier.padding(top = 32.dp))
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { r ->
                        val p = products.find { it.id == r.productId }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(p?.name ?: "Deleted item", fontWeight = FontWeight.Medium)
                                Text(
                                    "${com.cakeshop.manager.data.formatDateShort(r.date)}, ${RETURN_TYPES.find { it.first == r.type }?.second}" +
                                        (if (r.reason.isNotBlank()) ", ${r.reason}" else ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("${qty(r.qty)} ${p?.unit ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (r.type == "customer") money(r.refund) else if (r.type == "waste") money(r.loss) else "")
                                TextButton(onClick = { confirmDelete = r.id }) { Text("Delete") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) ReturnDialog(vm, products, onDismiss = { showAdd = false })
    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("Stock will be recalculated.") },
            confirmButton = { TextButton(onClick = { vm.deleteReturn(confirmDelete!!); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReturnDialog(vm: com.cakeshop.manager.data.AppViewModel, products: List<Product>, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("customer") }
    var productId by remember { mutableStateOf<String?>(null) }
    var qtyStr by remember { mutableStateOf("") }
    var refund by remember { mutableStateOf("") }
    var restock by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf(REASONS[0]) }
    var billNo by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val product = products.find { it.id == productId }
    val qDouble = qtyStr.toDoubleOrNull() ?: 0.0
    val lossPreview = if (type == "waste" && product != null) qDouble * product.unitCost() else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add return or wastage") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RETURN_TYPES.forEach { (k, label) -> FilterChip(selected = type == k, onClick = { type = k }, label = { Text(label.substringBefore(" ")) }) }
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(product?.let { (if (it.code.isNotBlank()) it.code + " " else "") + it.name } ?: "Choose item")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        products.sortedBy { it.name }.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { productId = p.id; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(qtyStr, { qtyStr = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (type == "customer") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(refund, { refund = it }, label = { Text("Refund (\u20B9)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(billNo, { billNo = it }, label = { Text("Bill number (optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Checkbox(checked = restock, onCheckedChange = { restock = it })
                        Text("Put the item back in stock")
                    }
                }
                if (type != "kitchen") {
                    Spacer(Modifier.height(8.dp))
                    var reasonExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { reasonExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(reason) }
                        DropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                            REASONS.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { reason = r; reasonExpanded = false }) }
                        }
                    }
                }
                if (type == "waste" && product != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Loss at cost: ${money(lossPreview)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (productId != null && qDouble > 0) {
                    vm.addReturn(
                        type, todayStr(), productId!!, qDouble,
                        refund.toDoubleOrNull() ?: 0.0, restock, if (type != "kitchen") reason else "", billNo, lossPreview
                    )
                    onDismiss()
                }
            }) { Text("Save entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
