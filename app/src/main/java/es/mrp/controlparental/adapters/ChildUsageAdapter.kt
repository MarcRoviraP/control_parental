package es.mrp.controlparental.adapters

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import es.mrp.controlparental.R
import es.mrp.controlparental.models.AppUsageInfo
import es.mrp.controlparental.models.ChildUsageData
import es.mrp.controlparental.utils.DataBaseUtils

class ChildUsageAdapter(private val dbUtils: DataBaseUtils) : RecyclerView.Adapter<ChildUsageAdapter.ChildViewHolder>() {

    private val childrenList = mutableListOf<ChildUsageData>()

    // Callback para abrir la activity de gestión de apps bloqueadas
    var onManageBlockedAppsClick: ((childUuid: String) -> Unit)? = null

    // Callback para abrir la activity de gestión de límites de tiempo
    var onManageTimeLimitsClick: ((childUuid: String) -> Unit)? = null

    companion object {
        private const val PREFS_NAME = "child_aliases"
        private const val ALIAS_PREFIX = "alias_"

        /**
         * Guarda el alias de un hijo en SharedPreferences
         */
        fun saveChildAlias(context: Context, childUuid: String, alias: String) {
            val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                putString("${ALIAS_PREFIX}$childUuid", alias)
                apply()
            }
        }

        /**
         * Obtiene el alias de un hijo desde SharedPreferences
         * @return El alias guardado o null si no existe
         */
        fun getChildAlias(context: Context, childUuid: String): String? {
            val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return sharedPref.getString("${ALIAS_PREFIX}$childUuid", null)
        }

        /**
         * Borra el alias de un hijo
         */
        fun removeChildAlias(context: Context, childUuid: String) {
            val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                remove("${ALIAS_PREFIX}$childUuid")
                apply()
            }
        }
    }

    class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val childNameTextView: TextView = itemView.findViewById(R.id.childNameTextView)
        val editNameIcon: ImageView = itemView.findViewById(R.id.editNameIcon)
        val totalUsageTimeTextView: TextView = itemView.findViewById(R.id.totalUsageTimeTextView)
        val lastUpdateTextView: TextView = itemView.findViewById(R.id.lastUpdateTextView)
        val appsCountTextView: TextView = itemView.findViewById(R.id.appsCountTextView)
        val appsRecyclerView: RecyclerView = itemView.findViewById(R.id.appsRecyclerView)
        val manageAppsButton: Button = itemView.findViewById(R.id.manageAppsButton)
        val manageTimeLimitsButton: Button = itemView.findViewById(R.id.manageTimeLimitsButton)

        // Runnable que actualiza la vista de tiempo cada segundo
        private var timeUpdater: Runnable? = null
        private var currentTimestamp: Long = 0

        fun startUpdatingTime(timestamp: Long) {
            // Si el timestamp no ha cambiado, no reiniciar el updater
            if (currentTimestamp == timestamp && timeUpdater != null) {
                return
            }

            currentTimestamp = timestamp

            // Asegurar que no haya otro runnable corriendo
            stopUpdatingTime()

            // Actualizar el texto inmediatamente
            fun buildText(): String {
                val timeAgo = System.currentTimeMillis() - currentTimestamp
                val secondsAgo = timeAgo / 1000
                return when {
                    secondsAgo < 60 -> "Hace ${secondsAgo}s"
                    secondsAgo < 3600 -> "Hace ${secondsAgo / 60}m"
                    secondsAgo < 86400 -> "Hace ${secondsAgo / 3600}h"
                    else -> "Hace ${secondsAgo / 86400}d"
                }
            }

            // Aplicar texto inicial
            lastUpdateTextView.text = buildText()

            timeUpdater = object : Runnable {
                override fun run() {
                    val newText = buildText()
                    // Sólo setText si ha cambiado el texto mostrado (reduce relayouts)
                    if (lastUpdateTextView.text.toString() != newText) {
                        lastUpdateTextView.text = newText
                    }
                    // Volver a postear dentro de 1s
                    itemView.postDelayed(this, 1000)
                }
            }

            // Lanzar el updater después de 1 segundo
            itemView.postDelayed(timeUpdater, 1000)
        }

        fun stopUpdatingTime() {
            timeUpdater?.let {
                itemView.removeCallbacks(it)
                timeUpdater = null
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child_usage, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        
        val childData = childrenList[position]
        val context = holder.itemView.context

        // Priorizar alias sobre el nombre de Firebase
        val displayName = getChildAlias(context, childData.childUuid) ?: childData.childName
        holder.childNameTextView.text = displayName

        // Configurar click listener para editar el nombre
        val editClickListener = View.OnClickListener {
            showEditNameDialog(context, childData.childUuid, displayName)
        }
        holder.childNameTextView.setOnClickListener(editClickListener)
        holder.editNameIcon.setOnClickListener(editClickListener)

        holder.appsCountTextView.text = "${childData.apps.size} apps"

        // Iniciar actualización activa del tiempo desde última actualización
        holder.startUpdatingTime(childData.timestamp)

        // Configurar botón para gestionar apps bloqueadas
        holder.manageAppsButton.setOnClickListener {
            onManageBlockedAppsClick?.invoke(childData.childUuid)
        }

        // Configurar botón para gestionar límites de tiempo
        holder.manageTimeLimitsButton.setOnClickListener {
            onManageTimeLimitsClick?.invoke(childData.childUuid)
        }

        // Configurar RecyclerView de apps (solo si no tiene adapter o cambió el contenido)
        if (holder.appsRecyclerView.adapter == null) {
            holder.appsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
            val appsAdapter = AppUsageAdapter(childData.apps)

            holder.appsRecyclerView.adapter = appsAdapter
        } else {
            val appsAdapter = holder.appsRecyclerView.adapter as? AppUsageAdapter
            appsAdapter?.updateApps(childData.apps)
        }

        // Calcular y mostrar el tiempo total de uso
        val totalTimeInForeground = childData.apps.sumOf { it.timeInForeground }
        holder.totalUsageTimeTextView.text = "Tiempo total: ${formatTime(totalTimeInForeground)}"
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }

    /**
     * Muestra un diálogo para editar el alias del hijo
     */
    private fun showEditNameDialog(context: Context, childUuid: String, currentName: String) {
        val editText = EditText(context).apply {
            setText(currentName)
            hint = "Nombre del hijo"
            setPadding(50, 40, 50, 40)
            selectAll()
        }

        AlertDialog.Builder(context)
            .setTitle("Editar Nombre")
            .setMessage("Ingresa un alias para este hijo")
            .setView(editText)
            .setPositiveButton("Guardar") { dialog, _ ->
                val newAlias = editText.text.toString().trim()
                if (newAlias.isNotEmpty()) {
                    // Guardar el alias en SharedPreferences
                    saveChildAlias(context, childUuid, newAlias)

                    // Actualizar la vista
                    val position = childrenList.indexOfFirst { it.childUuid == childUuid }
                    if (position != -1) {
                        notifyItemChanged(position)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
            .setNeutralButton("Restaurar") { dialog, _ ->
                // Eliminar el alias y volver al nombre original
                removeChildAlias(context, childUuid)

                // Actualizar la vista
                val position = childrenList.indexOfFirst { it.childUuid == childUuid }
                if (position != -1) {
                    notifyItemChanged(position)
                }
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun getItemCount(): Int = childrenList.size

    override fun onViewRecycled(holder: ChildViewHolder) {
        // Parar el updater para evitar callbacks cuando la vista ya no está en pantalla
        holder.stopUpdatingTime()
        super.onViewRecycled(holder)
    }

    /**
     * Actualiza o agrega los datos de un hijo
     */
    fun updateChildData(childUuid: String, apps: List<AppUsageInfo>, timestamp: Long, childName: String? = null) {
        val existingIndex = childrenList.indexOfFirst { it.childUuid == childUuid }

        // Si no se proporciona nombre y ya existe, mantener el nombre anterior
        val finalChildName = childName ?: (childrenList.getOrNull(existingIndex)?.childName ?: "Cargando...")

        val childData = ChildUsageData(
            childUuid = childUuid,
            childName = finalChildName,
            timestamp = timestamp,
            apps = apps.sortedByDescending { it.timeInForeground }.take(10) // Top 10 apps
        )

        if (existingIndex != -1) {
            // Actualizar datos existentes
            val oldData = childrenList[existingIndex]
            childrenList[existingIndex] = childData

            // Solo notificar cambio si realmente cambió el timestamp, nombre o las apps
            if (oldData.timestamp != childData.timestamp ||
                oldData.childName != childData.childName ||
                oldData.apps != childData.apps) {
                notifyItemChanged(existingIndex, childData) // Usar payload para evitar rebindeo completo
            }
        } else {
            // Agregar nuevo hijo
            childrenList.add(childData)
            notifyItemInserted(childrenList.size - 1)

            // Si no se proporcionó nombre, obtenerlo de Firebase
            if (childName == null) {
                dbUtils.getUser(
                    uuid = childUuid,
                    onSuccess = { nombre ->
                        // Actualizar el nombre una vez obtenido
                        updateChildData(childUuid, apps, timestamp, nombre)
                    },
                    onError = {
                        // Si falla, dejar el nombre por defecto
                    }
                )
            }
        }
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // Rebindeo completo
            onBindViewHolder(holder, position)
        } else {
            // Actualización parcial con payload
            val childData = payloads[0] as ChildUsageData

            holder.appsCountTextView.text = "${childData.apps.size} apps"
            holder.startUpdatingTime(childData.timestamp)
            (holder.appsRecyclerView.adapter as? AppUsageAdapter)?.updateApps(childData.apps)

            // Calcular y mostrar el tiempo total de uso
            val totalTimeInForeground = childData.apps.sumOf { it.timeInForeground }
            holder.totalUsageTimeTextView.text = "Tiempo total: ${formatTime(totalTimeInForeground)}"
        }
    }

    /**
     * Limpia todos los datos
     */
    fun clearData() {
        // Al limpiar, asegurarse de que no queden runnables activos en ViewHolders visibles
        for (i in 0 until itemCount) {
            // No es trivial acceder a ViewHolder por índice desde aquí; dejar a RecyclerView gestionar recicled views.
        }
        childrenList.clear()
        notifyDataSetChanged()
    }
}
