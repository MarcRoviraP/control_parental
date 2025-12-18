package es.mrp.controlparental.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.services.AppUsageMonitorService
import es.mrp.controlparental.services.AppBlockerOverlayService
import es.mrp.controlparental.utils.AutoStartHelper
import es.mrp.controlparental.receivers.ParentalControlAdminReceiver
import es.mrp.controlparental.utils.enableDeviceAdmin
import es.mrp.controlparental.utils.getGlobalTimestamp
import es.mrp.controlparental.utils.hasUsageAccess
import es.mrp.controlparental.workers.StartupWorker
import java.util.concurrent.TimeUnit

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private var overlayPermissionRequested = false
    private var usageAccessRequested = false
    private var deviceAdminRequested = false
    private var batteryOptimizationRequested = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        android.util.Log.d("SplashActivity", "📥 Resultado de Overlay Permission recibido")
        overlayPermissionRequested = true
        // Re-verificar todos los permisos
        checkAllPermissions()
    }

    private val usageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        android.util.Log.d("SplashActivity", "📥 Resultado de Usage Access recibido")
        usageAccessRequested = true
        // Re-verificar todos los permisos
        checkAllPermissions()
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        android.util.Log.d("SplashActivity", "📥 Resultado de Battery Optimization recibido")
        batteryOptimizationRequested = true
        // Re-verificar todos los permisos
        checkAllPermissions()
    }

    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("SplashActivity", "━━━━━━━━━━━ onCreate() ━━━━━━━━━━━")

        // Inicializar timestamp global
        getGlobalTimestamp()

        // Guardar UUID de Google Auth e iniciar servicio de monitoreo
        ensureUuidAndStartService()

        // Configurar WorkManager para reiniciar servicios periódicamente
        setupWorkManager()

        // NO verificar auto-inicio aquí, se hará en onResume después de los otros permisos
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("SplashActivity", "━━━━━━━━━━━ onResume() ━━━━━━━━━━━")

        // SIEMPRE verificar el estado de los permisos cuando se reanuda la actividad
        checkAllPermissions()
    }

    /**
     * Verifica TODOS los permisos necesarios y los solicita en orden
     * Se ejecuta SIEMPRE en onResume para asegurar que los permisos estén correctos
     */
    private fun checkAllPermissions() {
        android.util.Log.d("SplashActivity", "🔍 Verificando TODOS los permisos...")

        val hasDeviceAdmin = isDeviceAdminActive()
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasUsageAccess = hasUsageAccess(this)
        val hasBatteryOptimization = isIgnoringBatteryOptimizations()
        val isProblematicDevice = AutoStartHelper.isProblematicManufacturer()
        val shouldShowAutoStart = AutoStartHelper.shouldShowAutoStartDialog(this)

        android.util.Log.d("SplashActivity", """
            Estado de permisos:
            ✓ Device Admin: $hasDeviceAdmin
            ✓ Overlay: $hasOverlay
            ✓ Usage Access: $hasUsageAccess
            ✓ Battery Optimization: $hasBatteryOptimization
            ✓ Es dispositivo problemático: $isProblematicDevice
            ✓ Debe mostrar auto-inicio: $shouldShowAutoStart
        """.trimIndent())

        // Verificar permisos en orden de prioridad
        when {
            // 1. Device Admin es crítico - verificar primero
            !hasDeviceAdmin -> {
                android.util.Log.w("SplashActivity", "❌ Falta Device Admin - Solicitando...")
                if (!deviceAdminRequested) {
                    showDeviceAdminDialog()
                    deviceAdminRequested = true
                }
            }

            // 2. Overlay es necesario para bloquear apps
            !hasOverlay -> {
                android.util.Log.w("SplashActivity", "❌ Falta Overlay - Solicitando...")
                if (!overlayPermissionRequested) {
                    showOverlayPermissionDialog()
                    overlayPermissionRequested = true
                }
            }

            // 3. Usage Access para monitorear apps
            !hasUsageAccess -> {
                android.util.Log.w("SplashActivity", "❌ Falta Usage Access - Solicitando...")
                if (!usageAccessRequested) {
                    showUsageAccessDialog()
                    usageAccessRequested = true
                }
            }

            // 4. Exención de optimización de batería (CRÍTICO para funcionamiento 24/7)
            !hasBatteryOptimization -> {
                android.util.Log.w("SplashActivity", "❌ Falta exención de batería - Solicitando...")
                if (!batteryOptimizationRequested) {
                    showBatteryOptimizationDialog()
                    batteryOptimizationRequested = true
                }
            }

            // 5. Auto-inicio para dispositivos problemáticos (DESPUÉS de los otros permisos)
            isProblematicDevice && shouldShowAutoStart -> {
                android.util.Log.w("SplashActivity", "⚠️ Dispositivo problemático - Solicitando configuración de auto-inicio...")
                showForceAutoStartDialog()
            }

            // 6. TODOS los permisos concedidos - Continuar a MainActivity
            else -> {
                android.util.Log.d("SplashActivity", "✅ TODOS los permisos concedidos - Procediendo a MainActivity")
                proceedToMainActivity()
            }
        }
    }

    private fun isDeviceAdminActive(): Boolean {
        val componentName = android.content.ComponentName(this, ParentalControlAdminReceiver::class.java)
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        return devicePolicyManager.isAdminActive(componentName)
    }

    private fun requestDeviceAdmin() {
        enableDeviceAdmin(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestUsageAccess() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageAccessLauncher.launch(intent)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true // En versiones anteriores no existe este permiso
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚡ Optimización de batería")
            .setMessage(
                "Para que el control parental funcione correctamente incluso con la pantalla apagada durante horas, " +
                "necesitas desactivar la optimización de batería.\n\n" +
                "Esto permite que el servicio continúe monitoreando apps 24/7."
            )
            .setPositiveButton("Configurar") { _, _ ->
                requestBatteryOptimization()
            }
            .setNegativeButton("Más tarde") { dialog, _ ->
                dialog.dismiss()
                batteryOptimizationRequested = true
                checkAllPermissions()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(intent)
                android.util.Log.d("SplashActivity", "⚡ Solicitando exención de optimización de batería")
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "❌ Error solicitando exención de batería", e)
                batteryOptimizationRequested = true
                checkAllPermissions()
            }
        } else {
            batteryOptimizationRequested = true
            checkAllPermissions()
        }
    }

    private fun proceedToMainActivity() {
        // Iniciar servicio de bloqueo solo si tenemos el permiso
        if (Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, AppBlockerOverlayService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Navegar a MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Guarda el UUID de Google Auth e inicia el servicio de monitoreo
     * Se ejecuta automáticamente al iniciar la app
     */
    private fun ensureUuidAndStartService() {
        val dbUtils = DataBaseUtils(this)
        val sharedPref = getSharedPreferences("preferences", MODE_PRIVATE)

        // Obtener el UID del usuario autenticado de Google
        val currentUser = dbUtils.auth.currentUser

        if (currentUser != null) {
            val googleUid = currentUser.uid

            // Guardar el UID de Google en SharedPreferences
            sharedPref.edit().apply {
                putString("uuid", googleUid)
                apply()
            }

            android.util.Log.d("SplashActivity", "✅ UUID de Google Auth guardado: $googleUid")
            android.util.Log.d("SplashActivity", "Usuario: ${currentUser.displayName ?: currentUser.email}")

            // Iniciar el servicio de monitoreo en segundo plano
            startAppUsageMonitorService()
        } else {
            android.util.Log.w("SplashActivity", "⚠️ No hay usuario autenticado todavía")
        }
    }

    /**
     * Inicia el servicio de monitoreo de uso de apps en segundo plano
     */
    private fun startAppUsageMonitorService() {
        try {
            val serviceIntent = Intent(this, AppUsageMonitorService::class.java)
            startService(serviceIntent)
            android.util.Log.d("SplashActivity", "✅ Servicio de monitoreo iniciado automáticamente")
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "❌ Error iniciando servicio de monitoreo", e)
        }
    }

    /**
     * Configura WorkManager para asegurar que los servicios se mantengan activos
     */
    private fun setupWorkManager() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<StartupWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "service_monitor",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            android.util.Log.d("SplashActivity", "✅ WorkManager configurado para monitorear servicios")
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "❌ Error configurando WorkManager", e)
        }
    }

    /**
     * Muestra un diálogo que OBLIGA al usuario a configurar el auto-inicio
     */
    private fun showForceAutoStartDialog() {
        val manufacturer = AutoStartHelper.getManufacturerName()

        android.util.Log.d("SplashActivity", "📢 Mostrando diálogo OBLIGATORIO de auto-inicio para $manufacturer")

        AlertDialog.Builder(this)
            .setTitle("⚠️ Configuración OBLIGATORIA")
            .setMessage("""
                Tu dispositivo $manufacturer requiere configuración adicional para funcionar.
                
                📋 Debes realizar estos pasos:
                
                1️⃣ Toca "Ir a Configuración"
                2️⃣ Busca "Control Parental"
                3️⃣ Activa "Auto-inicio" o "Inicio automático"
                4️⃣ Desactiva "Optimización de batería"
                5️⃣ Vuelve a la app
                
                ⚠️ La app NO funcionará correctamente sin esta configuración.
                
                Esta configuración es necesaria para:
                • Proteger a tus hijos 24/7
                • Funcionar después de reiniciar
                • Monitorear el uso de apps
            """.trimIndent())
            .setPositiveButton("Ir a Configuración") { dialog, _ ->
                android.util.Log.d("SplashActivity", "👆 Usuario va a configurar auto-inicio")
                dialog.dismiss()

                val opened = AutoStartHelper.openAutoStartSettings(this)

                if (!opened) {
                    android.util.Log.w("SplashActivity", "⚠️ No se pudo abrir configuración específica, abriendo ajustes generales")
                    openGeneralSettings()
                } else {
                    android.util.Log.d("SplashActivity", "✅ Configuración específica abierta")
                }

                AutoStartHelper.markAutoStartDialogShown(this)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Abre los ajustes generales de la app como fallback
     */
    private fun openGeneralSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            android.util.Log.d("SplashActivity", "✅ Ajustes de la app abiertos")
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "❌ Error abriendo ajustes", e)
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                android.util.Log.e("SplashActivity", "❌ Error abriendo ajustes generales", e2)
            }
        }
    }

    private fun showDeviceAdminDialog() {
        android.util.Log.d("SplashActivity", "📢 Mostrando diálogo de Device Admin")

        AlertDialog.Builder(this)
            .setTitle("Permisos de Administrador")
            .setMessage("Esta app necesita permisos de administrador del dispositivo para:\n\n" +
                    "• Prevenir la desinstalación no autorizada\n" +
                    "• Proteger la configuración de control parental\n" +
                    "• Garantizar la seguridad de los niños\n\n" +
                    "Por favor, activa los permisos en la siguiente pantalla.")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                requestDeviceAdmin()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                android.util.Log.w("SplashActivity", "⚠️ Usuario canceló Device Admin - Cerrando app")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        android.util.Log.d("SplashActivity", "📢 Mostrando diálogo de Overlay Permission")

        AlertDialog.Builder(this)
            .setTitle("Permiso de Superposición")
            .setMessage("Esta app necesita permiso para mostrarse sobre otras apps para:\n\n" +
                    "• Bloquear aplicaciones restringidas\n" +
                    "• Mostrar advertencias cuando sea necesario\n" +
                    "• Proteger a los niños de contenido inapropiado\n\n" +
                    "Por favor, permite 'Mostrar sobre otras apps' en la siguiente pantalla.")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                requestOverlayPermission()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                android.util.Log.w("SplashActivity", "⚠️ Usuario canceló Overlay - Cerrando app")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showUsageAccessDialog() {
        android.util.Log.d("SplashActivity", "📢 Mostrando diálogo de Usage Access")

        AlertDialog.Builder(this)
            .setTitle("Acceso de Uso de Apps")
            .setMessage("Esta app necesita acceso al uso de aplicaciones para:\n\n" +
                    "• Monitorear qué apps están en uso\n" +
                    "• Detectar cuándo se abren apps restringidas\n" +
                    "• Generar informes de uso para los padres\n\n" +
                    "Por favor, busca 'Control Parental' en la lista y activa el permiso.")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                requestUsageAccess()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                android.util.Log.w("SplashActivity", "⚠️ Usuario canceló Usage Access - Cerrando app")
                finish()
            }
            .setCancelable(false)
            .show()
    }
}

