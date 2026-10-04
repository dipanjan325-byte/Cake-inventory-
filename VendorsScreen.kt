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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.entity.Vendor
import com.cakeshop.manager.ui.components.ListRow
import com.cakeshop.manager.ui.components.Pill
import com.cakeshop.manager.ui.BadBg
import com.cakeshop.manager.ui.BadInk
import com.cakeshop.manager.ui.OkBg
import com.cakeshop.manager.ui.OkInk
import com.cakeshop.manager.ui.WarnBg
import com.cakeshop.manager.ui.WarnInk
import com.cakeshop.manager.ui.money
import com.cakeshop.manager.ui.qty

@Composable
fun VendorsScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val vendors by vm.vendors.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val payments by vm.payments.collectAsState()
    var showAddVendor by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Vendor?>(null) }
    var editVendor by remember { mutableStateOf<Vendor?>(null) }

    val payables = remember(date, vendors, purchases, payments) { computePayables(date, vendors, purchases, payments) }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showAddVendor = true }) { Icon(Icons.Filled.Add, contentDescription = "Add vendor") }
    }) { inner ->
        LazyColumn(Modifier.padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Overdue ${money(payables.overdue)}", BadBg, BadInk)
                    Pill("Due ${money(payables.due)}", WarnBg, WarnInk)
                    Pill("Paid today ${money(payables.paidToday)}", OkBg, OkInk)
                }
            }
            if (vendors.isEmpty()) {
                item { Text("No vendors yet. Add the suppliers you buy from.") }
            }
            items(vendors.sortedByDescending { payables.byVendor[it.id]?.outstanding ?: 0.0 }, key = { it.id }) { v ->
                val vp = payables.byVendor[v.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(v.name, fontWeight = FontWeight.Bold)
                                Text("Pays in ${v.creditDays} days" + (if (v.phone.isNotBlank()) ", ${v.phone}" else ""), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(money(vp?.outstanding ?: 0.0), fontWeight = FontWeight.ExtraBold)
                        }
                        vp?.invoices?.forEach { inv ->
                            ListRow(
                                if (inv.purchase.purchase.invoiceNo.isNotBlank()) "Invoice ${inv.purchase.purchase.invoiceNo}" else "Delivery ${formatDateShort(inv.purchase.purchase.date)}",
                                "Pay by ${formatDateShort(inv.dueDate)}",
                                money(inv.unpaid)
                            )
                        }
                        Row(Modifier.padding(top = 8.dp)) {
                            Button(onClick = { payFor = v }) { Text("Record payment") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { editVendor = v }) { Text("Edit") }
                        }
                    }
                }
            }
            item {
                Text("Recent payments", fontWeight = FontWeight.Bold)
                payments.sortedByDescending { it.ts }.take(12).forEach { p ->
                    val vn = vendors.find { it.id == p.vendorId }?.name ?: "Unknown vendor"
                    ListRow(vn, "${formatDateShort(p.date)}, ${p.mode}", money(p.amount))
                }
            }
        }
    }

    if (showAddVendor) VendorDialog(vm, null, onDismiss = { showAddVendor = false })
    if (editVendor != null) VendorDialog(vm, editVendor, onDismiss = { editVendor = null })
    if (payFor != null) PaymentDialog(vm, payFor!!, payables.byVendor[payFor!!.id]?.outstanding ?: 0.0, onDismiss = { payFor = null })
}

@Composable
private fun VendorDialog(vm: com.cakeshop.manager.data.AppViewModel, existing: Vendor?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var creditDays by remember { mutableStateOf(existing?.creditDays?.toString() ?: "7") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit vendor" else "Add vendor") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Vendor name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(creditDays, { creditDays = it }, label = { Text("Days to pay after delivery") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    vm.saveVendor(existing?.id, name.trim(), phone.trim(), creditDays.toIntOrNull() ?: 7, note.trim())
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { vm.deleteVendor(existing.id); onDismiss() }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun PaymentDialog(vm: com.cakeshop.manager.data.AppViewModel, vendor: Vendor, outstanding: Double, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(if (outstanding > 0) qty(outstanding) else "") }
    var mode by remember { mutableStateOf("Cash") }
    var note by remember { mutableStateOf("") }
    val modes = listOf("Cash", "UPI", "Bank transfer", "Cheque")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment \u2014 ${vendor.name}") },
        text = {
            Column {
                Text("Outstanding: ${money(outstanding)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount paid (\u20B9)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    vm.addPayment(vendor.id, amt, todayStr(), mode, note.trim())
                    onDismiss()
                }
            }) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
