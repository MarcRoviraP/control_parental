# Solución: Polling de Apps Bloqueadas

## Problema Detectado

Cuando la pantalla está apagada durante muchas horas (3+ horas), el dispositivo Android entra en modo **Doze** (ahorro de energía profundo), lo que puede suspender las conexiones de red en segundo plano, incluyendo los **listeners en tiempo real de Firebase**.

Aunque el `WakeLock` mantiene la CPU activa, las conexiones de red pueden quedar suspendidas, lo que impide que el listener de Firebase detecte cambios en las apps bloqueadas.

## Estrategia Implementada: Doble Mecanismo

### 1. Listener en Tiempo Real (Método Principal)
```kotlin
private fun startListeningToBlockedApps() {
    dbUtils.listenToBlockedAppsFromUsage(uuid) { blockedPackages ->
        blockedApps.clear()
        blockedApps.addAll(blockedPackages)
        Log.d(TAG, "📝 Apps bloqueadas actualizadas (listener): ${blockedApps.size}")
    }
}
```
- **Ventaja**: Actualización instantánea cuando Firebase está conectado
- **Limitación**: Se suspende en modo Doze

### 2. Polling Periódico (Método de Respaldo)
```kotlin
private fun checkBlockedAppsFromFirebase() {
    dbUtils.getChildAppUsage(uuid) { usageData ->
        val blockedAppsField = usageData["blockedApps"] as? List<String>
        if (blockedAppsField != null) {
            blockedApps.clear()
            blockedApps.addAll(blockedAppsField)
            Log.d(TAG, "📝 Apps bloqueadas actualizadas (polling): ${blockedApps.size}")
            // Verificar inmediatamente si la app actual debe bloquearse
            handler.post { checkForegroundAppWithFallback() }
        }
    }
}
```
- **Frecuencia**: Cada 30 segundos
- **Ventaja**: Funciona incluso si Firebase se desconecta
- **Función**: Consulta activa de la base de datos

## Frecuencias de Verificación

| Tarea | Intervalo | Propósito |
|-------|-----------|-----------|
| **Verificación de app en foreground** | 500ms | Detectar si la app actual debe bloquearse |
| **Actualización de uso (UsageStatsManager)** | 60 segundos | Actualizar tiempos de uso y subir a Firebase |
| **Actualización de foreground con UsageEvents** | 10 segundos | Detectar cambios MOVE_TO_FOREGROUND/BACKGROUND |
| **Polling de apps bloqueadas** | 30 segundos | Consultar Firebase directamente (backup) |
| **Listener de apps bloqueadas** | Tiempo real | Actualización instantánea vía Firebase |

## Cómo Funciona en tu Caso

1. **Pantalla apagada 3 horas** → Android entra en modo Doze
2. **Padre bloquea Temu** → Se actualiza en Firebase
3. **Listener suspendido** → No detecta el cambio inmediatamente
4. **Polling a los 30 segundos** → Consulta Firebase y detecta que Temu está bloqueada
5. **Bloqueo inmediato** → Llama a `checkForegroundAppWithFallback()` y bloquea la app

## Logs Esperados

### Listener en tiempo real (cuando funciona):
```
📝 Apps bloqueadas actualizadas (listener): 3
```

### Polling periódico (cuando listener no funciona):
```
📝 Apps bloqueadas actualizadas (polling): 3
```

## Resultado

Ahora el servicio verifica las apps bloqueadas de **dos formas simultáneas**:
- ✅ **Instantáneo**: Listener en tiempo real (cuando Firebase está conectado)
- ✅ **Polling cada 30s**: Consulta directa (funciona siempre, incluso en Doze)

**Máximo tiempo de retraso**: 30 segundos para detectar una app recién bloqueada cuando el dispositivo está en modo Doze profundo.

