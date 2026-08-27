package mn.blazeapps.foxplayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mn.blazeapps.foxplayer.ui.book.BookDetailScreen
import mn.blazeapps.foxplayer.ui.book.BookViewModel
import mn.blazeapps.foxplayer.ui.library.LibraryScreen
import mn.blazeapps.foxplayer.ui.library.LibraryViewModel
import mn.blazeapps.foxplayer.ui.theme.FoxPlayerTheme
import mn.blazeapps.foxplayer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val bookViewModel: BookViewModel by viewModels()
    private val pendingBookId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingBookId.value = intent.bookIdExtra()
        enableEdgeToEdge()
        val app = application as FoxPlayerApplication
        setContent {
            val themeMode by app.container.themePreferences.mode.collectAsStateWithLifecycle()
            FoxPlayerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val openBookId by pendingBookId.collectAsStateWithLifecycle()
                    FoxPlayerNav(
                        libraryViewModel = libraryViewModel,
                        bookViewModel = bookViewModel,
                        themeMode = themeMode,
                        onThemeModeChange = app.container.themePreferences::setMode,
                        openBookId = openBookId,
                        onOpenBookConsumed = { pendingBookId.value = null },
                        onPickedFolder = { uri, rebindId ->
                            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            try {
                                contentResolver.takePersistableUriPermission(uri, flags)
                            } catch (_: SecurityException) {
                            }
                            libraryViewModel.importFolder(uri, rebindId)
                        },
                        onRequestNotifications = { requestNotificationPermission() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBookId.value = intent.bookIdExtra()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PermissionChecker.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    companion object {
        const val EXTRA_OPEN_BOOK_ID = "open_book_id"
    }
}

private fun Intent.bookIdExtra(): Long? =
    getLongExtra(MainActivity.EXTRA_OPEN_BOOK_ID, -1L).takeIf { it > 0 }

@Composable
private fun FoxPlayerNav(
    libraryViewModel: LibraryViewModel,
    bookViewModel: BookViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    openBookId: Long?,
    onOpenBookConsumed: () -> Unit,
    onPickedFolder: (Uri, Long?) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val navController = rememberNavController()
    var rebindBookId by remember { mutableStateOf<Long?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onRequestNotifications()
            onPickedFolder(uri, rebindBookId)
        }
        rebindBookId = null
    }

    LaunchedEffect(openBookId) {
        val id = openBookId ?: return@LaunchedEffect
        navController.navigate("book/$id") {
            launchSingleTop = true
        }
        onOpenBookConsumed()
    }

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                viewModel = libraryViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenBook = { id -> navController.navigate("book/$id") },
                onAddFolder = {
                    rebindBookId = null
                    folderPicker.launch(null)
                },
            )
        }
        composable(
            route = "book/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                viewModel = bookViewModel,
                onBack = { navController.popBackStack() },
                onRestoreAccess = {
                    rebindBookId = bookId
                    folderPicker.launch(null)
                },
            )
        }
    }
}
