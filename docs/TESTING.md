# Pruebas de Nuvio (Avix)

Estados: **PASS** (ejecutada y superada) · **FAIL** (ejecutada y fallida) · **SKIPPED** (omitida a propósito) · **BLOCKED** (no se puede ejecutar en el entorno disponible) · **NOT RUN** (aún no ejecutada). Nunca se marca PASS una prueba que no se haya ejecutado.

## Comandos

| Comando | Qué cubre |
|---|---|
| `./gradlew :composeApp:testAndroidHostTest -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` | Tests unitarios de `commonTest` y `androidHostTest` (JVM + Robolectric) |
| `./gradlew :androidApp:assembleFullDebug …` | APK completa |
| `./gradlew :androidApp:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nuvio.android.MpvRxEngineSmokeTest …` | Motor mpvRx en emulador/dispositivo (workflow `mpvrx-tests.yml`) |

## Resultados registrados

### 2026-10-15 — local (contenedor Linux, JDK 21, sin KVM)

| Prueba | Resultado | Evidencia |
|---|---|---|
| Compilación `:androidApp:assembleFullDebug` (libmpv anterior) | PASS | APK `androidApp-full-debug.apk`, 166 MB |
| Compilación con motor mpvRx (`mpvlib 1.1.3`) + APK de tests instrumentados | PASS | APK 184 MB; `androidApp-full-debug-androidTest.apk` 1,3 MB |
| SHA-256 del AAR descargado | PASS | `1b2161c1…fd7b` (coincide) |
| La APK empaqueta las `.so` de mpvlib | PASS | `libmpv.so` y `libplayer.so` (arm64-v8a) idénticas byte a byte a las del AAR |
| Suite `testAndroidHostTest` con motor mpvRx | FAIL (24 de 1689) | **Mismo conjunto exacto** de fallos que la línea base (diff vacío) |
| `LocalSubtitleImportTest` (11) | PASS | `TEST-…LocalSubtitleImportTest.xml` 11/11 |
| `AppUrlBridgeTest` (8) con `avixnuvio://` | PASS | 8/8 |
| `InfusePlaybackTest` (11) con `avixnuvio://` | PASS | 11/11 |
| Suite completa `testAndroidHostTest` | FAIL (26 de 1661) | Ver abajo |
| Línea base: misma suite en el merge `6ac3eb66` (solo con los arreglos de compilación de tests) | FAIL (24 de 1678) | Mismos 22 tests reales + 2 de `RootTabHostTest` |

**Conclusión:** los fallos reales ya existen en la base Luqman+original; los cambios de Avix no añaden ninguno. Las 4 diferencias son errores de infraestructura del contenedor: Robolectric no pudo descargar `android-all-instrumented` (Maven Central responde 429) en `RootTabHostTest`, `StreamOrientationTest`, `ReleaseDateDisplayTest` y `LocalAccountDataCleanerTest`.

Fallos heredados (pendientes de corregir; no se ocultan ni se desactivan):
`PluginSyncTest`, `AvatarPickerTest`, `RootTabHostTest` (2), `ExternalPlaybackProgressTest`, `BackendRateLimitTest`, `WatchProgressIdentityTest`, `HomeHeroSectionTest` (3), `DownloadSubtitlesTest`, `RatingsVisibilityTest`, `WhatsNewSnapshotBuilderTest` (3), `PlayerGestureOverlayTest` (5), `AndroidDownloadLifecycleTest` (2), `HomeHeroPagerTest` (2).

### 2026-10-15 — GitHub Actions

| Prueba | Resultado | Evidencia |
|---|---|---|
| CI run #2 (`a44828db`): APK debug | PASS (compila) | Artefacto `Nuvio-Avix-Debug-2` |
| `MpvRxEngineSmokeTest` API 34 / API 35 16 KB (`a44828db`) | **FAIL 7/7** | run 38819658331: `libmpv.so` no carga, la APK llevaba el `libc++_shared.so` antiguo de `ass-kt` |
| Corrección local: `libc++_shared.so` de mpvlib en las 4 ABI + `verifyAvixCxxRuntimeFullDebug` | PASS | hashes idénticos; símbolo `__hash_memory` presente |
| `MpvRxEngineSmokeTest` tras la corrección (`643b58c6`) | **PASS 7/7 en API 34 y 7/7 en API 35 (16 KB)** | run 38824203030; logcat: `mpv v0.41.0-dev-gc3ac7f395-dirty`, `ffmpeg n9.0.2`, `pageSize=16384` en API 35 |

### 2026-10-15 — tema claro (local)

| Prueba | Resultado | Evidencia |
|---|---|---|
| `LightPaletteContrastTest` (13 paletas: texto ≥ 7:1, secundario/atenuado ≥ 4,5:1, acento y texto sobre acento ≥ 3:1) | PASS 3/3 | `TEST-…LightPaletteContrastTest.xml` |
| Suite `testAndroidHostTest` con tema claro | FAIL (24 de 1692) | **Mismo conjunto exacto** que la línea base |
| APK + `verifyAvixCxxRuntimeFullDebug` | PASS | APK 190 MB |
| Revisión visual en modo claro (todas las pantallas) | NOT RUN | requiere dispositivo |

### 2026-10-16 — Fase 2: los 23 fallos heredados de tests unitarios

Regla: ningún test se desactiva, elimina ni debilita. Cada fallo se clasificó como **regresión del código** (se corrige el código, preferentemente al comportamiento del Nuvio original) o **test desactualizado por un cambio deliberado** (se actualiza el test para comprobar el comportamiento nuevo con la misma fuerza).

| Test | Causa | Decisión |
|---|---|---|
| `RootTabHostTest` ×2 | Luqman añadió la pestaña `LiveTv`; el test recorría solo 4 pestañas pero esperaba todas | Test: recorre también Live TV |
| `BackendRateLimitTest` | Luqman limitó a 30 s también el `Retry-After` del servidor | Código: se respeta el `Retry-After` completo, como el original |
| `WhatsNewSnapshotBuilderTest` ×2 (IMDb) | El código normaliza a «IMDb» a propósito (patrón propio) | Test: espera «IMDb» |
| `WhatsNewSnapshotBuilderTest` (mes) | `periodUntil` cuenta 31 ene → 28 feb como 0 meses | Código: un mes natural (con fin de mes) cuenta como 1 mes |
| `PluginSyncTest` | Los merges de Luqman reintrodujeron una subida automática `sync_push_addons` cuando el servidor está vacío, que upstream retiró (1854dfc3) | Código: igual que el original (no se sube nada automáticamente; D-6) |
| `AndroidDownloadLifecycleTest` ×2 | Cambio deliberado (8591da78): un trabajo de descarga perdido se reprograma en lugar de pausarse | Test: comprueba la reprogramación (estado, sin error, mismo job) |
| `DownloadSubtitlesTest` | Los subtítulos se descargan en paralelo al vídeo (54ae5bdc) | Test: comprueba que todo existe al completar, no el orden |
| `WatchProgressIdentityTest`, `RatingsVisibilityTest` (2.ª parte) | Duraciones de 1 s: upstream (80860602) trata < 2 min como clip de relleno que nunca se completa | Test: mismo 94–95 % sobre un episodio de 40 min |
| `ExternalPlaybackProgressTest` (intermitente) | `clearLocalState()` descartaba el guardado en segundo plano pendiente (4bd6c3b8) | Código: se escribe lo pendiente antes de limpiar; preparación del test ordenada |
| `PlayerGestureOverlayTest` ×5 | Umbral de deslizamiento ×3 deliberado (c30367ac) y lectura «40%» deliberada (19fe73ce) | Test: supera el nuevo umbral; espera «40%» |
| `HomeHeroPagerTest` ×2 | El hero mantiene compuestas las páginas vecinas ocultas (rendimiento) y seguían en el árbol de semántica/TalkBack | Código: páginas fuera de pantalla sin semántica |
| `HomeHeroSectionTest` ×3 | Hero vertical de Luqman (ancho × 1,5) ignoraba el espacio para «Continuar viendo» y pantallas cortas | Código: se mantiene el tamaño de Luqman pero limitado por el espacio de «Continuar viendo»; tests actualizados al tamaño vertical y uno nuevo para pantalla corta |
| `AvatarPickerTest` | Avatares sin imagen perdieron su etiqueta de accesibilidad en un merge | Código: etiqueta restaurada |
| `RatingsVisibilityTest` | El merge dejó de usar las valoraciones obtenidas y las del addon en dos diseños de episodio | Código: los tres diseños usan las mismas reglas |
| Entorno de CI | `AndroidDownloadLifecycleTest` usa `@Config(sdk = [34, 36])`; Robolectric necesita Java 21 para SDK 36 y CI usaba JDK 17 (`UnsupportedOperationException` en `DefaultSdkProvider`) | CI pasa a JDK 21 (el mismo con el que la suite completa pasa en local) |

### Motor mpvRx — pruebas 1–20

Detalle y procedimiento en [MPVRX_INTEGRATION.md](MPVRX_INTEGRATION.md). Las pruebas instrumentadas se ejecutan en GitHub Actions (`mpvrx-tests.yml`); el contenedor de desarrollo no tiene `/dev/kvm`.

| # | Prueba | Nivel | Resultado |
|---|---|---|---|
| 1 | Inicialización / JNI | Instrumentada (`t01`) | **PASS** (API 34, API 35 16 KB) |
| 2 | MKV | Instrumentada (`t02`, decodificación sin salida) | **PASS** (headless) |
| 3 | Audio | Instrumentada (`t02`, pista AAC detectada y decodificada con `ao=null`) | **PASS** (headless; sonido real: NOT RUN) |
| 4 | ASS/SSA | Instrumentada (`t02`/`t03`: eventos vía `sub-text`); render → ASS_SSA_TESTS.md | **PASS** eventos; render: NOT RUN |
| 5 | Fuentes incrustadas | Manual (ASS_SSA_TESTS.md) | NOT RUN |
| 6 | Subtítulos externos | Instrumentada (`t04`, SRT Windows-1252 + `sub-add`) | **PASS** (texto decodificado por iconv/uchardet) |
| 7 | Cambio de pistas | Instrumentada (`t04`: segunda pista seleccionada) + manual | **PASS** (instrumentada); manual: NOT RUN |
| 8 | Búsqueda (seek) | Instrumentada (`t03`) | **PASS** |
| 9 | Cambio de calidad | Manual en dispositivo | NOT RUN |
| 10 | Addons | Manual en dispositivo | NOT RUN |
| 11 | Cabeceras HTTP | Instrumentada (`t05`, servidor local) | **PASS** (`User-Agent` y `X-Avix-Test` recibidas) |
| 12 | Progreso / historial | Manual | NOT RUN |
| 13 | PiP | Manual | NOT RUN |
| 14 | Abrir/cerrar repetidamente | Instrumentada (`t06`, 5 ciclos) + manual en la app | **PASS** (instrumentada); en la app: NOT RUN |
| 15 | Cambio de motor | Manual (selector en el reproductor) | NOT RUN |
| 16 | Rotación / segundo plano | Manual | NOT RUN |
| 17 | Memoria | Instrumentada (`t06`, heap nativo < 64 MB tras 5 ciclos) | **PASS** (crecimiento ≈ 0 MB) |
| 18 | ABI | Instrumentada (`t07`) en x86_64; ARM64 → dispositivo real | **PASS** x86_64; ARM64: NOT RUN |
| 19 | Rendimiento | Manual | NOT RUN |
| 20 | Páginas de 16 KB | Estática (40 `.so` de 64 bits ≥ `0x4000`) + emulador API 35 `google_apis_ps16k` | **PASS** (estática y en ejecución, `pageSize=16384`) |

Prueba 20, evidencia estática (`readelf -lW`): todas las `.so` de `arm64-v8a` y `x86_64` de `mpvlib-1.1.3.aar` y de la APK final (40 bibliotecas, incluidas las de Media3, ass-media y nuvio-engine) tienen alineación ≥ `0x4000`. Las `.so` se guardan comprimidas en la APK (se extraen al instalar), así que la alineación de zip no aplica. En `armeabi-v7a`/`x86` (`libc++_shared.so`, `libplayer.so`) es `0x1000`, irrelevante porque las páginas de 16 KB solo existen en ABIs de 64 bits.

Una prueba headless (`vo=null`, `ao=null`) **no** demuestra imagen, sonido ni fidelidad de libass.

## Manuales en dispositivo (checklist por versión)

Anotar el modelo, la versión de Android, la APK (run o tag) y el resultado.

### Identidad e instalación
- [ ] Se instala junto a `com.nuvio.app`, `com.nuvio.enhanced` y `com.nuvio.media`, sin conflicto
- [ ] El nombre es «Nuvio» y el icono es el original
- [ ] Los enlaces `avixnuvio://…` abren Avix y no otra app

### Cuenta de Nuvio (con la cuenta y la configuración autorizada del propietario)
- [ ] Inicio de sesión con correo y contraseña
- [ ] Aparecen perfiles, addons, biblioteca y Seguir viendo
- [ ] Cruzada: un cambio de biblioteca hecho en Nuvio original aparece en Avix y viceversa
- [ ] Un ajuste exclusivo (p. ej. Mini reproductor) **no** se pierde después de cambiar ajustes en Nuvio original

### Reproducción y subtítulos
- [ ] ExoPlayer y libmpv (mpvRx): HLS, MP4 y MKV de un addon
- [ ] Cambio de motor en el reproductor: misma posición, velocidad, audio y subtítulo; sin entrada duplicada en el historial
- [ ] Importar subtítulo local (SRT, ASS) con ambos motores; error claro con un archivo no válido sin cerrar el reproductor
- [ ] ASS/SSA: procedimiento comparativo de [ASS_SSA_TESTS.md](ASS_SSA_TESTS.md)

## Entorno de desarrollo sin acceso a Maven Central

Si `repo1.maven.org` responde 429, se puede usar el mirror de Google con un init script local de Gradle (no se versiona):
`~/.gradle/init.d/maven-mirror.init.gradle.kts` → `https://maven-central.storage-download.googleapis.com/maven2/`.
