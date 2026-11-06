package com.oudi.tv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String, val group: String = "", val logo: String = "")

class MainActivity : Activity() {
    private val gold = Color.rgb(224, 184, 75)
    private val bg = Color.rgb(9, 10, 13)
    private val panel = Color.rgb(23, 25, 31)
    private val muted = Color.rgb(155, 160, 170)
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var tabs: LinearLayout
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var activeTab = "الكل"
    private var playlistAddress = ""
    private val sections = listOf("الكل", "رياضة", "أفلام", "مسلسلات", "أخبار", "أطفال", "منوعات")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildScreen()
        val default = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
        val saved = getPreferences(MODE_PRIVATE).getString("playlist", "") ?: ""
        playlistAddress = if (saved.isBlank()) default else saved
        loadPlaylist(playlistAddress)
    }

    private fun shape(color: Int, radius: Float = 16f) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 6)
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        brand.addView(label("OUDI  TV", 25f, gold, true))
        brand.addView(label("قنواتك المفضلة في مكان واحد", 11f, muted))
        header.addView(brand)
        val settings = TextView(this).apply {
            text = "⚙"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(gold); background = shape(panel, 14f)
            setOnClickListener { showSourceDialog() }
        }
        header.addView(settings, LinearLayout.LayoutParams(46, 44))
        root.addView(header, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 12 })

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label("شاهد الآن", 19f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        status = label("جاري تجهيز القنوات...", 11f, muted)
        titleRow.addView(status)
        root.addView(titleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })

        playerView = PlayerView(this).apply {
            useController = true; setBackgroundColor(Color.BLACK); visibility = View.GONE
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 190).apply { bottomMargin = 10 })

        search = EditText(this).apply {
            hint = "ابحث عن قناة..."
            textSize = 14f; setHintTextColor(muted); setTextColor(Color.WHITE)
            setSingleLine(true); setPadding(14, 0, 14, 0)
            background = shape(panel, 13f)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 46).apply { bottomMargin = 12 })

        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(tabs)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 43).apply { bottomMargin = 12 })

        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label("القنوات", 17f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label("● بث مباشر", 10f, gold))
        root.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 9 })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderChannels()
            override fun afterTextChanged(s: Editable?) {}
        })
        renderTabs()
        renderChannels()
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        sections.forEach { section ->
            val selected = section == activeTab
            val chip = TextView(this).apply {
                text = section; textSize = 13f; gravity = Gravity.CENTER
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                setPadding(18, 0, 18, 0)
                background = shape(if (selected) gold else panel, 22f)
                setOnClickListener { activeTab = section; renderTabs(); renderChannels() }
            }
            tabs.addView(chip, LinearLayout.LayoutParams(-2, 37).apply { marginEnd = 7 })
        }
    }

    private fun matchesSection(c: Channel): Boolean {
        val value = (c.group + " " + c.name).lowercase()
        return when (activeTab) {
            "رياضة" -> listOf("sport", "sports", "bein", "football", "soccer", "رياض", "كرة").any { value.contains(it) }
            "أفلام" -> listOf("movie", "film", "cinema", "فيلم", "أفلام").any { value.contains(it) }
            "مسلسلات" -> listOf("series", "drama", "مسلسل", "دراما").any { value.contains(it) }
            "أخبار" -> listOf("news", "أخبار", "خبر").any { value.contains(it) }
            "أطفال" -> listOf("kids", "children", "cartoon", "child", "أطفال", "كرتون").any { value.contains(it) }
            "منوعات" -> listOf("entertain", "music", "lifestyle", "منوع").any { value.contains(it) }
            else -> true
        }
    }

    private fun renderChannels() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim() else ""
        val visible = channels.filter { matchesSection(it) &&
            (it.name.contains(query, true) || it.group.contains(query, true)) }
        visible.take(400).forEach { channel ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10); background = shape(panel, 14f)
                setOnClickListener { play(channel) }
            }
            val badge = TextView(this).apply {
                text = "▶"; textSize = 17f; gravity = Gravity.CENTER
                setTextColor(gold); background = shape(Color.rgb(48, 41, 25), 12f)
            }
            row.addView(badge, LinearLayout.LayoutParams(45, 45).apply { marginEnd = 12 })
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            info.addView(label(channel.name, 14f, Color.WHITE, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            info.addView(label(channel.group.ifBlank { "بث مباشر" }, 11f, muted))
            row.addView(info)
            row.addView(label("مشاهدة  ›", 11f, gold))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
        }
        if (visible.isEmpty()) {
            val message = if (channels.isEmpty()) "جاري تحميل القنوات..." else
                if (activeTab == "الكل") "لا توجد نتائج مطابقة" else "لا توجد قنوات في قسم " + activeTab + " ضمن القائمة الحالية"
            list.addView(label(message, 13f, muted).apply {
                gravity = Gravity.CENTER; setPadding(10, 35, 10, 35)
            })
        }
    }

    private fun showSourceDialog() {
        val input = EditText(this).apply {
            setText(playlistAddress); setTextColor(Color.BLACK); hint = "https://..."
        }
        AlertDialog.Builder(this).setTitle("مصدر القنوات")
            .setMessage("يمكنك تغيير قائمة القنوات بصيغة M3U.")
            .setView(input).setNegativeButton("إلغاء", null)
            .setPositiveButton("تحميل") { _, _ ->
                val value = input.text.toString().trim()
                if (value.startsWith("http")) {
                    playlistAddress = value
                    getPreferences(MODE_PRIVATE).edit().putString("playlist", value).apply()
                    loadPlaylist(value)
                } else Toast.makeText(this, "الرابط غير صحيح", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun loadPlaylist(address: String) {
        status.text = "تحميل القنوات..."
        thread {
            try {
                val conn = URL(address).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000; conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 OUDI-TV")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val parsed = parseM3u(body)
                runOnUiThread {
                    channels = parsed
                    renderChannels()
                    status.text = if (parsed.isEmpty()) "القائمة فارغة" else parsed.size.toString() + " قناة"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    status.text = "فشل تحميل القائمة"
                    list.removeAllViews()
                    list.addView(label("تعذر الاتصال بمصدر القنوات. تحقق من الإنترنت أو غيّر المصدر من زر الإعدادات.", 13f, muted).apply {
                        gravity = Gravity.CENTER; setPadding(18, 35, 18, 35)
                    })
                }
            }
        }
    }

    private fun parseM3u(body: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = "قناة"; var group = ""; var logo = ""
        body.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "قناة").trim().ifBlank { "قناة" }
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
                logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            } else if (line.startsWith("http://") || line.startsWith("https://")) {
                out.add(Channel(name, line, group, logo))
                name = "قناة"; group = ""; logo = ""
            }
        }
        return out
    }

    private fun play(channel: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView.player = player
                player?.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        status.text = "تعذر تشغيل البث — قد يكون متوقفًا أو محجوبًا جغرافيًا"
                    }
                })
            }
            playerView.visibility = View.VISIBLE
            playerView.player = player
            player?.setMediaItem(MediaItem.fromUri(channel.url))
            player?.prepare(); player?.play()
            status.text = "يعمل الآن: " + channel.name
        } catch (_: Exception) {
            status.text = "تعذر تشغيل القناة"
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release(); player = null
        if (::playerView.isInitialized) playerView.player = null
    }
}