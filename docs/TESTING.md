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

### Motor mpvRx — pruebas 1–20

Detalle y procedimiento en [MPVRX_INTEGRATION.md](MPVRX_INTEGRATION.md). Las pruebas instrumentadas no pueden ejecutarse en este contenedor (no hay `/dev/kvm`); se ejecutan en GitHub Actions (`mpvrx-tests.yml`), que requiere tener Actions habilitado en el fork.

| # | Prueba | Nivel | Resultado |
|---|---|---|---|
| 1 | Inicialización / JNI | Instrumentada (`t01`) | NOT RUN |
| 2 | MKV | Instrumentada (`t02`, decodificación sin salida) | NOT RUN |
| 3 | Audio | Instrumentada (`t02`, pista AAC detectada y decodificada con `ao=null`) | NOT RUN |
| 4 | ASS/SSA | Instrumentada (`t02`/`t03`: eventos vía `sub-text`); render → ASS_SSA_TESTS.md | NOT RUN |
| 5 | Fuentes incrustadas | Manual (ASS_SSA_TESTS.md) | NOT RUN |
| 6 | Subtítulos externos | Instrumentada (`t04`, SRT Windows-1252 + `sub-add`) | NOT RUN |
| 7 | Cambio de pistas | Instrumentada (`t04`: segunda pista seleccionada) + manual | NOT RUN |
| 8 | Búsqueda (seek) | Instrumentada (`t03`) | NOT RUN |
| 9 | Cambio de calidad | Manual en dispositivo | NOT RUN |
| 10 | Addons | Manual en dispositivo | NOT RUN |
| 11 | Cabeceras HTTP | Instrumentada (`t05`, servidor local) | NOT RUN |
| 12 | Progreso / historial | Manual | NOT RUN |
| 13 | PiP | Manual | NOT RUN |
| 14 | Abrir/cerrar repetidamente | Instrumentada (`t06`, 5 ciclos) + manual en la app | NOT RUN |
| 15 | Cambio de motor | Manual (selector en el reproductor) | NOT RUN |
| 16 | Rotación / segundo plano | Manual | NOT RUN |
| 17 | Memoria | Instrumentada (`t06`, heap nativo < 64 MB tras 5 ciclos) | NOT RUN |
| 18 | ABI | Instrumentada (`t07`) en x86_64; ARM64 → dispositivo real | NOT RUN |
| 19 | Rendimiento | Manual | NOT RUN |
| 20 | Páginas de 16 KB | Estática: segmentos `LOAD` de las 40 `.so` de 64 bits de la APK (arm64-v8a, x86_64) ≥ `0x4000` | **PASS (estática)** — en ejecución (emulador `google_apis_ps16k`): NOT RUN |

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
