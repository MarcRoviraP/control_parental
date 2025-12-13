# 📊 Flujo de Subida de Datos - Control Parental

## Resumen Ejecutivo

Actualmente hay **3 lugares diferentes** que suben datos de uso a Firebase, lo cual puede causar conflictos y duplicación. Además, uno de ellos (ChildActivity) está **comentado y NO se ejecuta**.

---

## 🔍 Lugares Desde Donde SE SUBEN Datos

### 1. ✅ **AppUsageMonitorService** (ACTIVO - RECIÉN ARREGLADO)
**Archivo:** `services/AppUsageMonitorService.kt`

#### ¿Cuándo se ejecuta?
- Cada **30 segundos** en ciclo continuo
- Se inicia al:
  - Arrancar el dispositivo (BootReceiver)
  - Desde WorkManager (StartupWorker)
  - Después del login (MainActivity)

#### ¿Qué sube?
```kotlin
// Línea 321
dbUtils.uploadAppUsage(childUuid!!, usageData)
```
- **Apps con uso:** Top 20 apps más usadas del día (desde 00:00)
- **Apps instaladas:** Cada 5 minutos sube lista completa
- **Datos incluidos:**
  - packageName
  - appName
  - timeInForeground
  - lastTimeUsed
  - capturedAt (timestamp)
  - lastCaptureTime

#### Estado: ✅ **FUNCIONANDO CORRECTAMENTE AHORA**
- Acabo de eliminar el `return` que lo bloqueaba
- Ahora sí sube datos cada 30 segundos

---

### 2. ✅ **BlockService (AppBlockerOverlayService)** (ACTIVO)
**Archivo:** `services/BlockService.kt`

#### ¿Cuándo se ejecuta?
- Cada **60 segundos** (1 minuto)
- Dentro de la función `updateCurrentAppUsage()` → `uploadCurrentUsageToFirebase()`

#### ¿Qué sube?
```kotlin
// Línea 374
dbUtils.uploadAppUsage(uuid, usageData)
```
- **TODOS los datos de uso diario** almacenados en memoria local
- Incluye el tiempo acumulado de cada app
- **Reutiliza las claves existentes** (app_0, app_1, etc.) para evitar duplicados

#### Estado: ✅ **FUNCIONANDO**
- Este servicio sube datos basándose en su propio tracking interno
- Mantiene contadores en memoria (`dailyUsage` map)

---

### 3. ❌ **ChildActivity** (COMENTADO - NO SE EJECUTA)
**Archivo:** `activities/ChildActivity.kt`

#### ¿Cuándo se ejecutaría?
- Cada **60 segundos** (1 minuto)
- Solo cuando la Activity está visible

#### ¿Qué subiría?
```kotlin
// Línea 297
dbUtils.uploadAppUsage(childUuid, usageData)
```
- Top 20 apps más usadas del día

#### Estado: ❌ **COMENTADO Y NO SE USA**
```kotlin
// Línea 63 en onCreate():
// ⚠️ YA NO SUBIMOS DATOS AQUÍ - AppUsageMonitorService lo hace en background
// startPeriodicUsageUpload()  // ← ESTA LÍNEA ESTÁ COMENTADA
```

---

## ⚠️ PROBLEMA IDENTIFICADO: DUPLICACIÓN DE SUBIDAS

### Conflicto Actual

Tienes **2 servicios activos subiendo datos simultáneamente:**

```
AppUsageMonitorService          BlockService
   (cada 30s)                    (cada 60s)
       ↓                              ↓
   uploadAppUsage()             uploadAppUsage()
       ↓                              ↓
    FIREBASE (misma colección: appUsage)
```

### Consecuencias:

1. **Sobrescritura de datos:** Como ambos usan `SetOptions.merge()`, se mezclan los datos
2. **Consumo innecesario:** Dos servicios haciendo lo mismo
3. **Batería:** Gasto doble de recursos
4. **Sincronización:** Los datos pueden no estar sincronizados entre servicios

---

## 🎯 ¿Cuál Debería Ser el Responsable?

### Opción A: **Solo AppUsageMonitorService** (RECOMENDADO ✅)

**Ventajas:**
- ✅ Diseñado específicamente para monitorear uso
- ✅ Ya tiene toda la lógica de filtrado de apps del sistema
- ✅ Sube apps instaladas también
- ✅ Más frecuente (30s vs 60s)
- ✅ Funciona en background siempre

**Desventajas:**
- ❌ No tiene tracking en tiempo real de la app actual

**Acción requerida:**
- Eliminar la subida desde BlockService

### Opción B: **Solo BlockService** 

**Ventajas:**
- ✅ Tiene tracking en tiempo real de app actual
- ✅ Mantiene contadores en memoria más precisos
- ✅ Evita duplicación de claves (app_0, app_1)

**Desventajas:**
- ❌ No sube apps instaladas
- ❌ Menos frecuente (60s)
- ❌ Lógica de negocio mezclada (bloqueo + monitoreo)

**Acción requerida:**
- Eliminar la subida desde AppUsageMonitorService

### Opción C: **Híbrido** (COMPLEJO)

**BlockService:**
- Tracking en tiempo real
- Mantiene contadores en memoria

**AppUsageMonitorService:**
- Solo sube apps instaladas
- NO sube uso de apps

---

## 💡 MI RECOMENDACIÓN

### Mantener **AppUsageMonitorService** como único responsable

#### Razones:

1. **Separación de responsabilidades:**
   - `AppUsageMonitorService` → Monitorear y subir uso
   - `BlockService` → Solo bloquear apps (su función principal)

2. **Mejor arquitectura:**
   - Un servicio, una responsabilidad
   - Más fácil de mantener y debuggear

3. **Funcionalidad completa:**
   - Ya sube apps instaladas
   - Ya sube uso de apps
   - Ya filtra correctamente

4. **Frecuencia adecuada:**
   - 30 segundos es suficiente para monitoreo parental

#### Cambios necesarios:

1. ✅ **Mantener:** AppUsageMonitorService como está (ya lo arreglé)
2. ❌ **Eliminar:** La función `uploadCurrentUsageToFirebase()` de BlockService
3. ✅ **Verificar:** Que BlockService solo se encargue de bloquear apps

---

## 📝 Estado Actual de Cada Servicio

### AppUsageMonitorService
```
✅ Se inicia al boot
✅ Sube apps instaladas cada 5 min
✅ Sube uso de apps cada 30s (RECIÉN ARREGLADO)
✅ Filtra apps del sistema
✅ Logs detallados
```

### BlockService (AppBlockerOverlayService)
```
✅ Se inicia al boot
✅ Bloquea apps según configuración
✅ Monitorea tiempo límite
⚠️ TAMBIÉN sube uso cada 60s (DUPLICADO)
✅ Tracking en tiempo real
```

### ChildActivity
```
❌ NO sube datos (comentado)
✅ Solo muestra UI
✅ Escucha cambios de Firebase
```

---

## 🔧 Próximos Pasos Recomendados

1. **Decidir:** ¿Qué servicio debe ser el responsable único?
2. **Implementar:** Eliminar la subida duplicada del otro
3. **Probar:** Verificar que los datos lleguen correctamente
4. **Optimizar:** Ajustar frecuencia si es necesario

---

## 📊 Resumen Visual

```
ANTES (PROBLEMÁTICO):
┌─────────────────────────┐
│  ChildActivity (OFF)    │
└─────────────────────────┘

┌─────────────────────────┐     ┌──────────────┐
│ AppUsageMonitorService  │────→│   FIREBASE   │
│     (cada 30s)          │     │   appUsage   │
└─────────────────────────┘     └──────────────┘
                                       ↑
┌─────────────────────────┐           │
│   BlockService          │───────────┘
│     (cada 60s)          │  (CONFLICTO)
└─────────────────────────┘


DESPUÉS (RECOMENDADO):
┌─────────────────────────┐
│  ChildActivity (OFF)    │
└─────────────────────────┘

┌─────────────────────────┐     ┌──────────────┐
│ AppUsageMonitorService  │────→│   FIREBASE   │
│     (cada 30s)          │     │   appUsage   │
└─────────────────────────┘     └──────────────┘

┌─────────────────────────┐
│   BlockService          │
│  (solo bloqueo)         │
└─────────────────────────┘
```

---

## 🎯 Conclusión

**Actualmente tienes 2 servicios subiendo datos simultáneamente**, lo cual es ineficiente y puede causar conflictos. Te recomiendo mantener solo **AppUsageMonitorService** como responsable de subir datos de uso, y dejar que **BlockService** se enfoque únicamente en su tarea principal: bloquear aplicaciones.

¿Quieres que implemente esta recomendación?

