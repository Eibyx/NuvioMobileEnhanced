# Estado de funciones de Nuvio (Avix)

Estados: **PENDIENTE** · **EN DESARROLLO** · **IMPLEMENTADA** (código escrito) · **COMPILADA** (compila; tests unitarios propios en verde) · **PROBADA** (verificada en dispositivo, con evidencia en docs/TESTING.md) · **BLOQUEADA**.

Lo heredado de Luqman/original aparece como **COMPILADA**: su código está y compila, pero Avix aún no lo ha probado en dispositivo. Ninguna función figura como PROBADA todavía.

## 1. Identidad y proyecto

| Característica | Estado | Archivos | Pruebas / limitaciones |
|---|---|---|---|
| `applicationId` `com.avix.nuvio` / `com.avix.nuvio.debug` | COMPILADA | `androidApp/build.gradle.kts` | Instalación conjunta pendiente en dispositivo |
| Nombre «Nuvio», icono/logo/splash originales | COMPILADA | `res/values/strings.xml` | Sin recursos nuevos (ver nota de marca en README) |
| Esquema `avixnuvio://` (deep links, OAuth, Infuse) | COMPILADA | `core/deeplink/AppScheme.kt`, `AndroidManifest.xml` | `AppUrlBridgeTest`, `InfusePlaybackTest` PASS. OAuth requiere apps propias registradas |
| AniList/WeTrakr `CLIENT_ID` configurable | COMPILADA | `AniListConfig.kt`, `WeTrakrConfig.kt` | Sin cliente propio → no disponible |
| Actualizador → `Eibyx/NuvioMobileEnhanced` | COMPILADA | `AppUpdaterRepository.kt` | Aún no hay releases publicadas |
| Ajustes exclusivos solo locales (D-5) | COMPILADA | `core/avix/AvixLocalStore*`, `PlayerSettingsStorage.*` | Prueba cruzada con Nuvio original: NOT RUN |
| CI (tests + `Nuvio-Avix-Debug.apk`) | IMPLEMENTADA | `.github/workflows/ci.yml` | **Actions está deshabilitado en el fork**: no se ha ejecutado |
| Pruebas del motor en emulador | IMPLEMENTADA | `mpvrx-tests.yml`, `MpvRxEngineSmokeTest.kt` | No ejecutada (ver TESTING.md) |
| Release firmada + SHA-256 | IMPLEMENTADA | `android-release.yml` | Requiere secretos `AVIX_RELEASE_*` |
| Vigilancia de upstreams y bibliotecas | IMPLEMENTADA | `upstream-watch.yml`, `.github/upstreams.json` | Programado solo desde la rama por defecto |

## 2. Reproductor

| Característica | Estado | Archivos | Pruebas / limitaciones |
|---|---|---|---|
| Motor libmpv = mpvRx (`mpvlib 1.1.3`), SHA-256 verificado | COMPILADA | `settings.gradle.kts`, `composeApp/build.gradle.kts`, `androidLibmpvMpvrx/` | Pruebas 1–20: ver TESTING.md |
| Volver al motor anterior (`-Pavix.legacyLibmpv=true`) | EN DESARROLLO | `androidLibmpvLegacy/` | Compilación de esta variante pendiente |
| Instancia única de libmpv (`LibmpvInstanceGuard`) | COMPILADA | `LibmpvInstanceGuard.kt`, `PlayerEngine.android.kt` | Cambio de motor / siguiente episodio en dispositivo: NOT RUN |
| ExoPlayer + fallback a libmpv (heredado) | COMPILADA | `PlayerEngine.android.kt` | — |
| Cambio de motor en el reproductor conservando estado | COMPILADA | `PlayerScreenRuntimeEngineSwitch.kt` | Dispositivo: NOT RUN |
| Motor recordado por serie/película, reset | COMPILADA | `AvixPlaybackEnginePreferences.kt`, `AvixEngineSettingsRows.kt` | Dispositivo: NOT RUN |
| Automático → libmpv al elegir ASS/SSA | COMPILADA | `maybeSwitchToLibmpvForAss()` | Dispositivo: NOT RUN |
| Selector con 3 opciones (Automático / ExoPlayer / libmpv (mpvRx)) en el reproductor | COMPILADA | `PlayerEnginePickerDialog.kt`, `selectPlaybackEngine()` | Dispositivo: NOT RUN |
| Volver al motor anterior si el elegido falla antes de reproducir | COMPILADA | `recoverFromFailedEngineSwitch()` | Dispositivo: NOT RUN |
| Importar subtítulos locales | COMPILADA | `LocalSubtitleImport.kt`, `LocalSubtitlePicker.*`, `SubtitleModal.kt` | `LocalSubtitleImportTest` 11/11 PASS; dispositivo NOT RUN; iOS no soportado |
| libmpv: `sub-ass-override=no`, fuentes incrustadas, carpeta de fuentes | COMPILADA | `PlayerEngine.android.kt` | ASS_SSA_TESTS.md NOT RUN |
| Importar fuentes (UI) | PENDIENTE | — | Port de Yesnt10 |

## 3. Heredado de Luqman (+ original)

| Característica | Estado |
|---|---|
| Cuenta Nuvio (Supabase): correo, sesiones, perfiles, PIN, biometría, código TV | COMPILADA (requiere `NUVIO_SUPABASE_*`; sin ellos, modo sin cuenta) |
| Sincronización: addons, biblioteca, progreso, historial, colecciones, catálogos | COMPILADA |
| Addons Stremio, plugins QuickJS, debrid, P2P | COMPILADA |
| Jellyfin / Emby | COMPILADA |
| Live TV: M3U, Xtream, Stalker, EPG | COMPILADA |
| Trakt, Simkl, MDBList, AniList, WeTrakr | COMPILADA (credenciales propias) |
| PiP, mini reproductor, Chromecast, calidad HLS, volume boost, capítulos, velocidad, atajos | COMPILADA |
| Subtítulos sidecar, AutoSync, estilos | COMPILADA |
| Fondos dinámicos, hero con tráiler, degradados, AMOLED | COMPILADA |
| Descargas, Profile Insights, What's New, logs | COMPILADA |

## 4. Pendiente

| Característica | Estado |
|---|---|
| Ports de Yesnt10 | PENDIENTE (docs/integration/YESNT_PORT_LEDGER.md) |
| Tema Claro / Oscuro / Sistema | PENDIENTE |
| Valoraciones IMDb de episodios junto a OMDb | PENDIENTE |
| Revisión de `ProviderCredentialSync` y de tokens en logs | PENDIENTE |
| CloudStream | BLOQUEADA (procedencia y seguridad del runtime) |
| Telegram Android | Fuera de la v1 (no existe implementación Android) |
