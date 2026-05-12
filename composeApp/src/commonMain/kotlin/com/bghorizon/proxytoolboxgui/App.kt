package com.bghorizon.proxytoolboxgui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.data.db.AppDatabase
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.SettingsRepository
import com.bghorizon.proxytoolboxgui.data.SubscriptionRepository
import com.bghorizon.proxytoolboxgui.data.ProxyTestManager
import com.bghorizon.proxytoolboxgui.data.ProxyWebServer
import com.bghorizon.proxytoolboxgui.platform.getPlatform
import com.bghorizon.proxytoolboxgui.ui.screens.*
import com.bghorizon.proxytoolboxgui.ui.theme.AppTheme
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.Screen
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(appDb: AppDatabase, subDb: SubscriptionDatabase) {
    val platform = remember { getPlatform() }
    val settingsRepository = remember { SettingsRepository(appDb.settingsDao()) }
    val subscriptionRepository = remember { SubscriptionRepository(subDb.subscriptionDao()) }
    val testManager = remember { ProxyTestManager(subscriptionRepository) }
    val webServer = remember { ProxyWebServer() }

    LaunchedEffect(Unit) {
        settingsRepository.loadSettings()
    }

    val viewModel: MainViewModel = viewModel {
        MainViewModel(platform, settingsRepository, subscriptionRepository, testManager, webServer)
    }

    val uiState by viewModel.uiState.collectAsState()

    AppTheme(
        themeMode = uiState.settings.theme,
        dynamicColor = uiState.settings.dynamicColor
    ) {
        Surface {
            BoxWithConstraints {
                val isCompact = maxWidth < 600.dp
                val isExpanded = maxWidth >= 900.dp

                Scaffold(
                    topBar = {
                        when (uiState.currentScreen) {
                            Screen.Main -> MainTopBar(viewModel)
                            Screen.Subscriptions -> SubscriptionsTopBar(viewModel)
                            Screen.Settings -> SettingsTopBar()
                        }
                    },
                    bottomBar = {
                        if (!isExpanded) {
                            NavigationBar(modifier = Modifier.height(80.dp)) {
                                AdaptiveNavigationItem(
                                    label = stringResource(Res.string.home),
                                    icon = Icons.Default.Home,
                                    selected = uiState.currentScreen == Screen.Main,
                                    onClick = { viewModel.navigateTo(Screen.Main) },
                                    isCompact = isCompact
                                )
                                AdaptiveNavigationItem(
                                    label = stringResource(Res.string.subscriptions),
                                    icon = Icons.AutoMirrored.Filled.List,
                                    selected = uiState.currentScreen == Screen.Subscriptions,
                                    onClick = { viewModel.navigateTo(Screen.Subscriptions) },
                                    isCompact = isCompact
                                )
                                AdaptiveNavigationItem(
                                    label = stringResource(Res.string.title_settings),
                                    icon = Icons.Default.Settings,
                                    selected = uiState.currentScreen == Screen.Settings,
                                    onClick = { viewModel.navigateTo(Screen.Settings) },
                                    isCompact = isCompact
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        when (uiState.currentScreen) {
                            Screen.Main -> MainFAB(viewModel)
                            Screen.Subscriptions -> SubscriptionsFAB(viewModel)
                            else -> {}
                        }
                    }
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        if (isExpanded) {
                            val navDrawItemHorizontalPadding = 12.dp
                            val navRailItemsSpacerHeight = navDrawItemHorizontalPadding / 2
                            val navRailSpacer: @Composable () -> Unit = {
                                Spacer(Modifier.height(navRailItemsSpacerHeight))
                            }
                            NavigationRail(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets(0, 0, 0, 0)
                            ) {
                                NavigationDrawerItem(
                                    label = { Text(stringResource(Res.string.home)) },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    selected = uiState.currentScreen == Screen.Main,
                                    onClick = { viewModel.navigateTo(Screen.Main) },
                                    modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                )
                                navRailSpacer()
                                NavigationDrawerItem(
                                    label = { Text(stringResource(Res.string.subscriptions)) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    selected = uiState.currentScreen == Screen.Subscriptions,
                                    onClick = { viewModel.navigateTo(Screen.Subscriptions) },
                                    modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                )
                                navRailSpacer()
                                NavigationDrawerItem(
                                    label = { Text(stringResource(Res.string.title_settings)) },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    selected = uiState.currentScreen == Screen.Settings,
                                    onClick = { viewModel.navigateTo(Screen.Settings) },
                                    modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize().weight(1f)) {
                            AppScreenContent(uiState.currentScreen, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.AdaptiveNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    isCompact: Boolean
) {
    if (isCompact) {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = { Icon(icon, null) },
            label = { Text(label) },
            modifier = Modifier.weight(1f)
        )
    } else {
        val contentColor =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        val containerColor =
            if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(containerColor)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, tint = contentColor)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun AppScreenContent(currentScreen: Screen, viewModel: MainViewModel) {
    when (currentScreen) {
        Screen.Main -> MainScreen(viewModel)
        Screen.Subscriptions -> SubscriptionsScreen(viewModel)
        Screen.Settings -> SettingsScreen(viewModel)
    }
}
