package app.filemate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private val Blue = Color(0xFF245DAF)
private val Ink = Color(0xFF101B2C)
private val Muted = Color(0xFF58667F)
private val Pale = Color(0xFFEAF1FC)
private val Background = Color(0xFFFAFBFE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue,onPrimary = Color.White,
                background = Background,surface = Background,onBackground = Ink,onSurface = Ink,
                primaryContainer = Pale,onPrimaryContainer = Blue)) {
                FileMate(application as FileMateApp,this)
            }
        }
    }
}

@Composable
private fun FileMate(app: FileMateApp, activity: MainActivity) {
    var page by rememberSaveable { mutableStateOf("AI Hub") }
    var selectedProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showProjectEditor by rememberSaveable { mutableStateOf(false) }
    var deleteProject by remember { mutableStateOf<Project?>(null) }
    var sortingSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignmentPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<DetectedFile?>(null) }
    var resumed by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val uiScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val revision by app.revision.collectAsStateWithLifecycle()
    val monitor by app.monitor.collectAsStateWithLifecycle()
    val checking by app.checking.collectAsStateWithLifecycle()
    val hub by produceState(emptyList<HubApp>(),revision) { value = withContext(Dispatchers.IO) { app.store.hub() } }
    val recent by produceState(emptyList<DetectedFile>(),revision) { value = withContext(Dispatchers.IO) { app.store.recent() } }
    val history by produceState(emptyList<HistoryItem>(),revision) { value = withContext(Dispatchers.IO) { app.store.history() } }
    val projects by produceState(emptyList<Project>(),revision) { value = withContext(Dispatchers.IO) { app.store.projects() } }
    val needsSorting by produceState(emptyList<DetectedFile>(),revision) { value = withContext(Dispatchers.IO) { app.store.needsSorting() } }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val projectFiles by produceState(emptyList<DetectedFile>(),revision,selectedProjectId) {
        value = selectedProjectId?.let { withContext(Dispatchers.IO) { app.store.projectFiles(it) } }.orEmpty()
    }
    val ignored by produceState("0",revision) { value = withContext(Dispatchers.IO) { app.store.state("ignored") ?: "0" } }
    val lastCheck by produceState<String?>(null,revision) { value = withContext(Dispatchers.IO) { app.store.state("last_check") } }
    val filesAllowed = remember(permissionTick) { Environment.isExternalStorageManager() }
    val usageAllowed = remember(permissionTick) { Access.usage(activity) }
    val notificationsAllowed = remember(permissionTick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    LaunchedEffect(needsSorting) {
        val available = needsSorting.mapTo(mutableSetOf()) { it.path }
        sortingSelection = sortingSelection.intersect(available)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _,event ->
            if(event == Lifecycle.Event.ON_RESUME) {
                resumed = true;permissionTick++
                app.scope.launch { app.reconcile() }
            } else if(event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            resumed = true;permissionTick++;app.scope.launch { app.reconcile() }
        }
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(monitor.readyRequest,pendingId,resumed) {
        if(resumed && pendingId != null && monitor.readyRequest == pendingId) {
            val pkg = pendingPackage
            val launch = pkg?.let { activity.packageManager.getLaunchIntentForPackage(it) }
            if(launch == null) error = "This app is no longer available. Remove it from the Hub in Setup."
            else try {
                // Do not suspend after changing this effect's keys: recomposition cancels
                // the old effect. Dispatch the launch first, then consume the request.
                activity.startActivity(launch)
                val label = hub.firstOrNull { it.packageName == pkg }?.label ?: pkg
                app.scope.launch {
                    app.store.history("AI opened from Hub", "$label. File watchers were ready before launch.")
                    app.changed()
                }
            } catch(_: Exception) { error = "Android couldn't open this app. Try opening it normally, or choose another app." }
            pendingId = null;pendingPackage = null
        }
    }
    LaunchedEffect(monitor.error) { if(monitor.error != null) { pendingId = null;pendingPackage = null;error = monitor.error } }
    BackHandler(page != "AI Hub") {
        if(page == "Project detail" || page == "Needs Sorting") { page = "Projects";selectedProjectId = null }
        else page = "AI Hub"
    }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp,vertical = 12.dp),verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.filemate_logo),contentDescription = null,Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Text("FileMate",fontWeight = FontWeight.Bold,fontSize = 24.sp,modifier = Modifier.weight(1f))
                Surface(color = Pale,shape = RoundedCornerShape(12.dp)) { Text("STAGE 2B",color = Blue,fontSize = 11.sp,fontWeight = FontWeight.SemiBold,modifier = Modifier.padding(10.dp,7.dp)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFF3F7FD)) {
                listOf("AI Hub" to Icons.Outlined.Apps,"Projects" to Icons.Outlined.Folder,"Recent" to Icons.Outlined.Schedule,"Activity" to Icons.Outlined.History,"Setup" to Icons.Outlined.Tune).forEach { (label,icon) ->
                    NavigationBarItem(selected = page == label || (page == "Add apps" && label == "AI Hub") || ((page == "Project detail" || page == "Needs Sorting") && label == "Projects"),onClick = {
                        page = label
                        if(label == "Projects") selectedProjectId = null
                    },icon = { Icon(icon,null) },label = { Text(label,fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        when(page) {
            "Add apps" -> AppPicker(app,hub,Modifier.padding(padding)) { page = "AI Hub" }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding = PaddingValues(22.dp,12.dp,22.dp,24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when(page) {
                    "AI Hub" -> {
                        item { Text("AI Hub",fontSize = 43.sp,fontWeight = FontWeight.Bold,letterSpacing = (-1).sp);Text("Your AI apps, together.",color = Muted,fontSize = 17.sp) }
                        if(!filesAllowed) item {
                            InfoCard("Let's connect your phone", "Allow file access so FileMate can watch Downloads and Documents.",Icons.Outlined.FolderOpen) {
                                Button(onClick = { page = "Setup" }) { Text("Set up FileMate") }
                            }
                        }
                        item {
                            Column(Modifier.padding(vertical = 12.dp),verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                val slots = hub.map<HubApp,HubApp?> { it } + listOf(null)
                                slots.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { selected ->
                                            Column(Modifier.weight(1f),horizontalAlignment = Alignment.CenterHorizontally) {
                                                Surface(onClick = {
                                                    if(selected == null) page = "Add apps"
                                                    else if(!filesAllowed) page = "Setup"
                                                    else if(pendingId == null) {
                                                        if(activity.packageManager.getLaunchIntentForPackage(selected.packageName) == null) error = "${selected.label} is not available on this phone. You can remove it in Setup."
                                                        else {
                                                            val id = UUID.randomUUID().toString();pendingId = id;pendingPackage = selected.packageName
                                                            try { ContextCompat.startForegroundService(activity,Intent(activity,MonitorService::class.java).putExtra("request",id).putExtra("package",selected.packageName).putExtra("label",selected.label)) }
                                                            catch(_: Exception) { pendingId = null;pendingPackage = null;error = "Android couldn't start monitoring. Keep FileMate open and try again." }
                                                        }
                                                    }
                                                },enabled = pendingId == null,shape = RoundedCornerShape(25.dp),color = Pale,modifier = Modifier.size(82.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if(selected == null) Icon(Icons.Outlined.Add,null,tint = Blue,modifier = Modifier.size(32.dp))
                                                        else AppIcon(selected.packageName,activity,Modifier.size(49.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(9.dp))
                                                Text(selected?.label ?: "Add app",fontSize = 14.sp,fontWeight = FontWeight.Medium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        item {
                            InfoCard(when { monitor.starting -> "Preparing monitoring…";monitor.running -> "Monitoring is on";else -> "Ready when you are" },
                                when { monitor.starting -> "Checking for missed files and attaching file watchers.";monitor.running -> "Switch between your selected AI apps as usual.";else -> "Tap an AI app to start a session." },Icons.Outlined.Radar) {
                                if(monitor.running) TextButton(onClick = { activity.startService(Intent(activity,MonitorService::class.java).setAction(MonitorService.STOP)) }) { Text("Stop monitoring") }
                            }
                        }
                        item { Text(if(usageAllowed) "Stops after 30 minutes away from your selected AI apps." else "Without app activity access, sessions end 30 minutes after the last Hub launch. Enable it in Setup to track normal app switching.",color = Muted,fontSize = 13.sp) }
                        if(needsSorting.isNotEmpty()) item { ActionRow("Needs Sorting","${needsSorting.size} files need a project",Icons.Outlined.RuleFolder) { page = "Needs Sorting" } }
                        if(recent.isNotEmpty()) item { ActionRow("Recent detections","${recent.size} likely AI files · review source clues",Icons.Outlined.InsertDriveFile) { page = "Recent" } }
                        item { Text("Project names and assignments stay in FileMate's local database. Files remain in their original locations.",color = Muted,fontSize = 12.sp) }
                    }
                    "Projects" -> {
                        item { Title("Projects", "Keep files together by what you're working on.") }
                        item { ActionRow("Needs Sorting",if(needsSorting.isEmpty()) "Everything detected has a project" else "${needsSorting.size} files ready to review",Icons.Outlined.RuleFolder) { page = "Needs Sorting" } }
                        item {
                            Button(onClick = { editingProjectId = null;showProjectEditor = true },modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.CreateNewFolder,null);Spacer(Modifier.width(8.dp));Text("Create project")
                            }
                        }
                        if(projects.isEmpty()) item {
                            InfoCard("No projects yet", "Create a project yourself. FileMate will never invent one from an unfamiliar filename.",Icons.Outlined.FolderOpen) {}
                        }
                        items(projects,key = { it.id }) { project ->
                            OutlinedCard(onClick = { selectedProjectId = project.id;page = "Project detail" },modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(17.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                    Surface(color = Pale,shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Folder,null,tint = Blue,modifier = Modifier.padding(12.dp)) }
                                    Column(Modifier.weight(1f)) {
                                        Text(project.name,fontWeight = FontWeight.SemiBold,fontSize = 17.sp,maxLines = 2,overflow = TextOverflow.Ellipsis)
                                        Text(if(project.fileCount == 1) "1 file" else "${project.fileCount} files",fontSize = 12.sp,color = Muted,modifier = Modifier.padding(top = 3.dp))
                                    }
                                    Icon(Icons.Outlined.ChevronRight,null,tint = Muted)
                                }
                            }
                        }
                    }
                    "Project detail" -> {
                        if(selectedProject == null) item {
                            InfoCard("Project unavailable", "Return to Projects and choose it again.",Icons.Outlined.FolderOff) {
                                TextButton(onClick = { page = "Projects";selectedProjectId = null }) { Text("Back to Projects") }
                            }
                        } else {
                            item { Title(selectedProject.name, if(selectedProject.fileCount == 1) "1 assigned file" else "${selectedProject.fileCount} assigned files") }
                            item {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { editingProjectId = selectedProject.id;showProjectEditor = true },modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(6.dp));Text("Rename")
                                    }
                                    OutlinedButton(onClick = { deleteProject = selectedProject },modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text("Delete")
                                    }
                                }
                            }
                            if(projectFiles.isEmpty()) item {
                                InfoCard("No files assigned", "Use Needs Sorting to add one file or a batch.",Icons.Outlined.DriveFileMove) {}
                            }
                            items(projectFiles,key = { it.path }) { file ->
                                FileRow(file) { detail = file }
                            }
                        }
                    }
                    "Needs Sorting" -> {
                        item { Title("Needs Sorting", "Choose the project. FileMate won't guess.") }
                        item { Text("Source confidence describes where a file may have come from. Project assignment stays separate and becomes confirmed only when you choose it.",fontSize = 13.sp,color = Muted) }
                        if(projects.isEmpty()) item {
                            InfoCard("Create a project first", "You need somewhere to assign the selected files.",Icons.Outlined.CreateNewFolder) {
                                TextButton(onClick = { editingProjectId = null;showProjectEditor = true }) { Text("Create project") }
                            }
                        }
                        if(needsSorting.isEmpty()) item { InfoCard("Nothing waiting", "New uncertain or unassigned AI files will appear here.",Icons.Outlined.CheckCircle) {} }
                        if(needsSorting.isNotEmpty()) item {
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                Text("${sortingSelection.size} selected",fontWeight = FontWeight.SemiBold,modifier = Modifier.weight(1f))
                                TextButton(onClick = { sortingSelection = if(sortingSelection.size == needsSorting.size) emptySet() else needsSorting.mapTo(mutableSetOf()) { it.path } }) {
                                    Text(if(sortingSelection.size == needsSorting.size) "Clear" else "Select all")
                                }
                            }
                        }
                        items(needsSorting,key = { it.path }) { file ->
                            FileRow(file,selected = file.path in sortingSelection) {
                                sortingSelection = if(file.path in sortingSelection) sortingSelection - file.path else sortingSelection + file.path
                            }
                        }
                        if(needsSorting.isNotEmpty()) item {
                            Button(enabled = sortingSelection.isNotEmpty() && projects.isNotEmpty(),onClick = { assignmentPaths = sortingSelection.toList() },modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.DriveFileMove,null);Spacer(Modifier.width(8.dp));Text(if(sortingSelection.size == 1) "Assign file" else "Assign ${sortingSelection.size} files")
                            }
                        }
                    }
                    "Recent" -> {
                        item { Title("Recent", "Likely AI files found on your phone.") }
                        item { Text("All files stay in their original locations in this test. Uncertain sources need your review.",color = Muted,fontSize = 14.sp) }
                        if(recent.isEmpty()) item { InfoCard("Nothing detected yet", "Launch a selected AI from the Hub, download a file, then come back here.",Icons.Outlined.InsertDriveFile) {} }
                        items(recent,key = { it.id }) { file -> FileRow(file) { detail = file } }
                    }
                    "Activity" -> {
                        item { Title("Activity", "A local record of FileMate's work.") }
                        item { InfoCard(if(checking) "Checking for missed files…" else "Catch-up",lastCheck?.toLongOrNull()?.let { "Last successful check: ${time(it)}" } ?: "The first check indexes existing files without moving them.",Icons.Outlined.Refresh) {
                            TextButton(enabled = filesAllowed && !checking,onClick = { app.scope.launch { app.reconcile() } }) { Text("Check now") }
                        } }
                        item { Text("$ignored new or changed files had no useful AI clues and were left out of Recent.",fontSize = 13.sp,color = Muted) }
                        items(history) { row -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(row.title,fontWeight = FontWeight.SemiBold)
                            Text(row.detail,fontSize = 13.sp,color = Muted)
                            Text(time(row.time),fontSize = 11.sp,color = Muted,modifier = Modifier.padding(top = 5.dp))
                            HorizontalDivider(Modifier.padding(top = 12.dp),color = Color(0xFFE0E7F0))
                        } }
                    }
                    "Setup" -> {
                        item { Title("Setup", "Everything stays on your phone.") }
                        item { PermissionCard("File access",filesAllowed,"Watch shared Downloads and Documents. Private app folders are excluded.","Allow file access") {
                            try { activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,"package:${activity.packageName}".toUri())) }
                            catch(_: Exception) { try { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch(_: Exception) { error = "Open Android Settings → Apps → Special app access → All files access → FileMate." } }
                        } }
                        item { PermissionCard("App activity",usageAllowed,"See which selected AI was used most recently and stop after AI inactivity. Optional; no screen content is read.","Allow app activity") {
                            try { activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                            catch(_: Exception) { error = "Open Android Settings → Special app access → Usage access → FileMate." }
                        } }
                        item { PermissionCard("Monitoring notification",notificationsAllowed,"A quiet status notification lets you stop a session. It disappears when the session ends.","Allow notification") {
                            if(Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } }
                        item { Text("Your AI apps",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        items(hub,key = { it.packageName }) { selected ->
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(selected.packageName,activity,Modifier.size(32.dp));Spacer(Modifier.width(12.dp))
                                Text(selected.label,modifier = Modifier.weight(1f))
                                IconButton(onClick = { app.scope.launch { app.store.remove(selected);app.store.history("Removed from Hub", "${selected.label} is still installed on your phone.");app.changed() } }) { Icon(Icons.Outlined.RemoveCircleOutline,"Remove ${selected.label} from Hub") }
                            }
                        }
                        item { OutlinedButton(onClick = { page = "Add apps" },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add installed apps") } }
                        item { Text("Watching: Downloads and Documents, including their subfolders. Session: 30 minutes of AI inactivity. Projects and confirmed assignments are stored locally. No files are moved, renamed, uploaded or deleted in Stage 2B.",fontSize = 13.sp,color = Muted) }
                        item { Text("FileMate ${BuildConfig.VERSION_NAME} · Android 11 or newer",fontSize = 12.sp,color = Muted) }
                    }
                }
            }
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null },title = { Text("FileMate") },text = { Text(message) },confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
    detail?.let { file -> AlertDialog(onDismissRequest = { detail = null },title = { Text(file.name,maxLines = 3,overflow = TextOverflow.Ellipsis) },text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${file.source ?: "Unknown source"} · ${file.confidence} source confidence",fontWeight = FontWeight.SemiBold)
            Text(file.reason)
            Text(if(file.projectName == null) "Project: Not assigned." else "Project: ${file.projectName} · ${file.projectConfidence.lowercase()} by you.")
            Text("Original name and location unchanged.")
            Text(file.path,fontSize = 12.sp)
            Text("${file.size} bytes · ${file.via}",fontSize = 12.sp,color = Muted)
        }
    },dismissButton = {
        if(file.projectId != null) TextButton(onClick = {
            detail = null
            uiScope.launch {
                runCatching { withContext(Dispatchers.IO) { app.store.unassignFiles(listOf(file.path)) } }
                    .onSuccess { app.changed() }
                    .onFailure { error = "FileMate couldn't return this file to Needs Sorting." }
            }
        }) { Text("Needs Sorting") }
    },confirmButton = { TextButton(onClick = {
        detail = null
        if(projects.isEmpty()) { editingProjectId = null;showProjectEditor = true }
        else assignmentPaths = listOf(file.path)
    }) { Text(if(file.projectId == null) "Assign" else "Change project") } }) }
    if(showProjectEditor) {
        val editing = projects.firstOrNull { it.id == editingProjectId }
        ProjectEditorDialog(editing,onDismiss = { showProjectEditor = false;editingProjectId = null }) { name ->
            try {
                withContext(Dispatchers.IO) {
                    if(editing == null) app.store.createProject(name) else app.store.renameProject(editing.id,name)
                }
                app.changed()
                null
            } catch(e: Exception) {
                when(e) {
                    is android.database.sqlite.SQLiteConstraintException -> "A project with that name already exists."
                    is IllegalArgumentException -> e.message ?: "Check the project name."
                    else -> "FileMate couldn't save this project. Try again."
                }
            }
        }
    }
    if(assignmentPaths.isNotEmpty()) AssignmentDialog(projects,assignmentPaths.size,onDismiss = { assignmentPaths = emptyList() }) { projectId ->
        try {
            withContext(Dispatchers.IO) { app.store.assignFiles(assignmentPaths,projectId) }
            sortingSelection = sortingSelection - assignmentPaths.toSet()
            assignmentPaths = emptyList()
            app.changed()
            null
        } catch(e: Exception) { e.message ?: "FileMate couldn't assign the selected files." }
    }
    deleteProject?.let { project -> AlertDialog(
        onDismissRequest = { deleteProject = null },
        title = { Text("Delete ${project.name}?") },
        text = { Text(if(project.fileCount == 0) "This removes the empty project." else "The files will stay on your phone and return to Needs Sorting.") },
        dismissButton = { TextButton(onClick = { deleteProject = null }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            deleteProject = null
            uiScope.launch {
                runCatching { withContext(Dispatchers.IO) { app.store.deleteProject(project.id) } }
                    .onSuccess { selectedProjectId = null;page = "Projects";app.changed() }
                    .onFailure { error = "FileMate couldn't delete this project. Try again." }
            }
        }) { Text("Delete") } }
    ) }
}

@Composable private fun Title(title: String, subtitle: String) { Column { Text(title,fontSize = 32.sp,fontWeight = FontWeight.Bold);Text(subtitle,color = Muted,modifier = Modifier.padding(top = 5.dp)) } }
@Composable private fun InfoCard(title: String, body: String, icon: ImageVector, action: @Composable () -> Unit) {
    Surface(color = Pale,shape = RoundedCornerShape(18.dp),modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp),horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(icon,null,tint = Blue,modifier = Modifier.padding(top = 3.dp))
            Column(Modifier.weight(1f)) { Text(title,fontWeight = FontWeight.SemiBold,fontSize = 17.sp);Text(body,color = Muted,fontSize = 14.sp,modifier = Modifier.padding(top = 5.dp));action() }
        }
    }
}
@Composable private fun ActionRow(title: String, subtitle: String, icon: ImageVector, action: () -> Unit) {
    OutlinedCard(onClick = action,modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,tint = Blue);Column(Modifier.weight(1f)) { Text(title,fontWeight = FontWeight.SemiBold);Text(subtitle,fontSize = 12.sp,color = Muted) };Icon(Icons.Outlined.ChevronRight,null)
    } }
}
@Composable private fun FileRow(file: DetectedFile, selected: Boolean? = null, action: () -> Unit) {
    Card(onClick = action,colors = CardDefaults.cardColors(containerColor = Color.White),modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if(selected != null) Checkbox(checked = selected,onCheckedChange = { action() })
            Icon(Icons.Outlined.InsertDriveFile,null,tint = Blue)
            Column(Modifier.weight(1f)) {
                Text(file.name,fontWeight = FontWeight.SemiBold,maxLines = 2,overflow = TextOverflow.Ellipsis)
                Text("${file.source ?: "Unknown source"} · ${file.confidence.lowercase()} confidence",fontSize = 12.sp,color = Muted)
                Text(file.projectName?.let { "$it · ${file.projectConfidence.lowercase()} project" } ?: "Project not assigned",fontSize = 12.sp,color = Muted)
                Text("${file.via} · ${time(file.time)}",fontSize = 11.sp,color = Muted)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint = Muted)
        }
    }
}
@Composable private fun ProjectEditorDialog(project: Project?, onDismiss: () -> Unit, save: suspend (String) -> String?) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var message by remember(project?.id) { mutableStateOf<String?>(null) }
    var saving by remember(project?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if(!saving) onDismiss() },
        title = { Text(if(project == null) "Create project" else "Rename project") },
        text = { Column {
            OutlinedTextField(value = name,onValueChange = { if(it.length <= ProjectNames.MAX_LENGTH) { name = it;message = null } },
                singleLine = true,label = { Text("Project name") },isError = message != null,
                supportingText = { Text(message ?: "${name.length}/${ProjectNames.MAX_LENGTH}") },modifier = Modifier.fillMaxWidth())
        } },
        dismissButton = { TextButton(enabled = !saving,onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = !saving,onClick = {
            scope.launch {
                saving = true
                message = save(name)
                saving = false
                if(message == null) onDismiss()
            }
        }) { Text(if(saving) "Saving…" else "Save") } }
    )
}
@Composable private fun AssignmentDialog(projects: List<Project>, fileCount: Int, onDismiss: () -> Unit, assign: suspend (Long) -> String?) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if(!saving) onDismiss() },
        title = { Text(if(fileCount == 1) "Choose a project" else "Assign $fileCount files") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The files stay where they are. This saves only the project assignment.",fontSize = 13.sp,color = Muted)
            LazyColumn(Modifier.heightIn(max = 330.dp)) {
                items(projects,key = { it.id }) { project ->
                    Row(Modifier.fillMaxWidth().clickable { selectedId = project.id;message = null }.padding(vertical = 9.dp),verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedId == project.id,onClick = { selectedId = project.id;message = null })
                        Text(project.name,modifier = Modifier.weight(1f),maxLines = 2,overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            message?.let { Text(it,color = MaterialTheme.colorScheme.error,fontSize = 12.sp) }
        } },
        dismissButton = { TextButton(enabled = !saving,onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = selectedId != null && !saving,onClick = {
            val id = selectedId ?: return@Button
            scope.launch {
                saving = true
                message = assign(id)
                saving = false
                if(message == null) onDismiss()
            }
        }) { Text(if(saving) "Assigning…" else "Assign") } }
    )
}
@Composable private fun PermissionCard(title: String, allowed: Boolean, description: String, button: String, action: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title,fontWeight = FontWeight.SemiBold,modifier = Modifier.weight(1f));if(allowed) Icon(Icons.Outlined.CheckCircle,"Allowed",tint = Color(0xFF357252)) }
        Text(description,fontSize = 13.sp,color = Muted)
        if(!allowed) Button(onClick = action) { Text(button) } else Text("Allowed",color = Color(0xFF357252),fontSize = 12.sp)
    } }
}
@Composable private fun AppIcon(packageName: String, activity: android.content.Context, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,packageName) {
        value = withContext(Dispatchers.IO) { runCatching { activity.packageManager.getApplicationIcon(packageName).toBitmap(96,96) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(),null,modifier) } ?: Icon(Icons.Outlined.Apps,null,modifier,tint = Blue)
}
@Composable private fun AppPicker(app: FileMateApp, selected: List<HubApp>, modifier: Modifier, done: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val installed by produceState<List<HubApp>?>(null) { value = withContext(Dispatchers.IO) {
        val pm = app.packageManager
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter { it.activityInfo.packageName != app.packageName }
            .map { HubApp(it.activityInfo.packageName,it.loadLabel(pm).toString()) }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    } }
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) { Text("Add apps",fontSize = 28.sp,fontWeight = FontWeight.Bold,modifier = Modifier.weight(1f));TextButton(onClick = done) { Text("Done") } }
        Text("Choose any installed app to include in your AI Hub.",fontSize = 14.sp,color = Muted)
        OutlinedTextField(value = query,onValueChange = { query = it },singleLine = true,label = { Text("Search installed apps") },leadingIcon = { Icon(Icons.Outlined.Search,null) },modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        if(installed == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        val matches = installed.orEmpty().filter { it.label.contains(query,true) || it.packageName.contains(query,true) }
        if(installed != null && matches.isEmpty()) Text("No matching installed apps. Install the app on your phone first, then reopen this list.",color = Muted)
        LazyColumn(Modifier.weight(1f),contentPadding = PaddingValues(bottom = 24.dp)) {
            items(matches,key = { it.packageName }) { candidate ->
                val added = selected.any { it.packageName == candidate.packageName }
                val toggle = { app.scope.launch { if(added) app.store.remove(candidate) else app.store.add(candidate);app.changed() };Unit }
                Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(candidate.packageName,app,Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) { Text(candidate.label,fontWeight = FontWeight.Medium);Text(candidate.packageName,fontSize = 10.sp,color = Muted,maxLines = 1,overflow = TextOverflow.Ellipsis) }
                    Checkbox(checked = added,onCheckedChange = { toggle() })
                }
            }
        }
    }
}
private fun time(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(timestamp))
