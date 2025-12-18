package es.mrp.controlparental.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import es.mrp.controlparental.R
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.utils.AppTrackingUtils
import es.mrp.controlparental.models.TimeLimit
import androidx.core.content.edit
import kotlin.math.abs

class AppBlockerOverlayService : Service() {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var activityManager: ActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 500L
    private lateinit var dbUtils: DataBaseUtils
    private var childUuid: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val blockedApps = mutableSetOf<String>()
    private var overlayView: LinearLayout? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0
    private var overlayShowTime: Long = 0
    private var autoRemoveRunnable: Runnable? = null

    // Variables para límites de tiempo
    private val timeLimits = mutableMapOf<String, TimeLimit>()
    private val dailyUsage = mutableMapOf<String, Long>()
    private var globalTimeLimit: TimeLimit? = null
    private var globalDailyUsage: Long = 0L
    private var currentForegroundApp: String? = null
    private var foregroundAppStartTime: Long = 0L
    private val updateUsageInterval = 60000L

    // Snapshot para detectar cambios y evitar escrituras innecesarias
    private val lastSyncedUsage = mutableMapOf<String, Long>()
    private var lastSyncedGlobalUsage: Long = 0L
    private var isInitialSnapshotLoaded = false


    companion object {
        private const val TAG = "AppBlockerService"
        private const val PREFS_NAME = "preferences"
        private const val UUID_KEY = "uuid"
        private const val LAST_RESET_DATE_KEY = "last_reset_date"
        private const val BLOCK_COOLDOWN = 500L
        private const val OVERLAY_MIN_DISPLAY_TIME = 3000L
    }

    override fun onCreate() {
        super.onCreate()

        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        dbUtils = DataBaseUtils(this)

        // Adquirir WakeLock para mantener el servicio activo incluso con pantalla apagada
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ControlParental::BlockServiceWakeLock"
        ).apply {
            acquire()
            Log.d(TAG, "🔋 WakeLock adquirido - Servicio funcionará con pantalla apagada")
        }

        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        childUuid = sharedPref.getString(UUID_KEY, null)

        if (childUuid != null) {
            Log.d(TAG, "✅ UUID encontrado: $childUuid - Iniciando monitoreo completo")

            // Verificar si cambió el día y reiniciar contadores
            checkAndResetLocalCountersIfNeeded()

            startListeningToBlockedApps()
            startListeningToTimeLimits()

            // Cargar snapshot inicial (Firebase primero, UsageStatsManager como fallback)
            loadTodayUsage()
        } else {
            Log.w(TAG, "⚠️ No se encontró UUID del hijo")
        }

        startForegroundServiceWithNotification()

        handler.postDelayed({
            Log.d(TAG, "🔍 Verificación inicial de app en primer plano")
            checkForegroundAppWithFallback()
        }, 500)

        handler.post(checkRunnable)
        handler.post(usageUpdateRunnable)
    }

    private fun checkAndResetLocalCountersIfNeeded() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastResetDate = sharedPref.getString(LAST_RESET_DATE_KEY, "")
        val currentDate = getCurrentDate()

        Log.d(TAG, "📅 Verificando cambio de día - Último reset: $lastResetDate, Fecha actual: $currentDate")

        if (lastResetDate != currentDate) {
            Log.d(TAG, "🔄 ¡Cambió el día! Reiniciando contadores...")

            // Limpiar todo el estado local
            dailyUsage.clear()
            globalDailyUsage = 0L
            lastSyncedUsage.clear()
            lastSyncedGlobalUsage = 0L
            isInitialSnapshotLoaded = false

            sharedPref.edit { putString(LAST_RESET_DATE_KEY, currentDate) }

            Log.d(TAG, "✅ Contadores reiniciados para el nuevo día: $currentDate")
        } else {
            Log.d(TAG, "✅ Mismo día, continuando con contadores actuales")
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return String.format("%04d-%02d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }


    private fun startListeningToBlockedApps() {
        childUuid?.let { uuid ->
            dbUtils.listenToBlockedAppsFromUsage(uuid) { blockedPackages ->
                blockedApps.clear()
                blockedApps.addAll(blockedPackages)
                Log.d(TAG, "📝 Apps bloqueadas actualizadas: ${blockedApps.size}")
                handler.post { checkForegroundAppWithFallback() }
            }
        }
    }

    private fun startListeningToTimeLimits() {
        childUuid?.let { uuid ->
            dbUtils.listenToTimeLimitsFromUsage(uuid) { limits ->
                timeLimits.clear()
                globalTimeLimit = null

                Log.d(TAG, "📊 Total de límites recibidos: ${limits.size}")

                for (limit in limits) {
                    if (limit.packageName.isEmpty() || limit.packageName == "GLOBAL_LIMIT") {
                        globalTimeLimit = limit
                        Log.d(TAG, "⏰ Límite global: ${limit.dailyLimitMinutes} min (enabled=${limit.enabled})")
                    } else {
                        timeLimits[limit.packageName] = limit
                        Log.d(TAG, "⏰ Límite ${limit.appName}: ${limit.dailyLimitMinutes} min")
                    }
                }
            }
        }
    }

    /**
     * PASO 1: Intentar cargar desde Firebase
     * Si no hay datos o es la primera vez, cargar desde UsageStatsManager
     */
    private fun loadTodayUsage() {
        childUuid?.let { uuid ->
            dbUtils.getChildAppUsage(uuid) { usageData ->
                if (usageData != null && !isInitialSnapshotLoaded) {
                    // Cargar desde Firebase
                    dailyUsage.clear()
                    var totalUsage = 0L

                    val excludedFields = setOf("childUID", "timestamp", "lastCaptureTime", "blockedApps", "timeLimits")

                    for ((key, value) in usageData) {
                        if (!excludedFields.contains(key) && value is Map<*, *>) {
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val appData = value as Map<String, Any>

                                val packageName = appData["packageName"] as? String ?: ""
                                val timeInForeground = (appData["timeInForeground"] as? Number)?.toLong() ?: 0L

                                if (AppTrackingUtils.shouldExcludeFromTimeTracking(applicationContext, packageName)) {
                                    continue
                                }

                                if (packageName.isNotEmpty() && timeInForeground > 0) {
                                    dailyUsage[packageName] = timeInForeground
                                    totalUsage += timeInForeground
                                    Log.d(TAG, "📊 Firebase: $packageName = ${timeInForeground / 60000} min")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando campo $key: ${e.message}")
                            }
                        }
                    }

                    globalDailyUsage = totalUsage
                    lastSyncedUsage.clear()
                    lastSyncedUsage.putAll(dailyUsage)
                    lastSyncedGlobalUsage = globalDailyUsage
                    isInitialSnapshotLoaded = true

                    Log.d(TAG, "✅ Snapshot inicial desde Firebase: ${dailyUsage.size} apps | ${globalDailyUsage / 60000} min")
                } else if (!isInitialSnapshotLoaded) {
                    Log.d(TAG, "⚠️ No hay datos en Firebase - Cargando snapshot desde UsageStatsManager...")
                    loadInitialSnapshotFromLocal()
                }
            }
        }
    }

    /**
     * PASO 2: Cargar snapshot inicial desde UsageStatsManager
     * Solo se ejecuta UNA VEZ al arrancar el servicio
     */
    private fun loadInitialSnapshotFromLocal() {
        try {
            Log.d(TAG, "📸 Capturando snapshot inicial desde UsageStatsManager...")

            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 20)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats != null && usageStats.isNotEmpty()) {
                dailyUsage.clear()
                var totalUsage = 0L

                val uniqueStats = usageStats
                    .filter { (it.totalTimeInForeground / 1000) > 0 }
                    .groupBy { it.packageName }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.totalTimeInForeground } }

                for (stat in uniqueStats) {
                    if (AppTrackingUtils.shouldExcludeFromTimeTracking(applicationContext, stat.packageName)) {
                        continue
                    }


                    dailyUsage[stat.packageName] = stat.totalTimeInForeground
                    totalUsage += stat.totalTimeInForeground
                    Log.d(TAG, "📊 UsageStats: ${stat.packageName} = ${stat.totalTimeInForeground / 60000} min")
                }

                globalDailyUsage = totalUsage
                lastSyncedUsage.clear()
                lastSyncedUsage.putAll(dailyUsage)
                lastSyncedGlobalUsage = globalDailyUsage

                Log.d(TAG, "✅ Snapshot inicial capturado: ${dailyUsage.size} apps | ${globalDailyUsage / 60000} min")

                // Subir snapshot inicial a Firebase
                uploadUsageToFirebaseIfChanged()
            } else {
                Log.d(TAG, "⚠️ No hay datos de uso disponibles")
            }

            isInitialSnapshotLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturando snapshot: ${e.message}", e)
            isInitialSnapshotLoaded = true
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                checkForegroundApp()
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            } finally {
                handler.postDelayed(this, checkInterval)
            }
        }
    }

    private val usageUpdateRunnable = object : Runnable {
        override fun run() {
            updateCurrentAppUsage()
            handler.postDelayed(this, updateUsageInterval)
        }
    }

    /**
     * PASO 3: Actualizar uso cada 60 segundos
     * Consulta UsageStatsManager local y sube a Firebase
     */
    private fun updateCurrentAppUsage() {
        try {
            Log.d(TAG, "🔄 Actualizando uso desde UsageStatsManager local...")

            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 20)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats != null && usageStats.isNotEmpty()) {
                // Limpiar y recalcular desde cero
                dailyUsage.clear()
                var totalUsage = 0L

                val uniqueStats = usageStats
                    .filter { it.totalTimeInForeground > 0 }
                    .groupBy { it.packageName }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.totalTimeInForeground } }

                for (stat in uniqueStats) {
                    if (AppTrackingUtils.shouldExcludeFromTimeTracking(applicationContext, stat.packageName)) {
                        continue
                    }

                    Log.d("conseguirAppsDiaria", "📊 UsageStats: ${stat.packageName} = ${stat.totalTimeInForeground / 60000} min")


                    dailyUsage[stat.packageName] = stat.totalTimeInForeground
                    totalUsage += stat.totalTimeInForeground
                }

                globalDailyUsage = totalUsage

                Log.d(TAG, "📊 Uso actualizado: ${dailyUsage.size} apps | ${globalDailyUsage / 60000} min")

                // Verificar límites
                currentForegroundApp?.let { app ->
                    val appUsageMinutes = (dailyUsage[app] ?: 0L) / 60000
                    timeLimits[app]?.let { limit ->
                        if (limit.enabled) {
                            Log.d(TAG, "📊 ${limit.appName}: ${appUsageMinutes}/${limit.dailyLimitMinutes}min")
                        }
                    }

                    if (isTimeLimitExceeded(app)) {
                        blockApp(app)
                    }
                }

                val globalUsageMinutes = globalDailyUsage / 60000
                globalTimeLimit?.let { limit ->
                    if (limit.enabled) {
                        Log.d(TAG, "🌐 Tiempo Global: ${globalUsageMinutes}/${limit.dailyLimitMinutes}min")
                    }
                }

                // Subir a Firebase solo si hay cambios significativos
                uploadUsageToFirebaseIfChanged()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error actualizando uso: ${e.message}", e)
        }
    }

    /**
     * PASO 4: Detectar cambios y subir a Firebase solo cuando sea necesario
     */
    private fun uploadUsageToFirebaseIfChanged() {
        var hasSignificantChanges = false
        val changeThresholdMillis = 60000L // 1 minuto

        // Verificar cambio global
        val globalDifference = abs(globalDailyUsage - lastSyncedGlobalUsage)
        if (globalDifference >= changeThresholdMillis) {
            hasSignificantChanges = true
            Log.d(TAG, "🔄 Cambio global: ${globalDifference / 60000} min")
        }

        // Verificar cambios por app
        for ((packageName, currentUsage) in dailyUsage) {
            val lastUsage = lastSyncedUsage[packageName] ?: 0L
            val difference = abs(currentUsage - lastUsage)

            if (difference >= changeThresholdMillis) {
                hasSignificantChanges = true
            }
        }

        // Detectar apps nuevas
        val newApps = dailyUsage.keys - lastSyncedUsage.keys
        if (newApps.isNotEmpty()) {
            hasSignificantChanges = true
            Log.d(TAG, "🔄 Nuevas apps: ${newApps.size}")
        }

        Log.d(TAG, "📤 Subiendo cambios a Firebase...")
        uploadCurrentUsageToFirebase()

        // Actualizar snapshot
        lastSyncedUsage.clear()
        lastSyncedUsage.putAll(dailyUsage)
        lastSyncedGlobalUsage = globalDailyUsage
    }

    private fun uploadCurrentUsageToFirebase() {
        childUuid?.let { uuid ->
            try {
                dbUtils.getChildAppUsage(uuid) { existingData ->
                    val usageData = hashMapOf<String, Any>()
                    val captureTimestamp = System.currentTimeMillis()

                    val existingPackageKeys = mutableMapOf<String, String>()

                    if (existingData != null) {
                        val excludedFields = setOf("childUID", "timestamp", "lastCaptureTime", "blockedApps", "timeLimits")

                        for ((key, value) in existingData) {
                            if (!excludedFields.contains(key) && value is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val appData = value as Map<String, Any>
                                val packageName = appData["packageName"] as? String
                                if (packageName != null) {
                                    existingPackageKeys[packageName] = key
                                }
                            }
                        }
                    }

                    var newIndex = 0
                    dailyUsage.forEach { (packageName, timeInForeground) ->
                        try {
                            val appInfo = packageManager.getApplicationInfo(packageName, 0)
                            val appName = packageManager.getApplicationLabel(appInfo).toString()

                            val key = existingPackageKeys[packageName] ?: run {
                                while (existingPackageKeys.containsValue("app_$newIndex")) {
                                    newIndex++
                                }
                                "app_${newIndex++}"
                            }

                            usageData[key] = hashMapOf(
                                "packageName" to packageName,
                                "appName" to appName,
                                "timeInForeground" to timeInForeground,
                                "lastTimeUsed" to System.currentTimeMillis(),
                                "capturedAt" to captureTimestamp
                            )
                        } catch (_: PackageManager.NameNotFoundException) {
                            // App desinstalada, omitir
                        }
                    }

                    if (usageData.isNotEmpty()) {
                        usageData["lastCaptureTime"] = captureTimestamp
                        dbUtils.uploadAppUsage(uuid, usageData)
                        Log.d(TAG, "✅ Subido a Firebase: ${usageData.size - 1} apps")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error subiendo a Firebase: ${e.message}")
            }
        }
    }

    private fun trackAppChange(newApp: String?) {
        if (currentForegroundApp != null && currentForegroundApp != newApp) {
            updateCurrentAppUsage()
        }
        currentForegroundApp = newApp
        foregroundAppStartTime = System.currentTimeMillis()
        Log.d(TAG, "📱 App: $newApp")
    }

    private fun isTimeLimitExceeded(packageName: String): Boolean {
        globalTimeLimit?.let { limit ->
            if (limit.enabled) {
                val limitMillis = limit.dailyLimitMinutes * 60000L
                if (globalDailyUsage >= limitMillis) {
                    Log.d(TAG, "⏰ Límite global excedido")
                    return true
                }
            }
        }

        timeLimits[packageName]?.let { limit ->
            if (limit.enabled) {
                val usage = dailyUsage[packageName] ?: 0L
                val limitMillis = limit.dailyLimitMinutes * 60000L
                if (usage >= limitMillis) {
                    Log.d(TAG, "⏰ Límite ${limit.appName} excedido")
                    return true
                }
            }
        }

        return false
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "OverlayServiceChannel"
        val channelName = "Control Parental"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Control Parental Activo")
            .setContentText("Monitorizando apps bloqueadas")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    private fun checkForegroundAppWithFallback() {
        var packageName = getForegroundAppFromUsageStats()

        if (packageName == null) {
            packageName = getForegroundAppFromActivityManager()
        }

        if (packageName != null) {
            if (blockedApps.contains(packageName)) {
                blockApp(packageName)
            } else {
                trackAppChange(packageName)
            }
        }
    }

    private fun getForegroundAppFromUsageStats(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 2000

        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastApp = event.packageName
            }
        }

        return lastApp
    }

    private fun getForegroundAppFromActivityManager(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val end = System.currentTimeMillis()
                val begin = end - 5000

                val events = usageStatsManager.queryEvents(begin, end)
                val event = UsageEvents.Event()
                var lastApp: String? = null

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastApp = event.packageName
                    }
                }

                lastApp
            } else {
                @Suppress("DEPRECATION")
                val tasks = activityManager.getRunningTasks(1)
                tasks?.firstOrNull()?.topActivity?.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            null
        }
    }

    private fun checkForegroundApp() {
        val end = System.currentTimeMillis()
        val begin = end - 2000

        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastApp = event.packageName
            }
        }

        lastApp?.let { packageName ->
            if (packageName != currentForegroundApp) {
                trackAppChange(packageName)
            }

            if (blockedApps.contains(packageName)) {
                blockApp(packageName)
            } else if (isTimeLimitExceeded(packageName)) {
                blockApp(packageName)
            }
        }
    }

    private fun blockApp(packageName: String) {
        val currentTime = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (currentTime - lastBlockTime) < BLOCK_COOLDOWN) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = currentTime

        Log.d(TAG, "🚫 Bloqueando: $packageName")

        showOverlay()
        goToHomeScreen()

        handler.postDelayed({
            handler.postDelayed({
                checkForegroundApp()
            }, 200)
        }, 200)
    }

    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Log.d(TAG, "🏠 Volviendo al home")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}")
        }
    }

    private fun showOverlay() {
        if (overlayView != null) {
            return
        }

        autoRemoveRunnable?.let { handler.removeCallbacks(it) }
        overlayShowTime = System.currentTimeMillis()

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 18, 18, 18))
            setPadding(60, 60, 60, 60)

            val cardView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(50, 60, 50, 60)

                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#DC2626"),
                        Color.parseColor("#991B1B")
                    )
                )
                gradientDrawable.cornerRadius = 48f
                gradientDrawable.setStroke(4, Color.parseColor("#EF4444"))
                background = gradientDrawable

                val iconView = TextView(context).apply {
                    text = "🔒"
                    textSize = 80f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }

                val titleView = TextView(context).apply {
                    text = "Aplicación Bloqueada"
                    setTextColor(Color.WHITE)
                    textSize = 32f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 20)
                    setShadowLayer(8f, 0f, 4f, Color.BLACK)
                }

                val dividerView = TextView(context).apply {
                    text = "━━━━━━━━━━━━━━"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 20)
                    alpha = 0.6f
                }

                val messageView = TextView(context).apply {
                    text = "Esta aplicación está restringida\npor Control Parental"
                    setTextColor(Color.parseColor("#FECACA"))
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(20, 0, 20, 20)
                    setLineSpacing(8f, 1f)
                }

                val warningView = TextView(context).apply {
                    text = "⚠️"
                    textSize = 32f
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 0)
                }

                addView(iconView)
                addView(titleView)
                addView(dividerView)
                addView(messageView)
                addView(warningView)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, 40, 40, 40)
                }
            }

            addView(cardView)

            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
                fillAfter = true
            }
            startAnimation(fadeIn)
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(overlayView, layoutParams)
            Log.d(TAG, "👁️ Overlay mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}")
            return
        }

        autoRemoveRunnable = Runnable {
            removeOverlay()
        }
        handler.postDelayed(autoRemoveRunnable!!, OVERLAY_MIN_DISPLAY_TIME)
    }

    private fun removeOverlay() {
        autoRemoveRunnable?.let { handler.removeCallbacks(it) }
        autoRemoveRunnable = null

        overlayView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
                overlayView = null
                Log.d(TAG, "👁️ Overlay removido")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(usageUpdateRunnable)
        removeOverlay()
        lastBlockedPackage = null

        // Liberar WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "🔋 WakeLock liberado")
            }
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
