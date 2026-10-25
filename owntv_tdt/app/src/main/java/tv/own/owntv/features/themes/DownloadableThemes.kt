package tv.own.owntv.features.themes

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class InstalledTheme(val theme: ThemePackage, val wallpaper: File?)
data class ThemeStoreState(val catalog: List<ThemeEntry> = emptyList(),
    val installed: Map<String, InstalledTheme> = emptyMap(), val selected: String = "",
    val busy: Boolean = false, val failed: Boolean = false) {
    val active: InstalledTheme? get() = installed[selected]
}

/** Private app storage; failed downloads never replace a working theme. One operation at a time. */
class DownloadableThemes private constructor(context: Context) {
    private val root = File(context.filesDir, "downloadable-themes").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("downloadable-themes", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()
    private val mutable = MutableStateFlow(ThemeStoreState())
    val state = mutable.asStateFlow()
    init { operation {
        val installed = root.listFiles().orEmpty().filter { it.extension == "json" && it.name != "catalog.json" }
            .mapNotNull { file -> runCatching {
                val theme = ThemePackage.parse(file.readText())
                require(file.name == "${theme.id}.json")
                val wall = theme.wallpaper?.let { asset ->
                    File(root, "${asset.sha256}.png").also { require(it.isFile && it.length() == asset.bytes.toLong() && digest(it.readBytes()) == asset.sha256) }
                }
                theme.id to InstalledTheme(theme, wall)
            }.getOrNull() }.toMap()
        val catalog = runCatching { parseCatalog(File(root, "catalog.json").readText()) }.getOrDefault(emptyList())
        mutable.value = mutable.value.copy(installed = installed, catalog = catalog,
            selected = prefs.getString("selected", "").orEmpty().takeIf { it in installed }.orEmpty())
        cleanOrphans()
    } }
    private fun operation(block: () -> Unit) { scope.launch { mutex.withLock {
        mutable.value = mutable.value.copy(busy = true, failed = false)
        try { block() } catch (_: Exception) { mutable.value = mutable.value.copy(failed = true) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    } } }
    private fun read(url: String, limit: Int): ByteArray {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Channelplus-Themes").build()).execute().use { response ->
            check(response.isSuccessful)
            val body = response.body
            require(body.contentLength() <= limit)
            return body.byteStream().use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= limit)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        }
    }
    private fun verified(asset: ThemeAsset): ByteArray = read(asset.url, asset.bytes).also {
        require(it.size == asset.bytes && digest(it) == asset.sha256)
    }
    private fun atomic(file: File, bytes: ByteArray) {
        val temp = File(root, "${file.name}.part")
        try { temp.outputStream().use { it.write(bytes); it.fd.sync() }; check(temp.renameTo(file)) }
        finally { temp.delete() }
    }
    fun refresh() = operation {
        val bytes = read(CATALOG_URL, 128 * 1024)
        val catalog = parseCatalog(bytes.toString(Charsets.UTF_8))
        atomic(File(root, "catalog.json"), bytes)
        mutable.value = mutable.value.copy(catalog = catalog)
    }
    fun download(entry: ThemeEntry) = operation {
        val bytes = verified(entry.asset)
        val theme = ThemePackage.parse(bytes.toString(Charsets.UTF_8))
        require(entry.totalBytes == bytes.size + (theme.wallpaper?.bytes ?: 0))
        require(theme.id == entry.id && theme.version == entry.version && theme.light == entry.light && theme.name == entry.name)
        val wall = theme.wallpaper?.let { asset ->
            val image = verified(asset)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, options)
            require(options.outWidth in 1..3840 && options.outHeight in 1..2160)
            File(root, "${asset.sha256}.png").also { atomic(it, image) }
        }
        atomic(File(root, "${theme.id}.json"), bytes)
        mutable.value = mutable.value.copy(installed = mutable.value.installed + (theme.id to InstalledTheme(theme, wall)))
        cleanOrphans()
    }
    fun select(id: String) = operation {
        require(id.isEmpty() || id in mutable.value.installed)
        check(prefs.edit().putString("selected", id).commit())
        mutable.value = mutable.value.copy(selected = id)
    }
    fun remove(id: String) = operation {
        safeId(id)
        if (mutable.value.selected == id) {
            check(prefs.edit().putString("selected", "").commit())
            mutable.value = mutable.value.copy(selected = "")
        }
        check(!File(root, "$id.json").exists() || File(root, "$id.json").delete())
        mutable.value = mutable.value.copy(installed = mutable.value.installed - id)
        cleanOrphans()
    }
    private fun cleanOrphans() {
        val used = mutable.value.installed.values.mapNotNull { it.wallpaper?.name }.toSet()
        root.listFiles().orEmpty().filter { it.extension == "part" || (it.extension == "png" && it.name !in used) }.forEach { it.delete() }
    }
    companion object {
        private const val CATALOG_URL = "https://raw.githubusercontent.com/Ayanawars/Channelplus/codex/complete-import-channel-review/themes/catalog.json"
        @Volatile private var instance: DownloadableThemes? = null
        fun get(context: Context): DownloadableThemes = instance ?: synchronized(this) {
            instance ?: DownloadableThemes(context.applicationContext).also { instance = it }
        }
    }
}
