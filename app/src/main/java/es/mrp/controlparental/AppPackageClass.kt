package es.mrp.controlparental

import android.annotation.SuppressLint

class AppPackageClass (
    var packageName: String,
    var appName: String,
    var time: Long,
    var blocked: Boolean
) {
    @SuppressLint("DefaultLocale")
    override fun toString(): String {
        return String.format("AppPackageClass(packageName=%s, appName=%s, time=%d, blocked=%b)", packageName, appName, time, blocked)
    }
}