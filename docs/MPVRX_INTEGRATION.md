# Motor mpvRx en Nuvio (Avix)

## Qué se integra

Solo el **motor**: la biblioteca `app.gyrolet.mpvlib:mpvlib:1.1.3` (Riteshp2001/mpvlibAndroid), la misma que usa mpvRx. **No** se integra: Anime4K, shaders, filtros visuales de mpvRx, su interfaz, ni la apertura de mpvRx como app externa. El reproductor sigue siendo el de Nuvio/Luqman (controles, gestos, PiP, subtítulos, historial).

| | Anterior | mpvRx (`mpvlib 1.1.3`) |
|---|---|---|
| Artefacto | `io.github.abdallahmehiz:mpv-android-lib:0.1.12` | `app.gyrolet.mpvlib:mpvlib:1.1.3` |
| mpv | v0.41.0-174-g76a5eba99 | v0.41.0-dev-gc3ac7f395 (979 commits más reciente) |
| libass | 0.17.4-21 | 0.17.5-11 |
| FFmpeg | N-122742 | n9.0.2 |
| iconv / uchardet | No (`-Diconv=disabled`) | Sí |
| Vulkan | No | Sí |
| Fuente de respaldo (`subfont.ttf`) | Droid Sans Fallback | Go Noto Current |
| API Java/Kotlin | `is.xyz.mpv.MPV` **por instancia** + `BaseMPVView` | `is.xyz.mpv.MPVLib` **singleton** + `BaseMPVView` |

## Dependencia y verificación

- Repositorio: releases de GitHub de mpvlibAndroid, declarado en `settings.gradle.kts` como repositorio Ivy **exclusivo** para el grupo `app.gyrolet.mpvlib` (patrón `v[revision]/[artifact]-[revision].aar`).
- `:composeApp:verifyMpvlibChecksum` calcula el SHA-256 del AAR resuelto y **detiene la compilación** si no es `1b2161c1b424017a333032fb9644dab53c7ff2c0e1295d62300598382cf4fd7b`. Todas las tareas de compilación Kotlin dependen de ella.
- No conviven los dos AAR: ambos traen `libmpv.so`, `libplayer.so`, `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libavfilter.so`, `libavdevice.so`, `libswresample.so`, `libswscale.so` y `libc++_shared.so`. No se usa `pickFirst`.

## Runtime C++ (`libc++_shared.so`)

`ass-kt 0.5.1` (ruta ExoPlayer/libass) y `mpvlib 1.1.3` incluyen su propio `libc++_shared.so`, compilados con NDK distintos. La regla heredada `pickFirsts` empaquetaba el de `ass-kt` (más antiguo) y `libmpv.so` no cargaba: `UnsatisfiedLinkError: cannot locate symbol "_ZNSt6__ndk113__hash_memoryEPKvm"` (detectado por `MpvRxEngineSmokeTest` en el emulador, run 38819658331). libc++_shared es compatible hacia atrás, así que:

- `extractAvixCxxRuntime` extrae el `libc++_shared.so` de mpvlib a `androidApp/build/generated/avixCxxRuntime/<abi>/`, que se añade como `jniLibs` propio de la app (prioritario frente a dependencias);
- `verifyAvixCxxRuntime<Variant>` (dependencia de `assemble<Variant>`) comprueba que cada `lib/<abi>/libc++_shared.so` de cada APK es idéntico al de mpvlib y **falla la compilación** si no.

## Volver al motor anterior (validación)

```bash
./gradlew :androidApp:assembleFullDebug -Pavix.legacyLibmpv=true -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore
```

La selección se hace en Gradle: cambia la dependencia y el archivo `LibmpvBackend.kt` que se compila (`composeApp/src/androidLibmpvMpvrx/` o `composeApp/src/androidLibmpvLegacy/`). Ambos exponen los mismos nombres (`MpvHandle`, `mpvLib`, `MpvEventObserver`, `MpvLogObserver`, `MpvConstants`, `LIBMPV_BACKEND_NAME`), así que `PlayerEngine.android.kt` no cambia. El log del reproductor indica el motor: `libmpv backend=…`.

## Singleton: `LibmpvInstanceGuard`

Con el API anterior cada vista tenía su propio núcleo. Con `MPVLib` solo puede existir uno por proceso, y en el código nativo de mpv-android `create()` con un núcleo vivo y `destroy()` sin núcleo **abortan el proceso**. Situaciones afectadas: cambio de motor en el reproductor, siguiente episodio, reabrir el reproductor.

`composeApp/src/androidMain/.../player/LibmpvInstanceGuard.kt`:
- antes de `initialize()`, la vista adquiere la propiedad del núcleo y espera (máx. 3 s, por debajo del umbral de ANR) a que la vista anterior termine su `destroy()`;
- si no lo consigue, la inicialización falla de forma controlada (`latestOnError`) y el reproductor aplica su fallback a ExoPlayer; la vista fallida queda inerte (no toca `MPVLib`);
- solo la vista propietaria, y solo si su núcleo se creó por completo, llama a `MPVLib.destroy()`; después libera la propiedad.

## Opciones de subtítulos

`initOptions()`: `sub-ass-override=no` (ASS tal como fue escrito), `embeddedfonts=yes`, `sub-font-provider=auto`, `sub-fonts-dir=<filesDir>/fonts` (fuentes importadas por el usuario). Con mpvlib, `sub-codepage=auto` funciona gracias a iconv/uchardet; la recodificación previa a UTF-8 de subtítulos externos se mantiene (inocua con UTF-8 y necesaria con el motor anterior).

## Pruebas

`androidApp/src/androidTest/kotlin/com/nuvio/android/MpvRxEngineSmokeTest.kt`, ejecutada por `.github/workflows/mpvrx-tests.yml` en emulador x86_64 API 34 y API 35 con páginas de 16 KB.

Fixture `androidApp/src/androidTest/assets/avix_fixture.mkv` (84 KB):
```bash
ffmpeg -f lavfi -i testsrc=duration=6:size=160x90:rate=24 -f lavfi -i sine=frequency=440:duration=6 -i sub.ass \
  -map 0 -map 1 -map 2 -c:v mpeg4 -q:v 8 -c:a aac -b:a 32k -c:s ass \
  -metadata:s:a:0 language=spa -metadata:s:s:0 language=spa -metadata:s:s:0 title="Signs" avix_fixture.mkv
```

| Test | Comprueba |
|---|---|
| `t01_jniLoadsAndReportsEngineVersions` | Carga JNI, `create/init`, `mpv-version` empieza por `mpv v0.41`, `ffmpeg-version` |
| `t02_decodesMkvWithVideoAudioAndAssTracks` | Avance de reproducción, pistas mpeg4/aac/ass, texto del evento ASS (`sub-text`) |
| `t03_seekReachesTargetAndNextSubtitleEvent` | Seek absoluto y evento ASS posterior |
| `t04_externalWindows1252SubtitleIsDecodedByEngine` | `sub-add` de un SRT Windows-1252 decodificado por el motor (iconv/uchardet) |
| `t05_httpRequestCarriesConfiguredHeaders` | `user-agent` y `http-header-fields` llegan al servidor (servidor HTTP local) |
| `t06_repeatedCreateDestroyKeepsNativeHeapBounded` | 5 ciclos create/load/destroy; crecimiento del heap nativo < 64 MB |
| `t07_reportsAbiAndPageSize` | ABI, tamaño de página, núcleo operativo |

Es una prueba **sin salida de vídeo ni audio** (`vo=null`, `ao=null`): no demuestra imagen, sonido ni fidelidad de render de libass. Los niveles se validan por separado: compilación → JNI → decodificación → audiovisual → subtítulos → UI. Estado de cada prueba 1–20: [TESTING.md](TESTING.md).

## Licencias

- `mpvlib` (wrapper Java/JNI y scripts de build de mpvlibAndroid): MIT.
- Componentes nativos: los scripts de build de mpvlibAndroid (rama principal, **no fijada a la 1.1.3**; puede diferir) compilan mpv, FFmpeg, libass, FreeType, FriBiDi, HarfBuzz, libunibreak, libiconv, uchardet, libplacebo, shaderc, lcms2, dav1d, libxml2, libmysofa, libaribcaption, mbedtls, OpenSSL, curl, Lua, MuJS, rubberband, libbluray, libdvdread/libdvdnav, libarchive, bzip2, xz y zstd. Sus licencias (LGPL/GPL, MIT, BSD, ISC, Apache-2.0, MPL, OFL para Go Noto Current…) son compatibles con la GPL-3.0 de la APK; rubberband (GPL-2.0-or-later) hace que el conjunto sea GPL. **Pendiente antes de una release pública:** confirmar qué componentes y versiones contiene exactamente el AAR 1.1.3 y listar cada licencia.
- mpvRx (AGPL-3.0-or-later): solo referencia; no se copia su código de aplicación.
