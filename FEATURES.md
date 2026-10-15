# Estado de funciones de Programming

Estados: **PENDIENTE** · **EN DESARROLLO** · **IMPLEMENTADA** (código escrito) · **COMPILADA** (compila y pasan los tests unitarios) · **PROBADA** (verificada en dispositivo) · **BLOQUEADA**.

Las funciones heredadas de Luqman/upstream aparecen como **COMPILADA**: su código está y compila, pero Programming todavía no las ha probado en un dispositivo.

## 1. Identidad y proyecto

| Característica | Origen | Estado | Archivos | Pruebas | Limitaciones |
|---|---|---|---|---|---|
| `applicationId` `app.programming` / `.debug` | Programming | COMPILADA | `androidApp/build.gradle.kts` | Build local | Instalación conjunta pendiente de probar en dispositivo |
| Nombre «Programming» | Programming | COMPILADA | `composeApp/src/androidMain/res/values/strings.xml`, `androidApp/src/debug/res/values/strings.xml` | Build | — |
| Icono, variantes, splash y wordmark propios | Programming | COMPILADA | `scripts/branding/generate_icons.py`, `res/mipmap-*`, `composeResources/drawable/app_*` | Revisión visual de los PNG generados | — |
| Esquema `programming://` (deep links, OAuth, Infuse) | Programming | COMPILADA | `core/deeplink/AppScheme.kt`, `AppUrlBridge.kt`, `InfusePlayback.kt`, `AndroidManifest.xml`, `build.gradle.kts` | Tests de deep links existentes | Requiere apps OAuth propias registradas con estos redirects |
| AniList/WeTrakr con `CLIENT_ID` configurable | Programming | COMPILADA | `AniListConfig.kt`, `WeTrakrConfig.kt`, `composeApp/build.gradle.kts` | Build | Sin cliente propio → no disponible |
| Actualizador → `Eibyx/programming` | Programming | COMPILADA | `features/updater/AppUpdaterRepository.kt` | — | El repo es privado: la API pública no devuelve releases (el actualizador no encontrará versiones) |
| Ajustes exclusivos de Luqman solo locales (opción B) | Programming | COMPILADA | `PlayerSettingsStorage.{android,ios}.kt` | Tests unitarios existentes | — |
| CI (tests + APK debug) | Programming | IMPLEMENTADA | `.github/workflows/ci.yml` | — | Pendiente de la primera ejecución en GitHub |
| Release firmada + SHA-256 + GitHub Release | Programming | IMPLEMENTADA | `.github/workflows/release.yml` | — | Requiere los secretos del keystore |
| Vigilancia de upstreams | Programming | IMPLEMENTADA | `.github/workflows/upstream-watch.yml`, `.github/upstreams.json` | — | — |

## 2. Heredado de Luqman (+ upstream 0.5.10)

| Característica | Estado |
|---|---|
| Cuenta Nuvio (Supabase): correo, sesiones, perfiles, PIN, código TV | COMPILADA (requiere `NUVIO_SUPABASE_*`; sin ellos, modo sin cuenta) |
| Sincronización: addons, biblioteca, progreso, historial, colecciones, catálogos | COMPILADA |
| Addons Stremio, plugins QuickJS, debrid, P2P | COMPILADA |
| Jellyfin / Emby | COMPILADA |
| Live TV: M3U, Xtream, Stalker, EPG | COMPILADA |
| Trakt, Simkl, MDBList, AniList, WeTrakr | COMPILADA (requieren credenciales propias) |
| Reproductor ExoPlayer + libmpv con fallback, libass, PiP, mini reproductor, Chromecast, visionado privado | COMPILADA |
| Selector de calidad HLS, volume boost, capítulos, miniaturas, velocidad, atajos de teclado | COMPILADA |
| Subtítulos: sidecar, AutoSync, sincronizar de oído, estilos | COMPILADA |
| Fuentes fijadas, búsqueda de streams, navegador de episodios, valoraciones en la app | COMPILADA |
| Fondos dinámicos, hero con tráiler, degradados, barra flotante | COMPILADA |
| Descargas (carpeta personalizada, solo Wi-Fi), biometría, Profile Insights, What's New, logs | COMPILADA |

## 3. Pendiente (orden de las fases)

| Característica | Fase | Estado |
|---|---|---|
| Merge de los 4 commits del original | 2 | PENDIENTE |
| Valoraciones IMDb de episodios junto a OMDb | 2 | PENDIENTE |
| Selector de motor dentro del reproductor + motor por serie | 3 | PENDIENTE |
| Fidelidad ASS/SSA (libmpv: fuentes, carpeta de fuentes, fallback, charset; Exo: modo libass) | 4 | PENDIENTE |
| Importar subtítulos locales | 5 | PENDIENTE |
| Ports de Yesnt10 | 6 | PENDIENTE (ver `docs/integration/YESNT_PORT_LEDGER.md`) |
| Tema Claro / Oscuro / Sistema | 7 | PENDIENTE |
| Subtítulos dobles, Anime4K, shaders | 8 | PENDIENTE |
| CloudStream | — | BLOQUEADA (seguridad y procedencia del AAR) |
| Telegram Android | — | Fuera de la v1 (no existe implementación Android) |
