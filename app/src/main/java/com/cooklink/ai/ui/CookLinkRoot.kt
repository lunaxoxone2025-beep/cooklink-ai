@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cooklink.ai.AppContainer
import com.cooklink.ai.ui.screens.HomeScreen
import com.cooklink.ai.ui.screens.ImportScreen
import com.cooklink.ai.ui.screens.ProfileScreen
import com.cooklink.ai.ui.screens.RecipeDetailScreen
import com.cooklink.ai.ui.screens.RecipeEditorScreen
import com.cooklink.ai.ui.screens.RecipesScreen
import com.cooklink.ai.ui.screens.ShoppingScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val HOME = "home"
    const val RECIPES = "recipes"
    const val IMPORT = "import"
    const val SHOPPING = "shopping"
    const val PROFILE = "profile"
    const val DETAIL = "recipe/{id}"
    const val EDIT = "edit/{id}"
    fun detail(id: Long) = "recipe/$id"
    fun edit(id: Long = 0) = "edit/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.RECIPES, "Recipes", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
    Tab(Routes.IMPORT, "Import", Icons.Filled.Add, Icons.Filled.Add),
    Tab(Routes.SHOPPING, "Shopping", Icons.Outlined.ShoppingBasket, Icons.Filled.ShoppingBasket),
    Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun CookLinkRoot(
    container: AppContainer,
    sharedText: StateFlow<String?>,
    onSharedTextConsumed: () -> Unit,
    sharedMedia: StateFlow<List<android.net.Uri>?>,
    onSharedMediaConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }
    val shared by sharedText.collectAsState()
    val media by sharedMedia.collectAsState()

    // Anything shared from another app jumps straight to Import.
    LaunchedEffect(shared, media) {
        if (shared != null || media != null) nav.switchTab(Routes.IMPORT)
    }

    // When an AI import finishes, open the new recipe wherever the user is in the app.
    val importState by container.imports.state.collectAsState()
    LaunchedEffect(importState) {
        val s = importState
        if (s is com.cooklink.ai.data.importer.ImportState.Done && !s.opened) {
            container.imports.markOpened()
            nav.navigate(Routes.detail(s.recipeId))
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                BottomBar(route) { nav.switchTab(it) }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    openRecipe = { nav.navigate(Routes.detail(it)) },
                    openImport = { nav.switchTab(Routes.IMPORT) },
                    openRecipes = { nav.switchTab(Routes.RECIPES) },
                )
            }
            composable(Routes.RECIPES) {
                RecipesScreen(
                    container = container,
                    openRecipe = { nav.navigate(Routes.detail(it)) },
                    createRecipe = { nav.navigate(Routes.edit()) },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    container = container,
                    sharedText = shared,
                    onSharedTextConsumed = onSharedTextConsumed,
                    sharedMedia = media,
                    onSharedMediaConsumed = onSharedMediaConsumed,
                    writeManually = { nav.navigate(Routes.edit()) },
                    openRecipe = { nav.navigate(Routes.detail(it)) },
                    openProfile = { nav.switchTab(Routes.PROFILE) },
                )
            }
            composable(Routes.SHOPPING) { ShoppingScreen(container = container) }
            composable(Routes.PROFILE) { ProfileScreen(container = container) }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RecipeDetailScreen(
                    container = container,
                    recipeId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RecipeEditorScreen(
                    container = container,
                    recipeId = id,
                    onClose = { nav.popBackStack() },
                    onSaved = { savedId ->
                        nav.popBackStack()
                        if (id == 0L) nav.navigate(Routes.detail(savedId))
                    },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            if (tab.route == Routes.IMPORT) {
                // Import is the app's main action: a raised, always-visible button in the middle.
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(tab.icon, contentDescription = "Import a recipe", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            } else {
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        }
    }
}
