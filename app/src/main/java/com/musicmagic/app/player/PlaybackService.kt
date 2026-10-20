package com.musicmagic.app.player

import android.graphics.Bitmap
import android.net.Uri
import java.lang.ref.WeakReference
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicmagic.app.library.LibraryMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Media3 foreground playback plus lock-screen artwork.
 * Only the active item is decoded; a 192px JPEG keeps IPC/session payloads small.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        private var livePlayer: WeakReference<ExoPlayer>? = null

        /**
         * Called on the app's main thread BEFORE writing/renaming files.
         * Clearing references from the queue prevents stale file descriptors
         * and accidental playback while tags are being replaced on disk.
         * An empty collection is used for Undo, where the target may be renamed.
         */
        fun stopForEditing(uris: Collection<Uri>) {
            val player = livePlayer?.get() ?: return
            val target = uris.mapTo(mutableSetOf()) { it.toString() }
            if (target.isEmpty() || (0 until player.mediaItemCount).any {
                    player.getMediaItemAt(it).mediaId in target
                }) {
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artJob: Job? = null
    private val artworkListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshArtwork(mediaItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .build()
        livePlayer = WeakReference(player)
        player.addListener(artworkListener)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun refreshArtwork(item: MediaItem?) {
        artJob?.cancel()
        if (item?.mediaMetadata?.artworkData != null) return
        val uri = item?.localConfiguration?.uri ?: return
        val id = item.mediaId
        artJob = scope.launch {
            val image = withContext(Dispatchers.IO) {
                runCatching {
                    val original = LibraryMedia.cover(this@PlaybackService, uri).bitmap ?: return@runCatching null
                    val scaled = if (original.width > 192 || original.height > 192) {
                        val ratio = minOf(192f / original.width, 192f / original.height)
                        Bitmap.createScaledBitmap(
                            original, (original.width * ratio).toInt().coerceAtLeast(1),
                            (original.height * ratio).toInt().coerceAtLeast(1), true,
                        )
                    } else original
                    try {
                        ByteArrayOutputStream().use { output ->
                            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 78, output)) null
                            else output.toByteArray().takeIf { it.size <= 256 * 1024 }
                        }
                    } finally {
                        if (scaled !== original) scaled.recycle()
                    }
                }.getOrNull()
            } ?: return@launch

            val player = mediaSession?.player ?: return@launch
            val index = player.currentMediaItemIndex
            val current = player.currentMediaItem ?: return@launch
            if (index !in 0 until player.mediaItemCount || current.mediaId != id) return@launch
            if (current.mediaMetadata.artworkData != null) return@launch
            // Preserve playback position and the existing URI, changing metadata only.
            val updated = current.buildUpon()
                .setMediaMetadata(current.mediaMetadata.buildUpon()
                    .setArtworkData(image, 3) // ID3 APIC front-cover picture type
                    .build())
                .build()
            player.replaceMediaItem(index, updated)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        livePlayer = null
        artJob?.cancel()
        scope.cancel()
        mediaSession?.let {
            it.player.removeListener(artworkListener)
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
