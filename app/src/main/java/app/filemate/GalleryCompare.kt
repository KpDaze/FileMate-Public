package app.filemate

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest

/** A deliberate, cancellable read-only scan. No writes to MediaStore or shared media. */
data class ImageComparisonResult(val matches: List<ImageMatch>, val items: List<IndexedMedia>, val checked: Int,
    val skipped: Int, val visualChecked: Int, val total: Int)
class GalleryCompareScanner(private val context: Context) {
    suspend fun scan(progress: (String) -> Unit): ImageComparisonResult {
        val access = MediaAccess.read(context)
        check(access.images || access.selected) { "Choose photo access in Gallery before comparing images." }
        val snapshot = GalleryScanner(context).scan(access).filter { it.kind == "image" }.sortedByDescending { it.sortTime }
        val signatures = mutableListOf<ImageSignature>();var skipped = 0;var visualChecked = 0
        val job = currentCoroutineContext()
        for((index,item) in snapshot.withIndex()) {
            job.ensureActive();progress("Reading image ${index+1} of ${snapshot.size}")
            if(item.size <= 0 || item.size > 256L*1024*1024) { skipped++;continue }
            val signature = try {
                val digest = MessageDigest.getInstance("SHA-256");var bytes = 0L
                context.contentResolver.openInputStream(item.uri.toUri())?.use { stream ->
                    val buffer = ByteArray(64*1024)
                    while(true) {
                        job.ensureActive();val n = stream.read(buffer);if(n < 0) break
                        bytes += n;check(bytes <= item.size) { "Image changed while reading" };digest.update(buffer,0,n)
                    }
                } ?: error("Image unavailable")
                check(bytes == item.size) { "Image changed while reading" }
                var pixels: IntArray? = null;var width = item.width;var height = item.height
                if(signatures.size < GalleryCompareRules.VISUAL_LIMIT) {
                    try {
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,item.uri.toUri())) { decoder,info,_ ->
                            check(!info.isAnimated) { "Animated images excluded from visual suggestions" }
                            width = info.size.width;height = info.size.height
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE;decoder.setTargetSize(9,8)
                        }
                        try {
                            val raw = IntArray(72);bitmap.getPixels(raw,0,9,0,0,9,8)
                            pixels = raw.map { c ->
                                val alpha = Color.alpha(c)
                                fun white(v: Int) = (v*alpha+255*(255-alpha))/255
                                Color.rgb(white(Color.red(c)),white(Color.green(c)),white(Color.blue(c)))
                            }.toIntArray();visualChecked++
                        } finally { bitmap.recycle() }
                    } catch(e: Exception) { job.ensureActive() /* exact evidence is still valid */ }
                }
                ImageSignature(item.identity,item.name,"${item.volume}/${item.relativePath}",item.size,stamp(item),
                    digest.digest().joinToString("") { "%02x".format(it) },width,height,pixels)
            } catch(e: Exception) { job.ensureActive();skipped++;null }
            if(signature != null) signatures += signature
        }
        job.ensureActive();progress("Checking for changes…")
        val latest = GalleryScanner(context).scan(access).filter { it.kind == "image" }
        check(snapshot.associate { it.identity to stamp(it) } == latest.associate { it.identity to stamp(it) }) {
            "Images or access changed during comparison. Scan again; no files were changed."
        }
        progress("Comparing images…")
        val matches = GalleryCompareRules.matches(signatures) { job.ensureActive() }
        return ImageComparisonResult(matches,snapshot,signatures.size,skipped,visualChecked,snapshot.size)
    }
    companion object {
        fun stamp(item: IndexedMedia) = "${item.generation}:${item.modified}:${item.size}:${item.name}:${item.relativePath}"
    }
}
