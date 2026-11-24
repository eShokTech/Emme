# EMME Android 0.1.0

Primera base nativa Android para EMME. Aplicación en Kotlin con interfaz de chat, historial persistente local y respuestas de demostración sin conexión.

## Estado
- App Android nativa, paquete `com.eshok.emme`.
- Android mínimo 8.0 (API 26).
- Chat de prueba y memoria local mediante SharedPreferences.
- Caso confirmado del iPad A1219 incluido como respuesta local de demostración.
- Sin conexión a IA/API, sin búsqueda web y sin voz en esta versión.

## Compilación
Requiere JDK 17 y Android SDK con plataformas/build-tools para API 35, además de Gradle 8.9 o compatible con Android Gradle Plugin 8.7.3.

```bash
gradle assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

El workflow de GitHub Actions compila el APK y lo publica como artifact descargable.

## Seguridad
Esta versión no solicita permisos especiales, no transmite conversaciones y almacena el historial en el almacenamiento privado de la aplicación. Desinstalarla elimina esos datos locales.
