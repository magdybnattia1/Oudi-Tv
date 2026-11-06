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
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String, val group: String = "")

class MainActivity : Activity() {
    private val gold = Color.rgb(224, 184, 75)
    private val bg = Color.rgb(10, 11, 14)
    private val card = Color.rgb(25, 27, 33)
    private val muted = Color.rgb(155, 160, 170)
    private lateinit var channelList: LinearLayout
    private lateinit var search: EditText
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var categoryRow: LinearLayout
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()
    private var selectedGroup = "الكل"
    private var playlistAddress = ""
    private var selectedChannel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildScreen()
        val defaultPlaylist = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"
        val stored = getPreferences(MODE_PRIVATE).getString("playlist", "") ?: ""
        playlistAddress = if (stored.isBlank() || stored.contains("biskyt.github.io") || stored.contains("SaidFathi/iptv-channels")) defaultPlaylist else stored
        getPreferences(MODE_PRIVATE).edit().putString("playlist", playlistAddress).apply()
        loadPlaylist(playlistAddress)
    }

    private fun rounded(color: Int, radius: Float = 18f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 8)
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        brand.addView(label("OUDI TV", 27f, gold, true))
        brand.addView(label("عالم القنوات بين إيديك", 12f, muted))
        header.addView(brand)
        val sourceButton = TextView(this).apply {
            text = "⚙  المصدر"
            textSize = 13f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(14, 10, 14, 10)
            background = rounded(card, 14f)
            setOnClickListener { showSourceDialog() }
        }
        header.addView(sourceButton)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 205).apply { bottomMargin = 12 })

        val section = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        section.addView(label("القنوات", 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        status = label("جاري التحميل...", 12f, muted)
        section.addView(status)
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })

        search = EditText(this).apply {
            hint = "ابحث عن قناة أو دولة..."
            textSize = 14f
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            singleLine = true
            setPadding(14, 0, 14, 0)
            background = rounded(card, 14f)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 48).apply { bottomMargin = 10 })

        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, 42).apply { bottomMargin = 10 })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        channelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(channelList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showChannels()
            override fun afterTextChanged(s: Editable?) {}
        })
        setContentView(root)
        updateCategories()
        showChannels()
    }

    private fun showSourceDialog() {
        val input = EditText(this).apply {
            setText(playlistAddress)
            setTextColor(Color.BLACK)
            hint = "https://..."
            setPadding(20, 12, 20, 12)
        }
        AlertDialog.Builder(this)
            .setTitle("مصدر القنوات")
            .setMessage("أدخل رابط قائمة M3U أو اترك الرابط الحالي.")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تحميل") { _, _ ->
                val value = input.text.toString().trim()
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    playlistAddress = value
                    getPreferences(MODE_PRIVATE).edit().putString("playlist", value).apply()
                    loadPlaylist(value)
                } else Toast.makeText(this, "الرابط غير صحيح", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun loadPlaylist(address: String) {
        status.text = "جاري تحميل القنوات..."
        thread {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 OUDI-TV")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val parsed = parseM3u(body)
                runOnUiThread {
                    channels = parsed
                    selectedGroup = "الكل"
                    updateCategories()
                    showChannels()
                    status.text = if (parsed.isEmpty()) "لم يتم العثور على قنوات" else "${parsed.size} قناة"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "تعذر تحميل القائمة"
                    channelList.removeAllViews()
                    channelList.addView(label("تعذر الاتصال بمصدر القنوات. تأكد من الإنترنت أو جرّب مصدرًا آخر من زر المصدر.", 14f, muted).apply {
                        gravity = Gravity.CENTER
                        setPadding(20, 36, 20, 36)
                    })
                }
            }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = "قناة"
        var group = ""
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "قناة").trim().ifBlank { "قناة" }
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotBlank() && !line.startsWith("#") &&
                (line.startsWith("http://") || line.startsWith("https://"))) {
                result.add(Channel(name, line, group))
                name = "قناة"
                group = ""
            }
        }
        return result
    }

    private fun updateCategories() {
        if (!::categoryRow.isInitialized) return
        categoryRow.removeAllViews()
        val groups = listOf("الكل") + channels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
        groups.take(35).forEach { group ->
            val chip = TextView(this).apply {
                text = group
                textSize = 12f
                setTextColor(if (group == selectedGroup) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(15, 0, 15, 0)
                background = rounded(if (group == selectedGroup) gold else card, 22f)
                setOnClickListener { selectedGroup = group; updateCategories(); showChannels() }
            }
            categoryRow.addView(chip, LinearLayout.LayoutParams(-2, 36).apply { marginEnd = 7 })
        }
    }

    private fun showChannels() {
        if (!::channelList.isInitialized) return
        channelList.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim() else ""
        val visible = channels.filter {
            (selectedGroup == "الكل" || it.group == selectedGroup) &&
                (it.name.contains(query, true) || it.group.contains(query, true))
        }
        visible.take(500).forEachIndexed { index, channel ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 10, 14, 10)
                background = rounded(card, 15f)
                setOnClickListener { play(channel) }
            }
            val number = TextView(this).apply {
                text = "▶"
                textSize = 15f
                setTextColor(gold)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(42, 39, 29), 12f)
            }
            item.addView(number, LinearLayout.LayoutParams(42, 42).apply { marginEnd = 12 })
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            details.addView(label(channel.name, 14f, Color.WHITE, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            details.addView(label(if (channel.group.isBlank()) "بث مباشر" else channel.group, 11f, muted).apply { maxLines = 1 })
            item.addView(details)
            item.addView(label("›", 24f, gold))
            channelList.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
        }
        if (visible.isEmpty()) {
            channelList.addView(label(
                if (channels.isEmpty()) "القنوات هتظهر هنا بعد تحميل القائمة..." else "مفيش نتائج مطابقة للبحث",
                14f, muted
            ).apply { gravity = Gravity.CENTER; setPadding(12, 40, 12, 40) })
        }
    }

    private fun play(channel: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView.player = player
            }
            selectedChannel = channel
            playerView.visibility = View.VISIBLE
            playerView.player = player
            player?.setMediaItem(MediaItem.fromUri(channel.url))
            player?.prepare()
            player?.play()
            status.text = "يعمل الآن: ${channel.name}"
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر تشغيل القناة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        if (::playerView.isInitialized) playerView.player = null
    }
}
