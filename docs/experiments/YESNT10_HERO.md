# Experimento: Hero de yesnt10 en NuvioEibyx

## Base y alcance

- Destino: `Eibyx/NuvioMobileEnhanced`, `experiment/yesnt10-hero`.
- Base intacta: `integration/avix`, `f1ef8013a0f7e50c3cf94440669e03fc65a4af7a`.
- Fuente KMP: `yesnt10/NuvioMobile-Enhanced`, `enhanced`, `894499115fb1a69cd4bb34d6969a74832f0e5636` (HEAD remoto comprobado).
- Atribución GPL-3.0 conservada. No se usa React Native ni Nuvio Infinity.
- No se importan las capas, painters retenidos, cambios globales de Coil ni precargas de `fix/image-pipeline-performance`.

## Componentes adaptados

`HomeHeroSection.kt` incorpora el renderer de StreamingShowcase y Poster Art, los controles, gradientes, logo con sombra, indicadores, tipografía cinematográfica, alturas adaptables, gesto circular y movimiento del artwork de yesnt10. El pager finito controla la selección; su contenido no dibuja otros banners. Hay **un solo artwork principal**. La selección sigue `targetPage` durante el movimiento y `currentPage` al terminar, igual que la referencia. No se retiene una segunda imagen para interpolar alpha entre contenidos.

Adaptaciones locales respecto a yesnt10:

- Identidad de cada slot: `type:id:page`, separada de la identidad compartida de metadata `type:id`.
- El mapa reactivo de metadata se lee directamente: no queda capturado en un `remember` que impida actualizar el artwork.
- El logo y su sombra utilizan una única solicitud Coil y dos dibujos del resultado, con las mismas dimensiones y transformaciones.
- Se mantienen Eibyx Card, overscroll, paletas, acciones, controles de sonido y lifecycle del Home.
- El Hero es una isla `NuvioForceDarkTheme`; el tema global y las fuentes del dispositivo siguen siendo los de Eibyx.
- “Guardar/Mi lista” abre las acciones existentes de Eibyx. Las confirmaciones, listas y escrituras a proveedores siguen bajo su controlador actual; no se añade un segundo escritor de Tracking.
- `HomeHeroCompatibility.kt` conserva cálculos de tamaños y contratos existentes, no el antiguo renderer multicapa.

## Recorrido del artwork

1. Home conserva catálogos, colecciones y snapshots de Eibyx.
2. Si TMDB/artwork está habilitado y configurado, `HomeRepository.resolveLocalizedHeroArtwork` resuelve primero un preview y luego lotes de hasta tres. La ventana inicial es de 1.500 ms y las posteriores de 4.000 ms, tomadas de la estrategia de yesnt10; el primer elemento no espera al resto de su lote. Cada fallo devuelve **el preview de ese mismo contenido**. Un snapshot válido permanece disponible durante el refresco.
3. `TmdbMetadataService.resolveHeroArtwork` comparte trabajos simultáneos por cuenta/perfil, tipo, ID e idioma. Limita a tres resoluciones remotas y ocho segundos por resolución. El trabajo acotado puede completar la caché aunque un consumidor cambie de página; no publica en un perfil distinto.
4. `fetchHeroArtwork` obtiene únicamente detalles básicos e imágenes TMDB en paralelo: poster localizado w780 (fallback w500), backdrop preferentemente sin texto w1280 y logo localizado w500. No consulta créditos, recomendaciones ni temporadas.
5. `TmdbPreviewArtworkStorage` guarda datos públicos en un único archivo mediante `AppFileCache`, con máximo de 128 entradas, caducidad de siete días y aislamiento por cuenta/perfil e idioma. No guarda tokens ni bitmaps.
6. `HeroMetadataSession` prioriza el elemento seleccionado y después sus dos vecinos distintos. Publica artwork antes del addon, tráilers y ratings; comparte el trabajo de una sesión entre swipes y lo cancela al salir de composición o cambiar su contexto. Los fallos opcionales no derriban el Hero; la cancelación se propaga.
7. El addon usa `fetchLightweight(..., includeTmdbFallback = false)`; el comportamiento predeterminado del resto de consumidores permanece igual. Los tráilers TMDB consultan vídeos del título, sin enumerar temporadas. No hace falta portar `TmdbEpisodeEnrichmentStorage`: los episodios ya no son una dependencia de este recorrido. Details mantiene su enriquecimiento completo.
8. `HeroArtworkImage` hace una solicitud visible dimensionada por Compose, con fallback finito del mismo contenido y el interceptor de posters personalizados existente. No utiliza `Size.ORIGINAL`, claves globales artificiales ni `ImageLoader.enqueue()`.

Coil 3.6.3, SingletonImageLoader, políticas de memoria/disco, estrategia HTTP, decodificadores y dependencias permanecen iguales a f1ef8013. Dos solicitudes no implican dos descargas; no se promete que URLs o tamaños diferentes compartan una representación decodificada.

Los snapshots guardan el idioma del artwork, conservan compatibilidad con el formato previo y no restauran artwork localizado de otro idioma. Se permite restaurar/guardar Hero procedente exclusivamente de colecciones. La escritura diferida comprueba perfil e idioma antes de guardar.

## Preferencias y vídeo

- Predeterminado: StreamingShowcase de yesnt10.
- La opción existente de poster selecciona Poster Art de yesnt10; sus valores almacenados no se borran.
- El botón Play de Poster Art abre la ruta existente de Details/selección de fuente de Eibyx; no introduce un segundo flujo de reproducción.
- Card conserva su contenedor/tamaño opcional alrededor del nuevo Hero.
- Autorrotación: intervalo de yesnt10 (5,5 s; triple con preview de vídeo), con pausa durante interacción y fuera de la ruta activa.
- Sinopsis, ratings, metadata compacta y modo cinematográfico/ampliado se adaptan al nuevo contenido.
- Movimiento cinematográfico: nueva opción local, sin duplicar la de vídeo.
- Tráilers: preferencias existentes de activación, retraso y sonido inicial; el botón de sonido conserva el estado de audio de Eibyx.
- Android Full comunica disponibilidad al **primer frame renderizado**, no a STATE_READY. El artwork sigue debajo del vídeo. La superficie se identifica por contenido y fuentes; salir de Home, cambiar de página o activar miniplayer retira la preview.
- No se modifica el reproductor principal, ExoPlayer de reproducción, mpvRx, ASS/SSA, PiP ni los repositorios de Tracking.

## Compilación experimental

```bash
./gradlew :androidApp:assembleFullDebug \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore \
  -Pavix.debug.applicationId=com.eibyx.nuvio.yesnthero.debug \
  -Pavix.debug.abis=arm64-v8a --stacktrace
```

Ambas propiedades Debug son opcionales. Sin ellas permanecen `com.eibyx.nuvio.debug` y sus ABI habituales. No alteran el ID estable ni los splits de Release. Se conserva la verificación del AAR mpvlib y de su `libc++_shared.so`.

El workflow manual `yesnt10-hero-debug.yml` sólo admite esta rama y repositorio, firma con la configuración estándar Debug y adjunta APK, commit, badging y SHA-256 durante 14 días. No usa firma de producción ni publica releases. El CI ya existente también genera el ID/ABI experimentales únicamente para esta rama. Un workflow nuevo puede necesitar estar registrado en la rama predeterminada para aparecer en el selector manual de GitHub; el CI de push no depende de ello.

El esquema OAuth existente `avixnuvio://` se conserva. Al instalar varias aplicaciones, Android puede pedir seleccionar qué aplicación abre un retorno OAuth; la identidad Debug independiente no registra nuevos clientes OAuth. FileProvider utiliza `${applicationId}` y queda separado. No se cambian ni copian credenciales privadas.

## Diagnósticos y comparación reproducible

`HeroImageDiagnostics.enabled` está desactivado por defecto; un desarrollador puede habilitarlo temporalmente mediante debugger. Registra `requestStart`, éxito/error/cancelación, fuente real de Coil, dimensiones decodificadas, hash de la clave de memoria y primer `drawSubmitted`. No imprime URLs ni cabeceras. `drawSubmitted` significa que Compose emitió un dibujo, no prueba de presentación física. La medición local no separa fetch/decode ni 200/304: para ello se necesita una captura de red y una sesión Perfetto/FrameTimeline o un EventListener de diagnóstico en una compilación de medición separada, sin cambiar las políticas de caché de la aplicación.

Comparar f1ef8013, 89449911 y este experimento en el mismo teléfono, proveedor, perfil, idioma y conectividad. Distinguir instalación limpia, proceso reiniciado con datos preservados y memoria caliente. Registrar disponibilidad de URL, petición, resultado y FrameTimeline; NETWORK no demuestra transferencia completa (puede existir revalidación).

Casos manuales obligatorios: 1/2/3/8 elementos; swipes lentos/rápidos y circular; autorrotación; refresh; perfil/idioma; posters personalizados y fallidos; offline/online; preview con y sin audio; entrada/salida de Home; miniplayer; tablet/rotación; Details y retorno. Sword Art Online, Clannad, Re:Zero y Howl son ejemplos adicionales, no fixtures obligatorios.

No se ha certificado equivalencia visual, ausencia de ghosting en GPU real, latencia comparable a yesnt10 ni iOS. Android host tests no sustituyen pruebas en teléfono. Details no cambia de diseño ni eleva su painter fuera del LazyColumn: la retención al hacer scroll queda fuera de este experimento del Hero.

## Validación ejecutada (2026-10-17)

- JDK 21, plataforma Android 37 y build-tools 36.0.0 del entorno preparado.
- `:composeApp:testAndroidHostTest` con distribución Full: **1.819 pruebas, 0 fallos, 0 errores y 0 omitidas**. Incluye 21 pruebas nuevas; las aserciones previas del pager se conservan, adaptando sólo el título a mayúsculas del diseño original.
- `:androidApp:assembleFullDebug`, con ID y ABI experimentales: **BUILD SUCCESSFUL**.
- Firma APK v2 verificada: certificado estándar Android Debug; minSdk 24, targetSdk 36.
- Badging y ZIP: `com.eibyx.nuvio.yesnthero.debug`, únicamente `arm64-v8a`, integridad correcta, 20 bibliotecas nativas (incluidas libass, libasskt, libmpv, libplayer, libnuvio_engine y libquickjs).
- `verifyMpvlibChecksum` y `verifyAvixCxxRuntimeFullDebug` superadas. El runtime C++ empaquetado coincide byte a byte con el AAR mpvlib 1.1.3 verificado.
- El primer intento de tests se bloqueó por la caché Robolectric en `/home/agent` de sólo lectura. Se resolvió con `-Duser.home=/workspace -Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2` en `JAVA_TOOL_OPTIONS`; no se modificaron dependencias ni se omitieron pruebas. Una aserción nueva buscaba “My list” y se corrigió al texto real “Save”.
- No hay emulador instalado. ADB no pudo inicializar su directorio de usuario en este sandbox; no se realizó instalación ni benchmark en teléfono.
- iOS requiere compilación/ejecución en macOS. No se certifica mediante el build Android.

Para reproducir la apariencia base de yesnt10, seleccionar Full-bleed, desactivar Poster Art y mantener movimiento cinematográfico. Las opciones previamente guardadas de Card/Poster Art siguen aplicándose hasta que el usuario las cambie.

Los resultados anteriores validan código y empaquetado. No acreditan todavía los tiempos de dos segundos observados por el usuario, ausencia de ghosting en un teléfono, estabilidad de vídeo real ni todos los flujos de OAuth. La superficie de tráiler de Android conserva sus capacidades y cambia únicamente el momento de disponibilidad/callbacks obsoletos.

## Archivos del experimento

- `.github/workflows/ci.yml`
- `.github/workflows/yesnt10-hero-debug.yml`
- `androidApp/build.gradle.kts`
- `composeApp/src/androidFull/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.android.kt`
- `composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeHeroPagerTest.kt`
- `composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/YesntHeroPreferencesTest.kt`
- `composeApp/src/commonMain/composeResources/values-es/strings.xml`
- `composeApp/src/commonMain/composeResources/values-es/yesnt_hero.xml`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/commonMain/composeResources/values/yesnt_hero.xml`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsRepository.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/AvixHomeLayoutPreferences.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HeroMetadataSession.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HeroRequestCoordinator.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeRepository.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeScreen.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeSnapshot.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HeroArtworkImage.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HeroImageDiagnostics.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroCompatibility.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeSkeletonLoading.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/AvixHomeLayoutSettingsSection.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/tmdb/TmdbImages.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/tmdb/TmdbMetadataService.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/tmdb/TmdbPreviewArtworkStorage.kt`
- `composeApp/src/commonTest/kotlin/com/nuvio/app/features/home/YesntHeroArtworkTest.kt`
- `composeApp/src/commonTest/kotlin/com/nuvio/app/features/tmdb/TmdbPreviewArtworkStoreTest.kt`
- `docs/experiments/YESNT10_HERO.md`
