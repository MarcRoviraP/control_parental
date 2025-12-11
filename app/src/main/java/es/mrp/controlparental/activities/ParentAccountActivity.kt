package es.mrp.controlparental.activities

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
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
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseUser
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import es.mrp.controlparental.R
import es.mrp.controlparental.databinding.ActivityParentAccountBinding
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.adapters.ChildUsageAdapter
import es.mrp.controlparental.models.AppUsageInfo
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

        // Configurar listener para abrir la activity de gestión de apps bloqueadas
        childUsageAdapter.onManageBlockedAppsClick = { childUuid ->
            openBlockedAppsActivity(childUuid)
        }

        // Configurar listener para abrir la activity de gestión de límites de tiempo
        childUsageAdapter.onManageTimeLimitsClick = { childUuid ->
            openTimeLimitsActivity(childUuid)
        }
    }

    /**
     * Abre la activity de gestión de apps bloqueadas para un hijo específico
     */
    private fun openBlockedAppsActivity(childUuid: String) {
        val intent = Intent(this, BlockedAppsActivity::class.java)
        intent.putExtra(BlockedAppsActivity.EXTRA_CHILD_UUID, childUuid)
        startActivity(intent)
    }

    /**
     * Abre la activity de gestión de límites de tiempo para un hijo específico
     */
    private fun openTimeLimitsActivity(childUuid: String) {
        val intent = Intent(this, TimeLimitsActivity::class.java)
        intent.putExtra(TimeLimitsActivity.EXTRA_CHILD_UUID, childUuid)
        startActivity(intent)
    }

    /**
     * Inicia la escucha en tiempo real de los datos de uso de apps de todos los hijos
     */
    private fun startListeningToChildrenUsage() {
        val currentUser = dbUtils.auth.currentUser

        if (currentUser != null) {
            val parentUuid = currentUser.uid

            // Usar la función que lee de appUsage
            dbUtils.listenToChildrenAppUsage(parentUuid) { childUuid, usageData ->
                // Aquí se reciben los datos en tiempo real cuando el hijo sube nueva información
                Log.d("ParentActivity", "Datos recibidos del hijo: $childUuid")
                Log.d("ParentActivity", "Total de datos: ${usageData.size}")

                // Procesar los datos de uso
                processChildUsageData(childUuid, usageData)
            }

            Log.d("ParentActivity", "Escuchando datos de hijos desde appUsage...")
        } else {
            Log.w("ParentActivity", "No hay usuario autenticado para escuchar datos")
        }
    }

    /**
     * Procesa los datos de uso recibidos del hijo desde appUsage
     */
    private fun processChildUsageData(childUuid: String, usageData: Map<String, Any>) {
        // Convertir los datos de Firestore a la estructura de nuestra app
        val apps = mutableListOf<AppUsageInfo>()

        val timestamp = usageData["timestamp"] as? Long ?: System.currentTimeMillis()

        // Lista de campos que NO son app_X (metadatos)
        val excludedFields = setOf(
            "childUID",
            "timestamp",
            "lastCaptureTime",
            "blockedApps",
            "timeLimits"
        )

        // Procesar cada campo app_X en los datos
        usageData.forEach { (key, value) ->
            // Buscar campos con formato app_X que contienen mapas de datos
            if (!excludedFields.contains(key) && key.startsWith("app_") && value is Map<*, *>) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val appData = value as Map<String, Any>

                    val packageName = appData["packageName"] as? String ?: ""
                    val appName = appData["appName"] as? String ?: ""
                    val timeInForeground = (appData["timeInForeground"] as? Number)?.toLong() ?: 0L
                    val lastTimeUsed = (appData["lastTimeUsed"] as? Number)?.toLong() ?: timestamp

                    if (packageName.isNotEmpty() && timeInForeground > 0) {
                        apps.add(
                            AppUsageInfo(
                                packageName = packageName,
                                appName = appName,
                                timeInForeground = timeInForeground,
                                lastTimeUsed = lastTimeUsed
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ParentActivity", "Error procesando $key: ${e.message}")
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
                true
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
        isProcessingQR = false
        previewView = binding.previewView
        binding.previewView.visibility = android.view.View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

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
                                    if (!isProcessingQR && barcodes.isNotEmpty()) {
                                        isProcessingQR = true
                                        val barcode = barcodes[0]
                                        Log.d("QR", "Contenido: ${barcode.rawValue}")

                                        checkInfo(
                                            uuid = barcode.rawValue,
                                            onValid = { validUuid ->
                                                addChildToParent(validUuid)
                                                stopScanner()
                                            },
                                            onInvalid = {
                                                stopScanner()
                                            }
                                        )
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
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

        dbUtils.qrUuidExists(uuid) { exists ->
            if (!exists) {
                makeText(this, "Código QR caducado o inválido", LENGTH_SHORT).show()
                Log.w("QRScanner", "UUID no encontrado en Firestore (caducado o inválido): $uuid")
                onInvalid()
            } else {
                Log.d("QRScanner", "UUID válido encontrado: $uuid")
                dbUtils.deleteQrContentFromFirestore(uuid)
                onValid(uuid)
            }
        }
    }

    private fun stopScanner() {
        toggleScanner = true
        isProcessingQR = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            binding.previewView.visibility = android.view.View.GONE
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStart() {
        super.onStart()
        val currentUser = DataBaseUtils(this).auth.currentUser
        updateUI(currentUser)
    }

    private fun signOut() {
        DataBaseUtils(this).auth.signOut()

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

    private fun updateUI(user: FirebaseUser?) {
        if (user == null) return

        user.reload().addOnCompleteListener {
            val name = user.displayName ?: "Usuario"
            supportActionBar?.title = name
            setContentView(binding.root)
        }
    }
}
