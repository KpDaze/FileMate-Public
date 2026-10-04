package app.filemate

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProviderScreen(app: FileMateApp, folder: GrantedStorageFolder, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val scope=rememberCoroutineScope()
    val tree=remember(folder.uri) { Uri.parse(folder.uri) }
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<ProviderEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteEntry by remember { mutableStateOf<ProviderEntry?>(null) }
    val entries by produceState(emptyList<ProviderEntry>(),tree,query,refresh) {
        val result=withContext(Dispatchers.IO) { runCatching { ProviderBrowser(app).search(tree,query) } }
        result.onSuccess { loadError=null;value=it }.onFailure { loadError=it.message ?: "This storage folder is unavailable." }
    }
    Column(modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick=onBack) { Icon(Icons.Outlined.ArrowBack,null);Spacer(Modifier.width(6.dp));Text("Setup") }
        Text(folder.name,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text("Android-granted storage folder. FileMate has no cloud API key.")
        OutlinedTextField(query,{query=it},label={Text("Search this folder")},singleLine=true,modifier=Modifier.fillMaxWidth())
        loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick={refresh++}) { Text("Refresh") }
            TextButton(onClick={folderName="";newFolder=true}) { Icon(Icons.Outlined.CreateNewFolder,null);Spacer(Modifier.width(5.dp));Text("New folder") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(entries,key={it.uri}) { item ->
                ListItem(headlineContent={Text(item.name)},supportingContent={Text(if(item.directory) "Folder" else formatBytes(item.size))},modifier=Modifier.fillMaxWidth(),trailingContent={ TextButton(onClick={selectedEntry=item;renameText=item.name}) { Text("Manage") } })
                HorizontalDivider()
            }
        }
    }
    selectedEntry?.let { item ->
        AlertDialog(onDismissRequest={selectedEntry=null},title={Text("Manage ${item.name}")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(renameText,{renameText=it},label={Text("Name")},singleLine=true)
                Text("Rename is applied only after you press Rename.",fontSize=12.sp)
            }},
            confirmButton={Button(enabled=renameText.isNotBlank() && renameText!=item.name,onClick={
                val uri=Uri.parse(item.uri);val name=renameText;scope.launch { runCatching { withContext(Dispatchers.IO) { ProviderBrowser(app).rename(uri,name) } }
                    .onSuccess { app.store.history("External item renamed","${item.name} → $name");selectedEntry=null;refresh++;app.changed() }
                    .onFailure { error=it.message ?: "Item could not be renamed." } }
            }) {Text("Rename")}},
            dismissButton={Row { TextButton(onClick={selectedEntry=null}) {Text("Cancel")};TextButton(onClick={deleteEntry=item;selectedEntry=null}) {Text("Delete…")} }})
    }
    deleteEntry?.let { item ->
        AlertDialog(onDismissRequest={deleteEntry=null},title={Text("Delete from ${folder.name}?")},
            text={Text("This sends a delete request to the storage provider for ${item.name}. The provider controls its own Trash/recovery behaviour.")},
            confirmButton={Button(onClick={
                val uri=Uri.parse(item.uri);scope.launch { runCatching { withContext(Dispatchers.IO) { ProviderBrowser(app).delete(uri) } }
                    .onSuccess { app.store.history("External item delete requested",item.name);deleteEntry=null;refresh++;app.changed() }
                    .onFailure { error=it.message ?: "The storage provider could not delete this item." } }
            }) {Text("Delete")}},dismissButton={TextButton(onClick={deleteEntry=null}) {Text("Cancel")}})
    }
    if(newFolder) AlertDialog(onDismissRequest={newFolder=false},title={Text("Create folder")},
        text={OutlinedTextField(folderName,{folderName=it},label={Text("Folder name")},singleLine=true)},
        confirmButton={Button(enabled=folderName.isNotBlank(),onClick={
            val name=folderName;scope.launch {
                runCatching { withContext(Dispatchers.IO) { ProviderBrowser(app).createFolder(tree,name) } }
                    .onSuccess { newFolder=false;refresh++;app.store.history("External folder created",name);app.changed() }
                    .onFailure { error=it.message ?: "Folder could not be created." }
            }
        }) {Text("Create")}},dismissButton={TextButton(onClick={newFolder=false}) {Text("Cancel")}})
    error?.let { AlertDialog(onDismissRequest={error=null},title={Text("Storage folder")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}}) }
}
