package es.mrp.controlparental.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityBlockedAppsBinding
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.adapters.InstalledAppsAdapter

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
    private var toogleBlocked: Boolean = false


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

        // Configurar el botón de seleccionar/deseleccionar todas
        binding.btnSelectAll.setOnClickListener {
            toggleAllApps()
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
                    binding.actionButtonsLayout.visibility = android.view.View.GONE
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
                    updateToggleButton()

                    binding.progressBar.visibility = android.view.View.GONE
                    binding.appsRecyclerView.visibility = android.view.View.VISIBLE
                    binding.emptyStateTextView.visibility = android.view.View.GONE
                    binding.actionButtonsLayout.visibility = android.view.View.VISIBLE

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
                            updateToggleButton()
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
                            updateToggleButton()
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

    private fun updateToggleButton() {
        val totalApps = adapter.currentList.size
        val blockedCount = adapter.currentList.count { it.isBlocked }

        // Determinar el estado basado en el conteo real de apps bloqueadas
        toogleBlocked = blockedCount == totalApps && totalApps > 0

        // Si todas están bloqueadas, el botón debe decir "Desbloquear todas"
        // Si no todas están bloqueadas, el botón debe decir "Bloquear todas"
        if (toogleBlocked) {
            binding.btnSelectAll.text = "✅ Desbloquear todas"
        } else {
            binding.btnSelectAll.text = "🚫 Bloquear todas"
        }
    }

    private fun toggleAllApps() {
        childUuid?.let { uuid ->
            val currentList = adapter.currentList
            if (currentList.isEmpty()) return

            // Determinar la acción basándose en el estado ACTUAL de las apps, no en toggleBlocked
            val totalApps = currentList.size
            val blockedCount = currentList.count { it.isBlocked }
            val allAreBlocked = blockedCount == totalApps

            if (allAreBlocked) {
                // Desbloquear TODAS las apps
                desbloquearTodasLasApps(uuid, currentList)
            } else {
                // Bloquear TODAS las apps
                bloquearTodasLasApps(uuid, currentList)
            }
        }
    }

    private fun bloquearTodasLasApps(uuid: String, currentList: List<AppItem>) {
        var processed = 0
        var successful = 0
        val toProcess = currentList.filter { !it.isBlocked }

        if (toProcess.isEmpty()) {
            Toast.makeText(this, "✅ Todas las apps ya están bloqueadas", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "=== INICIANDO BLOQUEO MASIVO ===")
        Log.d(TAG, "Total apps a bloquear: ${toProcess.size}")
        toProcess.forEachIndexed { index, app ->
            Log.d(TAG, "[$index] ${app.appName} (${app.packageName}) - Bloqueada: ${app.isBlocked}")
        }

        Toast.makeText(this, "🚫 Bloqueando ${toProcess.size} apps...", Toast.LENGTH_SHORT).show()

        toProcess.forEach { app ->
            Log.d(TAG, "Intentando bloquear: ${app.appName} (${app.packageName})")

            dbUtils.blockAppForChild(
                childUuid = uuid,
                packageName = app.packageName,
                appName = app.appName,
                onSuccess = {
                    runOnUiThread {
                        blockedPackages.add(app.packageName)
                        processed++
                        successful++

                        Log.d(TAG, "✅ ÉXITO bloqueando: ${app.appName} (Procesadas: $processed/${toProcess.size}, Exitosas: $successful)")

                        if (processed == toProcess.size) {
                            Log.d(TAG, "=== BLOQUEO MASIVO COMPLETADO ===")
                            Log.d(TAG, "Total procesadas: $processed")
                            Log.d(TAG, "Total exitosas: $successful")
                            Log.d(TAG, "Total fallidas: ${processed - successful}")

                            // Recargar toda la lista para reflejar los cambios visuales
                            reloadAppsList {
                                if (successful == toProcess.size) {
                                    Toast.makeText(this, "✅ ${successful} apps bloqueadas correctamente", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "⚠️ ${successful} de ${toProcess.size} apps bloqueadas", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        processed++
                        Log.e(TAG, "❌ ERROR bloqueando: ${app.appName} (${app.packageName})")
                        Log.e(TAG, "Error detalle: $error")
                        Log.e(TAG, "Procesadas: $processed/${toProcess.size}, Exitosas: $successful")

                        if (processed == toProcess.size) {
                            Log.d(TAG, "=== BLOQUEO MASIVO COMPLETADO (CON ERRORES) ===")
                            Log.d(TAG, "Total procesadas: $processed")
                            Log.d(TAG, "Total exitosas: $successful")
                            Log.d(TAG, "Total fallidas: ${processed - successful}")

                            // Recargar toda la lista para reflejar los cambios visuales
                            reloadAppsList {
                                if (successful > 0) {
                                    Toast.makeText(this, "⚠️ ${successful} de ${toProcess.size} apps bloqueadas", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "❌ No se pudo bloquear ninguna app", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun desbloquearTodasLasApps(uuid: String, currentList: List<AppItem>) {
        var processed = 0
        var successful = 0
        val toProcess = currentList.filter { it.isBlocked }

        if (toProcess.isEmpty()) {
            Toast.makeText(this, "✅ Todas las apps ya están desbloqueadas", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "=== INICIANDO DESBLOQUEO MASIVO ===")
        Log.d(TAG, "Total apps a desbloquear: ${toProcess.size}")
        toProcess.forEachIndexed { index, app ->
            Log.d(TAG, "[$index] ${app.appName} (${app.packageName}) - Bloqueada: ${app.isBlocked}")
        }

        Toast.makeText(this, "✅ Desbloqueando ${toProcess.size} apps...", Toast.LENGTH_SHORT).show()

        toProcess.forEach { app ->
            Log.d(TAG, "Intentando desbloquear: ${app.appName} (${app.packageName})")

            dbUtils.unblockAppForChild(
                childUuid = uuid,
                packageName = app.packageName,
                onSuccess = {
                    runOnUiThread {
                        blockedPackages.remove(app.packageName)
                        processed++
                        successful++

                        Log.d(TAG, "✅ ÉXITO desbloqueando: ${app.appName} (Procesadas: $processed/${toProcess.size}, Exitosas: $successful)")

                        if (processed == toProcess.size) {
                            Log.d(TAG, "=== DESBLOQUEO MASIVO COMPLETADO ===")
                            Log.d(TAG, "Total procesadas: $processed")
                            Log.d(TAG, "Total exitosas: $successful")
                            Log.d(TAG, "Total fallidas: ${processed - successful}")

                            // Recargar toda la lista para reflejar los cambios visuales
                            reloadAppsList {
                                if (successful == toProcess.size) {
                                    Toast.makeText(this, "✅ ${successful} apps desbloqueadas correctamente", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "⚠️ ${successful} de ${toProcess.size} apps desbloqueadas", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        processed++
                        Log.e(TAG, "❌ ERROR desbloqueando: ${app.appName} (${app.packageName})")
                        Log.e(TAG, "Error detalle: $error")
                        Log.e(TAG, "Procesadas: $processed/${toProcess.size}, Exitosas: $successful")

                        if (processed == toProcess.size) {
                            Log.d(TAG, "=== DESBLOQUEO MASIVO COMPLETADO (CON ERRORES) ===")
                            Log.d(TAG, "Total procesadas: $processed")
                            Log.d(TAG, "Total exitosas: $successful")
                            Log.d(TAG, "Total fallidas: ${processed - successful}")

                            // Recargar toda la lista para reflejar los cambios visuales
                            reloadAppsList {
                                if (successful > 0) {
                                    Toast.makeText(this, "⚠️ ${successful} de ${toProcess.size} apps desbloqueadas", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "❌ No se pudo desbloquear ninguna app", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun reloadAppsList(onComplete: (() -> Unit)? = null) {
        childUuid?.let { uuid ->
            Log.d(TAG, "Recargando lista completa desde Firebase...")

            // Primero recargar las apps bloqueadas desde Firebase
            dbUtils.getBlockedAppsForChild(uuid) { blocked ->
                runOnUiThread {
                    // Actualizar el set de apps bloqueadas
                    blockedPackages.clear()
                    blockedPackages.addAll(blocked)
                    Log.d(TAG, "Apps bloqueadas recargadas desde Firebase: ${blockedPackages.size}")

                    // Luego recargar la lista de apps instaladas con el estado actualizado
                    dbUtils.getInstalledApps(uuid) { installedApps ->
                        runOnUiThread {
                            val appItems = installedApps.map { (packageName, appName) ->
                                AppItem(
                                    packageName = packageName,
                                    appName = appName,
                                    isBlocked = blockedPackages.contains(packageName)
                                )
                            }.sortedBy { it.appName.lowercase() }

                            Log.d(TAG, "Actualizando lista en UI: ${appItems.size} apps, ${appItems.count { it.isBlocked }} bloqueadas")

                            // Forzar actualización completa de la lista
                            adapter.submitList(null) // Limpiar primero
                            adapter.submitList(appItems) {
                                // Callback cuando la lista se ha actualizado completamente en la UI
                                updateToggleButton()
                                Log.d(TAG, "Lista actualizada en UI correctamente")
                                onComplete?.invoke()
                            }
                        }
                    }
                }
            }
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
