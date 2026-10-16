# Decisiones

- H1: se implementa solo el esqueleto y CI; datos, navegación, fuente Inter, tema completo, pantallas y recordatorios quedan para sus hitos.
- H1: se usan versiones estables verificadas el 2026-10-16 en los metadatos de Google Maven/Maven Central: AGP 9.4.1, Kotlin/Compose compiler 2.4.21, Compose BOM 2026.09.00, Activity 1.13.0, Core 1.19.1 y SplashScreen 1.2.0.
- H1: Gradle 9.8.1 es la versión estable actual verificada en https://services.gradle.org/versions/current; satisface el mínimo 9.6.0 de AGP 9.4 y los mínimos del proyecto.
- H1: se actualiza la dependencia KGP del Kotlin integrado mediante buildscript para alinear su versión con el compilador Compose; no se aplica kotlin-android ni se desactiva el DSL nuevo.
- H1: los scripts wrapper proceden de la etiqueta oficial de Gradle; el JAR se extrae de la distribución oficial y se verifican ambos SHA-256 contra los publicados por Gradle.
- H1: el contenedor manual comienza vacío porque sus dependencias pertenecen a H2 y H5; no se registran receivers hasta H5.
- H1: se incluyen los recursos adaptativos requeridos por el manifiesto, con el trazado de la sección 11; el resto del trabajo de iconos y accesibilidad queda en H6.
- H1: el tema mínimo usa colores dinámicos (minSdk 34) y splash del sistema; la paleta completa, tipografía y navegación pertenecen a H3.
- H1: se excluyen todas las áreas de almacenamiento de copias y transferencia, además de allowBackup=false.

- H2: se añaden únicamente dependencias permitidas: Room 2.8.5, DataStore 1.2.1, KSP 2.3.12, coroutines 1.11.0, serialization JSON 1.11.0 y JUnit 4.13.2; versiones estables verificadas en Google Maven y Maven Central el 2026-10-16.
- H2: los modelos y cálculos de domain no importan Android ni Room; Clock y ZoneId se inyectan, y las entidades Room se convierten en la capa de datos.
- H2: al retirar una hora se conserva su Schedule con daysMask=0; no se elimina la clave foránea de las tomas históricas. Las consultas de horarios activos y el generador excluyen esa máscara.
- H2: el salto de primavera usa LocalDateTime.atZone (02:30 a 03:30 en Madrid), siguiendo el ejemplo explícito del test de la sección 13; la frase «primera hora válida» de la sección 6 es ambigua respecto a ese ejemplo.
- H2: a las 12 horas exactas se descarta el aviso; el resto de avisos tardíos dispara inmediatamente y el repositorio persiste los descartes.
- H2: archivar borra las PENDING desde ahora; las anteriores se conservan sin nuevos avisos. No se borran TAKEN/SKIPPED ni sus snapshots.
- H2: al cambiar hora o zona solo se recalculan PENDING futuras; una posposición conserva su instante absoluto, mientras el aviso original sigue la hora local programada.
- H2: JSON versionado (formatVersion=1) incluye todos los IDs, horarios retirados, snapshots, estados, repeticiones y ajustes; se valida el grafo completo antes de reemplazar datos.
- H2: Room reemplaza todas sus tablas en una transacción; el ajuste DataStore se escribe al final y se compensa si falla el commit. Room y DataStore son archivos independientes y no ofrecen atomicidad conjunta ante un cierre abrupto del proceso.
- H2: CSV usa cabeceras de la sección 8.5, comillas RFC 4180, CRLF, hora de 24 horas, estados persistidos PENDING/TAKEN/SKIPPED y tomada_a con fecha ISO y offset de la zona elegida.
- H2: el resumen de siete días incluye hoy y seis días anteriores y redondea al entero más próximo; el historial incluye hoy y 29 días anteriores, omitiendo días vacíos y fechas futuras.
- H2: la apertura prepara las dosis; las llamadas desde alarmas, reinicio y cambios del sistema y la programación Android pertenecen a H5. No se añaden receivers ni pantallas en H2.

- H3: se incorpora la fuente variable Inter del URL de la sección 7.4 y su OFL; se cargan solo los pesos 400 y 600, con variaciones explícitas y letterSpacing=0 en los quince estilos M3.
- H3: los ocho estilos indicados respetan exactamente la escala; los siete no especificados conservan tamaños/interlineados M3 y usan Regular o SemiBold. TimeTextStyle fija cifras tabulares para las horas de H4.
- H3: las paletas de respaldo reproducen la sección 7.2; los surfaceContainer conservan los valores M3 por defecto y los colores dinámicos siguen activados. dynamicColor=false permite verificar el respaldo sin añadir un ajuste de usuario.
- H3: los tokens de forma M3 asignan extraSmall/small=16 dp para campos, medium=24 dp para tarjetas, large=16 dp para FAB y extraLarge=28 dp para diálogos; botones, chips e indicador de navegación conservan su forma pill M3.
- H3: los tres destinos principales solo muestran título; Hoy permite abrir Ajustes y Medicamentos abrir Nuevo. Editar se registra con medicationId Long, sin datos ni acciones ficticias para hacerlo accesible antes de H4.
- H3: navegación estándar con launchSingleTop, saveState/restoreState, barra inferior oculta en Nuevo/Editar/Ajustes y atrás predictivo sin interceptarlo. Se usan insets safeDrawing para cámara y barras, sin bloquear orientación.
- H3: el tema observa los ajustes DataStore con lifecycle; barras transparentes e iconos siguen el tema activo. El splash del sistema usa fondo claro/oscuro y el icono existentes, sin pantalla adicional.
- H3: Navigation 2.10.2 y Lifecycle 2.11.0 son las últimas versiones estables verificadas en Google Maven el 2026-10-16. Iconos: solo material-icons-core y el vector de cápsula exacto de la sección 11.
- H3: la verificación visual de CI usa el emulador estable API 37.0, perfil Pixel 7 Pro, 1440x3120 y 560 dpi (aprox. 411x892 dp); captura temas claro/oscuro y fuente al 200% usando adb, sin dependencias nuevas de tests en la app.
- H3: el fondo XML del splash usa system_background_light/dark (públicos desde API 34, el minSdk) para coincidir con Material You durante el arranque.
- H3: las capturas al 200% incluyen español (Medicamentos, la etiqueta más larga), además del recorrido en inglés; se cambia el idioma solo desde adb en el emulador.
