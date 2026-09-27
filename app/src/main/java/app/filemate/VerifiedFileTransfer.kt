package app.filemate

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

data class ContentFingerprint(val size: Long, val modified: Long, val hash: String) {
    companion object {
        /** A failed or changing read never becomes a trusted fingerprint. */
        fun read(file: File): ContentFingerprint? = runCatching { readChecked(file) }.getOrNull()

        internal fun readChecked(file: File): ContentFingerprint {
            val path = file.toPath()
            val before = Files.readAttributes(path,BasicFileAttributes::class.java,NOFOLLOW_LINKS)
            check(before.isRegularFile) { "Not a regular file" }
            // Android NIO attributes can round to seconds while File.lastModified()
            // (used by the scanner/journal) retains milliseconds. Compare like with like.
            val modified = file.lastModified()
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            Files.newInputStream(path,READ,NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                while(true) {
                    val count = input.read(buffer)
                    if(count < 0) break
                    if(count > 0) {
                        size += count
                        check(size <= before.size()) { "File changed while reading" }
                        digest.update(buffer,0,count)
                    }
                }
            }
            val after = Files.readAttributes(path,BasicFileAttributes::class.java,NOFOLLOW_LINKS)
            check(after.isRegularFile && size == before.size() && size == after.size() &&
                before.lastModifiedTime() == after.lastModifiedTime() && before.fileKey() == after.fileKey() &&
                file.lastModified() == modified) {
                "File changed while reading"
            }
            return ContentFingerprint(size,modified,digest.digest().joinToString("") { "%02x".format(it) })
        }

        fun matches(file: File, size: Long, hash: String?): Boolean {
            if(hash == null) return false
            val actual = read(file) ?: return false
            return actual.size == size && actual.hash == hash
        }
    }
}

/**
 * CREATE_NEW reserves the final name atomically; no rename/replace fallback is allowed.
 * A move is a verified, flushed copy followed by source removal. If copying or checking
 * fails, keep the source and any new partial copy for review. Never clean up a path that
 * another app may have changed. Recovery only reconciles metadata, never deletes a copy.
 * The two callbacks are internal deterministic failure/race seams for fixture tests.
 */
class VerifiedFileTransfer internal constructor(
    private val beforeCreate: (File) -> Unit = {},
    private val afterCopy: (File,File) -> Unit = { _,_ -> }
) {
    fun move(source: File, target: File, expectedSize: Long, expectedHash: String?) {
        require(expectedHash != null) { "No original content fingerprint is available. Nothing was moved." }
        val original = ContentFingerprint.read(source)
        require(original != null && original.size == expectedSize && original.hash == expectedHash) {
            "The file content changed or could not be read. Nothing was moved."
        }
        if(Files.exists(target.toPath(),NOFOLLOW_LINKS)) throw FileAlreadyExistsException(target.path)
        beforeCreate(target)
        // Opening the source first means an unreadable source does not create an empty target.
        FileChannel.open(source.toPath(),READ,NOFOLLOW_LINKS).use { input ->
            FileChannel.open(target.toPath(),CREATE_NEW,WRITE).use { output ->
                val buffer = ByteBuffer.allocate(64 * 1024)
                var copied = 0L
                while(true) {
                    buffer.clear()
                    val count = input.read(buffer)
                    if(count < 0) break
                    copied += count
                    if(copied > expectedSize) throw IOException("The source grew during the copy; both paths need review.")
                    buffer.flip()
                    while(buffer.hasRemaining()) output.write(buffer)
                }
                if(copied != expectedSize) throw IOException("The source changed during the copy; both paths need review.")
                output.force(true)
            }
        }
        afterCopy(source,target)
        if(!ContentFingerprint.matches(target,expectedSize,expectedHash)) {
            throw IOException("The new copy could not be verified. The source was kept; both paths need review.")
        }
        if(!target.setLastModified(original.modified)) {
            throw IOException("The modification time could not be preserved. Both paths were kept for review.")
        }
        if(ContentFingerprint.read(source) != original || !ContentFingerprint.matches(target,expectedSize,expectedHash)) {
            throw IOException("A file changed during the move. Both paths were kept for review.")
        }
        Files.delete(source.toPath())
    }
}
