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

## D-7 Credenciales de aplicación de terceros
- `ANILIST_CLIENT_ID` y `WETRAKR_CLIENT_ID` pasan a configuración de compilación (en Luqman estaban fijos en el código). Por defecto vacíos: la integración aparece como no disponible hasta configurar un cliente propio registrado con `avixnuvio://`.
- `SIMKL_APP_NAME` por defecto `avixnuvio`.

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
