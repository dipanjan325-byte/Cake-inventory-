package com.cakeshop.manager.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cakeshop.manager.data.dao.BillWithLines
import com.cakeshop.manager.data.dao.PurchaseWithLines
import com.cakeshop.manager.data.entity.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)

    val products: StateFlow<List<Product>> =
        db.productDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val vendors: StateFlow<List<Vendor>> =
        db.vendorDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bills: StateFlow<List<BillWithLines>> =
        db.billDao().observeAllWithLines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val purchases: StateFlow<List<PurchaseWithLines>> =
        db.purchaseDao().observeAllWithLines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val returns: StateFlow<List<ReturnEntry>> =
        db.returnDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val payments: StateFlow<List<Payment>> =
        db.paymentDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val feedback: StateFlow<List<Feedback>> =
        db.feedbackDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettingsEntity> =
        db.settingsDao().observe()
            .map { it ?: AppSettingsEntity() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsEntity())

    init {
        viewModelScope.launch {
            if (db.settingsDao().getOnce() == null) db.settingsDao().upsert(AppSettingsEntity())
        }
    }

    // ---------------- Products ----------------
    fun saveProduct(
        existingId: String?, code: String, name: String, category: String, unit: String,
        price: Double, shelfLifeRaw: String, reorderAt: Double, opening: Double
    ) = viewModelScope.launch {
        val id = existingId ?: newId()
        val existing = existingId?.let { db.productDao().getById(it) }
        db.productDao().upsert(
            Product(
                id = id, code = code, name = name, category = category, unit = unit,
                price = price, shelfLifeRaw = shelfLifeRaw, shelfLifeDays = parseShelfLifeDays(shelfLifeRaw),
                reorderAt = reorderAt,
                opening = existing?.opening ?: opening,
                dateAdded = existing?.dateAdded ?: todayStr()
            )
        )
    }

    fun deleteProduct(id: String) = viewModelScope.launch { db.productDao().delete(id) }

    /** Imports the bundled price list: adds anything missing (matched by
     *  code), and backfills shelf life onto items you already have that
     *  don't have one set yet. Never overwrites a price or stock you've
     *  already edited. Returns (added, shelfLifeFilled). */
    suspend fun importPriceList(): Pair<Int, Int> {
        val existingByCode = db.productDao().getAllOnce().filter { it.code.isNotBlank() }.associateBy { it.code }
        var added = 0; var filled = 0
        val today = todayStr()
        PRICE_LIST.forEach { seed ->
            val existing = existingByCode[seed.code]
            if (existing == null) {
                db.productDao().upsert(
                    Product(
                        id = newId(), code = seed.code, name = seed.name, category = seed.category,
                        unit = "pc", price = seed.price, shelfLifeRaw = seed.shelfLifeRaw,
                        shelfLifeDays = seed.shelfLifeDays, reorderAt = 0.0, opening = 0.0, dateAdded = today
                    )
                )
                added++
            } else if (existing.shelfLifeRaw.isBlank() && seed.shelfLifeRaw.isNotBlank()) {
                db.productDao().upsert(
                    existing.copy(shelfLifeRaw = seed.shelfLifeRaw, shelfLifeDays = seed.shelfLifeDays)
                )
                filled++
            }
        }
        return added to filled
    }

    // ---------------- Vendors ----------------
    fun saveVendor(existingId: String?, name: String, phone: String, creditDays: Int, note: String) =
        viewModelScope.launch {
            db.vendorDao().upsert(Vendor(existingId ?: newId(), name, phone, creditDays, note))
        }

    fun deleteVendor(id: String) = viewModelScope.launch { db.vendorDao().delete(id) }

    // ---------------- Billing ----------------
    data class CartLine(val product: Product, val qty: Double)

    suspend fun nextBillNo(date: String): String {
        val seq = db.billDao().countForDate(date) + 1
        val ymd = date.replace("-", "").drop(2)
        return "INV-$ymd-" + seq.toString().padStart(3, '0')
    }

    suspend fun createBill(
        lines: List<CartLine>, customer: String, phone: String, pay: String,
        discount: Double, discountIsPercent: Boolean, taxRate: Double
    ): Bill {
        val date = todayStr()
        val billId = newId()
        val no = nextBillNo(date)
        val sub = lines.sumOf { it.qty * it.product.price }
        val disc = (if (discountIsPercent) sub * minOf(discount, 100.0) / 100.0 else minOf(discount, sub)).coerceAtLeast(0.0)
        val taxable = sub - disc
        val tax = taxable * taxRate / 100.0
        val raw = taxable + tax
        val total = Math.round(raw).toDouble()
        val bill = Bill(
            id = billId, no = no, date = date, ts = System.currentTimeMillis(),
            customer = customer, phone = phone, pay = pay,
            subtotal = sub, discount = disc, taxRate = taxRate, tax = tax,
            roundOff = total - raw, total = total
        )
        val billLines = lines.map {
            BillLine(
                billId = billId, productId = it.product.id, name = it.product.name,
                unit = it.product.unit, qty = it.qty, price = it.product.price, cost = it.product.unitCost()
            )
        }
        db.billDao().insertBillWithLines(bill, billLines)
        return bill
    }

    fun voidBill(id: String) = viewModelScope.launch { db.billDao().delete(id) }

    // ---------------- Stock in ----------------
    suspend fun addPurchase(
        kind: String, vendorId: String?, date: String, dueDate: String?, invoiceNo: String,
        lines: List<Triple<String, Double, Double>> // productId, qty, unitCost
    ) {
        val purchaseId = newId()
        val total = lines.sumOf { it.second * it.third }
        val purchase = Purchase(
            id = purchaseId, kind = kind, vendorId = vendorId, date = date,
            dueDate = dueDate, invoiceNo = invoiceNo, total = total, ts = System.currentTimeMillis()
        )
        val purchaseLines = lines.map { (pid, qty, cost) -> PurchaseLine(purchaseId = purchaseId, productId = pid, qty = qty, unitCost = cost) }
        db.purchaseDao().insertPurchaseWithLines(purchase, purchaseLines)
    }

    fun deletePurchase(id: String) = viewModelScope.launch { db.purchaseDao().delete(id) }

    // ---------------- Returns & wastage ----------------
    fun addReturn(
        type: String, date: String, productId: String, qty: Double,
        refund: Double, restock: Boolean, reason: String, billNo: String, loss: Double
    ) = viewModelScope.launch {
        db.returnDao().insert(
            ReturnEntry(
                id = newId(), type = type, date = date, productId = productId, qty = qty,
                refund = refund, restock = restock, reason = reason, billNo = billNo, loss = loss,
                ts = System.currentTimeMillis()
            )
        )
    }

    fun deleteReturn(id: String) = viewModelScope.launch { db.returnDao().delete(id) }

    // ---------------- Vendors payments ----------------
    fun addPayment(vendorId: String, amount: Double, date: String, mode: String, note: String) =
        viewModelScope.launch {
            db.paymentDao().insert(Payment(newId(), vendorId, amount, date, mode, note, System.currentTimeMillis()))
        }

    fun deletePayment(id: String) = viewModelScope.launch { db.paymentDao().delete(id) }

    // ---------------- Feedback ----------------
    fun addFeedback(billId: String, billNo: String, customer: String, rating: Int, comment: String, date: String) =
        viewModelScope.launch {
            db.feedbackDao().insert(Feedback(newId(), billId, billNo, customer, rating, comment, date, System.currentTimeMillis()))
        }

    fun deleteFeedback(id: String) = viewModelScope.launch { db.feedbackDao().delete(id) }

    // ---------------- Settings ----------------
    fun saveSettings(shopName: String, taxPct: Double, countryCode: String, reviewUrl: String, anthropicApiKey: String) =
        viewModelScope.launch {
            db.settingsDao().upsert(AppSettingsEntity("main", shopName, taxPct, countryCode, reviewUrl, anthropicApiKey))
        }

    fun eraseAll() = viewModelScope.launch {
        db.productDao().deleteAll(); db.vendorDao().deleteAll(); db.billDao().deleteAll()
        db.purchaseDao().deleteAll(); db.returnDao().deleteAll(); db.paymentDao().deleteAll()
        db.feedbackDao().deleteAll()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(application) as T
            }
    }
}
