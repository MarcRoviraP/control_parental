package es.mrp.controlparental

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptador para mostrar la lista de hijos y su uso de apps
 */
class ChildUsageAdapter : RecyclerView.Adapter<ChildUsageAdapter.ChildViewHolder>() {

    private val childrenList = mutableListOf<ChildUsageData>()

    // Callback para abrir la activity de gestión de apps bloqueadas
    var onManageBlockedAppsClick: ((childUuid: String) -> Unit)? = null

    // Callback para abrir la activity de gestión de límites de tiempo
    var onManageTimeLimitsClick: ((childUuid: String) -> Unit)? = null

    class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val childNameTextView: TextView = itemView.findViewById(R.id.childNameTextView)
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

        // Configurar información del hijo
        holder.childNameTextView.text = "Hijo ${position + 1}"
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
    fun updateChildData(childUuid: String, apps: List<AppUsageInfo>, timestamp: Long) {
        val existingIndex = childrenList.indexOfFirst { it.childUuid == childUuid }

        val childData = ChildUsageData(
            childUuid = childUuid,
            timestamp = timestamp,
            apps = apps.sortedByDescending { it.timeInForeground }.take(10) // Top 10 apps
        )

        if (existingIndex != -1) {
            // Actualizar datos existentes
            val oldData = childrenList[existingIndex]
            childrenList[existingIndex] = childData

            // Solo notificar cambio si realmente cambió el timestamp o las apps
            if (oldData.timestamp != childData.timestamp || oldData.apps != childData.apps) {
                notifyItemChanged(existingIndex, childData) // Usar payload para evitar rebindeo completo
            }
        } else {
            // Agregar nuevo hijo
            childrenList.add(childData)
            notifyItemInserted(childrenList.size - 1)
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
