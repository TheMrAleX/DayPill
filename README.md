# Pastillero

App Android personal para recordar la medicación, registrar tomas y consultar el historial. Los datos permanecen en el dispositivo: sin permiso de Internet, sin anuncios ni analítica. Licencia MIT.

## Estado

H2: base de datos Room, lógica de tomas, ajustes DataStore y exportación/importación JSON/CSV con tests unitarios. La app sigue mostrando una pantalla vacía: el tema y navegación completos, las pantallas y las alarmas se implementan en los siguientes hitos de [AGENTS.md](AGENTS.md).

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

Pendientes hasta implementar las pantallas.

## Licencia

[MIT](LICENSE), 2026.
