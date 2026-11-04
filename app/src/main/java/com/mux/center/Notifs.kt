package com.mux.center

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class NotifAction(val title: String, val intent: PendingIntent)

class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val bigText: String,
    val time: Long,
    val icon: ImageBitmap?,
    val avatar: ImageBitmap?,
    val intent: PendingIntent?,
    val actions: List<NotifAction>,
    val group: Int,            // 0 conversation, 1 notification, 2 silent
    val clearable: Boolean,
    val ongoing: Boolean,
    val progress: Int          // -1 when there is no progress bar
)

object Notifs {
    var items by mutableStateOf<List<NotifItem>>(emptyList())
    var connected by mutableStateOf(false)
    var listener: CenterNotificationListener? = null

    fun dismiss(key: String) {
        try { listener?.cancelNotification(key) } catch (e: Exception) { }
    }

    fun dismissAll() {
        try { listener?.cancelAllNotifications() } catch (e: Exception) { }
    }
}

class CenterNotificationListener : NotificationListenerService() {
    private val names = HashMap<String, String>()

    override fun onListenerConnected() {
        Notifs.listener = this
        Notifs.connected = true
        refresh()
        Media.start(this)
    }

    override fun onListenerDisconnected() {
        Notifs.connected = false
        Notifs.listener = null
        Media.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val arr: Array<StatusBarNotification>? = try { activeNotifications } catch (e: Exception) { null }
        if (arr == null) return
        Notifs.items = arr.mapNotNull { build(it) }.sortedByDescending { it.time }
    }

    private fun appName(pkg: String): String = names.getOrPut(pkg) {
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
    }

    private fun build(sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        if (title.isBlank() && text.isBlank()) return null

        val rank = NotificationListenerService.Ranking()
        val importance = try {
            if (currentRanking.getRanking(sbn.key, rank)) rank.importance else NotificationManager_DEFAULT
        } catch (e: Exception) { NotificationManager_DEFAULT }
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val conversation = n.category == Notification.CATEGORY_MESSAGE || template.contains("MessagingStyle")
        val ongoing = sbn.isOngoing
        val group = when {
            ongoing || importance <= 2 -> 2
            conversation -> 0
            else -> 1
        }
        val icon = try { n.smallIcon?.loadDrawable(this)?.toBitmap(48, 48)?.asImageBitmap() } catch (e: Exception) { null }
        val avatar = try { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96)?.asImageBitmap() } catch (e: Exception) { null }
        val actions = ArrayList<NotifAction>()
        n.actions?.forEach { a ->
            val pi = a.actionIntent
            val label = a.title?.toString()
            val needsInput = a.remoteInputs != null && a.remoteInputs.isNotEmpty()
            if (pi != null && !label.isNullOrBlank() && !needsInput) actions.add(NotifAction(label, pi))
        }
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = if (max > 0) (ex.getInt(Notification.EXTRA_PROGRESS, 0) * 100 / max) else -1
        return NotifItem(
            sbn.key, sbn.packageName, appName(sbn.packageName), title, text, big, sbn.postTime,
            icon, avatar, n.contentIntent, actions, group, sbn.isClearable, ongoing, progress
        )
    }

    private companion object {
        const val NotificationManager_DEFAULT = 3
    }
}

/** Now playing card for the Control Center. */
object Media {
    var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)

    private var controller: MediaController? = null
    private var msm: MediaSessionManager? = null
    private var component: ComponentName? = null

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { update() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { update() }
        override fun onSessionDestroyed() { pick(null) }
    }

    fun start(ctx: Context) {
        try {
            val m = ctx.getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(ctx, CenterNotificationListener::class.java)
            msm = m
            component = cn
            m.addOnActiveSessionsChangedListener(sessions, cn)
            pick(m.getActiveSessions(cn))
        } catch (e: Exception) { }
    }

    fun stop() {
        try { msm?.removeOnActiveSessionsChangedListener(sessions) } catch (e: Exception) { }
        controller?.unregisterCallback(callback)
        controller = null
        active = false
    }

    private fun pick(list: List<MediaController>?) {
        controller?.unregisterCallback(callback)
        controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        controller?.registerCallback(callback)
        update()
    }

    private fun update() {
        val c = controller
        if (c == null) {
            active = false
            return
        }
        val md = c.metadata
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art = bmp?.asImageBitmap()
        playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        active = title.isNotBlank() || artist.isNotBlank()
    }

    fun toggle() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
}
