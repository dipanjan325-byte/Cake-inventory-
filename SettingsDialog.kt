package com.cakeshop.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cakeshop.manager.csv.CsvExporter
import com.cakeshop.manager.data.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val products by vm.products.collectAsState()
    val bills by vm.bills.collectAsState()
    val purchases by vm.purchases.collectAsState()
    val returns by vm.returns.collectAsState()
    val vendors by vm.vendors.collectAsState()
    val payments by vm.payments.collectAsState()
    val feedback by vm.feedback.collectAsState()

    var shopName by remember(settings) { mutableStateOf(settings.shopName) }
    var taxPct by remember(settings) { mutableStateOf(settings.taxPct.toString()) }
    var countryCode by remember(settings) { mutableStateOf(settings.countryCode) }
    var reviewUrl by remember(settings) { mutableStateOf(settings.reviewUrl) }
    var apiKey by remember(settings) { mutableStateOf(settings.anthropicApiKey) }

    var confirmErase by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(shopName, { shopName = it }, label = { Text("Shop name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    taxPct, { taxPct = it }, label = { Text("Tax on bills (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    countryCode, { countryCode = it.filter { c -> c.isDigit() } },
                    label = { Text("Country code for phone numbers") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    reviewUrl, { reviewUrl = it },
                    label = { Text("Google review link (shown after a bill)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    apiKey, { apiKey = it },
                    label = { Text("Anthropic API key (for Scan a photo)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Only used on this device, only when you tap Read photo. Get one at console.anthropic.com") }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val result = CsvExporter.exportAll(context, products, bills, purchases, returns, vendors, payments, feedback)
                            statusMsg = if (result.filesWritten.isNotEmpty())
                                "Saved ${result.filesWritten.size} CSV files to ${result.folderLabel}"
                            else "Could not save the CSV files. Try again."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Download CSV of everything") }
                if (statusMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(statusMsg!!, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmErase = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Erase all data") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(
                    shopName.ifBlank { "My cake shop" },
                    taxPct.toDoubleOrNull() ?: 0.0,
                    countryCode,
                    reviewUrl,
                    apiKey
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase all data?") },
            text = { Text("This deletes every item, bill, vendor and payment on this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.eraseAll(); confirmErase = false; onDismiss() }) { Text("Erase everything") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } }
        )
    }
}
