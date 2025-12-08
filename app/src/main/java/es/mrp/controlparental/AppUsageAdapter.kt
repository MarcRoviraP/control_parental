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
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppUsageInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}

