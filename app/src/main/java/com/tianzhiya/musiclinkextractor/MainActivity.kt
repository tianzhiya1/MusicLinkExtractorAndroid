package com.tianzhiya.musiclinkextractor

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (intent?.action == android.content.Intent.ACTION_SEND) {
            input.setText(intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: "")
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "音乐链接提取器"
            textSize = 24f
            setTextColor(0xFF172033.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "粘贴音乐网页，查找页面公开的音频资源地址"
            textSize = 14f
            setTextColor(0xFF657086.toInt())
            setPadding(0, dp(6), 0, dp(16))
        })
        input = EditText(this).apply {
            hint = "https://example.com/music-page"
            textSize = 15f
            minLines = 2
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        runButton = Button(this).apply {
            text = "开始提取"
            isAllCaps = false
            setOnClickListener { extract() }
        }
        root.addView(runButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        status = TextView(this).apply {
            text = "输入网页地址后开始分析。"
            textSize = 14f
            setTextColor(0xFF45516A.toInt())
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "仅用于公开可访问或你有权访问的音频资源；不会绕过登录、付费限制或 DRM。"
            textSize = 11f
            setTextColor(0xFF7A8496.toInt())
            setPadding(0, dp(10), 0, 0)
        })
        setContentView(root)
    }

    private fun extract() {
        val address = input.text.toString().trim()
        val parsed = runCatching { URL(address) }.getOrNull()
        if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
            status.text = "请输入有效的 http 或 https 网页地址。"
            return
        }
        runButton.isEnabled = false
        status.text = "正在读取网页并查找音频资源…"
        results.removeAllViews()
        executor.execute {
            try {
                val conn = URL(address).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("网页返回 HTTP $code")
                val bytes = conn.inputStream.use { it.readBytes() }
                val charsetName = conn.contentType?.let {
                    Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.trim()
                } ?: "UTF-8"
                val html = String(bytes, runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8))
                val base = conn.url.toString()
                conn.disconnect()
                val urls = findAudioUrls(html, base)
                handler.post {
                    runButton.isEnabled = true
                    if (urls.isEmpty()) {
                        status.text = "没有找到可直接访问的音频地址。某些网站使用动态脚本、登录验证或受保护的媒体流，静态分析无法提取。"
                    } else {
                        status.text = "找到 ${urls.size} 个可能的音频资源："
                        urls.forEachIndexed { i, value -> addResult(i + 1, value) }
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    runButton.isEnabled = true
                    status.text = "提取失败：${e.message ?: "网络错误"}"
                }
            }
        }
    }

    private fun findAudioUrls(html: String, base: String): List<String> {
        val text = html.replace("&amp;", "&").replace("\\/", "/")
            .replace("\\u0026", "&").replace("\\u003d", "=")
        val candidates = linkedSetOf<String>()
        val patterns = listOf(
            Regex("""(?is)<(?:audio|source)[^>]+(?:src|data-src)\s*=\s*["']([^"']+)["']"""),
            Regex("""(?is)<meta[^>]+(?:property|name)\s*=\s*["'](?:og:audio|twitter:audio)["'][^>]+content\s*=\s*["']([^"']+)["']"""),
            Regex("""(?is)<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["'](?:og:audio|twitter:audio)["']"""),
            Regex("""(?i)["'](?:audioUrl|audio_url|contentUrl|file|src)["']\s*:\s*["']([^"']+)["']"""),
            Regex("""(?i)(https?:\\?/\\?/[^\s"'<>\\]+?\.(?:mp3|m4a|aac|ogg|opus|wav|flac|m3u8|mpd)(?:\?[^\s"'<>\\]*)?)""")
        )
        patterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val raw = match.groupValues[1].trim().replace("\\/", "/")
                val resolved = runCatching { URI(base).resolve(raw).toURL().toString() }.getOrDefault(raw)
                if (isAudioUrl(resolved)) candidates.add(resolved)
            }
        }
        return candidates.take(100)
    }

    private fun isAudioUrl(value: String): Boolean {
        if (!value.startsWith("https://", true) && !value.startsWith("http://", true)) return false
        val path = runCatching { URL(value).path.lowercase(Locale.ROOT) }.getOrDefault("")
        return listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".m3u8", ".mpd").any { path.endsWith(it) }
    }

    private fun addResult(index: Int, link: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        card.addView(TextView(this).apply {
            text = "$index. ${runCatching { URL(link).path.substringAfterLast('/').ifBlank { "音频资源" } }.getOrDefault("音频资源")}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF202B40.toInt())
        })
        card.addView(TextView(this).apply {
            text = link
            textSize = 12f
            setTextColor(0xFF52627C.toInt())
            setPadding(0, dp(6), 0, dp(8))
        })
        card.addView(Button(this).apply {
            text = "复制链接"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("音频链接", link))
                Toast.makeText(this@MainActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        })
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
