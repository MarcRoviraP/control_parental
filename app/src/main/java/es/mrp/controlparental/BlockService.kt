package es.mrp.controlparental

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class AppBlockerOverlayService : Service() {

    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L // Reducido a 1 segundo para mejor respuesta
    private lateinit var dbUtils: DataBaseUtils
    private var childUuid: String? = null

    // Lista de apps bloqueadas (se actualiza desde Firestore)
    private val blockedApps = mutableSetOf<String>()

    private var overlayView: TextView? = null
    private var isBlockingActive = false

    companion object {
        private const val TAG = "AppBlockerService"
        private const val PREFS_NAME = "preferences"
        private const val UUID_KEY = "uuid"
    }

    override fun onCreate() {
        super.onCreate()

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        dbUtils = DataBaseUtils(this)

        // Obtener UUID del hijo desde SharedPreferences
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        childUuid = sharedPref.getString(UUID_KEY, null)

        if (childUuid != null) {
            Log.d(TAG, "✅ UUID encontrado: $childUuid - Iniciando escucha de apps bloqueadas")
            startListeningToBlockedApps()
        } else {
            Log.w(TAG, "⚠️ No se encontró UUID del hijo, no se pueden bloquear apps")
        }

        startForegroundServiceWithNotification()

        // Ejecutar tarea periódica
        handler.post(checkRunnable)
    }

    /**
     * Inicia la escucha en tiempo real de las apps bloqueadas desde Firestore
     */
    private fun startListeningToBlockedApps() {
        childUuid?.let { uuid: String ->
            dbUtils.listenToBlockedApps(uuid, object : (List<String>) -> Unit {
                override fun invoke(blockedPackages: List<String>) {
                    // Actualizar la lista de apps bloqueadas
                    blockedApps.clear()
                    blockedApps.addAll(blockedPackages)
                    Log.d(TAG, "📝 Apps bloqueadas actualizadas: ${blockedApps.size} apps")
                    Log.d(TAG, "Apps: ${blockedApps.joinToString(", ")}")

                    // Si la app actual está bloqueada, bloquearla inmediatamente
                    if (isBlockingActive) {
                        // Verificar si la app bloqueada actual ya no está en la lista
                        checkForegroundApp()
                    }
                }
            })
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "OverlayServiceChannel"
        val channelName = "Control Parental"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Control Parental Activo")
            .setContentText("Monitorizando apps bloqueadas")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                checkForegroundApp()
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            } finally {
                handler.postDelayed(this, checkInterval)
            }
        }
    }

    private fun checkForegroundApp() {
        val end = System.currentTimeMillis()
        val begin = end - 2000

        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastApp = event.packageName
            }
        }

        lastApp?.let { packageName ->
            // Log.d(TAG, "Foreground: $packageName")
            if (blockedApps.contains(packageName)) {
                if (!isBlockingActive) {
                    blockApp(packageName)
                }
            } else {
                if (isBlockingActive) {
                    unblockCurrentApp()
                }
            }
        }
    }

    private fun blockApp(packageName: String) {
        Log.d(TAG, "🚫 Bloqueando: $packageName")
        handler.postDelayed({
            isBlockingActive = true

            // 1. Mostrar overlay INMEDIATAMENTE
            showOverlay()

            // 2. Forzar app al background
            forceAppToBackground()

        }, 100) // Reducido a 100ms para respuesta más rápida
    }

    private fun forceAppToBackground() {
        try {
            // Llevar el usuario al home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Log.d(TAG, "📱 App forzada al background")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando al background: ${e.message}")
        }
    }

    private fun unblockCurrentApp() {
        Log.d(TAG, "✅ Desactivando bloqueo")
        isBlockingActive = false
        removeOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return // ya mostrado

        overlayView = TextView(this).apply {
            text = "🚫 APP BLOQUEADA\n\nEsta aplicación está restringida\npor el control parental"
            setBackgroundColor(Color.argb(240, 0, 0, 0)) // Fondo casi opaco
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            // Capturar todos los toques para bloquear interacción
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(overlayView, layoutParams)
            Log.d(TAG, "👁️ Overlay mostrado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando overlay: ${e.message}")
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
                overlayView = null
                Log.d(TAG, "👁️ Overlay removido")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removiendo overlay: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        removeOverlay()
        isBlockingActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
