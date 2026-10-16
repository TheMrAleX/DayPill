# Pastillero

App Android personal para recordar la medicación, registrar tomas y consultar el historial. Los datos permanecen en el dispositivo: sin permiso de Internet, sin anuncios ni analítica. Licencia MIT.

## Estado

H4: Hoy, Medicamentos, Nuevo/Editar e Historial conectados a Room, con validación, selección de horas/días/color, marcado de tomas y archivado que conserva el historial. El formulario conserva su borrador al recrearse. Las alarmas y notificaciones reales corresponden al H5; Ajustes y exportar/importar desde la interfaz, al H6. La capa de datos y sus pruebas proceden del H2.

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

Actions publica las capturas reales del emulador en el artefacto `pastillero-ui-h4`, en claro/oscuro y con fuente al 200 %. El recorrido también comprueba crear, editar, marcar, reiniciar y archivar conservando historial.

## Licencia

[MIT](LICENSE), 2026.
