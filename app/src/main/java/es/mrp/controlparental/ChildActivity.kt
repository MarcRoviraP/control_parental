package es.mrp.controlparental

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityChildBinding

class ChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
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
}
