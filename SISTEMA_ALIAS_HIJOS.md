# Sistema de Alias para Nombres de Hijos

## 🎯 Funcionalidad Implementada

Se ha implementado un sistema de **alias personalizables** para los nombres de los hijos en el `ChildUsageAdapter`. Los padres ahora pueden asignar nombres personalizados que se guardan en **SharedPreferences** local.

---

## ✨ Características

### 1. **Edición de Nombre**
- ✏️ Click en el nombre del hijo o en el icono de edición
- 💬 Diálogo con tres opciones:
  - **Guardar**: Establece un alias personalizado
  - **Cancelar**: Cierra sin cambios
  - **Restaurar**: Elimina el alias y muestra el nombre original de Firebase

### 2. **Almacenamiento Local**
- 💾 Los alias se guardan en **SharedPreferences**
- 📱 Persisten entre sesiones de la app
- 🔐 Cada hijo tiene su propio alias asociado a su UUID

### 3. **Prioridad de Nombres**
```
1º Alias personalizado (SharedPreferences)
    ↓ (si no existe)
2º Nombre de Firebase (usuarios/{uuid}/nombre)
    ↓ (si no existe)
3º "Cargando..." (temporal mientras se consulta Firebase)
```

---

## 📂 Estructura de Almacenamiento

### SharedPreferences: `child_aliases`

| Clave | Valor | Ejemplo |
|-------|-------|---------|
| `alias_{childUuid}` | Alias personalizado | "Mi hijo mayor" |
| `alias_{childUuid}` | Alias personalizado | "Pequeñín" |

### Ejemplo:
```xml
<string name="alias_aB3dF9kL2mN5pQ8">Mi hijo mayor</string>
<string name="alias_xY7zK4mL9pN2qR5">Pequeñín</string>
```

---

## 🎨 Cambios en la UI

### Layout: `item_child_usage.xml`

**Antes:**
```xml
<TextView
    android:id="@+id/childNameTextView"
    android:text="Hijo"
    android:textSize="18sp"/>
```

**Después:**
```xml
<LinearLayout orientation="horizontal">
    <TextView
        android:id="@+id/childNameTextView"
        android:text="Hijo"
        android:textSize="18sp"
        android:clickable="true"
        android:background="?attr/selectableItemBackground"/>
    
    <ImageView
        android:id="@+id/editNameIcon"
        android:src="@android:drawable/ic_menu_edit"
        android:layout_width="16dp"
        android:layout_height="16dp"/>
</LinearLayout>
```

- ✅ Efecto táctil al presionar el nombre
- ✅ Icono de lápiz para indicar que es editable

---

## 🔧 Métodos Añadidos al Adapter

### 1. `saveChildAlias()`
```kotlin
companion object {
    fun saveChildAlias(context: Context, childUuid: String, alias: String) {
        val sharedPref = context.getSharedPreferences("child_aliases", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("alias_$childUuid", alias)
            apply()
        }
    }
}
```

### 2. `getChildAlias()`
```kotlin
fun getChildAlias(context: Context, childUuid: String): String? {
    val sharedPref = context.getSharedPreferences("child_aliases", Context.MODE_PRIVATE)
    return sharedPref.getString("alias_$childUuid", null)
}
```

### 3. `removeChildAlias()`
```kotlin
fun removeChildAlias(context: Context, childUuid: String) {
    val sharedPref = context.getSharedPreferences("child_aliases", Context.MODE_PRIVATE)
    sharedPref.edit().apply {
        remove("alias_$childUuid")
        apply()
    }
}
```

### 4. `showEditNameDialog()`
```kotlin
private fun showEditNameDialog(context: Context, childUuid: String, currentName: String) {
    val editText = EditText(context).apply {
        setText(currentName)
        hint = "Nombre del hijo"
        selectAll()
    }

    AlertDialog.Builder(context)
        .setTitle("Editar Nombre")
        .setMessage("Ingresa un alias para este hijo")
        .setView(editText)
        .setPositiveButton("Guardar") { dialog, _ ->
            val newAlias = editText.text.toString().trim()
            if (newAlias.isNotEmpty()) {
                saveChildAlias(context, childUuid, newAlias)
                notifyItemChanged(position)
            }
        }
        .setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }
        .setNeutralButton("Restaurar") { dialog, _ ->
            removeChildAlias(context, childUuid)
            notifyItemChanged(position)
        }
        .show()
}
```

---

## 🔄 Flujo de Funcionamiento

```
┌──────────────────────────┐
│ Usuario hace click en    │
│ nombre o icono edición   │
└────────────┬─────────────┘
             │
             v
┌──────────────────────────┐
│ showEditNameDialog()     │
│ muestra diálogo          │
└────────────┬─────────────┘
             │
      ┌──────┴──────┐
      │             │
      v             v
┌──────────┐  ┌──────────┐
│ Guardar  │  │Restaurar │
└────┬─────┘  └────┬─────┘
     │             │
     v             v
┌─────────────┐ ┌────────────┐
│saveChildAlias│removeChild  │
│(SharedPref) │Alias (SP)   │
└────┬────────┘ └────┬───────┘
     │               │
     └───────┬───────┘
             v
    ┌────────────────┐
    │notifyItemChanged│
    │Actualiza UI    │
    └────────────────┘
```

---

## 📱 Experiencia de Usuario

### Escenario 1: Primera vez con hijo nuevo
```
1. Hijo aparece con nombre "Cargando..."
2. Firebase responde: "Juan Pérez"
3. Se muestra: "Juan Pérez"
4. Padre hace click → Edita → "Mi hijo mayor"
5. Se guarda en SharedPreferences
6. Desde ahora siempre muestra: "Mi hijo mayor"
```

### Escenario 2: Usuario con alias ya establecido
```
1. Hijo aparece inmediatamente con: "Mi hijo mayor"
   (Leído de SharedPreferences)
2. No consulta Firebase para el nombre
3. Si hace click → puede cambiar a "Juanito" o "Restaurar"
```

### Escenario 3: Restaurar nombre original
```
1. Se muestra: "Mi hijo mayor" (alias)
2. Padre hace click → "Restaurar"
3. Se elimina el alias de SharedPreferences
4. Vuelve a mostrar: "Juan Pérez" (nombre de Firebase)
```

---

## ✅ Ventajas del Sistema

| Ventaja | Descripción |
|---------|-------------|
| **🎨 Personalizable** | Cada padre puede usar nombres familiares |
| **💾 Persistente** | Los alias se mantienen entre sesiones |
| **⚡ Rápido** | No requiere consultas a Firebase |
| **🔄 Reversible** | Botón "Restaurar" para volver al nombre original |
| **👨‍👩‍👧‍👦 Múltiples hijos** | Cada hijo tiene su propio alias independiente |
| **🔐 Local** | Los alias no se sincronizan con Firebase (privacidad) |

---

## 🎭 Casos de Uso

### 👨‍👩‍👧‍👦 Familia con varios hijos
```
Firebase:           Alias Personalizado:
- Ana García    →   "La mayor"
- Luis García   →   "Luisito"
- María García  →   "La pequeña"
```

### 👴 Abuelos como supervisores
```
Firebase:           Alias Personalizado:
- Roberto López →   "Mi nieto"
- Laura López   →   "Mi nieta mayor"
```

### 🏫 Tutor legal
```
Firebase:           Alias Personalizado:
- Pablo Ruiz    →   "Pablo"
- Sofía Ruiz    →   "Sofía"
```

---

## 📊 Comparación

| Aspecto | Antes | Después |
|---------|-------|---------|
| **Nombre mostrado** | Siempre el de Firebase | Alias personalizable |
| **Edición** | No disponible | Click para editar |
| **Almacenamiento** | Solo Firebase | SharedPreferences + Firebase |
| **Personalización** | ❌ | ✅ |
| **Restauración** | - | ✅ Botón "Restaurar" |

---

## 🔍 Detalles Técnicos

### Imports añadidos
```kotlin
import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.ImageView
```

### Variables de ViewHolder actualizadas
```kotlin
class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val childNameTextView: TextView = itemView.findViewById(R.id.childNameTextView)
    val editNameIcon: ImageView = itemView.findViewById(R.id.editNameIcon) // ← Nuevo
    // ...
}
```

### onBindViewHolder actualizado
```kotlin
override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
    val childData = childrenList[position]
    val context = holder.itemView.context

    // Priorizar alias sobre el nombre de Firebase
    val displayName = getChildAlias(context, childData.childUuid) ?: childData.childName
    holder.childNameTextView.text = displayName

    // Configurar click listeners
    val editClickListener = View.OnClickListener {
        showEditNameDialog(context, childData.childUuid, displayName)
    }
    holder.childNameTextView.setOnClickListener(editClickListener)
    holder.editNameIcon.setOnClickListener(editClickListener)
    // ...
}
```

---

## ✅ Estado de Implementación

- ✅ Métodos de SharedPreferences implementados
- ✅ Layout actualizado con icono de edición
- ✅ Diálogo de edición funcional
- ✅ Sistema de prioridad: Alias → Firebase → "Cargando..."
- ✅ Botón "Restaurar" para eliminar alias
- ✅ Sin errores de compilación
- ✅ UI responsive con feedback táctil

**Implementación completada con éxito** 🎉

