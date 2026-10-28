package com.brahma.mobile.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives Brahma hands: read the screen, tap, type, scroll, press system keys.
 * Opt-in (the user must enable it in Android Settings). Refuses to touch banking / wallet / password apps.
 */
class BrahmaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BrahmaAccessibilityService? = null

        private val BLOCKED = listOf(
            "bank", "bkash", "nagad", "rocket", "upay", "paypal", "payoneer", "wallet",
            "authenticator", "keepass", "bitwarden", "1password", "lastpass", "dashlane"
        )
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun guardedRoot(): AccessibilityNodeInfo {
        val root = rootInActiveWindow ?: error("No active window right now. Try wait(2) and screen_read again.")
        val pkg = root.packageName?.toString()?.lowercase().orEmpty()
        if (BLOCKED.any { pkg.contains(it) }) {
            error("Blocked for safety: $pkg looks like a banking, payment or password app. The user must operate it manually.")
        }
        return root
    }

    fun readScreen(): String {
        val root = guardedRoot()
        val sb = StringBuilder("APP: ${root.packageName}\n")
        var n = 0
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || n >= 140) return
            if (node.isVisibleToUser) {
                val raw = (node.text ?: node.contentDescription)?.toString()?.replace("\n", " ")?.take(90)
                val txt = if (node.isPassword) "[password field hidden]" else raw
                val interactive = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable
                if (!txt.isNullOrBlank() || interactive) {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    sb.append("- ").append(node.className?.toString()?.substringAfterLast('.') ?: "View")
                    if (!txt.isNullOrBlank()) sb.append(" \"").append(txt).append("\"")
                    if (node.isClickable) sb.append(" [click]")
                    if (node.isEditable) sb.append(" [input]")
                    if (node.isScrollable) sb.append(" [scroll]")
                    if (node.isCheckable) sb.append(if (node.isChecked) " [on]" else " [off]")
                    sb.append(" @").append(r.centerX()).append(',').append(r.centerY()).append('\n')
                    n++
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return sb.toString()
    }

    fun clickText(text: String): Boolean {
        val root = guardedRoot()
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null && !cur.isClickable) cur = cur.parent
            if (cur != null && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val first = nodes.firstOrNull() ?: return false
        val r = Rect()
        first.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun typeText(text: String): Boolean {
        val root = guardedRoot()
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable && !n.isPassword) return n
        for (i in 0 until n.childCount) {
            val f = findEditable(n.getChild(i))
            if (f != null) return f
        }
        return null
    }

    fun tap(x: Float, y: Float): Boolean {
        guardedRoot()
        return gesture(Path().apply { moveTo(x, y) }, 60)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 350): Boolean {
        guardedRoot()
        return gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)
    }

    /** direction = which way the user wants to see more content: "down" shows content further down. */
    fun scroll(direction: String): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        return when (direction.lowercase()) {
            "down" -> swipe(w / 2, h * 0.72f, w / 2, h * 0.28f)
            "up" -> swipe(w / 2, h * 0.28f, w / 2, h * 0.72f)
            "left" -> swipe(w * 0.8f, h / 2, w * 0.2f, h / 2)
            "right" -> swipe(w * 0.2f, h / 2, w * 0.8f, h / 2)
            else -> false
        }
    }

    fun key(name: String): Boolean {
        val action = when (name.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return performGlobalAction(action)
    }

    private fun gesture(p: Path, ms: Long): Boolean {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, ms))
            .build()
        return dispatchGesture(g, null, null)
    }
}
