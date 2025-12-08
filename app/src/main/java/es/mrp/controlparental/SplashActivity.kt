package es.mrp.controlparental

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private var overlayPermissionRequested = false
    private var usageAccessRequested = false
    private var deviceAdminRequested = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionRequested = true
        checkPermissionsFlow()
    }

    private val usageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        usageAccessRequested = true
        checkPermissionsFlow()
    }

    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar timestamp global
        getGlobalTimestamp()

        // Iniciar flujo de permisos
        checkPermissionsFlow()
    }

    override fun onResume() {
        super.onResume()
        // Solo verificar si ya se han solicitado permisos
        if (overlayPermissionRequested || usageAccessRequested || deviceAdminRequested) {
            checkPermissionsFlow()
        }
    }

    private fun checkPermissionsFlow() {
        when {
            // 1. Verificar Device Admin primero
            !isDeviceAdminActive() && !deviceAdminRequested -> {
                showDeviceAdminDialog()
            }

            // 2. Verificar permiso de overlay
            !Settings.canDrawOverlays(this) && !overlayPermissionRequested -> {
                showOverlayPermissionDialog()
            }

            // 3. Verificar permiso de uso de apps
            !hasUsageAccess(this) && !usageAccessRequested -> {
                showUsageAccessDialog()
            }

            // 4. Todos los permisos concedidos
            Settings.canDrawOverlays(this) && hasUsageAccess(this) -> {
                proceedToMainActivity()
            }

            // 5. Si faltan permisos después de solicitarlos, esperar a onResume
            else -> {
                // No hacer nada, esperar a que el usuario conceda permisos
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

    private fun proceedToMainActivity() {
        // Iniciar servicio de bloqueo solo si tenemos el permiso
        if (Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, AppBlockerOverlayService::class.java))
            } catch (e: Exception) {
                // Manejar error si el servicio no puede iniciarse
                e.printStackTrace()
            }
        }

        // Navegar a MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showDeviceAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos de Administrador")
            .setMessage("Esta app necesita permisos de administrador del dispositivo para:\n\n" +
                    "• Prevenir la desinstalación no autorizada\n" +
                    "• Proteger la configuración de control parental\n" +
                    "• Garantizar la seguridad de los niños\n\n" +
                    "Por favor, activa los permisos en la siguiente pantalla.")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                deviceAdminRequested = true
                requestDeviceAdmin()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showOverlayPermissionDialog() {
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
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showUsageAccessDialog() {
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
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
