package com.cakeshop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.data.*
import com.cakeshop.manager.ui.*
import com.cakeshop.manager.ui.components.*
import kotlinx.coroutines.launch

private enum class Metric { STOCK, SALES, RETURNS, VENDOR }

@Composable
fun DashboardScreen(vm: com.cakeshop.manager.data.AppViewModel, date: String) {
    val products by vm.products.collectAsState()
    val vendors by vm.vendors.collectAsState()
    val bills by vm.bills.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val returns by vm.returns.collectAsState()
    val payments by vm.payments.collectAsState()

    var metric by remember { mutableStateOf(Metric.SALES) }
    var span by remember { mutableIntStateOf(7) }

    if (products.isEmpty() && bills.isEmpty()) {
        OnboardingPanel(vm)
        return
    }

    val days = remember(date, span) { (maxOf(span, 7)) .let { n -> (n - 1 downTo 0).map { addDaysStr(date, -it.toLong()) } } }

    data class DayCompute(val d: String, val ledger: Map<String, StockRow>, val kpis: DayKpis, val payables: Payables)
    val computed = remember(date, span, products, vendors, bills, purchases, returns, payments) {
        days.map { d ->
            val ledger = buildLedger(d, products, purchases, bills, returns)
            val kpis = computeKpis(d, products, ledger, bills, returns)
            val payables = computePayables(d, vendors, purchases, payments)
            DayCompute(d, ledger, kpis, payables)
        }
    }
    val today = computed.last()
    val prev = computed.getOrNull(computed.size - 2) ?: today

    fun valueFor(m: Metric, c: DayCompute) = when (m) {
        Metric.STOCK -> c.kpis.stockValue
        Metric.SALES -> c.kpis.net
        Metric.RETURNS -> c.kpis.returnsValue
        Metric.VENDOR -> c.payables.due + c.payables.overdue
    }

    val shelfAlerts = remember(date, products, purchases, returns, bills) {
        val ledger = buildLedger(date, products, purchases, bills, returns)
        computeShelfAlerts(date, products, ledger, purchases, returns)
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        KpiTile(
                            "Stock value at close", money(today.kpis.stockValue),
                            "Opening ${money(prev.kpis.stockValue)}",
                            "${today.kpis.itemsInStock} items, ${today.kpis.lowStockCount} low",
                            Pistachio, metric == Metric.STOCK
                        ) { metric = Metric.STOCK }
                    }
                    Box(Modifier.weight(1f)) {
                        KpiTile(
                            "Net sales", money(today.kpis.net),
                            "${today.kpis.billCount} bills, avg ${money(today.kpis.avgBill)}",
                            "Refunds ${money(today.kpis.refunds)}",
                            Raspberry, metric == Metric.SALES
                        ) { metric = Metric.SALES }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        KpiTile(
                            "Returns & wastage", money(today.kpis.returnsValue),
                            "${qty(today.kpis.customerReturnUnits)} returned",
                            "${qty(today.kpis.wasteUnits)} wasted",
                            Caramel, metric == Metric.RETURNS
                        ) { metric = Metric.RETURNS }
                    }
                    Box(Modifier.weight(1f)) {
                        KpiTile(
                            "Vendor payments due", money(today.payables.due + today.payables.overdue),
                            "Due ${money(today.payables.due)}",
                            "Overdue ${money(today.payables.overdue)}",
                            Blue, metric == Metric.VENDOR
                        ) { metric = Metric.VENDOR }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = when (metric) {
                    Metric.STOCK -> "Stock value per day"; Metric.SALES -> "Net sales per day"
                    Metric.RETURNS -> "Returns & wastage per day"; Metric.VENDOR -> "Vendor dues per day"
                },
                action = {
                    Row {
                        FilterChip(selected = span == 7, onClick = { span = 7 }, label = { Text("7d") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = span == 14, onClick = { span = 14 }, label = { Text("14d") })
                    }
                }
            ) {
                val points = computed.takeLast(span).map { BarPoint(formatDateShort(it.d), valueFor(metric, it)) }
                val color = when (metric) { Metric.STOCK -> Pistachio; Metric.SALES -> Raspberry; Metric.RETURNS -> Caramel; Metric.VENDOR -> Blue }
                SimpleBarChart(points, color)
            }
        }

        if (shelfAlerts.isNotEmpty()) {
            item {
                SectionCard("Shelf life alerts", "Items to pull tomorrow or already overdue") {
                    shelfAlerts.forEach { a ->
                        val label = when {
                            a.diffDays == 1L -> "Return tomorrow"
                            a.diffDays == 0L -> "Return today"
                            else -> "Overdue ${-a.diffDays}d"
                        }
                        val (bg, ink) = if (a.diffDays == 1L) WarnBg to WarnInk else BadBg to BadInk
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.product.code} ${a.product.name}".trim())
                                Text("Stocked ${formatDateShort(a.lastStockDate)}, ${a.product.shelfLifeRaw}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${qty(a.qty)} ${a.product.unit}", modifier = Modifier.padding(end = 8.dp))
                                Pill(label, bg, ink)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Vendors to pay", "Paid today ${money(today.payables.paidToday)}") {
                val toPay = today.payables.byVendor.values.filter { it.outstanding > 0.005 }.sortedByDescending { it.due + it.overdue }
                if (toPay.isEmpty()) Text("Nothing owed to vendors on this date.", style = MaterialTheme.typography.bodySmall)
                toPay.forEach { v ->
                    ListRow(
                        v.vendor.name,
                        buildString {
                            if (v.overdue > 0) append("Overdue ${money(v.overdue)}. ")
                            if (v.due > 0) append("Due today ${money(v.due)}.")
                        },
                        money(v.outstanding)
                    )
                }
            }
        }

        item {
            SectionCard("Needs restocking") {
                val low = products.filter { it.reorderAt > 0 && (today.ledger[it.id]?.close ?: 0.0) <= it.reorderAt }
                if (low.isEmpty()) Text("Everything is above its reorder level.", style = MaterialTheme.typography.bodySmall)
                low.forEach { p ->
                    val c = today.ledger[p.id]?.close ?: 0.0
                    ListRow(p.name, "Reorder at ${qty(p.reorderAt)} ${p.unit}", if (c <= 0) "Out" else "${qty(c)} left")
                }
            }
        }

        item {
            SectionCard("Top sellers on ${formatDateShort(date)}") {
                val tops = HashMap<String, Pair<Double, Double>>()
                bills.filter { it.bill.date == date }.forEach { bw ->
                    bw.lines.forEach { l ->
                        val cur = tops[l.name] ?: (0.0 to 0.0)
                        tops[l.name] = (cur.first + l.qty) to (cur.second + l.qty * l.price)
                    }
                }
                val list = tops.entries.sortedByDescending { it.value.first }.take(5)
                if (list.isEmpty()) Text("No sales on this date.", style = MaterialTheme.typography.bodySmall)
                list.forEach { (name, v) -> ListRow(name, money(v.second), "${qty(v.first)} sold") }
            }
        }
    }
}

@Composable
private fun OnboardingPanel(vm: com.cakeshop.manager.data.AppViewModel) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Set up your shop", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Import the official price list from Inventory, or add items one at a time.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { scope.launch { vm.importPriceList() } }) { Text("Import official price list") }
    }
}
