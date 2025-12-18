# Sistema de Gestión de Usuarios

## Implementación Completa

Se ha implementado un sistema de gestión de usuarios con almacenamiento en **SharedPreferences** (local) y **Firebase** (remoto), con verificación en cascada para evitar llamadas innecesarias a la base de datos.

## Flujo de Verificación (Cascada)

```
┌─────────────────────────────┐
│  Usuario inicia sesión      │
│  (Google Sign-In)           │
└──────────┬──────────────────┘
           │
           v
┌─────────────────────────────┐
│ 1. ¿Existe en               │
│    SharedPreferences?       │
└──────────┬──────────────────┘
           │
      ¿Sí?─┴─No
       │        │
       v        v
   ✅ FIN   ┌─────────────────┐
            │ 2. Buscar en    │
            │    Firebase     │
            └────────┬────────┘
                     │
                ¿Existe?
                  │
             Sí──┴──No
              │      │
              v      v
        ┌────────┐  ┌────────┐
        │Guardar │  │Crear en│
        │en SP   │  │Firebase│
        └────────┘  └────┬───┘
                         │
                         v
                    ┌────────┐
                    │Guardar │
                    │en SP   │
                    └────────┘
```

## Colección en Firebase: `usuarios`

### Estructura del Documento

```javascript
usuarios/{uuid}
  ├── uuid: String          // UID de Firebase Auth
  ├── nombre: String        // Nombre del usuario
  └── lastLogin: Long       // Timestamp del último inicio de sesión
```

### Ejemplo:

```json
{
  "uuid": "aB3dF9kL2mN5pQ8",
  "nombre": "Juan Pérez",
  "lastLogin": 1702910345000
}
```

## SharedPreferences

### Archivo: `preferences`

| Clave | Tipo | Descripción |
|-------|------|-------------|
| `uuid` | String | UID del usuario autenticado |
| `nombre` | String | Nombre del usuario |

## Métodos en DataBaseUtils

### 1. `saveUser()`
Guarda o actualiza un usuario en Firebase.

```kotlin
dbUtils.saveUser(
    uuid = "aB3dF9kL2mN5pQ8",
    nombre = "Juan Pérez",
    onSuccess = { 
        Log.d("TAG", "Usuario guardado") 
    },
    onError = { error -> 
        Log.e("TAG", "Error: $error") 
    }
)
```

### 2. `getUser()`
Obtiene un usuario de Firebase por UUID.

```kotlin
dbUtils.getUser(
    uuid = "aB3dF9kL2mN5pQ8",
    onSuccess = { nombre ->
        Log.d("TAG", "Usuario encontrado: $nombre")
    },
    onError = { error ->
        Log.e("TAG", "Error: $error")
    }
)
```

## Método en MainActivity: `checkAndSaveUser()`

### ¿Cuándo se ejecuta?

1. **onResume()**: Si hay un usuario autenticado
2. **firebaseAuthWithGoogle()**: Después de un login exitoso

### Flujo de ejecución:

```kotlin
1. Obtener usuario actual (Firebase Auth)
2. Verificar SharedPreferences
   └─ Si existe ➜ FIN
3. Buscar en Firebase
   ├─ Si existe ➜ Guardar en SharedPreferences ➜ FIN
   └─ Si NO existe ➜ Crear en Firebase ➜ Guardar en SharedPreferences ➜ FIN
```

### Logs esperados:

#### ✅ Usuario ya en SharedPreferences (caso más frecuente):
```
👤 Verificando usuario en sistema...
UUID: aB3dF9kL2mN5pQ8
Nombre: Juan Pérez
✅ Usuario encontrado en SharedPreferences
UUID: aB3dF9kL2mN5pQ8
Nombre: Juan Pérez
```

#### ⚠️ Usuario en Firebase, no en SharedPreferences:
```
👤 Verificando usuario en sistema...
UUID: aB3dF9kL2mN5pQ8
Nombre: Juan Pérez
⚠️ Usuario no encontrado en SharedPreferences
Buscando en Firebase...
✅ Usuario encontrado en Firebase: Juan Pérez
✅ Usuario guardado en SharedPreferences
```

#### 🆕 Usuario nuevo (primera vez):
```
👤 Verificando usuario en sistema...
UUID: aB3dF9kL2mN5pQ8
Nombre: Juan Pérez
⚠️ Usuario no encontrado en SharedPreferences
Buscando en Firebase...
⚠️ Usuario no encontrado en Firebase: Usuario no encontrado en Firebase
Creando nuevo usuario en Firebase...
✅ Usuario creado en Firebase
✅ Usuario guardado en SharedPreferences
```

## Ventajas del Sistema

### ✅ Eficiencia
- **Primera verificación**: SharedPreferences (local, instantáneo)
- Solo consulta Firebase si no existe localmente
- Evita llamadas innecesarias a la base de datos

### ✅ Persistencia
- **Local**: SharedPreferences sobrevive al cierre de la app
- **Remoto**: Firebase permite acceso desde múltiples dispositivos

### ✅ Sincronización
- Si el usuario borra caché: Se recupera desde Firebase
- Si es primera vez: Se crea en Firebase automáticamente

### ✅ Trazabilidad
- Campo `lastLogin` para auditoría
- Logs detallados en cada paso del proceso

## Casos de Uso

### 1. Usuario Frecuente
- ✅ Verifica SharedPreferences
- ✅ Encuentra datos
- ⏱️ **Tiempo: < 1ms**
- 📡 **Llamadas a Firebase: 0**

### 2. Usuario con Caché Borrada
- ✅ Verifica SharedPreferences (vacío)
- ✅ Consulta Firebase
- ✅ Encuentra datos
- ✅ Guarda en SharedPreferences
- ⏱️ **Tiempo: 100-300ms**
- 📡 **Llamadas a Firebase: 1**

### 3. Usuario Nuevo
- ✅ Verifica SharedPreferences (vacío)
- ✅ Consulta Firebase (no existe)
- ✅ Crea usuario en Firebase
- ✅ Guarda en SharedPreferences
- ⏱️ **Tiempo: 200-500ms**
- 📡 **Llamadas a Firebase: 2** (lectura + escritura)

## Integración con el Flujo de Login

```
┌──────────────────┐
│ MainActivity     │
│ onCreate()       │
└────────┬─────────┘
         │
         v
┌──────────────────┐     No autenticado
│ ¿Usuario Auth?   ├─────────────────┐
└────────┬─────────┘                 │
         │ Sí                        v
         v                    ┌──────────────┐
┌──────────────────┐          │ Google       │
│ onResume()       │          │ Sign-In      │
│ checkAndSaveUser()│◄─────────┤ Exitoso      │
└──────────────────┘          └──────────────┘
         │
         v
┌──────────────────┐
│ Verificación     │
│ en cascada       │
└──────────────────┘
```

## Resumen

Este sistema asegura que:
1. ✅ **El usuario se guarda automáticamente** al iniciar sesión
2. ✅ **Se evitan llamadas innecesarias** a Firebase gracias a SharedPreferences
3. ✅ **Los datos persisten** localmente entre sesiones
4. ✅ **Se sincronizan** con Firebase cuando es necesario
5. ✅ **Se recuperan** automáticamente si se borra la caché local

