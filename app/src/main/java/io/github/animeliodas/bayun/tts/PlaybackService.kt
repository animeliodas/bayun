package io.github.animeliodas.bayun.tts

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.animeliodas.bayun.MainActivity
import io.github.animeliodas.bayun.data.Prefs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * «Плеер» для системы: отдаёт чтение вслух медиа-сессии — уведомление с кнопками, экран блокировки,
 * кнопки гарнитуры и часов. Само чтение — в ReadAloud.
 */
@OptIn(UnstableApi::class)
class TtsPlayer(looper: Looper) : SimpleBasePlayer(looper) {
    private var st = ReadAloud.state.value

    fun update(s: ReadAloud.State) {
        st = s
        invalidateState()
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE, Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_RELEASE,
        ).build()
        val b = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(st.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackParameters(PlaybackParameters(Prefs.settings.value.ttsSpeed))
        val book = st.book ?: return b.setPlaybackState(Player.STATE_IDLE).build()
        val meta = MediaMetadata.Builder()
            .setTitle(st.chapterTitle.ifBlank { st.bookTitle })
            .setArtist(st.bookTitle)
            .setAlbumTitle(st.bookTitle)
            .apply { st.cover?.let { setArtworkUri(Uri.fromFile(File(it))) } }
            .build()
        val id = "$book#${st.chapter}"
        val item = MediaItem.Builder().setMediaId(id).setMediaMetadata(meta).build()
        val data = MediaItemData.Builder(id).setMediaItem(item).setDurationUs(C.TIME_UNSET).build()
        return b.setPlaylist(listOf(data))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(if (st.loading) Player.STATE_BUFFERING else Player.STATE_READY)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) ReadAloud.play() else ReadAloud.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> ReadAloud.nextChapter()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> ReadAloud.prevChapter()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        Prefs.update { it.copy(ttsSpeed = playbackParameters.speed) }
        ReadAloud.onParamsChanged()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        ReadAloud.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val player = TtsPlayer(Looper.getMainLooper())
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        scope.launch { ReadAloud.state.collect { player.update(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // приложение смахнули из списка задач: если читаем — продолжаем, иначе выключаемся
        if (!ReadAloud.state.value.playing) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
