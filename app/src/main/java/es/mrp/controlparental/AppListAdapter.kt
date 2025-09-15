package es.mrp.controlparental

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import es.mrp.controlparental.databinding.AppsItemBinding

class AppListAdapter(
    private val apps: List<AppPackageClass>,
    private val onBlockToggle: (AppPackageClass, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(private val binding: AppsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppPackageClass, onBlockToggle: (AppPackageClass, Boolean) -> Unit) {
            app.blocked = blockedApps.contains(app.packageName)
            binding.textAppName.text = app.appName
            binding.textPackageName.text = app.packageName
            binding.textUsageTime.text = formatTime(app.time)
            binding.switchBlocked.isChecked = app.blocked

            binding.switchBlocked.setOnCheckedChangeListener { _, isChecked ->
                app.blocked = isChecked
                if (!isChecked)  blockedApps.remove(app.packageName) else blockedApps.add(app.packageName)
                onBlockToggle(app, isChecked)
            }
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
