package app.filemate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
fun GalleryCompareScreen(app: FileMateApp, resumed: Boolean, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ImageComparisonResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Scan accessible images on this phone. Your photos and project assignments stay unchanged.") }
    var kind by remember { mutableStateOf(ImageMatchKind.EXACT) }
    var kept by remember { mutableStateOf(setOf<String>()) }
    var showKept by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ImageMatch?>(null) }
    var left by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(1) }
    var large by remember { mutableStateOf<IndexedMedia?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun cancel() { job?.cancel() }
    fun back() { if(large != null) large = null else if(selected != null) selected = null else { cancel();onBack() } }
    BackHandler { back() }
    LaunchedEffect(resumed) {
        if(!resumed) { cancel();result = null;selected = null;large = null;message = "Scan again after returning. Access or images may have changed." }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun scan() {
        if(running || !resumed) return
        result = null;selected = null;running = true;message = "Preparing comparison…"
        job = scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    GalleryCompareScanner(app).scan { stage -> scope.launch { message = stage } }
                }
                val stored = withContext(Dispatchers.IO) {
                    val projects = app.store.mediaItems().associateBy { it.identity }
                    output.copy(items = output.items.map { item -> projects[item.identity]?.let {
                        item.copy(projectId = it.projectId,projectName = it.projectName,projectConfidence = it.projectConfidence)
                    } ?: item }) to app.store.state("gallery_compare_kept_v1").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
                }
                result = stored.first;kept = stored.second
                message = "Scan complete. ${output.checked} of ${output.total} images read; ${output.skipped} skipped. Nothing changed."
            } catch(e: CancellationException) { message = "Scan cancelled. No files or assignments changed.";throw e }
            catch(e: Exception) { message = e.message ?: "Comparison could not finish. No files changed." }
            finally { running = false }
        }
    }
    fun keep(match: ImageMatch, value: Boolean) {
        if(saving) return
        saving = true
        scope.launch {
            try {
                val updated = (if(value) kept + match.key else kept - match.key).toList().takeLast(2000).toSet()
                withContext(Dispatchers.IO) { app.store.state("gallery_compare_kept_v1",updated.joinToString(",")) }
                kept = updated;selected = null
                message = if(value) "Kept all ${match.ids.size} images. This group is dismissed; files and assignments are unchanged." else "Group restored for review. Files and assignments are unchanged."
            } catch(e: Exception) { message = "Could not save that review. Try again." }
            finally { saving = false }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selected?.key) { listState.scrollToItem(0) }
    val active = selected
    val comparisonItems = active?.ids?.mapNotNull { id -> result?.items?.find { it.identity == id } }.orEmpty()
    LazyColumn(modifier.fillMaxSize(),state = listState,contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = { back() }) { Text(if(active == null) "Gallery" else "Comparison results") } }
        item { Text(active?.kind?.title ?: "Compare images",fontSize = 28.sp,fontWeight = FontWeight.Bold) }
        if(active == null) {
            item { Text("Exact duplicates have identical file contents. Similar images and possible versions are suggestions, not proof.") }
            item { Text("Review only. Nothing is selected for deletion or moved, including camera photos.",fontSize = 13.sp) }
            item { if(running) Button(onClick = { cancel();message = "Scan cancelled. No files or assignments changed." }) { Text("Cancel scan") }
                else Button(enabled = resumed,onClick = { scan() }) { Text(if(result == null) "Scan images" else "Scan again") } }
            if(running) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Text(message,fontSize = 14.sp) }
            result?.let { output ->
                item { Text("Visual comparison: ${output.visualChecked} images decoded among the first ${minOf(output.checked,GalleryCompareRules.VISUAL_LIMIT)} successfully read images, newest first. Version suggestions use those same first ${minOf(output.checked,GalleryCompareRules.VISUAL_LIMIT)} images. Up to 100 suggestions per category. Unreadable images and files over 256 MB are skipped. No matches does not prove there are no other copies.",fontSize = 12.sp) }
                item { Column { ImageMatchKind.entries.forEach { option ->
                    val count = output.matches.count { it.kind == option && (showKept || it.key !in kept) }
                    FilterChip(selected = kind == option,onClick = { kind = option },label = { Text("${option.title} ($count)") })
                } } }
                item { TextButton(onClick = { showKept = !showKept }) { Text(if(showKept) "Hide kept groups" else "Show kept groups") } }
                val matches = output.matches.filter { it.kind == kind && (showKept || it.key !in kept) }
                if(matches.isEmpty()) item { Text("No ${kind.title.lowercase()} to review in these results.") }
                items(matches,key = { it.key }) { match ->
                    OutlinedCard(onClick = { selected = match;left = 0;right = 1 },modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${match.ids.size} images${if(match.key in kept) " · Kept all" else ""}",fontWeight = FontWeight.SemiBold)
                            Text(match.ids.take(2).mapNotNull { id -> output.items.find { it.identity == id }?.name }.joinToString("\n"),fontSize = 13.sp)
                            Text("Compare side by side",color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else if(comparisonItems.size >= 2) {
            item { Text(when(active.kind) {
                ImageMatchKind.EXACT -> "Full file contents match. Names, locations and project assignments may differ."
                ImageMatchKind.SIMILAR -> "Similar small-image patterns and colours. Crops or edits may be missed, and unrelated images may look similar."
                ImageMatchKind.VERSION -> "Related filenames in the same folder. This does not prove they are versions of the same work."
            }) }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(left,right).forEach { index ->
                    val item = comparisonItems[index]
                    Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaThumbnail(item,Modifier.fillMaxWidth().height(190.dp),large = true)
                        TextButton(onClick = { large = item }) { Text("View larger") }
                        Text(item.name,fontWeight = FontWeight.SemiBold,fontSize = 14.sp)
                        Text("${item.width} × ${item.height}\n${formatBytes(item.size)}\n${if(item.sortTime > 0) DateFormat.getDateInstance().format(Date(item.sortTime)) else "Date unavailable"}",fontSize = 12.sp)
                        Text(item.currentPath,fontSize = 12.sp)
                        Text("Project: ${item.projectName ?: "Unassigned"}",fontSize = 12.sp)
                        if(item.clues.camera) Text("Camera photo · unchanged",fontSize = 12.sp)
                    }
                }
            } }
            if(comparisonItems.size > 2) item { Row {
                TextButton(onClick = { left = (left+1)%comparisonItems.size;if(left == right) left = (left+1)%comparisonItems.size }) { Text("Change left") }
                TextButton(onClick = { right = (right+1)%comparisonItems.size;if(right == left) right = (right+1)%comparisonItems.size }) { Text("Change right") }
            } }
            item { Text("Keep all dismisses this group only. Your images and project assignments stay as they are.",fontSize = 13.sp) }
            item { Button(enabled = !saving,onClick = { keep(active,active.key !in kept) }) { Text(if(active.key in kept) "Review this group again" else "Keep all ${active.ids.size} images") } }
            item { TextButton(onClick = { selected = null }) { Text("Back without deciding") } }
        }
    }
    large?.let { item -> AlertDialog(onDismissRequest = { large = null },title = { Text(item.name) },text = {
        MediaThumbnail(item,Modifier.fillMaxWidth().height(360.dp),large = true)
    },confirmButton = { TextButton(onClick = { large = null }) { Text("Done") } }) }
}
