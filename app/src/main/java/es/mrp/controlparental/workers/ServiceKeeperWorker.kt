package es.mrp.controlparental.workers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import es.mrp.controlparental.services.AppBlockerOverlayService

/**
 * Worker que se ejecuta periódicamente para asegurar que BlockService esté activo
 * Útil cuando la pantalla está apagada durante horas y el sistema puede matar el servicio
 */
class ServiceKeeperWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceKeeperWorker"
        const val WORK_NAME = "ServiceKeeperWork"
    }

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "🔄 Verificando estado del servicio...")

            // Intentar reiniciar el servicio si no está activo
            val intent = Intent(applicationContext, AppBlockerOverlayService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
                Log.d(TAG, "✅ Servicio reiniciado como Foreground Service")
            } else {
                applicationContext.startService(intent)
                Log.d(TAG, "✅ Servicio reiniciado")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reiniciando servicio: ${e.message}", e)
            Result.retry()
        }
    }
}

