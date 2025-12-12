package es.mrp.controlparental.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import es.mrp.controlparental.databinding.AppsItemBinding
import es.mrp.controlparental.models.AppPackageClass
import es.mrp.controlparental.utils.formatTime

class AppListAdapter(
    private val apps: List<AppPackageClass>,
    private var blockedApps: Set<String> = emptySet(),
    private val onBlockToggle: (AppPackageClass, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(private val binding: AppsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppPackageClass, isBlocked: Boolean, onBlockToggle: (AppPackageClass, Boolean) -> Unit) {
            binding.textAppName.text = app.appName
            binding.textUsageTime.text = formatTime(app.time)

            // Cambiar el color de fondo del CardView si la app está bloqueada
            if (isBlocked) {
                // Color rojo suave para apps bloqueadas
                binding.root.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.textAppName.setTextColor(Color.parseColor("#C62828"))
            } else {
                // Color normal (blanco o del tema)
                binding.root.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                binding.textAppName.setTextColor(Color.parseColor("#212121"))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = AppsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        val isBlocked = blockedApps.contains(app.packageName)
        holder.bind(app, isBlocked, onBlockToggle)
    }

    override fun getItemCount(): Int = apps.size

    /**
     * Actualiza la lista de apps bloqueadas y notifica al adaptador para refrescar la UI
     */
    fun updateBlockedApps(newBlockedApps: Set<String>) {
        blockedApps = newBlockedApps
        notifyDataSetChanged()
    }
}
