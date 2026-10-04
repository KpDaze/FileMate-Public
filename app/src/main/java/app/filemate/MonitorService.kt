package app.filemate

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Access {
    fun usage(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName) == AppOpsManager.MODE_ALLOWED
    }
}

/** Sampling usage events is separate from storage observation: Downloads is never polled. */
class AiActivity(private val context: Context, private val selected: () -> List<HubApp>) {
    private var since = System.currentTimeMillis() - 1000
    private var foreground: String? = null
    private var last: AiContext? = null
    private val clock = SessionClock(MonitoringSettings.minutes((context.applicationContext as FileMateApp).store.state("monitor_timeout_minutes")) * 60_000L)
    init { clock.touch(SystemClock.elapsedRealtime()) }
    @Synchronized fun hubLaunch(packageName: String, label: String) {
        clock.touch(SystemClock.elapsedRealtime())
        last = AiContext(label,packageName,System.currentTimeMillis())
    }
    @Synchronized fun refresh(): AiContext? {
        val now = System.currentTimeMillis()
        if(Access.usage(context)) {
            val end = now
            val events = try { context.getSystemService(UsageStatsManager::class.java).queryEvents(since,end) }
                catch(_: SecurityException) { foreground = null;return last?.takeIf { now - it.lastUsedAt in 0..120_000 } }
            val e = UsageEvents.Event()
            while(events.hasNextEvent()) {
                events.getNextEvent(e)
                when(e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> foreground = e.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED,UsageEvents.Event.ACTIVITY_STOPPED -> if(foreground == e.packageName) foreground = null
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,UsageEvents.Event.KEYGUARD_SHOWN -> foreground = null
                }
                if(e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                    selected().firstOrNull { it.packageName == e.packageName }?.let {
                        val age = (now - e.timeStamp).coerceAtLeast(0)
                        clock.touch(SystemClock.elapsedRealtime() - age)
                        last = AiContext(it.label,it.packageName,e.timeStamp)
                    }
                }
            }
            since = end
            val power = context.getSystemService(PowerManager::class.java)
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if(power.isInteractive && !locked) selected().firstOrNull { it.packageName == foreground }?.let {
                clock.touch(SystemClock.elapsedRealtime());last = AiContext(it.label,it.packageName,now)
            }
        } else foreground = null
        return last?.takeIf { now - it.lastUsedAt in 0..120_000 }
    }
    @Synchronized fun expired() = clock.expired(SystemClock.elapsedRealtime())
}

class MonitorService : Service() {
    private val app get() = application as FileMateApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()
    private val observers = ConcurrentHashMap<String,FileObserver>()
    private val pending = ConcurrentHashMap<String,Job>()
    private lateinit var activity: AiActivity
    private var ticker: Job? = null
    @Volatile private var closing = false
    override fun onCreate() { super.onCreate();activity = AiActivity(this) { app.store.hub() } }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == STOP) { finishSession("Monitoring stopped", "Stopped from FileMate or its notification.");return START_NOT_STICKY }
        if(intent == null) { stopSelf();return START_NOT_STICKY }
        val request = intent.getStringExtra("request") ?: return START_NOT_STICKY
        val pkg = intent.getStringExtra("package") ?: return START_NOT_STICKY
        val label = intent.getStringExtra("label") ?: pkg
        try {
            val notification = notification()
            if(Build.VERSION.SDK_INT >= 34) startForeground(44,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(44,notification)
        } catch(e: Exception) { fail("Android could not start monitoring: ${e.message}");return START_NOT_STICKY }
        app.monitor.value = app.monitor.value.copy(starting = true,error = null,readyRequest = null)
        scope.launch {
            startLock.withLock {
                try {
                    check(Environment.isExternalStorageManager()) { "Allow file access in Setup before launching an AI." }
                    if(observers.isEmpty()) {
                        app.reconcile()
                        @Suppress("DEPRECATION")
                        val parent = Environment.getExternalStorageDirectory()
                        check(parent.isDirectory && parent.canRead()) { "Shared phone storage is unavailable. Try again after unlocking the phone." }
                        watchParent(parent)
                        app.roots().filter { it.isDirectory }.forEach { watchTree(it) }
                        check(observers.isNotEmpty()) { "Shared storage could not be watched." }
                        // Close the gap between initial indexing and watcher installation.
                        app.reconcile()
                        app.store.state("session_active","true")
                        app.store.history("Monitoring started", "Download events are being watched. High-confidence AI downloads may be organised automatically; uncertain files stay untouched.")
                    }
                    activity.hubLaunch(pkg,label)
                    if(ticker == null) ticker = scope.launch {
                        while(isActive) {
                            delay(15_000)
                            if(!Environment.isExternalStorageManager()) { fail("File access was removed. Restore it in Setup.");break }
                            activity.refresh()
                            if(activity.expired()) {
                                finishSession("Monitoring ended automatically", "No selected AI activity for ${MonitoringSettings.minutes(app.store.state("monitor_timeout_minutes"))} minutes. Reopening FileMate checks for missed files.");break
                            }
                            app.monitor.value = app.monitor.value.copy(usageAvailable = Access.usage(this@MonitorService))
                        }
                    }
                    if(!closing) app.monitor.value = MonitorState(running = true,readyRequest = request,watchedFolders = observers.size - 1,usageAvailable = Access.usage(this@MonitorService))
                    app.changed()
                } catch(e: Exception) { fail(e.message ?: "Monitoring could not start") }
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"AI download monitoring",NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this,2,Intent(this,MonitorService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle("FileMate is watching for AI downloads")
            .setContentText("Stops automatically after ${MonitoringSettings.minutes(app.store.state("monitor_timeout_minutes"))} minutes of AI inactivity")
            .setContentIntent(open).addAction(0,"Stop monitoring",stop)
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true).build()
    }
    private fun insideRoot(file: File): Boolean = app.roots().any {
        val p = file.canonicalPath; val r = it.canonicalPath;p == r || p.startsWith("$r/")
    }
    private fun watchParent(parent: File) {
        val observer = object : FileObserver(parent,CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if(path == null || closing) return
                val dir = File(parent,path)
                if(app.roots().any { it.absolutePath == dir.absolutePath }) scope.launch {
                    if(dir.isDirectory) { watchTree(dir);scanNewDirectory(dir) }
                }
            }
        }
        observers[parent.absolutePath] = observer;observer.startWatching()
    }
    private fun watchTree(root: File) {
        val queue = ArrayDeque<File>();queue.add(root)
        while(queue.isNotEmpty() && !closing) {
            val dir = queue.removeFirst()
            if(!insideRoot(dir) || dir.name.startsWith('.')) continue
            val key = dir.canonicalPath
            if(observers.containsKey(key)) continue
            val observer = object : FileObserver(dir,CREATE or CLOSE_WRITE or MOVED_TO or DELETE_SELF or MOVE_SELF) {
                override fun onEvent(event: Int, path: String?) {
                    if(closing) return
                    if(event and (DELETE_SELF or MOVE_SELF) != 0) {
                        observers.remove(key)?.stopWatching();return
                    }
                    if(path == null) return
                    val file = File(dir,path)
                    if(FileRules.temporary(file.name)) return
                    scope.launch {
                        try {
                            if(!insideRoot(file)) return@launch
                            if(file.isDirectory) { watchTree(file);scanNewDirectory(file) }
                            else schedule(file)
                        } catch(e: Exception) { app.store.history("A file event needs catch-up",e.message ?: "Reopen FileMate to retry.");app.changed() }
                    }
                }
            }
            if(observers.putIfAbsent(key,observer) == null) observer.startWatching()
            dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') }?.forEach { queue.add(it) }
                ?: throw IllegalStateException("Cannot read ${dir.name}; file access may have changed.")
        }
    }
    private fun scanNewDirectory(dir: File) {
        walkFiles(dir,{ app.store.history("Folder needs catch-up",it.name) }) { schedule(it) }
    }
    @Synchronized private fun schedule(file: File) {
        if(closing || FileRules.temporary(file.name)) return
        // FileMate's own organised destination is inside Documents and is watched for catch-up.
        // Never feed those files back through live AI classification/automatic organisation.
        @Suppress("DEPRECATION")
        val managedRoot = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"FileMate")
        val canonical = runCatching { file.canonicalPath }.getOrNull()
        val managedPath = runCatching { managedRoot.canonicalPath }.getOrNull()
        if(canonical != null && managedPath != null && (canonical == managedPath || canonical.startsWith("$managedPath/"))) return
        val path = file.absolutePath
        pending.remove(path)?.cancel()
        pending[path] = scope.launch {
            // Event-triggered stability checks target one file, never a recurring Downloads scan.
            var previous: FileStamp? = null
            var stable = 0
            repeat(30) {
                delay(1000)
                if(!file.isFile) return@launch
                val current = FileStamp(file.length(),file.lastModified())
                stable = if(current == previous && current.size > 0) stable + 1 else 0
                previous = current
                if(stable >= 2) {
                    val now = System.currentTimeMillis()
                    val finding = FileRules.classify(file.name,activity.refresh(),now)
                    val observed = app.store.observe(file,finding,"Live monitoring")
                    if(observed) {
                        val result = AutoSorter(app.store).trySort(file,finding)
                        when {
                            result == null -> Unit
                            result.applied > 0 -> app.store.history("Automatically organised","${file.name}. High-confidence source and project evidence agreed.")
                            result.failed > 0 || result.skipped > 0 -> app.store.history("Automatic organisation left for review",
                                result.messages.take(3).joinToString(" ").ifBlank { file.name })
                        }
                    }
                    app.changed()
                    return@launch
                }
            }
            // A still-changing download is deliberately left to its next event or catch-up.
        }.also { job -> job.invokeOnCompletion { pending.remove(path,job) } }
    }
    private fun fail(message: String) {
        app.monitor.value = MonitorState(error = message)
        finishSession("Monitoring could not continue",message,keepError = true)
    }
    private fun finishSession(title: String, detail: String, keepError: Boolean = false) {
        if(closing) return
        closing = true
        app.store.state("session_active","false");app.store.history(title,detail)
        app.monitor.value = MonitorState(error = if(keepError) detail else null);app.changed()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() {
        closing = true;observers.values.forEach { it.stopWatching() };observers.clear()
        scope.cancel()
        if(app.monitor.value.running || app.monitor.value.starting) {
            app.store.state("session_active","false")
            app.store.history("Monitoring ended", "Reopening FileMate checks for missed files.")
            app.monitor.value = MonitorState();app.changed()
        }
        super.onDestroy()
    }
    companion object { const val STOP = "app.filemate.STOP";private const val CHANNEL = "ai_monitoring" }
}
