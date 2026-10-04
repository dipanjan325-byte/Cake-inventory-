package com.cakeshop.manager.data

import com.cakeshop.manager.data.dao.BillWithLines
import com.cakeshop.manager.data.dao.PurchaseWithLines
import com.cakeshop.manager.data.entity.Product
import com.cakeshop.manager.data.entity.ReturnEntry
import com.cakeshop.manager.data.entity.Vendor
import com.cakeshop.manager.data.entity.unitCost

// ---------------------------------------------------------------------
// Stock ledger — same opening/in/sold/out/close model as the web app.
// ---------------------------------------------------------------------

data class StockRow(
    val open: Double = 0.0,
    val inQty: Double = 0.0,
    val sold: Double = 0.0,
    val out: Double = 0.0
) {
    val close: Double get() = open + inQty - sold - out
}
val EMPTY_ROW = StockRow()

private class MutableRow {
    var open = 0.0; var inQty = 0.0; var sold = 0.0; var out = 0.0
    fun toStockRow() = StockRow(open, inQty, sold, out)
}

fun buildLedger(
    date: String,
    products: List<Product>,
    purchases: List<PurchaseWithLines>,
    bills: List<BillWithLines>,
    returns: List<ReturnEntry>
): Map<String, StockRow> {
    val m = HashMap<String, MutableRow>()
    fun row(id: String) = m.getOrPut(id) { MutableRow() }

    // isIn = true for events that add stock (opening, purchases, restocked
    // returns); false for events that remove it (sales, waste, kitchen use).
    // Before the selected date, every event just folds into "open"; on the
    // selected date itself it lands in the matching in/sold/out bucket.
    fun apply(d: String?, id: String, isIn: Boolean, qty: Double, onDate: (MutableRow) -> Unit) {
        if (d == null || d > date || qty == 0.0) return
        val r = row(id)
        if (d < date) {
            if (isIn) r.open += qty else r.open -= qty
        } else {
            onDate(r)
        }
    }

    products.forEach { p ->
        if (p.opening != 0.0) apply(p.dateAdded, p.id, true, p.opening) { it.inQty += p.opening }
    }
    purchases.forEach { pu ->
        pu.lines.forEach { l -> apply(pu.purchase.date, l.productId, true, l.qty) { it.inQty += l.qty } }
    }
    bills.forEach { b ->
        b.lines.forEach { l -> apply(b.bill.date, l.productId, false, l.qty) { it.sold += l.qty } }
    }
    returns.forEach { r ->
        if (r.type == "customer") {
            if (r.restock) apply(r.date, r.productId, true, r.qty) { it.inQty += r.qty }
        } else {
            apply(r.date, r.productId, false, r.qty) { it.out += r.qty }
        }
    }
    return m.mapValues { it.value.toStockRow() }
}

// ---------------------------------------------------------------------
// Dashboard KPIs for one day
// ---------------------------------------------------------------------

data class DayKpis(
    val stockValue: Double = 0.0,
    val itemsInStock: Int = 0,
    val lowStockCount: Int = 0,
    val gross: Double = 0.0,
    val refunds: Double = 0.0,
    val net: Double = 0.0,
    val billCount: Int = 0,
    val itemsSold: Double = 0.0,
    val avgBill: Double = 0.0,
    val customerReturnUnits: Double = 0.0,
    val wasteUnits: Double = 0.0,
    val wasteLoss: Double = 0.0,
    val returnsValue: Double = 0.0
)

fun computeKpis(
    date: String,
    products: List<Product>,
    ledger: Map<String, StockRow>,
    bills: List<BillWithLines>,
    returns: List<ReturnEntry>
): DayKpis {
    var stockValue = 0.0; var items = 0; var low = 0
    products.forEach { p ->
        val close = ledger[p.id]?.close ?: 0.0
        stockValue += maxOf(close, 0.0) * p.unitCost()
        if (close > 0) items++
        if (p.reorderAt > 0 && close <= p.reorderAt) low++
    }
    var gross = 0.0; var billCount = 0; var itemsSold = 0.0
    bills.filter { it.bill.date == date }.forEach { bw ->
        billCount++; gross += bw.bill.total
        bw.lines.forEach { itemsSold += it.qty }
    }
    var refunds = 0.0; var custUnits = 0.0; var wasteUnits = 0.0; var wasteLoss = 0.0
    returns.filter { it.date == date }.forEach { r ->
        when (r.type) {
            "customer" -> { custUnits += r.qty; refunds += r.refund }
            "waste" -> { wasteUnits += r.qty; wasteLoss += r.loss }
        }
    }
    val net = gross - refunds
    val avg = if (billCount > 0) gross / billCount else 0.0
    return DayKpis(stockValue, items, low, gross, refunds, net, billCount, itemsSold, avg, custUnits, wasteUnits, wasteLoss, refunds + wasteLoss)
}

// ---------------------------------------------------------------------
// Vendor payables — who's owed what, as of a given date
// ---------------------------------------------------------------------

data class VendorInvoice(val purchase: PurchaseWithLines, val unpaid: Double, val dueDate: String)
data class VendorPayable(
    val vendor: Vendor,
    val invoices: List<VendorInvoice>,
    val due: Double,
    val overdue: Double,
    val upcoming: Double,
    val outstanding: Double
)
data class Payables(
    val due: Double = 0.0,
    val overdue: Double = 0.0,
    val upcoming: Double = 0.0,
    val outstanding: Double = 0.0,
    val paidToday: Double = 0.0,
    val purchasedToday: Double = 0.0,
    val byVendor: Map<String, VendorPayable> = emptyMap()
)

private class VendorBuilder(val vendor: Vendor) {
    val invoices = mutableListOf<VendorInvoice>()
    var due = 0.0; var overdue = 0.0; var upcoming = 0.0; var outstanding = 0.0
    fun build() = VendorPayable(vendor, invoices, due, overdue, upcoming, outstanding)
}

fun computePayables(
    date: String,
    vendors: List<Vendor>,
    purchases: List<PurchaseWithLines>,
    payments: List<com.cakeshop.manager.data.entity.Payment>
): Payables {
    val byVendor = LinkedHashMap<String, VendorBuilder>()
    vendors.forEach { byVendor[it.id] = VendorBuilder(it) }
    fun vb(id: String) = byVendor.getOrPut(id) { VendorBuilder(Vendor(id, "Unknown vendor")) }

    val paid = HashMap<String, Double>()
    var paidToday = 0.0
    payments.filter { it.date <= date }.forEach { x ->
        paid[x.vendorId] = (paid[x.vendorId] ?: 0.0) + x.amount
        if (x.date == date) paidToday += x.amount
    }

    var purchasedToday = 0.0
    purchases.filter { it.purchase.kind == "vendor" && it.purchase.date <= date }
        .sortedWith(compareBy({ it.purchase.date }, { it.purchase.ts }))
        .forEach { pu ->
            val vendorId = pu.purchase.vendorId ?: return@forEach
            val v = vb(vendorId)
            val total = pu.purchase.total
            if (pu.purchase.date == date) purchasedToday += total
            val available = paid[vendorId] ?: 0.0
            val use = minOf(available, total)
            paid[vendorId] = available - use
            val remaining = total - use
            if (remaining > 0.005) {
                val due = pu.purchase.dueDate ?: pu.purchase.date
                v.invoices.add(VendorInvoice(pu, remaining, due))
                v.outstanding += remaining
                when {
                    due < date -> v.overdue += remaining
                    due == date -> v.due += remaining
                    else -> v.upcoming += remaining
                }
            }
        }

    var dueT = 0.0; var overdueT = 0.0; var upcomingT = 0.0; var outstandingT = 0.0
    byVendor.values.forEach { dueT += it.due; overdueT += it.overdue; upcomingT += it.upcoming; outstandingT += it.outstanding }
    return Payables(dueT, overdueT, upcomingT, outstandingT, paidToday, purchasedToday, byVendor.mapValues { it.value.build() })
}

// ---------------------------------------------------------------------
// Shelf-life "return tomorrow / overdue" alerts
// ---------------------------------------------------------------------

data class ShelfAlert(val product: Product, val qty: Double, val lastStockDate: String, val diffDays: Long)

private fun lastStockDate(
    product: Product,
    date: String,
    purchases: List<PurchaseWithLines>,
    returns: List<ReturnEntry>
): String? {
    var last: String? = if (product.opening != 0.0 && product.dateAdded <= date) product.dateAdded else null
    purchases.forEach { pu ->
        if (pu.purchase.date > date) return@forEach
        pu.lines.forEach { l ->
            if (l.productId == product.id && l.qty > 0 && (last == null || pu.purchase.date > last!!)) {
                last = pu.purchase.date
            }
        }
    }
    returns.forEach { r ->
        if (r.type == "customer" && r.restock && r.productId == product.id && r.date <= date && (last == null || r.date > last!!)) {
            last = r.date
        }
    }
    return last
}

fun computeShelfAlerts(
    date: String,
    products: List<Product>,
    ledger: Map<String, StockRow>,
    purchases: List<PurchaseWithLines>,
    returns: List<ReturnEntry>
): List<ShelfAlert> {
    val out = mutableListOf<ShelfAlert>()
    products.forEach { p ->
        val days = p.shelfLifeDays ?: return@forEach
        val close = ledger[p.id]?.close ?: 0.0
        if (close <= 0) return@forEach
        val last = lastStockDate(p, date, purchases, returns) ?: return@forEach
        val expiry = addDaysStr(last, days.toLong())
        val diff = daysBetweenStr(date, expiry)
        if (diff <= 1) out.add(ShelfAlert(p, close, last, diff))
    }
    return out.sortedWith(compareBy({ it.diffDays }, { it.product.name }))
}
