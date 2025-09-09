package es.mrp.controlparental

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import android.os.Build

class MainActivity : ComponentActivity() {
    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        } else {
            startService(Intent(this, AppBlockerOverlayService::class.java))
        }


        val pm: PackageManager = packageManager
        val packages: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        imprimirTiempodeUsoAppsInstaladas(packages,pm)



        return

    }

    private fun imprimirTiempodeUsoAppsInstaladas(
        packages: List<ApplicationInfo>,
        pm: PackageManager
    ) {
        // Solo apps de usuario
        val userApps = packages.filter { app ->
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }

        // Comprobar permisos de uso
        if (!hasUsageAccess(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        // Obtener UsageStats del último día
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24 // últimas 24h

        val usageStatsList: List<UsageStats> =
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        // Crear mapa packageName -> tiempo de uso
        val usageMap = usageStatsList
            .filter { it.totalTimeInForeground > 0 }
            .associateBy({ it.packageName }, { it.totalTimeInForeground })

        // Cruzar apps instaladas con tiempo de uso
        for (app in userApps) {
            val appName = pm.getApplicationLabel(app).toString()
            val packageName = app.packageName
            val totalTime = usageMap[packageName] ?: 0L
            if (totalTime == 0L) continue
            Log.d(
                "AppUsage",
                "App: $appName - Package: $packageName - Tiempo en primer plano: ${
                    formatTime(
                        totalTime
                    )
                }"
            )
        }
    }

    // Verifica si el usuario dio permiso de acceso a estadísticas
    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Convierte ms a "h m s"
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%dh %02dm %02ds", hours, minutes, seconds)
    }
}
