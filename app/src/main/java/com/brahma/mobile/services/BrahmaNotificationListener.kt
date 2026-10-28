package com.brahma.mobile.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps the latest notifications in memory so Brahma can read and summarize them on request. */
class BrahmaNotificationListener : NotificationListenerService() {

    companion object {
        private val buf = ArrayDeque<String>()
        fun snapshot(): List<String> = synchronized(buf) { buf.toList() }
        private fun add(line: String) = synchronized(buf) {
            buf.addLast(line)
            while (buf.size > 40) buf.removeFirst()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try { activeNotifications?.forEach { record(it) } } catch (e: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record(sbn)

    private fun record(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val ex = sbn.notification.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        add("${sbn.packageName}: $title - $text".take(220))
    }
}
