package es.mrp.controlparental.activities

import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityChildBinding
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.adapters.AppListAdapter
import es.mrp.controlparental.dialogs.QRDialog
import es.mrp.controlparental.models.TimeLimit
import es.mrp.controlparental.utils.getInstalledApps
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildBinding
    private lateinit var dbUtils: DataBaseUtils
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private val uploadInterval = 60000L // Subir cada 1 minuto
    private var appListAdapter: AppListAdapter? = null
    private val blockedAppsList = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar DataBaseUtils y UsageStatsManager
        dbUtils = DataBaseUtils(this)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.floatingActionButton.setOnClickListener {
            try {
                Log.d("ChildActivity", "FloatingActionButton clicked")
                val dialog = QRDialog.newInstance(dbUtils)
                dialog.show(supportFragmentManager, "QRDialog")
            } catch (e: Exception) {
                Log.e("ChildActivity", e.toString())
            }
        }

        setupRecyclerView()
        loadHeaderData()

        // ⚠️ YA NO SUBIMOS DATOS AQUÍ - AppUsageMonitorService lo hace en background
        // startPeriodicUsageUpload()

        Log.d("ChildActivity", "✅ Activity iniciada - AppUsageMonitorService gestiona la subida de datos")
    }

    /**
     * Carga los datos del header: tiempo global, apps bloqueadas y límites configurados
     */
    private fun loadHeaderData() {
        val currentUser = dbUtils.auth.currentUser
        if (currentUser == null) {
            Log.w("ChildActivity", "No hay usuario autenticado")
            return
        }

        val childUuid = currentUser.uid

        // Cargar tiempo global del día
        loadGlobalTime()

        // Escuchar cambios en apps bloqueadas
        dbUtils.listenToBlockedAppsFromUsage(childUuid) { blockedApps ->
            binding.textBlockedCount.text = blockedApps.size.toString()
            Log.d("ChildActivity", "📦 Apps bloqueadas: ${blockedApps.size}")

            // Actualizar la lista de apps bloqueadas y notificar al adaptador
            blockedAppsList.clear()
            blockedAppsList.addAll(blockedApps)
            appListAdapter?.updateBlockedApps(blockedApps.toSet())
        }

        // Escuchar cambios en límites de tiempo
        dbUtils.listenToTimeLimitsFromUsage(childUuid) { timeLimits ->
            binding.textLimitsCount.text = timeLimits.size.toString()
            Log.d("ChildActivity", "⏱️ Límites configurados: ${timeLimits.size}")

            // Actualizar tiempo global con límite
            updateGlobalTimeDisplay(timeLimits)
        }
    }

    /**
     * Carga el tiempo global usado hoy
     */
    private fun loadGlobalTime() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats != null && usageStats.isNotEmpty()) {
            // Sumar el tiempo de todas las apps excepto la app de control parental
            val totalTimeInForeground = usageStats
                .filter { it.packageName != applicationContext.packageName }
                .sumOf { it.totalTimeInForeground }

            val minutesUsed = TimeUnit.MILLISECONDS.toMinutes(totalTimeInForeground)

            // Guardar para mostrar con el límite más tarde
            binding.textGlobalTime.tag = minutesUsed
            binding.textGlobalTime.text = "$minutesUsed min / Sin límite"

            Log.d("ChildActivity", "⏰ Tiempo global usado: $minutesUsed minutos")
        }
    }

    /**
     * Actualiza la visualización del tiempo global con el límite si existe
     */
    private fun updateGlobalTimeDisplay(timeLimits: List<TimeLimit>) {
        val minutesUsed = binding.textGlobalTime.tag as? Long ?: 0L

        // Buscar límite global (packageName vacío)
        val globalLimit = timeLimits.find { it.packageName.isEmpty() }

        if (globalLimit != null && globalLimit.enabled) {
            val limitText = if (globalLimit.dailyLimitMinutes > 0) {
                "$minutesUsed min / ${globalLimit.dailyLimitMinutes} min"
            } else {
                "$minutesUsed min / Sin límite"
            }
            binding.textGlobalTime.text = limitText

            // Cambiar color si se excedió el límite
            if (globalLimit.dailyLimitMinutes > 0 && minutesUsed >= globalLimit.dailyLimitMinutes) {
                binding.textGlobalTime.setTextColor(getColor(android.R.color.holo_red_dark))
            } else {
                binding.textGlobalTime.setTextColor(getColor(android.R.color.darker_gray))
            }
        } else {
            binding.textGlobalTime.text = "$minutesUsed min / Sin límite"
            binding.textGlobalTime.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    private fun setupRecyclerView() {
        // Obtener lista de apps instaladas
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Filtrar para excluir la propia app de control parental
        val filteredPackages = packages.filter { appInfo ->
            appInfo.packageName != applicationContext.packageName
        }

        val appList = getInstalledApps(this, filteredPackages, pm)

        // Configurar RecyclerView y guardar referencia al adaptador
        appListAdapter = AppListAdapter(appList, blockedAppsList) { app, isBlocked ->
            if (isBlocked) {
                blockApp(app.packageName)
            } else {
                unblockApp(app.packageName)
            }
        }

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = appListAdapter
    }

    /**
     * Inicia la subida periódica de datos de uso a Firebase
     */
    @Suppress("unused")
    private fun startPeriodicUsageUpload() {
        val uploadRunnable = object : Runnable {
            override fun run() {
                uploadUsageDataToFirebase()
                handler.postDelayed(this, uploadInterval)
            }
        }

        // Primera subida inmediata
        uploadUsageDataToFirebase()

        // Programar subidas periódicas
        handler.postDelayed(uploadRunnable, uploadInterval)
    }

    /**
     * Obtiene el uso de apps del día actual y lo sube a Firebase en appUsage
     */
    private fun uploadUsageDataToFirebase() {
        val currentUser = dbUtils.auth.currentUser
        if (currentUser == null) {
            Log.w("ChildActivity", "No hay usuario autenticado")
            return
        }

        val childUuid = currentUser.uid

        // Obtener estadísticas de uso desde las 00:00 del día actual
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats == null || usageStats.isEmpty()) {
            Log.d("ChildActivity", "No hay datos de uso disponibles")
            return
        }

        // Crear el mapa de datos de uso para subir a appUsage
        val usageData = hashMapOf<String, Any>()
        val captureTimestamp = System.currentTimeMillis()

        // Agrupar por packageName y obtener el de mayor uso
        val uniqueStats = usageStats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.totalTimeInForeground } }
            .sortedByDescending { it.totalTimeInForeground }
            .take(20) // Top 20 apps más usadas

        var appsUploaded = 0
        val pm = packageManager

        uniqueStats.forEachIndexed { index, stat ->
            val packageName = stat.packageName
            val timeInForeground = stat.totalTimeInForeground

            // ⚠️ EXCLUIR LA PROPIA APP DE CONTROL PARENTAL
            if (packageName == applicationContext.packageName) {
                Log.d("ChildActivity", "⏭️ Excluyendo app de Control Parental del conteo")
                return@forEachIndexed
            }

            // Filtrar: solo apps instaladas por el usuario
            if (isUserInstalledApp(packageName) && !isExcludedPackage(pm, packageName)) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()

                    if (appName.isNotBlank()) {
                        // Usar el formato app_X como en AppUsageMonitorService
                        usageData["app_$index"] = hashMapOf(
                            "packageName" to packageName,
                            "appName" to appName,
                            "timeInForeground" to timeInForeground,
                            "lastTimeUsed" to stat.lastTimeUsed,
                            "capturedAt" to captureTimestamp
                        )
                        appsUploaded++
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    Log.w("ChildActivity", "App no encontrada: $packageName")
                }
            }
        }

        // Subir todo junto a Firebase en la colección appUsage
        if (appsUploaded > 0) {
            usageData["lastCaptureTime"] = captureTimestamp
            dbUtils.uploadAppUsage(childUuid, usageData)
            Log.d("ChildActivity", "✅ Subidas $appsUploaded apps a Firebase (appUsage)")
        } else {
            Log.d("ChildActivity", "No hay apps con uso para subir")
        }
    }

    /**
     * Verifica si una app está instalada por el usuario (no es del sistema)
     */
    private fun isUserInstalledApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            // Una app es del usuario si:
            // 1. No tiene la flag SYSTEM
            // 2. O tiene la flag UPDATED_SYSTEM_APP (es una app del sistema actualizada por el usuario)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Decide si un paquete debe excluirse del reporte de uso
     * (misma lógica que AppUsageMonitorService)
     */
    private fun isExcludedPackage(pm: PackageManager, packageName: String?): Boolean {
        if (packageName == null) return true
        if (packageName == applicationContext.packageName) return true

        try {
            val ai: ApplicationInfo = pm.getApplicationInfo(packageName, 0)

            // Lista blanca de apps del sistema que SÍ queremos monitorear
            val whitelist = setOf(
                "com.google.android.apps.photos", "com.android.gallery3d", "com.miui.gallery",
                "com.coloros.gallery3d", "com.oppo.gallery3d", "com.samsung.android.gallery3d",
                "com.sec.android.gallery3d", "com.android.camera", "com.android.camera2",
                "com.google.android.GoogleCamera", "com.android.contacts", "com.android.mms",
                "com.google.android.apps.messaging", "com.android.phone", "com.google.android.dialer",
                "com.android.calculator2", "com.google.android.calculator", "com.android.calendar",
                "com.google.android.calendar", "com.android.email", "com.google.android.gm",
                "com.android.deskclock"
            )

            if (whitelist.contains(packageName)) return false

            // Omitir apps de sistema (no actualizadas)
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) return true

            // Omitir apps persistentes
            val isPersistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            if (isPersistent) return true

            // Omitir el launcher/Home
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val homePkg = resolveInfo?.activityInfo?.packageName
            if (packageName == homePkg) return true

            // Lista negra de paquetes del sistema
            val blacklist = setOf(
                "com.android.settings", "com.android.systemui", "com.android.providers.settings",
                "com.google.android.googlequicksearchbox", "com.google.android.apps.nexuslauncher",
                "com.miui.home", "com.oppo.launcher", "com.realme.launcher", "com.coloros.launcher",
                "com.samsung.android.launcher", "com.sec.android.app.launcher", "com.htc.launcher",
                "com.microsoft.launcher"
            )

            if (blacklist.contains(packageName)) return true

            // Omitir paquetes con nombres sospechosos
            val lower = packageName.lowercase()
            if (lower.contains("launcher") || lower.contains("systemui") ||
                lower.contains("settings") || lower.contains("setupwizard")) {
                return true
            }

            return false
        } catch (_: PackageManager.NameNotFoundException) {
            return true
        }
    }

    /**
     * Obtiene la fecha actual en formato YYYY-MM-DD
     */
    @Suppress("unused")
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Calendar.getInstance().time)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun blockApp(packageName: String) {
        // Implementar lógica de bloqueo
        // Por ejemplo, usando DevicePolicyManager
    }

    @Suppress("UNUSED_PARAMETER")
    private fun unblockApp(packageName: String) {
        // Implementar lógica de desbloqueo
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener las subidas periódicas cuando se destruya la activity
        handler.removeCallbacksAndMessages(null)
    }
}

