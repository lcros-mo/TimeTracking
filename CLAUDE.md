# Claude.md
# Time Tracking App - Grec Mallorca

## 📋 Descripción del Proyecto

Aplicación Android de gestión de fichajes (check-in/check-out) para el Grupo de Recuperación y Emergencias de Mallorca (GREC). La app permite:

- ✅ Fichaje de entrada/salida con un solo botón
- ✅ Visualización de tiempo trabajado (diario y semanal)
- ✅ Historial de fichajes con edición
- ✅ Exportación semanal a PDF con subida automática al servidor
- ✅ Autenticación con Google (Firebase)
- ✅ Soporte multiidioma (Español/Catalán)
- ✅ Base de datos local con Room
- ✅ Cálculo automático de horas extras
- ✅ Sistema de reintentos para exportaciones fallidas

**Package**: `com.timetracking.app`  
**Versión Actual**: 1.7.2 (versionCode: 24)  
**Estado**: ✅ **EN PRODUCCIÓN** - Actualmente siendo probada en administración antes del despliegue completo.

---

## 🏗️ Arquitectura

### Stack Tecnológico

- **Lenguaje**: Kotlin 1.9.0
- **SDK Target**: Android 34 (API 34)
- **Min SDK**: Android 24 (API 24)
- **Build System**: Gradle 8.7 con Kotlin DSL

### Librerías Principales

```kotlin
// Core Android
androidx.core:core-ktx:1.13.1
androidx.appcompat:appcompat:1.7.0
com.google.android.material:material:1.12.0

// Architecture Components
androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0
androidx.lifecycle:lifecycle-livedata-ktx:2.7.0
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0

// Room Database
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1

// Firebase Authentication
com.google.firebase:firebase-auth-ktx:22.0.0
com.google.android.gms:play-services-auth:21.2.0
com.google.android.libraries.identity.googleid:googleid:1.1.1
androidx.credentials:credentials:1.3.0

// PDF Generation
com.itextpdf:itext7-core:7.2.5

// Networking
com.squareup.okhttp3:okhttp:4.11.0
com.squareup.retrofit2:retrofit:2.9.0
com.squareup.retrofit2:converter-gson:2.9.0

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1
```

### Patrón de Arquitectura

**MVVM (Model-View-ViewModel)** con:
- Repository Pattern para abstracción de datos
- LiveData/StateFlow para comunicación reactiva
- Coroutines para operaciones asíncronas
- Service Locator para inyección manual de dependencias

---

## 📁 Estructura del Proyecto

```
app/src/main/java/com/timetracking/app/
│
├── 🎯 TimeTrackingApp.kt                    # Application class
│
├── core/
│   ├── data/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt              # Room Database (versión 3)
│   │   │   ├── Converters.kt               # Type converters para Date
│   │   │   └── TimeRecordDao.kt            # DAO para operaciones DB
│   │   │
│   │   ├── model/
│   │   │   ├── RecordType.kt               # Enum: CHECK_IN, CHECK_OUT
│   │   │   ├── TimeRecord.kt               # Entidad Room (id, date, type, note, exported)
│   │   │   └── TimeRecordBlock.kt          # Agrupación de entrada/salida
│   │   │
│   │   └── repository/
│   │       └── TimeRecordRepository.kt     # Abstracción de operaciones de datos
│   │
│   ├── di/
│   │   └── ServiceLocator.kt               # Inyección manual de dependencias
│   │
│   ├── network/
│   │   └── (sin implementar aún)
│   │
│   └── utils/
│       ├── DateTimeUtils.kt                # Utilidades para manejo de fechas
│       ├── LanguageUtils.kt                # Cambio dinámico de idioma
│       ├── PDFManager.kt                   # Generación y subida de PDFs
│       ├── TimeRecordValidator.kt          # Validación de lógica de fichajes
│       └── TrustAllCerts.kt                # SSL (TEMPORAL - para servidor desarrollo)
│
└── ui/
    ├── auth/
    │   └── LoginActivity.kt                # Pantalla de login con Google
    │
    ├── common/
    │   └── BaseDialog.kt                   # Clase base para diálogos
    │
    ├── history/
    │   ├── HistoryFragment.kt              # Pantalla de historial
    │   ├── HistoryViewModel.kt             # ViewModel del historial
    │   ├── AddRecordDialog.kt              # Diálogo para añadir fichajes manualmente
    │   ├── TimeEditBottomSheet.kt          # Edición de registros existentes
    │   └── adapter/
    │       └── TimeRecordBlockAdapter.kt   # Adapter para RecyclerView
    │
    └── home/
        ├── MainActivity.kt                 # Pantalla principal
        └── MainViewModel.kt                # ViewModel principal
```

---

## 🗄️ Base de Datos (Room)

### Entidad: TimeRecord

```kotlin
@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,              // Fecha/hora del registro
    val type: RecordType,        // CHECK_IN o CHECK_OUT
    val note: String? = null,    // Observaciones opcionales
    val exported: Boolean = false // Si ya se exportó a PDF
)
```

### Entidad: OvertimeBalance

```kotlin
@Entity(tableName = "overtime_balance")
data class OvertimeBalance(
    @PrimaryKey
    val id: Int = 1,              // Siempre 1 (single row table)
    val totalMinutes: Long = 0    // Balance acumulado en minutos
)
```

**Versión actual**: 3  
**Migraciones**:
- **1→2**: Se añadió el campo `exported` para controlar exportaciones
- **2→3**: Se añadió la tabla `overtime_balance` para tracking de horas extras

### Schema Actual

```sql
-- Tabla de registros
CREATE TABLE time_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    date INTEGER NOT NULL,
    type TEXT NOT NULL,
    note TEXT,
    exported INTEGER NOT NULL DEFAULT 0
)

-- Tabla de balance de horas extras (single row)
CREATE TABLE overtime_balance (
    id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
    totalMinutes INTEGER NOT NULL DEFAULT 0
)
```

---

## 🔨 Build Commands

### Desarrollo

```bash
# Build debug APK
./gradlew assembleDebug

# Instalar en dispositivo conectado
./gradlew installDebug

# Clean build
./gradlew clean

# Build + Install + Launch
./gradlew installDebug && adb shell am start -n com.timetracking.app/.ui.auth.LoginActivity

# Ver logs en tiempo real
adb logcat -s TimeTracking:* MainActivity:* HistoryViewModel:* PDFManager:*
```

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumentation tests (requiere dispositivo/emulador)
./gradlew connectedAndroidTest

# Run tests con reporte HTML
./gradlew test --info
# Ver reporte en: app/build/reports/tests/testDebugUnitTest/index.html
```

### Release

```bash
# Build release APK (requiere keystore configurado)
./gradlew assembleRelease

# Build release bundle para Play Store
./gradlew bundleRelease

# Ubicación del APK generado:
# app/build/outputs/apk/release/app-release.apk
```

**⚠️ Importante**: Los builds de release requieren configuración de keystore en `local.properties`

---

## 🔐 Autenticación

### Firebase Authentication

- **Método**: Google Sign-In
- **Client ID**: Configurado en `strings.xml` (default_web_client_id)
- **Flujo**:
    1. Usuario pulsa "Iniciar sesión con Google"
    2. Se abre el selector de cuentas de Google
    3. Se valida con Firebase
    4. Si es exitoso → MainActivity
    5. Si falla → Se muestra error

**Configuración**:
```xml
<!-- app/src/main/res/values/strings.xml -->
<string name="default_web_client_id">550314087094-f8rnbfhgro0sgv2c998im7dn2o5h5j7v.apps.googleusercontent.com</string>
```

**IMPORTANTE**: El archivo `google-services.json` está en `.gitignore` y debe ser solicitado al responsable del proyecto.

---

## 📤 Sistema de Exportación PDF

### Flujo de Exportación

1. **Generación del PDF**:
    - Usuario selecciona una semana en HistoryFragment
    - Al pulsar "Exportar Semana" → Confirmación con total de horas
    - `PDFManager.createAndUploadPDF()` genera el PDF

2. **Lógica de Combinación**:
    - ✅ **Si el archivo ya existe en el servidor**: Descarga → Extrae registros existentes → Combina con nuevos → Sube actualizado
    - ✅ **Si no existe**: Crea nuevo PDF → Sube

3. **Subida al Servidor**:
    - **URL**: `https://80.32.125.224:5000/upload`
    - **Protocolo**: HTTPS con certificado autofirmado (TrustAllCerts)
    - **Método**: POST multipart/form-data
    - **Reintentos**: Hasta 3 intentos con backoff exponencial (1s, 2s, 3s)
    - **Timeout**: 30 segundos por request

4. **Sistema de Pending Records** (Nuevo en v1.7+):
    - Si todos los reintentos fallan → Guarda indicador en `app-specific-dir/pdfs/pending/`
    - Indicador contiene: nombre de archivo + número de registros pendientes + timestamp
    - Guarda copia local del PDF en Downloads
    - **Próxima exportación exitosa**: Automáticamente carga e incluye pending records
    - Usuario puede ver estado de pendientes en la UI

5. **Marcado de Exportación**:
    - Una vez exitoso, marca `exported = true` en todos los registros de esa semana
    - Los registros exportados NO se pueden editar ni eliminar
    - Actualiza `overtime_balance` con el delta de la semana

6. **Cálculo de Horas Extras**:
    - Baseline semanal: **37.5 horas (2250 minutos)**
    - Overtime = Total minutos trabajados - 2250
    - Balance acumulado se persiste en tabla `overtime_balance`

### Formato del PDF

```
┌─────────────────────────────────────────────────────┐
│          [Nombre del usuario de Firebase]           │
│                                                       │
│        Actualizado: 02/10/2025                       │
│                                                       │
│ ┌─────┬────────┬────────┬────────┬──────────────┐ │
│ │Fecha│Entrada │Salida  │Duración│Observaciones │ │
│ ├─────┼────────┼────────┼────────┼──────────────┤ │
│ │dd/MM│HH:mm   │HH:mm   │Xh Ym   │texto opcional│ │
│ └─────┴────────┴────────┴────────┴──────────────┘ │
│                                                       │
│              Total: Xh Ym | Extras: +/-Xh Ym        │
└─────────────────────────────────────────────────────┘
```

### Limpieza de Observaciones

El campo de observaciones se limpia automáticamente para evitar incluir:
- Timestamps automáticos
- Fechas en cualquier formato
- Horas aisladas
- Texto "Pendiente"
- Solo números y espacios

**Función**: `cleanObservationsField()` en PDFManager.kt

### Nombre del archivo
- Patrón: `RegistroHorario_[Nombre_Usuario].pdf`
- Ejemplo: `RegistroHorario_Juan_Garcia.pdf`
- El nombre se genera desde Firebase displayName o email

---

## 🎨 UI/UX

### Colores del Tema

```xml
<!-- Primary -->
<color name="primary">#ea8179</color>

<!-- Botones de fichaje -->
<color name="button_entry">#4CAF50</color>   <!-- Verde -->
<color name="button_exit">#F44336</color>    <!-- Rojo -->

<!-- Textos -->
<color name="text_primary">#212121</color>
<color name="text_secondary">#757575</color>

<!-- Fondos -->
<color name="background">#FFFFFF</color>
<color name="surface">#F5F5F5</color>
```

### Pantallas Principales

#### 1. LoginActivity
- Logo centrado
- Card con botón de Google Sign-In
- Sin formulario manual de credenciales

#### 2. MainActivity
- **Header**: Nombre del usuario + botón logout
- **Card de Estado**: Muestra último fichaje (entrada/salida)
- **Botón Central**: Grande, circular, cambia color según estado
    - Verde → CHECK_IN (Entrada)
    - Rojo → CHECK_OUT (Salida)
- **Card de Resumen**: Tiempo trabajado hoy | esta semana
- **Botón Historial**: Acceso a HistoryFragment

#### 3. HistoryFragment
- **Tabs**: Semanas disponibles para exportar
- **RecyclerView**: Lista de bloques entrada/salida
- **Long Press**: Abre TimeEditBottomSheet
- **FAB**: Añadir registro manual
- **Card Resumen**: Total semanal
- **Botón Exportar**: Genera y sube PDF

---

## ⚖️ Lógica de Negocio Importante

### Validación de Registros

**Reglas de negocio** (implementadas en `TimeRecordValidator.kt`):

1. ✅ Primer fichaje del día → Siempre CHECK_IN
2. ✅ Después de CHECK_IN → Solo puede ser CHECK_OUT
3. ✅ Después de CHECK_OUT → Solo puede ser CHECK_IN
4. ✅ Hora de salida DEBE ser posterior a hora de entrada
5. ✅ Comparaciones usan fechas **sin segundos/milisegundos** (vía `DateTimeUtils.clearSeconds()`)

```kotlin
// Ejemplo de validación
val (isValid, recordType, errorMsg) = TimeRecordValidator.validateNextAction(todayRecords)

if (!isValid) {
    showError(errorMsg)
    return
}

// Proceder con CHECK_IN o CHECK_OUT según recordType
```

### Cálculo de Horas Extras

**Baseline**: 37.5 horas semanales = **2250 minutos**

```kotlin
// Fórmula básica
val overtime = weeklyMinutes - 2250

// Positivo = horas extras a favor del empleado
// Negativo = horas pendientes de completar
```

**Persistencia**: El balance acumulado se guarda en la tabla `overtime_balance`:
```kotlin
@Entity(tableName = "overtime_balance")
data class OvertimeBalance(
    @PrimaryKey val id: Int = 1,  // Single row
    val balanceMinutes: Long = 0  // Balance acumulado
)
```

**Actualización**: Al exportar una semana, se calcula el delta y se actualiza el balance.

### Limpieza de Timestamps

**Problema**: Los registros pueden contener segundos/milisegundos que causan comparaciones incorrectas.

**Solución**: `DateTimeUtils.clearSeconds(date)` limpia antes de:
- Insertar en DB
- Comparar fechas
- Validar check-out vs check-in

```kotlin
// Siempre usar:
val cleanDate = DateTimeUtils.clearSeconds(Date())
repository.insertRecord(cleanDate, RecordType.CHECK_IN)
```

### Agrupación de Bloques (TimeRecordBlock)

**Lógica en** `TimeRecordBlock.createBlocks()`:

1. Ordena registros por fecha ascendente
2. Busca patrones CHECK_IN → CHECK_OUT
3. Si encuentra par → Crea TimeRecordBlock con duración
4. Si CHECK_IN sin salida → Block con checkOut = null
5. Si CHECK_OUT huérfano → Lo trata como entrada visible

**Resultado**: Lista de bloques para mostrar en HistoryFragment

---

## 🌐 Multiidioma

### Idiomas Soportados
- ✅ Español (es)
- ✅ Catalán (ca)

### Cambio de Idioma
- Se guarda en `SharedPreferences`: `language_preference`
- **CRÍTICO**: Requiere reinicio completo de la app para aplicar cambios
- Método: `LanguageUtils.restartApp(activity)`

### Estructura de Recursos
```
res/
├── values/
│   └── strings.xml          # Catalán (por defecto)
└── values-es/
    └── strings.xml          # Español
```

---

## ⚙️ Configuración y Build

### Variables de Entorno

Archivo: `local.properties` (NO está en Git)

```properties
# API Key para comunicación con servidor
API_KEY=tu_api_key_aqui

# Configuración de Keystore para release builds
keystore.path=/ruta/al/keystore.jks
keystore.password=password_del_keystore
keystore.alias=alias_de_la_key
keystore.alias.password=password_del_alias
```

**⚠️ Crítico**: Este archivo NUNCA debe estar en Git. Está en `.gitignore`.

### Versión Actual
- **versionCode**: 24
- **versionName**: "1.7.2"

### Historial de Versiones
- **v1.7.2** (24): Sistema de pending records + overtime balance
- **v1.6.8** (21): Primera versión en producción
- **v1.0.0** (1): Release inicial

### Comandos de Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requiere keystore configurado)
./gradlew assembleRelease

# Instalar en dispositivo conectado
./gradlew installDebug

# Clean
./gradlew clean
```

### ProGuard
- ✅ **Activado en Release**
- Configuración: `app/proguard-rules.pro`
- **Preserva**:
    - Room Database
    - Firebase Auth
    - Retrofit/OkHttp
    - iText PDF
    - Kotlin Coroutines
    - Clases Parcelables

---

## 🔍 Validaciones Importantes

### Lógica de Fichajes

**Reglas**:
1. Primer fichaje del día → Siempre CHECK_IN
2. Después de CHECK_IN → Solo puede ser CHECK_OUT
3. Después de CHECK_OUT → Solo puede ser CHECK_IN
4. Hora de salida DEBE ser posterior a hora de entrada

**Validador**: `TimeRecordValidator.kt`

```kotlin
// Verifica próxima acción permitida
fun validateNextAction(records: List<TimeRecord>): Triple<Boolean, RecordType?, String>

// Verifica que checkout > checkin
fun validateCheckOutTime(checkInTime: Date, checkOutTime: Date): Pair<Boolean, String>
```

---

## 🐛 Áreas Críticas y Puntos de Atención

### 🔴 Seguridad

**TEMPORAL - DEBE ARREGLARSE**:
```kotlin
// TrustAllCerts.kt - ACEPTA CUALQUIER CERTIFICADO SSL
// ⚠️ ESTO ES INSEGURO PARA PRODUCCIÓN
```

**Solución recomendada**:
- Usar certificado SSL válido firmado por CA reconocida
- O implementar Certificate Pinning
- Eliminar `TrustAllCerts.kt`

### 🟡 Sincronización

**Problema conocido**: Si la app se cierra inesperadamente después de fichar, el botón puede quedarse en estado inconsistente.

**Solución actual**:
- `MainViewModel.resetState()` - Método para forzar recarga
- Puede exponerse como opción oculta (ej: long press en el botón)

### 🟢 Exportación Robusta

El sistema de exportación tiene:
- ✅ Reintentos automáticos (3 intentos)
- ✅ Guardado local de fallback
- ✅ Sistema de retry manual (indicadores en `pending/`)
- ✅ Verificación de red antes de intentar

---

## 📝 Convenciones de Código

### Nomenclatura

```kotlin
// Classes: PascalCase
class TimeRecordRepository

// Functions: camelCase
fun loadWeekRecords()

// Constants: UPPER_SNAKE_CASE
const val SERVER_URL_BASE = "https://..."

// Properties: camelCase
val timeBlocks: LiveData<List<TimeRecordBlock>>
```

### Estructura de ViewModels

```kotlin
// 1. Estado UI (con StateFlow o LiveData)
private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// 2. LiveData para datos observables
private val _data = MutableLiveData<Data>()
val data: LiveData<Data> = _data

// 3. init block para cargas iniciales
init {
    loadInitialData()
}

// 4. Funciones públicas para acciones UI
fun handleAction() { ... }

// 5. Funciones privadas para lógica interna
private fun processData() { ... }
```

### Manejo de Errores

```kotlin
viewModelScope.launch {
    try {
        // Operación
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        val result = repository.doSomething()
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            data = result
        )
    } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "Error: ${e.message}"
        )
    }
}
```

---

## 🎨 View Binding

Este proyecto usa **View Binding** (habilitado en `build.gradle.kts`).

### Uso en Activities

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Acceso a vistas
        binding.checkButton.setOnClickListener { }
        binding.todayTimeText.text = "8h 30m"
    }
}
```

### Uso en Fragments

```kotlin
class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // Evitar memory leaks
    }
}
```

**✅ Ventajas**:
- Type-safe (detecta errores en compile-time)
- Null-safe
- Sin `findViewById()`
- Sin castings

---

## 🏭 ViewModel Factories

Los ViewModels se crean usando **factories personalizadas** de `ServiceLocator`.

### En Activities/Fragments

```kotlin
// Usando property delegate
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels {
        ServiceLocator.provideMainViewModelFactory(this)
    }
}

// Usando ViewModelProvider directamente
class HistoryFragment : Fragment() {
    private val viewModel: HistoryViewModel by viewModels {
        ServiceLocator.provideHistoryViewModelFactory(requireContext())
    }
}
```

### ServiceLocator (Manual DI)

**Ubicación**: `app/src/main/java/com/timetracking/app/core/di/ServiceLocator.kt`

**Propósito**: Provee instancias singleton de:
- Repository (con DAO)
- PDFManager
- ViewModelProviders.Factory para cada ViewModel

**Por qué no Dagger/Hilt**:
- Proyecto pequeño/mediano
- Control manual simple
- Sin necesidad de generación de código

```kotlin
object ServiceLocator {
    fun provideMainViewModelFactory(context: Context): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(getRepository(context)) as T
            }
        }
    }
}
```

---

## 🧪 Testing

**Estado actual**: Tests básicos incluidos, suite completa pendiente de implementar.

### Tests Existentes

```kotlin
// app/src/test/java/com/timetracking/app/ExampleUnitTest.kt
@Test
fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
}

// app/src/androidTest/java/com/timetracking/app/ExampleInstrumentedTest.kt
@Test
fun useAppContext() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.timetracking.app", appContext.packageName)
}
```

### Consideraciones para Testing

#### Room Database Tests
```kotlin
// Las operaciones de Room son suspend functions
@Test
fun testInsertRecord() = runBlocking {
    val record = TimeRecord(date = Date(), type = RecordType.CHECK_IN)
    val id = dao.insert(record)
    assertTrue(id > 0)
}
```

#### PDF Operations Tests
```kotlin
// PDF operations usan Dispatchers.IO
@Test
fun testPDFGeneration() = runTest {
    withContext(Dispatchers.IO) {
        val pdfData = pdfManager.createPDFInMemory(blocks)
        assertNotNull(pdfData)
        assertTrue(pdfData.isNotEmpty())
    }
}
```

#### Network Tests
```kotlin
// OkHttp con timeout de 30s
// Mockear respuestas del servidor
@Test
fun testServerUpload() {
    val mockServer = MockWebServer()
    mockServer.enqueue(MockResponse().setResponseCode(200))
    // ... test upload
}
```

#### ViewModel Tests
```kotlin
// Mockear ServiceLocator para aislar ViewModels
@Before
fun setup() {
    mockkObject(ServiceLocator)
    every { ServiceLocator.getRepository(any()) } returns mockRepository
}
```

### Pendiente de Implementar

**Alta prioridad**:
- ✅ TimeRecordValidator.validateNextAction()
- ✅ TimeRecordValidator.validateCheckOutTime()
- ✅ DateTimeUtils (clearSeconds, addDays, etc.)
- ✅ TimeCalculationUtils (overtime calculation)
- ✅ TimeRecordBlock.createBlocks()

**Media prioridad**:
- ✅ Repository CRUD operations
- ✅ MainViewModel state management
- ✅ HistoryViewModel export flow

**Baja prioridad**:
- ✅ UI Tests (Espresso)
- ✅ Integration tests
- ✅ PDF content validation

---

## 🚀 Despliegue

### Proceso Actual

1. **Desarrollo local** → Push a repositorio
2. **Build Release**: `./gradlew assembleRelease`
3. **APK generado**: `app/build/outputs/apk/release/app-release.apk`
4. **Distribución**: Manual (por ahora)

### Configuración de Release

**Keystore**: Configurado en `local.properties`
**ProGuard**: Activado con reglas específicas
**Firma**: Automática si keystore está configurado

---

## 🔄 Database Migrations

### Cómo Crear una Migración

Cuando necesites añadir campos o tablas:

1. **Incrementar versión** en `AppDatabase.kt`:
```kotlin
@Database(
    entities = [TimeRecord::class, OvertimeBalance::class],
    version = 4,  // ← Incrementar
    exportSchema = true
)
```

2. **Crear objeto Migration**:
```kotlin
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ✅ Para añadir columna
        database.execSQL("ALTER TABLE time_records ADD COLUMN new_field TEXT")
        
        // ✅ Para crear tabla
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS new_table (
                id INTEGER PRIMARY KEY NOT NULL,
                data TEXT NOT NULL
            )
        """)
        
        // ❌ Evitar cambios destructivos (DROP TABLE, DROP COLUMN)
        // En su lugar, crear tabla nueva y migrar datos
    }
}
```

3. **Registrar migración**:
```kotlin
fun getDatabase(context: Context): AppDatabase {
    return Room.databaseBuilder(context, AppDatabase::class.java, "timetracking_database")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
}
```

### Migraciones Existentes

#### MIGRATION_1_2
```kotlin
// Añadió campo 'exported' a time_records
database.execSQL("ALTER TABLE time_records ADD COLUMN exported INTEGER NOT NULL DEFAULT 0")
```

#### MIGRATION_2_3
```kotlin
// Añadió tabla overtime_balance
database.execSQL("""
    CREATE TABLE IF NOT EXISTS overtime_balance (
        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
        balanceMinutes INTEGER NOT NULL DEFAULT 0
    )
""")

// Insertar fila inicial
database.execSQL("INSERT INTO overtime_balance (id, balanceMinutes) VALUES (1, 0)")
```

### Testing Migrations

```kotlin
@Test
fun testMigration_1_2() {
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )
    
    // Create database version 1
    val db = helper.createDatabase(TEST_DB, 1)
    db.close()
    
    // Run migration
    helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    
    // Verify schema
}
```

---

## 📞 Información de Soporte

### Servidor Backend

**URL Base**: `https://80.32.125.224:5000`
**Endpoints**:
- POST `/upload` - Subida de PDFs
- GET `/files/check?name=...` - Verificar existencia
- GET `/files/download?name=...` - Descargar PDF existente

**Certificado**: Autofirmado (TEMPORAL)

### Contacto

- **Organización**: Grupo de Recuperación y Emergencias de Mallorca (GREC)
- **Dominio permitido**: `@grecmallorca.org`
- **Estado**: En fase de prueba con administración

---

## 🎯 Roadmap y Mejoras Futuras

### Prioridad Alta
- [ ] Implementar certificado SSL válido (eliminar TrustAllCerts)
- [ ] Sistema de sincronización robusto en caso de cierre inesperado
- [ ] Tests unitarios para lógica crítica

### Prioridad Media
- [ ] Notificaciones push para recordatorios de fichaje
- [ ] Modo offline completo con cola de sincronización
- [ ] Estadísticas mensuales/anuales
- [ ] Exportación a Excel

### Prioridad Baja
- [ ] Temas personalizables (claro/oscuro)
- [ ] Widgets de home screen
- [ ] Geolocalización de fichajes
- [ ] Firma digital en PDFs

---

## 📚 Recursos Útiles

### Documentación Oficial

- [Kotlin Android](https://developer.android.com/kotlin)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Firebase Auth Android](https://firebase.google.com/docs/auth/android/start)
- [iText 7 PDF](https://kb.itextpdf.com/itext/technical-articles)
- [Material Design 3](https://m3.material.io/)

### Dependencias Clave

```gradle
// Architecture Components
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
implementation "androidx.room:room-ktx:2.6.1"

// Firebase
implementation "com.google.firebase:firebase-auth-ktx:22.0.0"
implementation "com.google.android.gms:play-services-auth:21.2.0"

// PDF
implementation "com.itextpdf:itext7-core:7.2.5"

// Networking
implementation "com.squareup.okhttp3:okhttp:4.11.0"
implementation "com.squareup.retrofit2:retrofit:2.9.0"
```

---

## ⚡ Tips para Desarrollo Eficiente

### Debug en Android Studio

```kotlin
// Logging
import android.util.Log

Log.d("TAG", "Debug message")
Log.e("TAG", "Error message", exception)
```

### Inspection de Base de Datos

**Usando adb**:
```bash
adb shell
cd /data/data/com.timetracking.app/databases/
sqlite3 timetracking_database

# Consultas SQL
SELECT * FROM time_records ORDER BY date DESC LIMIT 10;
```

**Usando Android Studio**:
- View → Tool Windows → App Inspection → Database Inspector

### Testing en Dispositivo

```bash
# Limpiar datos de la app
adb shell pm clear com.timetracking.app

# Simular fecha/hora diferente (requiere root)
adb shell date 01012025.120000
```

---

## 🎓 Conceptos Clave del Proyecto

### TimeRecordBlock

Representa un **par entrada/salida**:

```kotlin
data class TimeRecordBlock(
    val date: Date,              // Día (sin hora)
    val checkIn: TimeRecord,     // Registro de entrada
    val checkOut: TimeRecord?,   // Registro de salida (puede ser null si está abierto)
    val duration: Long           // Duración en minutos
)
```

**Lógica de agrupación**: `createBlocks()` agrupa registros contiguos CHECK_IN + CHECK_OUT

### Flujo de Datos

```
Usuario → MainActivity → MainViewModel → Repository → Room DAO → SQLite
                                              ↓
                                        LiveData/StateFlow
                                              ↓
                                         Observadores UI
```

### Ciclo de Vida de un Fichaje

1. **Usuario pulsa botón** → `MainActivity.checkButton.onClick`
2. **Validación** → `TimeRecordValidator.validateNextAction()`
3. **Inserción** → `Repository.insertRecord()`
4. **Actualización UI** → `StateFlow.collect()` en MainActivity
5. **Recalculo tiempos** → `updateTodayTime()` / `updateWeeklyTime()`

---

## 🔐 Consideraciones de Seguridad

### API Keys y Secrets

**NUNCA commitear**:
- ❌ `google-services.json`
- ❌ `local.properties`
- ❌ Keystores (`.jks`, `.keystore`)
- ❌ Credenciales hardcodeadas

**Checklist antes de commit**:
```bash
# Verificar que .gitignore esté actualizado
git status --ignored

# Buscar API keys accidentales
grep -r "AIza" .
grep -r "sk_" .
```

### Permisos de la App

```xml
<!-- Necesarios -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Almacenamiento (PDFs) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

---

## 📖 Glosario

| Término | Descripción |
|---------|-------------|
| **CHECK_IN** | Fichaje de entrada (inicio jornada) |
| **CHECK_OUT** | Fichaje de salida (fin jornada) |
| **TimeRecord** | Registro individual de entrada O salida |
| **TimeRecordBlock** | Par entrada-salida con duración calculada |
| **Exported** | Flag booleano que indica si un registro ya fue exportado a PDF |
| **Pending Records** | Registros que fallaron al exportar y se guardan para reintentos |
| **Overtime Balance** | Balance acumulado de horas extras (positivo o negativo) |
| **Baseline** | Jornada semanal estándar (37.5h = 2250 minutos) |
| **DAO** | Data Access Object (interfaz para operaciones DB) |
| **StateFlow** | Holder de estado reactivo de Kotlin Coroutines |
| **ViewBinding** | Alternativa segura a `findViewById()` |
| **Room** | Librería de persistencia SQLite de Android |
| **MVVM** | Model-View-ViewModel (patrón arquitectónico) |
| **ServiceLocator** | Patrón DI manual (alternativa a Dagger/Hilt) |
| **Migration** | Proceso de actualización de schema de base de datos |

---

## ✅ Checklist Pre-Commit

Antes de hacer commit, verificar:

- [ ] Código compila sin warnings críticos
- [ ] No hay API keys hardcodeadas
- [ ] Strings externalizados en `strings.xml` (ambos idiomas)
- [ ] Nombres de variables/funciones descriptivos
- [ ] Comentarios en código complejo
- [ ] Sin imports no utilizados
- [ ] Formatted según Kotlin Style Guide
- [ ] Git ignore actualizado si se añaden nuevos tipos de archivo

---

## 🎉 ¡Listo para Desarrollar!

Este documento debe ser tu referencia principal. Mantenlo actualizado cuando:
- Añadas nuevas features
- Cambies arquitectura
- Modifiques configuraciones críticas
- Resuelvas bugs importantes

**Regla de oro**: Si necesitas explicarlo a un compañero, probablemente debería estar en este documento.

---

**Última actualización**: 2025-10-02  
**Versión del documento**: 2.0 (Fusión optimizada)  
**Mantenido por**: Equipo de desarrollo GREC  
**App Version**: 1.7.2 (versionCode 24)
