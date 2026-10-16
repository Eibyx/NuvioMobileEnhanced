# Registro de decisiones de integración (Nuvio Avix)

Cada entrada documenta una divergencia deliberada frente a una fuente, para resolver de forma consistente los conflictos de futuros merges.

## D-0 Repositorio y ramas
- Único repositorio: **Eibyx/NuvioMobileEnhanced** (fork de NuvioMedia/NuvioMobile). No se crean otros repositorios.
- `cmp-rewrite` sigue al original. El trabajo de Avix vive en **`integration/avix`** y se integra mediante PR hacia ramas de este mismo repositorio; nunca se abren PR hacia NuvioMedia ni hacia otros forks.
- Sin force push destructivo: los merges conservan el historial y la relación de fork con NuvioMedia.
- Estado inicial registrado: `cmp-rewrite` = `808272dc` (= NuvioMedia/NuvioMobile `cmp-rewrite`, 2026-10-15).

## D-1 Identidad Avix con cambios mínimos
- `applicationId` **`com.avix.nuvio`**, debug **`com.avix.nuvio.debug`**: se instala junto a Nuvio original (`com.nuvio.app`), Yesnt10 (`com.nuvio.enhanced`) y Luqman (`com.nuvio.media` / `com.nuviodebug.com`).
- Nombre visible «Nuvio» («Nuvio Debug» en debug). Icono, logotipo, splash y wordmark **originales de Nuvio**, sin regenerar (Luqman no los había cambiado). No se crea identidad gráfica nueva.
- **No** se renombran `namespace` ni paquetes Kotlin (`com.nuvio.*`).
- Componentes dependientes del id: el `FileProvider` usa `${applicationId}.fileprovider`; los alias del Android Keystore (`com.nuvio.media.*`) son por app (UID), así que no colisionan. No hay `ContentProvider` propios con autoridad fija.
- Esquema de enlaces **`avixnuvio://`** centralizado en `core/deeplink/AppScheme.kt` (`APP_URL_SCHEME`): deep links, OAuth (`avixnuvio://auth/{trakt,simkl,anilist}`) y callbacks de Infuse. `stremio://` se mantiene. Si upstream añade un `"nuvio://…"`, se sustituye por `APP_URL_SCHEME`.
- Actualizador y «Novedades» apuntan a `Eibyx/NuvioMobileEnhanced` (`features/updater/AppUpdaterRepository.kt`).
- APK: `Nuvio-Avix-Debug.apk`, `Nuvio-Avix-Release.apk` (+ `Nuvio-Avix-Release-<abi>.apk`).
- Restricción de marca para distribución pública: ver README.

## D-2 Merge de Luqman sobre el fork
- Commit de merge: `cmp-rewrite@808272dc` + Luqman `enhanced@2f2e9c1e`.
- Conflictos: `iosApp/iosApp/Player/PlayerPictureInPictureController.swift`, `iosApp/iosApp/SystemUI.swift`, `store.json` → se conserva la versión de Luqman (su PiP iOS con frames VideoToolbox, `ContentView.swift`, `MPVPlayerBridge.swift`, `MetalLayer.swift`). El código Android/común se fusiona de ambos (incluye nuvio-engine 0.1.5 del original).

## D-3 Buffers y caché VOD
- Se mantiene la implementación de buffers y memoria nativa de **Luqman** (`PlaybackBuffering.android.kt`).
- La del original (PR #2178: `VodCache.kt`, `PlatformExoPlayback*.kt`, `PlaybackBufferSettings.kt`) sigue **excluida**, como en Luqman (`d26b12f7`). En futuros merges, los conflictos *modify/delete* sobre esos archivos se resuelven **eliminándolos**.

## D-4 Valoraciones IMDb de episodios
- Luqman eliminó `ImdbEpisodeRatingsRepository.kt` y `SeriesGraphApi.kt` (`2e467fad`) en favor de OMDb. Restaurarlas junto a OMDb queda pendiente (NEXT_STEPS).

## D-5 Preferencias solo locales
- El blob de ajustes del perfil (`sync_push_profile_settings_blob`) se reemplaza completo en cada sincronización: los ajustes que Nuvio original no conoce se perderían o lo alterarían.
- Las 5 claves de reproductor de Luqman (`mini_player_enabled`, `swipe_to_seek_enabled`, `movie_recommendations_enabled`, `auto_show_subtitles_on_rewind_enabled`, `auto_show_subtitles_on_mute_enabled`) se retiran de `syncKeys` y de `exportToSyncPayload()`; al recibir un blob remoto **solo se adoptan si no hay valor local** (migración sin pérdida).
- Toda preferencia nueva de Avix (tema, motor por defecto/por serie, subtítulos importados, fuentes, opciones de mpv, ports de Yesnt10…) va a `core/avix/AvixLocalStore` (SharedPreferences `avix_local_preferences` / NSUserDefaults `avix.local.*`), **nunca** a `MobileProfileSettingsBlob`.

## D-6 Autenticación y sincronización intactas
- No se modifican `core/auth/*`, `core/sync/*`, `core/network/Supabase*`, los adaptadores `Supabase*SyncAdapter` ni nombres o parámetros de RPC. Sin servidor propio, sin Mellon, sin cuentas nuevas.
- La identificación ante el servidor (`User-Agent: NuvioMobile/…`, `CLIENT_NAME`) es la misma que en Luqman.
- Configuración del backend oficial: `NUVIO_SUPABASE_URL=https://api.nuvio.tv` y `NUVIO_SUPABASE_ANON_KEY` = `publishable_key` publicado por el propio servidor en `https://api.nuvio.tv/.well-known/nuvio` (documento de descubrimiento para clientes, el mismo formato que usa Nuvio para servidores autoalojados). Se guarda como secreto del repositorio, nunca en el código ni en los logs. Nuvio no documenta una autorización expresa para apps derivadas; los forks Enhanced (Luqman, Yesnt10) usan el mismo backend con su propio secreto privado `NUVIO_LOCAL_PROPERTIES_BASE64`.
- Sin configuración, la app lo indica en la pantalla de inicio de sesión y distingue «sin configurar», «sin conexión» y «credenciales incorrectas» (`core/auth/AuthFailure.kt`).
- Logs: `NuvioLogging.install()` redacta tokens, `apikey`, `Authorization` y JWT de todos los logs y de Sentry (las excepciones de supabase-kt incluyen las cabeceras de la petición).

## D-7 Credenciales de aplicación de terceros
- `ANILIST_CLIENT_ID` y `WETRAKR_CLIENT_ID` pasan a configuración de compilación (en Luqman estaban fijos en el código). Por defecto vacíos: la integración aparece como no disponible hasta configurar un cliente propio registrado con `avixnuvio://`.
- `MAL_CLIENT_ID` (MyAnimeList, portado desde AKRusso/NuvioMobile-Enhanced) sigue la misma regla: configuración de compilación, vacío por defecto, cliente propio de tipo «Other» registrado con `avixnuvio://auth/mal`; sin él la integración aparece como no configurada.
- `SIMKL_APP_NAME` por defecto `avixnuvio`.
- Los cinco client IDs (`ANILIST_`, `MAL_`, `TRAKT_`, `SIMKL_`, `MDBLIST_CLIENT_ID`) se leen de secretos dedicados del repositorio en Debug y Release, con precedencia secreto dedicado → `NUVIO_LOCAL_PROPERTIES_BASE64` → vacío (BUILDING.md). Nunca se incluyen Client Secrets en la APK.

## D-8 Workflows
- Se retiran los workflows heredados (iOS, AltStore, triage de issues, PR-comment) que actuarían sobre este fork.
- Se sustituyen por `ci.yml`, `mpvrx-tests.yml`, `android-release.yml` y `upstream-watch.yml`. Los workflows que lleguen en futuros merges se eliminan.

## D-9 Yesnt10 nunca se fusiona
- Su historial elimina en silencio archivos de upstream. Solo ports manuales: ver UPSTREAMS.md y YESNT_PORT_LEDGER.md.

## D-10 Motor libmpv = mpvRx (`mpvlib 1.1.3`), opción B
- Se **sustituye** `io.github.abdallahmehiz:mpv-android-lib:0.1.12` por `app.gyrolet.mpvlib:mpvlib:1.1.3` (mismo motor que mpvRx). No conviven: ambos AAR contienen `libmpv.so`, `libplayer.so`, `libav*.so`, `libc++_shared.so` con los mismos nombres; no se usa `pickFirst`.
- SHA-256 del AAR verificado en cada build: `1b2161c1b424017a333032fb9644dab53c7ff2c0e1295d62300598382cf4fd7b`.
- `MPVLib` es un singleton (el anterior `MPV` era por instancia): `LibmpvInstanceGuard` garantiza una sola instancia viva y espera a que la anterior se destruya antes de crear otra.
- Sin Anime4K, sin shaders, sin filtros visuales de mpvRx, sin copiar su UI y sin abrir mpvRx como app externa.
- ExoPlayer se mantiene y sigue siendo el motor «Automático» por defecto con fallback a libmpv. Durante la validación, el motor anterior se puede recuperar compilando con `-Pavix.legacyLibmpv=true` (ver MPVRX_INTEGRATION.md).

## D-11 Claves del usuario para servicios de terceros
- Avix no incluye claves de API de terceros en la APK (se pueden extraer). Las funciones que las necesitan (avatares GIF con GIPHY, asistente de IA, búsqueda web Tavily) piden la clave propia del usuario y están desactivadas o vacías por defecto.
- Las claves se guardan cifradas con una clave AES-GCM del Android Keystore (`core/avix/AvixSecretStore`, alias `com.avix.nuvio.secrets.v1`), fuera de `AvixLocalStore`, sin sincronizar y fuera de la copia de ajustes locales. En iOS no hay almacén seguro en v1: esas funciones se ocultan.
- Una clave ilegible (p. ej. tras restaurar el dispositivo) se descarta y el usuario la vuelve a introducir.
- Los datos enviados al proveedor se limitan a la pregunta y los datos públicos del título; solo al proveedor elegido.

## D-12 Sincronización de addons igual que el original
- Con la lista de addons del servidor vacía, Avix ya no sube automáticamente los addons locales (`sync_push_addons`) ni los conserva: sigue al servidor, como Nuvio original desde 1854dfc3 («prevent automatic pulls from restoring deleted data»). La rama de migración la habían reintroducido merges de Luqman.
- El estilo y la posición de la barra de navegación siguen siendo locales (decisión de Luqman, b8c793b9), sin sincronizar.


## D-13 Identidad Nuvio Eibyx (`com.eibyx.nuvio`)
- `applicationId` **`com.eibyx.nuvio`**, debug **`com.eibyx.nuvio.debug`** (antes `com.avix.nuvio` / `com.avix.nuvio.debug`, D-1). Nombre visible «Nuvio Eibyx» («Nuvio Eibyx Debug» en debug). Icono, logotipo, iconos adaptativos y splash sin cambios.
- APK: `Nuvio-Eibyx-Debug.apk`, `Nuvio-Eibyx-Release.apk` (universal) y `Nuvio-Eibyx-Release-<abi>.apk` (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`). Releases de GitHub tituladas «Nuvio Eibyx …».
- No se renombran el `namespace` de Kotlin (`com.nuvio.app`), los paquetes, las clases `Avix*`, el formato de la copia de ajustes (`nuvio-avix-local-settings`), los tags `v*-avix.*` ni los alias del Keystore (`com.avix.nuvio.secrets.v1`, `com.avix.nuvio.mal.credentials.v1`). Los alias del Keystore son por app (UID), así que no chocan con la instalación anterior.
- El esquema `avixnuvio://` y los redirects OAuth registrados (`avixnuvio://auth/{anilist,mal,simkl,trakt}`) **no cambian**: no dependen del paquete.
- El `FileProvider` usa `${applicationId}.fileprovider` y el código `context.packageName`, así que se adaptan solos.
- `versionCode` sigue siendo incremental (`100000 + número de ejecución` en CI, `-Pavix.versionCode` en release).
- **Datos:** Android trata el nuevo paquete como otra app con su propio almacenamiento. No hay migración automática y no se borra nada de la instalación anterior (ver docs/RELEASE.md).

## D-14 OAuth como cliente público (sin Client Secret)
- Trakt usa Authorization Code + PKCE S256 sin `client_secret` (documentación oficial de Trakt; el secreto está obsoleto para el inicio de sesión de usuarios). `TRAKT_CLIENT_SECRET` deja de leerse y no se incluye en la APK.
- Simkl pasa a AUTH V2 (`/oauth2/*`, PKCE S256, refresh token, revocación).
- Se mantienen los redirects registrados `avixnuvio://auth/{anilist,mal,simkl,trakt}`. No se usa un App Link HTTPS porque no hay dominio propio (no se inventa). Detalles: docs/OAUTH.md.

## D-15 Release solo ARM64 con R8
- La release publicada es una única APK `Nuvio-Eibyx-Release-arm64-v8a.apk` (split ABI de Gradle, `-Pavix.abis=arm64-v8a`), sin universal ni otras ABI. Las otras ABI siguen disponibles con `abis=all`.
- R8 (minificación + reducción de recursos, `proguard-android-optimize.txt` + `composeApp/proguard-rules.pro`) sin cambios de reglas: el puente JNI de mpvRx (`is.xyz.mpv`) ya se conserva; `verify-release-apk.py` comprueba con el mapping de R8 que ningún método nativo exportado fue renombrado (los no usados que R8 elimina se informan).
- Se mantiene `subfont.ttf` (fuente de reserva de libass para subtítulos ASS/SSA) y todas las traducciones.
- La firma release solo se aplica si están las cuatro credenciales (`AVIX_RELEASE_*`); sin ellas la APK queda sin firmar y el workflow la rechaza. Nunca se firma con la clave de depuración.
