package es.mrp.controlparental

/**
 * Clase de datos que representa la información de uso de apps de un hijo
 */
data class ChildUsageData(
    val childUuid: String,
    val childName: String = "Hijo",
    val timestamp: Long = 0,
    val apps: List<AppUsageInfo> = emptyList()
)

/**
 * Información de uso de una aplicación específica
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val timeInForeground: Long,
    val lastTimeUsed: Long
)

