package es.mrp.controlparental

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class DataBaseUtils(context: Context) {
    val db = FirebaseFirestore.getInstance()
    val collectionFamilia = "familia"
    val collectionQrContent = "qrContent"
    val collectionAppUsage = "appUsage"
    val collectionBlockedApps = "blockedApps"
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
                    // Eliminar cada documento encontrado (debería ser solo uno)
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
    /**
     * Obtiene el UUID de un código QR desde Firestore
     * @param uuid UUID a buscar
     * @param onSuccess Callback que se ejecuta cuando se encuentra el UUID
     * @param onError Callback que se ejecuta cuando hay un error o no se encuentra
     */
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

    /**
     * Verifica si un UUID existe en Firestore
     * @param uuid UUID a verificar
     * @param callback Callback que recibe true si existe, false si no
     */
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

    /**
     * Verifica si existe una relación padre-hijo en Firestore
     * @param childUuid UUID del hijo
     * @param parentUuid UUID del padre
     * @param callback Callback que recibe true si la relación existe, false si no
     */
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

    /**
     * Sube los datos de uso de aplicaciones del hijo a Firestore
     * @param childUuid UUID del hijo
     * @param usageData Datos de uso de aplicaciones
     */
    fun uploadAppUsage(childUuid: String, usageData: HashMap<String, Any>) {
        usageData["childUID"] = childUuid
        usageData["timestamp"] = System.currentTimeMillis()

        db.collection(collectionAppUsage)
            .document(childUuid)  // Usar el UUID del hijo como ID del documento
            .set(usageData)
            .addOnSuccessListener {
                Log.d("FIRESTORE", "Datos de uso subidos para hijo: $childUuid")
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error subiendo datos de uso", e)
            }
    }

    /**
     * Escucha en tiempo real los datos de uso de las apps de todos los hijos vinculados al padre
     * @param parentUuid UUID del padre
     * @param onDataReceived Callback con los datos de uso cuando hay cambios
     */
    fun listenToChildrenAppUsage(
        parentUuid: String,
        onDataReceived: (childUuid: String, usageData: Map<String, Any>) -> Unit
    ) {
        // Primero obtener todos los hijos del padre
        db.collection(collectionFamilia)
            .whereEqualTo("parentUID", parentUuid)
            .get()
            .addOnSuccessListener { familyDocs ->
                // Para cada hijo, escuchar sus datos de uso
                for (familyDoc in familyDocs) {
                    val childUuid = familyDoc.getString("childUID")

                    if (childUuid != null) {
                        // Listener en tiempo real para cada hijo
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

    /**
     * Obtiene los datos de uso de aplicaciones de un hijo específico
     * @param childUuid UUID del hijo
     * @param callback Callback con los datos de uso
     */
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

    /**
     * Obtiene información del usuario (hijo) desde Firebase Auth usando su UID
     * @param childUid UID del hijo
     * @param callback Callback con el nombre del hijo
     */
    fun getChildName(childUid: String, callback: (String) -> Unit) {
        // Firebase Auth no permite obtener información de otros usuarios directamente
        // Por ahora devolvemos "Hijo" pero podríamos guardarlo en Firestore
        callback("Hijo")
    }

    /**
     * Bloquea una app para un hijo específico
     * @param childUuid UUID del hijo
     * @param packageName Nombre del paquete de la app a bloquear
     * @param appName Nombre legible de la app
     * @param onSuccess Callback cuando se bloquea exitosamente
     * @param onError Callback cuando hay un error
     */
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

        // Usar childUuid_packageName como ID del documento para evitar duplicados
        val documentId = "${childUuid}_${packageName}"

        db.collection(collectionBlockedApps)
            .document(documentId)
            .set(blockData)
            .addOnSuccessListener {
                Log.d("FIRESTORE", "App bloqueada: $appName para hijo $childUuid")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error bloqueando app", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Desbloquea una app para un hijo específico
     * @param childUuid UUID del hijo
     * @param packageName Nombre del paquete de la app a desbloquear
     * @param onSuccess Callback cuando se desbloquea exitosamente
     * @param onError Callback cuando hay un error
     */
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
                Log.d("FIRESTORE", "App desbloqueada: $packageName para hijo $childUuid")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.w("FIRESTORE", "Error desbloqueando app", e)
                onError(e.message ?: "Error desconocido")
            }
    }

    /**
     * Escucha en tiempo real los cambios en las apps bloqueadas para un hijo
     * @param childUuid UUID del hijo
     * @param onUpdate Callback que se ejecuta cuando hay cambios en la lista de apps bloqueadas
     */
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

    /**
     * Obtiene la lista de apps bloqueadas para un hijo (sin listener en tiempo real)
     * @param childUuid UUID del hijo
     * @param callback Callback con la lista de paquetes bloqueados
     */
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

    /**
     * Verifica si una app específica está bloqueada para un hijo
     * @param childUuid UUID del hijo
     * @param packageName Nombre del paquete a verificar
     * @param callback Callback con true si está bloqueada, false si no
     */
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

    /**
     * Sube la lista de apps instaladas del hijo a Firestore
     * @param childUuid UUID del hijo
     * @param installedApps Lista de apps instaladas (packageName -> appName)
     */
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

    /**
     * Obtiene la lista de apps instaladas de un hijo
     * @param childUuid UUID del hijo
     * @param callback Callback con el mapa de apps (packageName -> appName)
     */
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

    /**
     * Escucha cambios en las apps instaladas de un hijo en tiempo real
     * @param childUuid UUID del hijo
     * @param onUpdate Callback que se ejecuta cuando hay cambios
     */
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
}
