package es.mrp.controlparental.dialogs

import android.app.Dialog
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.fragment.app.DialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import es.mrp.controlparental.R
import es.mrp.controlparental.utils.DataBaseUtils

class QRDialog : DialogFragment() {

    companion object {
        private const val TAG = "QRDialog"
        private const val PREFS_NAME = "preferences"
        private const val UUID_KEY = "uuid"
        private const val QR_SIZE = 512

        /**
         * Método factory para crear una instancia del diálogo con DataBaseUtils
         */
        fun newInstance(dbUtils: DataBaseUtils): QRDialog {
            val dialog = QRDialog()
            dialog.dbUtils = dbUtils
            return dialog
        }
    }

    // Variable para almacenar DataBaseUtils
    private var dbUtils: DataBaseUtils? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Usar el tema transparente
        val builder = AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.qr_dialog_layout, null)

        builder.setView(view)
            .setTitle("Código QR de Emparejamiento\n")

        val dialog = builder.create()
        val qrImageView = view.findViewById<ImageView>(R.id.dialog_qr)
        val qrContent = createUUID()

        try {
            val qrBitmap = generateQRCode(qrContent)
            if (qrBitmap != null) {
                qrImageView?.setImageBitmap(qrBitmap)
            } else {
                showError("No se pudo generar el código QR")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generando QR", e)
            showError("Error al generar el código QR: ${e.message}")
        }

        return dialog
    }

    private fun createUUID(): String {
        val sharedPref = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Obtener el UID del usuario autenticado de Google
        val currentUser = dbUtils?.auth?.currentUser
        val uuid = currentUser?.uid

        if (uuid != null) {
            // Guardar el UID de Google en SharedPreferences
            sharedPref.edit {
                putString(UUID_KEY, uuid)
            }
            Log.d(TAG, "UUID de Google Auth usado para QR: $uuid")
        } else {
            Log.e(TAG, "No hay usuario autenticado de Google")
        }


        // Guardar en Firestore si dbUtils está disponible
        dbUtils?.let { db ->
            val userId = db.auth.currentUser?.uid
            if (userId != null) {


                db.writeInFirestore(db.collectionQrContent, hashMapOf("uuid" to uuid!!))
                Log.d(TAG, "UUID guardado en Firestore: $uuid")
            } else {
                Log.w(TAG, "No hay usuario autenticado, no se puede guardar en Firestore")
            }
        } ?: Log.w(TAG, "DataBaseUtils no está disponible")

        return "$uuid"
    }

    private fun generateQRCode(content: String, size: Int = QR_SIZE): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) BLACK else WHITE
                }
            }

            createBitmap(width, height).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: WriterException) {
            Log.e(TAG, "Error al escribir QR", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al generar QR", e)
            null
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        // Mostrar un TextView con el error si existe en el layout
        view?.findViewById<TextView>(R.id.error_text)?.apply {
            text = message
            visibility = android.view.View.VISIBLE
        }
    }
}