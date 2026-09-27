package app.filemate

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

class VerifiedFileTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source(bytes: ByteArray = "original".toByteArray()): File = temporary.newFile("source").apply { writeBytes(bytes) }
    private fun target() = File(temporary.root,"destination")
    private fun move(source: File, target: File, transfer: VerifiedFileTransfer = VerifiedFileTransfer()) {
        val fingerprint = requireNotNull(ContentFingerprint.read(source))
        transfer.move(source,target,fingerprint.size,fingerprint.hash)
    }

    @Test fun normalMovePreservesFullContentAndModificationTime() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val source = source(bytes)
        assertTrue(source.setLastModified(1_600_000_000_123))
        assertEquals(source.lastModified(),requireNotNull(ContentFingerprint.read(source)).modified)
        val target = target()
        move(source,target)
        assertFalse(source.exists())
        assertArrayEquals(bytes,target.readBytes())
        assertEquals(1_600_000_000_123,target.lastModified())
    }

    @Test fun zeroLengthFileIsFingerprintableAndMovable() {
        val source = source(byteArrayOf())
        val target = target()
        move(source,target)
        assertFalse(source.exists())
        assertTrue(target.isFile)
        assertEquals(0,target.length())
    }

    @Test fun fingerprintUsesContentRatherThanSizeOrTimestamp() {
        val source = source()
        val before = requireNotNull(ContentFingerprint.read(source))
        source.writeText("modified")
        assertTrue(source.setLastModified(before.modified))
        assertEquals(before.size,source.length())
        assertFalse(ContentFingerprint.matches(source,before.size,before.hash))
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedFileTransfer().move(source,target(),before.size,before.hash)
        }
        assertEquals("modified",source.readText())
        assertFalse(target().exists())
    }

    @Test fun lateDestinationCollisionNeverOverwritesEitherFile() {
        val source = source()
        val target = target()
        val transfer = VerifiedFileTransfer(beforeCreate = { it.writeText("another app's file") })
        assertThrows(FileAlreadyExistsException::class.java) { move(source,target,transfer) }
        assertEquals("original",source.readText())
        assertEquals("another app's file",target.readText())
    }

    @Test fun existingDestinationIsUntouched() {
        val source = source()
        val target = target().apply { writeText("keep") }
        assertThrows(FileAlreadyExistsException::class.java) { move(source,target) }
        assertEquals("original",source.readText())
        assertEquals("keep",target.readText())
    }

    @Test fun danglingDestinationSymlinkIsNotFollowedOrReplaced() {
        val source = source()
        val target = target()
        val elsewhere = File(temporary.root,"unrelated")
        Files.createSymbolicLink(target.toPath(),elsewhere.toPath())
        assertThrows(FileAlreadyExistsException::class.java) { move(source,target) }
        assertTrue(Files.isSymbolicLink(target.toPath()))
        assertFalse(elsewhere.exists())
        assertEquals("original",source.readText())
    }

    @Test fun sourceEditDuringTransferKeepsBothCopiesForReview() {
        val source = source()
        val target = target()
        val transfer = VerifiedFileTransfer(afterCopy = { from,_ -> from.writeText("modified") })
        assertThrows(IOException::class.java) { move(source,target,transfer) }
        assertEquals("modified",source.readText())
        assertEquals("original",target.readText())
    }

    @Test fun damagedDestinationNeverCausesSourceRemoval() {
        val source = source()
        val target = target()
        val transfer = VerifiedFileTransfer(afterCopy = { _,to -> to.writeText("damaged!") })
        assertThrows(IOException::class.java) { move(source,target,transfer) }
        assertEquals("original",source.readText())
        assertEquals("damaged!",target.readText())
    }

    @Test fun interruptedCopyLeavesSourceAvailable() {
        val source = source()
        val target = target()
        val transfer = VerifiedFileTransfer(afterCopy = { _,_ -> throw IOException("Simulated interruption") })
        assertThrows(IOException::class.java) { move(source,target,transfer) }
        assertEquals("original",source.readText())
        assertEquals("original",target.readText())
    }

    @Test fun missingHistoricalFingerprintCannotBeInventedForUndo() {
        val source = source()
        assertFalse(ContentFingerprint.matches(source,source.length(),null))
        assertThrows(IllegalArgumentException::class.java) { VerifiedFileTransfer().move(source,target(),source.length(),null) }
        assertEquals("original",source.readText())
        assertFalse(target().exists())
    }
}
