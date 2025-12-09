# 📱 Configuración para Fabricantes Específicos

## ⚠️ Fabricantes Problemáticos

Los siguientes fabricantes tienen optimizaciones de batería agresivas que pueden impedir que la app funcione correctamente:

- **Xiaomi / Redmi / POCO**
- **Oppo / Realme / OnePlus**
- **Vivo / iQOO**
- **Huawei / Honor**
- **Samsung** (algunas versiones)
- **Asus**
- **Letv / Coolpad**

## 🔧 Configuración Necesaria

### **OPPO / Realme / OnePlus (ColorOS / OxygenOS)**

#### Método 1: Auto-inicio
1. Ve a **Configuración** → **Administrador de aplicaciones**
2. Busca **Control Parental**
3. Activa **"Inicio automático"** o **"Auto-inicio"**

#### Método 2: Optimización de batería
1. Ve a **Configuración** → **Batería** → **Optimización de batería**
2. Busca **Control Parental**
3. Selecciona **"No optimizar"**

#### Método 3: Restricciones en segundo plano
1. Ve a **Configuración** → **Administrador de aplicaciones** → **Control Parental**
2. Toca en **"Uso de batería"**
3. Selecciona **"No restringir"** o **"Permitir en segundo plano"**

#### Método 4: Bloqueo en pantalla de recientes (IMPORTANTE)
1. Abre la app **Control Parental**
2. Ve a **Recientes** (botón cuadrado/multitarea)
3. **Arrastra hacia abajo** la tarjeta de Control Parental
4. Toca el **icono de candado** 🔒
   - Esto evita que el sistema mate la app automáticamente

---

### **Xiaomi / Redmi / POCO (MIUI)**

#### Método 1: Auto-inicio
1. Ve a **Configuración** → **Aplicaciones** → **Administrar aplicaciones**
2. Busca **Control Parental**
3. Toca en **"Auto-inicio"**
4. Activa el interruptor

#### Método 2: Ahorro de energía
1. Ve a **Configuración** → **Aplicaciones** → **Administrar aplicaciones**
2. Busca **Control Parental**
3. Toca en **"Ahorro de energía"**
4. Selecciona **"Sin restricciones"**

#### Método 3: Optimización de batería
1. Ve a **Configuración** → **Batería y rendimiento** → **Batería**
2. Toca en **"Optimización de batería"** (icono de engranaje)
3. Selecciona **"Todas las apps"**
4. Busca **Control Parental** y selecciona **"No optimizar"**

#### Método 4: Bloqueo en recientes
1. Abre **Control Parental**
2. Ve a **Recientes**
3. **Arrastra hacia abajo** la tarjeta
4. Toca el **icono de candado** 🔒

---

### **Huawei / Honor (EMUI / Magic UI)**

#### Método 1: Inicio automático
1. Ve a **Configuración** → **Aplicaciones** → **Inicio automático**
2. Busca **Control Parental**
3. Activa el interruptor

#### Método 2: Aplicaciones protegidas
1. Ve a **Administrador de teléfono** → **Aplicaciones protegidas**
2. Activa **Control Parental**

#### Método 3: Optimización de batería
1. Ve a **Configuración** → **Batería** → **Iniciar aplicaciones**
2. Busca **Control Parental**
3. Desactiva **"Administrar automáticamente"**
4. Activa:
   - ✅ Inicio automático
   - ✅ Inicio secundario
   - ✅ Ejecutar en segundo plano

---

### **Vivo / iQOO (Funtouch OS / Origin OS)**

#### Método 1: Auto-inicio
1. Ve a **i Manager** → **Inicio de aplicaciones**
2. O **Configuración** → **Administrador de aplicaciones** → **Permisos**
3. Busca **Control Parental**
4. Activa **"Auto-inicio"**

#### Método 2: Uso de batería
1. Ve a **Configuración** → **Batería**
2. Toca en **"Uso de batería en segundo plano"**
3. Busca **Control Parental**
4. Selecciona **"Alto consumo de fondo"** o **"Permitir actividad en segundo plano"**

---

### **Samsung (One UI)**

#### Método 1: Optimización de batería
1. Ve a **Configuración** → **Aplicaciones** → **Control Parental**
2. Toca en **"Batería"**
3. Selecciona **"Sin restricciones"**

#### Método 2: Aplicaciones en suspensión
1. Ve a **Configuración** → **Cuidado de la batería**
2. Asegúrate de que **Control Parental** NO esté en:
   - Aplicaciones inactivas
   - Aplicaciones en suspensión
   - Aplicaciones en suspensión profunda

---

## 🧪 Cómo Verificar que Funciona

### Después de configurar:

1. **Reinicia el dispositivo**
2. **NO abras la app manualmente**
3. **Espera 1 minuto**
4. **Verifica los logs con Logcat:**
   ```
   adb logcat | grep -E "BootReceiver|AppUsageMonitor|StartupWorker"
   ```

### Logs esperados:
```
BootReceiver: 🔔 BOOTRECEIVER ACTIVADO!!!
BootReceiver: ✅ UUID encontrado: xxxxx
BootReceiver: ✅ AppUsageMonitorService iniciado
AppUsageMonitor: ⭐ SERVICIO INICIADO DESDE BOOTRECEIVER ⭐
```

O si el BootReceiver falló pero WorkManager funcionó:
```
StartupWorker: 🚀 StartupWorker ejecutándose...
AppUsageMonitor: ⭐ SERVICIO INICIADO DESDE WORKMANAGER ⭐
```

---

## 🆘 Si Aún No Funciona

### Solución 1: Fuerza el inicio desde la app
La app está configurada para verificar y reiniciar los servicios cada 15 minutos automáticamente.

### Solución 2: Verifica permisos adicionales
Algunos dispositivos requieren:
- ✅ Permiso de "Mostrar sobre otras apps"
- ✅ Permiso de "Uso de datos"
- ✅ Permiso de "Administrador del dispositivo"

### Solución 3: Desactiva el ahorro de batería adaptativo
En algunos dispositivos, hay un "Ahorro de batería adaptativo" o "AI Battery" que aprende qué apps usar. Desactívalo.

---

## 📊 Estadísticas de Compatibilidad

| Fabricante | BootReceiver | WorkManager | Solución |
|-----------|--------------|-------------|----------|
| Realme    | ❌ Bloqueado | ✅ Funciona | Configurar auto-inicio + bloquear en recientes |
| Oppo      | ❌ Bloqueado | ✅ Funciona | Configurar auto-inicio + desactivar optimización |
| Xiaomi    | ⚠️ A veces   | ✅ Funciona | Auto-inicio + sin restricciones de batería |
| Huawei    | ❌ Bloqueado | ✅ Funciona | Aplicaciones protegidas + inicio automático |
| Samsung   | ✅ Funciona  | ✅ Funciona | Solo desactivar optimización de batería |
| Google    | ✅ Funciona  | ✅ Funciona | Sin configuración adicional |
| Emulador  | ✅ Funciona  | ✅ Funciona | Sin configuración adicional |

---

## 💡 Consejo Final

**En dispositivos Oppo/Realme/OnePlus**, el paso más importante es:
1. **Bloquear la app en recientes** (candado 🔒)
2. **Activar auto-inicio**
3. **Desactivar optimización de batería**

Sin el candado en recientes, el sistema matará la app muy agresivamente.

