package io.github.zhshuaii.tvinbox

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.URLDecoder
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Raw request bodies are streamed directly to the inbox, never parseBody(). */
class UploadServer(val bindAddress: String, port: Int, private val store: InboxStore, private val asset: (String) -> ByteArray) : NanoHTTPD(bindAddress, port) {
    init {
        setAsyncRunner(LimitedRunner())
    }

    override fun serve(session: IHTTPSession): Response {
        val authority = "$bindAddress:$listeningPort"
        if (session.headers["host"] != authority) return json(403, "访问地址不匹配，请重新扫描电视二维码")
        val origin = session.headers["origin"]
        if (origin != null && origin != "http://$authority") return json(403, "不允许跨站请求")
        if (session.headers["sec-fetch-site"] == "cross-site") return json(403, "不允许跨站请求")
        return try {
            if (session.method == Method.GET) {
                val file = when (session.uri) {
                    "/", "/index.html" -> "index.html"
                    "/app.css" -> "app.css"
                    "/app.js" -> "app.js"
                    else -> return json(404, "页面不存在")
                }
                val mime = when (file) {
                    "app.js" -> "text/javascript; charset=utf-8"
                    "app.css" -> "text/css; charset=utf-8"
                    else -> "text/html; charset=utf-8"
                }
                val bytes = asset(file)
                secure(newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong()))
            } else if (session.method == Method.POST && session.uri == "/api/upload") {
                if (session.headers["x-tvinbox-upload"] != "1") return json(403, "请使用电视提供的上传页面")
                if (session.headers.containsKey("transfer-encoding")) return json(411, "需要明确的文件长度")
                if (session.headers["content-type"]?.substringBefore(';')?.trim() != "application/octet-stream") return json(415, "不支持的上传格式")
                val length = session.headers["content-length"]?.toLongOrNull() ?: return json(411, "缺少文件长度")
                val encoded = session.headers["x-file-name"] ?: return json(400, "缺少文件名")
                if (encoded.length > 2048) return json(400, "文件名过长")
                val name = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: IllegalArgumentException) { return json(400, "文件名编码无效") }
                val entry = store.receive(name, length, session.inputStream)
                json(201, "上传完成，请在电视列表中选择安装", true, entry.originalName)
            } else {
                json(405, "不支持的操作")
            }
        } catch (error: InboxException) {
            json(error.status, error.message)
        } catch (_: SocketTimeoutException) {
            json(408, "上传超时，请检查局域网连接")
        } catch (_: Exception) {
            json(500, "接收失败，请检查电视存储空间后重试")
        }
    }

    private fun json(code: Int, message: String, ok: Boolean = false, name: String? = null): Response {
        val status = object : Response.IStatus {
            override fun getRequestStatus() = code
            override fun getDescription() = "$code ${if (code < 400) "OK" else "Error"}"
        }
        val body = JSONObject().put("ok", ok).put("message", message)
        if (name != null) body.put("name", name)
        return secure(newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()))
    }

    private fun secure(response: Response): Response {
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("Referrer-Policy", "no-referrer")
        response.addHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'")
        response.closeConnection(true)
        return response
    }

    private class LimitedRunner : AsyncRunner {
        private val clients = Collections.newSetFromMap(ConcurrentHashMap<ClientHandler, Boolean>())
        private val pool = ThreadPoolExecutor(0, 4, 30L, TimeUnit.SECONDS, SynchronousQueue<Runnable>()) { task -> Thread(task, "tvinbox-http").apply { isDaemon = true } }
        override fun exec(code: ClientHandler) {
            clients.add(code)
            try {
                pool.execute(code)
            } catch (_: RejectedExecutionException) {
                clients.remove(code)
                code.close()
            }
        }
        override fun closed(clientHandler: ClientHandler) {
            clients.remove(clientHandler)
        }
        override fun closeAll() {
            clients.toList().forEach { it.close() }
            pool.shutdownNow()
            clients.clear()
        }
    }
}
