package io.github.zhshuaii.tvinbox

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val app get() = application as InboxApp
    private val listener: () -> Unit = { render() }
    private var previousAddress: String? = null
    private var previousEntries: List<ApkEntry>? = null
    private var externalId: String? = null
    private var externalFile: File? = null
    private var externalStage: String? = null
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(R.layout.activity_main)
        externalId = savedInstanceState?.getString("externalId")
        externalStage = savedInstanceState?.getString("externalStage")
        if (externalStage == "preparing") {
            app.release(externalId)
            externalId = null
            externalStage = null
        }
        externalFile = externalId?.let { File(filesDir, "apk-inbox/ready/$it.apk") }
        if (externalId == null) app.releaseStaleHold()
        findViewById<Button>(R.id.exit_button).tag = "exit"
        findViewById<Button>(R.id.retry_button).tag = "retry"
        findViewById<Button>(R.id.clear_button).tag = "clear"
        findViewById<Button>(R.id.exit_button).setOnClickListener { confirmExit() }
        findViewById<Button>(R.id.retry_button).setOnClickListener { app.startReceiving() }
        findViewById<Button>(R.id.clear_button).setOnClickListener {
            AlertDialog.Builder(this).setTitle("清空安装包？").setMessage("仅删除轻收接收的 APK，不会卸载应用。正在使用的文件将被跳过。")
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear) { _, _ ->
                    app.work({ store -> val result = store.clear(); "已删除 ${result.deleted} 个，跳过 ${result.skipped} 个" }, ::message)
                }.show()
        }
    }

    override fun onStart() {
        super.onStart()
        app.addListener(listener)
        app.startReceiving()
        render()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // Activity results may arrive before onResume; always restore receiving.
        app.startReceiving()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        app.removeListener(listener)
        if (externalStage == null) app.stopReceiving() else app.limitExternalSession()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("externalId", externalId)
        outState.putString("externalStage", externalStage)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { confirmExit() }

    private fun confirmExit() {
        if (app.store?.snapshot()?.upload != null) {
            AlertDialog.Builder(this).setTitle("正在上传").setMessage("退出会中断当前上传，是否退出？").setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exit) { _, _ -> app.stopReceiving(); finish() }.show()
        } else {
            app.stopReceiving()
            finish()
        }
    }

    private fun render() {
        if (isFinishing || isDestroyed) return
        val url = app.address
        findViewById<TextView>(R.id.address).text = url ?: "—"
        findViewById<TextView>(R.id.server_status).text = app.error ?: if (url != null) "局域网上传已开启" else "正在准备接收服务…"
        if (url != previousAddress) {
            previousAddress = url
            val qr = findViewById<ImageView>(R.id.qr_image)
            if (url == null) qr.setImageDrawable(null) else qr.setImageBitmap(qrBitmap(url))
        }
        val snapshot = app.store?.snapshot() ?: return
        findViewById<TextView>(R.id.list_title).text = "安装包列表（${snapshot.entries.size}）"
        findViewById<TextView>(R.id.storage_summary).text = "已用 ${sizeText(snapshot.bytes)} / 512 MiB"
        val progress = findViewById<TextView>(R.id.upload_status)
        progress.visibility = if (snapshot.upload != null) View.VISIBLE else View.GONE
        snapshot.upload?.let { upload -> progress.text = "接收 ${upload.name} · ${upload.received * 100 / upload.total}%" }
        findViewById<Button>(R.id.clear_button).isEnabled = snapshot.entries.isNotEmpty()
        if (snapshot.entries != previousEntries) {
            previousEntries = snapshot.entries
            rebuildRows(snapshot.entries)
        }
    }

    private fun rebuildRows(entries: List<ApkEntry>) {
        val rows = findViewById<LinearLayout>(R.id.apk_rows)
        val previousFocus = currentFocus?.tag as? String
        rows.removeAllViews()
        if (entries.isEmpty()) {
            rows.addView(TextView(this).apply { setText(R.string.empty); textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(-1, dp(150)))
            return
        }
        entries.forEach { entry ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply { text = entry.info.label.ifBlank { entry.originalName }; textSize = 18f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            val timestamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.receivedAt))
            labels.addView(TextView(this).apply { text = "${entry.info.version} · ${sizeText(entry.size)} · $timestamp"; textSize = 12f; setTextColor(Color.parseColor("#ADB9CB")); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton(getString(R.string.install), "install:${entry.id}") { install(entry.id) }, LinearLayout.LayoutParams(dp(68), dp(44)).apply { marginStart = dp(10) })
            row.addView(actionButton(getString(R.string.delete), "delete:${entry.id}") {
                AlertDialog.Builder(this).setTitle("删除安装包？").setMessage(entry.originalName).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ -> app.work({ store -> if (store.delete(entry.id)) "已删除安装包" else "文件正在使用或无法删除" }, ::message) }.show()
            }, LinearLayout.LayoutParams(dp(68), dp(44)).apply { marginStart = dp(8) })
            rows.addView(row, LinearLayout.LayoutParams(-1, dp(66)))
            rows.addView(View(this).apply { setBackgroundColor(Color.parseColor("#2B3A4C")) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val target = previousFocus?.let { rows.findViewWithTag<View>(it) }
        if (target != null) target.requestFocus() else if (previousFocus == null || previousFocus.startsWith("install:") || previousFocus.startsWith("delete:")) rows.findViewWithTag<View>("install:${entries.first().id}")?.requestFocus()
    }

    private fun actionButton(label: String, key: String, action: () -> Unit): Button {
        return Button(this, null, 0, R.style.ActionButton).apply { text = label; tag = key; isFocusable = true; setOnClickListener { action() } }
    }

    private fun install(id: String) {
        if (externalId != null) { message("已有安装操作正在处理中"); return }
        externalId = id
        externalStage = "preparing"
        app.hold(id) { file, error ->
            if (isFinishing || isDestroyed || !resumed) { finishExternal(); return@hold }
            if (file == null) { finishExternal(); message(error); return@hold }
            externalFile = file
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                externalStage = "permission"
                try {
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")), REQUEST_PERMISSION)
                } catch (_: Exception) {
                    finishExternal()
                    message("请在电视系统设置中允许轻收安装未知来源应用")
                }
            } else {
                openSystemInstaller()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openSystemInstaller() {
        val file = externalFile ?: run { finishExternal(); return }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("APK", uri)
            val handlers = packageManager.queryIntentActivities(intent, 0).filter { result ->
                result.activityInfo.exported && (result.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            }
            val handler = handlers.firstOrNull { it.activityInfo.packageName.contains("packageinstaller") } ?: handlers.firstOrNull() ?: throw IllegalStateException("未找到系统原生安装器")
            intent.component = ComponentName(handler.activityInfo.packageName, handler.activityInfo.name)
            externalStage = "installer"
            startActivityForResult(intent, REQUEST_INSTALL)
        } catch (error: Exception) {
            finishExternal()
            message(error.message ?: "无法打开系统安装器")
        }
    }

    @Deprecated("Platform callback is kept for the small native Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSION) {
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) openSystemInstaller() else { finishExternal(); message("尚未授予安装权限，安装包已保留") }
        } else if (requestCode == REQUEST_INSTALL) {
            // ACTION_VIEW has no trustworthy installation-success result.
            // Returning from the installer only releases the file lease.
            finishExternal()
        }
    }

    private fun finishExternal() {
        app.release(externalId)
        externalId = null
        externalFile = null
        externalStage = null
        if (!resumed) app.stopReceiving()
    }

    private fun qrBitmap(url: String): Bitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 384, 384, mapOf(EncodeHintType.MARGIN to 3))
        val pixels = IntArray(384 * 384) { index -> if (matrix[index % 384, index / 384]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, 384, 384, Bitmap.Config.ARGB_8888)
    }

    private fun message(text: String?) { if (text != null && !isFinishing) Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun sizeText(bytes: Long) = String.format(Locale.getDefault(), "%.1f MiB", bytes / 1048576.0)

    companion object {
        private const val REQUEST_PERMISSION = 101
        private const val REQUEST_INSTALL = 102
    }
}
