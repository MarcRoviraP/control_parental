package es.mrp.controlparental

                            import android.content.BroadcastReceiver
                            import android.content.Context
                            import android.content.Intent
                            import android.util.Log
                            import kotlinx.coroutines.*

                            class BootReceiver : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent?) {
                                    Log.d("BootReceiver", "Recibido intent: ${intent?.action}")

                                    // Usar corrutina para no bloquear el receiver
                                    CoroutineScope(Dispatchers.IO).launch {
                                        when (intent?.action) {
                                            Intent.ACTION_BOOT_COMPLETED,
                                            Intent.ACTION_LOCKED_BOOT_COMPLETED, // Arranque antes del unlock
                                            "android.intent.action.QUICKBOOT_POWERON",
                                            "com.htc.intent.action.QUICKBOOT_POWERON",
                                            "android.intent.action.REBOOT",
                                            Intent.ACTION_MY_PACKAGE_REPLACED,
                                            Intent.ACTION_PACKAGE_REPLACED -> {

                                                Log.d("BootReceiver", "Iniciando servicio de bloqueo...")
                                                startServiceWithRetry(context)
                                            }
                                        }
                                    }
                                }

                                private suspend fun startServiceWithRetry(context: Context) {
                                    repeat(3) { attempt ->
                                        try {
                                            val serviceIntent = Intent(context, AppBlockerOverlayService::class.java)
                                            serviceIntent.putExtra("auto_start", true)

                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }

                                            Log.d("BootReceiver", "Servicio iniciado correctamente en intento ${attempt + 1}")
                                            return // Éxito, salir del bucle

                                        } catch (e: Exception) {
                                            Log.e("BootReceiver", "Error en intento ${attempt + 1}: ${e.message}")
                                            if (attempt < 2) {
                                                delay(2000) // Esperar 2 segundos antes del siguiente intento
                                            }
                                        }
                                    }
                                }
                            }