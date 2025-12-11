# Sistema de Límites de Tiempo - Control Parental

## Resumen de Cambios

Se ha implementado un sistema completo de gestión de límites de tiempo de uso para aplicaciones y dispositivo móvil.

## Archivos Creados/Modificados

### 1. **TimeLimit.kt** (NUEVO)
Modelos de datos para límites de tiempo y uso diario:
- `TimeLimit`: Define límites de tiempo por app o globales
- `AppUsageTime`: Registra el tiempo de uso diario

### 2. **DataBaseUtils.kt** (MODIFICADO)
Se añadieron nuevas funciones para gestionar límites de tiempo:

**Nuevas colecciones en Firebase:**
- `timeLimits`: Almacena los límites configurados por el padre
- `dailyUsage`: Registra el uso diario de cada app

**Funciones añadidas:**
- `setTimeLimit()`: Establece un límite de tiempo para una app o global
- `removeTimeLimit()`: Elimina un límite de tiempo
- `listenToTimeLimits()`: Escucha cambios en límites en tiempo real
- `getTimeLimits()`: Obtiene los límites configurados
- `updateDailyUsage()`: Registra el uso de una app
- `getDailyUsage()`: Obtiene el uso diario de una app
- `listenToDailyUsage()`: Escucha cambios en el uso diario

### 3. **TimeLimitsActivity.kt** (NUEVO)
Activity para que el padre gestione los límites de tiempo:
- Lista todos los límites configurados
- Permite añadir límites para apps específicas o límite global
- Editar límites existentes
- Eliminar límites
- Activar/desactivar límites

### 4. **BlockService.kt** (MODIFICADO)
El servicio ahora también monitorea el tiempo de uso:

**Nuevas variables:**
- `timeLimits`: Mapa de límites por app
- `dailyUsage`: Uso acumulado del día por app
- `globalTimeLimit`: Límite global del dispositivo
- `globalDailyUsage`: Uso total del día
- `currentForegroundApp`: App actual en primer plano
- `foregroundAppStartTime`: Momento en que la app pasó a primer plano

**Nuevas funciones:**
- `startListeningToTimeLimits()`: Escucha los límites configurados
- `loadTodayUsage()`: Carga el uso del día actual
- `updateCurrentAppUsage()`: Actualiza el tiempo de uso cada minuto
- `trackAppChange()`: Rastrea cuando el usuario cambia de app
- `isTimeLimitExceeded()`: Verifica si se excedió el límite
- `getCurrentDate()`: Obtiene la fecha actual en formato yyyy-MM-dd

**Comportamiento:**
- Rastrea el tiempo que el usuario pasa en cada app
- Actualiza el uso cada 60 segundos en Firebase
- Bloquea automáticamente apps cuando se excede el límite
- Bloquea TODO el dispositivo si se excede el límite global

### 5. **Layouts XML** (NUEVOS)
- `activity_time_limits.xml`: Pantalla principal con lista de límites
- `dialog_time_limit.xml`: Diálogo para añadir/editar límites
- `item_time_limit.xml`: Item de la lista de límites

### 6. **AndroidManifest.xml** (MODIFICADO)
Se añadió la nueva Activity al manifest.

## Cómo Usar

### Para el Padre:

1. **Abrir la gestión de límites:**
```kotlin
// Desde ParentAccountActivity u otra activity del padre
val intent = Intent(this, TimeLimitsActivity::class.java)
intent.putExtra(TimeLimitsActivity.EXTRA_CHILD_UUID, childUuid)
startActivity(intent)
```

2. **Añadir un límite de tiempo:**
   - Pulsar el botón flotante (+)
   - Seleccionar "Límite Global del Dispositivo" o una app específica
   - Ingresar los minutos permitidos por día
   - Marcar si está habilitado
   - Guardar

3. **Editar un límite:**
   - Tocar el límite en la lista
   - Modificar los minutos o el estado (activo/desactivado)
   - Guardar o Eliminar

### Para el Hijo:

El sistema funciona automáticamente en el `BlockService`:
- Rastrea el tiempo de uso de cada app
- Cuando se excede el límite de una app, la bloquea
- Si hay límite global y se excede, bloquea todas las apps
- El overlay de bloqueo muestra el mensaje usual

## Estructura de Datos en Firebase

### Colección `timeLimits`:
```
timeLimits/{childUuid}_{packageName}/
{
  childUID: "uuid_del_hijo",
  packageName: "com.example.app",  // vacío para límite global
  appName: "Nombre de la App",
  dailyLimitMinutes: 60,
  enabled: true,
  updatedAt: timestamp
}
```

### Colección `dailyUsage`:
```
dailyUsage/{childUuid}_{packageName}_{date}/
{
  childUID: "uuid_del_hijo",
  packageName: "com.example.app",  // vacío para uso global
  date: "2025-12-11",
  usageTimeMillis: 3600000,  // 1 hora en milisegundos
  updatedAt: timestamp
}
```

## Características Destacadas

✅ **Límite Global del Dispositivo**: Bloquea todo el móvil después de X minutos de uso total
✅ **Límites por App**: Bloquea apps específicas cuando se excede su límite
✅ **Actualización en Tiempo Real**: Los cambios del padre se reflejan inmediatamente en el hijo
✅ **Persistencia Diaria**: Los límites se resetean cada día automáticamente
✅ **Monitoreo Preciso**: Actualiza el uso cada 60 segundos en Firebase
✅ **Interfaz Intuitiva**: Fácil de configurar para los padres

## Ejemplo de Integración en ParentAccountActivity

```kotlin
// Añadir un botón para gestionar límites de tiempo
btnTimeLimits.setOnClickListener {
    val intent = Intent(this, TimeLimitsActivity::class.java)
    intent.putExtra(TimeLimitsActivity.EXTRA_CHILD_UUID, selectedChildUuid)
    startActivity(intent)
}
```

## Logs para Debugging

El servicio genera logs útiles:
- `⏰ Límite global establecido: X minutos`
- `⏰ Límite para [App]: X minutos`
- `📊 Uso actualizado: [App] = Xmin, Global = Xmin`
- `⏰ Límite de [App] excedido: Xmin / Xmin`
- `⏰ Límite global excedido: Xmin / Xmin`

## Consideraciones

1. **Reinicio del Dispositivo**: Los contadores se reinician cada día (formato fecha yyyy-MM-dd)
2. **Actualización de Uso**: Se guarda cada 60 segundos para no sobrecargar Firebase
3. **Prioridad de Bloqueo**: Si hay límite global Y límite de app, se verifica el global primero
4. **Apps del Sistema**: No se cuentan en el límite global automáticamente (launcher, settings, etc.)

## Próximos Pasos Sugeridos

- [ ] Añadir notificaciones de advertencia cuando se acerque al límite
- [ ] Permitir horarios específicos (ej: 2h entre 14:00-18:00)
- [ ] Estadísticas semanales/mensuales de uso
- [ ] Bonificación de tiempo adicional por buenas acciones

