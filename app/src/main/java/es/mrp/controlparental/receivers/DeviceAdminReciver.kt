package es.mrp.controlparental.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ParentalControlAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "Control parental activado")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "¿Estás seguro de que quieres desactivar el control parental? Esto permitirá el acceso sin restricciones."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "Control parental desactivado")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d("DeviceAdmin", "Intento de contraseña fallido")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d("DeviceAdmin", "Contraseña correcta")
    }
}
