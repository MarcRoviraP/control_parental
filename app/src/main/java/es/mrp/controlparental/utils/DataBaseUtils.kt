package es.mrp.controlparental.utils

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import es.mrp.controlparental.models.TimeLimit

class DataBaseUtils(context: Context) {
    val db = FirebaseFirestore.getInstance()
    val collectionFamilia = "familia"
    val collectionQrContent = "qrContent"
    val collectionAppUsage = "appUsage"
    val collectionBlockedApps = "blockedApps"
    val collectionTimeLimits = "timeLimits"
    val collectionInstalledApps = "installedApps"

    var auth = Firebase.auth
    var credentialManager = CredentialManager.create(context)

    // ============================================
    // TAGS PARA LOGS POR COLECCIÓN
    // ============================================
    companion object {
        private const val TAG_QR = "DB_QR"
        private const val TAG_FAMILIA = "DB_FAMILIA"
        private const val TAG_APP_USAGE = "DB_APP_USAGE"
        private const val TAG_BLOCKED_APPS = "DB_BLOCKED_APPS"
        private const val TAG_TIME_LIMITS = "DB_TIME_LIMITS"
        private const val TAG_INSTALLED_APPS = "DB_INSTALLED_APPS"
    }

    // ============================================
    // COLECCIÓN: qrContent
    // ============================================

    fun deleteQrContentFromFirestore(uuid: String) {
        db.collection(collectionQrContent)
            .whereEqualTo("uuid", uuid)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.w(TAG_QR, "No se encontró ningún documento con uuid: $uuid")
                } else {
                    for (document in result) {
                        db.collection(collectionQrContent)
                            .document(document.id)
                            .delete()
                            .addOnSuccessListener {
                                Log.d(TAG_QR, "Documento QR eliminado exitosamente: ${document.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG_QR, "Error eliminando documento QR: ${document.id}", e)
                            }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG_QR, "Error buscando documentos para eliminar.", exception)
            }
    }
    
    fun getQrUuid(
        uuid: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        db.collection(collectionQrContent)
            .whereEqualTo("uuid", uuid)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    val errorMsg = "No se encontró ningún documento con uuid: $uuid"
                    Log.w(TAG_QR, errorMsg)
                    onError(errorMsg)
                } else {
                    val foundUuid = result.documents[0].getString("uuid")
                    if (foundUuid != null) {
                        Log.d(TAG_QR, "UUID encontrado: $foundUuid")
                        onSuccess(foundUuid)
                    } else {
                        val errorMsg = "El documento no contiene el campo uuid"
                        Log.w(TAG_QR, errorMsg)
                        onError(errorMsg)
                    }
                }
            }
            .addOnFailureListener { exception ->
                val errorMsg = "Error buscando documento con uuid: $uuid"
                Log.w(TAG_QR, errorMsg, exception)
                onError(errorMsg)
            }
    }

    fun qrUuidExists(
        uuid: String,
        callback: (Boolean) -> Unit
    ) {
        db.collection(collectionQrContent)
            .whereEqualTo("uuid", uuid)
            .get()
            .addOnSuccessListener { result ->
                val exists = !result.isEmpty
                Log.d(TAG_QR, "UUID $uuid existe: $exists")
                callback(exists)
            }
            .addOnFailureListener { exception ->
                Log.w(TAG_QR, "Error verificando existencia de uuid: $uuid", exception)
                callback(false)
            }
    }

    fun writeInFirestore(collection: String, message: HashMap<String, Any>) {
        db.collection(collection)
            .add(message)
            .addOnSuccessListener { documentReference ->
                Log.d("DB_WRITE", "Documento añadido con ID: ${documentReference.id} en $collection")
            }
            .addOnFailureListener { e ->
                Log.w("DB_WRITE", "Error añadiendo documento en $collection", e)
            }
    }

    // ============================================
    // COLECCIÓN: familia
    // ============================================

    fun childExists(
        childUuid: String,
        parentUuid: String,
        callback: (Boolean) -> Unit
    ) {
        db.collection(collectionFamilia)
            .whereEqualTo("childUID", childUuid)
            .whereEqualTo("parentUID", parentUuid)
            .get()
            .addOnSuccessListener { result ->
                val exists = !result.isEmpty
                if (exists) {
                    Log.d(TAG_FAMILIA, "Relación encontrada: Padre $parentUuid tiene hijo $childUuid")
                } else {
                    Log.d(TAG_FAMILIA, "No existe relación entre padre $parentUuid e hijo $childUuid")
                }
                callback(exists)
            }
            .addOnFailureListener { exception ->
                Log.w(TAG_FAMILIA, "Error verificando relación padre-hijo", exception)
                callback(false)
            }
    }

    fun getChildName(childUid: String, callback: (String) -> Unit) {
        callback("Hijo")
    }

    // ============================================
    // COLECCIÓN: appUsage
    // ============================================

    fun uploadAppUsage(childUuid: String, usageData: HashMap<String, Any>) {
        usageData["childUID"] = childUuid
        usageData["timestamp"] = System.currentTimeMillis()

        Log.d(TAG_APP_USAGE, "Subiendo datos para hijo $childUuid: ${usageData.keys.filter { it.startsWith("app_") }.size} apps")
        db.collection(collectionAppUsage)
            .document(childUuid)
            .set(usageData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG_APP_USAGE, "✅ Datos de uso subidos para hijo: $childUuid")
            }
            .addOnFailureListener { e ->
                Log.w(TAG_APP_USAGE, "❌ Error subiendo datos de uso", e)
            }
    }

    /**
     * Reinicia el contador de uso diario a 0 para todas las aplicaciones
     * Esto se debe llamar cuando cambia el día (a las 00:00)
     */
    fun resetDailyUsage(childUuid: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        Log.d(TAG_APP_USAGE, "🔄 Reiniciando contador diario para hijo: $childUuid")

        // Obtener el documento actual para preservar solo los campos no relacionados con app_X
        db.collection(collectionAppUsage)
            .document(childUuid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentData = document.data ?: emptyMap()

                    // Crear un nuevo mapa solo con los campos que NO son app_X
                    val preservedData = mutableMapOf<String, Any>()
                    val excludedFields = setOf("childUID", "timestamp", "lastCaptureTime", "blockedApps", "timeLimits")

                    for ((key, value) in currentData) {
                        if (excludedFields.contains(key)) {
                            preservedData[key] = value
                        }
                    }

                    // Actualizar timestamp
                    preservedData["timestamp"] = System.currentTimeMillis()
                    preservedData["lastCaptureTime"] = System.currentTimeMillis()
                    preservedData["lastResetDate"] = getCurrentDate()

                    // Sobrescribir el documento (esto elimina los campos app_X)
                    db.collection(collectionAppUsage)
                        .document(childUuid)
                        .set(preservedData)
                        .addOnSuccessListener {
                            Log.d(TAG_APP_USAGE, "✅ Contador diario reiniciado exitosamente")
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG_APP_USAGE, "❌ Error reiniciando contador diario", e)
                            onError(e.message ?: "Error desconocido")
                        }
                } else {
                    Log.w(TAG_APP_USAGE, "⚠️ No hay documento para reiniciar")
                    onSuccess()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG_APP_USAGE, "❌ Error obteniendo documento para reinicio", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Obtiene la fecha actual en formato YYYY-MM-DD
     */
    private fun getCurrentDate(): String {
        val calendar = java.util.Calendar.getInstance()
        return String.format("%04d-%02d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    fun listenToChildrenAppUsage(
        parentUuid: String,
        onDataReceived: (childUuid: String, usageData: Map<String, Any>) -> Unit
    ) {
        db.collection(collectionFamilia)
            .whereEqualTo("parentUID", parentUuid)
            .get()
            .addOnSuccessListener { familyDocs ->
                for (familyDoc in familyDocs) {
                    val childUuid = familyDoc.getString("childUID")

                    if (childUuid != null) {
                        db.collection(collectionAppUsage)
                            .document(childUuid)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Log.w(TAG_APP_USAGE, "Error escuchando datos de hijo: $childUuid", error)
                                    return@addSnapshotListener
                                }

                                if (snapshot != null && snapshot.exists()) {
                                    val usageData = snapshot.data ?: emptyMap()
                                    Log.d(TAG_APP_USAGE, "📊 Datos recibidos del hijo: $childUuid")
                                    onDataReceived(childUuid, usageData)
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG_APP_USAGE, "Error obteniendo hijos del padre", e)
            }
    }

    fun getChildAppUsage(
        childUuid: String,
        callback: (Map<String, Any>?) -> Unit
    ) {
        db.collection(collectionAppUsage)
            .document(childUuid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG_APP_USAGE, "📊 Datos de uso obtenidos para hijo: $childUuid")
                    callback(document.data)
                } else {
                    Log.w(TAG_APP_USAGE, "⚠️ No hay datos de uso para el hijo: $childUuid")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG_APP_USAGE, "❌ Error obteniendo datos de uso", e)
                callback(null)
            }
    }

    // ============================================
    // COLECCIÓN: blockedApps
    // ============================================

    fun blockAppForChild(
        childUuid: String,
        packageName: String,
        appName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val blockData = hashMapOf(
            "childUID" to childUuid,
            "packageName" to packageName,
            "appName" to appName,
            "blockedAt" to System.currentTimeMillis(),
            "isBlocked" to true
        )

        val documentId = "${childUuid}_${packageName}"

        db.collection(collectionBlockedApps)
            .document(documentId)
            .set(blockData)
            .addOnSuccessListener {
                Log.d(TAG_BLOCKED_APPS, "✅ App bloqueada: $appName para hijo $childUuid")
                updateBlockedAppsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_BLOCKED_APPS, "❌ Error bloqueando app", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun unblockAppForChild(
        childUuid: String,
        packageName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val documentId = "${childUuid}_${packageName}"

        db.collection(collectionBlockedApps)
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG_BLOCKED_APPS, "✅ App desbloqueada: $packageName para hijo $childUuid")
                updateBlockedAppsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_BLOCKED_APPS, "❌ Error desbloqueando app", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun getBlockedAppsForChild(
        childUuid: String,
        callback: (List<String>) -> Unit
    ) {
        db.collection(collectionBlockedApps)
            .whereEqualTo("childUID", childUuid)
            .whereEqualTo("isBlocked", true)
            .get()
            .addOnSuccessListener { documents ->
                val blockedPackages = documents.mapNotNull { doc ->
                    doc.getString("packageName")
                }
                Log.d(TAG_BLOCKED_APPS, "📦 Apps bloqueadas obtenidas para hijo $childUuid: ${blockedPackages.size}")
                callback(blockedPackages)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_BLOCKED_APPS, "❌ Error obteniendo apps bloqueadas", e)
                callback(emptyList())
            }
    }

    fun isAppBlocked(
        childUuid: String,
        packageName: String,
        callback: (Boolean) -> Unit
    ) {
        val documentId = "${childUuid}_${packageName}"

        db.collection(collectionBlockedApps)
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->
                val isBlocked = document.exists() && document.getBoolean("isBlocked") == true
                callback(isBlocked)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_BLOCKED_APPS, "❌ Error verificando si app está bloqueada", e)
                callback(false)
            }
    }

    private fun updateBlockedAppsInAppUsage(
        childUuid: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        getBlockedAppsForChild(childUuid) { blockedPackages ->
            val data = hashMapOf<String, Any>(
                "blockedApps" to blockedPackages
            )

            db.collection(collectionAppUsage)
                .document(childUuid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG_BLOCKED_APPS, "✅ Campo blockedApps actualizado en appUsage: ${blockedPackages.size} apps")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG_BLOCKED_APPS, "❌ Error actualizando blockedApps en appUsage", e)
                    onError(e.message ?: "Error desconocido")
                }
        }
    }

    fun listenToBlockedAppsFromUsage(
        childUuid: String,
        onUpdate: (List<String>) -> Unit
    ) {
        db.collection(collectionAppUsage)
            .document(childUuid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG_BLOCKED_APPS, "❌ Error escuchando apps bloqueadas desde appUsage", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val blockedAppsField = snapshot.data?.get("blockedApps") as? List<String>

                    if (blockedAppsField != null) {
                        Log.d(TAG_BLOCKED_APPS, "✅ Apps bloqueadas desde appUsage: ${blockedAppsField.size}")
                        onUpdate(blockedAppsField)
                    } else {
                        Log.d(TAG_BLOCKED_APPS, "⚠️ Campo 'blockedApps' no encontrado en appUsage")
                        onUpdate(emptyList())
                    }
                } else {
                    Log.d(TAG_BLOCKED_APPS, "⚠️ No hay documento appUsage para hijo $childUuid")
                    onUpdate(emptyList())
                }
            }
    }

    // ============================================
    // COLECCIÓN: timeLimits
    // ============================================

    fun setTimeLimit(
        childUuid: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Usar "GLOBAL_LIMIT" en lugar de cadena vacía para el límite global
        val normalizedPackageName = if (packageName.isEmpty()) "GLOBAL_LIMIT" else packageName
        val documentId = "${childUuid}_${normalizedPackageName}"

        val timeLimitData = hashMapOf(
            "childUID" to childUuid,
            "packageName" to normalizedPackageName,
            "appName" to appName,
            "dailyLimitMinutes" to dailyLimitMinutes,
            "enabled" to enabled,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection(collectionTimeLimits)
            .document(documentId)
            .set(timeLimitData)
            .addOnSuccessListener {
                Log.d(TAG_TIME_LIMITS, "✅ Límite establecido: $appName ($dailyLimitMinutes min)")
                updateTimeLimitsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_TIME_LIMITS, "❌ Error estableciendo límite", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun removeTimeLimit(
        childUuid: String,
        packageName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Usar "GLOBAL_LIMIT" en lugar de cadena vacía para el límite global
        val normalizedPackageName = if (packageName.isEmpty()) "GLOBAL_LIMIT" else packageName
        val documentId = "${childUuid}_${normalizedPackageName}"

        db.collection(collectionTimeLimits)
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG_TIME_LIMITS, "✅ Límite eliminado: $packageName")
                updateTimeLimitsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_TIME_LIMITS, "❌ Error eliminando límite", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun listenToTimeLimits(
        childUuid: String,
        onUpdate: (List<TimeLimit>) -> Unit
    ) {
        db.collection(collectionTimeLimits)
            .whereEqualTo("childUID", childUuid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG_TIME_LIMITS, "❌ Error escuchando límites de tiempo", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val limits = snapshots.mapNotNull { doc ->
                        try {
                            val packageName = doc.getString("packageName") ?: ""
                            // Convertir "GLOBAL_LIMIT" de vuelta a cadena vacía
                            val normalizedPackage = if (packageName == "GLOBAL_LIMIT") "" else packageName

                            TimeLimit(
                                packageName = normalizedPackage,
                                dailyLimitMinutes = doc.getLong("dailyLimitMinutes")?.toInt() ?: 0,
                                enabled = doc.getBoolean("enabled") ?: true,
                                appName = doc.getString("appName") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG_TIME_LIMITS, "❌ Error parseando límite", e)
                            null
                        }
                    }
                    Log.d(TAG_TIME_LIMITS, "📊 Límites actualizados: ${limits.size}")
                    onUpdate(limits)
                }
            }
    }

    fun getTimeLimits(
        childUuid: String,
        callback: (List<TimeLimit>) -> Unit
    ) {
        db.collection(collectionTimeLimits)
            .whereEqualTo("childUID", childUuid)
            .get()
            .addOnSuccessListener { documents ->
                val limits = documents.mapNotNull { doc ->
                    try {
                        val packageName = doc.getString("packageName") ?: ""
                        // Convertir "GLOBAL_LIMIT" de vuelta a cadena vacía
                        val normalizedPackage = if (packageName == "GLOBAL_LIMIT") "" else packageName

                        TimeLimit(
                            packageName = normalizedPackage,
                            dailyLimitMinutes = doc.getLong("dailyLimitMinutes")?.toInt() ?: 0,
                            enabled = doc.getBoolean("enabled") ?: true,
                            appName = doc.getString("appName") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG_TIME_LIMITS, "❌ Error parseando límite", e)
                        null
                    }
                }
                Log.d(TAG_TIME_LIMITS, "📊 Límites obtenidos: ${limits.size}")
                callback(limits)
            }
            .addOnFailureListener { e ->
                Log.w(TAG_TIME_LIMITS, "❌ Error obteniendo límites", e)
                callback(emptyList())
            }
    }

    private fun updateTimeLimitsInAppUsage(
        childUuid: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        getTimeLimits(childUuid) { limits ->
            val timeLimitsMap = mutableMapOf<String, Any>()
            
            for (limit in limits) {
                val limitData = hashMapOf<String, Any>(
                    "appName" to limit.appName,
                    "dailyLimitMinutes" to limit.dailyLimitMinutes,
                    "enabled" to limit.enabled
                )
                
                // Usar "GLOBAL_LIMIT" como clave en lugar de cadena vacía
                val key = if (limit.packageName.isEmpty()) "GLOBAL_LIMIT" else limit.packageName
                timeLimitsMap[key] = limitData
            }
            
            val data = hashMapOf<String, Any>(
                "timeLimits" to timeLimitsMap
            )
            
            db.collection(collectionAppUsage)
                .document(childUuid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG_TIME_LIMITS, "✅ Campo timeLimits actualizado en appUsage: ${timeLimitsMap.size} límites")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG_TIME_LIMITS, "❌ Error actualizando timeLimits en appUsage", e)
                    onError(e.message ?: "Error desconocido")
                }
        }
    }

    fun listenToTimeLimitsFromUsage(
        childUuid: String,
        onUpdate: (List<TimeLimit>) -> Unit
    ) {
        db.collection(collectionAppUsage)
            .document(childUuid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG_TIME_LIMITS, "❌ Error escuchando límites desde appUsage", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val limits = mutableListOf<TimeLimit>()

                    @Suppress("UNCHECKED_CAST")
                    val timeLimitsField = snapshot.data?.get("timeLimits") as? Map<String, Any>

                    if (timeLimitsField != null) {
                        Log.d(TAG_TIME_LIMITS, "✅ Campo timeLimits encontrado: ${timeLimitsField.size} límites")

                        for ((packageName, limitData) in timeLimitsField) {
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val limitMap = limitData as? Map<String, Any>

                                if (limitMap != null) {
                                    val appName = limitMap["appName"] as? String ?: ""
                                    val dailyLimitMinutes = (limitMap["dailyLimitMinutes"] as? Number)?.toInt() ?: 0
                                    val enabled = limitMap["enabled"] as? Boolean ?: true

                                    // Convertir "GLOBAL_LIMIT" de vuelta a cadena vacía
                                    val normalizedPackage = if (packageName == "GLOBAL_LIMIT") "" else packageName

                                    limits.add(TimeLimit(
                                        packageName = normalizedPackage,
                                        appName = appName,
                                        dailyLimitMinutes = dailyLimitMinutes,
                                        enabled = enabled
                                    ))
                                    Log.d(TAG_TIME_LIMITS, "  📊 $appName ($packageName) = $dailyLimitMinutes min")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG_TIME_LIMITS, "❌ Error parseando límite para $packageName", e)
                            }
                        }
                    } else {
                        Log.d(TAG_TIME_LIMITS, "⚠️ Campo 'timeLimits' no encontrado en appUsage")
                    }

                    onUpdate(limits)
                } else {
                    Log.d(TAG_TIME_LIMITS, "⚠️ No hay documento appUsage para hijo $childUuid")
                    onUpdate(emptyList())
                }
            }
    }

    // ============================================
    // COLECCIÓN: installedApps
    // ============================================

    fun uploadInstalledApps(childUuid: String, installedApps: Map<String, String>) {
        val data = hashMapOf<String, Any>(
            "childUID" to childUuid,
            "timestamp" to System.currentTimeMillis(),
            "apps" to installedApps
        )

        db.collection(collectionInstalledApps)
            .document(childUuid)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG_INSTALLED_APPS, "✅ Apps instaladas subidas: ${installedApps.size} apps")
            }
            .addOnFailureListener { e ->
                Log.w(TAG_INSTALLED_APPS, "❌ Error subiendo apps instaladas", e)
            }
    }

    fun getInstalledApps(
        childUuid: String,
        callback: (Map<String, String>) -> Unit
    ) {
        db.collection(collectionInstalledApps)
            .document(childUuid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val apps = document.get("apps") as? Map<String, String> ?: emptyMap()
                    Log.d(TAG_INSTALLED_APPS, "📦 Apps instaladas obtenidas: ${apps.size}")
                    callback(apps)
                } else {
                    Log.w(TAG_INSTALLED_APPS, "⚠️ No hay apps instaladas para el hijo: $childUuid")
                    callback(emptyMap())
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG_INSTALLED_APPS, "❌ Error obteniendo apps instaladas", e)
                callback(emptyMap())
            }
    }

    fun listenToInstalledApps(
        childUuid: String,
        onUpdate: (Map<String, String>) -> Unit
    ) {
        db.collection(collectionInstalledApps)
            .document(childUuid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG_INSTALLED_APPS, "❌ Error escuchando apps instaladas", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val apps = snapshot.get("apps") as? Map<String, String> ?: emptyMap()
                    Log.d(TAG_INSTALLED_APPS, "📦 Apps instaladas actualizadas: ${apps.size}")
                    onUpdate(apps)
                }
            }
    }
}
