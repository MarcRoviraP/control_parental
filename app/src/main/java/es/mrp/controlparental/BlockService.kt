package es.mrp.controlparental

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.Context
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
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent

class AppBlockerOverlayService : Service() {

    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 2000L // 2 segundos

    // Lista de apps bloqueadas
    private val blockedApps = listOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.facebook.katana"
    )

    override fun onCreate() {
        super.onCreate()

        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        startForegroundServiceWithNotification()

        // Ejecutar tarea periódica
        handler.post(checkRunnable)
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
                Log.e("OverlayService", "Error: ${e.message}")
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
            Log.d("OverlayService", "Foreground: $packageName")
            if (blockedApps.contains(packageName)) {
                blockApp(packageName)
            }else{
                removeOverlay()
            }
        }
    }

    private fun blockApp(packageName: String) {
        Log.d("OverlayServiceBlock", "Bloqueando: $packageName")
        val componentName = ComponentName(this, ParentalControlAdminReceiver::class.java)
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (devicePolicyManager.isAdminActive(componentName)) {
            // Ocultar la app del launcher
            devicePolicyManager.setApplicationHidden(componentName, packageName, true)
        }
    }

    private fun unblockApp(packageName: String) {
        val componentName = ComponentName(this, ParentalControlAdminReceiver::class.java)
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.setApplicationHidden(componentName, packageName, false)
        }
    }
    private var overlayView: TextView? = null

    private fun showOverlay() {
        if (overlayView != null) return // ya mostrado

        overlayView = TextView(this).apply {
            text = "💀 APP BLOQUEADA"
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(overlayView, layoutParams)
    }

    private fun removeOverlay() {
        overlayView?.let {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        removeOverlay()
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
