# Pastillero

App Android personal para recordar la medicación, registrar tomas y consultar el historial. Los datos permanecen en el dispositivo: sin permiso de Internet, sin anuncios ni analítica. Licencia MIT.

## Estado

H5: pantallas conectadas a Room, una alarma exacta pendiente y notificaciones por toma con acciones Tomada/Posponer. Los recordatorios se restauran tras reinicio/actualización y cambios de hora/zona; las pautas semanales continúan aunque no se abra la app. Sin permiso de notificaciones no se muestran avisos. Ajustes y exportar/importar desde la interfaz corresponden al H6.

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

Actions publica las capturas y comprobaciones de alarmas/notificaciones del emulador en `pastillero-ui-h5`. El recorrido completo de las pantallas (H4, ambos temas/idiomas y fuente al 200 %) permanece en `scripts/verify_screens.py`.

## Licencia

[MIT](LICENSE), 2026.
