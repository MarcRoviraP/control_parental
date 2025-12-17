package es.mrp.controlparental.utils

import android.content.Context

/**
 * Utilidades para el tracking de uso de aplicaciones.
 * Contiene la lógica compartida entre ChildActivity y BlockService.
 */
object AppTrackingUtils {

    /**
     * Paquetes del sistema que se deben excluir del tracking de tiempo
     */
    val systemPackagesToExclude = setOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher", // Samsung
        "com.huawei.android.launcher", // Huawei
        "com.oppo.launcher", // OPPO
        "com.bbk.launcher2", // Vivo
        "com.mi.android.globallauncher", // Xiaomi
        "com.miui.home", // Xiaomi MIUI
        "com.oneplus.launcher", // OnePlus
        "com.android.systemui",
        "com.android.settings"
    )

    /**
     * Decide si un paquete debe excluirse del tracking de tiempo
     *
     * @param context Contexto de la aplicación
     * @param packageName Nombre del paquete a evaluar
     * @return true si debe excluirse, false si debe incluirse en el tracking
     */
    fun shouldExcludeFromTimeTracking(context: Context, packageName: String): Boolean {
        // Excluir la propia app de control parental
        if (packageName == context.packageName) {
            return true
        }

        // Excluir apps del sistema (launchers, settings, etc.)
        if (systemPackagesToExclude.contains(packageName)) {
            return true
        }

        return false
    }
}

