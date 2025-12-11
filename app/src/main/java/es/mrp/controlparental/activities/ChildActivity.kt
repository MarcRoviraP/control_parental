package es.mrp.controlparental.activities

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import es.mrp.controlparental.databinding.ActivityChildBinding
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.adapters.AppListAdapter
import es.mrp.controlparental.dialogs.QRDialog
import es.mrp.controlparental.models.AppPackageClass
import es.mrp.controlparental.utils.getInstalledApps
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildBinding
    private lateinit var dbUtils: DataBaseUtils
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private val uploadInterval = 60000L // Subir cada 1 minuto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar DataBaseUtils y UsageStatsManager
        dbUtils = DataBaseUtils(this)
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.floatingActionButton.setOnClickListener {
         try {
            Log.d("ChildActivity", "FloatingActionButton clicked")
            val dialog = QRDialog.newInstance(dbUtils)
            dialog.show(supportFragmentManager, "QRDialog" )
         }catch (e: Exception){
             Log.e("ChildActivity", e.toString())
         }
        }

        setupRecyclerView()

        // Iniciar la subida periódica de datos de uso
        startPeriodicUsageUpload()
    }

    private fun setupRecyclerView() {
        // Obtener lista de apps instaladas
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = getInstalledApps(this, packages, pm)

        // Configurar RecyclerView
        val adapter = AppListAdapter(appList) { app, isBlocked ->
            if (isBlocked) {
                blockApp(app.packageName)
            } else {
                unblockApp(app.packageName)
            }
        }

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
    }

    /**
     * Inicia la subida periódica de datos de uso a Firebase
     */
    private fun startPeriodicUsageUpload() {
        val uploadRunnable = object : Runnable {
            override fun run() {
                uploadUsageDataToFirebase()
                handler.postDelayed(this, uploadInterval)
            }
        }

        // Primera subida inmediata
        uploadUsageDataToFirebase()

        // Programar subidas periódicas
        handler.postDelayed(uploadRunnable, uploadInterval)
    }

    /**
     * Obtiene el uso de apps del día actual y lo sube a Firebase en appUsage
     */
    private fun uploadUsageDataToFirebase() {
        val currentUser = dbUtils.auth.currentUser
        if (currentUser == null) {
            Log.w("ChildActivity", "No hay usuario autenticado")
            return
        }

        val childUuid = currentUser.uid
        val today = getCurrentDate()

        // Obtener estadísticas de uso del día
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats == null || usageStats.isEmpty()) {
            Log.d("ChildActivity", "No hay datos de uso disponibles")
            return
        }

        // Crear el mapa de datos de uso para subir a appUsage
        val usageData = hashMapOf<String, Any>()
        var appsUploaded = 0

        // Procesar cada app y agregar solo las instaladas por el usuario
        for (usageStat in usageStats) {
            val packageName = usageStat.packageName
            val timeInForeground = usageStat.totalTimeInForeground

            // Filtrar: solo apps con uso > 0 y que sean instaladas por el usuario
            if (timeInForeground > 0 && isUserInstalledApp(packageName)) {
                // Agregar al mapa con el packageName como clave
                usageData[packageName] = timeInForeground
                appsUploaded++
            }
        }

        // Subir todo junto a Firebase en la colección appUsage
        if (appsUploaded > 0) {
            dbUtils.uploadAppUsage(childUuid, usageData)
            Log.d("ChildActivity", "✅ Subidas $appsUploaded apps a Firebase (appUsage) para el día $today")
        } else {
            Log.d("ChildActivity", "No hay apps con uso para subir")
        }
    }

    /**
     * Verifica si una app está instalada por el usuario (no es del sistema)
     */
    private fun isUserInstalledApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            // Una app es del usuario si:
            // 1. No tiene la flag SYSTEM
            // 2. O tiene la flag UPDATED_SYSTEM_APP (es una app del sistema actualizada por el usuario)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Obtiene la fecha actual en formato YYYY-MM-DD
     */
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Calendar.getInstance().time)
    }

    private fun blockApp(packageName: String) {
        // Implementar lógica de bloqueo
        // Por ejemplo, usando DevicePolicyManager
    }

    private fun unblockApp(packageName: String) {
        // Implementar lógica de desbloqueo
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener las subidas periódicas cuando se destruya la activity
        handler.removeCallbacksAndMessages(null)
    }
}
