package es.mrp.controlparental
import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import es.mrp.controlparental.databinding.ActivityParentAccountBinding
import kotlinx.coroutines.launch

/**
 * Demonstrate Firebase Authentication using a Google ID Token.
 */
class ParentAccountActivity : AppCompatActivity() {

    private var toggleScanner = true
    private var isProcessingQR = false  // Bandera para evitar escaneos múltiples
    private lateinit var binding: ActivityParentAccountBinding
    private lateinit var previewView: PreviewView
    private lateinit var dbUtils: DataBaseUtils
    private lateinit var childUsageAdapter: ChildUsageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentAccountBinding.inflate(layoutInflater)

        setSupportActionBar(binding.toolbar)
        // [START initialize_auth]
        // Initialize Firebase Auth

        // Inicializar DataBaseUtils
        dbUtils = DataBaseUtils(this)

        // Configurar RecyclerView
        setupRecyclerView()

        // Iniciar escucha de datos de uso de hijos
        startListeningToChildrenUsage()
    }

    /**
     * Configura el RecyclerView para mostrar la lista de hijos
     */
    private fun setupRecyclerView() {
        childUsageAdapter = ChildUsageAdapter()
        binding.childrenRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ParentAccountActivity)
            adapter = childUsageAdapter
        }

        // Configurar listener para bloquear/desbloquear apps
        childUsageAdapter.onAppLongClickListener = { childUuid, packageName, appName, _ ->
            showBlockAppDialog(childUuid, packageName, appName)
        }
    }

    /**
     * Muestra un diálogo para bloquear o desbloquear una app
     */
    private fun showBlockAppDialog(childUuid: String, packageName: String, appName: String) {
        // Primero verificar si ya está bloqueada
        dbUtils.isAppBlocked(childUuid, packageName) { isBlocked ->
            runOnUiThread {
                val message = if (isBlocked) {
                    "¿Desbloquear la app '$appName'?"
                } else {
                    "¿Bloquear la app '$appName'?"
                }

                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(if (isBlocked) "Desbloquear App" else "Bloquear App")
                    .setMessage(message)
                    .setPositiveButton(if (isBlocked) "Desbloquear" else "Bloquear") { _, _ ->
                        if (isBlocked) {
                            unblockApp(childUuid, packageName, appName)
                        } else {
                            blockApp(childUuid, packageName, appName)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .create()

                dialog.show()
            }
        }
    }

    /**
     * Bloquea una app para un hijo
     */
    private fun blockApp(childUuid: String, packageName: String, appName: String) {
        dbUtils.blockAppForChild(
            childUuid = childUuid,
            packageName = packageName,
            appName = appName,
            onSuccess = {
                runOnUiThread {
                    makeText(this, "App '$appName' bloqueada", LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    makeText(this, "Error al bloquear: $error", LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Desbloquea una app para un hijo
     */
    private fun unblockApp(childUuid: String, packageName: String, appName: String) {
        dbUtils.unblockAppForChild(
            childUuid = childUuid,
            packageName = packageName,
            onSuccess = {
                runOnUiThread {
                    makeText(this, "App '$appName' desbloqueada", LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    makeText(this, "Error al desbloquear: $error", LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Inicia la escucha en tiempo real de los datos de uso de apps de todos los hijos
     */
    private fun startListeningToChildrenUsage() {
        val currentUser = dbUtils.auth.currentUser

        if (currentUser != null) {
            val parentUuid = currentUser.uid

            dbUtils.listenToChildrenAppUsage(parentUuid) { childUuid, usageData ->
                // Aquí se reciben los datos en tiempo real cuando el hijo sube nueva información
                Log.d("ParentActivity", "Datos recibidos del hijo: $childUuid")
                Log.d("ParentActivity", "Total de apps monitoreadas: ${usageData.size}")

                // Procesar los datos de uso
                processChildUsageData(childUuid, usageData)
            }

            Log.d("ParentActivity", "Escuchando datos de hijos...")
        } else {
            Log.w("ParentActivity", "No hay usuario autenticado para escuchar datos")
        }
    }

    /**
     * Procesa los datos de uso recibidos del hijo
     */
    private fun processChildUsageData(childUuid: String, usageData: Map<String, Any>) {
        // Convertir los datos de Firestore a la estructura de nuestra app
        val apps = mutableListOf<AppUsageInfo>()
        
        // IMPORTANTE: Usar lastCaptureTime que es cuando el hijo capturó estos datos
        // Esto hace que el timer "Hace Xs" sea consistente y no salte
        val timestamp = usageData["lastCaptureTime"] as? Long 
            ?: usageData["timestamp"] as? Long 
            ?: System.currentTimeMillis()

        // Procesar cada app en los datos
        usageData.filter { it.key.startsWith("app_") }.forEach { (_, value) ->
            if (value is Map<*, *>) {
                val packageName = value["packageName"] as? String ?: ""
                val appName = value["appName"] as? String ?: "App desconocida"
                val timeInForeground = (value["timeInForeground"] as? Number)?.toLong() ?: 0L
                val lastTimeUsed = (value["lastTimeUsed"] as? Number)?.toLong() ?: 0L

                if (packageName.isNotEmpty()) {
                    apps.add(
                        AppUsageInfo(
                            packageName = packageName,
                            appName = appName,
                            timeInForeground = timeInForeground,
                            lastTimeUsed = lastTimeUsed
                        )
                    )
                }
            }
        }

        runOnUiThread {
            // Actualizar el adaptador con los nuevos datos
            childUsageAdapter.updateChildData(childUuid, apps, timestamp)

            // Mostrar/ocultar mensaje de estado vacío
            if (childUsageAdapter.itemCount > 0) {
                binding.emptyStateTextView.visibility = android.view.View.GONE
                binding.childrenRecyclerView.visibility = android.view.View.VISIBLE
            } else {
                binding.emptyStateTextView.visibility = android.view.View.VISIBLE
                binding.childrenRecyclerView.visibility = android.view.View.GONE
            }

            Log.d("ParentActivity", "Hijo $childUuid: ${apps.size} apps actualizadas")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.parents_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.signOutItem -> {
                signOut()
                finish()
                true
            }

            R.id.scannerQR -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                    if (toggleScanner) {

                        startCamera()
                    } else {
                        stopScanner()
                    }
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 123)
                }

                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            makeText(this, "Permiso de cámara denegado", LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        toggleScanner = false
        isProcessingQR = false  // Resetear la bandera al iniciar
        previewView = binding.previewView
        binding.previewView.visibility = android.view.View.VISIBLE
        // Usamos CameraX

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview (para ver la cámara en pantalla)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // Analyzer (para leer el QR)
            val analysisUseCase = ImageAnalysis.Builder()
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    // Solo procesar si no estamos procesando otro QR
                                    if (!isProcessingQR && barcodes.isNotEmpty()) {
                                        isProcessingQR = true  // Marcar como procesando
                                        val barcode = barcodes[0]  // Solo tomar el primer QR
                                        Log.d("QR", "Contenido: ${barcode.rawValue}")

                                        // Validar el QR de forma asíncrona
                                        checkInfo(
                                            uuid = barcode.rawValue,
                                            onValid = { validUuid ->
                                                // QR válido - agregar hijo
                                                addChildToParent(validUuid)
                                                stopScanner()
                                            },
                                            onInvalid = {
                                                // QR inválido - el mensaje ya se mostró
                                                stopScanner()
                                            }
                                        )
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close() // <- siempre cerrar el frame
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // 🔹 Aquí sí usamos el analysisUseCase
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, analysisUseCase
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Error al iniciar la cámara", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun addChildToParent(childUUID: String) {
        val dbUtils = DataBaseUtils(this)
        val parentUser = dbUtils.auth.currentUser
        if (parentUser != null) {
            val parentUID = parentUser.uid

            val messageForParent = hashMapOf<String, Any>(
                "parentUID" to parentUID,
                "childUID" to childUUID,
            )
            dbUtils.childExists(
                childUuid = childUUID,
                parentUuid = parentUID
            ) { exists ->
                if (exists) {
                    Log.d("TAG", "El hijo ya está vinculado a este padre")
                } else {
                    Log.d("TAG", "No existe vinculación, se puede crear")
                    dbUtils.writeInFirestore(dbUtils.collectionFamilia, messageForParent)
                    makeText(this, "Niño añadido correctamente", LENGTH_SHORT).show()

                }
            }
        } else {
            makeText(this, "Error al añadir niño", LENGTH_SHORT).show()
        }
    }

    /**
     * Valida un UUID de QR contra Firestore
     * @param uuid UUID escaneado del código QR
     * @param onValid Callback ejecutado si el UUID es válido
     * @param onInvalid Callback ejecutado si el UUID no es válido
     */
    private fun checkInfo(
        uuid: String?,
        onValid: (String) -> Unit,
        onInvalid: () -> Unit
    ) {
        if (uuid.isNullOrBlank()) {
            makeText(this, "Código QR vacío o inválido", LENGTH_SHORT).show()
            onInvalid()
            return
        }

        val dbUtils = DataBaseUtils(this)

        // Verificar si el UUID existe en Firestore
        dbUtils.qrUuidExists(uuid) { exists ->
            if (!exists) {
                // UUID no encontrado en Firestore - puede estar caducado o ser inválido
                makeText(this, "Código QR caducado o inválido", LENGTH_SHORT).show()
                Log.w("QRScanner", "UUID no encontrado en Firestore (caducado o inválido): $uuid")
                onInvalid()
            } else {
                // UUID válido - eliminar de Firestore y continuar
                Log.d("QRScanner", "UUID válido encontrado: $uuid")
                dbUtils.deleteQrContentFromFirestore(uuid)
                onValid(uuid)
            }
        }
    }

    private fun stopScanner() {
        toggleScanner = true
        isProcessingQR = false  // Resetear la bandera al detener
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            binding.previewView.visibility = android.view.View.GONE
        }, ContextCompat.getMainExecutor(this))
    }
    // [START on_start_check_user]
    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = DataBaseUtils(this).auth.currentUser
        updateUI(currentUser)
    }


    // [START sign_out]
    private fun signOut() {
        // Firebase sign out
        DataBaseUtils(this).auth.signOut()

        // When a user signs out, clear the current user credential state from all credential providers.
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Clearing user credential state")
                val clearRequest = ClearCredentialStateRequest()
                DataBaseUtils(this@ParentAccountActivity).credentialManager.clearCredentialState(clearRequest)

            } catch (e: ClearCredentialException) {
                Log.e(TAG, "Couldn't clear user credentials: ${e.localizedMessage}")
            }
        }
    }
    // [END sign_out]

    private fun updateUI(user: FirebaseUser?) {
        if (user == null) return

        user.reload().addOnCompleteListener {
            val name = user.displayName ?: "Usuario"
            supportActionBar?.title = name
            setContentView(binding.root)
        }
    }


}