package es.mrp.controlparental.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import es.mrp.controlparental.R
import es.mrp.controlparental.databinding.ActivityMainBinding
import es.mrp.controlparental.services.AppUsageMonitorService
import es.mrp.controlparental.utils.DataBaseUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dbUtils: DataBaseUtils
    private var isAuthInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar DataBaseUtils una sola vez
        dbUtils = DataBaseUtils(this)
        dbUtils.credentialManager = CredentialManager.create(this)

        binding.cardVwChild.setOnClickListener {
            // Los hijos no necesitan autenticación
            startActivity(android.content.Intent(this, ChildActivity::class.java))
        }

        binding.cardVwParent.setOnClickListener {
            // Los padres necesitan autenticación
            if (dbUtils.auth.currentUser == null) {
                Toast.makeText(this, "Por favor, inicia sesión primero", Toast.LENGTH_SHORT).show()
            } else {
                // Ya está autenticado, ir directamente
                startActivity(android.content.Intent(this, ParentAccountActivity::class.java))
            }
        }

        // ✅ CAMBIO PRINCIPAL: Intentar login automático al iniciar la app
        if (dbUtils.auth.currentUser == null) {
            Log.d(TAG, "No hay usuario autenticado - Intentando login automático con Google")
            launchCredentialManager()
        } else {
            Log.d(TAG, "Usuario ya autenticado: ${dbUtils.auth.currentUser?.email}")
        }
    }

    override fun onStart() {
        super.onStart()
        if (dbUtils.auth.currentUser != null) {
            Log.d(TAG, "Usuario en onStart: ${dbUtils.auth.currentUser?.email}")
        }
    }


    override fun onResume() {
        super.onResume()
        // ✅ CAMBIO PRINCIPAL: Intentar login automático al iniciar la app
        if (dbUtils.auth.currentUser == null) {
            Log.d(TAG, "No hay usuario autenticado - Intentando login automático con Google")
            launchCredentialManager()
        } else {
            Log.d(TAG, "Usuario ya autenticado: ${dbUtils.auth.currentUser?.email}")
        }
    }
    private fun launchCredentialManager() {
        if (isAuthInProgress) {
            Log.d(TAG, "Autenticación ya en progreso")
            return
        }

        isAuthInProgress = true
        Log.d(TAG, "Iniciando Credential Manager")

        // Configurar opciones de Google Sign-In
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        // Crear solicitud de credenciales
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                // Mostrar UI del Credential Manager
                val result = dbUtils.credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )

                // Procesar credencial obtenida
                handleSignIn(result.credential)
            } catch (e: NoCredentialException) {
                // ✅ SOLUCIÓN: No hay cuenta de Google → Mostrar opciones INMEDIATAMENTE
                isAuthInProgress = false
                Log.e(TAG, "No hay credenciales de Google disponibles", e)

                // Mostrar diálogo con opción de crear cuenta de Google
                openGoogleAccountSettings()

            } catch (e: GetCredentialException) {
                // Otros errores
                isAuthInProgress = false

                val errorMessage = e.message ?: "Error desconocido"
                Log.e(TAG, "Error obteniendo credenciales: ${e.localizedMessage}", e)

                // Si el usuario canceló, mostrar opciones alternativas
                if (errorMessage.contains("cancel", ignoreCase = true) ||
                    errorMessage.contains("user", ignoreCase = true)) {
                    Log.d(TAG, "Usuario canceló el inicio de sesión - Mostrando opciones alternativas")
                    openGoogleAccountSettings()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al iniciar sesión: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    /**
     * ✅ Abre la configuración para agregar cuenta de Google
     */
    private fun openGoogleAccountSettings() {
        try {
            // Intenta abrir directamente la pantalla de agregar cuenta
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Agrega tu cuenta de Google y vuelve a la app",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Si falla, abre configuración general de cuentas
            try {
                startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                Toast.makeText(
                    this,
                    "Ve a Agregar cuenta → Google",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Ve a Configuración → Cuentas → Agregar cuenta → Google",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            isAuthInProgress = false
            Log.w(TAG, "La credencial no es de tipo Google ID")
            Toast.makeText(this, "Tipo de credencial no válido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        dbUtils.auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                isAuthInProgress = false
                if (task.isSuccessful) {
                    Log.d(TAG, "Inicio de sesión con Google exitoso")
                    val user = dbUtils.auth.currentUser
                    Log.d(TAG, "Usuario: ${user?.email}, Nombre: ${user?.displayName}")

                    // NUEVO: Guardar UUID en SharedPreferences inmediatamente después del login
                    user?.let {
                        val sharedPref = getSharedPreferences("preferences", MODE_PRIVATE)
                        sharedPref.edit().apply {
                            putString("uuid", it.uid)
                            apply()
                        }
                        Log.d(TAG, "✅ UUID guardado en SharedPreferences: ${it.uid}")

                        // Iniciar el servicio de monitoreo si no está corriendo
                        try {
                            val serviceIntent = Intent(this, AppUsageMonitorService::class.java)
                            startService(serviceIntent)
                            Log.d(TAG, "✅ Servicio de monitoreo iniciado después del login")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error iniciando servicio de monitoreo", e)
                        }
                    }

                    Toast.makeText(this, "✅ Bienvenido ${user?.displayName ?: user?.email}", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Error en inicio de sesión con Google", task.exception)
                    Toast.makeText(
                        this,
                        "Error al autenticar: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
