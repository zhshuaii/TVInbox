package io.github.zhshuaii.tvinbox

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class InboxStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_700_000_000_000L
    private val info = ApkInfo("Example", "example.app", "1.0")
    private fun store(root: File = folder.newFolder(), count: Int = 10, bytes: Long = 1024, age: Long = 1000, pins: Set<String> = emptySet(), inspector: (File) -> ApkInfo = { info }) = InboxStore(root, inspector, InboxLimits(count, bytes, age, 0), { now }, { 100_000L }, pins)
    private fun add(store: InboxStore, name: String = "example.apk", bytes: Int = 8) = store.receive(name, bytes.toLong(), ByteArrayInputStream(ByteArray(bytes)))
    private fun expect(code: Int, action: () -> Unit) {
        try { action(); fail("Expected HTTP $code") } catch (error: InboxException) { assertEquals(code, error.status) }
    }

    @Test fun completeUploadIsStoredAndRecovered() {
        val root = folder.newFolder()
        val entry = add(store(root))
        val recovered = store(root).snapshot()
        assertEquals(listOf(entry), recovered.entries)
        assertEquals(8L, recovered.bytes)
        assertTrue(File(root, "incoming").listFiles()!!.isEmpty())
    }

    @Test fun interruptedUploadLeavesNoFileOrReservation() {
        val root = folder.newFolder()
        val inbox = store(root)
        expect(400) { inbox.receive("broken.apk", 10, ByteArrayInputStream(byteArrayOf(1, 2))) }
        assertTrue(inbox.snapshot().entries.isEmpty())
        assertNull(inbox.snapshot().upload)
        assertTrue(File(root, "incoming").listFiles()!!.isEmpty())
        add(inbox)
        assertEquals(1, inbox.snapshot().entries.size)
    }

    @Test fun invalidApkDoesNotBecomeReady() {
        val root = folder.newFolder()
        val inbox = store(root, inspector = { throw InboxException(422, "invalid") })
        expect(422) { add(inbox) }
        assertEquals(0L, inbox.snapshot().bytes)
        assertTrue(File(root, "ready").listFiles()!!.isEmpty())
        assertTrue(File(root, "incoming").listFiles()!!.isEmpty())
    }

    @Test fun rejectsBadNamesBeforeReading() {
        val inbox = store()
        for (name in listOf("../bad.apk", "bad\\name.apk", "bad.apk\n", "a.xapk", "", "x".repeat(181) + ".apk")) {
            expect(400) { add(inbox, name) }
        }
        assertTrue(inbox.snapshot().entries.isEmpty())
    }

    @Test fun quotaAndCountAreReservedBeforeReading() {
        val inbox = store(count = 1)
        add(inbox)
        expect(409) { add(inbox) }
        val small = store(bytes = 10)
        add(small, bytes = 8)
        expect(507) { add(small, bytes = 3) }
        expect(413) { add(small, bytes = 11) }
    }

    @Test fun lowDiskSpaceRejectsUpload() {
        val inbox = InboxStore(folder.newFolder(), { info }, InboxLimits(reserveBytes = 10), { now }, { 15L })
        expect(507) { add(inbox, bytes = 8) }
    }

    @Test fun expirationNeverRemovesPinnedApk() {
        val inbox = store()
        val first = add(inbox)
        inbox.pin(first.id)
        now += 2000
        inbox.prune()
        assertEquals(1, inbox.snapshot().entries.size)
        assertEquals(CleanupResult(0, 1), inbox.clear())
        assertFalse(inbox.delete(first.id))
        inbox.unpin(first.id)
        inbox.prune()
        assertTrue(inbox.snapshot().entries.isEmpty())
    }

    @Test fun onlyOneInstallerMayHoldAFileAtATime() {
        val inbox = store()
        val first = add(inbox)
        val second = add(inbox)
        inbox.pin(first.id)
        expect(409) { inbox.pin(second.id) }
        expect(409) { inbox.pin(first.id) }
        assertFalse(inbox.delete(first.id))
        inbox.unpin(first.id)
        assertTrue(inbox.pin(second.id).isFile)
    }

    @Test fun restoredInstallerLeaseProtectsFileBeforeStartupPruning() {
        val root = folder.newFolder()
        val first = add(store(root))
        now += 2000
        val recovered = store(root, pins = setOf(first.id))
        assertEquals(1, recovered.snapshot().entries.size)
        recovered.unpin(first.id)
        recovered.prune()
        assertTrue(recovered.snapshot().entries.isEmpty())
    }

    @Test fun oldApksArePrunedBeforeNewUpload() {
        val inbox = store(count = 1)
        add(inbox)
        now += 2000
        val next = add(inbox, "new.apk")
        assertEquals(listOf(next), inbox.snapshot().entries)
    }

    @Test fun identicalNamesDoNotOverwriteEachOther() {
        val inbox = store()
        val a = add(inbox)
        val b = add(inbox)
        assertNotEquals(a.id, b.id)
        assertEquals(2, inbox.snapshot().entries.size)
    }

    @Test fun startupRemovesAbandonedPartsAndRecoversMissingMetadata() {
        val root = folder.newFolder()
        val first = add(store(root))
        File(root, "ready/${first.id}.json").writeText("broken json")
        File(root, "incoming/stale.part").writeText("partial")
        val recovered = store(root)
        assertEquals(1, recovered.snapshot().entries.size)
        assertTrue(File(root, "incoming").listFiles()!!.isEmpty())
        assertEquals(info, recovered.snapshot().entries.single().info)
    }

    @Test fun secondUploadIsRejectedAndClearSkipsLiveTransfer() {
        val inbox = store()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val stream = object : InputStream() {
            override fun read(): Int = 1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                buffer.fill(1, offset, offset + length)
                return length
            }
        }
        val thread = Thread { try { inbox.receive("first.apk", 8, stream) } catch (error: Throwable) { failure.set(error) } }
        thread.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            expect(409) { add(inbox) }
            assertEquals(CleanupResult(0, 1), inbox.clear())
        } finally {
            release.countDown()
            thread.join(5000)
        }
        assertNull(failure.get())
        assertEquals(1, inbox.snapshot().entries.size)
    }
}
