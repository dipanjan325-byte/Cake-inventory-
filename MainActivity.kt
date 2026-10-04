package com.cakeshop.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.cakeshop.manager.data.AppViewModel
import com.cakeshop.manager.ui.AppRoot
import com.cakeshop.manager.ui.CakeShopTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CakeShopTheme {
                AppRoot(viewModel)
            }
        }
    }
}
