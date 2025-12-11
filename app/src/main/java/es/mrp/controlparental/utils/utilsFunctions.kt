package es.mrp.controlparental.utils

    import android.app.AppOpsManager
    import android.app.admin.DevicePolicyManager
    import android.app.usage.UsageStats
    import android.app.usage.UsageStatsManager
    import android.content.ComponentName
    import android.content.Context
    import android.content.Intent
    import android.content.pm.ApplicationInfo
    import android.content.pm.PackageManager
    import android.util.Log
import es.mrp.controlparental.models.AppPackageClass
import es.mrp.controlparental.receivers.ParentalControlAdminReceiver
    import java.time.ZoneId
    import java.time.ZonedDateTime

var UUID: String? = "uuid"
    val blockedApps = mutableSetOf<String>()

    fun getInstalledApps(
        context: Context,
        packages: List<ApplicationInfo>,
        pm: PackageManager
    ) : List<AppPackageClass> {

        val returnList = ArrayList<AppPackageClass>()
        val userApps = packages.filter { app ->
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }

        if (!hasUsageAccess(context)) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 * 24

        val usageStatsList: List<UsageStats> =
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        val usageMap = usageStatsList
            .filter { it.totalTimeInForeground > 0 }
            .associateBy({ it.packageName }, { it.totalTimeInForeground })

        for (app in userApps) {
            val appName = pm.getApplicationLabel(app).toString()
            val packageName = app.packageName
            val totalTime = usageMap[packageName] ?: 0L
            // if (totalTime == 0L) continue

            returnList.add(AppPackageClass(packageName, appName, totalTime, false))
        }

        return returnList.sortedByDescending { it.time }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%dh %02dm %02ds", hours, minutes, seconds)
    }

    fun enableDeviceAdmin(context: Context) {
        val componentName = ComponentName(context, ParentalControlAdminReceiver::class.java)
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Esta aplicación necesita permisos de administrador para proteger el control parental")
            }
            context.startActivity(intent)
        }
    }
 fun getGlobalTimestamp() : Long {
     val time = ZonedDateTime.now(ZoneId.of(ZoneId.getAvailableZoneIds().first())).toInstant().toEpochMilli()
     Log.d("GlobalTimestamp", "Timestamp: $time")
     return time
}