package com.oudi.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String, val group: String = "", val logo: String = "")
data class MatchInfo(val home: String, val away: String, val hs: String, val ascore: String, val state: String, val league: String, val time: String)

class MainActivity : Activity() {
    private val gold = Color.rgb(224,184,75)
    private val bg = Color.rgb(8,9,12)
    private val panel = Color.rgb(24,26,32)
    private val muted = Color.rgb(160,164,173)
    private lateinit var root: LinearLayout
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private val officialBeinChannels = listOf(
        Channel("beIN SPORTS — البث الرسمي المجاني", "https://watch.beinsports-apps.com/live-channels", "Sports"),
        Channel("beIN SPORTS NEWS — البث الرسمي المجاني", "https://watch.beinsports-apps.com/live-channels", "Sports")
    )
    private var channels = officialBeinChannels
    private var matches = emptyList<MatchInfo>()
    private var playlistAddress = ""
    private var playlistLoading = true
    private var playlistError = false
    private var section = ""
    private var screen = "home"
    private val sections = listOf("رياضة","أفلام","مسلسلات","أخبار","أطفال","منوعات")
    private val leagues = listOf("eng.1","esp.1","ger.1","ita.1","fra.1","uefa.champions","usa.1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(10)); setBackgroundColor(bg) }
        setContentView(root)
        showHome()
        // Replace any previously saved playlist with the user's selected IPTV-org master list.
        playlistAddress = "https://iptv-org.github.io/iptv/index.m3u"
        getPreferences(MODE_PRIVATE).edit().putString("playlist", playlistAddress).apply()
        loadPlaylist(playlistAddress)
    }

    private fun dp(v:Int):Int = (v * resources.displayMetrics.density).toInt()
    private fun shape(c:Int,r:Float=17f) = GradientDrawable().apply { setColor(c); cornerRadius=r }
    private fun txt(s:String,n:Float,c:Int,b:Boolean=false) = TextView(this).apply {
        text=s; textSize=n; setTextColor(c); if(b) typeface=Typeface.DEFAULT_BOLD
    }
    private fun btn(s:String,action:()->Unit) = TextView(this).apply {
        text=s; textSize=14f; gravity=Gravity.CENTER; setTextColor(gold); background=shape(panel,14f)
        setPadding(15,8,15,8); setOnClickListener { action() }
    }
    private fun header(sub:String, back:Boolean=false) {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val brand=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        brand.addView(txt("OUDI  TV",27f,gold,true))
        brand.addView(txt(sub,11f,muted))
        row.addView(brand)
        row.addView(btn(if(back) "رجوع  ‹" else "الإعدادات  ⚙") {
            if(back) showHome() else sourceDialog()
        },LinearLayout.LayoutParams(-2,44))
        root.addView(row,LinearLayout.LayoutParams(-1,dp(72)).apply { bottomMargin=dp(16) })
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(52,45,29)) },
            LinearLayout.LayoutParams(-1,dp(1)).apply { bottomMargin=dp(16) })
    }
    private fun title(s:String,sub:String="") {
        root.addView(txt(s,21f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=5 })
        if(sub.isNotBlank()) root.addView(txt(sub,12f,muted),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })
    }
    private fun showHome() {
        screen="home"
        root.removeAllViews()
        header("منصة المشاهدة والترفيه")
        val hero=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(52,42,22),Color.rgb(25,26,31))).apply {
                cornerRadius=dp(22).toFloat()
                setStroke(dp(1),Color.rgb(105,83,39))
            }
            setPadding(dp(20),dp(20),dp(20),dp(20))
        }
        hero.addView(txt("أهلاً بك في OUDI TV",13f,muted))
        hero.addView(txt("ترفيهك المفضل\nفي مكان واحد",25f,Color.WHITE,true).apply {
            setPadding(0,dp(8),0,dp(8))
        })
        hero.addView(txt("بث مباشر  •  رياضة  •  مباريات",12f,gold,true))
        root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(22) })
        root.addView(txt("استكشف التطبيق",18f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply {
            bottomMargin=dp(12)
        })
        root.addView(tile("▶","البث المباشر","قنوات رياضية وترفيهية وأخبار"){showSections()},
            LinearLayout.LayoutParams(-1,dp(112)).apply { bottomMargin=dp(12) })
        root.addView(tile("⚽","مباريات اليوم","المواعيد والنتائج وحالة المباريات"){showMatches()},
            LinearLayout.LayoutParams(-1,dp(112)).apply { bottomMargin=dp(18) })
        val status=when {
            playlistLoading -> "جاري تحميل قائمة القنوات..."
            playlistError -> "تعذر تحميل القنوات — افتح الإعدادات لتغيير المصدر"
            else -> "القنوات جاهزة • " + channels.size + " قناة"
        }
        root.addView(txt(status,11f,if(playlistError) Color.rgb(230,110,100) else muted).apply {
            gravity=Gravity.CENTER
            setPadding(dp(4),dp(8),dp(4),dp(8))
        })
        root.addView(txt("أسود × ذهبي   •   OUDI TV",11f,muted).apply {
            gravity=Gravity.CENTER
            setPadding(0,dp(12),0,0)
        })
    }
    private fun tile(icon:String,name:String,desc:String,action:()->Unit)=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL
        gravity=Gravity.CENTER_VERTICAL
        background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(39,35,27),Color.rgb(24,26,32))).apply {
            cornerRadius=dp(19).toFloat()
            setStroke(dp(1),Color.rgb(72,60,38))
        }
        setPadding(dp(16),dp(14),dp(16),dp(14))
        val iconBox=TextView(this).apply {
            text=icon
            textSize=27f
            gravity=Gravity.CENTER
            setTextColor(gold)
            background=shape(Color.rgb(55,46,29),15f)
        }
        addView(iconBox,LinearLayout.LayoutParams(dp(58),dp(58)).apply { marginEnd=dp(14) })
        val labels=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        }
        labels.addView(txt(name,18f,Color.WHITE,true))
        labels.addView(txt(desc,11f,muted).apply { setPadding(0,dp(5),0,dp(6)) })
        labels.addView(txt("اضغط للدخول  ‹",11f,gold,true))
        addView(labels)
        isClickable=true
        isFocusable=true
        setOnClickListener { action() }
    }
    private fun showSections() {
        screen="sections"
        root.removeAllViews()
        header("البث المباشر",true)
        title("اختر القسم",if(playlistLoading) "جاري تحميل القنوات..." else if(playlistError) "تعذر الاتصال بمصدر القنوات" else "تم تحميل " + channels.size + " قناة • اختر تصنيفك")
        val scroll=ScrollView(this).apply { clipToPadding=false; isFillViewport=false }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(6),0,dp(18)) }
        val entries=listOf(
            Triple("⚽","رياضة","قنوات المباريات والرياضة"),
            Triple("🎬","أفلام","أفلام عربية وأجنبية"),
            Triple("📺","مسلسلات","دراما ومسلسلات متنوعة"),
            Triple("◉","أخبار","أخبار محلية وعالمية"),
            Triple("★","أطفال","قنوات الأطفال والكرتون"),
            Triple("♫","منوعات","ترفيه وموسيقى وبرامج")
        )
        entries.forEachIndexed { index, item ->
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
                background=shape(Color.rgb(25,27,34),20f)
                setPadding(dp(18),dp(15),dp(18),dp(15))
                isClickable=true
                isFocusable=true
                setOnClickListener { section=item.second; showChannels() }
            }
            val icon=TextView(this).apply {
                text=item.first; textSize=25f; gravity=Gravity.CENTER
                setTextColor(gold); background=shape(Color.rgb(43,38,27),16f)
            }
            card.addView(icon,LinearLayout.LayoutParams(dp(58),dp(58)))
            val labels=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER_VERTICAL
                layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply {
                    marginStart=dp(16); marginEnd=dp(8)
                }
            }
            labels.addView(txt(item.second,18f,Color.WHITE,true))
            labels.addView(txt(item.third,12f,muted).apply {
                setPadding(0,dp(5),0,0)
            })
            card.addView(labels)
            card.addView(txt("‹",27f,gold,true))
            box.addView(card,LinearLayout.LayoutParams(-1,dp(92)).apply {
                bottomMargin=dp(14)
            })
        }
        scroll.addView(box)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun category(c:Channel,s:String):Boolean {
        val v=(c.group+" "+c.name).lowercase(Locale.ROOT)
        val keys=when(s){
            "رياضة"->listOf("sport","bein","football","soccer","رياض","كرة","cricket")
            "أفلام"->listOf("movie","film","cinema","فيلم","movies")
            "مسلسلات"->listOf("series","drama","مسلسل","دراما","soap")
            "أخبار"->listOf("news","أخبار","خبر")
            "أطفال"->listOf("kids","children","cartoon","أطفال","كرتون","animation")
            else->listOf("entertain","music","lifestyle","documentary","culture","منوع")
        }
        return keys.any{v.contains(it)}
    }
    private var fullscreenPlayer = false

    private fun showChannels() {
        screen = "channels"
        root.removeAllViews()

        if (!fullscreenPlayer) {
            header("البث المباشر  /  " + section, true)
            title(section, "اختر قناة للمشاهدة")
        }

        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val search = EditText(this).apply {
            hint = "ابحث عن قناة..."
            textSize = 14f
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = shape(panel, 13f)
        }

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        scroll.addView(list)

        fun render(q: String = "") {
            list.removeAllViews()
            val found = channels.filter {
                category(it, section) && (it.name.contains(q, true) || it.group.contains(q, true))
            }
            found.take(300).forEachIndexed { index, ch ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = shape(if (index == 0) Color.rgb(37, 34, 25) else panel, 14f)
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { play(ch) }
                }
                val playIcon = TextView(this).apply {
                    text = "▶"
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(gold)
                    background = shape(Color.rgb(43, 38, 27), 12f)
                }
                row.addView(playIcon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                info.addView(txt(ch.name, 13f, Color.WHITE, true))
                info.addView(txt(ch.group.ifBlank { "بث مباشر" }, 10f, muted))
                row.addView(info)
                row.addView(txt("مشاهدة", 10f, gold, true))
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            if (found.isEmpty()) {
                list.addView(txt(
                    if (channels.isEmpty()) "جاري تحميل القنوات..." else "لا توجد قنوات مطابقة في هذا القسم",
                    13f, muted
                ).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(30), dp(8), dp(30)) })
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {
                render(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render()

        val pv = playerView ?: PlayerView(this).also {
            it.useController = true
            it.setBackgroundColor(Color.BLACK)
            playerView = it
        }
        (pv.parent as? ViewGroup)?.removeView(pv)
        pv.player = player

        val videoFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(pv, FrameLayout.LayoutParams(-1, -1))
        }
        val fullButton = TextView(this).apply {
            text = if (fullscreenPlayer) "تصغير  ⤢" else "ملء الشاشة  ⛶"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xCC17191F.toInt())
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), gold)
            }
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setOnClickListener {
                fullscreenPlayer = !fullscreenPlayer
                showChannels()
            }
        }
        videoFrame.addView(fullButton, FrameLayout.LayoutParams(-2, dp(38), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(10), dp(10), 0)
        })

        if (fullscreenPlayer) {
            root.addView(videoFrame, LinearLayout.LayoutParams(-1, 0, 1f))
            return
        }

        root.addView(search, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(10) })

        if (landscape) {
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.FILL
            }
            content.addView(videoFrame, LinearLayout.LayoutParams(0, -1, 2.2f).apply {
                marginEnd = dp(10)
            })
            content.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(videoFrame, LinearLayout.LayoutParams(-1, dp(205)).apply {
                bottomMargin = dp(10)
            })
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
    private fun showMatches() {
        screen="matches";root.removeAllViews();header("مباريات اليوم",true)
        val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        bar.addView(txt("مباريات كرة القدم",20f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(btn("تحديث ↻"){loadMatches()})
        root.addView(bar,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=7})
        root.addView(txt("المصدر: ESPN. قد لا تشمل البيانات كل البطولات المحلية.",11f,muted),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12})
        val scroll=ScrollView(this);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun render(){
            list.removeAllViews()
            if(matches.isEmpty())list.addView(txt("جاري تحميل مباريات اليوم أو لا توجد مباريات متاحة حاليًا.",14f,muted).apply{gravity=Gravity.CENTER;setPadding(8,40,8,40)})
            matches.forEach{m->
                val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=shape(panel,16f);setPadding(14,12,14,12);setOnClickListener{matchChannels(m)}}
                card.addView(txt(m.league,10f,gold))
                val teams=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
                teams.addView(txt(m.home,13f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
                teams.addView(txt(m.hs+" - "+m.ascore,18f,gold,true))
                teams.addView(txt(m.away,13f,Color.WHITE,true).apply{gravity=Gravity.END},LinearLayout.LayoutParams(0,-2,1f))
                card.addView(teams,LinearLayout.LayoutParams(-1,-2).apply{topMargin=12;bottomMargin=9})
                card.addView(txt(m.state+"  •  "+m.time,11f,muted))
                card.addView(txt("اضغط لعرض القنوات الرياضية المتاحة  ›",11f,gold).apply{gravity=Gravity.CENTER;setPadding(0,10,0,0)})
                list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10})
            }
        }
        render();loadMatches{render()}
    }
    private fun matchChannels(m:MatchInfo){
        val sports=channels.filter{category(it,"رياضة")}
        if(sports.isEmpty()){
            AlertDialog.Builder(this).setTitle("القناة الناقلة").setMessage("لا توجد قناة رياضية في قائمة البث الحالية، ولا تتوفر مطابقة موثوقة لقناة ناقلة لهذه المباراة.").setPositiveButton("حسنًا",null).show();return
        }
        AlertDialog.Builder(this).setTitle(m.home+" × "+m.away)
            .setMessage("لا تتوفر مطابقة موثوقة للقناة الناقلة تلقائيًا. اختر قناة رياضية يدويًا؛ قد لا تكون ناقلة للمباراة.")
            .setItems(sports.map{it.name}.toTypedArray()){_,i->play(sports[i])}.setNegativeButton("إلغاء",null).show()
    }
    private fun loadMatches(done:(()->Unit)?=null){
        thread{
            val found=LinkedHashMap<String,MatchInfo>()
            val day=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())
            for(league in leagues)try{
                val con=URL("https://site.api.espn.com/apis/site/v2/sports/soccer/"+league+"/scoreboard?dates="+day).openConnection() as HttpURLConnection
                con.connectTimeout=10000;con.readTimeout=12000;con.setRequestProperty("User-Agent","Mozilla/5.0 OUDI-TV")
                val json=JSONObject(con.inputStream.bufferedReader().use{it.readText()});con.disconnect()
                val events=json.optJSONArray("events")?:continue
                for(i in 0 until events.length()){
                    val event=events.optJSONObject(i)?:continue
                    val comp=event.optJSONArray("competitions")?.optJSONObject(0)?:continue
                    val teams=comp.optJSONArray("competitors")?:continue
                    var home="";var away="";var hs="-";var ascore="-"
                    for(j in 0 until teams.length()){
                        val t=teams.optJSONObject(j)?:continue;val n=t.optJSONObject("team")?.optString("displayName","فريق")?:"فريق"
                        if(t.optString("homeAway")=="home"){home=n;hs=t.optString("score","-")}else{away=n;ascore=t.optString("score","-")}
                    }
                    val typ=comp.optJSONObject("status")?.optJSONObject("type")
                    val state=typ?.optString("shortDetail","")?.ifBlank{typ.optString("description","موعد المباراة")}?:"موعد المباراة"
                    val raw=event.optString("date","")
                    val time=try{SimpleDateFormat("HH:mm",Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'",Locale.US).parse(raw)!!)}catch(_:Exception){""}
                    val leagueName=json.optJSONArray("leagues")?.optJSONObject(0)?.optString("name",league)?:league
                    found[event.optString("id")]=MatchInfo(home,away,hs,ascore,state,leagueName,time)
                }
            }catch(_:Exception){}
            runOnUiThread{matches=found.values.toList();done?.invoke()}
        }
    }
    private fun sourceDialog(){
        val input=EditText(this).apply{setText(playlistAddress);setTextColor(Color.BLACK);hint="https://..."}
        AlertDialog.Builder(this).setTitle("مصدر القنوات").setMessage("أدخل رابط قائمة قنوات بصيغة M3U.").setView(input)
            .setNegativeButton("إلغاء",null).setPositiveButton("تحميل"){_,_->
                val v=input.text.toString().trim()
                if(v.startsWith("http")){playlistAddress=v;getPreferences(MODE_PRIVATE).edit().putString("playlist",v).apply();loadPlaylist(v)}
                else Toast.makeText(this,"الرابط غير صحيح",Toast.LENGTH_SHORT).show()
            }.show()
    }
    private fun loadPlaylist(address:String){
        thread{
            val merged=LinkedHashMap<String,Channel>()
            var successes=0
            address.split("|").map{it.trim()}.filter{it.startsWith("http")}.forEach{source->
                try{
                    val con=URL(source).openConnection() as HttpURLConnection
                    con.connectTimeout=20000;con.readTimeout=30000;con.instanceFollowRedirects=true
                    con.setRequestProperty("User-Agent","Mozilla/5.0 OUDI-TV")
                    val body=con.inputStream.bufferedReader().use{it.readText()}
                    con.disconnect()
                    parseM3u(body).forEach{ch->merged.putIfAbsent(ch.name.lowercase(Locale.ROOT)+"|"+ch.url,ch)}
                    successes++
                }catch(_:Exception){}
            }
            val parsed=merged.values.toList()
            runOnUiThread{
                playlistLoading=false
                playlistError=successes==0 || parsed.isEmpty()
                if(successes>0 && parsed.isNotEmpty()){
                    channels=officialBeinChannels + parsed
                    if(screen=="channels") showChannels()
                    else if(screen=="sections") showSections()
                    else if(screen=="home") showHome()
                    Toast.makeText(this,"تم تحميل "+parsed.size+" قناة",Toast.LENGTH_LONG).show()
                }else{
                    if(screen=="home") showHome() else if(screen=="sections") showSections()
                    Toast.makeText(this,"تعذر تحميل القائمة. تحقق من الاتصال أو غيّر المصدر.",Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun parseM3u(body:String):List<Channel>{
        val out=mutableListOf<Channel>();var name="قناة";var group="";var logo=""
        body.lineSequence().map{it.trim()}.forEach{line->
            if(line.startsWith("#EXTINF",true)){
                name=line.substringAfterLast(',',"قناة").trim().ifBlank{"قناة"}
                group=Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?:""
                logo=Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?:""
            }else if(line.startsWith("http://")||line.startsWith("https://")){out.add(Channel(name,line,group,logo));name="قناة";group="";logo=""}
        }
        return out
    }
    private fun play(ch: Channel) {
        if (ch.url.contains("bein.com", true) || ch.url.contains("beinsports-apps.com", true)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ch.url)))
            } catch (_: Exception) {
                Toast.makeText(this, "تعذر فتح صفحة beIN الرسمية", Toast.LENGTH_SHORT).show()
            }
            return
        }
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                player?.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@MainActivity, "تعذر تشغيل البث؛ قد يكون متوقفًا أو محجوبًا جغرافيًا", Toast.LENGTH_LONG).show()
                    }
                })
            }
            fullscreenPlayer = false
            showChannels()
            playerView?.player = player
            player?.setMediaItem(MediaItem.fromUri(ch.url))
            player?.prepare()
            player?.play()
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر تشغيل القناة", Toast.LENGTH_SHORT).show()
        }
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed(){when(screen){"channels"->showSections();"sections","matches"->showHome();else->super.onBackPressed()}}
    override fun onStop(){super.onStop();player?.release();player=null;playerView?.player=null}
}
