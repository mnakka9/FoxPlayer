package mn.blazeapps.foxplayer

import android.app.Application
import mn.blazeapps.foxplayer.data.AudiobookDatabase
import mn.blazeapps.foxplayer.data.AudiobookRepository
import mn.blazeapps.foxplayer.data.CoverResolver
import mn.blazeapps.foxplayer.data.FolderScanner
import mn.blazeapps.foxplayer.playback.PlaybackManager
import mn.blazeapps.foxplayer.ui.theme.ThemePreferences

class FoxPlayerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playbackManager.connect()
    }
}

class AppContainer(app: Application) {
    private val database = AudiobookDatabase.create(app)
    private val metadata = mn.blazeapps.foxplayer.data.AudioMetadataReader(app)
    private val scanner = FolderScanner(app, metadata)
    private val covers = CoverResolver(app, metadata)
    val themePreferences = ThemePreferences(app)
    val repository = AudiobookRepository(app, database, scanner, covers)
    val playbackManager = PlaybackManager(app, repository)
}
