package es.mrp.controlparental.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import es.mrp.controlparental.databinding.AppsItemBinding
import es.mrp.controlparental.models.AppPackageClass
import es.mrp.controlparental.utils.blockedApps
import es.mrp.controlparental.utils.formatTime

class AppListAdapter(
    private val apps: List<AppPackageClass>,
    private val onBlockToggle: (AppPackageClass, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(private val binding: AppsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppPackageClass, onBlockToggle: (AppPackageClass, Boolean) -> Unit) {
            app.blocked = blockedApps.contains(app.packageName)
            binding.textAppName.text = app.appName
            binding.textUsageTime.text = formatTime(app.time)


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = AppsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position], onBlockToggle)
    }

    override fun getItemCount(): Int = apps.size
}
