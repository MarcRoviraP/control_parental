package es.mrp.controlparental

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * Helper para gestionar la configuración de auto-inicio en diferentes fabricantes
 * Especialmente importante para Oppo, Xiaomi, Huawei, Vivo, etc.
 */
object AutoStartHelper {

    private const val TAG = "AutoStartHelper"

    private val POWER_MANAGER_INTENTS = arrayOf(
        // Xiaomi
        Intent().setClassName("com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"),

        // Oppo
        Intent().setClassName("com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        Intent().setClassName("com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"),

        // Vivo
        Intent().setClassName("com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        Intent().setClassName("com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),

        // Huawei
        Intent().setClassName("com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        Intent().setClassName("com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"),

        // Samsung
        Intent().setClassName("com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"),

        // OnePlus
        Intent().setClassName("com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),

        // Letv
        Intent().setClassName("com.letv.android.letvsafe",
            "com.letv.android.letvsafe.AutobootManageActivity"),

        // Asus
        Intent().setClassName("com.asus.mobilemanager",
            "com.asus.mobilemanager.MainActivity")
    )

    /**
     * Detecta si el dispositivo es de un fabricante problemático
     */
    fun isProblematicManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.d(TAG, "🔍 Detectando fabricante: '$manufacturer'")

        val problematicBrands = listOf(
            "xiaomi", "oppo", "vivo", "huawei", "honor",
            "oneplus", "realme", "asus", "letv", "coolpad", "iqoo"
        )

        val isProblematic = problematicBrands.any { brand ->
            val matches = manufacturer.contains(brand)
            if (matches) {
                Log.d(TAG, "✅ Match encontrado: '$manufacturer' contiene '$brand'")
            }
            matches
        }

        Log.d(TAG, "Resultado: ${if (isProblematic) "ES PROBLEMÁTICO ⚠️" else "No problemático ✓"}")
        return isProblematic
    }

    /**
     * Obtiene el nombre del fabricante
     */
    fun getManufacturerName(): String {
        return Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    /**
     * Intenta abrir la configuración de auto-inicio del fabricante
     */
    fun openAutoStartSettings(context: Context): Boolean {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔓 Intentando abrir configuración de auto-inicio...")
        Log.d(TAG, "Fabricante: ${Build.MANUFACTURER}")

        for (intent in POWER_MANAGER_INTENTS) {
            try {
                Log.d(TAG, "🔍 Probando: ${intent.component?.className}")

                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    Log.d(TAG, "✅ Configuración encontrada: ${intent.component}")
                    Log.d(TAG, "📱 Abriendo configuración...")

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)

                    Log.d(TAG, "✅ Configuración de auto-inicio abierta exitosamente")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    return true
                } else {
                    Log.d(TAG, "❌ No disponible: ${intent.component?.className}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Error con ${intent.component?.className}: ${e.message}")
            }
        }

        Log.w(TAG, "❌ No se encontró configuración de auto-inicio específica del fabricante")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        return false
    }

    /**
     * Muestra un diálogo educativo sobre cómo habilitar auto-inicio
     */
    fun showAutoStartDialog(context: Context) {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📢 showAutoStartDialog() llamado")
        Log.d(TAG, "Context: ${context.javaClass.simpleName}")

        val manufacturer = getManufacturerName()
        Log.d(TAG, "Fabricante: $manufacturer")

        val message = getInstructionsForManufacturer(manufacturer)
        Log.d(TAG, "Mensaje preparado (${message.length} caracteres)")

        try {
            Log.d(TAG, "🔨 Creando AlertDialog...")

            val dialog = AlertDialog.Builder(context)
                .setTitle("⚠️ Configuración Importante")
                .setMessage(message)
                .setPositiveButton("Ir a Configuración") { dialog, _ ->
                    Log.d(TAG, "👆 Usuario presionó 'Ir a Configuración'")
                    dialog.dismiss()
                    val opened = openAutoStartSettings(context)
                    if (!opened) {
                        showManualInstructions(context, manufacturer)
                    }
                }
                .setNegativeButton("Más Tarde") { dialog, _ ->
                    Log.d(TAG, "👆 Usuario presionó 'Más Tarde'")
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()

            Log.d(TAG, "📱 Mostrando diálogo...")
            dialog.show()
            Log.d(TAG, "✅ Diálogo mostrado exitosamente")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR mostrando diálogo", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
        }
    }

    /**
     * Obtiene instrucciones específicas para cada fabricante
     */
    private fun getInstructionsForManufacturer(manufacturer: String): String {
        return when {
            manufacturer.contains("Xiaomi", ignoreCase = true) ->
                """
                Tu dispositivo $manufacturer necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. En la siguiente pantalla, busca "Control Parental"
                2. Activa el interruptor para permitir auto-inicio
                3. También ve a "Ahorro de energía" y selecciona "Sin restricciones"
                
                ⚠️ Sin esto, los servicios no se iniciarán al reiniciar el dispositivo.
                """.trimIndent()

            manufacturer.contains("Oppo", ignoreCase = true) ||
            manufacturer.contains("Realme", ignoreCase = true) ->
                """
                Tu dispositivo $manufacturer necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. En la siguiente pantalla, busca "Control Parental"
                2. Activa "Permitir auto-inicio"
                3. Ve a Configuración → Batería → Optimización de batería
                4. Busca "Control Parental" y selecciona "No optimizar"
                
                ⚠️ Sin esto, la app dejará de funcionar después de reiniciar.
                """.trimIndent()

            manufacturer.contains("Huawei", ignoreCase = true) ||
            manufacturer.contains("Honor", ignoreCase = true) ->
                """
                Tu dispositivo $manufacturer necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. Activa "Inicio automático" para Control Parental
                2. Ve a "Administrador de teléfono"
                3. En "Aplicaciones protegidas", activa Control Parental
                
                ⚠️ Sin esto, los servicios se cerrarán automáticamente.
                """.trimIndent()

            manufacturer.contains("Vivo", ignoreCase = true) ||
            manufacturer.contains("iQOO", ignoreCase = true) ->
                """
                Tu dispositivo $manufacturer necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. Permite el "Auto-inicio" para Control Parental
                2. Ve a "Uso de batería" en Configuración
                3. Establece Control Parental en "Alto consumo de fondo"
                
                ⚠️ Sin esto, la app no funcionará en segundo plano.
                """.trimIndent()

            manufacturer.contains("OnePlus", ignoreCase = true) ->
                """
                Tu dispositivo $manufacturer necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. Activa "Auto-inicio" para Control Parental
                2. Ve a Batería → Optimización de batería
                3. Busca Control Parental y selecciona "No optimizar"
                
                ⚠️ Sin esto, los servicios no se ejecutarán al arrancar.
                """.trimIndent()

            else ->
                """
                Tu dispositivo necesita permisos adicionales para funcionar correctamente.
                
                📋 Pasos a seguir:
                1. Permite el "Auto-inicio" o "Inicio automático"
                2. Desactiva la "Optimización de batería" para esta app
                3. Permite que la app funcione en segundo plano
                
                ⚠️ Sin estos permisos, la app no funcionará correctamente.
                """.trimIndent()
        }
    }

    /**
     * Muestra instrucciones manuales si no se puede abrir la configuración automáticamente
     */
    private fun showManualInstructions(context: Context, manufacturer: String) {
        val instructions = when {
            manufacturer.contains("Oppo", ignoreCase = true) ||
            manufacturer.contains("Realme", ignoreCase = true) ->
                """
                📱 Instrucciones Manuales para $manufacturer:
                
                1️⃣ Ve a Configuración
                2️⃣ Busca "Administrador de aplicaciones" o "Gestión de aplicaciones"
                3️⃣ Busca "Control Parental"
                4️⃣ Toca en "Inicio automático" y actívalo
                5️⃣ Toca en "Uso de batería" y selecciona "No optimizar"
                6️⃣ Toca en "Restricciones en segundo plano" y selecciona "Permitir"
                """.trimIndent()

            else ->
                """
                📱 Instrucciones Manuales:
                
                1️⃣ Ve a Configuración del dispositivo
                2️⃣ Busca "Aplicaciones" o "Gestión de aplicaciones"
                3️⃣ Encuentra "Control Parental"
                4️⃣ Activa "Auto-inicio" o "Inicio automático"
                5️⃣ Desactiva "Optimización de batería"
                6️⃣ Permite ejecución en segundo plano
                """.trimIndent()
        }

        AlertDialog.Builder(context)
            .setTitle("📖 Configuración Manual")
            .setMessage(instructions)
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Guarda que ya se mostró el diálogo para no molestar al usuario cada vez
     */
    fun shouldShowAutoStartDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val shown = prefs.getBoolean("autostart_dialog_shown", false)
        val isProblematic = isProblematicManufacturer()

        Log.d(TAG, "📋 shouldShowAutoStartDialog():")
        Log.d(TAG, "  - Ya mostrado: $shown")
        Log.d(TAG, "  - Es problemático: $isProblematic")
        Log.d(TAG, "  - Resultado: ${!shown && isProblematic}")

        return !shown && isProblematic
    }

    /**
     * Marca el diálogo como mostrado
     */
    fun markAutoStartDialogShown(context: Context) {
        Log.d(TAG, "✏️ Marcando diálogo como mostrado...")
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("autostart_dialog_shown", true).apply()
        Log.d(TAG, "✅ Marcado correctamente")
    }
}
