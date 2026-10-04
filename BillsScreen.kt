package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.formatDateShort
import com.cakeshop.manager.ui.money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BillsScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val bills by vm.bills.collectAsState()
    var range by remember { mutableStateOf("day") }
    var confirmVoid by remember { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.ENGLISH) }

    val filtered = remember(bills, date, range) {
        bills.filter { if (range == "day") it.bill.date == date else it.bill.date <= date && it.bill.date >= com.cakeshop.manager.data.addDaysStr(date, -29) }
            .sortedByDescending { it.bill.ts }
    }
    val total = filtered.sumOf { it.bill.total }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = range == "day", onClick = { range = "day" }, label = { Text("This day") })
            FilterChip(selected = range == "month", onClick = { range = "month" }, label = { Text("Last 30 days") })
        }
        Spacer(Modifier.height(6.dp))
        Text("${filtered.size} bills, ${money(total)} billed", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text("No bills in this range.", modifier = Modifier.padding(top = 32.dp))
        } else {
            LazyColumn {
                items(filtered, key = { it.bill.id }) { bw ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(bw.bill.no, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatDateShort(bw.bill.date)}, ${timeFmt.format(Date(bw.bill.ts))}" +
                                        (if (bw.bill.customer.isNotBlank()) ", ${bw.bill.customer}" else ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    bw.lines.joinToString(", ") { "${it.name} x${com.cakeshop.manager.ui.qty(it.qty)}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(money(bw.bill.total), fontWeight = FontWeight.Bold)
                                Text(bw.bill.pay, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { confirmVoid = bw.bill.id }) { Text("Void") }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (confirmVoid != null) {
        AlertDialog(
            onDismissRequest = { confirmVoid = null },
            title = { Text("Void this bill?") },
            text = { Text("The bill is deleted and its items go back into stock.") },
            confirmButton = { TextButton(onClick = { vm.voidBill(confirmVoid!!); confirmVoid = null }) { Text("Void bill") } },
            dismissButton = { TextButton(onClick = { confirmVoid = null }) { Text("Cancel") } }
        )
    }
}
