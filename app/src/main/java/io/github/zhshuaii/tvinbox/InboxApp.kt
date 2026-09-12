package io.github.zhshuaii.tvinbox

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.zip.ZipFile

class InboxApp : Application() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private var server: UploadServer? = null
    @Volatile private var requested = false
    @Volatile var store: InboxStore? = null
        private set
    @Volatile var address: String? = null
        private set
    @Volatile var error: String? = null
        private set
    private val preferences by lazy { getSharedPreferences("install-state", Context.MODE_PRIVATE) }
    private val idleStop = Runnable { stopReceiving() }

    override fun onCreate() {
        super.onCreate()
        worker.execute {
            try {
                val held = preferences.getString("held", null)
                store = InboxStore(File(filesDir, "apk-inbox"), ::inspectApk, initialPins = setOfNotNull(held), changed = ::publish)
            } catch (_: Exception) {
                error = "无法初始化安装包目录，请检查电视存储空间"
            }
            publish()
        }
        val manager = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
        manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = networkChanged()
            override fun onLost(network: Network) = networkChanged()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = networkChanged()
        })
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun publish() { main.post { listeners.forEach { it() } } }

    fun startReceiving() {
        main.removeCallbacks(idleStop)
        requested = true
        worker.execute { refreshServer() }
    }

    fun stopReceiving() {
        main.removeCallbacks(idleStop)
        requested = false
        worker.execute {
            server?.stop()
            server = null
            address = null
            publish()
        }
    }

    // A system settings/installer round trip may keep the session briefly alive,
    // but it must not turn into an indefinite background server.
    fun limitExternalSession() { main.postDelayed(idleStop, 10 * 60 * 1000L) }

    private fun networkChanged() {
        if (requested) worker.execute { refreshServer() }
    }

    private fun refreshServer() {
        if (!requested) return
        val inbox = store ?: return
        val host = localAddress()
        if (host != null && server?.bindAddress == host && server?.isAlive == true) return
        server?.stop()
        server = null
        address = null
        if (host == null) {
            error = "未连接可用的局域网，请检查 Wi-Fi 或网线"
            publish()
            return
        }
        for (port in 56321..56325) {
            val candidate = UploadServer(host, port, inbox) { name -> assets.open("web/$name").use { it.readBytes() } }
            try {
                candidate.start(30_000, true)
                server = candidate
                address = "http://$host:${candidate.listeningPort}/"
                error = null
                publish()
                return
            } catch (_: Exception) {
                candidate.stop()
            }
        }
        error = "接收端口无法启动，请重新连接或重启应用"
        publish()
    }

    @Suppress("DEPRECATION")
    private fun localAddress(): String? {
        val manager = getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.sortedBy { network ->
            if (manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) 0 else 1
        }.firstNotNullOfOrNull { network ->
            val caps = manager.getNetworkCapabilities(network)
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                null
            } else {
                manager.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && (it.isSiteLocalAddress || it.isLinkLocalAddress) }?.hostAddress
            }
        }
    }

    fun work(action: (InboxStore) -> String?, result: (String?) -> Unit = {}) {
        worker.execute {
            val message = try {
                val inbox = store ?: throw InboxException(503, "安装包目录尚未准备好")
                action(inbox)
            } catch (failure: Exception) {
                failure.message ?: "操作失败"
            }
            main.post { result(message) }
            publish()
        }
    }

    fun hold(id: String, result: (File?, String?) -> Unit) {
        worker.execute {
            var file: File? = null
            var message: String? = null
            var acquired = false
            try {
                file = store?.pin(id) ?: throw InboxException(503, "安装包目录尚未准备好")
                acquired = true
                if (!preferences.edit().putString("held", id).commit()) throw InboxException(500, "无法记录安装任务")
            } catch (failure: Exception) {
                if (acquired) store?.unpin(id)
                file = null
                message = failure.message ?: "无法打开安装包"
            }
            main.post { result(file, message) }
        }
    }

    fun release(id: String?) {
        if (id == null) return
        worker.execute {
            store?.unpin(id)
            if (preferences.getString("held", null) == id) preferences.edit().remove("held").commit()
            store?.prune()
        }
    }

    fun releaseStaleHold() { release(preferences.getString("held", null)) }

    @Suppress("DEPRECATION")
    private fun inspectApk(file: File): ApkInfo {
        try {
            ZipFile(file).use { zip ->
                val manifest = zip.getEntry("AndroidManifest.xml") ?: throw InboxException(422, "文件不是有效的 APK")
                if (manifest.size !in 1..4L * 1024 * 1024) throw InboxException(422, "APK 清单大小异常")
            }
            val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: throw InboxException(422, "无法识别 APK，请上传完整的独立安装包")
            val appInfo = info.applicationInfo ?: throw InboxException(422, "APK 缺少应用信息")
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(info.packageName)
            return ApkInfo(label.take(100), info.packageName, (info.versionName ?: info.versionCode.toString()).take(80))
        } catch (failure: InboxException) {
            throw failure
        } catch (_: Exception) {
            throw InboxException(422, "APK 文件无效或已损坏")
        }
    }
}
