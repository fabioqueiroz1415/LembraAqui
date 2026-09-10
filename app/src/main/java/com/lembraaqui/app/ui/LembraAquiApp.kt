package com.lembraaqui.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lembraaqui.app.MainViewModel
import com.lembraaqui.app.PermissionStatus
import com.lembraaqui.app.PermissionStatusReader

@Composable
fun LembraAquiApp(openPlaceId: String?, openReminderId: String?, onOpenPlaceConsumed: () -> Unit, vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(PermissionStatusReader.read(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionStatus = PermissionStatusReader.read(context)
        if (permissionStatus.geofencingReady) vm.syncMonitoring()
    }

    LaunchedEffect(openPlaceId, openReminderId) {
        if (openReminderId != null) {
            nav.navigate("reminder/${Uri.encode(openReminderId)}") {
                popUpTo("home")
                launchSingleTop = true
            }
            onOpenPlaceConsumed()
        } else if (openPlaceId != null) {
            // Compatibilidade com notificações publicadas antes desta atualização.
            nav.navigate("place/${Uri.encode(openPlaceId)}") { launchSingleTop = true }
            onOpenPlaceConsumed()
        }
    }

    val refreshPermissions = { permissionStatus = PermissionStatusReader.read(context) }

    Scaffold { padding ->
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    permissionStatus = permissionStatus,
                    contentPadding = padding,
                    onAddPlace = { nav.navigate("place-edit/new") },
                    onOpenPlace = { nav.navigate("place/$it") },
                    onHistory = { nav.navigate("history") },
                    onPermissions = { nav.navigate("permissions") }
                )
            }
            composable(
                "place/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PlaceDetailScreen(
                    vm = vm,
                    placeId = id,
                    contentPadding = padding,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("place-edit/$id") },
                    onAddReminder = { nav.navigate("reminder-edit/$id/new") },
                    onOpenReminder = { nav.navigate("reminder/${Uri.encode(it)}") },
                    onDebug = { nav.navigate("debug/$id") }
                )
            }
            composable(
                "place-edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                PlaceEditScreen(
                    vm = vm,
                    placeId = entry.arguments?.getString("id") ?: "new",
                    contentPadding = padding,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        if (nav.previousBackStackEntry?.destination?.route?.startsWith("place/") == true) nav.popBackStack()
                        else {
                            nav.navigate("place/$id") {
                                popUpTo("home")
                            }
                        }
                    },
                    onNeedPermission = { nav.navigate("permissions") }
                )
            }
            composable(
                "reminder/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                ReminderDetailScreen(
                    vm = vm,
                    reminderId = id,
                    contentPadding = padding,
                    onBack = { nav.popBackStack() },
                    onEdit = { placeId -> nav.navigate("reminder-edit/${Uri.encode(placeId)}/${Uri.encode(id)}") }
                )
            }
            composable(
                "reminder-edit/{placeId}/{id}",
                arguments = listOf(
                    navArgument("placeId") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType }
                )
            ) { entry ->
                ReminderEditScreen(
                    vm = vm,
                    placeId = entry.arguments?.getString("placeId") ?: return@composable,
                    reminderId = entry.arguments?.getString("id") ?: "new",
                    contentPadding = padding,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() }
                )
            }
            composable("history") {
                HistoryScreen(vm, padding, onBack = { nav.popBackStack() })
            }
            composable("permissions") {
                PermissionScreen(
                    status = permissionStatus,
                    contentPadding = padding,
                    onBack = { nav.popBackStack() },
                    onRefresh = refreshPermissions,
                    onReady = { vm.syncMonitoring() }
                )
            }
            composable(
                "debug/{placeId}",
                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
            ) { entry ->
                DebugToolsScreen(
                    vm = vm,
                    placeId = entry.arguments?.getString("placeId") ?: return@composable,
                    contentPadding = padding,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun PermissionLaunchers(
    status: PermissionStatus,
    onRefresh: () -> Unit,
    onReady: () -> Unit,
    content: @Composable (
        requestForeground: () -> Unit,
        requestBackground: () -> Unit,
        requestNotifications: () -> Unit,
        openLocationSettings: () -> Unit,
        openAppSettings: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onRefresh()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRefresh(); onReady()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRefresh()
    }
    val appSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onRefresh(); onReady()
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onRefresh(); onReady()
    }

    content(
        {
            foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        },
        {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                appSettingsLauncher.launch(intent)
            } else onReady()
        },
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                appSettingsLauncher.launch(intent)
            }
        },
        {
            locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        {
            appSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    )
}
