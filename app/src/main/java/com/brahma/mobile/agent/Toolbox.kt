package com.brahma.mobile.agent

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.brahma.mobile.automation.BrahmaAccessibilityService
import com.brahma.mobile.data.Memory
import com.brahma.mobile.llm.Http
import com.brahma.mobile.llm.ToolDef
import com.brahma.mobile.services.BrahmaNotificationListener
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Every ability Brahma has on the phone. Sensitive ones set confirm = true. */
class Toolbox(private val ctx: Context, private val mem: Memory) {

    // ---------- helpers ----------
    private fun schema(vararg p: Triple<String, String, Boolean>): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        for ((name, desc, required) in p) {
            props.put(name, JSONObject().put("type", "string").put("description", desc))
            if (required) req.put(name)
        }
        val o = JSONObject().put("type", "object").put("properties", props)
        if (req.length() > 0) o.put("required", req)
        return o
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun acc(): BrahmaAccessibilityService = BrahmaAccessibilityService.instance
        ?: error("Accessibility service is OFF. Ask the user to enable Brahma: Settings tab -> Permissions -> Accessibility.")

    private fun resolveNumber(q: String): String {
        val onlyPhone = q.none { it.isLetter() } && q.any { it.isDigit() }
        if (onlyPhone) return q.filter { it.isDigit() || it == '+' }
        if (!granted(Manifest.permission.READ_CONTACTS)) error("Contacts permission is missing (Settings tab -> Permissions).")
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"), null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(1).filter { it.isDigit() || it == '+' }
        }
        error("No contact found matching '$q'.")
    }

    private val weatherNames = mapOf(
        0 to "clear", 1 to "mostly clear", 2 to "partly cloudy", 3 to "overcast", 45 to "fog", 48 to "fog",
        51 to "light drizzle", 53 to "drizzle", 55 to "heavy drizzle", 61 to "light rain", 63 to "rain",
        65 to "heavy rain", 80 to "rain showers", 81 to "rain showers", 82 to "violent showers",
        95 to "thunderstorm", 96 to "thunderstorm with hail", 99 to "thunderstorm with hail"
    )

    // ---------- tools ----------
    fun all(): List<ToolDef> = listOf(

        ToolDef("open_app", "Launch an installed app by its name (English label, e.g. 'WhatsApp', 'Camera', 'Settings').",
            schema(Triple("name", "App name", true))) { a ->
            val pm = ctx.packageManager
            val q = a.optString("name").lowercase().trim()
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val label = { r: android.content.pm.ResolveInfo -> r.loadLabel(pm).toString().lowercase() }
            val m = apps.firstOrNull { label(it) == q }
                ?: apps.firstOrNull { label(it).contains(q) }
                ?: apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(q.replace(" ", "")) }
                ?: error("No installed app matches '$q'.")
            val li = pm.getLaunchIntentForPackage(m.activityInfo.packageName) ?: error("Cannot launch ${m.activityInfo.packageName}")
            start(li)
            "Opened ${label(m)}."
        },

        ToolDef("open_url", "Open a web address in the browser.", schema(Triple("url", "Full URL", true))) { a ->
            var u = a.optString("url").trim()
            if (!u.startsWith("http")) u = "https://$u"
            start(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            "Opened $u"
        },

        ToolDef("web_search", "Search the web for a quick factual answer (DuckDuckGo). Falls back to opening the browser.",
            schema(Triple("query", "Search query", true)), untrusted = true) { a ->
            val q = a.optString("query")
            val j = JSONObject(Http.get("https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&skip_disambig=1"))
            val sb = StringBuilder()
            j.optString("AbstractText").takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
            j.optString("Answer").takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
            val rel = j.optJSONArray("RelatedTopics")
            if (rel != null) {
                var n = 0
                var i = 0
                while (i < rel.length() && n < 5) {
                    val t = rel.optJSONObject(i)?.optString("Text").orEmpty()
                    if (t.isNotBlank()) { sb.append("- ").append(t.take(200)).append('\n'); n++ }
                    i++
                }
            }
            if (sb.isBlank()) {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=${enc(q)}")))
                "No instant answer. Opened browser search for '$q'."
            } else sb.toString()
        },

        ToolDef("weather", "Current weather and today's forecast for a city (uses remembered city if omitted).",
            schema(Triple("city", "City name", false))) { a ->
            val city = a.optString("city").ifBlank { mem.all()["city"] ?: "Dhaka" }
            val geo = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1"))
            val p = geo.optJSONArray("results")?.optJSONObject(0) ?: error("City not found: $city")
            val w = JSONObject(Http.get(
                "https://api.open-meteo.com/v1/forecast?latitude=${p.optDouble("latitude")}&longitude=${p.optDouble("longitude")}" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"
            ))
            val c = w.getJSONObject("current")
            val d = w.getJSONObject("daily")
            "${p.optString("name")}: ${weatherNames[c.optInt("weather_code")] ?: "unknown"}, ${c.optDouble("temperature_2m")}°C " +
                "(feels ${c.optDouble("apparent_temperature")}°C), humidity ${c.optInt("relative_humidity_2m")}%, wind ${c.optDouble("wind_speed_10m")} km/h. " +
                "Today ${d.getJSONArray("temperature_2m_min").optDouble(0)}–${d.getJSONArray("temperature_2m_max").optDouble(0)}°C, " +
                "rain chance ${d.getJSONArray("precipitation_probability_max").optInt(0)}%."
        },

        ToolDef("flashlight", "Turn the flashlight on or off.", schema(Triple("state", "'on' or 'off'", true))) { a ->
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: error("This phone has no flash.")
            val on = a.optString("state").lowercase() != "off"
            cm.setTorchMode(id, on)
            "Flashlight ${if (on) "on" else "off"}."
        },

        ToolDef("set_volume", "Set a volume level 0-100 (0 = mute). stream: music, ring, alarm or call.",
            schema(Triple("percent", "0-100", true), Triple("stream", "music|ring|alarm|call (default music)", false))) { a ->
            val am = ctx.getSystemService(AudioManager::class.java)
            val stream = when (a.optString("stream", "music").lowercase()) {
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "call" -> AudioManager.STREAM_VOICE_CALL
                else -> AudioManager.STREAM_MUSIC
            }
            val pct = a.optInt("percent", 50).coerceIn(0, 100)
            val max = am.getStreamMaxVolume(stream)
            try {
                am.setStreamVolume(stream, (max * pct / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
            } catch (e: SecurityException) {
                error("Android blocked this change (Do Not Disturb is probably on).")
            }
            "Volume set to $pct%."
        },

        ToolDef("open_settings",
            "Open a system settings page: wifi, bluetooth, display, sound, battery, location, airplane, data, apps, accessibility, notification_access, or general.",
            schema(Triple("panel", "Which page", true))) { a ->
            val action = when (a.optString("panel").lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                "data" -> Settings.ACTION_WIRELESS_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "notification_access" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            start(Intent(action))
            "Opened settings page '${a.optString("panel")}'. Use screen tools to toggle things there."
        },

        ToolDef("set_alarm", "Set an alarm at a clock time (24h).",
            schema(Triple("hour", "0-23", true), Triple("minute", "0-59", true), Triple("label", "Label", false))) { a ->
            start(Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
                .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
                .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Brahma"))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Alarm set for %02d:%02d.".format(a.optInt("hour"), a.optInt("minute"))
        },

        ToolDef("set_timer", "Start a countdown timer.",
            schema(Triple("seconds", "Duration in seconds", true), Triple("label", "Label", false))) { a ->
            start(Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60))
                .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Brahma"))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Timer started for ${a.optInt("seconds", 60)} seconds."
        },

        ToolDef("call_phone", "Call a contact name or phone number.", schema(Triple("to", "Contact name or number", true)), confirm = true) { a ->
            val num = resolveNumber(a.optString("to"))
            if (granted(Manifest.permission.CALL_PHONE)) {
                start(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))))
                "Calling $num."
            } else {
                start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
                "Opened the dialer with $num (call permission not granted, user must press call)."
            }
        },

        ToolDef("send_sms", "Send an SMS to a contact name or number.",
            schema(Triple("to", "Contact name or number", true), Triple("message", "Text", true)), confirm = true) { a ->
            val num = resolveNumber(a.optString("to"))
            val msg = a.optString("message")
            if (granted(Manifest.permission.SEND_SMS)) {
                val sm = ctx.getSystemService(SmsManager::class.java)
                sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null)
                "SMS sent to $num."
            } else {
                start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", msg))
                "SMS permission missing, so the compose screen was opened with the text filled in."
            }
        },

        ToolDef("send_whatsapp", "Open a WhatsApp chat with the message prefilled. Number must include country code. Then press Send using screen_click('Send').",
            schema(Triple("to", "Contact name or number with country code", true), Triple("message", "Text", true)), confirm = true) { a ->
            val num = resolveNumber(a.optString("to")).removePrefix("+")
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=${enc(a.optString("message"))}"))
            try { start(Intent(i).setPackage("com.whatsapp")) } catch (e: Exception) { start(i) }
            "WhatsApp chat opened with the message prefilled. Call wait(2) then screen_click 'Send' to send it."
        },

        ToolDef("battery_status", "Battery level and charging state.", null) {
            val bm = ctx.getSystemService(BatteryManager::class.java)
            val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val st = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            "Battery $lvl%, ${if (charging) "charging" else "not charging"}."
        },

        ToolDef("current_time", "Current date, time and timezone of the phone.", null) {
            SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm:ss zzz", Locale.ENGLISH).format(Date())
        },

        ToolDef("read_notifications", "Read the most recent notifications on the phone (needs Notification access).", null, untrusted = true) {
            val list = BrahmaNotificationListener.snapshot()
            if (list.isEmpty()) "No notifications captured. Make sure Notification access is enabled for Brahma (Settings tab -> Permissions)."
            else list.takeLast(25).joinToString("\n")
        },

        ToolDef("get_clipboard", "Read the text currently on the clipboard.", null, untrusted = true) {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                ?: "Clipboard is empty (or Android hid it because Brahma is not in the foreground)."
        },

        ToolDef("set_clipboard", "Put text on the clipboard.", schema(Triple("text", "Text to copy", true))) { a ->
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("brahma", a.optString("text")))
            "Copied to clipboard."
        },

        // ----- screen control (Accessibility) -----
        ToolDef("screen_read", "Read what is currently on the phone screen (visible text, buttons, input fields with coordinates).", null, untrusted = true) {
            acc().readScreen()
        },

        ToolDef("screen_click", "Tap a button/text on screen by its visible text or description.",
            schema(Triple("text", "Visible text or content description", true))) { a ->
            if (acc().clickText(a.optString("text"))) "Clicked '${a.optString("text")}'." else error("Nothing on screen matches '${a.optString("text")}'. Use screen_read.")
        },

        ToolDef("screen_tap", "Tap at screen coordinates (use coordinates from screen_read).",
            schema(Triple("x", "X pixel", true), Triple("y", "Y pixel", true))) { a ->
            if (acc().tap(a.optDouble("x").toFloat(), a.optDouble("y").toFloat())) "Tapped." else error("Tap failed.")
        },

        ToolDef("screen_type", "Type text into the focused (or first) input field on screen. Replaces its content.",
            schema(Triple("text", "Text to type", true)), confirm = false) { a ->
            if (acc().typeText(a.optString("text"))) "Typed." else error("No input field found. Tap a field first.")
        },

        ToolDef("screen_scroll", "Scroll the screen: direction down (see more below), up, left or right.",
            schema(Triple("direction", "down|up|left|right", true))) { a ->
            if (acc().scroll(a.optString("direction"))) "Scrolled ${a.optString("direction")}." else error("Scroll failed.")
        },

        ToolDef("press_key", "Press a system key: back, home, recents, notifications, quick_settings, power_menu, lock, screenshot.",
            schema(Triple("key", "Key name", true))) { a ->
            if (acc().key(a.optString("key"))) "Pressed ${a.optString("key")}." else error("Unknown key or not supported.")
        },

        ToolDef("wait", "Wait a few seconds (max 8), e.g. after opening an app.", schema(Triple("seconds", "1-8", true))) { a ->
            val s = a.optInt("seconds", 2).coerceIn(1, 8)
            delay(s * 1000L)
            "Waited $s s."
        },

        // ----- memory -----
        ToolDef("save_memory", "Remember a durable fact about the user (key + value).",
            schema(Triple("key", "Short key e.g. 'city', 'name'", true), Triple("value", "Value", true))) { a ->
            mem.put(a.optString("key"), a.optString("value")); "Saved."
        },

        ToolDef("recall_memory", "Search stored memory.", schema(Triple("query", "Keyword", false))) { a ->
            mem.search(a.optString("query"))
        },

        ToolDef("save_rule", "Store a standing instruction from the user that applies to all future chats.",
            schema(Triple("rule", "The instruction", true))) { a ->
            mem.addRule(a.optString("rule")); "Rule saved."
        }
    )
}
