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
    import android.os.Handler
    import android.os.Looper
    import android.provider.Settings
    import android.util.Log
    import androidx.activity.ComponentActivity

    @SuppressLint("CustomSplashScreen")
    class SplashActivity : ComponentActivity() {

        private val handler = Handler(Looper.getMainLooper())

        @SuppressLint("QueryPermissionsNeeded")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Verificar y configurar permisos
            checkAndSetupPermissions()
        }
        private fun checkAndSetupPermissions() {
            enableDeviceAdmin(this)

            // Verificar permiso de overlay
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
                // Esperar y luego verificar de nuevo
                handler.postDelayed({
                    checkPermissionsAndProceed()
                }, 2000)
            } else {
                checkPermissionsAndProceed()
            }
        }

        private fun checkPermissionsAndProceed() {
            // Verificar permisos de uso
            if (!hasUsageAccess(this)) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                handler.postDelayed({
                    proceedToMainActivity()
                }, 1)
            } else {
                proceedToMainActivity()
            }
        }
        private fun proceedToMainActivity() {
            // Iniciar servicio de bloqueo
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, AppBlockerOverlayService::class.java))
            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()


        }

    }