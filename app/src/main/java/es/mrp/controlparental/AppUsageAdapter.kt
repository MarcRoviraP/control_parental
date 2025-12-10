package es.mrp.controlparental

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptador para mostrar las apps usadas por un hijo
 */
class AppUsageAdapter(
    private var apps: List<AppUsageInfo>
) : RecyclerView.Adapter<AppUsageAdapter.AppUsageViewHolder>() {

    // Callback para manejar clics largos en apps
    var onAppLongClickListener: ((packageName: String, appName: String) -> Unit)? = null

    class AppUsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appNameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        val appTimeTextView: TextView = itemView.findViewById(R.id.appTimeTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return AppUsageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        val app = apps[position]
        holder.appNameTextView.text = app.appName
        holder.appTimeTextView.text = formatTime(app.timeInForeground)

        // Configurar click largo para bloquear/desbloquear
        holder.itemView.setOnLongClickListener {
            onAppLongClickListener?.invoke(app.packageName, app.appName)
            true // Consumir el evento
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppUsageInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
