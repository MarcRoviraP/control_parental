package es.mrp.controlparental.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import es.mrp.controlparental.R
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.utils.hasUsageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en segundo plano que monitorea el uso de aplicaciones
 * y sube los datos a Firestore periódicamente
 */
class AppUsageMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var dbUtils: DataBaseUtils
    private var childUuid: String? = null

    companion object {
        private const val TAG = "AppUsageMonitor"
        private const val PREFS_NAME = "preferences"
        private const val UUID_KEY = "uuid"
        private const val LAST_RESET_DATE_KEY = "last_reset_date"
        private const val UPDATE_INTERVAL = 30000L // 30 segundos
        private const val INSTALLED_APPS_UPDATE_INTERVAL = 300000L // 5 minutos para apps instaladas
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "app_usage_monitor_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Servicio de monitoreo iniciado - onCreate()")

        // IMPORTANTE: Iniciar en foreground INMEDIATAMENTE si es Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "📱 Android O+ detectado - Iniciando en foreground...")
            try {
                startAsForegroundService()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error iniciando foreground service", e)
            }
        }

        dbUtils = DataBaseUtils(this)

        // Obtener el UUID del hijo desde SharedPreferences
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        childUuid = sharedPref.getString(UUID_KEY, null)

        if (childUuid != null) {
            Log.d(TAG, "✅ UUID encontrado: $childUuid - Iniciando monitoreo")

            // Verificar si cambió el día y reiniciar contadores si es necesario
            checkAndResetDailyUsageIfNeeded()

            // Enviar apps instaladas inmediatamente
            uploadInstalledApps()

            // Iniciar monitoreo periódico de apps instaladas
            startInstalledAppsMonitoring()

            startMonitoring()
        } else {
            Log.w(TAG, "⚠️ No se encontró UUID del hijo, no se puede monitorear")
        }
    }

    private fun startAsForegroundService() {
        try {
            Log.d(TAG, "📝 Creando canal de notificación...")
            createNotificationChannel()

            Log.d(TAG, "🔔 Creando notificación...")
            val notification = createNotification()

            Log.d(TAG, "🎯 Llamando a startForeground()...")
            startForeground(NOTIFICATION_ID, notification)

            Log.d(TAG, "✅ Servicio iniciado en modo foreground exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en startAsForegroundService", e)
            throw e
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Apps",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de monitoreo de uso de aplicaciones"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Parental")
            .setContentText("Monitoreando uso de aplicaciones")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    // Verificar cambio de día en cada ciclo
                    checkAndResetDailyUsageIfNeeded()

                    collectAndUploadUsageData()
                    delay(UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en el monitoreo", e)
                    delay(UPDATE_INTERVAL)
                }
            }
        }
    }

    /**
     * Inicia el monitoreo periódico de apps instaladas
     */
    private fun startInstalledAppsMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    delay(INSTALLED_APPS_UPDATE_INTERVAL)
                    uploadInstalledApps()
                } catch (e: Exception) {
                    Log.e(TAG, "Error actualizando apps instaladas", e)
                }
            }
        }
    }

    /**
     * Obtiene y sube la lista de apps instaladas a Firestore
     */
    private fun uploadInstalledApps() {
        if (childUuid == null) return

        try {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Filtrar solo apps que no sean del sistema o que hayan sido actualizadas
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    !isSystem || isUpdatedSystem
                }
                .associate { appInfo ->
                    val packageName = appInfo.packageName
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    packageName to appName
                }
                .toMutableMap()

            // Filtrar la propia app de control parental
            installedApps.remove(applicationContext.packageName)

            Log.d(TAG, "📦 Apps instaladas detectadas: ${installedApps.size}")
            dbUtils.uploadInstalledApps(childUuid!!, installedApps)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo apps instaladas", e)
        }
    }

    private fun collectAndUploadUsageData() {
        if (childUuid == null) return

        // Verificar si tenemos permiso de uso de apps
        if (!hasUsageAccess(this)) {
            Log.w(TAG, "No hay permiso de uso de apps")
            return
        }

        // Obtener las estadísticas de uso desde las 00:00 del día actual
        val usageStatsList = getUsageStats()

        if (usageStatsList.isNotEmpty()) {
            val usageData = hashMapOf<String, Any>()
            val pm = packageManager

            // IMPORTANTE: Usar el timestamp actual para saber cuándo se hizo esta captura
            val captureTimestamp = System.currentTimeMillis()

            // Convertir las estadísticas de uso a un formato serializable
            // Agrupar por packageName y seleccionar la entrada con mayor totalTimeInForeground por paquete
            val uniqueStats = usageStatsList
                .filter { it.totalTimeInForeground > 0 }
                .groupBy { it.packageName }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.totalTimeInForeground } }
                .sortedByDescending { it.totalTimeInForeground }
                .take(20) // Solo las 20 apps más usadas únicas por paquete

            uniqueStats.forEachIndexed { index, stat ->
                try {
                    // Filtrado: omitir paquetes no relevantes (sistema, launcher, ajustes, etc.)
                    if (isExcludedPackage(pm, stat.packageName)) {
                        Log.d(TAG, "Omitiendo paquete: ${stat.packageName}")
                        return@forEachIndexed
                    }

                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()

                    // Evitar nombres vacíos o nulos
                    if (appName.isBlank()) {
                        Log.d(TAG, "App con nombre vacío omitida: ${stat.packageName}")
                        return@forEachIndexed
                    }

                    usageData["app_$index"] = hashMapOf(
                        "packageName" to stat.packageName,
                        "appName" to appName,
                        "timeInForeground" to stat.totalTimeInForeground,
                        "lastTimeUsed" to stat.lastTimeUsed,
                        "capturedAt" to captureTimestamp // Timestamp de esta captura
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "App no encontrada: ${stat.packageName}")
                } catch (e: Exception) {
                    Log.w(TAG, "Error procesando app ${stat.packageName}", e)
                }
            }

            // Subir a Firestore con el timestamp de captura
            if (usageData.isNotEmpty()) {
                usageData["lastCaptureTime"] = captureTimestamp // Para mostrar "última actualización"
                dbUtils.uploadAppUsage(childUuid!!, usageData)
                Log.d(TAG, "Datos de uso subidos: ${usageData.size} apps monitoreadas")
            }
        } else {
            Log.d(TAG, "No hay datos de uso para subir")
        }
    }

    /**
     * Decide si un paquete debe excluirse del reporte de uso.
     * Omite apps de sistema puras, launcher (home), y paquetes explícitamente listados.
     */
    private fun isExcludedPackage(pm: PackageManager, packageName: String?): Boolean {
        if (packageName == null) return true

        // No reportar la propia app
        if (packageName == applicationContext.packageName) return true

        try {
            val ai: ApplicationInfo = pm.getApplicationInfo(packageName, 0)

            // Lista blanca: apps de sistema que SÍ queremos monitorear (aunque tengan FLAG_SYSTEM)
            val whitelist = setOf(
                "com.google.android.apps.photos",  // Google Fotos
                "com.android.gallery3d",           // Galería AOSP
                "com.miui.gallery",                // Galería MIUI
                "com.coloros.gallery3d",           // Galería ColorOS (Oppo/Realme)
                "com.oppo.gallery3d",              // Galería Oppo
                "com.samsung.android.gallery3d",   // Galería Samsung
                "com.sec.android.gallery3d",       // Galería Samsung alternativa
                "com.android.camera",              // Cámara sistema
                "com.android.camera2",             // Cámara alternativa
                "com.google.android.GoogleCamera", // Google Camera
                "com.android.contacts",            // Contactos
                "com.android.mms",                 // Mensajes/SMS
                "com.google.android.apps.messaging", // Mensajes Google
                "com.android.phone",               // Teléfono
                "com.google.android.dialer",       // Teléfono Google
                "com.android.calculator2",         // Calculadora
                "com.google.android.calculator",   // Calculadora Google
                "com.android.calendar",            // Calendario
                "com.google.android.calendar",     // Calendario Google
                "com.android.email",               // Email
                "com.google.android.gm",           // Gmail
                "com.android.deskclock"            // Reloj/Alarmas
            )

            // Si está en la whitelist, SIEMPRE permitir (no excluir)
            if (whitelist.contains(packageName)) {
                Log.d(TAG, "✅ App en whitelist permitida: $packageName")
                return false
            }

            // Omitir apps de sistema (no actualizadas) - PERO ya revisamos whitelist antes
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) {
                Log.d(TAG, "❌ App de sistema pura omitida: $packageName")
                return true
            }

            // Omitir apps persistentes o con privilegios especiales
            val isPersistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            if (isPersistent) return true

            // Omitir el launcher/Home
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val homePkg = resolveInfo?.activityInfo?.packageName
            if (packageName == homePkg) return true

            // Lista negra de paquetes comunes que no aportan valor o son del sistema
            val blacklist = setOf(
                "com.android.settings",
                "com.android.systemui",
                "com.android.providers.settings",
                "com.google.android.googlequicksearchbox", // launcher on some devices
                "com.google.android.apps.nexuslauncher",
                "com.miui.home",
                "com.oppo.launcher",
                "com.realme.launcher",
                "com.coloros.launcher",
                "com.samsung.android.launcher",
                "com.sec.android.app.launcher",
                "com.htc.launcher",
                "com.microsoft.launcher"
            )

            if (blacklist.contains(packageName)) return true

            // Omitir paquetes cuyo label o packageName parezcan contener "launcher" o "systemui" o "settings" o "setupwizard"
            val lower = packageName.lowercase()
            if (lower.contains("launcher") || lower.contains("systemui") || lower.contains("settings") || lower.contains("setupwizard")) {
                return true
            }

            return false
        } catch (e: PackageManager.NameNotFoundException) {
            // Si no se encuentra la app en el PackageManager, omitirla para no subir basura
            Log.w(TAG, "PackageManager: paquete no encontrado: $packageName")
            return true
        }
    }

    /**
     * Obtiene las estadísticas de uso desde las 00:00 del día actual
     */
    private fun getUsageStats(): List<UsageStats> {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()

        // Calcular el inicio del día (00:00:00)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        Log.d(TAG, "📊 Obteniendo estadísticas desde las 00:00 del día actual")

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📨 onStartCommand llamado")
        Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")

        val startedFromBoot = intent?.getBooleanExtra("started_from_boot", false) ?: false
        val startedFromWorker = intent?.getBooleanExtra("started_from_worker", false) ?: false

        when {
            startedFromBoot -> Log.d(TAG, "🔄 ⭐ SERVICIO INICIADO DESDE BOOTRECEIVER ⭐")
            startedFromWorker -> Log.d(TAG, "🔄 ⭐ SERVICIO INICIADO DESDE WORKMANAGER ⭐")
            else -> Log.d(TAG, "▶️ Servicio iniciado manualmente desde la app")
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Si no se había iniciado en onCreate, intentar aquí
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startAsForegroundService()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en onStartCommand al iniciar foreground", e)
            }
        }

        return START_STICKY // El servicio se reinicia si es terminado por el sistema
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio de monitoreo detenido")
    }

    /**
     * Verifica si cambió el día y reinicia los contadores si es necesario
     */
    private fun checkAndResetDailyUsageIfNeeded() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastResetDate = sharedPref.getString(LAST_RESET_DATE_KEY, "")
        val currentDate = getCurrentDate()

        Log.d(TAG, "📅 Verificando cambio de día - Último reset: $lastResetDate, Fecha actual: $currentDate")

        if (lastResetDate != currentDate) {
            Log.d(TAG, "🔄 ¡Cambió el día! Reiniciando contadores...")

            childUuid?.let { uuid ->
                dbUtils.resetDailyUsage(uuid,
                    onSuccess = {
                        // Guardar la nueva fecha de reinicio
                        sharedPref.edit().putString(LAST_RESET_DATE_KEY, currentDate).apply()
                        Log.d(TAG, "✅ Contadores reiniciados para el nuevo día: $currentDate")
                    },
                    onError = { error ->
                        Log.e(TAG, "❌ Error reiniciando contadores: $error")
                    }
                )
            }
        } else {
            Log.d(TAG, "✅ Mismo día, no se requiere reinicio")
        }
    }

    /**
     * Obtiene la fecha actual en formato YYYY-MM-DD
     */
    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return String.format("%04d-%02d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}

