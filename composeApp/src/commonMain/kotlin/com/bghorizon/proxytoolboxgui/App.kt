package com.bghorizon.proxytoolboxgui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.data.ProxyTestManager
import com.bghorizon.proxytoolboxgui.data.ProxyWebServer
import com.bghorizon.proxytoolboxgui.data.SettingsRepository
import com.bghorizon.proxytoolboxgui.data.SubscriptionRepository
import com.bghorizon.proxytoolboxgui.data.db.AppDatabase
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDatabase
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.di.LocalAppModule
import com.bghorizon.proxytoolboxgui.platform.getPlatform
import com.bghorizon.proxytoolboxgui.ui.screens.MainScreenState
import com.bghorizon.proxytoolboxgui.ui.screens.SettingsScreenState
import com.bghorizon.proxytoolboxgui.ui.screens.SubscriptionsScreenState
import com.bghorizon.proxytoolboxgui.ui.theme.AppTheme
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*

val LocalScaffoldPadding = compositionLocalOf { PaddingValues(0.dp) }
val ScreenPadding = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(appDb: AppDatabase, subDb: SubscriptionDatabase) {
    val platform = remember { getPlatform() }
    val settingsRepository = remember { SettingsRepository(appDb.settingsDao()) }
    val subscriptionRepository = remember { SubscriptionRepository(subDb.subscriptionDao()) }
    val testManager = remember { ProxyTestManager(subscriptionRepository) }
    val webServer = remember { ProxyWebServer() }

    val appModule = remember {
        AppModule(
            settingsRepository = settingsRepository,
            subscriptionRepository = subscriptionRepository,
            testManager = testManager,
            webServer = webServer,
            platform = platform
        )
    }

    val viewModel: MainViewModel = viewModel {
        MainViewModel(appModule)
    }

    val uiState by viewModel.uiState.collectAsState()

    // Autonomous FAB padding calculation:
    // We use remember(uiState.screen::class) to reset the padding to 0.dp whenever the screen changes.
    // This prevents "padding leakage" from previous screens. The measurement logic below 
    // will re-calculate the clearance if the new screen contains a FAB.
    var fabTotalPadding by remember(uiState.screen::class) { mutableStateOf(0.dp) }
    var scaffoldCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val animatedFabPadding by animateDpAsState(fabTotalPadding)

    AppTheme(
        themeMode = uiState.settings.theme,
        dynamicColor = uiState.settings.dynamicColor
    ) {
        CompositionLocalProvider(LocalAppModule provides appModule) {
            Surface {
                BoxWithConstraints {
                    val isCompact = maxWidth < 600.dp
                    val isExpanded = maxWidth >= 900.dp

                    Scaffold(
                        // Track scaffold coordinates to enable relative measurement of the FAB position
                        modifier = Modifier.onGloballyPositioned { scaffoldCoords = it },
                        topBar = { uiState.screen.TopBar(viewModel) },
                        bottomBar = {
                            if (!isExpanded) {
                                NavigationBar(modifier = Modifier.height(80.dp)) {
                                    AdaptiveNavigationItem(
                                        label = stringResource(Res.string.home),
                                        icon = Icons.Default.Home,
                                        selected = uiState.screen is MainScreenState,
                                        onClick = { viewModel.navigateTo(MainScreenState()) },
                                        isCompact = isCompact
                                    )
                                    AdaptiveNavigationItem(
                                        label = stringResource(Res.string.subscriptions),
                                        icon = Icons.AutoMirrored.Filled.List,
                                        selected = uiState.screen is SubscriptionsScreenState,
                                        onClick = { viewModel.navigateTo(SubscriptionsScreenState()) },
                                        isCompact = isCompact
                                    )
                                    AdaptiveNavigationItem(
                                        label = stringResource(Res.string.title_settings),
                                        icon = Icons.Default.Settings,
                                        selected = uiState.screen is SettingsScreenState,
                                        onClick = { viewModel.navigateTo(SettingsScreenState()) },
                                        isCompact = isCompact
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            // Use a container Box to autonomously measure FAB clearance without screen-specific knowledge.
                            // If no FAB is rendered, size becomes 0 and padding resets.
                            Box(
                                modifier = Modifier.onGloballyPositioned { fabCoords ->
                                    scaffoldCoords?.let { sc ->
                                        if (sc.isAttached && fabCoords.isAttached) {
                                            if (fabCoords.size.height > 0) {
                                                // Calculate clearance: Distance from FAB top to Scaffold bottom.
                                                // This includes FAB height, margins, and bottom bar height.
                                                val fabTopInScaffold =
                                                    sc.localPositionOf(fabCoords, Offset.Zero).y
                                                fabTotalPadding =
                                                    with(density) { (sc.size.height - fabTopInScaffold).toDp() }
                                            } else {
                                                fabTotalPadding = 0.dp
                                            }
                                        }
                                    }
                                }
                            ) {
                                uiState.screen.FAB(viewModel)
                            }
                        }
                    ) { padding ->
                        val layoutDirection = LocalLayoutDirection.current
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    start = padding.calculateStartPadding(layoutDirection),
                                    end = padding.calculateEndPadding(layoutDirection)
                                )
                        ) {
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
                                    Spacer(Modifier.height(padding.calculateTopPadding()))
                                    NavigationDrawerItem(
                                        label = { Text(stringResource(Res.string.home)) },
                                        icon = { Icon(Icons.Default.Home, null) },
                                        selected = uiState.screen is MainScreenState,
                                        onClick = { viewModel.navigateTo(MainScreenState()) },
                                        modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                    )
                                    navRailSpacer()
                                    NavigationDrawerItem(
                                        label = { Text(stringResource(Res.string.subscriptions)) },
                                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                        selected = uiState.screen is SubscriptionsScreenState,
                                        onClick = { viewModel.navigateTo(SubscriptionsScreenState()) },
                                        modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                    )
                                    navRailSpacer()
                                    NavigationDrawerItem(
                                        label = { Text(stringResource(Res.string.title_settings)) },
                                        icon = { Icon(Icons.Default.Settings, null) },
                                        selected = uiState.screen is SettingsScreenState,
                                        onClick = { viewModel.navigateTo(SettingsScreenState()) },
                                        modifier = Modifier.padding(horizontal = navDrawItemHorizontalPadding)
                                    )
                                }
                            }
                            Box(Modifier.fillMaxSize().weight(1f)) {
                                // Inject adjusted padding into LocalScaffoldPadding.
                                // All screens use this to ensure content clears both the bottom bar and the FAB.
                                val adjustedPadding = PaddingValues(
                                    start = padding.calculateStartPadding(layoutDirection),
                                    top = padding.calculateTopPadding(),
                                    end = padding.calculateEndPadding(layoutDirection),
                                    bottom = maxOf(
                                        padding.calculateBottomPadding(),
                                        animatedFabPadding
                                    )
                                )
                                CompositionLocalProvider(LocalScaffoldPadding provides adjustedPadding) {
                                    uiState.screen.Content(viewModel)
                                }
                            }
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
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
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
