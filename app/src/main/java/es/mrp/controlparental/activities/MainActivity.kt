package es.mrp.controlparental.activities

import android.content.Intent
import android.os.Build
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

    companion object {
        private const val TAG = "MainActivity"

        private fun logD(message: String) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            Log.d(TAG, "[Línea $lineNumber] $message")
        }

        private fun logW(message: String) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            Log.w(TAG, "[Línea $lineNumber] $message")
        }

        private fun logE(message: String, throwable: Throwable? = null) {
            val lineNumber = Thread.currentThread().stackTrace[3].lineNumber
            if (throwable != null) {
                Log.e(TAG, "[Línea $lineNumber] $message", throwable)
            } else {
                Log.e(TAG, "[Línea $lineNumber] $message")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("📱 MainActivity.onCreate() | Thread: ${Thread.currentThread().name}")
        logD("Timestamp: ${System.currentTimeMillis()}")

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logD("Vista inflada y establecida")

        // Inicializar DataBaseUtils una sola vez
        logD("Inicializando DataBaseUtils y CredentialManager...")
        dbUtils = DataBaseUtils(this)
        dbUtils.credentialManager = CredentialManager.create(this)
        logD("✅ DataBaseUtils inicializado")

        binding.cardVwChild.setOnClickListener {
            logD("👆 Usuario tocó cardVwChild (modo hijo)")
            logD("Iniciando ChildActivity sin autenticación...")
            // Los hijos no necesitan autenticación
            startActivity(android.content.Intent(this, ChildActivity::class.java))
        }

        binding.cardVwParent.setOnClickListener {
            logD("👆 Usuario tocó cardVwParent (modo padre)")
            // Los padres necesitan autenticación
            if (dbUtils.auth.currentUser == null) {
                logW("Usuario no autenticado | Solicitando inicio de sesión")
                Toast.makeText(this, "Por favor, inicia sesión primero", Toast.LENGTH_SHORT).show()
            } else {
                logD("Usuario ya autenticado: ${dbUtils.auth.currentUser?.email}")
                logD("Iniciando ParentAccountActivity...")
                // Ya está autenticado, ir directamente
                startActivity(android.content.Intent(this, ParentAccountActivity::class.java))
            }
        }

        // ✅ CAMBIO PRINCIPAL: Intentar login automático al iniciar la app
        if (dbUtils.auth.currentUser == null) {
            logD("No hay usuario autenticado | Intentando login automático con Google...")
            launchCredentialManager()
        } else {
            logD("✅ Usuario ya autenticado: ${dbUtils.auth.currentUser?.email}")
            logD("UID: ${dbUtils.auth.currentUser?.uid}")
        }
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    override fun onStart() {
        super.onStart()
        logD("onStart() llamado")
        if (dbUtils.auth.currentUser != null) {
            logD("Usuario actual en onStart: ${dbUtils.auth.currentUser?.email} | UID: ${dbUtils.auth.currentUser?.uid}")
        } else {
            logD("No hay usuario autenticado en onStart")
        }
    }


    override fun onResume() {
        super.onResume()
        logD("onResume() llamado")
        // ✅ CAMBIO PRINCIPAL: Intentar login automático al iniciar la app
        if (dbUtils.auth.currentUser == null) {
            logD("No hay usuario autenticado en onResume | Intentando login automático...")
            launchCredentialManager()
        } else {
            logD("Usuario ya autenticado en onResume: ${dbUtils.auth.currentUser?.email}")
        }
    }

    private fun launchCredentialManager() {
        if (isAuthInProgress) {
            logD("Autenticación ya en progreso | Saltando llamada duplicada")
            return
        }

        isAuthInProgress = true
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("🔐 Iniciando Credential Manager | Timestamp: ${System.currentTimeMillis()}")

        // Configurar opciones de Google Sign-In
        val webClientId = getString(R.string.default_web_client_id)
        logD("WebClientId obtenido: ${webClientId.take(20)}...")

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()

        logD("GoogleIdOption configurado | FilterByAuthorizedAccounts: false")

        // Crear solicitud de credenciales
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        logD("GetCredentialRequest creado | Lanzando corrutina...")

        lifecycleScope.launch {
            try {
                logD("Mostrando UI del Credential Manager...")
                // Mostrar UI del Credential Manager
                val result = dbUtils.credentialManager.getCredential(
                    context = this@MainActivity,
                    request = request
                )

                logD("✅ Credencial obtenida | Tipo: ${result.credential.type}")
                // Procesar credencial obtenida
                handleSignIn(result.credential)
            } catch (e: NoCredentialException) {
                // ✅ SOLUCIÓN: No hay cuenta de Google → Mostrar opciones INMEDIATAMENTE
                isAuthInProgress = false
                logE("❌ No hay credenciales de Google disponibles | Tipo: NoCredentialException", e)
                logE("Mensaje: ${e.message}")

                // Mostrar diálogo con opción de crear cuenta de Google
                logD("Abriendo configuración de cuentas de Google...")
                openGoogleAccountSettings()

            } catch (e: GetCredentialException) {
                // Otros errores
                isAuthInProgress = false

                val errorMessage = e.message ?: "Error desconocido"
                logE("❌ Error obteniendo credenciales | Tipo: ${e.javaClass.simpleName}", e)
                logE("Mensaje: $errorMessage")

                // Si el usuario canceló, mostrar opciones alternativas
                if (errorMessage.contains("cancel", ignoreCase = true) ||
                    errorMessage.contains("user", ignoreCase = true)) {
                    logD("Usuario canceló el inicio de sesión | Mostrando opciones alternativas")
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
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }


    /**
     * ✅ Abre la configuración para agregar cuenta de Google
     */
    private fun openGoogleAccountSettings() {
        logD("Intentando abrir configuración de cuentas de Google...")
        try {
            // Intenta abrir directamente la pantalla de agregar cuenta
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
            startActivity(intent)
            logD("✅ Intent lanzado: ACTION_ADD_ACCOUNT con tipo com.google")
            Toast.makeText(
                this,
                "Agrega tu cuenta de Google y vuelve a la app",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            logE("❌ No se pudo abrir ACTION_ADD_ACCOUNT | Intentando ACTION_SYNC_SETTINGS", e)
            // Si falla, abre configuración general de cuentas
            try {
                startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                logD("✅ Intent lanzado: ACTION_SYNC_SETTINGS")
                Toast.makeText(
                    this,
                    "Ve a Agregar cuenta → Google",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                logE("❌ Tampoco se pudo abrir ACTION_SYNC_SETTINGS", e2)
                Toast.makeText(
                    this,
                    "Ve a Configuración → Cuentas → Agregar cuenta → Google",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        logD("handleSignIn() llamado | Tipo de credencial: ${credential.type}")

        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            logD("✅ Credencial es de tipo Google ID Token")
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            logD("Token extraído | Longitud: ${googleIdTokenCredential.idToken.length} caracteres")
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            isAuthInProgress = false
            logW("⚠️ La credencial NO es de tipo Google ID Token | Tipo: ${credential.type}")
            Toast.makeText(this, "Tipo de credencial no válido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logD("🔥 Autenticando con Firebase usando Google ID Token...")
        logD("Token length: ${idToken.length} caracteres")

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        logD("Credential de Firebase creado | Llamando a signInWithCredential...")

        dbUtils.auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                isAuthInProgress = false
                if (task.isSuccessful) {
                    logD("✅ ¡Inicio de sesión con Google EXITOSO!")
                    val user = dbUtils.auth.currentUser
                    logD("Usuario autenticado:")
                    logD("  - Email: ${user?.email}")
                    logD("  - Nombre: ${user?.displayName}")
                    logD("  - UID: ${user?.uid}")
                    logD("  - Foto: ${user?.photoUrl}")

                    // NUEVO: Guardar UUID en SharedPreferences inmediatamente después del login
                    user?.let {
                        val sharedPref = getSharedPreferences("preferences", MODE_PRIVATE)
                        sharedPref.edit().apply {
                            putString("uuid", it.uid)
                            apply()
                        }
                        logD("✅ UUID guardado en SharedPreferences: ${it.uid}")

                        // Iniciar el servicio de monitoreo si no está corriendo
                        try {
                            logD("Intentando iniciar AppUsageMonitorService...")
                            val serviceIntent = Intent(this, AppUsageMonitorService::class.java)
                            startService(serviceIntent)
                            logD("✅ Servicio de monitoreo iniciado después del login")
                        } catch (e: Exception) {
                            logE("❌ Error iniciando servicio de monitoreo", e)
                        }
                    }

                    Toast.makeText(this, "✅ Bienvenido ${user?.displayName ?: user?.email}", Toast.LENGTH_SHORT).show()
                } else {
                    logW("❌ Error en inicio de sesión con Google")
                    logE("Exception: ${task.exception?.message}", task.exception)
                    logE("Causa: ${task.exception?.cause?.message}")
                    Toast.makeText(
                        this,
                        "Error al autenticar: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                logD("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
    }
}
