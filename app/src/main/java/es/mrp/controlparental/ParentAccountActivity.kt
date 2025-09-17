package es.mrp.controlparental
import android.Manifest
import android.R.attr.bitmap
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.widget.Toast.*
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import es.mrp.controlparental.databinding.ActivityParentAccountBinding
import kotlinx.coroutines.launch

/**
 * Demonstrate Firebase Authentication using a Google ID Token.
 */
class ParentAccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentAccountBinding
    private lateinit var previewView: PreviewView

    // [START declare_auth]

    private lateinit var auth: FirebaseAuth
    // [END declare_auth]

    // [START declare_credential_manager]
    private lateinit var credentialManager: CredentialManager
    // [END declare_credential_manager]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentAccountBinding.inflate(layoutInflater)

        setSupportActionBar(binding.toolbar)
        // [START initialize_auth]
        // Initialize Firebase Auth
        auth = Firebase.auth
        // [END initialize_auth]

        // [START initialize_credential_manager]
        // Initialize Credential Manager
        credentialManager = CredentialManager.create(baseContext)
        // [END initialize_credential_manager]

        // Botón de Google
        if (auth.currentUser == null) {
            Log.e(TAG, "Firebase auth is null")
            launchCredentialManager()
        }else{
            //displayUI()
        }


      }
 /*   private fun displayUI() {
        val name = auth.currentUser?.displayName
        Log.d(TAG, "Firebase auth is not null: $name")
        supportActionBar?.title = name
        setContentView(binding.root)
    }*/
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.parents_toolbar,menu)
        return super.onCreateOptionsMenu(menu)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId){
            R.id.signOutItem -> {
                signOut()
                finish()
                true
            }

            R.id.scannerQR -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
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
                                    for (barcode in barcodes) {
                                        Log.d("QR", "Contenido: ${barcode.rawValue}")
                                        stopScanner()
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
    private fun stopScanner() {
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
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }
    // [END on_start_check_user]


    private fun launchCredentialManager() {
        // [START create_credential_manager_request]
        // Instantiate a Google sign-in request
        val googleIdOption = GetGoogleIdOption.Builder()
            // Your server's client ID, not your Android client ID.
            .setServerClientId(getString(R.string.default_web_client_id))
            // Only show accounts previously used to sign in.
            .setFilterByAuthorizedAccounts(false)
            .build()

        // Create the Credential Manager request
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        // [END create_credential_manager_request]

        lifecycleScope.launch {
            try {
                // Launch Credential Manager UI
                val result = credentialManager.getCredential(
                    context = baseContext,
                    request = request
                )

                // Extract credential from the result returned by Credential Manager
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                finish()
                Log.e(TAG, "Couldn't retrieve user's credentials: ${e.localizedMessage}")
            }
            //displayUI()
        }
    }

    // [START handle_sign_in]
    private fun handleSignIn(credential: Credential) {
        // Check if credential is of type Google ID
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            // Create Google ID Token
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            // Sign in to Firebase with using the token
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }
    }
    // [END handle_sign_in]

    // [START auth_with_google]
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    updateUI(null)
                }
            }
    }
    // [END auth_with_google]

    // [START sign_out]
    private fun signOut() {
        // Firebase sign out
        auth.signOut()

        // When a user signs out, clear the current user credential state from all credential providers.
        lifecycleScope.launch {
            try {
                val clearRequest = ClearCredentialStateRequest()
                credentialManager.clearCredentialState(clearRequest)
                updateUI(null)
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

    companion object {
        private const val TAG = "GoogleActivity"
    }
}