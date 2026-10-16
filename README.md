# Pastillero

App Android personal para recordar la medicación, registrar tomas y consultar el historial. Los datos permanecen en el dispositivo: sin permiso de Internet, sin anuncios ni analítica. Licencia MIT.

## Estado

H3: tema Material 3 claro/oscuro, fuente Inter y navegación entre Hoy, Medicamentos e Historial, con rutas de Nuevo/Editar/Ajustes. Los destinos aún están vacíos: el contenido y las alarmas se implementan en los siguientes hitos de [AGENTS.md](AGENTS.md). La capa de datos y sus 37 tests pertenecen al H2.

## Descargar el APK

1. Abre [Actions](https://github.com/TheMrAleX/DayPill/actions/workflows/android.yml) y elige una ejecución correcta de **Android**.
2. Descarga **pastillero-debug-apk** en **Artifacts** (requiere iniciar sesión en GitHub).
3. Descomprime el ZIP e instala `app-debug.apk` en el teléfono, autorizando esa fuente de instalación cuando Android lo solicite.

Android mínimo: 14 (API 34). Objetivo: Pixel 7 Pro con Android 17 (API 37).

## Compilar

Necesitas JDK 17 o superior (CI: Temurin 21) y Android SDK con plataforma API 37 y Build-Tools 36.0.0. Configura `ANDROID_HOME` o `sdk.dir` en `local.properties`.

```sh
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para ejecutar las comprobaciones de CI:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace
```

## Capturas

Actions publica las capturas reales del emulador en el artefacto `pastillero-ui-h3`, en claro/oscuro y con fuente al 200 %. Capturas de las pantallas completas: pendientes de H4.

## Licencia

[MIT](LICENSE), 2026.
