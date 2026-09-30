package app.filemate

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/** Exact content and heuristic suggestions never share a category. No file actions. */
data class ImageSignature(val id: String, val name: String, val folder: String, val size: Long,
    val stamp: String, val sha256: String, val width: Int, val height: Int, val pixels: IntArray? = null)
enum class ImageMatchKind(val title: String) { EXACT("Exact duplicates"), SIMILAR("Similar images"), VERSION("Possible versions") }
data class ImageMatch(val key: String, val kind: ImageMatchKind, val ids: List<String>)

object GalleryCompareRules {
    const val VISUAL_LIMIT = 500
    const val SUGGESTION_LIMIT = 100
    fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    fun group(kind: ImageMatchKind, items: List<ImageSignature>): ImageMatch {
        val sorted = items.sortedBy { it.id }
        val key = digest(kind.name + sorted.joinToString("") { "${it.id.length}:${it.id}:${it.stamp}:${it.sha256};" })
        return ImageMatch(key,kind,sorted.map { it.id })
    }
    fun versionStem(name: String): String {
        val stem = name.substringBeforeLast('.',name).lowercase(Locale.ROOT)
        return stem.replace(Regex("(?:[ _-]+(?:copy|v[0-9]+|version[ _-]*[0-9]+)|[ _-]*\\([0-9]+\\))+$"),"")
    }
    fun similar(a: ImageSignature, b: ImageSignature): Boolean {
        if(a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val ratioA = a.width.toDouble()/a.height;val ratioB = b.width.toDouble()/b.height
        if(abs(ratioA-ratioB)/maxOf(ratioA,ratioB) > .04) return false
        val x = a.pixels ?: return false;val y = b.pixels ?: return false
        if(x.size != 72 || y.size != 72) return false
        fun grey(c: Int) = (((c shr 16) and 255)*30 + ((c shr 8) and 255)*59 + (c and 255)*11)/100
        val gx = x.map(::grey);val gy = y.map(::grey)
        // Flat-colour/blank images are not useful similarity evidence.
        if(gx.max()-gx.min() < 32 || gy.max()-gy.min() < 32) return false
        var edges = 0;var colourError = 0L
        for(row in 0..7) for(col in 0..7) {
            val i = row*9+col
            if((gx[i] > gx[i+1]) != (gy[i] > gy[i+1])) edges++
        }
        for(i in x.indices) for(shift in listOf(0,8,16)) colourError += abs(((x[i] shr shift) and 255)-((y[i] shr shift) and 255))
        return edges <= 6 && colourError.toDouble()/(72*3) <= 18
    }
    fun matches(input: List<ImageSignature>, cancelled: () -> Unit = {}): List<ImageMatch> {
        val items = input.distinctBy { it.id }.filter { it.size > 0 && it.sha256.matches(Regex("[0-9a-f]{64}")) }
        val exact = items.groupBy { it.size to it.sha256 }.values.filter { it.size > 1 }.map { group(ImageMatchKind.EXACT,it) }
        // One representative per exact group prevents repeated approximate suggestions.
        val candidates = items.take(VISUAL_LIMIT).distinctBy { it.size to it.sha256 }
        val visualGroups = mutableListOf<ImageMatch>();val versions = mutableListOf<ImageMatch>()
        for(i in candidates.indices) {
            cancelled()
            for(j in i+1 until candidates.size) {
                val a = candidates[i];val b = candidates[j]
                if(visualGroups.size < SUGGESTION_LIMIT && similar(a,b)) visualGroups += group(ImageMatchKind.SIMILAR,listOf(a,b))
                val stem = versionStem(a.name)
                if(versions.size < SUGGESTION_LIMIT && a.folder == b.folder && stem.length >= 4 && stem == versionStem(b.name) && a.name != b.name)
                    versions += group(ImageMatchKind.VERSION,listOf(a,b))
            }
        }
        return exact + visualGroups + versions
    }
}
