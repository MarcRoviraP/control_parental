package es.mrp.controlparental

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "📢 ACTION_BOOT_COMPLETED recibido")
        }
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔔 BOOTRECEIVER ACTIVADO!!!")
        Log.d(TAG, "Intent recibido: ${intent?.action}")
        Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Usar goAsync() para operaciones largas
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        Log.d(TAG, "📱 Dispositivo iniciado, esperando 5 segundos...")
                        delay(5000) // Aumentar el delay a 5 segundos

                        startServicesWithRetry(context)

                        // NUEVO: Programar WorkManager como respaldo
                        scheduleWorkerBackup(context)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED,
                    "android.intent.action.QUICKBOOT_POWERON",
                    "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                        Log.d(TAG, "📱 Dispositivo iniciado, esperando 5 segundos...")
                        delay(5000)

                        startServicesWithRetry(context)
                        scheduleWorkerBackup(context)
                    }
                    else -> {
                        Log.w(TAG, "⚠️ Intent action no manejado: ${intent?.action}")
                        // Aun así, intentar iniciar los servicios por si acaso
                        Log.d(TAG, "🔄 Intentando iniciar servicios de todas formas...")
                        delay(5000)
                        startServicesWithRetry(context)
                        scheduleWorkerBackup(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception en onReceive", e)
            } finally {
                Log.d(TAG, "🏁 Finalizando BootReceiver.onReceive()")
                pendingResult.finish()
            }
        }
    }

    private suspend fun startServicesWithRetry(context: Context) {
        repeat(3) { attempt ->
            try {
                Log.d(TAG, "🔄 Intento ${attempt + 1} de 3 para iniciar servicios...")

                // Verificar si hay UUID guardado
                val sharedPref = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                val uuid = sharedPref.getString("uuid", null)

                Log.d(TAG, "SharedPreferences UUID: ${uuid ?: "null"}")

                if (uuid != null) {
                    Log.d(TAG, "✅ UUID encontrado: $uuid")

                    // 1. Iniciar servicio de monitoreo
                    try {
                        val monitorIntent = Intent(context, AppUsageMonitorService::class.java)
                        monitorIntent.putExtra("started_from_boot", true)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(monitorIntent)
                        } else {
                            context.startService(monitorIntent)
                        }
                        Log.d(TAG, "✅ AppUsageMonitorService iniciado")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error iniciando AppUsageMonitorService", e)
                    }
                } else {
                    Log.w(TAG, "⚠️ No hay UUID guardado, no se inicia AppUsageMonitorService")
                }

                // 2. Iniciar servicio de bloqueo (siempre)
                try {
                    val blockerIntent = Intent(context, AppBlockerOverlayService::class.java)
                    blockerIntent.putExtra("auto_start", true)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(blockerIntent)
                    } else {
                        context.startService(blockerIntent)
                    }
                    Log.d(TAG, "✅ AppBlockerOverlayService iniciado")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error iniciando AppBlockerOverlayService", e)
                }

                Log.d(TAG, "✅ Servicios iniciados correctamente en intento ${attempt + 1}")
                return // Éxito, salir del bucle

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en intento ${attempt + 1}: ${e.message}", e)
                if (attempt < 2) {
                    delay(3000) // Esperar 3 segundos antes del siguiente intento
                }
            }
        }

        Log.e(TAG, "❌ Falló después de 3 intentos")
    }

    /**
     * Programa un Worker como respaldo para asegurar que los servicios se inicien
     * WorkManager es más confiable en dispositivos con optimizaciones de batería
     */
    private fun scheduleWorkerBackup(context: Context) {
        try {
            Log.d(TAG, "📅 Programando WorkManager como respaldo...")

            val workRequest = OneTimeWorkRequestBuilder<StartupWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "✅ WorkManager programado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error programando WorkManager", e)
        }
    }
}