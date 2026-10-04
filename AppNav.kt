package com.cakeshop.manager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cakeshop.manager.data.AppViewModel
import com.cakeshop.manager.data.addDaysStr
import com.cakeshop.manager.data.formatDateShort
import com.cakeshop.manager.data.todayStr
import com.cakeshop.manager.ui.screens.*
import kotlinx.coroutines.launch

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val NAV_ITEMS = listOf(
    NavItem("dashboard", "Dashboard", Icons.Filled.Dashboard),
    NavItem("pos", "New bill", Icons.Filled.PointOfSale),
    NavItem("bills", "Bills", Icons.Filled.Receipt),
    NavItem("inventory", "Inventory", Icons.Filled.Inventory2),
    NavItem("stockin", "Stock in", Icons.Filled.LocalShipping),
    NavItem("returns", "Returns", Icons.Filled.AssignmentReturn),
    NavItem("feedback", "Feedback", Icons.Filled.Star),
    NavItem("vendors", "Vendors", Icons.Filled.Store)
)

/** Screens where a global date-picker in the top bar makes sense (everything
 *  except billing itself, which is always "today"). */
val DATED_ROUTES = setOf("dashboard", "bills", "inventory", "stockin", "returns", "feedback")

@Composable
fun AppRoot(vm: AppViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var selectedDate by remember { mutableStateOf(todayStr()) }
    var showSettings by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"
    val settings by vm.settings.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "\uD83C\uDF82  ${settings.shopName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                NAV_ITEMS.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { showSettings = true; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    title = { Text(NAV_ITEMS.find { it.route == currentRoute }?.label ?: "Cake Shop Manager") },
                    actions = {
                        if (currentRoute in DATED_ROUTES) {
                            IconButton(onClick = { selectedDate = addDaysStr(selectedDate, -1) }) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                            }
                            Text(formatDateShort(selectedDate), modifier = Modifier.align(Alignment.CenterVertically))
                            IconButton(onClick = { selectedDate = addDaysStr(selectedDate, 1) }) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                            }
                            if (selectedDate != todayStr()) {
                                TextButton(onClick = { selectedDate = todayStr() }) { Text("Today") }
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(vm, selectedDate) }
                    composable("pos") { PosScreen(vm) }
                    composable("bills") { BillsScreen(vm, selectedDate) }
                    composable("inventory") { InventoryScreen(vm, selectedDate) }
                    composable("stockin") { StockInScreen(vm, selectedDate) }
                    composable("returns") { ReturnsScreen(vm, selectedDate) }
                    composable("feedback") { FeedbackScreen(vm, selectedDate) }
                    composable("vendors") { VendorsScreen(vm, selectedDate) }
                }
            }
            if (showSettings) {
                SettingsDialog(vm, onDismiss = { showSettings = false })
            }
        }
    }
}
