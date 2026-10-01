package app.filemate

import android.content.Intent
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun GalleryScreen(app: FileMateApp, resumed: Boolean, initialProjectId: Long?, modifier: Modifier = Modifier, sortingOnly: Boolean = false, initialGroup: String? = null, onBack: () -> Unit) {
    var comparing by rememberSaveable { mutableStateOf(false) }
    if(comparing) {
        GalleryCompareScreen(app,resumed,modifier) { comparing = false }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by app.gallery.collectAsStateWithLifecycle()
    val revision by app.revision.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(if(sortingOnly) "Needs Sorting" else if(initialProjectId == null) "Unassigned" else "Projects") }
    var groupFilter by rememberSaveable { mutableStateOf(initialGroup) }
    var projectFilter by rememberSaveable { mutableStateOf(initialProjectId) }
    var albumFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingAlbum by remember { mutableStateOf(false) }
    var albumName by remember { mutableStateOf("") }
    var albumPicker by remember { mutableStateOf<List<String>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var assigning by remember { mutableStateOf(false) }
    var assignmentMessage by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var review by remember { mutableStateOf<List<String>?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }
    var targetChosen by remember { mutableStateOf(false) }
    fun clearSelection() { selecting = false;selection = emptySet() }
    fun openReview(ids: List<String>) { review = ids;target = null;targetChosen = false;selected = null }
    BackHandler(selecting && review == null) { clearSelection() }
    val access = MediaAccess.read(context)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.refreshGallery() }
    LaunchedEffect(resumed) {
        selected = null;review = null;clearSelection()
        if(resumed) app.refreshGallery()
    }
    val all by produceState(emptyList<IndexedMedia>(),revision,progress) {
        value = if(progress.ready && !progress.running) withContext(Dispatchers.IO) { app.store.mediaItems() } else emptyList()
    }
    val projects by produceState(emptyList<Project>(),revision) { value = withContext(Dispatchers.IO) { app.store.projects() } }
    val favourites by produceState(emptySet<String>(),revision) { value = withContext(Dispatchers.IO) { app.store.favouriteMediaIds() } }
    val albums by produceState(emptyList<GalleryAlbum>(),revision) { value = withContext(Dispatchers.IO) { app.store.galleryAlbums() } }
    val albumIds by produceState(emptySet<String>(),revision,albumFilter) {
        value = albumFilter?.let { withContext(Dispatchers.IO) { app.store.albumMediaIds(it) } } ?: emptySet()
    }
    // A resumed refresh must finish before retained data or thumbnails can be shown.
    val visible = if(resumed && progress.ready && !progress.running && access.any) all else emptyList()
    val groups = screenshotGroups(visible,unassignedOnly = filter == "Needs Sorting")
    val groupedIds = groups.filter { groupFilter == null || it.key == groupFilter }.flatMap { it.identities }.toSet()
    val filtered = visible.filter {
        when(filter) {
            "Unassigned" -> it.projectId == null
            "Camera" -> it.clues.camera
            "Screenshots", "Needs Sorting" -> it.identity in groupedIds
            "Downloads" -> it.clues.downloads
            "Projects" -> it.projectId != null && (projectFilter == null || it.projectId == projectFilter)
            "Favourites" -> it.identity in favourites
            "Albums" -> albumFilter != null && it.identity in albumIds
            else -> true
        }
    }
    val displayGroups: List<Pair<ScreenshotGroup?,List<IndexedMedia>>> = if(filter == "Screenshots" || filter == "Needs Sorting") {
        val byId = filtered.associateBy { it.identity }
        groups.map { it to it.identities.mapNotNull(byId::get) }.filter { it.second.isNotEmpty() }
    } else listOf(null to filtered)
    val detail = visible.firstOrNull { it.identity == selected }
    LazyVerticalGrid(columns = GridCells.Fixed(3),modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp,12.dp,20.dp,24.dp),horizontalArrangement = Arrangement.spacedBy(8.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack,null);Spacer(Modifier.width(6.dp));Text(if(sortingOnly) "Needs Sorting" else "Phone") }
                Text("Gallery",fontSize = 36.sp,fontWeight = FontWeight.Bold)
                Text("Your photos and videos, in date order.",color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Browse and assign a project. Your media stays where it is.",fontSize = 13.sp)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(access.description,fontWeight = FontWeight.SemiBold)
                        Text("Gallery can use photo access without All files access. Android may show only the items you select.",fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = !progress.running,onClick = { permission.launch(MediaAccess.permissions()) }) {
                                Text(if(access.any) "Choose access" else "Allow photos & videos")
                            }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())) }) { Text("Settings") }
                        }
                    }
                }
                TextButton(enabled = access.any && !progress.running,onClick = { comparing = true }) { Text("Compare images") }
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} visible",modifier = Modifier.weight(1f),fontSize = 13.sp)
                    TextButton(enabled = access.any && !progress.running,onClick = { selected = null;clearSelection();app.refreshGallery() }) {
                        Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text(if(progress.running) "Refreshing…" else "Refresh")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Unassigned","All Gallery","Favourites","Albums","Camera","Screenshots","Needs Sorting","Downloads","Projects").forEach { choice ->
                        FilterChip(selected = filter == choice,onClick = { filter = choice;projectFilter = null;groupFilter = null;assignmentMessage = null;clearSelection() },label = { Text(choice) })
                    }
                }
                if(filter == "Albums") {
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        albums.forEach { a -> FilterChip(selected = albumFilter == a.id,onClick = { albumFilter=a.id;clearSelection() },label = { Text("${a.name} (${a.itemCount})") }) }
                    }
                    TextButton(onClick = { albumName="";creatingAlbum=true }) { Text("Create album") }
                    if(albums.isEmpty()) Text("Create a FileMate album, then add photos or videos. Files stay in their original locations.",fontSize = 12.sp)
                }
                if(filter == "Projects") Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = projectFilter == null,onClick = { projectFilter = null;clearSelection() },label = { Text("Any project") })
                    projects.forEach { p -> FilterChip(selected = projectFilter == p.id,onClick = { projectFilter = p.id;clearSelection() },label = { Text(p.name) }) }
                }
                if(filter == "Screenshots" || filter == "Needs Sorting") {
                    Text("Grouped by date and folder clues, not by topic or project. Camera folder items are excluded.",fontSize = 12.sp)
                    if(filter == "Needs Sorting") Text("Only accessible, unassigned screenshots appear here. Choose items, then confirm their project.",fontSize = 12.sp)
                    if(groupFilter != null) TextButton(onClick = { groupFilter = null;clearSelection();assignmentMessage = null }) { Text("Show all screenshot groups") }
                }
                if(filter == "Unassigned") Text("Assign a project to clear an item from this view. It stays in All Gallery and its project.",fontSize = 12.sp)
                if(selecting) {
                    Text("${selection.size} selected",fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = selection.isNotEmpty(),onClick = { openReview(selection.toList()) }) { Text("Assign selected") }
                        TextButton(enabled = selection.isNotEmpty(),onClick = {
                            val ids=selection.toList();scope.launch { withContext(Dispatchers.IO) { app.store.setMediaFavourite(ids,true) };clearSelection();app.changed() }
                        }) { Text("Favourite") }
                        TextButton(enabled = selection.isNotEmpty(),onClick = { albumPicker=selection.toList() }) { Text("Add to album") }
                        TextButton(onClick = { clearSelection() }) { Text("Cancel selection") }
                    }
                } else TextButton(enabled = filtered.isNotEmpty(),onClick = { selecting = true }) { Text("Select photos") }
                assignmentMessage?.let { Text(it,fontSize = 13.sp,color = MaterialTheme.colorScheme.primary) }
                if(filter == "Camera") Text("Camera folder items are for browsing. Nothing is automatically selected or reorganised.",fontSize = 12.sp)
                if(filter == "Screenshots") Text("Likely screenshots from folder or filename clues. These clues do not prove how an image was made.",fontSize = 12.sp)
                if(progress.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                progress.message?.let { Text(it,fontSize = 13.sp) }
                if(!progress.running && progress.ready && filtered.isEmpty()) {
                    Text(if(visible.isEmpty()) "No accessible photos or videos yet. Try choosing more items or refresh after Android has indexed them."
                        else if(filter == "Needs Sorting") "No unassigned screenshots in this group. Browse other groups or All Gallery."
                        else if(filter == "Unassigned") "All accessible media has a project. Browse All Gallery or Projects."
                        else "No media in this filter. Try All Gallery or choose another project.",modifier = Modifier.padding(vertical = 20.dp))
                }
            }
        }
        displayGroups.forEach { (group,media) ->
            if(group != null) item(key = "group:${group.key}",span = { GridItemSpan(maxLineSpan) }) {
                Column { Text(group.day,fontWeight = FontWeight.SemiBold);Text("${group.folder} · ${media.size} items",fontSize = 12.sp) }
            }
            items(media,key = { it.identity }) { item ->
                OutlinedCard(onClick = { if(selecting) selection = if(item.identity in selection) selection - item.identity else selection + item.identity else selected = item.identity },modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        MediaThumbnail(item,Modifier.fillMaxSize())
                        if(selecting) Checkbox(checked = item.identity in selection,onCheckedChange = null,modifier = Modifier.align(Alignment.TopEnd),colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.primary))
                        if(item.kind == "video") Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp),color = MaterialTheme.colorScheme.surface) {
                            Text("▶ ${duration(item.duration)}",fontSize = 10.sp,modifier = Modifier.padding(4.dp))
                        }
                    }
                    Text(item.name,maxLines = 1,overflow = TextOverflow.Ellipsis,fontSize = 11.sp,modifier = Modifier.padding(6.dp,4.dp))
                    if(item.identity in favourites) Text("★ Favourite",fontSize = 10.sp,color = MaterialTheme.colorScheme.primary,modifier = Modifier.padding(6.dp,0.dp))
                    Text(mediaDate(item.sortTime),fontSize = 10.sp,modifier = Modifier.padding(6.dp,0.dp,6.dp,6.dp))
                    item.projectName?.let { Text(it,fontSize = 11.sp,color = MaterialTheme.colorScheme.primary,modifier = Modifier.padding(6.dp,0.dp,6.dp,6.dp)) }
                }
            }
        }
    }

    if(detail != null) AlertDialog(onDismissRequest = { if(!assigning) selected = null },title = { Text(detail.name,maxLines = 2,overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { MediaThumbnail(detail,Modifier.fillMaxWidth().height(180.dp),large = true) }
                item { Text("${detail.mime} · ${detail.width} × ${detail.height}\n${formatBytes(detail.size)}${if(detail.kind == "video") " · ${duration(detail.duration)}" else ""}\n${mediaDate(detail.sortTime)}",fontSize = 13.sp) }
                item { Text("Current location\n${detail.currentPath}",fontSize = 12.sp) }
                if(detail.originalPath != detail.currentPath || detail.originalName != detail.name) item { Text("First indexed as\n${detail.originalPath}",fontSize = 12.sp) }
                item { Text("Screenshot clue: ${detail.clues.screenshotConfidence}\n${detail.clues.explanation}",fontSize = 12.sp) }
                item { Text("Project: ${detail.projectName ?: "Unassigned"}\n${detail.projectConfidence}. Assignment changes only FileMate's records.",fontSize = 13.sp) }
                item { Button(onClick = { openReview(listOf(detail.identity)) }) { Text("Assign to project") } }
                item { TextButton(onClick = {
                    val id=detail.identity;val makeFavourite=id !in favourites
                    scope.launch { withContext(Dispatchers.IO) { app.store.setMediaFavourite(listOf(id),makeFavourite) };app.changed() }
                }) { Text(if(detail.identity in favourites) "Remove from Favourites" else "Add to Favourites") } }
                item { TextButton(onClick = { albumPicker=listOf(detail.identity) }) { Text("Add to album") } }
            }
        },confirmButton = { TextButton(enabled = !assigning,onClick = { selected = null }) { Text("Done") } })
    review?.let { ids ->
        val reviewItems = visible.filter { it.identity in ids }
        val valid = reviewItems.size == ids.size && targetChosen && (target == null || projects.any { it.id == target })
        AlertDialog(onDismissRequest = { if(!assigning) review = null },title = { Text("Assign ${ids.size} ${if(ids.size == 1) "item" else "items"}") },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Choose a project, then confirm. Nothing changes until you confirm.",fontSize = 13.sp) }
                items(reviewItems,key = { it.identity }) { item -> Text("${item.name} · ${item.projectName ?: "Unassigned"}",fontSize = 12.sp) }
                item { Text("Your media stays where it is. Only FileMate's project records change.",fontSize = 12.sp) }
                if(reviewItems.any { it.clues.camera }) item { Text("Includes manually selected camera media. No photo will be moved.",fontSize = 12.sp) }
                items(projects,key = { it.id }) { project ->
                    TextButton(enabled = !assigning,onClick = { target = project.id;targetChosen = true }) {
                        RadioButton(selected = targetChosen && target == project.id,onClick = null)
                        Text(project.name)
                    }
                }
                item { TextButton(enabled = !assigning,onClick = { target = null;targetChosen = true }) {
                    RadioButton(selected = targetChosen && target == null,onClick = null);Text("Clear project assignment")
                } }
                if(targetChosen) item { Text("Selected: ${projects.firstOrNull { it.id == target }?.name ?: "Unassigned"}",fontWeight = FontWeight.SemiBold) }
                if(reviewItems.size != ids.size) item { Text("Some items are unavailable. Cancel and refresh Gallery.") }
            } },
            confirmButton = { Button(enabled = valid && !assigning,onClick = {
                assigning = true
                val destination = target
                val destinationName = projects.firstOrNull { it.id == destination }?.name
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { app.store.assignMediaBatch(ids,destination) } }
                        .onSuccess {
                            review = null;clearSelection()
                            assignmentMessage = if(destination == null) "Assignment cleared. Find these items in Unassigned."
                                else "${ids.size} assigned to $destinationName. Your media stays where it is."
                        }.onFailure { error = it.message ?: "Couldn't assign these items. Nothing was changed." }
                    assigning = false;app.changed()
                }
            }) { Text(if(assigning) "Saving…" else "Confirm assignment") } },
            dismissButton = { TextButton(enabled = !assigning,onClick = { review = null }) { Text("Cancel") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null },title = { Text("Gallery") },text = { Text(message) },confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
internal fun MediaThumbnail(item: IndexedMedia, modifier: Modifier, large: Boolean = false) {
    val context = LocalContext.current
    var bitmap by remember(item.identity,item.generation,item.modified,item.size,large) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.identity,item.generation,item.modified,item.size,large) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(item.identity,item.generation,item.modified,item.size,large) {
        val signal = CancellationSignal()
        val job = scope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.loadThumbnail(item.uri.toUri(),Size(if(large) 800 else 280,if(large) 800 else 280),signal)
            }.getOrNull() }
            bitmap = loaded;failed = loaded == null
        }
        onDispose { signal.cancel();job.cancel() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer),contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(),contentDescription = item.name,modifier = Modifier.fillMaxSize(),contentScale = if(large) ContentScale.Fit else ContentScale.Crop) }
            ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if(item.kind == "video") Icons.Outlined.Videocam else Icons.Outlined.Image,null)
                if(failed) Text("Unavailable\nRefresh Gallery",fontSize = 10.sp)
            }
    }
}
private fun mediaDate(millis: Long): String = if(millis > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) else "Date unavailable"
private fun duration(millis: Long): String = "%d:%02d".format(millis / 60000,(millis / 1000) % 60)

    if(creatingAlbum) AlertDialog(onDismissRequest = { creatingAlbum=false },title = { Text("Create Gallery album") },
        text = { OutlinedTextField(value=albumName,onValueChange={ albumName=it },label={ Text("Album name") },singleLine=true) },
        confirmButton = { Button(enabled=albumName.isNotBlank(),onClick={
            val name=albumName;scope.launch {
                runCatching { withContext(Dispatchers.IO) { app.store.createGalleryAlbum(name) } }
                    .onSuccess { creatingAlbum=false;albumName="";filter="Albums";albumFilter=it;app.changed() }
                    .onFailure { error=it.message ?: "Couldn't create album." }
            }
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick={ creatingAlbum=false }) { Text("Cancel") } })
    albumPicker?.let { ids ->
        AlertDialog(onDismissRequest={ albumPicker=null },title={ Text("Add to album") },
            text={ LazyColumn { items(albums,key={it.id}) { a -> TextButton(onClick={
                scope.launch { runCatching { withContext(Dispatchers.IO) { app.store.addMediaToAlbum(a.id,ids) } }
                    .onSuccess { albumPicker=null;clearSelection();app.changed() }
                    .onFailure { error=it.message ?: "Couldn't add these items." } }
            }) { Text("${a.name} (${a.itemCount})") } }
                item { TextButton(onClick={ albumPicker=null;albumName="";creatingAlbum=true }) { Text("Create new album") } }
            } },
            confirmButton={},dismissButton={ TextButton(onClick={albumPicker=null}) { Text("Cancel") } })
    }
