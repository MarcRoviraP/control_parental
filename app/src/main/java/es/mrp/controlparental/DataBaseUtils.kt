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
}
