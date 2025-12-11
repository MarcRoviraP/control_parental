package es.mrp.controlparental

data class TimeLimit(
    val packageName: String = "", // Si está vacío, es límite global
    val dailyLimitMinutes: Int = 0, // Límite diario en minutos
    val enabled: Boolean = true,
    val appName: String = "" // Nombre legible de la app
)

data class AppUsageTime(
    val packageName: String = "",
    val date: String = "", // formato: yyyy-MM-dd
    val usageTimeMillis: Long = 0
)

