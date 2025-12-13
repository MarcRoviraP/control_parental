package es.mrp.controlparental.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import es.mrp.controlparental.services.AppUsageMonitorService
import es.mrp.controlparental.services.AppBlockerOverlayService
import es.mrp.controlparental.workers.StartupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

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

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            logD("📢 ACTION_BOOT_COMPLETED recibido | Intent válido")
        }
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("🔔 BOOTRECEIVER ACTIVADO!!! | Thread: ${Thread.currentThread().name}")
        logD("Intent recibido: ${intent?.action} | Timestamp: ${System.currentTimeMillis()}")
        logD("Context: ${context.javaClass.simpleName} | Package: ${context.packageName}")
        logD("Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} | SDK: ${android.os.Build.VERSION.SDK_INT}")
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Usar goAsync() para operaciones largas
        val pendingResult = goAsync()
        logD("goAsync() llamado | PendingResult obtenido: ${pendingResult.hashCode()}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent?.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        logD("📱 Dispositivo iniciado (ACTION_BOOT_COMPLETED) | Esperando 5 segundos para estabilizar sistema...")
                        delay(5000) // Aumentar el delay a 5 segundos

                        logD("Delay completado | Iniciando servicios...")
                        startServicesWithRetry(context)

                        // NUEVO: Programar WorkManager como respaldo
                        logD("Programando WorkManager como respaldo...")
                        scheduleWorkerBackup(context)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED,
                    "android.intent.action.QUICKBOOT_POWERON",
                    "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                        logD("📱 Dispositivo iniciado (${intent.action}) | Esperando 5 segundos...")
                        delay(5000)

                        logD("Delay completado | Iniciando servicios...")
                        startServicesWithRetry(context)
                        scheduleWorkerBackup(context)
                    }
                    else -> {
                        logW("⚠️ Intent action no reconocido: ${intent?.action}")
                        logW("Intentando iniciar servicios de todas formas como medida de seguridad...")
                        delay(5000)
                        startServicesWithRetry(context)
                        scheduleWorkerBackup(context)
                    }
                }
            } catch (e: Exception) {
                logE("❌ Exception crítica en onReceive | Tipo: ${e.javaClass.simpleName} | Mensaje: ${e.message}", e)
                logE("Stack trace completo: ${e.stackTraceToString()}")
            } finally {
                logD("🏁 Finalizando BootReceiver.onReceive() | Llamando a pendingResult.finish()")
                pendingResult.finish()
            }
        }
    }

    private suspend fun startServicesWithRetry(context: Context) {
        logD("═══════════════════════════════════════════")
        logD("Iniciando startServicesWithRetry() | Intentos configurados: 3")

        repeat(3) { attempt ->
            try {
                logD("───────────────────────────────────────")
                logD("🔄 Intento ${attempt + 1} de 3 para iniciar servicios | Timestamp: ${System.currentTimeMillis()}")

                // Verificar si hay UUID guardado
                val sharedPref = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                val uuid = sharedPref.getString("uuid", null)

                logD("SharedPreferences consultadas | Path: ${context.filesDir.absolutePath}")
                logD("UUID presente: ${uuid != null} | UUID: ${uuid?.take(8) ?: "NULL"}...")

                if (uuid != null) {
                    logD("✅ UUID encontrado válido: ${uuid.take(8)}... | Longitud: ${uuid.length} caracteres")
                    logD("Procediendo a iniciar AppUsageMonitorService...")

                    // 1. Iniciar servicio de monitoreo
                    try {
                        logD("Creando Intent para AppUsageMonitorService...")
                        val monitorIntent = Intent(context, AppUsageMonitorService::class.java)
                        monitorIntent.putExtra("started_from_boot", true)

                        logD("Intent creado | Extras: started_from_boot=true")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            logD("Android O+ (SDK ${Build.VERSION.SDK_INT}) | Usando startForegroundService")
                            context.startForegroundService(monitorIntent)
                        } else {
                            logD("Android pre-O (SDK ${Build.VERSION.SDK_INT}) | Usando startService")
                            context.startService(monitorIntent)
                        }
                        logD("✅ AppUsageMonitorService iniciado correctamente")
                    } catch (e: Exception) {
                        logE("❌ Error iniciando AppUsageMonitorService | Tipo: ${e.javaClass.simpleName}", e)
                        logE("Mensaje: ${e.message} | Causa: ${e.cause?.message}")
                    }
                } else {
                    logW("⚠️ No hay UUID guardado | AppUsageMonitorService NO será iniciado")
                    logW("El usuario debe configurar la app primero")
                }

                // 2. Iniciar servicio de bloqueo (siempre)
                try {
                    logD("Creando Intent para AppBlockerOverlayService (siempre se inicia)...")
                    val blockerIntent = Intent(context, AppBlockerOverlayService::class.java)
                    blockerIntent.putExtra("auto_start", true)

                    logD("Intent creado | Extras: auto_start=true")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        logD("Android O+ (SDK ${Build.VERSION.SDK_INT}) | Usando startForegroundService")
                        context.startForegroundService(blockerIntent)
                    } else {
                        logD("Android pre-O (SDK ${Build.VERSION.SDK_INT}) | Usando startService")
                        context.startService(blockerIntent)
                    }
                    logD("✅ AppBlockerOverlayService iniciado correctamente")
                } catch (e: Exception) {
                    logE("❌ Error iniciando AppBlockerOverlayService | Tipo: ${e.javaClass.simpleName}", e)
                    logE("Mensaje: ${e.message} | Causa: ${e.cause?.message}")
                }

                logD("✅ Servicios iniciados correctamente en intento ${attempt + 1}")
                logD("═══════════════════════════════════════════")
                return // Éxito, salir del bucle

            } catch (e: Exception) {
                logE("❌ Error en intento ${attempt + 1} | Tipo: ${e.javaClass.simpleName} | Mensaje: ${e.message}", e)
                if (attempt < 2) {
                    logW("Esperando 3 segundos antes del siguiente intento...")
                    delay(3000) // Esperar 3 segundos antes del siguiente intento
                } else {
                    logE("❌ FALLO CRÍTICO: Todos los intentos agotados (3/3)")
                }
            }
        }

        logE("❌ Falló después de 3 intentos | Los servicios NO pudieron iniciarse")
        logD("═══════════════════════════════════════════")
    }

    /**
     * Programa un Worker como respaldo para asegurar que los servicios se inicien
     * WorkManager es más confiable en dispositivos con optimizaciones de batería
     */
    private fun scheduleWorkerBackup(context: Context) {
        try {
            logD("📅 Programando WorkManager como respaldo...")
            logD("Delay configurado: 10 segundos")

            val workRequest = OneTimeWorkRequestBuilder<StartupWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            logD("✅ WorkManager programado exitosamente | WorkRequest ID: ${workRequest.id}")
        } catch (e: Exception) {
            logE("❌ Error programando WorkManager | Tipo: ${e.javaClass.simpleName}", e)
            logE("Mensaje: ${e.message}")
        }
    }
}