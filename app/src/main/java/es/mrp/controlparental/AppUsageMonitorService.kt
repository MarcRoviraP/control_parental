package es.mrp.controlparental

import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
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
        private const val UPDATE_INTERVAL = 30000L // 30 segundos
    }

    override fun onCreate() {
        super.onCreate()
        val dbuitls = DataBaseUtils(this)
        Log.d(TAG, "Servicio de monitoreo iniciado")

        dbUtils = DataBaseUtils(this)

        // Obtener el UUID del hijo desde SharedPreferences
        childUuid = dbUtils.auth.currentUser?.uid

        if (childUuid != null) {
            startMonitoring()
        } else {
            Log.w(TAG, "No se encontró UUID del hijo, no se puede monitorear")
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    collectAndUploadUsageData()
                    delay(UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en el monitoreo", e)
                    delay(UPDATE_INTERVAL)
                }
            }
        }
    }

    private fun collectAndUploadUsageData() {
        if (childUuid == null) return

        // Verificar si tenemos permiso de uso de apps
        if (!hasUsageAccess(this)) {
            Log.w(TAG, "No hay permiso de uso de apps")
            return
        }

        // Obtener las estadísticas de uso de las últimas 24 horas
        val usageStatsList = getUsageStats()

        if (usageStatsList.isNotEmpty()) {
            val usageData = hashMapOf<String, Any>()
            val pm = packageManager

            // Convertir las estadísticas de uso a un formato serializable
            usageStatsList
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(20) // Solo las 20 apps más usadas
                .forEachIndexed { index, stat ->
                    try {
                        val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()

                        usageData["app_$index"] = hashMapOf(
                            "packageName" to stat.packageName,
                            "appName" to appName,
                            "timeInForeground" to stat.totalTimeInForeground,
                            "lastTimeUsed" to stat.lastTimeUsed
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "App no encontrada: ${stat.packageName}")
                    }
                }

            // Subir a Firestore
            if (usageData.isNotEmpty()) {
                dbUtils.uploadAppUsage(childUuid!!, usageData)
                Log.d(TAG, "Datos de uso subidos: ${usageData.size} apps monitoreadas")
            }
        } else {
            Log.d(TAG, "No hay datos de uso para subir")
        }
    }

    /**
     * Obtiene las estadísticas de uso de las últimas 24 horas
     */
    private fun getUsageStats(): List<UsageStats> {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // Últimas 24 horas

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // El servicio se reinicia si es terminado por el sistema
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio de monitoreo detenido")
    }
}
