package com.cakeshop.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.claude.ClaudeVisionClient
import com.cakeshop.manager.claude.ScanResult
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.entity.Product
import com.cakeshop.manager.data.entity.unitCost
import com.cakeshop.manager.ui.money
import kotlinx.coroutines.launch

@Composable
fun StockInScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val purchases by vm.purchases.collectAsState()
    val vendors by vm.vendors.collectAsState()
    val products by vm.products.collectAsState()
    var range by remember { mutableStateOf("day") }
    var showAdd by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val filtered = remember(purchases, date, range) {
        purchases.filter { if (range == "day") it.purchase.date == date else it.purchase.date <= date && it.purchase.date >= addDaysStr(date, -29) }
            .sortedByDescending { it.purchase.ts }
    }
    val total = filtered.sumOf { it.purchase.total }

    Scaffold(floatingActionButton = {
        Column(horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(onClick = { showScan = true }) { Text("\uD83D\uDCF7") }
            Spacer(Modifier.height(8.dp))
            ExtendedFloatingActionButton(onClick = { showAdd = true }, text = { Text("Add stock") }, icon = {})
        }
    }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = range == "day", onClick = { range = "day" }, label = { Text("This day") })
                FilterChip(selected = range == "month", onClick = { range = "month" }, label = { Text("Last 30 days") })
            }
            Spacer(Modifier.height(6.dp))
            Text("${filtered.size} entries, ${money(total)} at cost", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Text("No stock entries yet. Record vendor deliveries or items baked in-house.", modifier = Modifier.padding(top = 32.dp))
            } else {
                LazyColumn {
                    items(filtered, key = { it.purchase.id }) { pu ->
                        val vendorName = vendors.find { it.id == pu.purchase.vendorId }?.name ?: "Unknown vendor"
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (pu.purchase.kind == "vendor") vendorName else "Baked in-house", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        formatDateShort(pu.purchase.date) + (if (pu.purchase.invoiceNo.isNotBlank()) ", ${pu.purchase.invoiceNo}" else ""),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        pu.lines.joinToString(", ") { l -> (products.find { it.id == l.productId }?.name ?: "Deleted item") + " x" + com.cakeshop.manager.ui.qty(l.qty) },
                                        style = MaterialTheme.typography.bodySmall, maxLines = 2
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(money(pu.purchase.total), fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { confirmDelete = pu.purchase.id }) { Text("Delete") }
                                }
                            }
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAdd) StockInDialog(vm, date, vendors, products, prefill = null, onDismiss = { showAdd = false })
    if (showScan) ScanStockDialog(vm, date, vendors, products, onDismiss = { showScan = false })

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this stock entry?") },
            text = { Text("Stock and vendor dues will be recalculated.") },
            confirmButton = { TextButton(onClick = { vm.deletePurchase(confirmDelete!!); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

private data class EditableLine(var productId: String?, var qty: String, var cost: String, var label: String = "")

@Composable
private fun StockInDialog(
    vm: com.cakeshop.manager.data.AppViewModel,
    date: String,
    vendors: List<com.cakeshop.manager.data.entity.Vendor>,
    products: List<Product>,
    prefill: List<EditableLine>?,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf("vendor") }
    var vendorId by remember { mutableStateOf<String?>(vendors.firstOrNull()?.id) }
    var invoiceNo by remember { mutableStateOf("") }
    var pickedDate by remember { mutableStateOf(date) }
    var dueDate by remember { mutableStateOf(date) }
    val lines = remember { mutableStateListOf<EditableLine>().apply { addAll(prefill ?: listOf(EditableLine(null, "", ""))) } }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stock") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "vendor", onClick = { kind = "vendor" }, label = { Text("Vendor") })
                    FilterChip(selected = kind == "inhouse", onClick = { kind = "inhouse" }, label = { Text("In-house") })
                }
                if (kind == "vendor") {
                    Spacer(Modifier.height(8.dp))
                    VendorPicker(vendors, vendorId) { vendorId = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(invoiceNo, { invoiceNo = it }, label = { Text("Invoice number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text("Items received", fontWeight = FontWeight.SemiBold)
                lines.forEachIndexed { idx, line ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        if (line.label.isNotBlank()) Text("Read as: \u201C${line.label}\u201D", style = MaterialTheme.typography.labelSmall)
                        ProductPicker(products, line.productId) { line.productId = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedTextField(line.qty, { line.qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(line.cost, { line.cost = it }, label = { Text("Unit cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                            TextButton(onClick = { if (lines.size > 1) lines.removeAt(idx) }) { Text("\u00D7") }
                        }
                    }
                }
                TextButton(onClick = { lines.add(EditableLine(null, "", "")) }) { Text("Add another item") }
                val totalPreview = lines.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.cost.toDoubleOrNull() ?: 0.0) }
                Text("Total: ${money(totalPreview)}", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = lines.filter { it.productId != null && (it.qty.toDoubleOrNull() ?: 0.0) > 0 }
                    .map { Triple(it.productId!!, it.qty.toDouble(), it.cost.toDoubleOrNull() ?: 0.0) }
                if (cleaned.isNotEmpty()) {
                    scope.launch {
                        vm.addPurchase(kind, if (kind == "vendor") vendorId else null, pickedDate, dueDate, invoiceNo, cleaned)
                        onDismiss()
                    }
                }
            }) { Text("Save stock entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ScanStockDialog(
    vm: com.cakeshop.manager.data.AppViewModel,
    date: String,
    vendors: List<com.cakeshop.manager.data.entity.Vendor>,
    products: List<Product>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageMediaType by remember { mutableStateOf("image/jpeg") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<EditableLine>>(emptyList()) }
    var reviewReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (reviewReady) {
        StockInDialog(vm, date, vendors, products, prefill = lines, onDismiss = onDismiss)
        return
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { imageBytes = it.readBytes() }
            imageMediaType = context.contentResolver.getType(uri) ?: "image/jpeg"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan a photo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Upload a photo of a delivery note, production sheet, or stock count. Claude reads the items and quantities and matches them to your catalog for you to check before saving.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Text(if (imageBytes == null) "Choose photo" else "Photo selected (${(imageBytes!!.size / 1024)} KB)")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !loading,
                    onClick = {
                        val bytes = imageBytes
                        if (bytes == null) { status = "Choose a photo first"; return@Button }
                        loading = true; status = "Reading the photo\u2026 this can take up to a minute."
                        scope.launch {
                            val catalog = products.joinToString("\n") { (if (it.code.isNotBlank()) it.code + " " else "") + it.name }
                            when (val result = ClaudeVisionClient.readStockPhoto(settings.anthropicApiKey, bytes, imageMediaType, catalog)) {
                                is ScanResult.Success -> {
                                    if (result.lines.isEmpty()) {
                                        status = "No items were found in that photo. Try a clearer photo."
                                    } else {
                                        val byCode = products.filter { it.code.isNotBlank() }.associateBy { it.code }
                                        lines = result.lines.map { sl ->
                                            val matched = sl.code?.let { byCode[it] }
                                                ?: products.find { it.name.equals(sl.text, true) }
                                                ?: products.find { sl.text.contains(it.name, true) || it.name.contains(sl.text, true) }
                                            EditableLine(
                                                productId = matched?.id,
                                                qty = sl.qty?.toString() ?: "",
                                                cost = matched?.unitCost()?.toString() ?: "",
                                                label = sl.text
                                            )
                                        }
                                        reviewReady = true
                                    }
                                }
                                is ScanResult.Failure -> status = result.message
                            }
                            loading = false
                        }
                    }
                ) { Text(if (loading) "Reading\u2026" else "Read photo") }
                if (status != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(status!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun VendorPicker(vendors: List<com.cakeshop.manager.data.entity.Vendor>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = vendors.find { it.id == selected }?.name ?: "Choose vendor"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vendors.forEach { v ->
                DropdownMenuItem(text = { Text(v.name) }, onClick = { onSelect(v.id); expanded = false })
            }
        }
    }
}

@Composable
private fun ProductPicker(products: List<Product>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val label = products.find { it.id == selected }?.let { (if (it.code.isNotBlank()) it.code + " " else "") + it.name } ?: "Choose item"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search") },
                modifier = Modifier.padding(8.dp), singleLine = true
            )
            products.filter { query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) }
                .sortedBy { it.name }.take(60).forEach { p ->
                    DropdownMenuItem(
                        text = { Text((if (p.code.isNotBlank()) p.code + " " else "") + "${p.name} (${p.unit})") },
                        onClick = { onSelect(p.id); expanded = false }
                    )
                }
        }
    }
}
