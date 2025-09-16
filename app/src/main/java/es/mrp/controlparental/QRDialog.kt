package es.mrp.controlparental

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.*
import android.os.Bundle
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

class QRDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.qr_dialog_layout, null)

        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val qrImageView = view.findViewById<ImageView>(R.id.dialog_qr)
        val qrContent = createUUID()
        val qrBitmap = generateQRCode(qrContent)
        if (qrBitmap != null) {
            qrImageView?.setImageBitmap(qrBitmap)
        }

        return dialog

    }

    private fun createUUID(): String {
        return String.format(java.util.UUID.randomUUID().toString() + "- -" + System.currentTimeMillis())
    }
    @SuppressLint("UseKtx")
    fun generateQRCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        setPixel(x, y, if (bitMatrix[x, y]) BLACK else WHITE)
                    }
                }
            }
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        }
    }

}