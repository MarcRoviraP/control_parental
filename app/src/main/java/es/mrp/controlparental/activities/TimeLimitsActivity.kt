package es.mrp.controlparental.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import es.mrp.controlparental.R
import es.mrp.controlparental.utils.DataBaseUtils
import es.mrp.controlparental.models.TimeLimit

class TimeLimitsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddLimit: FloatingActionButton
    private lateinit var dbUtils: DataBaseUtils
    private lateinit var adapter: TimeLimitsAdapter
    private lateinit var emptyStateLayout: LinearLayout
    private var childUuid: String? = null
    private val timeLimits = mutableListOf<TimeLimit>()
    private val installedApps = mutableMapOf<String, String>()

    companion object {
        const val EXTRA_CHILD_UUID = "child_uuid"
        private const val TAG = "TimeLimitsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_limits)

        childUuid = intent.getStringExtra(EXTRA_CHILD_UUID)
        if (childUuid == null) {
            Toast.makeText(this, "Error: No se encontró el UUID del hijo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Configurar toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Límites de Tiempo"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dbUtils = DataBaseUtils(this)

        recyclerView = findViewById(R.id.recyclerViewTimeLimits)
        fabAddLimit = findViewById(R.id.fabAddLimit)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        adapter = TimeLimitsAdapter(timeLimits) { timeLimit ->
            showEditDialog(timeLimit)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAddLimit.setOnClickListener {
            showAddDialog()
        }

        loadInstalledApps()
        loadTimeLimits()
    }

    private fun loadInstalledApps() {
        val uuid = childUuid ?: return
        dbUtils.getInstalledApps(uuid) { apps ->
            installedApps.clear()
            installedApps.putAll(apps)
            Log.d(TAG, "Apps instaladas cargadas: ${apps.size}")
        }
    }

    private fun loadTimeLimits() {
        val uuid = childUuid ?: return
        dbUtils.listenToTimeLimits(uuid) { limits ->
            timeLimits.clear()
            timeLimits.addAll(limits)
            adapter.notifyDataSetChanged()

            // Mostrar/ocultar empty state
            if (timeLimits.isEmpty()) {
                emptyStateLayout.visibility = android.view.View.VISIBLE
                recyclerView.visibility = android.view.View.GONE
            } else {
                emptyStateLayout.visibility = android.view.View.GONE
                recyclerView.visibility = android.view.View.VISIBLE
            }

            Log.d(TAG, "Límites de tiempo cargados: ${limits.size}")
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_limit, null)
        val spinnerApp = dialogView.findViewById<Spinner>(R.id.spinnerApp)
        val editHours = dialogView.findViewById<EditText>(R.id.editHours)
        val editMinutes = dialogView.findViewById<EditText>(R.id.editMinutes)
        val textTimeDisplay = dialogView.findViewById<TextView>(R.id.textTimeDisplay)
        val switchEnabled = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchEnabled)
        val btn30min = dialogView.findViewById<Button>(R.id.btn30min)
        val btn1hour = dialogView.findViewById<Button>(R.id.btn1hour)
        val btn2hours = dialogView.findViewById<Button>(R.id.btn2hours)
        val btn4hours = dialogView.findViewById<Button>(R.id.btn4hours)

        // Establecer valores iniciales con formato
        editHours.setText("00")
        editMinutes.setText("00")
        textTimeDisplay.text = "00:00"

        // Función para actualizar el display de tiempo
        val updateTimeDisplay = {
            val hours = editHours.text.toString().toIntOrNull() ?: 0
            val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
            textTimeDisplay.text = "${formatTwoDigits(hours)}:${formatTwoDigits(minutes)}"
        }

        // Listeners para actualizar el display en tiempo real
        editHours.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTimeDisplay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        editMinutes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTimeDisplay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Preparar lista de apps para el spinner
        val appList = mutableListOf("🌐 Límite Global del Dispositivo")
        appList.addAll(installedApps.values.sorted())

        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appList)
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerApp.adapter = appAdapter

        // Configurar botones de sugerencias rápidas con formato
        btn30min.setOnClickListener {
            editHours.setText("00")
            editMinutes.setText("30")
        }
        btn1hour.setOnClickListener {
            editHours.setText("01")
            editMinutes.setText("00")
        }
        btn2hours.setOnClickListener {
            editHours.setText("02")
            editMinutes.setText("00")
        }
        btn4hours.setOnClickListener {
            editHours.setText("04")
            editMinutes.setText("00")
        }

        AlertDialog.Builder(this)
            .setTitle("")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, which ->
                val hours = editHours.text.toString().toIntOrNull() ?: 0
                val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
                val totalMinutes = (hours * 60) + minutes
                val enabled = switchEnabled.isChecked
                val selectedPosition = spinnerApp.selectedItemPosition

                if (totalMinutes <= 0) {
                    Toast.makeText(this, "⚠️ Por favor ingresa un tiempo válido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val packageName: String
                val appName: String

                if (selectedPosition == 0) {
                    // Límite global
                    packageName = ""
                    appName = "Límite Global del Dispositivo"
                } else {
                    // App específica
                    appName = appList[selectedPosition]
                    packageName = installedApps.entries.find { it.value == appName }?.key ?: ""
                }

                val uuid = childUuid ?: return@setPositiveButton
                dbUtils.setTimeLimit(
                    childUuid = uuid,
                    packageName = packageName,
                    appName = appName,
                    dailyLimitMinutes = totalMinutes,
                    enabled = enabled,
                    onSuccess = {
                        Toast.makeText(this, "✓ Límite guardado correctamente", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditDialog(timeLimit: TimeLimit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_limit, null)
        val spinnerApp = dialogView.findViewById<Spinner>(R.id.spinnerApp)
        val cardAppNameDisplay = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardAppNameDisplay)
        val textAppIcon = dialogView.findViewById<TextView>(R.id.textAppIcon)
        val textAppNameDisplay = dialogView.findViewById<TextView>(R.id.textAppNameDisplay)
        val editHours = dialogView.findViewById<EditText>(R.id.editHours)
        val editMinutes = dialogView.findViewById<EditText>(R.id.editMinutes)
        val textTimeDisplay = dialogView.findViewById<TextView>(R.id.textTimeDisplay)
        val switchEnabled = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchEnabled)
        val btn30min = dialogView.findViewById<Button>(R.id.btn30min)
        val btn1hour = dialogView.findViewById<Button>(R.id.btn1hour)
        val btn2hours = dialogView.findViewById<Button>(R.id.btn2hours)
        val btn4hours = dialogView.findViewById<Button>(R.id.btn4hours)

        // Ocultar el spinner en modo edición y mostrar el nombre de la app
        spinnerApp.visibility = android.view.View.GONE
        cardAppNameDisplay.visibility = android.view.View.VISIBLE

        // Configurar el icono y nombre de la app
        if (timeLimit.packageName.isEmpty()) {
            textAppIcon.text = "🌐"
            textAppNameDisplay.text = timeLimit.appName
        } else {
            textAppIcon.text = "📱"
            textAppNameDisplay.text = timeLimit.appName
        }

        // Establecer valores actuales con formato 00:00
        val hours = timeLimit.dailyLimitMinutes / 60
        val minutes = timeLimit.dailyLimitMinutes % 60
        editHours.setText(formatTwoDigits(hours))
        editMinutes.setText(formatTwoDigits(minutes))
        textTimeDisplay.text = "${formatTwoDigits(hours)}:${formatTwoDigits(minutes)}"
        switchEnabled.isChecked = timeLimit.enabled

        // Función para actualizar el display de tiempo
        val updateTimeDisplay = {
            val h = editHours.text.toString().toIntOrNull() ?: 0
            val m = editMinutes.text.toString().toIntOrNull() ?: 0
            textTimeDisplay.text = "${formatTwoDigits(h)}:${formatTwoDigits(m)}"
        }

        // Listeners para actualizar el display en tiempo real
        editHours.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTimeDisplay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        editMinutes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTimeDisplay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Configurar botones de sugerencias rápidas con formato
        btn30min.setOnClickListener {
            editHours.setText("00")
            editMinutes.setText("30")
        }
        btn1hour.setOnClickListener {
            editHours.setText("01")
            editMinutes.setText("00")
        }
        btn2hours.setOnClickListener {
            editHours.setText("02")
            editMinutes.setText("00")
        }
        btn4hours.setOnClickListener {
            editHours.setText("04")
            editMinutes.setText("00")
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Límite de Tiempo")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, which ->
                val hours = editHours.text.toString().toIntOrNull() ?: 0
                val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
                val totalMinutes = (hours * 60) + minutes
                val enabled = switchEnabled.isChecked

                if (totalMinutes <= 0) {
                    Toast.makeText(this, "⚠️ Por favor ingresa un tiempo válido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val uuid = childUuid ?: return@setPositiveButton
                dbUtils.setTimeLimit(
                    childUuid = uuid,
                    packageName = timeLimit.packageName,
                    appName = timeLimit.appName,
                    dailyLimitMinutes = totalMinutes,
                    enabled = enabled,
                    onSuccess = {
                        Toast.makeText(this, "✓ Límite actualizado correctamente", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("🗑️ Eliminar") { dialog, which ->
                showDeleteConfirmation(timeLimit)
            }
            .show()
    }

    private fun showDeleteConfirmation(timeLimit: TimeLimit) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Límite")
            .setMessage("¿Estás seguro de que deseas eliminar este límite de tiempo?")
            .setPositiveButton("Eliminar") { dialog, which ->
                val uuid = childUuid ?: return@setPositiveButton
                dbUtils.removeTimeLimit(
                    childUuid = uuid,
                    packageName = timeLimit.packageName,
                    onSuccess = {
                        Toast.makeText(this, "✓ Límite eliminado", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// Adapter para RecyclerView
class TimeLimitsAdapter(
    private val timeLimits: List<TimeLimit>,
    private val onItemClick: (TimeLimit) -> Unit
) : RecyclerView.Adapter<TimeLimitsAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textAppName: TextView = view.findViewById(R.id.textAppName)
        val textAppIcon: TextView = view.findViewById(R.id.textAppIcon)
        val textLimit: TextView = view.findViewById(R.id.textLimit)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeLimit = timeLimits[position]

        // Configurar icono y nombre
        if (timeLimit.packageName.isEmpty()) {
            holder.textAppIcon.text = "🌐"
            holder.textAppName.text = timeLimit.appName
        } else {
            holder.textAppIcon.text = "📱"
            holder.textAppName.text = timeLimit.appName
        }

        val hours = timeLimit.dailyLimitMinutes / 60
        val minutes = timeLimit.dailyLimitMinutes % 60
        holder.textLimit.text = if (hours > 0 && minutes > 0) {
            "${hours}h ${minutes}m diarios"
        } else if (hours > 0) {
            "${hours}h diarios"
        } else {
            "${minutes}m diarios"
        }

        // Configurar estado con colores
        if (timeLimit.enabled) {
            holder.textStatus.text = "✓ Activo"
            holder.textStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            holder.textStatus.text = "✗ Inactivo"
            holder.textStatus.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
        }

        holder.itemView.setOnClickListener {
            onItemClick(timeLimit)
        }
    }

    override fun getItemCount() = timeLimits.size
}

// Función para formatear números a dos dígitos
fun formatTwoDigits(number: Int): String {
    return String.format("%02d", number)
}
