package com.example.questhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.questhub.tools.ToolsView

data class AppEntry(val label: String, val pkg: String, val icon: Drawable, val isGame: Boolean, val launch: Intent)

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var search: EditText
    private lateinit var discover: Discover
    private val tabNames = listOf("Apps", "Games", "Discover", "Tools")
    private val tabViews = ArrayList<TextView>()
    private var tab = 0
    private var all: List<AppEntry> = emptyList()
    private var tools: View? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        discover = Discover(this).also { it.register() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        bar.addView(label("Quest Hub", 22f, Theme.TEXT, true).apply { setPadding(0, 0, dp(24), 0) })
        tabNames.forEachIndexed { i, n ->
            val t = pill(n, i == 0) { select(i) }
            tabViews += t
            bar.addView(t, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        search = EditText(this).apply {
            hint = "Search"; setHintTextColor(Theme.MUTED); setTextColor(Theme.TEXT)
            background = rounded(Theme.SURFACE, 22); setPadding(dp(18), dp(8), dp(18), dp(8))
            setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { render() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        bar.addView(search, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
        root.addView(bar)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.statusBarColor = Theme.BG
    }

    override fun onResume() { super.onResume(); loadApps() }
    override fun onDestroy() { discover.unregister(); super.onDestroy() }

    private fun select(i: Int) {
        tab = i
        tabViews.forEachIndexed { n, t -> t.background = rounded(if (n == i) Theme.BLUE else Theme.SURFACE2, 22) }
        search.visibility = if (i == 3) View.GONE else View.VISIBLE
        render()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val list = pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { ai ->
                if (ai.packageName == packageName) return@mapNotNull null
                val launch = pm.getLaunchIntentForPackage(ai.packageName)
                    ?: Intent(Intent.ACTION_MAIN).addCategory("com.oculus.intent.category.VR").setPackage(ai.packageName)
                        .takeIf { pm.resolveActivity(it, 0) != null }
                    ?: return@mapNotNull null
                @Suppress("DEPRECATION")
                val game = ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    (!ai.packageName.startsWith("com.oculus.") && ai.metaData?.containsKey("com.oculus.supportedDevices") == true)
                AppEntry(ai.loadLabel(pm).toString(), ai.packageName, ai.loadBanner(pm) ?: ai.loadIcon(pm), game,
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.sortedBy { it.label.lowercase() }
            runOnUiThread { all = list; render() }
        }.start()
    }

    private fun render() {
        content.removeAllViews()
        when (tab) {
            0, 1 -> content.addView(appGrid(all.filter { it.isGame == (tab == 1) }))
            2 -> content.addView(discoverView())
            3 -> content.addView(tools ?: ToolsView(this).also { tools = it })
        }
    }

    private fun appGrid(items: List<AppEntry>): View {
        val q = search.text.toString().trim().lowercase()
        val shown = if (q.isEmpty()) items else items.filter { it.label.lowercase().contains(q) }
        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT; columnWidth = dp(120)
            horizontalSpacing = dp(12); verticalSpacing = dp(12)
            setPadding(dp(24), dp(12), dp(24), dp(24)); clipToPadding = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                val e = shown[p]
                return card(e.label, e.icon, null)
            }
        }
        grid.setOnItemClickListener { _, _, p, _ -> try { startActivity(shown[p].launch) } catch (_: Exception) {} }
        grid.setOnItemLongClickListener { _, _, p, _ -> appMenu(shown[p]); true }
        return grid
    }

    private fun card(title: String, icon: Drawable?, sub: String?): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(Theme.SURFACE); setPadding(dp(10), dp(14), dp(10), dp(12))
        addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(label(title, 13f).apply { maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0) })
        if (sub != null) addView(label(sub, 12f, Theme.BLUE, true).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun appMenu(e: AppEntry) {
        AlertDialog.Builder(this).setTitle(e.label).setItems(arrayOf("App info", "Uninstall")) { _, i ->
            val uri = Uri.parse("package:" + e.pkg)
            if (i == 0) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            else startActivity(Intent(Intent.ACTION_DELETE, uri))
        }.show()
    }

    private fun discoverView(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(0)) }
        head.addView(label("Recommended for you — games you don't have yet. Tap one to get it.", 14f, Theme.MUTED),
            LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(pill("Catalog URL", false) { catalogDialog() })
        col.addView(head)
        val frame = FrameLayout(this)
        col.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val q = search.text.toString().trim().lowercase()
        discover.load(all.map { it.pkg }.toSet()) { recs ->
            if (tab != 2) return@load
            val shown = if (q.isEmpty()) recs else recs.filter { it.name.lowercase().contains(q) }
            val grid = GridView(this).apply {
                numColumns = GridView.AUTO_FIT; columnWidth = dp(140)
                horizontalSpacing = dp(12); verticalSpacing = dp(12)
                setPadding(dp(24), dp(12), dp(24), dp(24)); clipToPadding = false
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
            }
            grid.adapter = object : BaseAdapter() {
                override fun getCount() = shown.size
                override fun getItem(p: Int) = shown[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                    val r = shown[p]
                    val c = card(r.name, null, if (r.apkUrl.isNotEmpty()) "DOWNLOAD" else "FIND") as LinearLayout
                    val iv = c.getChildAt(0) as ImageView
                    iv.setImageDrawable(rounded(Theme.SURFACE2, 14))
                    discover.fetchIcon(r.iconUrl) { bmp -> if (bmp != null) iv.setImageBitmap(bmp) }
                    return c
                }
            }
            grid.setOnItemClickListener { _, _, p, _ -> discover.get(shown[p]) }
            frame.addView(grid)
            if (shown.isEmpty()) frame.addView(label("Nothing to recommend right now.", 15f, Theme.MUTED).apply { setPadding(dp(24), dp(24), 0, 0) })
        }
        return col
    }

    private fun catalogDialog() {
        val input = EditText(this).apply { setText(discover.catalogUrl); hint = "https://…/recommended.json" }
        AlertDialog.Builder(this).setTitle("Recommendation catalog URL").setView(input)
            .setPositiveButton("Save") { _, _ -> discover.catalogUrl = input.text.toString(); render() }
            .setNegativeButton("Cancel", null).show()
    }
}
