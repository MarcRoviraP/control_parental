package es.mrp.controlparental

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class AppBlockerOverlayService : Service() {

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var activityManager: ActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 500L
    private lateinit var dbUtils: DataBaseUtils
    private var childUuid: String? = null

    private val blockedApps = mutableSetOf<String>()
    private var overlayView: LinearLayout? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0
    private var overlayShowTime: Long = 0
    private var autoRemoveRunnable: Runnable? = null

    companion object {
        private const val TAG = "AppBlockerService"
        private const val PREFS_NAME = "preferences"
        private const val UUID_KEY = "uuid"
        private const val BLOCK_COOLDOWN = 500L
        private const val OVERLAY_MIN_DISPLAY_TIME = 3000L
    }

    override fun onCreate() {
        super.onCreate()

        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        dbUtils = DataBaseUtils(this)

        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        childUuid = sharedPref.getString(UUID_KEY, null)

        if (childUuid != null) {
            Log.d(TAG, "✅ UUID encontrado: $childUuid - Iniciando escucha de apps bloqueadas")
            startListeningToBlockedApps()
        } else {
            Log.w(TAG, "⚠️ No se encontró UUID del hijo, no se pueden bloquear apps")
        }

        startForegroundServiceWithNotification()

        handler.postDelayed({
            Log.d(TAG, "🔍 Verificación inicial de app en primer plano tras inicio del servicio")
            checkForegroundAppWithFallback()
        }, 500)

        handler.post(checkRunnable)
    }

    private fun startListeningToBlockedApps() {
        childUuid?.let { uuid: String ->
            dbUtils.listenToBlockedApps(uuid, object : (List<String>) -> Unit {
                override fun invoke(blockedPackages: List<String>) {
                    blockedApps.clear()
                    blockedApps.addAll(blockedPackages)
                    Log.d(TAG, "📝 Apps bloqueadas actualizadas: ${blockedApps.size} apps")
                    Log.d(TAG, "Apps: ${blockedApps.joinToString(", ")}")

                    handler.post {
                        Log.d(TAG, "🔍 Verificando app actual tras actualización de lista")
                        checkForegroundAppWithFallback()
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

    private fun checkForegroundAppWithFallback() {
        var packageName = getForegroundAppFromUsageStats()

        if (packageName == null) {
            packageName = getForegroundAppFromActivityManager()
            if (packageName != null) {
                Log.d(TAG, "📱 App detectada usando ActivityManager: $packageName")
            }
        }

        if (packageName != null) {
            Log.d(TAG, "🔎 App actual: $packageName")
            if (blockedApps.contains(packageName)) {
                Log.d(TAG, "⚠️ App bloqueada detectada: $packageName")
                blockApp(packageName)
            }
        } else {
            Log.d(TAG, "⚠️ No se pudo detectar ninguna app en primer plano")
        }
    }

    private fun getForegroundAppFromUsageStats(): String? {
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

        return lastApp
    }

    private fun getForegroundAppFromActivityManager(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val end = System.currentTimeMillis()
                val begin = end - 10000

                val usageStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    begin,
                    end
                )

                usageStats?.maxByOrNull { it.lastTimeUsed }?.packageName
            } else {
                @Suppress("DEPRECATION")
                val tasks = activityManager.getRunningTasks(1)
                tasks?.firstOrNull()?.topActivity?.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo app desde ActivityManager: ${e.message}")
            null
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
            if (blockedApps.contains(packageName)) {
                blockApp(packageName)
            }
        }
    }

    private fun blockApp(packageName: String) {
        val currentTime = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (currentTime - lastBlockTime) < BLOCK_COOLDOWN) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = currentTime

        Log.d(TAG, "🚫 Bloqueando: $packageName")

        showOverlay()
        goToHomeScreen()

        handler.postDelayed({
            forceCloseApp(packageName)

            handler.postDelayed({
                checkForegroundApp()
            }, 200)
        }, 200)
    }

    private fun forceCloseApp(packageName: String) {
        try {
            Log.d(TAG, "💀 Intentando cerrar app: $packageName")

            activityManager.killBackgroundProcesses(packageName)

            try {
                Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
                Log.d(TAG, "✅ Comando force-stop ejecutado para: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error ejecutando force-stop: ${e.message}")
            }

            Log.d(TAG, "💀 Procesos de cierre ejecutados para: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando app: ${e.message}")
        }
    }

    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Log.d(TAG, "🏠 Volviendo al home screen")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error volviendo al home: ${e.message}")
        }
    }

    private fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "👁️ Overlay ya visible")
            return
        }

        autoRemoveRunnable?.let { handler.removeCallbacks(it) }
        overlayShowTime = System.currentTimeMillis()

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 18, 18, 18))
            setPadding(60, 60, 60, 60)

            val cardView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(50, 60, 50, 60)

                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#DC2626"),
                        Color.parseColor("#991B1B")
                    )
                )
                gradientDrawable.cornerRadius = 48f
                gradientDrawable.setStroke(4, Color.parseColor("#EF4444"))
                background = gradientDrawable

                val iconView = TextView(context).apply {
                    text = "🔒"
                    textSize = 80f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }

                val titleView = TextView(context).apply {
                    text = "Aplicación Bloqueada"
                    setTextColor(Color.WHITE)
                    textSize = 32f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 20)
                    setShadowLayer(8f, 0f, 4f, Color.BLACK)
                }

                val dividerView = TextView(context).apply {
                    text = "━━━━━━━━━━━━━━"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 20)
                    alpha = 0.6f
                }

                val messageView = TextView(context).apply {
                    text = "Esta aplicación está restringida\npor Control Parental"
                    setTextColor(Color.parseColor("#FECACA"))
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(20, 0, 20, 20)
                    setLineSpacing(8f, 1f)
                }

                val warningView = TextView(context).apply {
                    text = "⚠️"
                    textSize = 32f
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 0)
                }

                addView(iconView)
                addView(titleView)
                addView(dividerView)
                addView(messageView)
                addView(warningView)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(40, 40, 40, 40)
                }
            }

            addView(cardView)

            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
                fillAfter = true
            }
            startAnimation(fadeIn)
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(overlayView, layoutParams)
            Log.d(TAG, "👁️ Overlay mostrado - se auto-eliminará en ${OVERLAY_MIN_DISPLAY_TIME}ms")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error mostrando overlay: ${e.message}")
            return
        }

        autoRemoveRunnable = Runnable {
            Log.d(TAG, "⏰ Tiempo cumplido (${OVERLAY_MIN_DISPLAY_TIME}ms) - removiendo overlay automáticamente")
            removeOverlay()
        }
        handler.postDelayed(autoRemoveRunnable!!, OVERLAY_MIN_DISPLAY_TIME)
    }

    private fun removeOverlay() {
        autoRemoveRunnable?.let { handler.removeCallbacks(it) }
        autoRemoveRunnable = null

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
        lastBlockedPackage = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

