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

    override suspend fun doWork(): Result {
        logD("🚀 StartupWorker ejecutándose... | Thread: ${Thread.currentThread().name} | RunAttempt: $runAttemptCount")
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        try {
            val sharedPref = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val uuid = sharedPref.getString("uuid", null)

            logD("SharedPreferences consultadas | UUID presente: ${uuid != null} | UUID: ${uuid?.take(8)}...")

            if (uuid != null) {
                logD("✅ UUID encontrado válido: ${uuid.take(8)}... | Longitud: ${uuid.length}")
                logD("Iniciando AppUsageMonitorService desde Worker...")
                startMonitoringService()
            } else {
                logW("⚠️ No hay UUID guardado en SharedPreferences | Saltando AppUsageMonitorService")
            }

            // Iniciar servicio de bloqueo
            logD("Iniciando AppBlockerOverlayService (siempre se inicia)...")
            startBlockerService()

            logD("✅ Servicios iniciados exitosamente desde Worker")
            logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            return Result.success()

        } catch (e: Exception) {
            logE("❌ Error crítico iniciando servicios desde Worker | Tipo: ${e.javaClass.simpleName} | Mensaje: ${e.message}", e)
            logE("Stack trace: ${e.stackTraceToString()}")
            logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            return Result.retry()
        }
    }

    private fun startMonitoringService() {
        try {
            logD("Creando Intent para AppUsageMonitorService...")
            val intent = Intent(applicationContext, AppUsageMonitorService::class.java)
            intent.putExtra("started_from_worker", true)

            logD("Intent creado | Extras: started_from_worker=true")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                logD("Android O+ detectado (SDK ${Build.VERSION.SDK_INT}) | Usando startForegroundService")
                applicationContext.startForegroundService(intent)
            } else {
                logD("Android pre-O (SDK ${Build.VERSION.SDK_INT}) | Usando startService")
                applicationContext.startService(intent)
            }
            logD("✅ AppUsageMonitorService iniciado correctamente")
        } catch (e: Exception) {
            logE("❌ Error iniciando AppUsageMonitorService | Tipo: ${e.javaClass.simpleName}", e)
            logE("Detalles del error: ${e.message}")
        }
    }

    private fun startBlockerService() {
        try {
            logD("Creando Intent para AppBlockerOverlayService...")
            val intent = Intent(applicationContext, AppBlockerOverlayService::class.java)
            intent.putExtra("auto_start", true)

            logD("Intent creado | Extras: auto_start=true")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                logD("Android O+ detectado (SDK ${Build.VERSION.SDK_INT}) | Usando startForegroundService")
                applicationContext.startForegroundService(intent)
            } else {
                logD("Android pre-O (SDK ${Build.VERSION.SDK_INT}) | Usando startService")
                applicationContext.startService(intent)
            }
            logD("✅ AppBlockerOverlayService iniciado correctamente")
        } catch (e: Exception) {
            logE("❌ Error iniciando AppBlockerOverlayService | Tipo: ${e.javaClass.simpleName}", e)
            logE("Detalles del error: ${e.message}")
        }
    }
}
