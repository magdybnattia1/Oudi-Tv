package com.oudi.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String, val group: String = "")

class MainActivity : Activity() {
    private val gold = Color.rgb(212, 175, 55)
    private val bg = Color.rgb(12, 12, 14)
    private val panel = Color.rgb(27, 27, 31)
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var playlistUrl: EditText
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var channels: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()
        val saved = getPreferences(MODE_PRIVATE).getString("playlist", "") ?: ""
        playlistUrl.setText(saved)
        if (saved.isNotBlank()) loadPlaylist(saved)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 18, 16, 12)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "OUDI TV"
            textSize = 28f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 14)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 210))
        playlistUrl = EditText(this).apply {
            hint = "رابط قائمة M3U المسموح باستخدامها"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setBackgroundColor(panel)
            setPadding(12, 8, 12, 8)
        }
        root.addView(playlistUrl, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 12 })
        val load = Button(this).apply {
            text = "تحميل القنوات"
            setTextColor(Color.BLACK)
            setBackgroundColor(gold)
            setOnClickListener {
                val value = playlistUrl.text.toString().trim()
                if (value.startsWith("https://")) {
                    getPreferences(MODE_PRIVATE).edit().putString("playlist", value).apply()
                    loadPlaylist(value)
                } else toast("أدخل رابط HTTPS صحيح")
            }
        }
        root.addView(load, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 6 })
        search = EditText(this).apply {
            hint = "ابحث عن قناة..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setBackgroundColor(panel)
            setPadding(12, 4, 12, 4)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 8 })
        val scroller = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 8 })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showChannels()
            override fun afterTextChanged(s: Editable?) {}
        })
        setContentView(root)
        showChannels()
    }

    private fun loadPlaylist(address: String) {
        toast("جاري تحميل القائمة...")
        thread {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "OUDI-TV/1.0")
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val parsed = parseM3u(text)
                runOnUiThread {
                    channels = parsed
                    showChannels()
                    toast(if (parsed.isEmpty()) "لم يتم العثور على قنوات" else "تم تحميل ${parsed.size} قناة")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("تعذر تحميل القائمة: ${e.message ?: "خطأ اتصال"}") }
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

    private fun showChannels() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val query = if (::search.isInitialized) search.text.toString().trim() else ""
        val visible = channels.filter { it.name.contains(query, true) || it.group.contains(query, true) }
        visible.forEach { channel ->
            val button = Button(this).apply {
                text = if (channel.group.isBlank()) channel.name else "${channel.name}  •  ${channel.group}"
                setTextColor(gold)
                setBackgroundColor(panel)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { play(channel) }
            }
            list.addView(button, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 5 })
        }
        if (visible.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (channels.isEmpty()) "أضف رابط قائمة M3U لعرض القنوات" else "لا توجد نتائج"
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(8, 30, 8, 30)
            }
            list.addView(empty)
        }
    }

    private fun play(channel: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView.player = player
            }
            player?.setMediaItem(MediaItem.fromUri(channel.url))
            player?.prepare()
            player?.play()
            toast("تشغيل: ${channel.name}")
        } catch (e: Exception) {
            toast("تعذر تشغيل القناة")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        playerView.player = null
    }
}
