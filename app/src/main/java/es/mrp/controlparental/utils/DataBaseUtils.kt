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
    var auth = Firebase.auth
    var credentialManager = CredentialManager.create(context)


    fun updateInFirestore(collection: String, documentId: String, updates: Map<String, Any>) {
        db.collection(collection)
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Documento actualizado con ID: $documentId")
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error actualizando documento", e)
            }
    }
    
    fun writeInFirestore(collection: String, message: HashMap<String, Any>) {
        db.collection(collection)
            .add(message)
            .addOnSuccessListener { documentReference ->
                Log.d("FIRESTORE", "Documento añadido con ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error añadiendo documento", e)
            }
    }
    
    fun readCollectionFromFirestore(collection: String) {
        db.collection(collection)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d("FIRESTORE", "${document.id} => ${document.data}")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("FIRESTORE", "Error obteniendo documentos.", exception)
            }
    }
    
    fun deleteQrContentFromFirestore(uuid: String) {
        db.collection(collectionQrContent)
            .whereEqualTo("uuid", uuid)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.w("FIRESTORE", "No se encontró ningún documento con uuid: $uuid")
                } else {
                    for (document in result) {
                        db.collection(collectionQrContent)
                            .document(document.id)
                            .delete()
                            .addOnSuccessListener {
                                Log.d("FIRESTORE", "Documento QR eliminado exitosamente: ${document.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.w("FIRESTORE", "Error eliminando documento QR: ${document.id}", e)
                            }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.w("FIRESTORE", "Error buscando documentos para eliminar.", exception)
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
                    Log.w("FIRESTORE", errorMsg)
                    onError(errorMsg)
                } else {
                    val foundUuid = result.documents[0].getString("uuid")
                    if (foundUuid != null) {
                        Log.d("FIRESTORE", "UUID encontrado: $foundUuid")
                        onSuccess(foundUuid)
                    } else {
                        val errorMsg = "El documento no contiene el campo uuid"
                        Log.w("FIRESTORE", errorMsg)
                        onError(errorMsg)
                    }
                }
            }
            .addOnFailureListener { exception ->
                val errorMsg = "Error buscando documento con uuid: $uuid"
                Log.w("FIRESTORE", errorMsg, exception)
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
                Log.d("FIRESTORE", "UUID $uuid existe: $exists")
                callback(exists)
            }
            .addOnFailureListener { exception ->
                Log.w("FIRESTORE", "Error verificando existencia de uuid: $uuid", exception)
                callback(false)
            }
    }

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
                    Log.d("FIRESTORE", "Relación encontrada: Padre $parentUuid tiene hijo $childUuid")
                } else {
                    Log.d("FIRESTORE", "No existe relación entre padre $parentUuid e hijo $childUuid")
                }
                callback(exists)
            }
            .addOnFailureListener { exception ->
                Log.w("FIRESTORE", "Error verificando relación padre-hijo", exception)
                callback(false)
            }
    }

    fun uploadAppUsage(childUuid: String, usageData: HashMap<String, Any>) {
        usageData["childUID"] = childUuid
        usageData["timestamp"] = System.currentTimeMillis()

        db.collection(collectionAppUsage)
            .document(childUuid)
            .set(usageData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Datos de uso subidos para hijo: $childUuid")
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error subiendo datos de uso", e)
            }
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
                                    Log.w("FIRESTORE", "Error escuchando datos de hijo: $childUuid", error)
                                    return@addSnapshotListener
                                }

                                if (snapshot != null && snapshot.exists()) {
                                    val usageData = snapshot.data ?: emptyMap()
                                    Log.d("FIRESTORE", "Datos recibidos del hijo: $childUuid")
                                    onDataReceived(childUuid, usageData)
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error obteniendo hijos del padre", e)
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
                    callback(document.data)
                } else {
                    Log.w("FIRESTORE", "No hay datos de uso para el hijo: $childUuid")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error obteniendo datos de uso", e)
                callback(null)
            }
    }

    fun getChildName(childUid: String, callback: (String) -> Unit) {
        callback("Hijo")
    }

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
                Log.d("FIRESTORE", "App bloqueada en blockedApps: $appName para hijo $childUuid")
                updateBlockedAppsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error bloqueando app", e)
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
                Log.d("FIRESTORE", "App desbloqueada en blockedApps: $packageName para hijo $childUuid")
                updateBlockedAppsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error desbloqueando app", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun listenToBlockedApps(
        childUuid: String,
        onUpdate: (List<String>) -> Unit
    ) {
        db.collection(collectionBlockedApps)
            .whereEqualTo("childUID", childUuid)
            .whereEqualTo("isBlocked", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("FIRESTORE", "Error escuchando apps bloqueadas", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val blockedPackages = snapshots.mapNotNull { doc ->
                        doc.getString("packageName")
                    }
                    Log.d("FIRESTORE", "Apps bloqueadas actualizadas para hijo $childUuid: ${blockedPackages.size}")
                    onUpdate(blockedPackages)
                }
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
                Log.d("FIRESTORE", "Apps bloqueadas obtenidas para hijo $childUuid: ${blockedPackages.size}")
                callback(blockedPackages)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error obteniendo apps bloqueadas", e)
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
                Log.w("FIRESTORE", "Error verificando si app está bloqueada", e)
                callback(false)
            }
    }

    fun uploadInstalledApps(childUuid: String, installedApps: Map<String, String>) {
        val data = hashMapOf<String, Any>(
            "childUID" to childUuid,
            "timestamp" to System.currentTimeMillis(),
            "apps" to installedApps
        )

        db.collection("installedApps")
            .document(childUuid)
            .set(data)
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Apps instaladas subidas para hijo: $childUuid (${installedApps.size} apps)")
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error subiendo apps instaladas", e)
            }
    }

    fun getInstalledApps(
        childUuid: String,
        callback: (Map<String, String>) -> Unit
    ) {
        db.collection("installedApps")
            .document(childUuid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val apps = document.get("apps") as? Map<String, String> ?: emptyMap()
                    Log.d("FIRESTORE", "Apps instaladas obtenidas para hijo: $childUuid (${apps.size} apps)")
                    callback(apps)
                } else {
                    Log.w("FIRESTORE", "No hay apps instaladas para el hijo: $childUuid")
                    callback(emptyMap())
                }
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error obteniendo apps instaladas", e)
                callback(emptyMap())
            }
    }

    fun listenToInstalledApps(
        childUuid: String,
        onUpdate: (Map<String, String>) -> Unit
    ) {
        db.collection("installedApps")
            .document(childUuid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FIRESTORE", "Error escuchando apps instaladas", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val apps = snapshot.get("apps") as? Map<String, String> ?: emptyMap()
                    Log.d("FIRESTORE", "Apps instaladas actualizadas: $childUuid (${apps.size} apps)")
                    onUpdate(apps)
                }
            }
    }

    // ============ FUNCIONES DE LÍMITES DE TIEMPO ============

    fun setTimeLimit(
        childUuid: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val documentId = if (packageName.isEmpty()) {
            "${childUuid}_GLOBAL"
        } else {
            "${childUuid}_${packageName}"
        }

        val timeLimitData = hashMapOf(
            "childUID" to childUuid,
            "packageName" to packageName,
            "appName" to appName,
            "dailyLimitMinutes" to dailyLimitMinutes,
            "enabled" to enabled,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection(collectionTimeLimits)
            .document(documentId)
            .set(timeLimitData)
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Límite de tiempo establecido en timeLimits: $appName ($dailyLimitMinutes min)")
                updateTimeLimitsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error estableciendo límite de tiempo", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    fun removeTimeLimit(
        childUuid: String,
        packageName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val documentId = if (packageName.isEmpty()) {
            "${childUuid}_GLOBAL"
        } else {
            "${childUuid}_${packageName}"
        }

        db.collection(collectionTimeLimits)
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Límite de tiempo eliminado de timeLimits: $packageName")
                updateTimeLimitsInAppUsage(childUuid, onSuccess, onError)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error eliminando límite de tiempo", e)
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
                    Log.w("FIRESTORE", "Error escuchando límites de tiempo", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val limits = snapshots.mapNotNull { doc ->
                        try {
                            TimeLimit(
                                packageName = doc.getString("packageName") ?: "",
                                dailyLimitMinutes = doc.getLong("dailyLimitMinutes")?.toInt() ?: 0,
                                enabled = doc.getBoolean("enabled") ?: true,
                                appName = doc.getString("appName") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e("FIRESTORE", "Error parseando límite de tiempo", e)
                            null
                        }
                    }
                    Log.d("FIRESTORE", "Límites de tiempo actualizados: ${limits.size}")
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
                        TimeLimit(
                            packageName = doc.getString("packageName") ?: "",
                            dailyLimitMinutes = doc.getLong("dailyLimitMinutes")?.toInt() ?: 0,
                            enabled = doc.getBoolean("enabled") ?: true,
                            appName = doc.getString("appName") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e("FIRESTORE", "Error parseando límite de tiempo", e)
                        null
                    }
                }
                Log.d("FIRESTORE", "Límites de tiempo obtenidos: ${limits.size}")
                callback(limits)
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error obteniendo límites de tiempo", e)
                callback(emptyList())
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
                    Log.d("FIRESTORE", "✅ Campo blockedApps actualizado en appUsage: $blockedPackages")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w("FIRESTORE", "Error actualizando blockedApps en appUsage", e)
                    onError(e.message ?: "Error desconocido")
                }
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
                
                val key = if (limit.packageName.isEmpty()) "" else limit.packageName
                timeLimitsMap[key] = limitData
            }
            
            val data = hashMapOf<String, Any>(
                "timeLimits" to timeLimitsMap
            )
            
            db.collection(collectionAppUsage)
                .document(childUuid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FIRESTORE", "✅ Campo timeLimits actualizado en appUsage: ${timeLimitsMap.keys}")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w("FIRESTORE", "Error actualizando timeLimits en appUsage", e)
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
                    Log.w("FIRESTORE", "Error escuchando apps bloqueadas desde appUsage", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: emptyMap()
                    Log.d("FIRESTORE", "📦 Documento appUsage completo para $childUuid: ${data.keys}")

                    val blockedPackages = mutableListOf<String>()

                    @Suppress("UNCHECKED_CAST")
                    val blockedAppsField = data["blockedApps"] as? List<String>

                    if (blockedAppsField != null) {
                        blockedPackages.addAll(blockedAppsField)
                        Log.d("FIRESTORE", "✅ Apps bloqueadas encontradas en appUsage para hijo $childUuid: $blockedPackages")
                    } else {
                        Log.d("FIRESTORE", "⚠️ Campo 'blockedApps' no encontrado o vacío en appUsage para hijo $childUuid")
                        Log.d("FIRESTORE", "Campos disponibles: ${data.keys.joinToString(", ")}")
                    }

                    onUpdate(blockedPackages)
                } else {
                    Log.d("FIRESTORE", "❌ No hay documento appUsage para hijo $childUuid")
                    onUpdate(emptyList())
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
                    Log.w("FIRESTORE", "Error escuchando límites de tiempo desde appUsage", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: emptyMap()
                    Log.d("FIRESTORE", "📦 Buscando timeLimits en documento appUsage para $childUuid")

                    val limits = mutableListOf<TimeLimit>()

                    @Suppress("UNCHECKED_CAST")
                    val timeLimitsField = data["timeLimits"] as? Map<String, Any>

                    if (timeLimitsField != null) {
                        Log.d("FIRESTORE", "✅ Campo timeLimits encontrado con ${timeLimitsField.size} límites")

                        for ((packageName, limitData) in timeLimitsField) {
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val limitMap = limitData as? Map<String, Any>

                                if (limitMap != null) {
                                    val appName = limitMap["appName"] as? String ?: ""
                                    val dailyLimitMinutes = (limitMap["dailyLimitMinutes"] as? Number)?.toInt() ?: 0
                                    val enabled = limitMap["enabled"] as? Boolean ?: true

                                    limits.add(TimeLimit(
                                        packageName = packageName,
                                        appName = appName,
                                        dailyLimitMinutes = dailyLimitMinutes,
                                        enabled = enabled
                                    ))
                                    Log.d("FIRESTORE", "  📊 Límite: $appName ($packageName) = $dailyLimitMinutes min")
                                }
                            } catch (e: Exception) {
                                Log.e("FIRESTORE", "Error parseando límite de tiempo para $packageName", e)
                            }
                        }
                        Log.d("FIRESTORE", "Límites de tiempo actualizados desde appUsage: ${limits.size}")
                    } else {
                        Log.d("FIRESTORE", "⚠️ Campo 'timeLimits' no encontrado en appUsage para hijo $childUuid")
                    }

                    onUpdate(limits)
                } else {
                    Log.d("FIRESTORE", "❌ No hay documento appUsage para hijo $childUuid")
                    onUpdate(emptyList())
                }
            }
    }
}

