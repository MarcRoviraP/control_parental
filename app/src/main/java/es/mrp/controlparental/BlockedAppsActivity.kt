package es.mrp.controlparental

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityBlockedAppsBinding

/**
 * Activity para gestionar las apps bloqueadas de los hijos
 * El padre puede ver todas las apps instaladas y bloquear/desbloquear con un solo toque
 */
class BlockedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedAppsBinding
    private lateinit var dbUtils: DataBaseUtils
    private lateinit var adapter: InstalledAppsAdapter
    private val blockedPackages = mutableSetOf<String>()
    private var childUuid: String? = null

    companion object {
        const val EXTRA_CHILD_UUID = "child_uuid"
        private const val TAG = "BlockedAppsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Gestionar Apps Bloqueadas"

        dbUtils = DataBaseUtils(this)

        // Obtener UUID del hijo desde el intent
        childUuid = intent.getStringExtra(EXTRA_CHILD_UUID)

        if (childUuid == null) {
            Toast.makeText(this, "Error: No se encontró el hijo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = InstalledAppsAdapter { packageName, appName, isCurrentlyBlocked ->
            toggleBlockStatus(packageName, appName, isCurrentlyBlocked)
        }

        binding.appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BlockedAppsActivity)
            adapter = this@BlockedAppsActivity.adapter
        }
    }

    private fun loadData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.appsRecyclerView.visibility = android.view.View.GONE

        // Primero cargar apps bloqueadas
        childUuid?.let { uuid ->
            dbUtils.getBlockedAppsForChild(uuid) { blocked ->
                blockedPackages.clear()
                blockedPackages.addAll(blocked)
                Log.d(TAG, "Apps bloqueadas cargadas: ${blockedPackages.size}")

                // Luego cargar apps instaladas
                loadInstalledApps(uuid)
            }
        }
    }

    private fun loadInstalledApps(childUuid: String) {
        dbUtils.getInstalledApps(childUuid) { installedApps ->
            runOnUiThread {
                if (installedApps.isEmpty()) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.emptyStateTextView.visibility = android.view.View.VISIBLE
                    binding.emptyStateTextView.text = "No se han detectado apps instaladas en el dispositivo del hijo.\n\nAsegúrate de que el servicio esté activo."
                } else {
                    // Convertir a lista de AppItem con estado de bloqueo
                    val appItems = installedApps.map { (packageName, appName) ->
                        AppItem(
                            packageName = packageName,
                            appName = appName,
                            isBlocked = blockedPackages.contains(packageName)
                        )
                    }.sortedBy { it.appName.lowercase() }

                    adapter.submitList(appItems)

                    binding.progressBar.visibility = android.view.View.GONE
                    binding.appsRecyclerView.visibility = android.view.View.VISIBLE
                    binding.emptyStateTextView.visibility = android.view.View.GONE

                    Log.d(TAG, "Apps instaladas cargadas: ${appItems.size}")
                }
            }
        }
    }

    private fun toggleBlockStatus(packageName: String, appName: String, isCurrentlyBlocked: Boolean) {
        childUuid?.let { uuid ->
            if (isCurrentlyBlocked) {
                // Desbloquear
                dbUtils.unblockAppForChild(
                    childUuid = uuid,
                    packageName = packageName,
                    onSuccess = {
                        runOnUiThread {
                            blockedPackages.remove(packageName)
                            updateAppInList(packageName, false)
                            Toast.makeText(this, "✅ $appName desbloqueada", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                // Bloquear
                dbUtils.blockAppForChild(
                    childUuid = uuid,
                    packageName = packageName,
                    appName = appName,
                    onSuccess = {
                        runOnUiThread {
                            blockedPackages.add(packageName)
                            updateAppInList(packageName, true)
                            Toast.makeText(this, "🚫 $appName bloqueada", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    private fun updateAppInList(packageName: String, isBlocked: Boolean) {
        val currentList = adapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isBlocked = isBlocked)
            adapter.submitList(currentList)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/**
 * Clase de datos para representar una app instalada con su estado de bloqueo
 */
data class AppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean
)

