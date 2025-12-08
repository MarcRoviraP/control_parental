package es.mrp.controlparental

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptador para mostrar la lista de hijos y su uso de apps
 */
class ChildUsageAdapter : RecyclerView.Adapter<ChildUsageAdapter.ChildViewHolder>() {

    private val childrenList = mutableListOf<ChildUsageData>()

    class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val childNameTextView: TextView = itemView.findViewById(R.id.childNameTextView)
        val lastUpdateTextView: TextView = itemView.findViewById(R.id.lastUpdateTextView)
        val appsCountTextView: TextView = itemView.findViewById(R.id.appsCountTextView)
        val appsRecyclerView: RecyclerView = itemView.findViewById(R.id.appsRecyclerView)
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

        // Calcular tiempo desde última actualización
        val timeAgo = System.currentTimeMillis() - childData.timestamp
        val secondsAgo = timeAgo / 1000
        holder.lastUpdateTextView.text = when {
            secondsAgo < 60 -> "Hace ${secondsAgo}s"
            secondsAgo < 3600 -> "Hace ${secondsAgo / 60}m"
            else -> "Hace ${secondsAgo / 3600}h"
        }

        // Configurar RecyclerView de apps
        val appsAdapter = AppUsageAdapter(childData.apps)
        holder.appsRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.appsRecyclerView.adapter = appsAdapter
    }

    override fun getItemCount(): Int = childrenList.size

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
            childrenList[existingIndex] = childData
            notifyItemChanged(existingIndex)
        } else {
            // Agregar nuevo hijo
            childrenList.add(childData)
            notifyItemInserted(childrenList.size - 1)
        }
    }

    /**
     * Limpia todos los datos
     */
    fun clearData() {
        childrenList.clear()
        notifyDataSetChanged()
    }
}

