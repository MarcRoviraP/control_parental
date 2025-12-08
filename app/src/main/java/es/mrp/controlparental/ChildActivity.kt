package es.mrp.controlparental

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityChildBinding

class ChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildBinding
    private lateinit var dbUtils: DataBaseUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar DataBaseUtils
        dbUtils = DataBaseUtils(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.floatingActionButton.setOnClickListener {
         try {
            Log.d("ChildActivity", "FloatingActionButton clicked")
            // Usar el método factory newInstance() para pasar dbUtils
            val dialog = QRDialog.newInstance(dbUtils)
            dialog.show(supportFragmentManager, "QRDialog" )
         }catch (e: Exception){
             Log.e("ChildActivity", e.toString())
         }
        }

        // IMPORTANTE: Primero asegurar que el UUID existe
        ensureUuidExists()

        // Luego iniciar el servicio de monitoreo (ahora el UUID ya está guardado)
        startAppUsageMonitorService()

        setupRecyclerView()
    }

    /**
     * Inicia el servicio de monitoreo de uso de apps en segundo plano
     */
    private fun startAppUsageMonitorService() {
        try {
            val serviceIntent = Intent(this, AppUsageMonitorService::class.java)
            startService(serviceIntent)
            Log.d("ChildActivity", "Servicio de monitoreo iniciado")
        } catch (e: Exception) {
            Log.e("ChildActivity", "Error iniciando servicio de monitoreo", e)
        }
    }

    private fun setupRecyclerView() {
        // Obtener lista de apps instaladas
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = getInstalledApps(this, packages, pm)

        // Configurar RecyclerView
        val adapter = AppListAdapter(appList) { app, isBlocked ->
            // Callback cuando se cambia el estado de bloqueo
            if (isBlocked) {
                // Lógica para bloquear la app
                blockApp(app.packageName)
            } else {
                // Lógica para desbloquear la app
                unblockApp(app.packageName)
            }
        }

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
    }

    private fun blockApp(packageName: String) {
        // Implementar lógica de bloqueo
        // Por ejemplo, usando DevicePolicyManager
    }

    private fun unblockApp(packageName: String) {
        // Implementar lógica de desbloqueo
    }

    /**
     * Asegura que el UUID del hijo existe en SharedPreferences
     * Ahora usa el UID de Google Authentication
     */
    private fun ensureUuidExists() {
        val sharedPref = getSharedPreferences("preferences", MODE_PRIVATE)

        // Obtener el UID del usuario autenticado de Google
        val currentUser = dbUtils.auth.currentUser

        if (currentUser != null) {
            val googleUid = currentUser.uid

            // Guardar el UID de Google en SharedPreferences
            sharedPref.edit().apply {
                putString("uuid", googleUid)
                apply()
            }

            Log.d("ChildActivity", "✅ UUID de Google Auth guardado: $googleUid")
            Log.d("ChildActivity", "Email: ${currentUser.email}")
            Log.d("ChildActivity", "Nombre: ${currentUser.displayName}")

            // Mostrar confirmación al usuario
            android.widget.Toast.makeText(
                this,
                "Usuario: ${currentUser.displayName ?: currentUser.email}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            Log.e("ChildActivity", "❌ No hay usuario autenticado. El servicio no funcionará correctamente.")
            android.widget.Toast.makeText(
                this,
                "Error: No hay sesión de Google activa",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
