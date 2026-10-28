package com.example.questhub.tools

import android.app.Activity
import android.content.Intent
import android.net.wifi.WifiManager
import android.text.InputType
import android.view.View
import android.widget.*
import com.example.questhub.*
import java.util.concurrent.Executors

/**
 * Tools tab: everything the WirelessDebugOverlay app does, rebuilt here —
 * pair/connect over Wireless Debugging, refresh rate, CPU/GPU/foveation, boundary,
 * OVR Metrics HUD, battery/heat info, shell and app control.
 */
class ToolsView(private val act: Activity) : ScrollView(act) {
    private val io = Executors.newSingleThreadExecutor()
    private val adb get() = AdbManager.get(act)
    private val status = act.label("ADB: not connected", 14f, Theme.MUTED)
    private val log = act.label("Output appears here.", 12f, Theme.MUTED).apply {
        setTypeface(android.graphics.Typeface.MONOSPACE); setTextIsSelectable(true)
    }
    private val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(act.dp(24), act.dp(12), act.dp(24), act.dp(32)) }
    private val lock: WifiManager.MulticastLock? =
        (act.applicationContext.getSystemService(WifiManager::class.java))?.createMulticastLock("questhub")?.apply { setReferenceCounted(false) }

    init {
        addView(col)
        lock?.acquire()
        connection()
        section("Refresh rate", "Sets debug.oculus.refreshRate. Higher is smoother but hotter.") {
            row(listOf(60, 72, 90, 120, 200, 240).map { hz -> "$hz Hz" to { exec("setprop debug.oculus.refreshRate $hz", "Refresh rate set to $hz Hz.") } })
        }
        section("Performance", "More heat and faster battery drain.") {
            row(listOf(
                "CPU: max" to { exec("setprop debug.oculus.cpuLevel 5", "CPU level set to 5.") },
                "GPU: max" to { exec("setprop debug.oculus.gpuLevel 5", "GPU level set to 5, the highest. It can still slow down if the headset gets hot.") },
                "Foveation: off" to { exec("setprop debug.oculus.foveation.level 0", "Foveated rendering turned off for apps that use it.") },
                "Max everything" to { maxEverything() },
                "Reset to normal" to { exec("setprop debug.oculus.guardian_pause 0; setprop debug.oculus.refreshRate 90; setprop debug.oculus.cpuLevel ''; setprop debug.oculus.gpuLevel ''; setprop debug.oculus.foveation.level ''", "Reset: boundary on, 90 Hz, performance back to default.") }
            ))
        }
        section("Boundary", "Pausing the boundary hides the safety wall. Be careful of nearby objects.") {
            row(listOf(
                "Pause boundary" to { exec("setprop debug.oculus.guardian_pause 1", "Boundary paused. Be careful of nearby objects.") },
                "Restore boundary" to { exec("setprop debug.oculus.guardian_pause 0", "Boundary restored.") }
            ))
        }
        section("Metrics HUD", "Battery, FPS, refresh rate, battery temp and screen tears (needs OVR Metrics Tool).") {
            val svc = "am broadcast -n com.oculus.ovrmonitormetricsservice/.SettingsBroadcastReceiver -a com.oculus.ovrmonitormetricsservice.ENABLE_"
            val off = "am broadcast -n com.oculus.ovrmonitormetricsservice/.SettingsBroadcastReceiver -a com.oculus.ovrmonitormetricsservice.DISABLE_"
            row(listOf(
                "HUD on" to { exec(listOf(
                    svc + "OVERLAY --eb headlocked true --ef pitch 0.0",
                    svc + "STAT --es stat display_refresh_rate", svc + "STAT --es stat average_frame_rate",
                    svc + "STAT --es stat battery_level_percentage", svc + "STAT --es stat battery_temperature_celcius",
                    svc + "STAT --es stat screen_tear_count").joinToString("; "), "Metrics HUD on.") },
                "HUD tilted" to { exec(svc + "OVERLAY --eb headlocked true --ef pitch 20.0", "HUD tilted.") },
                "HUD off" to { exec(listOf(off + "OVERLAY", off + "STATS", off + "GRAPH").joinToString("; "), "HUD off.") },
                "Open OVR Metrics Tool" to { exec("am start -n com.oculus.ovrmonitormetricsservice/.PanelActivity", null) }
            ))
        }
        section("Battery & heat", null) {
            row(listOf(
                "Battery" to { exec("dumpsys battery | grep -E 'level|temperature'", null) },
                "Battery stats" to { exec("dumpsys batterystats | head -n 40", null) },
                "Temperature" to { exec("dumpsys thermalservice | head -n 40", null) },
                "Wi-Fi" to { exec("dumpsys wifi | grep -i -E 'rssi|linkspeed' | head -n 6", null) },
                "Stay awake: on" to { exec("settings put global stay_on_while_plugged_in 3", "Stay awake: on") },
                "Stay awake: off" to { exec("settings put global stay_on_while_plugged_in 0", "Stay awake: off") }
            ))
        }
        section("Info", null) {
            row(listOf(
                "Display" to { exec("dumpsys display | grep -i -E 'refreshrate|fps' | head -n 12", null) },
                "CPU" to { exec("cat /proc/cpuinfo | head -n 24", null) },
                "Top app" to { exec("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 5", null) },
                "Oculus properties" to { exec("getprop | grep -i oculus | head -n 60", null) },
                "User apps" to { exec("pm list packages -3 | sort | head -n 150", null) },
                "System apps" to { exec("pm list packages -s | sort | head -n 150", null) },
                "Disabled apps" to { exec("pm list packages -d | sort | head -n 150", null) }
            ))
        }
        section("Apps & power", null) {
            val pkg = field("Package name, e.g. com.oculus.browser")
            col.addView(pkg)
            row(listOf(
                "Force stop" to { val p = pkg.text.toString().trim(); if (p.isNotEmpty()) exec("am force-stop $p", "Stopped $p.") },
                "Kill all background" to { exec("am kill-all", "Background apps killed.") },
                "Boundary settings" to { exec("am start omms://app", null) },
                "Power off" to { exec("reboot -p", null) }
            ))
        }
        section("Shell command", null) {
            val cmd = field("Shell command (e.g. getprop ro.product.model)")
            col.addView(cmd)
            row(listOf("Run" to { val c = cmd.text.toString().trim(); if (c.isNotEmpty()) exec(c, null) }))
        }
        col.addView(act.label("Output", 16f, Theme.TEXT, true).apply { setPadding(0, act.dp(20), 0, act.dp(6)) })
        col.addView(log)
    }

    // ---- connection ---------------------------------------------------------------------------
    private fun connection() {
        col.addView(act.label("Connection", 18f, Theme.TEXT, true))
        col.addView(act.label("Turn on Wireless Debugging (Developer settings) and pair once. Keep the \"Pair device with pairing code\" window open while pairing; its code and port change every time.", 13f, Theme.MUTED).apply { setPadding(0, act.dp(4), 0, act.dp(8)) })
        col.addView(status)
        val pairPort = field("Pairing port (or ip:port)").apply { inputType = InputType.TYPE_CLASS_TEXT }
        val code = field("Pairing code").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        col.addView(pairPort); col.addView(code)
        row(listOf(
            "Pair" to {
                val (h, p) = splitHostPort(pairPort.text.toString())
                val c = code.text.toString().trim()
                if (p == null || c.isEmpty()) say("Enter the pairing port (or ip:port) and code exactly as the pairing window shows them.")
                else io.execute {
                    say("Pairing...")
                    try {
                        val ok = adb.pair(h, p, c)
                        say(if (ok) "Paired. Now tap Connect." else "Pairing was refused. Check the code, and keep the pairing window open.")
                    } catch (e: Exception) { say("Pairing failed (${e.message}). Keep the pairing window open and type the port and code exactly as shown.") }
                }
            },
            "Connect (auto)" to { connect() },
            "Developer settings" to { act.startActivity(Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")) }
        ))
    }

    private fun splitHostPort(s: String): Pair<String, Int?> {
        val t = s.trim()
        return if (t.contains(":")) t.substringBeforeLast(":").ifEmpty { "127.0.0.1" } to t.substringAfterLast(":").toIntOrNull()
        else "127.0.0.1" to t.toIntOrNull()
    }

    private fun connect() = io.execute {
        say("Looking for Wireless Debugging...")
        val ok = adb.ensureConnected()
        act.runOnUiThread { status.text = if (ok) "ADB: connected" else "ADB: not connected" }
        say(if (ok) "Connected." else "Wireless Debugging wasn't found on the network. Turn on Wireless Debugging and pair once.")
    }

    // ---- helpers ------------------------------------------------------------------------------
    private fun maxEverything() = io.execute {
        if (!adb.ensureConnected()) return@execute say("ADB: not connected. Tap Connect first.")
        val hz = Regex("fps=([0-9.]+)").findAll(adb.shell("dumpsys display | grep -o 'fps=[0-9.]*'"))
            .mapNotNull { it.groupValues[1].toFloatOrNull() }.maxOrNull()?.toInt()
        val use = hz ?: 120
        adb.shell("setprop debug.oculus.guardian_pause 0; setprop debug.oculus.refreshRate $use; setprop debug.oculus.cpuLevel 5; setprop debug.oculus.gpuLevel 5; setprop debug.oculus.foveation.level 0")
        say(if (hz != null) "Refresh rate set to $use Hz, the highest this headset reports. CPU/GPU max, foveation off."
            else "Couldn't read this headset's supported rates, so I tried $use Hz. CPU/GPU max, foveation off.")
    }

    private fun exec(cmd: String, ok: String?) = io.execute {
        if (!adb.ensureConnected()) { say("ADB: not connected. Pair once, then tap Connect."); return@execute }
        act.runOnUiThread { status.text = "ADB: connected" }
        try {
            val out = adb.shell(cmd).trim()
            say(ok?.let { if (out.isEmpty()) it else "$it\n$out" } ?: out.ifEmpty { "(no output)" })
        } catch (e: Exception) { say("Failed: ${e.message}") }
    }

    private fun say(t: String) = act.runOnUiThread { log.text = t }

    private fun field(hint: String) = EditText(act).apply {
        this.hint = hint; setHintTextColor(Theme.MUTED); setTextColor(Theme.TEXT); setSingleLine()
        background = act.rounded(Theme.SURFACE, 14); setPadding(act.dp(16), act.dp(10), act.dp(16), act.dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(8) }
    }

    private fun section(title: String, note: String?, body: () -> Unit) {
        col.addView(act.label(title, 18f, Theme.TEXT, true).apply { setPadding(0, act.dp(20), 0, act.dp(2)) })
        if (note != null) col.addView(act.label(note, 13f, Theme.MUTED))
        body()
    }

    private fun row(items: List<Pair<String, () -> Unit>>) {
        val flow = FlowLayout(act)
        items.forEach { (t, a) -> flow.addView(act.pill(t, false, a).apply {
            layoutParams = ViewGroupLP(act.dp(8))
        }) }
        col.addView(flow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(8) })
    }
}
