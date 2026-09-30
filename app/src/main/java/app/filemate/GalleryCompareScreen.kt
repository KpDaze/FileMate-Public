package app.filemate

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
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
    val mediaActions = remember(app) { GalleryMediaActions(app) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var review by remember { mutableStateOf<String?>(null) }
    var reviewItems by remember { mutableStateOf(emptyList<IndexedMedia>()) }
    var target by remember { mutableStateOf<Long?>(null) }
    var targetChosen by remember { mutableStateOf(false) }
    var projects by remember { mutableStateOf(emptyList<Project>()) }
    var trashView by rememberSaveable { mutableStateOf(false) }
    var trashItems by remember { mutableStateOf(emptyList<ComparisonTrashItem>()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    fun loadTrash() { scope.launch {
        try { trashItems = withContext(Dispatchers.IO) { mediaActions.trashItems() } }
        catch(e: Exception) { actionError = "Trash could not be read. Check photo access and try again." }
    } }
    val systemAction = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        val mode = pendingMode;val ids = pendingIds.toList()
        pendingMode = null;pendingIds = arrayListOf();saving = true
        review = null;chosen = emptySet();selected = null;result = null
        scope.launch {
            try {
                if(response.resultCode == Activity.RESULT_OK) {
                    val count = withContext(Dispatchers.IO) {
                        app.store.mediaItems(true).filter { it.identity in ids }.count {
                            mediaActions.trashState(it)?.first == (mode == "trash")
                        }
                    }
                    message = if(mode == "trash") "$count of ${ids.size} images verified in Trash. Open Comparison Trash to restore. Scan again to update comparisons."
                        else "$count of ${ids.size} images verified restored. Scan again to compare them."
                    withContext(Dispatchers.IO) { app.store.history(if(mode == "trash") "Comparison images trashed" else "Comparison images restored",message) }
                } else message = "Android confirmation cancelled. Scan again to continue reviewing."
            } catch(e: Exception) { message = "Android returned, but the result could not be verified. Check Comparison Trash and refresh Gallery." }
            finally { saving = false;app.refreshGallery();loadTrash() }
        }
    }
    fun openReview(mode: String, items: List<IndexedMedia>) {
        reviewItems = items;target = null;targetChosen = false;review = mode
        if(mode == "assign") scope.launch { projects = withContext(Dispatchers.IO) { app.store.projects() } }
    }
    fun applyReview() {
        val mode = review ?: return
        if(saving || pendingMode != null || !resumed) return
        val items = reviewItems.toList();val hashes = result?.fingerprints.orEmpty();val destination = target
        saving = true
        scope.launch {
            try {
                if(mode == "assign") {
                    check(targetChosen)
                    val updated = withContext(Dispatchers.IO) {
                        mediaActions.validate(items,hashes)
                        app.store.assignMediaBatch(items.map { it.identity },destination)
                        app.store.mediaItems().associateBy { it.identity }
                    }
                    result = result?.let { it.copy(items = it.items.map { item -> updated[item.identity] ?: item }) }
                    chosen = emptySet();review = null
                    message = "${items.size} project assignments saved. Images stay in their original locations."
                    app.changed()
                } else {
                    val request = withContext(Dispatchers.IO) {
                        if(mode == "trash") {
                            val valid = mediaActions.validate(items,hashes)
                            mediaActions.track(valid) // Persist before Android opens, so recovery survives process death.
                            MediaStore.createTrashRequest(app.contentResolver,valid.map { it.uri.toUri() },true)
                        } else mediaActions.restoreRequest(items)
                    }
                    pendingIds = ArrayList(items.map { it.identity });pendingMode = mode
                    systemAction.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            } catch(e: Exception) { pendingMode = null;pendingIds = arrayListOf();actionError = e.message ?: "Could not complete this action. Refresh and try again." }
            finally { saving = false }
        }
    }
    LaunchedEffect(resumed,trashView) { if(resumed && trashView) loadTrash() }
    fun cancel() { job?.cancel() }
    fun back() { if(large != null) large = null else if(selected != null) { selected = null;chosen = emptySet() } else if(trashView) { trashView = false;chosen = emptySet() } else { cancel();onBack() } }
    BackHandler(enabled = !saving && pendingMode == null) { back() }
    LaunchedEffect(resumed) {
        if(!resumed && pendingMode == null) { cancel();result = null;selected = null;large = null;chosen = emptySet();review = null;message = "Scan again after returning. Access or images may have changed." }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun scan() {
        if(running || !resumed) return
        result = null;selected = null;chosen = emptySet();running = true;message = "Preparing comparison…"
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
    LaunchedEffect(selected?.key,trashView) { listState.scrollToItem(0) }
    val active = selected
    val comparisonItems = active?.ids?.mapNotNull { id -> result?.items?.find { it.identity == id } }.orEmpty()
    LazyColumn(modifier.fillMaxSize(),state = listState,contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(enabled = !saving && pendingMode == null,onClick = { back() }) { Text(if(active == null && !trashView) "Gallery" else "Comparison results") } }
        item { Text(if(trashView) "Comparison Trash" else active?.kind?.title ?: "Compare images",fontSize = 28.sp,fontWeight = FontWeight.Bold) }
        if(trashView) {
            item { Text("Images sent to Trash from comparison. Restore before Android's expiry; Android may permanently remove expired items. Saved project assignments are retained.") }
            item { TextButton(enabled = !saving && pendingMode == null,onClick = { chosen = emptySet();loadTrash() }) { Text("Refresh Trash") } }
            item { Text(message,fontSize = 14.sp) }
            if(trashItems.isEmpty()) item { Text("No accessible comparison images in Trash. Expired, removed or inaccessible images cannot be listed.") }
            items(trashItems,key = { it.media.identity }) { entry ->
                val item = entry.media
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().toggleable(value = item.identity in chosen,role = Role.Checkbox,enabled = !saving && pendingMode == null,onValueChange = { checked -> chosen = if(checked) chosen + item.identity else chosen - item.identity })) {
                            Checkbox(checked = item.identity in chosen,onCheckedChange = null);Text(item.name,Modifier.weight(1f))
                        }
                        Text("Project: ${item.projectName ?: "Unassigned"}",fontSize = 13.sp)
                        Text(if(entry.expires > 0) "Restore before ${DateFormat.getDateInstance().format(Date(entry.expires))}" else "Expiry is controlled by Android. Restore promptly.",fontSize = 13.sp)
                    }
                }
            }
            item { Button(enabled = chosen.isNotEmpty() && !saving && pendingMode == null,onClick = { openReview("restore",trashItems.filter { it.media.identity in chosen }.map { it.media }) }) { Text("Restore selected (${chosen.size})") } }
        } else if(active == null) {
            item { Text("Exact duplicates have identical file contents. Similar images and possible versions are suggestions, not proof.") }
            item { Text("Select images yourself, then assign a project or move them to recoverable Trash. Nothing is selected automatically.",fontSize = 13.sp) }
            item { TextButton(enabled = !running && !saving && pendingMode == null,onClick = { trashView = true;chosen = emptySet() }) { Text("Comparison Trash") } }
            item { if(running) Button(onClick = { cancel();message = "Scan cancelled. No files or assignments changed." }) { Text("Cancel scan") }
                else Button(enabled = resumed && !saving && pendingMode == null,onClick = { scan() }) { Text(if(result == null) "Scan images" else "Scan again") } }
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
                    OutlinedCard(onClick = { selected = match;chosen = emptySet();left = 0;right = 1 },modifier = Modifier.fillMaxWidth()) {
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
                        Row(Modifier.fillMaxWidth().toggleable(value = item.identity in chosen,role = Role.Checkbox,enabled = !saving && pendingMode == null,onValueChange = { checked -> chosen = if(checked) chosen + item.identity else chosen - item.identity })) {
                            Checkbox(checked = item.identity in chosen,onCheckedChange = null);Text("Select ${item.name}",fontSize = 12.sp)
                        }
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
            item { Text("${chosen.size} selected · Nothing is selected automatically.",fontSize = 13.sp) }
            if(chosen.isNotEmpty()) item { Text(comparisonItems.filter { it.identity in chosen }.joinToString("\n") { it.name },fontSize = 12.sp) }
            item { Button(enabled = chosen.isNotEmpty() && !saving && pendingMode == null,onClick = { openReview("assign",comparisonItems.filter { it.identity in chosen }) }) { Text("Assign selected to project") } }
            item { OutlinedButton(enabled = chosen.isNotEmpty() && !saving && pendingMode == null,onClick = { openReview("trash",comparisonItems.filter { it.identity in chosen }) }) { Text("Move selected to Trash") } }
            item { Text(message,fontSize = 13.sp) }
            item { Text("Keep all dismisses this group only. Your images and project assignments stay as they are.",fontSize = 13.sp) }
            item { Button(enabled = !saving && pendingMode == null,onClick = { chosen = emptySet();keep(active,active.key !in kept) }) { Text(if(active.key in kept) "Review this group again" else "Keep all ${active.ids.size} images") } }
            item { TextButton(enabled = !saving && pendingMode == null,onClick = { selected = null;chosen = emptySet() }) { Text("Back without deciding") } }
        }
    }
    review?.let { mode ->
        AlertDialog(onDismissRequest = { if(!saving && pendingMode == null) review = null },
            title = { Text(when(mode) { "assign" -> "Assign ${reviewItems.size} items";"trash" -> "Move ${reviewItems.size} images to Trash?";else -> "Restore ${reviewItems.size} images?" }) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reviewItems,key = { it.identity }) { Text("${it.name}\n${it.currentPath}",fontSize = 13.sp) }
                if(mode == "assign") {
                    item { Text("Choose a project, then confirm. Your images stay where they are.") }
                    items(projects,key = { it.id }) { project -> TextButton(enabled = !saving,onClick = { target = project.id;targetChosen = true }) {
                        RadioButton(selected = targetChosen && target == project.id,onClick = null);Text(project.name)
                    } }
                    item { TextButton(enabled = !saving,onClick = { target = null;targetChosen = true }) { RadioButton(selected = targetChosen && target == null,onClick = null);Text("Clear project assignment") } }
                } else item { Text(if(mode == "trash") "Only these selected images will leave Gallery. Restore them from Comparison Trash before Android's expiry. Android will also ask you to confirm. Project assignments are kept." else "Android will ask you to confirm restoration. Saved project assignments are kept.") }
                if(reviewItems.any { it.clues.camera }) item { Text("Includes camera photos you selected manually.") }
            } },
            confirmButton = { Button(enabled = !saving && pendingMode == null && resumed && (mode != "assign" || targetChosen),onClick = { applyReview() }) {
                Text(if(saving) "Checking…" else when(mode) { "assign" -> "Confirm assignment";"trash" -> "Confirm Trash";else -> "Confirm restore" })
            } },dismissButton = { TextButton(enabled = !saving && pendingMode == null,onClick = { review = null }) { Text("Cancel") } })
    }
    actionError?.let { error -> AlertDialog(onDismissRequest = { actionError = null },title = { Text("Comparison action") },text = { Text(error) },confirmButton = { TextButton(onClick = { actionError = null }) { Text("OK") } }) }
    large?.let { item -> AlertDialog(onDismissRequest = { large = null },title = { Text(item.name) },text = {
        MediaThumbnail(item,Modifier.fillMaxWidth().height(360.dp),large = true)
    },confirmButton = { TextButton(onClick = { large = null }) { Text("Done") } }) }
}
