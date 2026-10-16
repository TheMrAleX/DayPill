# PASTILLERO — Especificación maestra para Codex

Este archivo es la ÚNICA fuente de verdad. Si algo no está aquí, NO lo inventes: elige la opción más simple y anótala en `docs/DECISIONS.md`.
Código, nombres de clases y comentarios en inglés. Textos de la interfaz en español (con traducción al inglés en `values-en`).

---

## 0. Reglas de trabajo (obligatorias)

1. Trabaja SOLO el hito que pida la tarea (sección 16). No adelantes hitos.
2. Rama nueva por tarea (`milestone-N-nombre`), commits pequeños, y abre PR a `main`.
3. NUNCA digas que algo compila o pasa tests si no lo ejecutaste. Si el entorno no tiene Android SDK o red, escribe el código con cuidado, dilo claramente en el informe final y deja que GitHub Actions lo verifique.
4. Informe final de cada tarea, máximo 15 líneas: qué hiciste, qué comandos ejecutaste y su resultado real, qué NO pudiste verificar.
5. No añadas dependencias que no estén en la sección 4. No añadas pantallas, ajustes ni textos que no estén en esta especificación.
6. Cero datos de ejemplo en la app final. Cero "lorem ipsum". Cero emojis en la interfaz.

---

## 1. Producto

App Android personal para recordar tomar medicación a diario. Abre rápido, avisa a la hora exacta, se marca con un toque, guarda historial.

NO es: red social, tienda, chat, app con cuentas, app con sincronización en la nube, app con IA.

Principios:
- Privacidad total: todo en el dispositivo. **Sin permiso INTERNET. Sin analytics. Sin anuncios. Sin Firebase. Sin Google Play Services.**
- Código abierto, licencia MIT.
- Poco texto. Mucho espacio en blanco. Tipografía grande y limpia.

Nombre: **Pastillero**. `applicationId`: `app.pastillero`. Paquete Kotlin: `app.pastillero`.

---

## 2. Dispositivo y toolchain

- Dispositivo objetivo: **Google Pixel 7 Pro, Android 17 (API 37)**. Pantalla de referencia: ~411 x 892 dp, densidad 3.5, 120 Hz, agujero de cámara centrado arriba.
- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 34`.
- **AGP 9.1.1 o superior** (compileSdk 37 requiere AGP ≥ 9.1; AGP 9.0 NO sirve). Gradle 9.3.1 o superior. JDK 17 mínimo (en CI usa 21). Build-Tools 36.0.0.
- AGP 9 trae Kotlin integrado y DSL nuevo: no apliques el plugin `kotlin-android` clásico. Sí aplica el plugin del compilador de Compose (`org.jetbrains.kotlin.plugin.compose`).
- Si un plugin (por ejemplo KSP para Room) choca con AGP 9, usa el flag de compatibilidad documentado por ese plugin. No bajes AGP por debajo de 9.1.
- Usa Gradle version catalog (`gradle/libs.versions.toml`). Busca y usa las últimas versiones ESTABLES (no alpha/beta/rc) compatibles entre sí. Si no tienes red para consultarlas, dilo en el informe.
- Gradle wrapper (`gradlew`, `gradle/wrapper/*`) commiteado. Si no puedes generar el `gradle-wrapper.jar`, deja que el CI lo genere (ver sección 14) y dilo.
- Un solo módulo: `:app`.

Comportamientos de Android 17 que SÍ debes respetar:
- Edge-to-edge obligatorio: `enableEdgeToEdge()`, respeta insets de barra de estado, navegación y cámara.
- Gesto "atrás" predictivo: `android:enableOnBackInvokedCallback="true"`. No bloquees la orientación (`screenOrientation` sin definir) y no pongas `resizeableActivity="false"`.
- Fondo de pantalla en tema: sin pantalla "splash" personalizada; usa `androidx.core:core-splashscreen` con el color de fondo del tema y el icono de la app.

---

## 3. Estructura del repo

```
/
├─ AGENTS.md                      (este archivo)
├─ README.md
├─ LICENSE                        (MIT, año 2026)
├─ .gitignore                     (Android estándar + .gradle, build, local.properties, *.keystore)
├─ .github/workflows/android.yml
├─ docs/DECISIONS.md              (decisiones que tomes y por qué, una línea cada una)
├─ gradle/libs.versions.toml
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
└─ app/
   ├─ build.gradle.kts, proguard-rules.pro
   ├─ schemas/                    (esquemas Room exportados, commiteados)
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml
      │  ├─ java/app/pastillero/
      │  │  ├─ PastilleroApp.kt            (Application + contenedor manual de dependencias)
      │  │  ├─ MainActivity.kt
      │  │  ├─ data/        (Room: entidades, DAOs, base de datos, repositorios, DataStore de ajustes, export/import)
      │  │  ├─ domain/      (lógica pura: DoseGenerator, NextAlarmCalculator, modelos)
      │  │  ├─ alarm/       (AlarmScheduler, DoseAlarmReceiver, DoseActionReceiver, BootReceiver, NotificationFactory)
      │  │  └─ ui/
      │  │     ├─ theme/    (Color.kt, Type.kt, Shape.kt, Theme.kt)
      │  │     ├─ nav/      (rutas y NavHost)
      │  │     ├─ today/, meds/, history/, settings/
      │  │     └─ components/
      │  └─ res/            (values, values-en, font, drawable, mipmap-anydpi)
      └─ test/java/app/pastillero/   (tests unitarios)
```

---

## 4. Dependencias permitidas (y solo estas)

- Compose BOM + `ui`, `ui-tooling-preview`, `foundation`, `material3`, `material-icons-core` (SOLO el set core; no `icons-extended`).
- `androidx.activity:activity-compose`, `androidx.core:core-ktx`, `androidx.core:core-splashscreen`
- `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`
- `androidx.navigation:navigation-compose`
- `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (con KSP)
- `androidx.datastore:datastore-preferences`
- `kotlinx-coroutines-android`, `kotlinx-serialization-json` (export/import)
- Tests: `junit`, `kotlinx-coroutines-test`

Prohibido: Hilt/Dagger/Koin, Retrofit/OkHttp, Firebase, Accompanist, Glide/Coil, ViewBinding, layouts XML, Timber, cualquier librería de calendario, cualquier librería de iconos externa.

---

## 5. Modelo de datos (Room, versión 1, `exportSchema = true`)

Fechas con `java.time`. Convertidores para `LocalDate` (ISO `yyyy-MM-dd` como String).

**Medication**
- `id` Long autogenerado
- `name` String (obligatorio, máx. 40 caracteres, recortado)
- `dose` String (opcional, máx. 30, puede estar vacío)
- `colorIndex` Int (0..7, ver paleta sección 7.3)
- `archived` Boolean (default false) — "eliminar" = archivar
- `createdAt` Long (epoch ms)

**Schedule** (una fila por hora de toma)
- `id` Long autogenerado
- `medicationId` Long (FK, cascade)
- `minuteOfDay` Int (0..1439)
- `daysMask` Int (bits 0..6 = lunes..domingo; 127 = todos los días)

**DoseLog** (una fila por toma concreta de un día)
- `id` Long autogenerado
- `medicationId` Long (FK, cascade), `scheduleId` Long (FK, cascade)
- `nameSnapshot` String, `doseSnapshot` String, `colorIndexSnapshot` Int (copias para que el historial no cambie si se edita el medicamento)
- `localDate` LocalDate
- `minuteOfDay` Int
- `scheduledAt` Long (epoch ms, calculado con la zona horaria del sistema en el momento de generar)
- `status` enum: `PENDING`, `TAKEN`, `SKIPPED`
- `takenAt` Long? (nulo salvo `TAKEN`)
- `notifyAt` Long? (próximo momento en que hay que avisar; nulo = no avisar más)
- `repeatCount` Int (default 0)
- Índice único: (`scheduleId`, `localDate`)

"Perdida" NO es un estado guardado: una fila `PENDING` cuyo `localDate` es anterior a hoy se considera perdida (solo se calcula al mostrar).

Ajustes (DataStore Preferences): `repeatMinutes` Int (default 10; valores 5, 10, 15, 30), `theme` (SYSTEM default, LIGHT, DARK).

---

## 6. Lógica de recordatorios

Archivo `domain/DoseGenerator.kt` — función pura y probada:
- `ensureDoses(today)` crea filas `DoseLog` PENDING para hoy y los 2 días siguientes, para cada `Schedule` de medicamentos no archivados cuyo `daysMask` incluya ese día. No duplica (índice único). `notifyAt = scheduledAt`.
- Hora inexistente por cambio horario (salto de primavera): se usa la primera hora válida posterior. Hora repetida (otoño): se usa la primera ocurrencia.
- Se ejecuta: al abrir la app, tras cualquier cambio de medicamentos, en cada alarma, tras reinicio, cambio de hora o de zona horaria.
- Al editar un medicamento: borrar las filas PENDING de hoy en adelante de ese medicamento y regenerar. Las filas TAKEN/SKIPPED y las pasadas NO se tocan.
- Al "eliminar": `archived = true` y borrar sus filas PENDING desde ahora. El historial se conserva.

Archivo `domain/NextAlarmCalculator.kt` — función pura y probada:
- Entre todas las filas `PENDING` con `notifyAt != null`, devuelve la de menor `notifyAt`.
- Si `notifyAt` está en el pasado hace menos de 12 horas: se dispara de inmediato (aviso tardío).
- Si hace más de 12 horas: pon `notifyAt = null` (se considera perdida).

`alarm/AlarmScheduler.kt`:
- Mantiene UNA sola alarma pendiente: la del próximo `notifyAt`.
- Usa `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` con un `PendingIntent` inmutable hacia `DoseAlarmReceiver`.
- Permiso en manifiesto: `USE_EXACT_ALARM` (se concede al instalar; la app es de uso personal fuera de Play). No uses `SCHEDULE_EXACT_ALARM`.
- Se vuelve a calcular y programar tras cualquier cambio de datos o ajustes.

`DoseAlarmReceiver` (al dispararse la alarma):
1. `ensureDoses`.
2. Para cada fila PENDING con `notifyAt <= ahora`: publica su notificación. Si `repeatCount < 3`: `repeatCount++` y `notifyAt = ahora + repeatMinutes`; si no: `notifyAt = null`.
3. Reprograma la siguiente alarma.
Usa `goAsync()` y una corrutina; termina en menos de 10 s.

`DoseActionReceiver`:
- Acción `TAKEN`: status = TAKEN, takenAt = ahora, notifyAt = null, cancela la notificación, reprograma.
- Acción `SNOOZE`: notifyAt = ahora + repeatMinutes, `repeatCount` se mantiene, cancela la notificación, reprograma.

`BootReceiver`: escucha `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`. Al recibir zona horaria u hora cambiada, recalcula `scheduledAt` de las filas PENDING futuras. Siempre reprograma.

---

## 7. Diseño visual (sigue esto al milímetro)

### 7.1 Principios
- Material 3, **color dinámico (Material You) activado en Android 12+**; paleta de respaldo de 7.2.
- Una acción principal por pantalla. Jerarquía clara con tamaño y peso, no con colores chillones.
- Máximo **2 pesos de fuente** en toda la app: Regular (400) y SemiBold (600).
- Nada de párrafos. Nada de subtítulos explicativos. Nada de texto de ayuda. Nada de pantallas de bienvenida.
- Márgenes laterales de pantalla: **20 dp**. Rejilla de 4 dp. Zonas táctiles mínimo **48 dp**.
- Tema claro/oscuro siguiendo el sistema (ajustable). Barras de sistema transparentes con iconos adaptados al tema.
- Animaciones: solo las por defecto de Material 3 y Navigation. El check de una toma: escala 0.9→1.0 en 150 ms. Nada más.
- Háptica al marcar una toma: `HapticFeedbackType.Confirm` si existe en la versión de Compose; si no, `LongPress`.

### 7.2 Paleta de respaldo (cuando el color dinámico no esté disponible o el usuario lo desactive en pruebas)
Claro: primary `#006A60`, onPrimary `#FFFFFF`, primaryContainer `#74F8E5`, onPrimaryContainer `#00201C`, secondary `#4A635F`, onSecondary `#FFFFFF`, secondaryContainer `#CCE8E2`, onSecondaryContainer `#05201C`, tertiary `#456179`, onTertiary `#FFFFFF`, background `#F4FBF8`, onBackground `#161D1C`, surface `#F4FBF8`, onSurface `#161D1C`, surfaceVariant `#DAE5E1`, onSurfaceVariant `#3F4947`, outline `#6F7977`, outlineVariant `#BEC9C6`, error `#BA1A1A`, onError `#FFFFFF`, errorContainer `#FFDAD6`, onErrorContainer `#410002`.
Oscuro: primary `#53DBC9`, onPrimary `#003731`, primaryContainer `#005048`, onPrimaryContainer `#74F8E5`, secondary `#B1CCC6`, onSecondary `#1C3531`, secondaryContainer `#334B47`, onSecondaryContainer `#CCE8E2`, tertiary `#ADCAE6`, onTertiary `#143349`, background `#0E1514`, onBackground `#DDE4E1`, surface `#0E1514`, onSurface `#DDE4E1`, surfaceVariant `#3F4947`, onSurfaceVariant `#BEC9C6`, outline `#899390`, outlineVariant `#3F4947`, error `#FFB4AB`, onError `#690005`, errorContainer `#93000A`, onErrorContainer `#FFDAD6`.
Completa los `surfaceContainer*` con los valores por defecto de `lightColorScheme()` / `darkColorScheme()`.

### 7.3 Colores de medicamento (índice 0..7, iguales en claro y oscuro)
0 Rojo `#E5484D` · 1 Naranja `#F97316` · 2 Ámbar `#F5B700` · 3 Verde `#2FA84F` · 4 Turquesa `#14B8A6` · 5 Azul `#3B82F6` · 6 Violeta `#8B5CF6` · 7 Rosa `#EC4899`. Por defecto al crear: 4.

### 7.4 Tipografía
- Fuente: **Inter** (licencia SIL OFL), fuente variable. Descárgala una vez y commitéala como `app/src/main/res/font/inter.ttf` desde:
  `https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf`
  Añade `docs/licenses/Inter-OFL.txt` con la licencia OFL. Si no puedes descargarla, usa `FontFamily.SansSerif`, deja un `TODO` visible en `Type.kt` y dilo en el informe.
- Cargar con `Font(R.font.inter, weight = ..., variationSettings = FontVariation.Settings(FontVariation.weight(w)))`.
- Todos los números de hora usan `fontFeatureSettings = "tnum"` (cifras tabulares).
- Escala (sp / interlineado sp / peso):

| Estilo | Tamaño/Interlineado | Peso | Uso |
|---|---|---|---|
| displaySmall | 36 / 44 | 600 | Progreso "2/4" |
| headlineMedium | 28 / 36 | 600 | Fecha en Hoy, título de pantallas |
| titleLarge | 22 / 28 | 600 | Hora de la toma |
| titleMedium | 16 / 24 | 600 | Nombre del medicamento |
| bodyLarge | 16 / 24 | 400 | Texto de campos y filas de ajustes |
| bodyMedium | 14 / 20 | 400 | Dosis, subtítulos de fila |
| labelLarge | 14 / 20 | 600 | Botones |
| labelMedium | 12 / 16 | 600 | Etiquetas de la barra inferior |

- `letterSpacing = 0` en todo. Nunca uses mayúsculas forzadas. Nunca cursiva.
- Soporta escala de fuente del sistema hasta 200 % sin recortes: alturas de fila con `heightIn(min = …)`, nunca altura fija.

### 7.5 Formas y medidas
- Tarjetas: radio 24 dp. Campos de texto: radio 16 dp. Botones y chips: pill (radio completo). Diálogos: 28 dp. FAB: 16 dp de radio, 56 dp.
- Tarjetas de toma y de medicamento: color `surfaceContainerLow`, sin sombra, sin borde.
- Separación entre tarjetas: 8 dp. Entre bloques: 24 dp. Relleno interior de tarjeta: 16 dp.
- Barra inferior: `NavigationBar` M3 estándar (80 dp), 3 destinos, indicador pill, etiquetas siempre visibles.
- Iconos: 24 dp. Botones de icono: 48 dp.

---

## 8. Pantallas (una por una)

Navegación: `NavigationBar` con 3 destinos: **Hoy**, **Medicamentos**, **Historial**. Arranca en Hoy. Editar/nuevo medicamento y Ajustes son pantallas aparte (sin barra inferior), con botón atrás (`Icons.AutoMirrored.Filled.ArrowBack`).
Iconos de la barra: Hoy = `Icons.Default.CheckCircle`; Medicamentos = `ic_capsule` (vector propio, sección 11); Historial = `Icons.Default.DateRange`. Ajustes = `Icons.Default.Settings`.

### 8.1 Hoy
Orden vertical (todo dentro de un `LazyColumn`, padding horizontal 20 dp, respetando insets superiores):
1. Cabecera (padding superior 16 dp). Izquierda, dos líneas: día de la semana (`bodyLarge`, `onSurfaceVariant`, p. ej. "viernes") y fecha (`headlineMedium`, p. ej. "16 octubre"), formateadas con `DateTimeFormatter` y `Locale.getDefault()`. Derecha: botón de icono Ajustes (48 dp).
2. Espacio 16 dp. Tarjeta de progreso (`surfaceContainerLow`, radio 24, relleno 16): texto `"%1$d/%2$d"` en `displaySmall`; debajo, 12 dp de espacio y un `LinearProgressIndicator` de **8 dp** de alto, esquinas redondeadas, pista `surfaceVariant`, relleno `primary`, sin animación de "indeterminado". Si todas están tomadas, en vez del número muestra "Todo listo" en `displaySmall`.
3. Espacio 24 dp. Lista de tomas de HOY, ordenadas por hora. Sin cabeceras de sección.
   Cada fila (tarjeta, altura mínima 72 dp, relleno 16 dp):
   - Izquierda: hora (`titleLarge`, ancho fijo 64 dp, cifras tabulares). Si está PENDING y ya pasó la hora: color `error`.
   - 16 dp de espacio. Centro (weight 1): nombre (`titleMedium`, 1 línea, elipsis) y debajo la dosis (`bodyMedium`, `onSurfaceVariant`, 1 línea; no se muestra la línea si la dosis está vacía).
   - Derecha: botón circular de 48 dp. PENDING: círculo con borde 2 dp `outline`, vacío. TAKEN: círculo relleno `primary` con check `onPrimary`. Al tocar alterna PENDING↔TAKEN (`takenAt` se guarda o se borra).
   - Una pequeña barra vertical de 4 dp de ancho y 32 dp de alto con el color del medicamento, al borde izquierdo de la tarjeta (dentro, con 8 dp de margen izquierdo).
   - Si TAKEN: todo el contenido de texto a 60 % de opacidad.
   - Tocar la fila NO hace nada (solo el botón circular).
4. Si no hay tomas hoy: centrado en el espacio libre, icono `ic_capsule` de 48 dp en `outline`, 12 dp, texto "Nada para hoy" (`bodyLarge`, `onSurfaceVariant`), 16 dp, botón `FilledTonalButton` "Añadir medicamento" (lleva a la pantalla Nuevo).
5. Si las notificaciones están denegadas: tarjeta `errorContainer` arriba de la lista (debajo de la cabecera) con texto "Notificaciones desactivadas" (`bodyLarge`, `onErrorContainer`) y botón de texto "Activar" (abre ajustes de notificaciones de la app).

### 8.2 Medicamentos
- Título "Medicamentos" (`headlineMedium`) con padding superior 16 dp, sin barra superior.
- Lista de tarjetas (altura mínima 72 dp): círculo de 16 dp con el color del medicamento; 16 dp de espacio; nombre (`titleMedium`) y debajo `bodyMedium` `onSurfaceVariant` con las horas separadas por " · " (ej. "08:00 · 20:00"); si no es diario, añade " · " y las letras de los días activos separadas por espacio (ej. "08:00 · L X V"). Tocar abre Editar.
- FAB (icono Add, 56 dp) abajo a la derecha, 16 dp sobre la barra inferior. Lleva a Nuevo.
- Vacío: igual que en Hoy pero texto "Sin medicamentos" y sin botón (el FAB ya está).

### 8.3 Nuevo / Editar medicamento
Pantalla con `Scaffold`, barra superior pequeña con atrás y título ("Nuevo" o "Editar", `titleLarge`). Si es Editar, a la derecha un botón de icono Delete (abre diálogo de borrado). Contenido con scroll vertical, padding horizontal 20 dp, separación entre bloques 24 dp. Botón "Guardar" fijo abajo: `Button` relleno, ancho completo, altura 56 dp, margen 20 dp, por encima del teclado/insets.
Bloques, en este orden:
1. **Nombre**: `OutlinedTextField`, etiqueta "Nombre", 1 línea, capitalización de oración, máx. 40. Error si vacío al guardar: "Escribe un nombre".
2. **Dosis**: `OutlinedTextField`, etiqueta "Dosis", placeholder "1 pastilla" (solo visible con foco), 1 línea, máx. 30. Opcional.
3. **Horas**: etiqueta de sección "Horas" (`labelLarge`, `onSurfaceVariant`). Cada hora = fila de 56 dp: hora en `titleLarge` (tocar abre `TimePickerDialog` de Material 3 con `is24Hour` según el sistema) y, a la derecha, botón de icono cerrar (solo si hay más de una). Debajo, `FilledTonalButton` "Añadir hora" (añade una fila con 08:00 y abre el selector). Al crear: una hora por defecto, 08:00. No se permiten horas repetidas (ignorar si ya existe).
4. **Días**: etiqueta "Días". Fila de 7 círculos de 44 dp (`Row` con `SpaceBetween`). Letras (es): L M X J V S D; (en): M T W T F S S. Seleccionado: fondo `primary`, texto `onPrimary`; no seleccionado: borde 1 dp `outline`, texto `onSurface`. Por defecto los 7 activos. Mínimo uno activo (si intenta quitar el último, no hace nada).
5. **Color**: etiqueta "Color". Fila de 8 círculos de 36 dp (`SpaceBetween`) con los colores de 7.3. Seleccionado: anillo de 3 dp `onSurface` separado 2 dp + check blanco 16 dp dentro.
Al guardar: valida, guarda, regenera dosis, reprograma alarma, vuelve atrás. Si es la primera vez y el permiso de notificaciones no está concedido, pídelo justo ahí (antes de volver atrás).
Diálogo de borrado: `AlertDialog`, título "¿Eliminar medicamento?", sin cuerpo, botones "Cancelar" (texto) y "Eliminar" (texto, color `error`).

### 8.4 Historial
- Título "Historial" (`headlineMedium`).
- Tarjeta resumen: etiqueta "7 días" (`labelLarge`, `onSurfaceVariant`) y debajo el porcentaje de tomas hechas sobre las programadas en los últimos 7 días (`displaySmall`, ej. "92 %"). Si no hay tomas programadas: "—".
- Lista de los últimos 30 días (de hoy hacia atrás) que tengan al menos una toma. Cada día = tarjeta (mín. 64 dp): izquierda, etiqueta del día ("Hoy", "Ayer" o "mié 14 oct", `titleMedium`); derecha, fila de puntos de 10 dp (uno por toma, ordenados por hora): TAKEN = relleno `primary`; SKIPPED o perdida = solo borde 1.5 dp `outline`; PENDING de hoy = borde `primary`. Al final del todo, `bodyMedium` `onSurfaceVariant` "3/4".
- Vacío: "Sin historial" centrado, `bodyLarge`.

### 8.5 Ajustes
Título "Ajustes" en la barra superior (con atrás). Lista simple, sin tarjetas, filas de 56 dp con `bodyLarge`; agrupadas con un espacio de 24 dp (sin cabeceras de grupo):
Grupo 1
- "Notificaciones": valor a la derecha ("Activadas" / "Desactivadas", `bodyMedium`, `onSurfaceVariant`). Toca → ajustes de notificaciones de la app.
- "Batería": toca → `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
- "Repetir cada": `SingleChoiceSegmentedButtonRow` con "5", "10", "15", "30" y la unidad "min" en la etiqueta de la fila como valor "10 min".
- "Tema": `SingleChoiceSegmentedButtonRow` "Sistema", "Claro", "Oscuro".
Grupo 2
- "Exportar datos" → `CreateDocument("application/json")`, nombre `pastillero-YYYY-MM-DD.json`.
- "Importar datos" → `OpenDocument`; diálogo "¿Reemplazar datos?" con "Cancelar" / "Reemplazar".
- "Exportar historial" → `CreateDocument("text/csv")`, columnas: `fecha,hora,medicamento,dosis,estado,tomada_a`.
Grupo 3
- "Acerca de": abre diálogo con: "Pastillero", versión, "Licencia MIT" y botón de texto "Código fuente" (intent `ACTION_VIEW` al URL del repo; el URL se define en una constante `REPO_URL` en `BuildConfig`/constants, vacío = ocultar botón).
Mensajes (Snackbar, una línea): "Datos exportados", "Datos importados", "Archivo no válido".

---

## 9. Notificación

- Canal `doses`, nombre "Tomas", importancia HIGH, vibración activada, luz desactivada, sonido: el de notificación por defecto. Se crea en `PastilleroApp.onCreate`.
- Una notificación por cada toma. ID = `dose.id.toInt()`.
- Título: nombre del medicamento. Texto: `"%hora% · %dosis%"` (si no hay dosis, solo la hora).
- Categoría `CATEGORY_REMINDER`. `setOnlyAlertOnce(false)`. `setAutoCancel(false)`. `setOngoing(false)`.
- Visibilidad `VISIBILITY_PRIVATE` con `publicVersion`: título "Hora de tu medicación", sin texto.
- Icono pequeño: `ic_capsule`. Color de acento: el color del medicamento.
- Acciones (2): "Tomada" y "Posponer". Tocar el cuerpo abre la app en Hoy.
- No uses estilos expandibles, ni imágenes, ni sonidos personalizados.

---

## 10. Manifiesto

Permisos: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`. Nada más. **Sin INTERNET.**
`android:allowBackup="false"` y reglas de extracción de datos que excluyan todo (el usuario tiene exportar/importar).
Receivers no exportados, excepto `BootReceiver` con los intent-filters necesarios.
`MainActivity`: `launchMode="singleTask"`, exportada solo por el intent LAUNCHER.
`android:label="@string/app_name"`, `android:icon` y `android:roundIcon` apuntan al icono adaptativo.

---

## 11. Iconos

`res/drawable/ic_capsule.xml` (24 dp, trazo; úsalo tal cual):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
  <group android:rotation="-45" android:pivotX="12" android:pivotY="12">
    <path android:strokeColor="#FF000000" android:strokeWidth="2"
        android:strokeLineJoin="round" android:strokeLineCap="round"
        android:pathData="M9,7 H15 A5,5 0 0 1 15,17 H9 A5,5 0 0 1 9,7 Z M12,7 V17"/>
  </group>
</vector>
```
Se tiñe con `tint`/`colorFilter` según contexto.

Icono de la app (adaptativo, `mipmap-anydpi/ic_launcher.xml` y `ic_launcher_round.xml`):
- Fondo: color sólido `#006A60`.
- Primer plano (`drawable/ic_launcher_foreground.xml`, viewport 108x108): grupo con rotación -45 y pivote 54,54; mitad izquierda `M54,42 H44 A12,12 0 0 0 44,66 H54 Z` en `#FFFFFF`; mitad derecha `M54,42 H64 A12,12 0 0 1 64,66 H54 Z` en `#74F8E5`.
- Monocromo (`drawable/ic_launcher_monochrome.xml`, mismo trazado, ambas mitades en `#000000`) y declarado en `<monochrome>`.

---

## 12. Accesibilidad

- Todo icono interactivo con `contentDescription` en el idioma activo (es/en). Botón de toma: "Marcar como tomada" / "Marcar como pendiente".
- Contraste mínimo AA en ambos temas.
- La fila de toma expone su estado con `semantics { stateDescription }`.
- Nada depende solo del color: el estado TAKEN se distingue por el check, no solo por el relleno.

---

## 13. Tests (JUnit, sin Robolectric)

Obligatorios en `src/test`:
- `DoseGeneratorTest`: máscara de días; no duplica; hoy+2 días; salto de primavera (2026-03-29, Europe/Madrid, hora 02:30 → 03:30) y otoño (2026-10-25, hora 02:30 → primera ocurrencia).
- `NextAlarmCalculatorTest`: elige el menor `notifyAt`; ignora TAKEN/SKIPPED; aviso tardío < 12 h dispara ya; > 12 h se descarta.
- `ExportImportTest`: ida y vuelta JSON conserva todo.
- `HistoryStatsTest`: porcentaje de 7 días y puntos por día.
Toda la lógica de `domain/` debe poder probarse sin Android (recibe `Clock`/`ZoneId` por parámetro).

---

## 14. CI (GitHub Actions) — `.github/workflows/android.yml`

Disparadores: `push` a `main`, `pull_request`, `workflow_dispatch`. Pasos:
1. `actions/checkout` (última versión mayor).
2. `actions/setup-java` Temurin 21.
3. `android-actions/setup-android` (última versión mayor).
4. `sdkmanager --install "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"`. Si el ID `android-37.0` no existe, lista con `sdkmanager --list | grep android-37`, usa el correcto y corrige el workflow.
5. `gradle/actions/setup-gradle` (última versión mayor). Si no hay wrapper commiteado, genera uno con `gradle wrapper --gradle-version 9.3.1`.
6. `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`
7. `actions/upload-artifact` subiendo `app/build/outputs/apk/debug/*.apk` con nombre `pastillero-debug-apk`.
El APK debug es lo que el usuario descarga e instala en el Pixel.

---

## 15. Documentación

`README.md` (corto, en español): qué es, capturas (placeholder hasta que existan), cómo descargar el APK desde Actions, cómo compilar (`./gradlew assembleDebug`), privacidad (sin internet, sin anuncios), licencia MIT.
`docs/DECISIONS.md`: lista de decisiones que hayas tenido que tomar.

---

## 16. Hitos (haz solo el que te pidan)

**H1 — Esqueleto y CI.** Proyecto Gradle, version catalog, manifiesto, `PastilleroApp`, `MainActivity` con pantalla vacía en el tema, `LICENSE`, `.gitignore`, `README`, workflow de CI. Criterio: `assembleDebug` verde en CI y APK en artefactos.

**H2 — Datos y lógica.** Sección 5 y 6 (sin receivers todavía), repositorios, DataStore, export/import JSON/CSV, y todos los tests de la sección 13. Criterio: tests verdes.

**H3 — Tema y navegación.** Fuente Inter, Color/Type/Shape/Theme (7.1–7.5), splash, `NavigationBar`, NavHost con las 3 pantallas vacías y rutas de Nuevo/Editar/Ajustes. Criterio: se ve el tema correcto en claro y oscuro.

**H4 — Pantallas.** Secciones 8.1 a 8.4 completas conectadas a los repositorios, textos en `values` y `values-en`. Criterio: crear, editar, eliminar y marcar tomas funciona.

**H5 — Alarmas y notificaciones.** Secciones 6 (receivers, scheduler) y 9, permisos y manifiesto (10). Criterio: tests siguen verdes; el código cubre arranque, cambio de hora y zona.

**H6 — Ajustes e iconos.** Sección 8.5 y 11, accesibilidad (12). Criterio: todo lo de ajustes funciona; icono adaptativo con monocromo.

**H7 — Pulido.** Revisión contra esta especificación: lista cada sección y confirma cumplimiento o desviación. Corrige desviaciones. Actualiza README.

---

## 17. Prohibiciones finales (revisa antes de terminar cada tarea)

- ❌ Permiso INTERNET, analytics, anuncios, Firebase, Play Services.
- ❌ Más de 2 pesos de fuente, mayúsculas forzadas, cursiva, emojis, degradados, sombras fuertes.
- ❌ Textos o ajustes no listados aquí. Cualquier cadena nueva debe existir en las dos tablas (`values`, `values-en`).
- ❌ Layouts XML, Hilt, librerías no listadas.
- ❌ Decir "funciona" o "compila" sin haberlo ejecutado.
- ❌ Cambiar medidas, colores o textos de esta especificación sin pedirlo.

---

## 18. Prompts cortos para cada tarea en Codex

Tras commitear este archivo como `AGENTS.md` en la raíz del repo, lanza una tarea por hito:

1. `Implementa el HITO 1 de AGENTS.md. Sigue las secciones 0, 2, 3, 10, 14 y 15.`
2. `Implementa el HITO 2 de AGENTS.md. Sigue las secciones 0, 5, 6 y 13.`
3. `Implementa el HITO 3 de AGENTS.md. Sigue las secciones 0, 7 y 8 (solo navegación).`
4. `Implementa el HITO 4 de AGENTS.md. Sigue las secciones 0, 7 y 8.1 a 8.4.`
5. `Implementa el HITO 5 de AGENTS.md. Sigue las secciones 0, 6, 9 y 10.`
6. `Implementa el HITO 6 de AGENTS.md. Sigue las secciones 0, 8.5, 11 y 12.`
7. `Ejecuta el HITO 7 de AGENTS.md.`

Antes de cada tarea, haz merge del PR anterior a `main`.
