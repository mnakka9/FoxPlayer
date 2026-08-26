package mn.blazeapps.foxplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity(null))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persist()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persist()
                val bookId = PlaybackManager.bookIdFromMediaId(mediaItem?.mediaId)
                    ?: mediaItem?.mediaMetadata?.extras
                        ?.getLong(PlaybackManager.EXTRA_BOOK_ID)
                        ?.takeIf { it != 0L }
                session?.setSessionActivity(sessionActivity(bookId))
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        persist()
        session?.release()
        player?.release()
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun sessionActivity(bookId: Long?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (bookId != null) {
                putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, bookId)
            }
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun persist() {
        val app = application as? FoxPlayerApplication ?: return
        scope.launch {
            app.container.playbackManager.persistProgress()
        }
    }
}
