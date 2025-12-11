package es.mrp.controlparental.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import es.mrp.controlparental.R
import es.mrp.controlparental.activities.AppItem

/**
 * Adapter para mostrar la lista de apps instaladas con switches para bloquear/desbloquear
 */
class InstalledAppsAdapter(
    private val onToggleBlock: (packageName: String, appName: String, isCurrentlyBlocked: Boolean) -> Unit
) : ListAdapter<AppItem, InstalledAppsAdapter.AppViewHolder>(AppDiffCallback()) {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        val blockSwitch: SwitchCompat = itemView.findViewById(R.id.blockSwitch)
        val packageNameTextView: TextView = itemView.findViewById(R.id.packageNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appItem = getItem(position)

        holder.appNameTextView.text = appItem.appName
        holder.packageNameTextView.text = appItem.packageName

        // Configurar el switch sin triggear el listener
        holder.blockSwitch.setOnCheckedChangeListener(null)
        holder.blockSwitch.isChecked = appItem.isBlocked

        // Configurar listener después de setear el valor
        holder.blockSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Si el estado cambió, notificar
            if (isChecked != appItem.isBlocked) {
                onToggleBlock(appItem.packageName, appItem.appName, appItem.isBlocked)
            }
        }

        // También permitir hacer click en toda la fila
        holder.itemView.setOnClickListener {
            holder.blockSwitch.isChecked = !holder.blockSwitch.isChecked
        }
    }
}

/**
 * DiffUtil para optimizar las actualizaciones del RecyclerView
 */
class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
    override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
        return oldItem == newItem
    }
}
