package es.mrp.controlparental

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class DataBaseUtils {
    val db = FirebaseFirestore.getInstance()
    val collectionFamilia = "familia"

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
    fun readFromFirestore(collection: String) {
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
}
