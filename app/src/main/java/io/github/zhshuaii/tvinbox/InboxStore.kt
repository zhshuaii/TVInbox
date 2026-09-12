package io.github.zhshuaii.tvinbox

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class InboxException(val status: Int, override val message: String) : IOException(message)

data class ApkInfo(val label: String, val packageName: String, val version: String)
data class ApkEntry(val id: String, val originalName: String, val info: ApkInfo, val size: Long, val receivedAt: Long)
data class UploadProgress(val name: String, val received: Long, val total: Long)
data class InboxSnapshot(val entries: List<ApkEntry>, val upload: UploadProgress?, val bytes: Long)
data class CleanupResult(val deleted: Int, val skipped: Int)
data class InboxLimits(val count: Int = 10, val bytes: Long = 512L * 1024 * 1024, val ageMillis: Long = 7L * 24 * 60 * 60 * 1000, val reserveBytes: Long = 16L * 1024 * 1024)

/** A bounded private inbox. Only fully received, inspected APKs enter ready/. */
class InboxStore(
    private val root: File,
    private val inspect: (File) -> ApkInfo,
    private val limits: InboxLimits = InboxLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val availableSpace: () -> Long = { root.usableSpace },
    initialPins: Set<String> = emptySet(),
    private val changed: () -> Unit = {}
) {
    private val lock = Any()
    private val incoming = File(root, "incoming")
    private val ready = File(root, "ready")
    private val entries = linkedMapOf<String, ApkEntry>()
    private val pinned = initialPins.toMutableSet()
    private var activeId: String? = null
    private var progress: UploadProgress? = null

    init {
        check(incoming.mkdirs() || incoming.isDirectory) { "无法创建接收目录" }
        check(ready.mkdirs() || ready.isDirectory) { "无法创建安装包目录" }
        recover()
    }

    fun snapshot(): InboxSnapshot = synchronized(lock) {
        val rows = entries.values.sortedByDescending { it.receivedAt }
        InboxSnapshot(rows, progress, rows.sumOf { it.size } + (progress?.received ?: 0L))
    }

    fun receive(name: String, size: Long, input: InputStream): ApkEntry {
        validateName(name)
        if (size <= 0L || size > limits.bytes) throw InboxException(413, "APK 为空或超过容量上限")
        val id = UUID.randomUUID().toString()
        synchronized(lock) {
            if (activeId != null) throw InboxException(409, "已有文件正在上传，请稍后重试")
            pruneLocked()
            if (entries.size >= limits.count) throw InboxException(409, "安装包数量已满，请在电视端删除或清空")
            if (entries.values.sumOf { it.size } > limits.bytes - size) throw InboxException(507, "收件箱空间不足，请在电视端清理")
            if (availableSpace() < size + limits.reserveBytes) throw InboxException(507, "电视可用存储空间不足")
            activeId = id
            progress = UploadProgress(name, 0, size)
        }
        changed()
        val partial = File(incoming, "$id.part")
        val completed = File(incoming, "$id.apk")
        val destination = File(ready, "$id.apk")
        try {
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                var lastNotice = 0L
                val started = System.nanoTime()
                while (received < size) {
                    if (Thread.currentThread().isInterrupted) throw InboxException(408, "上传已中断")
                    if ((System.nanoTime() - started) / 1_000_000 > 15 * 60 * 1000L) throw InboxException(408, "上传超时")
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), size - received).toInt())
                    if (read < 0) throw InboxException(400, "文件未完整接收，请重新上传")
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    received += read
                    synchronized(lock) { progress = UploadProgress(name, received, size) }
                    val tick = System.nanoTime() / 1_000_000
                    if (tick - lastNotice >= 200 || received == size) {
                        changed()
                        lastNotice = tick
                    }
                }
                output.fd.sync()
            }
            if (!partial.renameTo(completed)) throw IOException("无法完成接收文件")
            val info = inspect(completed)
            val entry = ApkEntry(id, name, info, size, clock())
            synchronized(lock) {
                if (Thread.currentThread().isInterrupted) throw InboxException(408, "上传已中断")
                if (!completed.renameTo(destination)) throw IOException("无法保存安装包")
                try {
                    saveMetadata(entry)
                    destination.setLastModified(entry.receivedAt)
                    entries[id] = entry
                } catch (error: Exception) {
                    destination.delete()
                    throw error
                }
            }
            return entry
        } finally {
            partial.delete()
            completed.delete()
            synchronized(lock) {
                if (activeId == id) {
                    activeId = null
                    progress = null
                }
            }
            changed()
        }
    }

    fun pin(id: String): File = synchronized(lock) {
        if (id !in entries || !File(ready, "$id.apk").isFile) throw InboxException(404, "安装包已不存在")
        if (pinned.isNotEmpty()) throw InboxException(409, "已有安装操作正在处理中")
        pinned.add(id)
        File(ready, "$id.apk")
    }

    fun unpin(id: String) = synchronized(lock) { pinned.remove(id); Unit }

    fun delete(id: String): Boolean {
        val removed = synchronized(lock) { deleteLocked(id) }
        changed()
        return removed
    }

    fun clear(): CleanupResult {
        val result = synchronized(lock) {
            val ids = entries.keys.toList()
            val removed = ids.count { deleteLocked(it) }
            // Active transfers own their temporary files; never delete those here.
            if (activeId == null) incoming.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            CleanupResult(removed, ids.size - removed + if (activeId != null) 1 else 0)
        }
        changed()
        return result
    }

    fun prune() {
        synchronized(lock) { pruneLocked() }
        changed()
    }

    private fun pruneLocked() {
        val expired = entries.values.filter { clock() - it.receivedAt >= limits.ageMillis }.map { it.id }
        expired.forEach { deleteLocked(it) }
    }

    private fun deleteLocked(id: String): Boolean {
        if (id in pinned || id !in entries) return false
        val apk = File(ready, "$id.apk")
        if (apk.exists() && !apk.delete()) return false
        File(ready, "$id.json").delete()
        File(ready, "$id.json.tmp").delete()
        entries.remove(id)
        return true
    }

    private fun saveMetadata(entry: ApkEntry) {
        val json = JSONObject().put("originalName", entry.originalName).put("label", entry.info.label)
        json.put("packageName", entry.info.packageName).put("version", entry.info.version).put("receivedAt", entry.receivedAt)
        val temp = File(ready, "${entry.id}.json.tmp")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (!temp.renameTo(File(ready, "${entry.id}.json"))) throw IOException("无法保存安装包信息")
        } finally {
            temp.delete()
        }
    }

    private fun recover() {
        // Called once before the server starts, so no live upload can be removed.
        incoming.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val validName = Regex("[0-9a-f-]{36}\\.apk")
        ready.listFiles()?.filter { it.isFile && validName.matches(it.name) }?.forEach { file ->
            val id = file.nameWithoutExtension
            try {
                val metadata = File(ready, "$id.json")
                val json = if (metadata.isFile && metadata.length() <= 16_384) runCatching { JSONObject(metadata.readText()) }.getOrNull() else null
                val info = if (json != null) runCatching { ApkInfo(json.getString("label"), json.getString("packageName"), json.getString("version")) }.getOrNull() else null
                val receivedAt = (json?.optLong("receivedAt", file.lastModified()) ?: file.lastModified()).coerceAtMost(clock())
                val entry = ApkEntry(id, json?.optString("originalName", file.name) ?: file.name, info ?: inspect(file), file.length(), receivedAt)
                if (info == null) saveMetadata(entry)
                entries[id] = entry
            } catch (_: Exception) {
                file.delete()
                File(ready, "$id.json").delete()
            }
        }
        ready.listFiles()?.filter { it.name.endsWith(".tmp") || (it.name.endsWith(".json") && it.nameWithoutExtension !in entries) }?.forEach { it.delete() }
        pruneLocked()
    }

    companion object {
        fun validateName(name: String) {
            if (name.length !in 1..180 || !name.endsWith(".apk", ignoreCase = true) || name.any { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }) {
                throw InboxException(400, "请选择名称有效的单个 .apk 文件")
            }
        }
    }
}
