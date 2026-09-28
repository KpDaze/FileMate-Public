package app.filemate

import android.content.Intent
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.provider.Settings
import android.util.Size
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
fun GalleryScreen(app: FileMateApp, resumed: Boolean, initialProjectId: Long?, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by app.gallery.collectAsStateWithLifecycle()
    val revision by app.revision.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(if(initialProjectId == null) "Unassigned" else "Projects") }
    var projectFilter by rememberSaveable { mutableStateOf(initialProjectId) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var assigning by remember { mutableStateOf(false) }
    var assignmentMessage by remember { mutableStateOf<String?>(null) }
    val access = MediaAccess.read(context)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.refreshGallery() }
    LaunchedEffect(resumed) {
        selected = null
        if(resumed) app.refreshGallery()
    }
    val all by produceState(emptyList<IndexedMedia>(),revision,progress) {
        value = if(progress.ready && !progress.running) withContext(Dispatchers.IO) { app.store.mediaItems() } else emptyList()
    }
    val projects by produceState(emptyList<Project>(),revision) { value = withContext(Dispatchers.IO) { app.store.projects() } }
    // A resumed refresh must finish before retained data or thumbnails can be shown.
    val visible = if(resumed && progress.ready && !progress.running && access.any) all else emptyList()
    val filtered = visible.filter {
        when(filter) {
            "Unassigned" -> it.projectId == null
            "Camera" -> it.clues.camera
            "Screenshots" -> it.clues.screenshot
            "Downloads" -> it.clues.downloads
            "Projects" -> it.projectId != null && (projectFilter == null || it.projectId == projectFilter)
            else -> true
        }
    }
    val detail = visible.firstOrNull { it.identity == selected }
    LazyVerticalGrid(columns = GridCells.Fixed(3),modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp,12.dp,20.dp,24.dp),horizontalArrangement = Arrangement.spacedBy(8.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack,null);Spacer(Modifier.width(6.dp));Text("Phone") }
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
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text("${filtered.size} visible",modifier = Modifier.weight(1f),fontSize = 13.sp)
                    TextButton(enabled = access.any && !progress.running,onClick = { selected = null;app.refreshGallery() }) {
                        Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text(if(progress.running) "Refreshing…" else "Refresh")
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Unassigned","All Gallery","Camera","Screenshots","Downloads","Projects").forEach { choice ->
                        FilterChip(selected = filter == choice,onClick = { filter = choice;projectFilter = null },label = { Text(choice) })
                    }
                }
                if(filter == "Projects") Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = projectFilter == null,onClick = { projectFilter = null },label = { Text("Any project") })
                    projects.forEach { p -> FilterChip(selected = projectFilter == p.id,onClick = { projectFilter = p.id },label = { Text(p.name) }) }
                }
                if(filter == "Unassigned") Text("Assign a project to clear an item from this view. It stays in All Gallery and its project.",fontSize = 12.sp)
                assignmentMessage?.let { Text(it,fontSize = 13.sp,color = MaterialTheme.colorScheme.primary) }
                if(filter == "Camera") Text("Camera folder items are for browsing. Nothing is automatically selected or reorganised.",fontSize = 12.sp)
                if(filter == "Screenshots") Text("Likely screenshots from folder or filename clues. These clues do not prove how an image was made.",fontSize = 12.sp)
                if(progress.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                progress.message?.let { Text(it,fontSize = 13.sp) }
                if(!progress.running && progress.ready && filtered.isEmpty()) {
                    Text(if(visible.isEmpty()) "No accessible photos or videos yet. Try choosing more items or refresh after Android has indexed them."
                        else if(filter == "Unassigned") "All accessible media has a project. Browse All Gallery or Projects."
                        else "No media in this filter. Try All Gallery or choose another project.",modifier = Modifier.padding(vertical = 20.dp))
                }
            }
        }
        items(filtered,key = { it.identity }) { item ->
            OutlinedCard(onClick = { selected = item.identity },modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MediaThumbnail(item,Modifier.fillMaxSize())
                    if(item.kind == "video") Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp),color = MaterialTheme.colorScheme.surface) {
                        Text("▶ ${duration(item.duration)}",fontSize = 10.sp,modifier = Modifier.padding(4.dp))
                    }
                }
                Text(item.name,maxLines = 1,overflow = TextOverflow.Ellipsis,fontSize = 11.sp,modifier = Modifier.padding(6.dp,4.dp))
                Text(mediaDate(item.sortTime),fontSize = 10.sp,modifier = Modifier.padding(6.dp,0.dp,6.dp,6.dp))
                item.projectName?.let { Text(it,fontSize = 11.sp,color = MaterialTheme.colorScheme.primary,modifier = Modifier.padding(6.dp,0.dp,6.dp,6.dp)) }
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
                item { Text("Assign to project",fontWeight = FontWeight.SemiBold) }
                if(projects.isEmpty()) item { Text("Create a project in Projects first.",fontSize = 12.sp) }
                items(projects,key = { it.id }) { project ->
                    TextButton(enabled = !assigning && detail.projectId != project.id,onClick = {
                        assigning = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { app.store.assignMedia(detail.identity,project.id) } }
                                .onSuccess { selected = null;assignmentMessage = "Assigned to ${project.name}. Your media stays where it is." }
                                .onFailure { error = it.message ?: "Couldn't assign this item." }
                            assigning = false;app.changed()
                        }
                    }) { Text(project.name) }
                }
                if(detail.projectId != null) item {
                    TextButton(enabled = !assigning,onClick = {
                        assigning = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { app.store.assignMedia(detail.identity,null) } }
                                .onSuccess { selected = null;assignmentMessage = "Assignment cleared. Find this item in Unassigned." }
                                .onFailure { error = it.message ?: "Couldn't clear this assignment." }
                            assigning = false;app.changed()
                        }
                    }) { Text("Clear project assignment") }
                }
            }
        },confirmButton = { TextButton(enabled = !assigning,onClick = { selected = null }) { Text("Done") } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null },title = { Text("Gallery") },text = { Text(message) },confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Composable
private fun MediaThumbnail(item: IndexedMedia, modifier: Modifier, large: Boolean = false) {
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
