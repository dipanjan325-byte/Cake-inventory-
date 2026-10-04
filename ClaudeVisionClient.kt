package com.cakeshop.manager.claude

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ScannedLine(val text: String, val qty: Double?, val code: String?)

sealed class ScanResult {
    data class Success(val lines: List<ScannedLine>) : ScanResult()
    data class Failure(val message: String) : ScanResult()
}

/**
 * Calls the Anthropic API directly from the device, using the API key you
 * enter in Settings. Only ever runs when you tap "Read photo" — nothing is
 * sent automatically. The key lives only in this app's local database.
 */
object ClaudeVisionClient {
    private const val MODEL = "claude-haiku-4-5-20251001"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun readStockPhoto(apiKey: String, imageBytes: ByteArray, mediaType: String, catalog: String): ScanResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext ScanResult.Failure("Add your Anthropic API key in Settings first.")
            try {
                val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                val prompt = buildPrompt(catalog)

                val content = JSONArray()
                content.put(JSONObject().apply {
                    put("type", "image")
                    put("source", JSONObject().apply {
                        put("type", "base64")
                        put("media_type", mediaType)
                        put("data", b64)
                    })
                })
                content.put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                })

                val message = JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                }
                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 1500)
                    put("messages", JSONArray().put(message))
                }

                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("content-type", "application/json")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext ScanResult.Failure(
                            when (resp.code) {
                                401 -> "That API key was rejected. Check it in Settings."
                                429 -> "Too many requests just now — wait a moment and try again."
                                else -> "The photo-reading service had a problem (code ${resp.code})."
                            }
                        )
                    }
                    val data = JSONObject(bodyStr)
                    val contentArr = data.optJSONArray("content") ?: JSONArray()
                    val text = StringBuilder()
                    for (i in 0 until contentArr.length()) {
                        val block = contentArr.getJSONObject(i)
                        if (block.optString("type") == "text") text.append(block.optString("text"))
                    }
                    val cleaned = text.toString()
                        .replace(Regex("```json", RegexOption.IGNORE_CASE), "")
                        .replace("```", "")
                        .trim()
                    val arr = try { JSONArray(cleaned) } catch (e: Exception) {
                        return@withContext ScanResult.Failure("Could not understand what was read from the photo. Try a clearer photo.")
                    }
                    val lines = mutableListOf<ScannedLine>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val lineText = obj.optString("text", "")
                        val qty = if (obj.has("qty") && !obj.isNull("qty")) obj.optDouble("qty").takeIf { !it.isNaN() } else null
                        val code = if (obj.has("code") && !obj.isNull("code")) obj.optString("code").ifBlank { null } else null
                        lines.add(ScannedLine(lineText, qty, code))
                    }
                    ScanResult.Success(lines)
                }
            } catch (e: java.io.IOException) {
                ScanResult.Failure("Could not reach the photo-reading service. Check your connection and try again.")
            } catch (e: Exception) {
                ScanResult.Failure("Something went wrong reading the photo. Try again.")
            }
        }

    private fun buildPrompt(catalog: String): String =
        "This image shows a bakery stock sheet, delivery note, or production list. " +
            "It may be handwritten or printed, in English or mixed with Bengali or Hindi. " +
            "Read every line that has an item and a quantity.\n\n" +
            "Here is the shop's item catalog, as \"CODE NAME\" (use it to find the closest " +
            "match for each line; use null for code if nothing matches well):\n$catalog\n\n" +
            "Reply with ONLY a JSON array, one object per line read: " +
            "{\"text\": the item text as written, \"qty\": the quantity as a plain number " +
            "(ignore any unit word), \"code\": the matching catalog code, or null}. " +
            "Skip lines with no readable quantity. Do not include anything other than the JSON array."
}
