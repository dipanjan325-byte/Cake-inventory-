package com.cakeshop.manager.csv

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.cakeshop.manager.data.*
import com.cakeshop.manager.data.dao.BillWithLines
import com.cakeshop.manager.data.dao.PurchaseWithLines
import com.cakeshop.manager.data.entity.*
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FOLDER_NAME = "business data"

/** One CSV field, safely quoted. */
private fun csvField(value: Any?): String {
    val s = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }
    return if (s.contains(',') || s.contains('"') || s.contains('\n')) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else s
}

private fun toCsv(headers: List<String>, rows: List<List<Any?>>): String {
    val sb = StringBuilder()
    sb.append(headers.joinToString(",") { csvField(it) }).append("\n")
    rows.forEach { row -> sb.append(row.joinToString(",") { csvField(it) }).append("\n") }
    return sb.toString()
}

data class ExportResult(val filesWritten: List<String>, val folderLabel: String)

/**
 * Writes one CSV per table (products, bills, bill_lines, stock_in,
 * stock_in_lines, returns, payments, vendors, feedback) into
 * Downloads/business data/, each named <table>-yyyy-MM-dd.csv.
 *
 * Uses MediaStore on Android 10+ (scoped storage, no permission needed);
 * falls back to the classic Downloads directory + WRITE_EXTERNAL_STORAGE
 * on Android 9 and below (declared with maxSdkVersion=28 in the manifest).
 */
object CsvExporter {

    suspend fun exportAll(
        context: Context,
        products: List<Product>,
        bills: List<BillWithLines>,
        purchases: List<PurchaseWithLines>,
        returns: List<ReturnEntry>,
        vendors: List<Vendor>,
        payments: List<Payment>,
        feedback: List<Feedback>
    ): ExportResult {
        val date = todayStr()
        val ledger = buildLedger(date, products, purchases, bills, returns)
        val vendorName = vendors.associateBy { it.id }
        val productName = products.associateBy { it.id }

        val files = LinkedHashMap<String, String>()

        files["products-$date.csv"] = toCsv(
            listOf("Code", "Name", "Category", "Unit", "Price", "Shelf life", "Reorder at", "Stock today", "Margin 20pct"),
            products.map { p ->
                listOf(
                    p.code, p.name, p.category, p.unit, p.price, p.shelfLifeRaw, p.reorderAt,
                    ledger[p.id]?.close ?: 0.0, p.margin20()
                )
            }
        )

        val billRows = mutableListOf<List<Any?>>()
        val fmtTime = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        bills.forEach { bw ->
            bw.lines.forEach { l ->
                billRows.add(
                    listOf(
                        bw.bill.no, bw.bill.date, fmtTime.format(Date(bw.bill.ts)),
                        bw.bill.customer, bw.bill.phone, l.name, l.qty, l.price,
                        Math.round(l.qty * l.price * 100) / 100.0, bw.bill.discount,
                        Math.round(bw.bill.tax * 100) / 100.0, bw.bill.total, bw.bill.pay
                    )
                )
            }
        }
        files["bills-$date.csv"] = toCsv(
            listOf("Bill", "Date", "Time", "Customer", "Phone", "Item", "Qty", "Rate", "Amount", "Discount", "Tax", "Bill total", "Paid by"),
            billRows
        )

        val purchaseRows = mutableListOf<List<Any?>>()
        purchases.forEach { pu ->
            val source = if (pu.purchase.kind == "vendor") vendorName[pu.purchase.vendorId]?.name ?: "Unknown vendor" else "In-house"
            pu.lines.forEach { l ->
                purchaseRows.add(
                    listOf(
                        pu.purchase.date, source, pu.purchase.invoiceNo, pu.purchase.dueDate ?: "",
                        productName[l.productId]?.name ?: "Deleted item", l.qty, l.unitCost,
                        Math.round(l.qty * l.unitCost * 100) / 100.0
                    )
                )
            }
        }
        files["stock_in-$date.csv"] = toCsv(
            listOf("Date", "Source", "Invoice", "Due date", "Item", "Qty", "Unit cost", "Amount"),
            purchaseRows
        )

        files["returns_and_wastage-$date.csv"] = toCsv(
            listOf("Date", "Type", "Item", "Qty", "Refund", "Loss", "Restocked", "Reason", "Bill"),
            returns.map { r ->
                listOf(
                    r.date, r.type, productName[r.productId]?.name ?: "Deleted item", r.qty,
                    if (r.type == "customer") r.refund else "",
                    if (r.type == "waste") r.loss else "",
                    if (r.type == "customer") (if (r.restock) "Yes" else "No") else "",
                    r.reason, r.billNo
                )
            }
        )

        files["vendors-$date.csv"] = toCsv(
            listOf("Name", "Phone", "Credit days", "Note"),
            vendors.map { listOf(it.name, it.phone, it.creditDays, it.note) }
        )

        files["payments-$date.csv"] = toCsv(
            listOf("Date", "Vendor", "Amount", "Paid by", "Note"),
            payments.map { listOf(it.date, vendorName[it.vendorId]?.name ?: "Unknown vendor", it.amount, it.mode, it.note) }
        )

        files["feedback-$date.csv"] = toCsv(
            listOf("Date", "Bill", "Customer", "Rating", "Comment"),
            feedback.map { listOf(it.date, it.billNo, it.customer, it.rating, it.comment) }
        )

        val written = mutableListOf<String>()
        files.forEach { (filename, content) ->
            if (writeToDownloads(context, filename, content)) written.add(filename)
        }
        return ExportResult(written, "Downloads/$FOLDER_NAME")
    }

    private fun writeToDownloads(context: Context, filename: String, content: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                val out: OutputStream? = resolver.openOutputStream(uri)
                out.use { it?.write(content.toByteArray(Charsets.UTF_8)) }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)
                if (!dir.exists()) dir.mkdirs()
                File(dir, filename).writeText(content, Charsets.UTF_8)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
