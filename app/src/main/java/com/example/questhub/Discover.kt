package com.example.questhub

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Rec(val name: String, val pkg: String, val apkUrl: String, val iconUrl: String)

/** Loads the "recommended, not installed" list and downloads + installs APKs on tap. */
class Discover(private val act: Activity) {
    private val prefs = act.getSharedPreferences("questhub", Context.MODE_PRIVATE)
    private val pending = HashMap<Long, File>()

    var catalogUrl: String
        get() = prefs.getString("catalog_url", "") ?: ""
        set(v) { prefs.edit().putString("catalog_url", v.trim()).apply() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val file = pending.remove(id) ?: return
            val dm = c.getSystemService(DownloadManager::class.java)
            dm.query(DownloadManager.Query().setFilterById(id))?.use { cur ->
                if (cur.moveToFirst()) {
                    val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (st == DownloadManager.STATUS_SUCCESSFUL) install(file)
                    else Toast.makeText(c, "Download failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun register() = ContextCompat.registerReceiver(
        act, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)

    fun unregister() = try { act.unregisterReceiver(receiver) } catch (_: Exception) {}

    /** Bundled list + optional remote catalog, minus anything already installed. */
    fun load(installed: Set<String>, done: (List<Rec>) -> Unit) {
        Thread {
            val out = LinkedHashMap<String, Rec>()
            fun add(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val r = Rec(o.optString("name"), o.optString("package"), o.optString("apkUrl"), o.optString("iconUrl"))
                    if (r.name.isNotEmpty() && r.pkg.isNotEmpty()) out[r.pkg] = r
                }
            }
            try { add(JSONArray(act.assets.open("recommended.json").bufferedReader().readText())) } catch (_: Exception) {}
            if (catalogUrl.startsWith("https://")) {
                try {
                    val c = URL(catalogUrl).openConnection() as HttpURLConnection
                    c.connectTimeout = 8000; c.readTimeout = 8000
                    add(JSONArray(c.inputStream.bufferedReader().readText()))
                } catch (_: Exception) {}
            }
            val list = out.values.filter { it.pkg !in installed }
            act.runOnUiThread { done(list) }
        }.start()
    }

    fun fetchIcon(url: String, done: (Bitmap?) -> Unit) {
        if (!url.startsWith("https://")) return done(null)
        Thread {
            val bmp = try { BitmapFactory.decodeStream(URL(url).openStream()) } catch (_: Exception) { null }
            act.runOnUiThread { done(bmp) }
        }.start()
    }

    fun get(r: Rec) {
        if (!r.apkUrl.startsWith("https://")) {
            // No direct download in the catalog: open a search so the user can pick a source themselves.
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sidequestvr.com/search?query=" + Uri.encode(r.name))))
            return
        }
        val dir = act.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = File(dir, r.pkg + ".apk").also { if (it.exists()) it.delete() }
        val req = DownloadManager.Request(Uri.parse(r.apkUrl))
            .setTitle("Downloading " + r.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        pending[act.getSystemService(DownloadManager::class.java).enqueue(req)] = file
        Toast.makeText(act, "Downloading " + r.name + "…", Toast.LENGTH_SHORT).show()
    }

    private fun install(file: File) {
        if (!act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, "Allow installs from Quest Hub, then tap the game again", Toast.LENGTH_LONG).show()
            act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName)))
            return
        }
        val uri = FileProvider.getUriForFile(act, act.packageName + ".files", file)
        act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
