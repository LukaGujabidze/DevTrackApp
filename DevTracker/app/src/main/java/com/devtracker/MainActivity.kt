package com.devtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.devtracker.ui.*

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val vm: AppVm = viewModel()
            val dark = when (vm.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme(); else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { Surface { Root(vm) } }
        }
    }
}

@Composable fun Root(vm: AppVm) {
    val nav = rememberNavController()
    val tabs = listOf(Triple("home", "Home", Icons.Default.Home), Triple("goals", "Goals", Icons.Default.Flag),
        Triple("learning", "Learning", Icons.Default.School), Triple("analytics", "Analytics", Icons.Default.BarChart), Triple("profile", "Profile", Icons.Default.Person))
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        if (route in tabs.map { it.first }) NavigationBar {
            tabs.forEach { (r, label, icon) -> NavigationBarItem(route == r, {
                nav.navigate(r) { popUpTo("home"); launchSingleTop = true }
            }, { Icon(icon, label) }, label = { Text(label) }) }
        }
    }) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("goals") { GoalsScreen(vm, nav) }
            composable("learning") { LearningScreen(vm, nav) }
            composable("analytics") { AnalyticsScreen(vm) }
            composable("profile") { ProfileScreen(vm, nav) }
            composable("calendar") { CalendarScreen(vm, nav) }
            composable("roadmap") { RoadmapScreen(vm, nav) }
            composable("achievements") { AchievementsScreen(vm, nav) }
            composable("search") { SearchScreen(vm, nav) }
            composable("goal/{id}") { GoalDetail(vm, it.arguments!!.getString("id")!!.toLong(), nav) }
            composable("course/{id}") { CourseDetail(vm, it.arguments!!.getString("id")!!.toLong(), nav) }
        }
    }
}
