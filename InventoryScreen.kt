package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.entity.Product
import com.cakeshop.manager.data.entity.isSellable
import com.cakeshop.manager.ui.*
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val products by vm.products.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val bills by vm.bills.collectAsState()
    val returns by vm.returns.collectAsState()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val ledger = remember(date, products, purchases, bills, returns) { buildLedger(date, products, purchases, bills, returns) }

    val categories = remember(products) { listOf("All") + products.map { it.category }.distinct().sorted() }
    val filtered = remember(products, query, category) {
        products.filter {
            (category == "All" || it.category == category) &&
                (query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true))
        }.sortedWith(compareBy({ it.category }, { it.name }))
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
        }
    ) { inner ->
        Column(Modifier.padding(inner).padding(12.dp)) {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search items or code") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(categories, category) { category = it }
            Spacer(Modifier.height(8.dp))

            if (products.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No items yet")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { vm.importPriceList() } }) { Text("Import official price list") }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.id }) { p ->
                        val row = ledger[p.id] ?: EMPTY_ROW
                        val low = p.reorderAt > 0 && row.close <= p.reorderAt
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${if (p.code.isNotBlank()) p.code + "  " else ""}${p.name}", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                Text("${p.category} \u00B7 ${p.shelfLifeRaw.ifBlank { "no shelf life set" }}", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (p.isSellable()) Text(money(p.price), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(
                                    "${qty(row.close)} ${p.unit}",
                                    color = if (row.close <= 0) MaterialTheme.colorScheme.error else if (low) WarnInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { editing = p }) { Text("Edit") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) ProductDialog(vm, null, onDismiss = { showAdd = false })
    if (editing != null) ProductDialog(vm, editing, onDismiss = { editing = null })
}

@Composable
private fun LazyRow(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(categories) { c ->
            FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c) })
        }
    }
}

@Composable
private fun ProductDialog(vm: com.cakeshop.manager.data.AppViewModel, existing: Product?, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Cakes") }
    var unit by remember { mutableStateOf(existing?.unit ?: "pc") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var shelfLife by remember { mutableStateOf(existing?.shelfLifeRaw ?: "") }
    var reorder by remember { mutableStateOf(existing?.reorderAt?.toString() ?: "") }
    var opening by remember { mutableStateOf(existing?.opening?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit item" else "Add item") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = it }, label = { Text("Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.weight(2f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(price, { price = it }, label = { Text("Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(shelfLife, { shelfLife = it }, label = { Text("Shelf life") }, placeholder = { Text("e.g. 3 days") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(reorder, { reorder = it }, label = { Text("Reorder at") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    if (existing == null) {
                        OutlinedTextField(opening, { opening = it }, label = { Text("Opening stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    } else {
                        Text("Stock changes via Stock in / Returns / a bill.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveProduct(
                    existing?.id, code.trim(), name.trim(), category.trim().ifBlank { "Other" }, unit.trim().ifBlank { "pc" },
                    price.toDoubleOrNull() ?: 0.0, shelfLife.trim(), reorder.toDoubleOrNull() ?: 0.0, opening.toDoubleOrNull() ?: 0.0
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { vm.deleteProduct(existing.id); onDismiss() }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
