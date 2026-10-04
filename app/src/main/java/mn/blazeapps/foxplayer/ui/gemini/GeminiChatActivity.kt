package mn.blazeapps.foxplayer.ui.gemini

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.ui.theme.FoxPlayerTheme
import mn.blazeapps.foxplayer.ui.theme.GlassBackground
import mn.blazeapps.foxplayer.ui.theme.ThemeMode

class GeminiChatActivity : ComponentActivity() {
    private val viewModel: GeminiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L).takeIf { it > 0 }
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE)
        val bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR)

        viewModel.setBookContext(bookId, bookTitle, bookAuthor)

        val app = application as FoxPlayerApplication
        setContent {
            val themeMode by app.container.themePreferences.mode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
            }

            FoxPlayerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    if (isDark) {
                        GlassBackground {
                            GeminiChatScreen(
                                viewModel = viewModel,
                                onBack = { finish() },
                            )
                        }
                    } else {
                        GeminiChatScreen(
                            viewModel = viewModel,
                            onBack = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_BOOK_AUTHOR = "extra_book_author"

        fun createIntent(
            context: Context,
            bookId: Long?,
            bookTitle: String?,
            bookAuthor: String?,
        ): Intent {
            return Intent(context, GeminiChatActivity::class.java).apply {
                if (bookId != null && bookId > 0) putExtra(EXTRA_BOOK_ID, bookId)
                if (!bookTitle.isNullOrBlank()) putExtra(EXTRA_BOOK_TITLE, bookTitle)
                if (!bookAuthor.isNullOrBlank()) putExtra(EXTRA_BOOK_AUTHOR, bookAuthor)
            }
        }
    }
}
