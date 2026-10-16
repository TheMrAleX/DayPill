package app.pastillero.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pastillero.R
import app.pastillero.data.MedicationRepository
import app.pastillero.ui.today.TodayScreen
import app.pastillero.ui.meds.MedicationsScreen
import app.pastillero.ui.meds.MedicationEditorScreen
import app.pastillero.ui.history.HistoryScreen

private enum class TopLevelDestination(val route: String, @param:StringRes val label: Int) {
    TODAY(Routes.TODAY, R.string.today),
    MEDICATIONS(Routes.MEDICATIONS, R.string.medications),
    HISTORY(Routes.HISTORY, R.string.history),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastilleroNavHost(repository: MedicationRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.TODAY
    val topLevel = TopLevelDestination.entries.any { it.route == route }
    val secondaryTitle = when (route) {
        Routes.SETTINGS -> R.string.settings
        else -> null
    }

    Scaffold(
        contentWindowInsets = if (route == Routes.NEW_MEDICATION || route == Routes.EDIT_MEDICATION) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        topBar = {
            if (secondaryTitle != null) {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    title = { Text(stringResource(secondaryTitle), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(24.dp))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (topLevel) {
                NavigationBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.label)
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { DestinationIcon(destination, label) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Routes.MEDICATIONS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.NEW_MEDICATION) },
                    modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_medication),
                        modifier = Modifier.size(24.dp))
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.TODAY,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(repository, onSettings = { navController.navigate(Routes.SETTINGS) },
                    onAdd = { navController.navigate(Routes.NEW_MEDICATION) })
            }
            composable(Routes.MEDICATIONS) {
                MedicationsScreen(repository) { id -> navController.navigate(Routes.editMedication(id)) }
            }
            composable(Routes.HISTORY) { HistoryScreen(repository) }
            composable(Routes.NEW_MEDICATION) {
                MedicationEditorScreen(repository, null) { navController.navigateUp() }
            }
            composable(Routes.EDIT_MEDICATION, arguments = listOf(navArgument("medicationId") { type = NavType.LongType })) { entry ->
                MedicationEditorScreen(repository, requireNotNull(entry.arguments).getLong("medicationId")) { navController.navigateUp() }
            }
            composable(Routes.SETTINGS) { Box(Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination, label: String) {
    val modifier = Modifier.size(24.dp)
    when (destination) {
        TopLevelDestination.TODAY -> Icon(Icons.Default.CheckCircle, label, modifier)
        TopLevelDestination.MEDICATIONS -> Icon(painterResource(R.drawable.ic_capsule), label, modifier)
        TopLevelDestination.HISTORY -> Icon(Icons.Default.DateRange, label, modifier)
    }
}
