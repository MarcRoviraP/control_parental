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

        private fun logD(message: String) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            Log.d(TAG, "[Línea $lineNumber] $message")
        }

        private fun logW(message: String) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            Log.w(TAG, "[Línea $lineNumber] $message")
        }

        private fun logE(message: String, throwable: Throwable? = null) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            if (throwable != null) {
                Log.e(TAG, "[Línea $lineNumber] $message", throwable)
            } else {
                Log.e(TAG, "[Línea $lineNumber] $message")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("🚀 Servicio de monitoreo iniciado - onCreate() | Thread: ${Thread.currentThread().name}")
        logD("Timestamp: ${System.currentTimeMillis()} | PID: ${android.os.Process.myPid()}")

        // IMPORTANTE: Iniciar en foreground INMEDIATAMENTE si es Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logD("📱 Android O+ detectado (SDK ${Build.VERSION.SDK_INT}) | Iniciando en foreground obligatorio...")
            try {
                startAsForegroundService()
            } catch (e: Exception) {
                logE("❌ Error CRÍTICO iniciando foreground service | Tipo: ${e.javaClass.simpleName}", e)
                logE("Mensaje: ${e.message} | Causa: ${e.cause?.message}")
            }
        } else {
            logD("Android pre-O (SDK ${Build.VERSION.SDK_INT}) | Foreground no obligatorio")
        }

        logD("Inicializando DataBaseUtils...")
        dbUtils = DataBaseUtils(this)
        logD("✅ DataBaseUtils inicializado")

        // Obtener el UUID del hijo desde SharedPreferences
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        childUuid = sharedPref.getString(UUID_KEY, null)

        logD("SharedPreferences consultadas | UUID presente: ${childUuid != null}")

        if (childUuid != null) {
            logD("✅ UUID encontrado válido: ${childUuid?.take(8)}... | Longitud: ${childUuid?.length} caracteres")
            logD("Iniciando secuencia de monitoreo completo...")

            // Verificar si cambió el día y reiniciar contadores si es necesario
            logD("1. Verificando cambio de día...")
            checkAndResetDailyUsageIfNeeded()

            // Enviar apps instaladas inmediatamente
            logD("2. Subiendo apps instaladas...")
            uploadInstalledApps()

            // Iniciar monitoreo periódico de apps instaladas
            logD("3. Iniciando monitoreo periódico de apps instaladas (cada ${INSTALLED_APPS_UPDATE_INTERVAL/1000}s)...")
            startInstalledAppsMonitoring()

            logD("4. Iniciando monitoreo principal de uso (cada ${UPDATE_INTERVAL/1000}s)...")
            startMonitoring()

            logD("✅ Servicio completamente inicializado y activo")
        } else {
            logW("⚠️ No se encontró UUID del hijo en SharedPreferences | No se puede monitorear")
            logW("El usuario debe vincular el dispositivo primero")
        }
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun startAsForegroundService() {
        try {
            logD("📝 Paso 1/3: Creando canal de notificación...")
            createNotificationChannel()

            logD("🔔 Paso 2/3: Creando notificación...")
            val notification = createNotification()
            logD("Notificación creada | ID: $NOTIFICATION_ID | Channel: $CHANNEL_ID")

            logD("🎯 Paso 3/3: Llamando a startForeground()...")
            startForeground(NOTIFICATION_ID, notification)

            logD("✅ Servicio iniciado en modo foreground exitosamente | Notificación visible")
        } catch (e: Exception) {
            logE("❌ Error en startAsForegroundService | Tipo: ${e.javaClass.simpleName}", e)
            logE("Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logD("Creando NotificationChannel para Android O+...")
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
            logD("✅ Canal creado | ID: $CHANNEL_ID | Importancia: LOW")
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
        logD("Lanzando corrutina de monitoreo en serviceScope...")
        serviceScope.launch {
            logD("Corrutina de monitoreo iniciada | Thread: ${Thread.currentThread().name}")
            var cycleCount = 0
            while (true) {
                try {
                    cycleCount++
                    logD("───────── Ciclo de monitoreo #$cycleCount ─────────")

                    // Verificar cambio de día en cada ciclo
                    checkAndResetDailyUsageIfNeeded()

                    // ⚠️ NOTA: La subida de datos de uso ahora la maneja BlockService
                    // que tiene tracking en tiempo real más preciso con dailyUsage
                    logD("✅ Ciclo #$cycleCount completado | BlockService gestiona la subida de datos")
                    logD("Próximo ciclo en ${UPDATE_INTERVAL/1000}s")

                    delay(UPDATE_INTERVAL)
                } catch (e: Exception) {
                    logE("❌ Error en ciclo de monitoreo #$cycleCount | Tipo: ${e.javaClass.simpleName}", e)
                    logE("Mensaje: ${e.message}")
                    delay(UPDATE_INTERVAL)
                }
            }
        }
    }

    /**
     * Inicia el monitoreo periódico de apps instaladas
     */
    private fun startInstalledAppsMonitoring() {
        logD("Lanzando corrutina de monitoreo de apps instaladas...")
        serviceScope.launch {
            logD("Corrutina iniciada | Intervalo: ${INSTALLED_APPS_UPDATE_INTERVAL/1000}s")
            var updateCount = 0
            while (true) {
                try {
                    delay(INSTALLED_APPS_UPDATE_INTERVAL)
                    updateCount++
                    logD("🔄 Actualización #$updateCount de apps instaladas...")
                    uploadInstalledApps()
                } catch (e: Exception) {
                    logE("❌ Error actualizando apps instaladas #$updateCount", e)
                }
            }
        }
    }

    /**
     * Obtiene y sube la lista de apps instaladas a Firestore
     */
    private fun uploadInstalledApps() {
        if (childUuid == null) {
            logW("UUID nulo | No se pueden subir apps instaladas")
            return
        }

        try {
            logD("Obteniendo lista de apps instaladas...")
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

            logD("📦 Apps instaladas detectadas: ${installedApps.size} apps de usuario")
            logD("Subiendo a Firestore para UUID: ${childUuid?.take(8)}...")
            dbUtils.uploadInstalledApps(childUuid!!, installedApps)
            logD("✅ Apps instaladas subidas exitosamente")
        } catch (e: Exception) {
            logE("❌ Error obteniendo/subiendo apps instaladas | Tipo: ${e.javaClass.simpleName}", e)
            logE("Mensaje: ${e.message}")
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
        calendar.set(java.util.Calendar.MINUTE, 20)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        Log.d(TAG, "📊 Obteniendo estadísticas desde las 00:00 del día actual")

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        for (stat in usageStatsList) {
            Log.d(
                TAG,
                "Uso: ${stat.packageName} - Tiempo en foreground: ${stat.totalTimeInForeground} ms"
            )
        }
        return usageStatsList
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("📨 onStartCommand llamado | StartId: $startId | Flags: $flags")
        logD("Timestamp: ${System.currentTimeMillis()} | Thread: ${Thread.currentThread().name}")

        val startedFromBoot = intent?.getBooleanExtra("started_from_boot", false) ?: false
        val startedFromWorker = intent?.getBooleanExtra("started_from_worker", false) ?: false

        when {
            startedFromBoot -> logD("🔄 ⭐ SERVICIO INICIADO DESDE BOOTRECEIVER ⭐")
            startedFromWorker -> logD("🔄 ⭐ SERVICIO INICIADO DESDE WORKMANAGER ⭐")
            else -> logD("▶️ Servicio iniciado manualmente desde la app o sistema")
        }

        logD("Intent extras: started_from_boot=$startedFromBoot, started_from_worker=$startedFromWorker")

        // Si no se había iniciado en onCreate, intentar aquí
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                logD("Verificando si servicio está en foreground...")
                startAsForegroundService()
            } catch (e: Exception) {
                logE("❌ Error en onStartCommand al iniciar foreground", e)
            }
        }

        logD("Retornando START_STICKY (servicio se reinicia si es terminado)")
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        return START_STICKY // El servicio se reinicia si es terminado por el sistema
    }

    override fun onBind(intent: Intent?): IBinder? {
        logD("onBind() llamado | Intent: ${intent?.action}")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("🛑 Servicio de monitoreo detenido - onDestroy()")
        logD("Timestamp: ${System.currentTimeMillis()} | PID: ${android.os.Process.myPid()}")
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Verifica si cambió el día y reinicia los contadores si es necesario
     */
    private fun checkAndResetDailyUsageIfNeeded() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastResetDate = sharedPref.getString(LAST_RESET_DATE_KEY, "")
        val currentDate = getCurrentDate()

        logD("📅 Verificando cambio de día | Último reset: '$lastResetDate' | Fecha actual: '$currentDate'")

        if (lastResetDate != currentDate) {
            logD("🔄 ¡CAMBIÓ EL DÍA! Reiniciando contadores de uso diario...")
            logD("De: $lastResetDate → A: $currentDate")

            childUuid?.let { uuid ->
                dbUtils.resetDailyUsage(uuid,
                    onSuccess = {
                        // Guardar la nueva fecha de reinicio
                        sharedPref.edit().putString(LAST_RESET_DATE_KEY, currentDate).apply()
                        logD("✅ Contadores reiniciados exitosamente para el nuevo día: $currentDate")
                        logD("Fecha guardada en SharedPreferences")
                    },
                    onError = { error ->
                        logE("❌ Error reiniciando contadores: $error")
                    }
                )
            }
        } else {
            logD("✅ Mismo día ($currentDate) | No se requiere reinicio de contadores")
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
