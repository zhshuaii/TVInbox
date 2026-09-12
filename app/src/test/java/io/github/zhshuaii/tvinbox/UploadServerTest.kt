package io.github.zhshuaii.tvinbox

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpURLConnection
import java.net.URL
import java.net.Socket

class UploadServerTest {
    @get:Rule val folder = TemporaryFolder()

    private fun withServer(action: (UploadServer, InboxStore) -> Unit) {
        val store = InboxStore(folder.newFolder(), { ApkInfo("Example", "example.app", "1.0") }, InboxLimits(reserveBytes = 0))
        val server = UploadServer("127.0.0.1", 0, store) { "asset".toByteArray() }
        server.start(2000, true)
        try { action(server, store) } finally { server.stop() }
    }

    private fun request(server: UploadServer, path: String = "/", origin: String? = null, upload: Boolean = false, marker: Boolean = true): HttpURLConnection {
        val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        if (origin != null) connection.setRequestProperty("Origin", origin)
        if (upload) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            if (marker) connection.setRequestProperty("X-TVInbox-Upload", "1")
            connection.setRequestProperty("X-File-Name", "example.apk")
            connection.setFixedLengthStreamingMode(8)
            connection.outputStream.use { it.write(ByteArray(8)) }
        }
        return connection
    }

    @Test fun servesOnlyBundledWebAssets() = withServer { server, _ ->
        val page = request(server)
        try { assertEquals(200, page.responseCode); assertEquals("no-store", page.getHeaderField("Cache-Control")); assertTrue(page.getHeaderField("Content-Security-Policy").contains("frame-ancestors 'none'")) } finally { page.disconnect() }
        val file = request(server, "/ready/example.apk")
        try { assertEquals(404, file.responseCode) } finally { file.disconnect() }
    }

    @Test fun completeRawUploadOnlyAddsAnInboxEntry() = withServer { server, store ->
        val upload = request(server, "/api/upload", upload = true)
        try { assertEquals(201, upload.responseCode); assertTrue(upload.inputStream.bufferedReader().use { it.readText() }.contains("\"ok\":true")) } finally { upload.disconnect() }
        assertEquals(1, store.snapshot().entries.size)
        assertNull(store.snapshot().upload)
    }

    @Test fun rejectsCrossOriginAndUnmarkedUploads() = withServer { server, store ->
        // HTTP requests have no authentication, but browser cross-origin writes are rejected.
        Socket("127.0.0.1", server.listeningPort).use { socket ->
            socket.soTimeout = 3000
            val request = "GET / HTTP/1.1\r\nHost: 127.0.0.1:${server.listeningPort}\r\nOrigin: http://example.invalid\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray())
            assertTrue(socket.getInputStream().bufferedReader().readLine().contains("403"))
        }
        val unmarked = request(server, "/api/upload", upload = true, marker = false)
        try { assertEquals(403, unmarked.responseCode) } finally { unmarked.disconnect() }
        assertTrue(store.snapshot().entries.isEmpty())
    }
}
