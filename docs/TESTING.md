# Pruebas de Programming

## Automáticas (CI)

| Comando | Qué cubre |
|---|---|
| `./gradlew :composeApp:testAndroidHostTest -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | Tests unitarios de `commonTest` y `androidHostTest` (JVM) |
| `./gradlew :androidApp:assembleFullDebug …` | Compilación completa de la APK |

## Manuales en dispositivo (checklist por versión)

Anotar el modelo, la versión de Android, la APK (run o tag) y el resultado.

### Identidad e instalación
- [ ] Se instala junto a `com.nuvio.app`, `com.nuvio.enhanced` y `com.nuvio.media`, sin conflicto
- [ ] El nombre y el icono son «Programming»; el icono adaptativo y el monocromo se ven bien
- [ ] Los enlaces `programming://…` abren Programming y no otra app

### Cuenta de Nuvio (con su cuenta y configuración autorizada)
- [ ] Inicio de sesión con correo y contraseña
- [ ] Aparecen perfiles, addons, biblioteca y Seguir viendo
- [ ] Cruzada: un cambio de biblioteca hecho en Nuvio original aparece en Programming tras volver a primer plano (≤15 min), y viceversa
- [ ] Un ajuste exclusivo de Programming (por ejemplo, Mini reproductor) **no** se pierde después de cambiar ajustes en Nuvio original

### Reproducción
- [ ] ExoPlayer: HLS, MP4 y MKV de un addon
- [ ] libmpv: el mismo contenido
- [ ] Cabeceras HTTP del stream (addons que las exigen)

### Subtítulos ASS/SSA
Procedimiento comparativo con mpvRx: [docs/ass/COMPARISON.md](ass/COMPARISON.md) (pendiente de crear en la fase 4). Ninguna afirmación de equivalencia con mpvRx sin capturas del mismo `time-pos`.

## Entorno de desarrollo sin acceso a Maven Central

Si `repo1.maven.org` responde 429, se puede usar el mirror de Google con un init script local de Gradle (no se versiona):
`~/.gradle/init.d/maven-mirror.init.gradle.kts` → `https://maven-central.storage-download.googleapis.com/maven2/`.
