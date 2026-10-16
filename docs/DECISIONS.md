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
