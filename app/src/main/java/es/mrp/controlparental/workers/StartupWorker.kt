package es.mrp.controlparental.workers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.mrp.controlparental.services.AppUsageMonitorService
import es.mrp.controlparental.services.AppBlockerOverlayService

/**
 * Worker que se ejecuta al arrancar el dispositivo como respaldo al BootReceiver
 * WorkManager es más confiable que los BroadcastReceivers en dispositivos modernos
 */
class StartupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "StartupWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🚀 StartupWorker ejecutándose...")

        try {
            val sharedPref = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val uuid = sharedPref.getString("uuid", null)

            if (uuid != null) {
                Log.d(TAG, "✅ UUID encontrado: $uuid - Iniciando AppUsageMonitorService")
                startMonitoringService()
            } else {
                Log.w(TAG, "⚠️ No hay UUID guardado")
            }

            // Iniciar servicio de bloqueo
            startBlockerService()

            Log.d(TAG, "✅ Servicios iniciados exitosamente desde Worker")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando servicios desde Worker", e)
            return Result.retry()
        }
    }

    private fun startMonitoringService() {
        try {
            val intent = Intent(applicationContext, AppUsageMonitorService::class.java)
            intent.putExtra("started_from_worker", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.d(TAG, "✅ AppUsageMonitorService iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando AppUsageMonitorService", e)
        }
    }

    private fun startBlockerService() {
        try {
            val intent = Intent(applicationContext, AppBlockerOverlayService::class.java)
            intent.putExtra("auto_start", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.d(TAG, "✅ AppBlockerOverlayService iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando AppBlockerOverlayService", e)
        }
    }
}
