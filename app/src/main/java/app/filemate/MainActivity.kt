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
    var gallerySorting by rememberSaveable { mutableStateOf(false) }
    var galleryGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var galleryProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showProjectEditor by rememberSaveable { mutableStateOf(false) }
    var deleteProject by remember { mutableStateOf<Project?>(null) }
    var sortingSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var assignmentPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var cleanupFlag by rememberSaveable { mutableIntStateOf(0) }
    var cleanupTitle by rememberSaveable { mutableStateOf("All scanned files") }
    var cleanupDetail by remember { mutableStateOf<CleanupEntry?>(null) }
    var cleanupSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var organiseEntries by remember { mutableStateOf<List<CleanupEntry>>(emptyList()) }
    var organisePlans by remember { mutableStateOf<List<OrganisePlan>>(emptyList()) }
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
    val cleanupProgress by app.cleanup.collectAsStateWithLifecycle()
    val hub by produceState(emptyList<HubApp>(),revision) { value = withContext(Dispatchers.IO) { app.store.hub() } }
    val recent by produceState(emptyList<DetectedFile>(),revision) { value = withContext(Dispatchers.IO) { app.store.recent() } }
    val history by produceState(emptyList<HistoryItem>(),revision) { value = withContext(Dispatchers.IO) { app.store.history() } }
    val projects by produceState(emptyList<Project>(),revision) { value = withContext(Dispatchers.IO) { app.store.projects() } }
    val detectedNeedsSorting by produceState(emptyList<DetectedFile>(),revision) { value = withContext(Dispatchers.IO) { app.store.needsSorting() } }
    val galleryProgress by app.gallery.collectAsStateWithLifecycle()
    val sortingMedia by produceState(emptyList<IndexedMedia>(),revision,galleryProgress,resumed) {
        value = if(resumed && galleryProgress.ready && !galleryProgress.running && MediaAccess.read(activity).any)
            withContext(Dispatchers.IO) { app.store.mediaItems() } else emptyList()
    }
    val accessibleSortingMedia = if(resumed && galleryProgress.ready && !galleryProgress.running && MediaAccess.read(activity).any) sortingMedia else emptyList()
    val screenshotIntake = screenshotGroups(accessibleSortingMedia,unassignedOnly = true)
    val screenshotIds = screenshotIntake.flatMap { it.identities }.toSet()
    val screenshotPaths = sortingMedia.filter { it.identity in screenshotIds }.mapTo(mutableSetOf()) { it.currentPath }
    val needsSorting = detectedNeedsSorting.filter { it.path !in screenshotPaths }
    val sortingCount = needsSorting.size + screenshotIds.size
    LaunchedEffect(resumed,page) {
        if(resumed && (page == "Projects" || page == "Needs Sorting")) app.refreshGallery()
    }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val projectFiles by produceState(emptyList<DetectedFile>(),revision,selectedProjectId) {
        value = selectedProjectId?.let { withContext(Dispatchers.IO) { app.store.projectFiles(it) } }.orEmpty()
    }
    val projectMediaCount by produceState(0,revision,selectedProjectId) { value = selectedProjectId?.let { withContext(Dispatchers.IO) { app.store.galleryProjectCount(it) } } ?: 0 }
    val cleanupSummary by produceState<CleanupSummary?>(null,revision) { value = withContext(Dispatchers.IO) { app.store.cleanupSummary() } }
    val cleanupEntries by produceState(emptyList<CleanupEntry>(),revision,cleanupFlag) { value = withContext(Dispatchers.IO) { app.store.cleanupEntries(cleanupFlag) } }
    val selectedFolders by produceState(emptyList<SelectedFolder>(),revision) { value = withContext(Dispatchers.IO) { app.store.selectedFolders() } }
    val fileActions by produceState(emptyList<FileActionRecord>(),revision) { value = withContext(Dispatchers.IO) { app.store.fileActions() } }
    val ignored by produceState("0",revision) { value = withContext(Dispatchers.IO) { app.store.state("ignored") ?: "0" } }
    val lastCheck by produceState<String?>(null,revision) { value = withContext(Dispatchers.IO) { app.store.state("last_check") } }
    val monitorTimeout by produceState(MonitoringSettings.DEFAULT_MINUTES,revision) {
        value = withContext(Dispatchers.IO) { MonitoringSettings.minutes(app.store.state("monitor_timeout_minutes")) }
    }
    val namingPreference by produceState(NamingPreference.KEEP_CURRENT,revision) {
        value = withContext(Dispatchers.IO) { NamingSettings.parse(app.store.state("naming_preference")) }
    }
    val externalStorage by produceState<GrantedStorageFolder?>(null,revision) { value = withContext(Dispatchers.IO) { app.store.externalStorageFolder() } }
    val filesAllowed = remember(permissionTick) { Environment.isExternalStorageManager() }
    val usageAllowed = remember(permissionTick) { Access.usage(activity) }
    val notificationsAllowed = remember(permissionTick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri != null) try {
            activity.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            app.addSelectedFolder(uri)
        } catch(_: Exception) { error = "Android didn't keep access to that folder. Choose it again and allow access." }
    }
    val storagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri != null) try {
            activity.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val storage=GrantedStorage(activity)
            app.scope.launch { app.store.externalStorageFolder(GrantedStorageFolder(uri.toString(),storage.name(uri)));app.changed() }
        } catch(_: Exception) { error = "Android did not keep access to that storage folder. Nothing was uploaded or removed." }
    }
    LaunchedEffect(needsSorting) {
        val available = needsSorting.mapTo(mutableSetOf()) { it.path }
        sortingSelection = sortingSelection.intersect(available)
    }
    LaunchedEffect(cleanupEntries,page) {
        if(page == "Cleanup list") cleanupSelection = cleanupSelection.intersect(cleanupEntries.mapTo(mutableSetOf()) { it.path })
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
        else if(page == "Gallery") page = if(gallerySorting) "Needs Sorting" else "Phone"
        else if(page == "Cleanup list") page = "Phone"
        else page = "AI Hub"
    }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp,vertical = 12.dp),verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.filemate_logo),contentDescription = null,Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Text("FileMate",fontWeight = FontWeight.Bold,fontSize = 24.sp,modifier = Modifier.weight(1f))
                Surface(color = Pale,shape = RoundedCornerShape(12.dp)) { Text("V1 RECOVERY",color = Blue,fontSize = 11.sp,fontWeight = FontWeight.SemiBold,modifier = Modifier.padding(10.dp,7.dp)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFF3F7FD)) {
                listOf("AI Hub" to Icons.Outlined.Apps,"Projects" to Icons.Outlined.Folder,"Phone" to Icons.Outlined.PhoneAndroid,"Activity" to Icons.Outlined.History,"Setup" to Icons.Outlined.Tune).forEach { (label,icon) ->
                    NavigationBarItem(selected = page == label || ((page == "Add apps" || page == "Recent") && label == "AI Hub") || ((page == "Project detail" || page == "Needs Sorting") && label == "Projects") || ((page == "Cleanup list" || page == "Gallery") && label == "Phone"),onClick = {
                        page = label
                        if(label == "Projects") selectedProjectId = null
                    },icon = { Icon(icon,null) },label = { Text(label,fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        when(page) {
            "Add apps" -> AppPicker(app,hub,Modifier.padding(padding)) { page = "AI Hub" }
            "Gallery" -> GalleryScreen(app,resumed,galleryProjectId,Modifier.padding(padding),gallerySorting,galleryGroup) { page = if(gallerySorting) "Needs Sorting" else "Phone" }
            "External storage" -> externalStorage?.let { ProviderScreen(app,it,Modifier.padding(padding)) { page="Setup" } } ?: run { LaunchedEffect(Unit) { page="Setup" } }
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
                        if(sortingCount > 0) item { ActionRow("Needs Sorting","$sortingCount items need a project",Icons.Outlined.RuleFolder) { page = "Needs Sorting" } }
                        if(recent.isNotEmpty()) item { ActionRow("Recent detections","${recent.size} likely AI files · review source clues",Icons.Outlined.InsertDriveFile) { page = "Recent" } }
                        item { Text("High-confidence AI downloads can be organised automatically. Uncertain files stay untouched in Needs Sorting.",color = Muted,fontSize = 12.sp) }
                    }
                    "Projects" -> {
                        item { Title("Projects", "Keep files together by what you're working on.") }
                        item { ActionRow("Needs Sorting",if(sortingCount == 0) "No accessible items waiting" else "$sortingCount items ready to review",Icons.Outlined.RuleFolder) { page = "Needs Sorting" } }
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
                            val storageRule by produceState(StorageRule.PHONE_ONLY,revision,selectedProject.id) {
                                value = withContext(Dispatchers.IO) { app.store.projectStorageRule(selectedProject.id) }
                            }
                            item {
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(15.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Future file storage",fontWeight = FontWeight.SemiBold)
                                        Text("Phone only is the default. Existing files never move when this rule changes.",fontSize = 12.sp,color = Muted)
                                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StorageRule.entries.forEach { rule ->
                                                FilterChip(selected = storageRule == rule,onClick = {
                                                    uiScope.launch { withContext(Dispatchers.IO) { app.store.setProjectStorageRule(selectedProject.id,rule) };app.changed() }
                                                },label = { Text(rule.label) })
                                            }
                                        }
                                        if(storageRule != StorageRule.PHONE_ONLY) Text(if(externalStorage==null) "Choose a storage-provider folder in Setup before copying anything." else "Selected provider: ${externalStorage!!.name}. Existing files are never changed just because this rule changes.",fontSize = 12.sp,color = Muted)
                                    }
                                }
                            }
                            if(projectMediaCount > 0) item { ActionRow("Project Gallery","$projectMediaCount indexed photos or videos · access may limit what is visible",Icons.Outlined.PhotoLibrary) { gallerySorting = false;galleryGroup = null;galleryProjectId = selectedProject.id;page = "Gallery" } }
                            if(projectFiles.isEmpty() && projectMediaCount == 0) item {
                                InfoCard("No files assigned", "Assign files in Needs Sorting or photos and videos in Gallery.",Icons.Outlined.DriveFileMove) {}
                            }
                            items(projectFiles,key = { it.path }) { file ->
                                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    FileRow(file) { detail = file }
                                    if(storageRule != StorageRule.PHONE_ONLY) {
                                        TextButton(enabled=externalStorage!=null,onClick={
                                            uiScope.launch {
                                                val problem=withContext(Dispatchers.IO) { ProjectProviderUpload(activity,app.store).upload(file,storageRule) }
                                                app.changed()
                                                if(problem!=null) error=problem
                                            }
                                        }) { Text(if(storageRule==StorageRule.DRIVE_AFTER_UPLOAD) "Copy to storage, then remove verified local copy" else "Copy to storage") }
                                    }
                                }
                            }
                        }
                    }
                    "Needs Sorting" -> {
                        item { Title("Needs Sorting", "Uncertain files wait here for you. High-confidence matches are handled automatically.") }
                        item { Text("Source and project confidence are separate. FileMate only acts automatically when both are strong enough; otherwise the file stays here.",fontSize = 13.sp,color = Muted) }
                        if(projects.isEmpty()) item {
                            InfoCard("Create a project first", "You need somewhere to assign the selected files.",Icons.Outlined.CreateNewFolder) {
                                TextButton(onClick = { editingProjectId = null;showProjectEditor = true }) { Text("Create project") }
                            }
                        }
                        item { Text("Screenshot groups",fontWeight = FontWeight.SemiBold) }
                        item { Text("Unassigned screenshots grouped by date and folder clues. These groups do not predict a project. Camera folder items stay out.",fontSize = 13.sp,color = Muted) }
                        if(galleryProgress.running) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if(screenshotIntake.isEmpty()) item { Text(if(galleryProgress.running) "Checking accessible screenshots…" else "No accessible unassigned screenshots. Open Gallery to choose photo access or refresh.",fontSize = 13.sp,color = Muted) }
                        items(screenshotIntake,key = { "screenshots:${it.key}" }) { group ->
                            ActionRow(group.day,"${group.identities.size} screenshots · ${group.folder}",Icons.Outlined.PhotoLibrary) {
                                gallerySorting = true;galleryGroup = group.key;galleryProjectId = null;page = "Gallery"
                            }
                        }
                        item { TextButton(onClick = { gallerySorting = true;galleryGroup = null;galleryProjectId = null;page = "Gallery" }) { Text("Review all unassigned screenshots") } }
                        item { Text("Other files",fontWeight = FontWeight.SemiBold) }
                        if(needsSorting.isEmpty()) item { Text("No other files waiting.",fontSize = 13.sp,color = Muted) }
                        if(needsSorting.isNotEmpty()) item {
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                Text("${sortingSelection.size} selected",fontWeight = FontWeight.SemiBold,modifier = Modifier.weight(1f))
                                TextButton(onClick = { sortingSelection = if(sortingSelection.size == needsSorting.size) emptySet() else needsSorting.mapTo(mutableSetOf()) { it.path } }) {
                                    Text(if(sortingSelection.size == needsSorting.size) "Clear" else "Select all")
                                }
                            }
                        }
                        items(needsSorting,key = { it.path }) { file ->
                            val learned by produceState<ProjectMatch?>(null,file.path,revision) {
                                value = withContext(Dispatchers.IO) { app.store.learnedProject(file.name) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FileRow(file,selected = file.path in sortingSelection) {
                                    sortingSelection = if(file.path in sortingSelection) sortingSelection - file.path else sortingSelection + file.path
                                }
                                learned?.let { suggestion ->
                                    Text("Suggestion: ${suggestion.projectName} · learned from earlier assignments. Review before applying.",
                                        fontSize = 11.sp,color = Muted,modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                        if(needsSorting.isNotEmpty()) item {
                            Button(enabled = sortingSelection.isNotEmpty() && projects.isNotEmpty(),onClick = { assignmentPaths = sortingSelection.toList() },modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.DriveFileMove,null);Spacer(Modifier.width(8.dp));Text(if(sortingSelection.size == 1) "Assign file" else "Assign ${sortingSelection.size} files")
                            }
                        }
                    }
                    "Phone" -> {
                        item { ActionRow("Gallery","Browse local photos, screenshots and videos",Icons.Outlined.PhotoLibrary) { gallerySorting = false;galleryGroup = null;galleryProjectId = null;page = "Gallery" } }
                        item { Title("Clean Up My Phone", "Scan first. You choose every change.") }
                        item { Text("The scan reads shared-file metadata and hashes only same-size duplicate candidates. It does not move, rename or delete anything.",fontSize = 13.sp,color = Muted) }
                        if(!filesAllowed) item {
                            InfoCard("File access is off", "Allow shared-file access before scanning the standard phone folders.",Icons.Outlined.FolderOpen) {
                                TextButton(onClick = { page = "Setup" }) { Text("Open Setup") }
                            }
                        }
                        if(cleanupProgress.running) item {
                            InfoCard(cleanupProgress.stage.ifBlank { "Scanning…" },"${cleanupProgress.files} files found so far",Icons.Outlined.Search) {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                            }
                        }
                        if(!cleanupProgress.running && cleanupProgress.stage.contains("unavailable")) item { InfoCard("Scan completed with limits",cleanupProgress.stage,Icons.Outlined.Info) {} }
                        cleanupProgress.error?.let { problem -> item { InfoCard("Scan needs another try",problem,Icons.Outlined.ErrorOutline) {} } }
                        item {
                            Button(enabled = filesAllowed && !cleanupProgress.running,onClick = { app.scanPhone() },modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Search,null);Spacer(Modifier.width(8.dp));Text(if(cleanupSummary == null) "Scan my phone" else "Scan again")
                            }
                        }
                        if(cleanupSummary == null) item { InfoCard("Nothing changes during a scan", "Results appear as review lists. Camera photos are excluded from the old-file suggestion.",Icons.Outlined.Shield) {} }
                        cleanupSummary?.let { summary ->
                            item { Text("Last scan ${time(summary.completed)} · ${summary.totalFiles} files · ${formatBytes(summary.totalBytes)}",fontSize = 12.sp,color = Muted) }
                            item { ActionRow("Browse scanned files","${summary.totalFiles} files across shared folders",Icons.Outlined.FolderOpen) { cleanupFlag = 0;cleanupTitle = "All scanned files";page = "Cleanup list" } }
                            item { ActionRow("Likely AI files","${summary.likelyAi} suggestions",Icons.Outlined.AutoAwesome) { cleanupFlag = CleanupFlags.LIKELY_AI;cleanupTitle = "Likely AI files";page = "Cleanup list" } }
                            item { ActionRow("Unsorted Downloads","${summary.unsortedDownloads} files without a project",Icons.Outlined.Download) { cleanupFlag = CleanupFlags.UNSORTED_DOWNLOAD;cleanupTitle = "Unsorted Downloads";page = "Cleanup list" } }
                            item { ActionRow("Large files","${summary.large} files of 100 MB or more",Icons.Outlined.DataUsage) { cleanupFlag = CleanupFlags.LARGE;cleanupTitle = "Large files";page = "Cleanup list" } }
                            item { ActionRow("Old files","${summary.old} suggestions · camera and pictures excluded",Icons.Outlined.Event) { cleanupFlag = CleanupFlags.OLD;cleanupTitle = "Old files";page = "Cleanup list" } }
                            item { ActionRow("Archives","${summary.archives} ZIP or archive files",Icons.Outlined.Inventory2) { cleanupFlag = CleanupFlags.ARCHIVE;cleanupTitle = "Archives";page = "Cleanup list" } }
                            item { ActionRow("Exact duplicates","${summary.duplicateGroups} groups · ${summary.duplicateFiles} files · up to ${formatBytes(summary.reclaimableBytes)} reviewable",Icons.Outlined.ContentCopy) { cleanupFlag = CleanupFlags.DUPLICATE;cleanupTitle = "Exact duplicates";page = "Cleanup list" } }
                        }
                    }
                    "Cleanup list" -> {
                        item { Title(cleanupTitle, "Review only. Nothing here is selected for deletion.") }
                        if(cleanupEntries.isEmpty()) item { InfoCard("No matches", "Run the scan again after files on the phone change.",Icons.Outlined.CheckCircle) {} }
                        if(cleanupEntries.isNotEmpty()) item {
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                Text("${cleanupSelection.size} selected",fontWeight = FontWeight.SemiBold,modifier = Modifier.weight(1f))
                                TextButton(onClick = { cleanupSelection = if(cleanupSelection.size == cleanupEntries.size) emptySet() else cleanupEntries.mapTo(mutableSetOf()) { it.path } }) {
                                    Text(if(cleanupSelection.size == cleanupEntries.size) "Clear" else "Select all")
                                }
                            }
                        }
                        items(cleanupEntries,key = { it.path }) { entry ->
                            CleanupRow(entry,entry.path in cleanupSelection,toggle = {
                                cleanupSelection = if(entry.path in cleanupSelection) cleanupSelection - entry.path else cleanupSelection + entry.path
                            },inspect = { cleanupDetail = entry })
                        }
                        if(cleanupEntries.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(enabled = cleanupSelection.isNotEmpty() && projects.isNotEmpty(),onClick = { assignmentPaths = cleanupSelection.toList() },modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Folder,null);Spacer(Modifier.width(8.dp));Text("Assign selected to project")
                                }
                                Button(enabled = cleanupSelection.isNotEmpty() && projects.isNotEmpty() && !monitor.running,onClick = { organiseEntries = cleanupEntries.filter { it.path in cleanupSelection } },modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.DriveFileMove,null);Spacer(Modifier.width(8.dp));Text("Preview move or rename")
                                }
                                if(projects.isEmpty()) Text("Create a project before assigning or organising files.",fontSize = 12.sp,color = Muted)
                                if(monitor.running) Text("Stop AI monitoring before moving files so FileMate doesn't mistake its own changes for new downloads.",fontSize = 12.sp,color = Muted)
                            }
                        }
                    }
                    "Recent" -> {
                        item { Title("Recent", "Likely AI files found on your phone.") }
                        item { Text("Automatically organised files show their project. Uncertain files remain untouched for review.",color = Muted,fontSize = 14.sp) }
                        if(recent.isEmpty()) item { InfoCard("Nothing detected yet", "Launch a selected AI from the Hub, download a file, then come back here.",Icons.Outlined.InsertDriveFile) {} }
                        items(recent,key = { it.id }) { file -> FileRow(file) { detail = file } }
                    }
                    "Activity" -> {
                        item { Title("Activity", "A local record of FileMate's work.") }
                        if(fileActions.isNotEmpty()) item { Text("File changes and undo",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        items(fileActions,key = { "action-${it.id}" }) { action ->
                            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${action.sourceName} → ${action.targetName}",fontWeight = FontWeight.SemiBold,maxLines = 2,overflow = TextOverflow.Ellipsis)
                                Text("${action.projectName} · ${action.status.replaceFirstChar { it.uppercase() }}",fontSize = 12.sp,color = Muted)
                                action.error?.let { Text(it,fontSize = 12.sp,color = MaterialTheme.colorScheme.error) }
                                if(action.status == "applied") TextButton(onClick = {
                                    uiScope.launch {
                                        val problem = withContext(Dispatchers.IO) { FileOrganiser(app.store).undo(action) }
                                        app.changed()
                                        if(problem != null) error = problem
                                    }
                                }) { Icon(Icons.Outlined.Undo,null);Spacer(Modifier.width(6.dp));Text("Undo") }
                            } }
                        }
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
                        item { Text("Monitoring",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Stop after AI inactivity",fontWeight = FontWeight.SemiBold)
                                    Text("Default is 30 minutes. Changing this affects future monitoring sessions.",fontSize = 12.sp,color = Muted)
                                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MonitoringSettings.allowedMinutes.forEach { minutes ->
                                            FilterChip(selected = monitorTimeout == minutes,onClick = {
                                                app.scope.launch { app.store.state("monitor_timeout_minutes",minutes.toString());app.store.history("Monitoring timeout changed","Future sessions stop after $minutes minutes of AI inactivity.");app.changed() }
                                            },label = { Text("$minutes min") })
                                        }
                                    }
                                }
                            }
                        }
                        item { Text("Naming",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Reviewed file moves",fontWeight = FontWeight.SemiBold)
                                    Text("Automatic high-confidence organisation keeps the downloaded filename. This preference only changes the default shown when you deliberately review a move.",fontSize = 12.sp,color = Muted)
                                    NamingPreference.entries.forEach { choice ->
                                        FilterChip(selected = namingPreference == choice,onClick = {
                                            app.scope.launch { app.store.state("naming_preference",choice.name);app.store.history("Naming preference changed",choice.label);app.changed() }
                                        },label = { Text(choice.label) })
                                    }
                                }
                            }
                        }
                        item { Text("Extra scan folders",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        item { Text("Downloads, Documents, Camera, Pictures, Movies and Music are included automatically when file access is allowed. Add any other shared folder you want included in deliberate scans.",fontSize = 13.sp,color = Muted) }
                        items(selectedFolders,key = { it.uri }) { folder ->
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder,null,tint = Blue);Spacer(Modifier.width(12.dp))
                                Text(folder.name,modifier = Modifier.weight(1f),maxLines = 2,overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = {
                                    runCatching { activity.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(folder.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                                    app.scope.launch { app.store.removeSelectedFolder(folder.uri);app.changed() }
                                }) { Icon(Icons.Outlined.RemoveCircleOutline,"Remove ${folder.name}") }
                            }
                        }
                        item { OutlinedButton(onClick = { folderPicker.launch(null) },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CreateNewFolder,null);Spacer(Modifier.width(8.dp));Text("Add scan folder") } }
                        item { Text("Your AI apps",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        items(hub,key = { it.packageName }) { selected ->
                            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(selected.packageName,activity,Modifier.size(32.dp));Spacer(Modifier.width(12.dp))
                                Text(selected.label,modifier = Modifier.weight(1f))
                                IconButton(onClick = { app.scope.launch { app.store.remove(selected);app.store.history("Removed from Hub", "${selected.label} is still installed on your phone.");app.changed() } }) { Icon(Icons.Outlined.RemoveCircleOutline,"Remove ${selected.label} from Hub") }
                            }
                        }
                        item { OutlinedButton(onClick = { page = "Add apps" },modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add installed apps") } }
                        item { Text("Watching: Downloads and Documents, including their subfolders. Session: $monitorTimeout minutes of AI inactivity. High-confidence live AI downloads may be organised automatically; uncertain files stay untouched. Phone cleanup remains review-first. Every move is recorded with Undo. FileMate does not auto-delete files.",fontSize = 13.sp,color = Muted) }
                        item { Text("Drive",fontSize = 22.sp,fontWeight = FontWeight.Bold) }
                        item { InfoCard(if(externalStorage==null) "Optional storage folder not connected" else "Storage folder: ${externalStorage!!.name}",
                            "Uses Android folder picker access instead of a developer cloud API. If Drive appears in the picker, you can grant one folder without an API key or metered developer service.",Icons.Outlined.CloudQueue) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={ storagePicker.launch(null) }) { Text(if(externalStorage==null) "Choose folder" else "Change") }
                                if(externalStorage!=null) TextButton(onClick={ page="External storage" }) { Text("Browse") }
                                if(externalStorage!=null) TextButton(onClick={ app.scope.launch { app.store.clearExternalStorageFolder();app.changed() } }) { Text("Disconnect") }
                            }
                        } }
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
            Text(if(file.projectConfidence == "Confirmed" && file.path.contains("/Documents/FileMate/")) "FileMate organised this file into its project folder. The move is recorded in Activity with Undo." else "This file has not been automatically moved by FileMate.")
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
    cleanupDetail?.let { entry -> AlertDialog(
        onDismissRequest = { cleanupDetail = null },
        title = { Text(entry.name,maxLines = 3,overflow = TextOverflow.Ellipsis) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.root,fontWeight = FontWeight.SemiBold)
            Text(cleanupReasons(entry).ifEmpty { listOf("Included in the phone browser") }.joinToString(" · "),fontSize = 13.sp)
            if(entry.flags and CleanupFlags.DUPLICATE != 0) Text("Exact duplicate means the full file content hash matched. You still choose which copy to keep.",fontSize = 13.sp,color = Muted)
            Text("${formatBytes(entry.size)} · ${if(entry.modified > 0) "modified ${time(entry.modified)}" else "date unavailable"}",fontSize = 12.sp,color = Muted)
            Text(entry.path,fontSize = 11.sp,color = Muted)
            Text("No file change has been proposed or applied.",fontSize = 12.sp,fontWeight = FontWeight.SemiBold)
        } },
        dismissButton = { TextButton(onClick = { cleanupDetail = null }) { Text("Done") } },
        confirmButton = { TextButton(onClick = {
            cleanupDetail = null
            if(projects.isEmpty()) { editingProjectId = null;showProjectEditor = true }
            else assignmentPaths = listOf(entry.path)
        }) { Text("Assign to project") } }
    ) }
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
            cleanupSelection = cleanupSelection - assignmentPaths.toSet()
            assignmentPaths = emptyList()
            app.changed()
            null
        } catch(e: Exception) { e.message ?: "FileMate couldn't assign the selected files." }
    }
    if(organiseEntries.isNotEmpty()) OrganiseOptionsDialog(organiseEntries.size,projects,defaultTidy = namingPreference == NamingPreference.TIDY_WHEN_REVIEWED,onDismiss = { organiseEntries = emptyList() }) { projectId,tidy ->
        val project = projects.firstOrNull { it.id == projectId }
        if(project == null) "Project no longer exists."
        else {
            organisePlans = withContext(Dispatchers.IO) { FileOrganiser(app.store).plans(organiseEntries,project,tidy) }
            organiseEntries = emptyList()
            null
        }
    }
    if(organisePlans.isNotEmpty()) OrganisePreviewDialog(organisePlans,onDismiss = { organisePlans = emptyList() }) { included ->
        val result = withContext(Dispatchers.IO) { FileOrganiser(app.store).apply(included) }
        organisePlans = emptyList()
        cleanupSelection = emptySet()
        app.changed()
        if(result.failed > 0 || result.skipped > 0) {
            error = "${result.applied} applied, ${result.skipped} skipped, ${result.failed} failed. " + result.messages.take(3).joinToString(" ")
        }
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
@Composable private fun CleanupRow(entry: CleanupEntry, selected: Boolean, toggle: () -> Unit, inspect: () -> Unit) {
    OutlinedCard(onClick = toggle,modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = selected,onCheckedChange = { toggle() })
            Icon(if(entry.flags and CleanupFlags.DUPLICATE != 0) Icons.Outlined.ContentCopy else Icons.Outlined.InsertDriveFile,null,tint = Blue)
            Column(Modifier.weight(1f)) {
                Text(entry.name,fontWeight = FontWeight.SemiBold,maxLines = 2,overflow = TextOverflow.Ellipsis)
                Text("${entry.root} · ${formatBytes(entry.size)}",fontSize = 12.sp,color = Muted)
                val reasons = cleanupReasons(entry)
                if(reasons.isNotEmpty()) Text(reasons.joinToString(" · "),fontSize = 11.sp,color = Muted,maxLines = 2,overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = inspect) { Icon(Icons.Outlined.Info,"Review ${entry.name}",tint = Muted) }
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
@Composable private fun OrganiseOptionsDialog(fileCount: Int, projects: List<Project>, defaultTidy: Boolean = false, onDismiss: () -> Unit, preview: suspend (Long,Boolean) -> String?) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var tidyNames by remember(defaultTidy) { mutableStateOf(defaultTidy) }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if(!working) onDismiss() },
        title = { Text("Organise $fileCount ${if(fileCount == 1) "file" else "files"}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose the project folder. The next screen shows every proposed path before anything changes.",fontSize = 13.sp,color = Muted)
            LazyColumn(Modifier.heightIn(max = 260.dp)) { items(projects,key = { it.id }) { project ->
                Row(Modifier.fillMaxWidth().clickable { selectedId = project.id;message = null }.padding(vertical = 7.dp),verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selectedId == project.id,onClick = { selectedId = project.id;message = null })
                    Text(project.name,modifier = Modifier.weight(1f),maxLines = 2,overflow = TextOverflow.Ellipsis)
                }
            } }
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Use tidy filenames",fontWeight = FontWeight.SemiBold);Text("Project_description_date.ext",fontSize = 11.sp,color = Muted) }
                Switch(checked = tidyNames,onCheckedChange = { tidyNames = it })
            }
            if(!tidyNames) Text("Current filenames will be kept.",fontSize = 12.sp,color = Muted)
            message?.let { Text(it,color = MaterialTheme.colorScheme.error,fontSize = 12.sp) }
        } },
        dismissButton = { TextButton(enabled = !working,onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = selectedId != null && !working,onClick = {
            val id = selectedId ?: return@Button
            scope.launch {
                working = true
                message = preview(id,tidyNames)
                working = false
                if(message == null) onDismiss()
            }
        }) { Text(if(working) "Preparing…" else "Preview") } }
    )
}
@Composable private fun OrganisePreviewDialog(plans: List<OrganisePlan>, onDismiss: () -> Unit, apply: suspend (List<OrganisePlan>) -> Unit) {
    val supported = remember(plans) { plans.filter { it.supported }.mapTo(mutableSetOf()) { it.sourcePath } }
    var included by remember(plans) { mutableStateOf<Set<String>>(supported) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val personal = plans.any { it.sourceRoot == "Camera" || it.sourceRoot == "Pictures" }
    AlertDialog(
        onDismissRequest = { if(!working) onDismiss() },
        title = { Text("Review file changes") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("${included.size} of ${plans.size} included. Existing files are never overwritten.",fontSize = 13.sp,color = Muted)
            if(personal) Text("This selection includes personal images. FileMate selected none of them automatically.",fontSize = 12.sp,color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.heightIn(max = 390.dp)) {
                items(plans,key = { it.sourcePath }) { plan ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),verticalAlignment = Alignment.Top) {
                        Checkbox(checked = plan.sourcePath in included,enabled = plan.supported,onCheckedChange = { checked ->
                            included = if(checked) included + plan.sourcePath else included - plan.sourcePath
                        })
                        Column(Modifier.weight(1f)) {
                            Text(plan.sourceName,fontWeight = FontWeight.SemiBold,maxLines = 2,overflow = TextOverflow.Ellipsis)
                            if(plan.supported) {
                                Text("→ ${plan.targetName}",fontSize = 12.sp,color = Blue,maxLines = 2,overflow = TextOverflow.Ellipsis)
                                Text(plan.targetPath.substringBeforeLast('/'),fontSize = 10.sp,color = Muted,maxLines = 2,overflow = TextOverflow.Ellipsis)
                            } else Text(plan.note,fontSize = 11.sp,color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text("Applied moves and renames appear in Activity with Undo.",fontSize = 12.sp,color = Muted)
        } },
        dismissButton = { TextButton(enabled = !working,onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(enabled = included.isNotEmpty() && !working,onClick = {
            scope.launch {
                working = true
                apply(plans.filter { it.sourcePath in included })
                working = false
                onDismiss()
            }
        }) { Text(if(working) "Applying…" else "Apply ${included.size}") } }
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
internal fun formatBytes(bytes: Long): String {
    if(bytes < 1024) return "$bytes B"
    val units = arrayOf("KB","MB","GB","TB")
    var value = bytes.toDouble()
    var unit = -1
    while(value >= 1024 && unit < units.lastIndex) { value /= 1024;unit++ }
    return if(value >= 10) "%.0f %s".format(value,units[unit]) else "%.1f %s".format(value,units[unit])
}
private fun cleanupReasons(entry: CleanupEntry): List<String> = buildList {
    if(entry.flags and CleanupFlags.LIKELY_AI != 0) add("Likely AI")
    if(entry.flags and CleanupFlags.UNSORTED_DOWNLOAD != 0) add("Unsorted download")
    if(entry.flags and CleanupFlags.LARGE != 0) add("Large")
    if(entry.flags and CleanupFlags.OLD != 0) add("Old")
    if(entry.flags and CleanupFlags.ARCHIVE != 0) add("Archive")
    if(entry.flags and CleanupFlags.DUPLICATE != 0) add("Exact duplicate")
}
