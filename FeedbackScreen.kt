package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.*

@Composable
fun FeedbackScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val feedback by vm.feedback.collectAsState()
    val bills by vm.bills.collectAsState()
    var range by remember { mutableStateOf("day") }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val filtered = remember(feedback, date, range) {
        feedback.filter { if (range == "day") it.date == date else it.date <= date && it.date >= addDaysStr(date, -29) }
            .sortedByDescending { it.ts }
    }
    val avg = if (filtered.isNotEmpty()) filtered.sumOf { it.rating } / filtered.size.toDouble() else 0.0

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add feedback") }
    }) { inner ->
        Column(Modifier.padding(inner).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = range == "day", onClick = { range = "day" }, label = { Text("This day") })
                FilterChip(selected = range == "month", onClick = { range = "month" }, label = { Text("Last 30 days") })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${filtered.size} rating(s)" + if (filtered.isNotEmpty()) ", average ${"%.1f".format(avg)} / 5" else "",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Text("No feedback yet. Send a bill to a customer, then log their rating here.", modifier = Modifier.padding(top = 32.dp))
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                StarRow(f.rating)
                                Text(
                                    "${f.customer.ifBlank { "Customer" }}${if (f.billNo.isNotBlank()) ", bill ${f.billNo}" else ""}, ${com.cakeshop.manager.data.formatDateShort(f.date)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (f.comment.isNotBlank()) Text(f.comment, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { confirmDelete = f.id }) { Text("Delete") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) FeedbackDialog(vm, bills, onDismiss = { showAdd = false })
    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this feedback?") },
            confirmButton = { TextButton(onClick = { vm.deleteFeedback(confirmDelete!!); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            text = {}
        )
    }
}

@Composable
private fun StarRow(rating: Int, onRate: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..5) {
            val base = Modifier.size(if (onRate != null) 32.dp else 18.dp)
            Icon(
                if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = Color(0xFFE39B00),
                modifier = if (onRate != null) base.clickable { onRate(i) } else base
            )
        }
    }
}

@Composable
private fun FeedbackDialog(
    vm: com.cakeshop.manager.data.AppViewModel,
    bills: List<com.cakeshop.manager.data.dao.BillWithLines>,
    onDismiss: () -> Unit
) {
    var billId by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val bill = bills.find { it.bill.id == billId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add customer feedback") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(bill?.let { "${it.bill.no}, ${it.bill.customer.ifBlank { "walk-in" }}" } ?: "No bill (walk-in)")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("No bill (walk-in)") }, onClick = { billId = null; expanded = false })
                        bills.sortedByDescending { it.bill.ts }.take(80).forEach { bw ->
                            DropdownMenuItem(text = { Text("${bw.bill.no}, ${bw.bill.customer.ifBlank { "walk-in" }}") }, onClick = { billId = bw.bill.id; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Rating")
                StarRow(rating) { rating = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(comment, { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (rating > 0) {
                    vm.addFeedback(billId ?: "", bill?.bill?.no ?: "", bill?.bill?.customer ?: "", rating, comment, todayStr())
                    onDismiss()
                }
            }) { Text("Save feedback") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
