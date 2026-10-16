# Estado de funciones de Nuvio (Avix)

Estados: **PENDIENTE** · **EN DESARROLLO** · **IMPLEMENTADA** (código escrito) · **COMPILADA** (compila; tests unitarios propios en verde) · **PROBADA** (verificada en dispositivo, con evidencia en docs/TESTING.md) · **BLOQUEADA**.

Lo heredado de Luqman/original aparece como **COMPILADA**: su código está y compila, pero Avix aún no lo ha probado en dispositivo. Ninguna función figura como PROBADA todavía.

## 1. Identidad y proyecto

| Característica | Estado | Archivos | Pruebas / limitaciones |
|---|---|---|---|
| `applicationId` `com.eibyx.nuvio` / `com.eibyx.nuvio.debug` (antes `com.avix.nuvio`, D-13) | COMPILADA | `androidApp/build.gradle.kts` | Instalación conjunta pendiente en dispositivo |
| Nombre «Nuvio», icono/logo/splash originales | COMPILADA | `res/values/strings.xml` | Sin recursos nuevos (ver nota de marca en README) |
| Esquema `avixnuvio://` (deep links, OAuth, Infuse) | COMPILADA | `core/deeplink/AppScheme.kt`, `AndroidManifest.xml` | `AppUrlBridgeTest`, `InfusePlaybackTest` PASS. OAuth requiere apps propias registradas |
| AniList/WeTrakr `CLIENT_ID` configurable | COMPILADA | `AniListConfig.kt`, `WeTrakrConfig.kt` | Sin cliente propio → no disponible |
| Actualizador → `Eibyx/NuvioMobileEnhanced` | COMPILADA | `AppUpdaterRepository.kt` | Aún no hay releases publicadas |
| Ajustes exclusivos solo locales (D-5) | COMPILADA | `core/avix/AvixLocalStore*`, `PlayerSettingsStorage.*` | Prueba cruzada con Nuvio original: NOT RUN |
| CI (tests + `Nuvio-Eibyx-Debug.apk`) | COMPILADA | `.github/workflows/ci.yml` | «Unit tests» (1781 tests, 0 fallos), «Debug APK» y «mpvRx engine tests» en verde en el mismo commit (`e35e81d5`, CI #24, mpvRx #15) |
| Pruebas del motor en emulador | COMPILADA | `mpvrx-tests.yml`, `MpvRxEngineSmokeTest.kt` | PASS en emulador de CI (ejecuciones #5–#9); dispositivo real NOT RUN |
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
| Importar fuente de subtítulos (UI) | COMPILADA | `SubtitleFontFile.kt`, `SubtitleFontPicker.android.kt` | `SubtitleFontFileTest` PASS; dispositivo NOT RUN |

## 3. Heredado de Luqman (+ original)

| Característica | Estado |
|---|---|
| Cuenta Nuvio (Supabase): correo, sesiones, perfiles, PIN, biometría, código TV | COMPILADA (CI inyecta `NUVIO_SUPABASE_*` desde secretos; mensajes de error por tipo y tokens redactados en logs) |
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
| Resto de ports de Yesnt10 | PENDIENTE (docs/integration/YESNT_PORT_LEDGER.md) |
| Tema Claro / Oscuro / Sistema | COMPILADA — `ThemeMode.kt`, `LightPalette.kt`, `AvixThemeModeRow.kt`; contraste WCAG `LightPaletteContrastTest` 3/3 PASS; revisión visual en dispositivo: NOT RUN |
| Valoraciones IMDb de episodios junto a OMDb | PENDIENTE |
| Redacción de tokens en logs y Sentry | COMPILADA (`SecretRedactor`, `SecretRedactorTest`) |
| CloudStream | BLOQUEADA (procedencia y seguridad del runtime) |
| Telegram Android | Fuera de la v1 (no existe implementación Android) |

## 5. Ports de Yesnt10 (detalle en docs/integration/YESNT_PORT_LEDGER.md)

Todos guardan su configuración solo en el dispositivo (D-5). Ninguno se ha probado aún en dispositivo.

| Característica | Estado | Archivos | Pruebas |
|---|---|---|---|
| Hora estimada de fin | COMPILADA | `PlaybackEndTime.kt` | `PlaybackEndTimeTest` PASS |
| Velocidad y ETA de descargas | COMPILADA | `DownloadsModels.kt` | `DownloadSpeedEtaTest` PASS |
| Recordar elecciones / conservar estado al cambiar de calidad | COMPILADA | `PlayerScreenRuntimeEngineSwitch.kt` | — |
| Fuente de subtítulos personalizada | COMPILADA | `SubtitleFontFile.kt` | `SubtitleFontFileTest` PASS |
| Copia de ajustes locales | COMPILADA | `core/avix/AvixSettingsBackup.kt` | `AvixSettingsBackupTest` PASS |
| Personas favoritas | COMPILADA | `FavoritePeopleRepository.kt` | `FavoritePeopleRepositoryTest` PASS |
| Degradados de Avix | COMPILADA | `core/ui/AvixGradientThemes.kt` | `LightPaletteContrastTest` PASS |
| «Detrás de cámaras» | COMPILADA | `TrailerCategories.kt` | `TrailerCategoriesTest` PASS |
| Informe local de errores | COMPILADA | `core/diagnostics/CrashDiagnostics*.kt`, `CrashReportDialog.kt` | `CrashDiagnosticsTest` PASS |
| Live TV compartido por perfiles con addons compartidos | COMPILADA | `LiveTvProfileScope.kt` | `LiveTvProfileScopeTest` PASS |
| Siguiente episodio tras un especial | COMPILADA | `SeriesContinuity.kt` | `SeriesContinuityTest` PASS |
| Barra de estado en el reproductor | COMPILADA | `PlayerDeviceStatus*.kt` | `PlayerDeviceStatusTest` PASS |
| Diseño de Inicio y catálogos (filas, columnas, fechas, auto-scroll del hero) | COMPILADA | `AvixHomeLayoutPreferences.kt`, `ShelfComponents.kt` | `AvixHomeLayoutPreferencesTest` PASS |
| Preparar streams por adelantado (desactivado por defecto) | COMPILADA | `StreamPrefetchPreference.kt`, `MetaDetailsScreen.kt` | `StreamPrefetchTargetTest` PASS |
| Búsqueda de avatares GIF (GIPHY, clave del usuario) | COMPILADA | `GifAvatarSearch.kt`, `GifAvatarSearchDialog.kt` | `GifAvatarSearchTest` PASS |
| Asistente de IA (desactivado por defecto, claves del usuario cifradas) | COMPILADA | `features/ai/*`, `AvixSecretStore*` | `AiAssistantServiceTest` PASS; con proveedor real NOT RUN |
| Opciones del destacado (estilo, datos, valoración, sinopsis, póster) | COMPILADA | `AvixHomeLayoutPreferences.kt`, `HomeHeroSection.kt` | `AvixHomeLayoutPreferencesTest` PASS |
| Deslizar para recargar Inicio (opcional) | COMPILADA | `AvixHomePullToRefresh.kt` | Dispositivo NOT RUN |
| Filtro Vistos / No vistos en catálogos de biblioteca | COMPILADA | `CatalogWatchedFilter.kt` | `CatalogWatchedFilterTest` PASS |
| Abrir/compartir M3U, vídeo y magnet | COMPILADA | `IncomingSharedSource*.kt`, `SharedSourceIntents.android.kt`, manifest | `IncomingSharedSourceTest` PASS; dispositivo NOT RUN |
| Recuento de temporadas y episodios | COMPILADA | `MetaSeriesStats.kt` | `MetaSeriesStatsTest` PASS |
| MyAnimeList: seguimiento automático (port de AKRusso) | COMPILADA | `features/mal/*`, `MalProviderCard.kt` | `MalTrackingTest` 18/18 PASS; con cuenta real NOT RUN (requiere `MAL_CLIENT_ID`) |
| Acento animado y ocultar barra de estado | COMPILADA | `AvixAppearanceExtras.kt`, `AvixSystemUiController.*` | `AnimatedAccentBrushTest` PASS; dispositivo NOT RUN |
| DNS seguro (DoH) para addons y reproducción | COMPILADA | `DnsOverHttpsSettings.kt`, `AvixDns.kt` | `DnsOverHttpsProviderTest` PASS; dispositivo NOT RUN |
| Filtros de tipo en Descargas | COMPILADA | `DownloadsTypeFilter.kt` | Dispositivo NOT RUN |
