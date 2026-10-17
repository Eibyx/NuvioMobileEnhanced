# NuvioEibyx original: investigación de artwork

Fecha de investigación: 17 de octubre de 2026. Esta rama es una **compilación de diagnóstico**, no una corrección certificada de los retrasos observados. No se importó ninguno de los dos experimentos descartados.

**Actualización: diagnóstico exportable desde el teléfono.** La captura actual se activa en Ajustes avanzados y se copia/exporta mediante Compartir de Android, sin ADB ni Logcat. Véase [guía de uso y eventos](ARTWORK_DIAGNOSTICS_PHONE.md). Los resultados y APK de la primera investigación al final de este documento son históricos y no sustituyen la validación de esta nueva compilación.

## A. Resultado y nivel de certeza

**Todavía no está demostrada la causa de los aproximadamente 11 segundos ni de la desaparición observada en el teléfono.** No se dispone de ejecución Android; ADB no arranca por su directorio de usuario de sólo lectura y no hay emulador instalado. Tampoco hay trazas del fallo, URL exacta, configuración TMDB/perfil ni APK de referencia identificada. No se puede convertir una diferencia estática en un resultado experimental.

Se verificaron diferencias concretas y se preparó observación optativa del original. Se conserva el comportamiento funcional, incluyendo las situaciones sospechosas, para no alterar la evidencia antes de reproducirlas. No se añaden cachés, resolutores, coordinadores, precargas, retención de painters ni cambios HTTP. No se porta StreamingShowcase.

Lo demostrado por lectura de código:

1. Home publica URL procedentes del catálogo sin esperar a `HeroContentBlock` ni a su enriquecimiento. Por ello, atribuir toda la demora del banner a las temporadas TMDB sería incorrecto.
2. Eibyx compone tres capas y además encola artwork/logos vecinos sin tamaño explícito. Es trabajo adicional respecto a yesnt10; no demuestra descargas duplicadas ni once segundos de demora.
3. En Details móvil el Hero pertenece a un elemento de `LazyColumn`. Ese diseño también está en oficial y yesnt10. `onBackdropLoaded` no conserva el painter para volver a dibujarlo en ninguno de esos renderizadores móviles. En oficial se conserva para extraer colores; eso **no equivale a reutilizarlo como painter visible**.
4. La solicitud de imagen de Details usa `meta.background ?: meta.poster`, sin una clave personalizada ni transformación Coil. Al salir el item pueden cancelarse solicitudes; al volver se necesita un resultado compatible en memoria o repetir fetch/decode. Una solicitud nueva no demuestra una descarga nueva.
5. Oficial y Eibyx usan Coil 3.6.3, Compose 1.12.0 y la misma política `CacheControlCacheStrategy`. No se encontró una justificación para actualizar Coil o ignorar HTTP.
6. El enriquecimiento TMDB de Eibyx espera todas las temporadas solicitadas antes de devolver el resultado. Oficial comparte esa barrera; yesnt10 prioriza temporadas y usa almacenamiento persistente. Eso puede retrasar la publicación de metadatos, pero no explica por sí solo un retraso que empieza **después de que el texto ya esté dibujado**.

## B. Referencias y procedencia

| Referencia | HEAD remoto comprobado | Dependencias principales |
|---|---|---|
| Eibyx `integration/avix` | `f1ef8013a0f7e50c3cf94440669e03fc65a4af7a` | Coil 3.6.3; CMP 1.12.0; Kotlin 2.4.10; Ktor 3.4.1; Media3 1.8.0 |
| Oficial `cmp-rewrite` | `229c083cf3db7bab6fda284484f87439eb4e0eb0` | Coil 3.6.3; CMP 1.12.0; Kotlin 2.4.10; Ktor 3.4.1; Media3 1.8.0 |
| yesnt10 `enhanced` | `894499115fb1a69cd4bb34d6969a74832f0e5636` | Coil 3.5.0-beta01; CMP 1.11.1; Kotlin 2.4.10; Ktor 3.4.1; Media3 1.8.0 |

Los tres HEAD coinciden con las referencias solicitadas al consultar las ramas. No hay un HEAD posterior en esas ramas que pueda presentarse como corrección más reciente. La versión instalada en el teléfono puede ser distinta y deberá anotarse.

Ancestro común Eibyx/oficial: `808272dcec4be02397282600b70a07111576e0c7` (15-10-2026). Ancestro Eibyx/yesnt10: `312d499ee6a2784279a77fb5211bd0f5ceabbffa` (01-09-2026). No se compararon los repositorios como si fueran versiones contemporáneas de un único archivo.

`git blame` y `git log` sitúan estas diferencias en cambios concretos, muchos heredados de luqmanfadlli, no escritos originalmente por Eibyx:

| Commit | Fecha | Cambio y relevancia |
|---|---|---|
| `bba54a5a7e961684e970a52951338363f858b3e2` | 12-10-2026 | Capas estables del Hero, precarga vecina por enqueue y metadata vecina. Posible competencia; falta medirla. |
| `96d663456b27eca0c697087b830bf266b2fc14de` | 14-10-2026 | Sustituye extracción desde resultado visible por muestra separada de color en Details. Posible variante adicional en memoria/red. |
| `8464dfdbe` | 15-10-2026 | Colores vecinos anticipados y artwork activo asociado a targetPage. No es prueba de bloqueo. |
| `4bd6c3b8` / `dd31c4dd` | 15-10-2026 | Preloader y snapshot por perfil; la versión endurecida espera a terminar, no abre simplemente tras 6 s. |
| `4ba92f3926e1432fb9e1a0d0873c6754ed12a291` | 16-07-2026 | Optimización de scroll oficial, heredada: lecturas diferidas en graphicsLayer. No es una regresión exclusiva del fork. |
| `989580e589e41f6be6adbd6417cdcec8fccb593a` | 04-08-2026 | Introducción de cache-control en el linaje común. No es una política exclusiva de Eibyx. |
| `613908677` | 16-10-2026 | Contraste para tema claro y scrims; cambia gradientes, no el modelo o el loader del background. Se conserva. |

Los commits son consultables en `https://github.com/Eibyx/NuvioMobileEnhanced/commit/<SHA>`; los anteriores al fork pueden estar también en sus repositorios de origen.

## C. Recorrido completo y puntos de separación

Las líneas siguientes corresponden a la **base f1**, antes de añadir observación. Prefijo compartido: `composeApp/src/commonMain/kotlin/com/nuvio/app/`.

### Home

- `core/startup/StartupPreloader.kt:40`: `awaitReady` espera la tarea completa; la advertencia a los 6 s no es un timeout de apertura. `MainActivity.kt:203` llama a esta barrera antes de `setContent`.
- `features/home/HomeRepository.kt:63`: `preloadSnapshot`; `:204` restauración con generación y perfil; `:220` `applySnapshot` rechaza snapshots sin secciones, incluso si contienen Hero de colecciones. Es una limitación comprobada de restauración, no prueba del retraso de una descarga.
- `HomeRepository.kt:112`: catálogos concurrentes, permisos limitados y publicación progresiva; `:247` `publishCurrentState` selecciona los previews existentes, sin hacer fetch de metadata por cada banner.
- `features/home/components/HomeHeroSection.kt:324`: se compone anterior/siguiente/actual, en ese orden. En dos elementos, anterior y siguiente apuntan al mismo contenido; las claves de `:451` y `:598` no incluyen página. Hay colisión estructural posible. No se ha demostrado que genere el síntoma observado, por lo que no se cambia en esta rama.
- `HomeHeroSection.kt:346`: enqueue vecino sin `.size` explícito, con el singleton; sus `Disposable` no se conservan ni se cancelan al terminar ese efecto. A continuación se pide metadata vecina. Las capas visibles ya solicitan ese artwork con constraints y `Crop`.
- `HomeHeroSection.kt:719`: `heroArtworkUrl` decide backdrop/poster según estilo/orientación/preferencia. Es necesario registrar la URL realmente elegida: no se puede comparar sólo el título.
- `HomeHeroSection.kt:798` / `:854`: `HeroContentBlock` obtiene metadata; no alimenta el modelo de las capas de artwork. El tráiler constituye otro consumidor.

### Details

- `features/details/MetaDetailsRepository.kt:63`: `load` → identidad TMDB/IMDb y espera de manifiestos → `tryFetchMeta` (`:387`) → parse de addon (`:408`) → TMDB con presupuesto de 5 s (`:409`) → publicación (`:508`). Hay además fallback standalone, MDBList/OMDb y recomendaciones.
- `features/tmdb/TmdbMetadataService.kt:684`: `enrichMeta` espera `fetchEnrichment` y el mapa de episodios. `:932` espera detalles, créditos, imágenes, age rating y otros enriquecimientos habilitados. `:1155` espera todas las temporadas. La URL puede existir en el addon antes de finalizar estas operaciones.
- `features/details/MetaDetailsScreen.kt:217`: la pantalla acepta metadata del estado o de `peek`; se compone contenido si existe metadata aunque haya enriquecimiento pendiente. `:1141` mantiene la lista; `:1276` LazyColumn; `:1284` item `detail-hero`; `:1324` invoca `DetailHero` móvil.
- `features/details/components/DetailHero.kt:116`: selección `background ?: poster`; `:118` AsyncImage con tamaño de layout y Crop, sin transformación Coil; `:127` parallax/escala dentro de graphicsLayer. No hay un `rememberAsyncImagePainter` externo que sobreviva al item.
- `MetaDetailsScreen.kt:1191` / `core/ui/ArtworkDominantColor.kt:98`: muestra independiente 96×96 con el mismo loader y URL. El tamaño no forma automáticamente una nueva clave de memoria; Coil comprueba si el resultado cacheado satisface la solicitud. Una variante pequeña puede reemplazar una grande bajo la misma clave; no implica que Coil sirva necesariamente la pequeña a una petición grande. Falta observar keys/dimensiones/hits reales.
- Tablet dibuja `TabletDetailBackdrop` fuera de la lista y puede tener una segunda variante difuminada en modo cinematográfico. No extrapolar un resultado móvil a tablet.
- El tráiler se dibuja encima del artwork. El estado ready de la pantalla y la creación/destrucción de una superficie pertenecen a ámbitos distintos; `STATE_READY` tampoco prueba primer fotograma. El usuario confirmó posteriormente que los tráilers automáticos están **desactivados** en el caso reportado: el filtro `heroTrailerPlaybackEnabled` deja la URL de superficie a null, por lo que el tráiler de Details queda descartado para ese síntoma. No se cambia el reproductor.

### Comparación con las referencias

Oficial mantiene el Hero móvil reciclable, misma URL de Details y mismo pipeline HTTP/Coil. Compone las capas de Home que tienen visibilidad en la transición; no tiene la combinación adicional de precarga explícita y muestreo anticipado de color de f1. Por ello, un defecto genérico de LazyColumn no explica por qué sólo una aplicación falla en el teléfono.

yesnt10 prioriza el item visible en su `HomeHeroSection.kt:335`, publica metadata básica mediante `fetchHeroDetailMeta` y después completa otras. Su `TmdbMetadataService.kt:1013` carga prioritariamente temporadas faltantes, recupera resultados por temporada e idioma y calienta otras en segundo plano. Resuelve previews localizados con almacenamiento existente en ese fork. Son diferencias verificadas; no se importan, pues no se ha demostrado que el intervalo problemático esté ahí.

El negro de `experiment/yesnt10-hero` se mantiene como un incidente independiente. No existe captura de sus estados/fallos HTTP/dibujo que demuestre su causa. No se usa como evidencia contra Coil ni se incorpora su helper de imágenes.

## D. Investigación externa

Consulta: 17-10-2026. Las fuentes GitHub se leyeron por REST/GraphQL y/o Git, incluyendo comentarios. Los estados son los observados entonces, no garantías para otras fechas.

| Fuente y fecha | Versión/síntoma y solución documentada | Aplicabilidad a f1 |
|---|---|---|
| [Coil #3379](https://github.com/coil-kt/coil/issues/3379), 03-04-2026; comentarios hasta 17-08 | Abierto. Coil 3.1.0/3.2.0, LazyRow, archivo local y transformación personalizada; se describe cancelación y vacíos. Un comentarista asocia el retraso a la transformación simulada y mejora con cacheKey estable. No hay corrección publicada demostrada por este issue. | Coincide el scroll; difieren versión, origen y transformaciones. f1 no transforma el backdrop con Coil. No justifica restart indiscriminado ni downgrade. |
| [Coil #2936](https://github.com/coil-kt/coil/issues/2936), 10-04-2025; comentarios hasta 24-03-2026 | Abierto. Flicker desde 3.1.0 con SVG en LazyVerticalGrid; autor informa que 3.0.0 no falla. Comentario del 01-11-2025 dice que tamaño explícito evita flicker; mantenedor no pudo reproducir inicialmente. | f1 usa backdrop raster con constraints, no ese ejemplo SVG/grid. El consejo no es una corrección universal. |
| [Coil #2096](https://github.com/coil-kt/coil/issues/2096), 01-02-2024, cierre tras comentarios de febrero | 3.0.0-alpha03, excepción de snapshots/JavaFX durante scroll en desktop. Autor confirma que quitar JavaFX resuelve su proyecto. | No es evidencia de un defecto Android 3.6.3; no importar esa solución. |
| [Coil discusión #2461](https://github.com/coil-kt/coil/discussions/2461), 02-09-2024; respuesta mantenedor 13-11 | Sustitución estática→WebP animado; recomienda imagen anterior como placeholder o ignorar Loading con rememberAsyncImagePainter. No especifica versión exacta. | Trata cambio de modelo dentro de una instancia; no garantiza conservación tras destruir un item. Retener imágenes podría ocultar otra causa. No aplicado. |
| [Coil discusión #2710](https://github.com/coil-kt/coil/discussions/2710), 22-11-2024; respuesta mantenedor 25-11 | Precarga FIT no satisface Crop; recomienda `Scale.FILL` coincidente y logging de razones del miss. Versión exacta no indicada. | Muy pertinente para comparar enqueue sin scale/tamaño y AsyncImage Crop de f1. Es hipótesis medible, no prueba de descarga duplicada. |
| [Coil discusión #3211](https://github.com/coil-kt/coil/discussions/3211), 24-10-2025 | Coil 3, RecyclerView/ImageView; pregunta cómo evitar cancelar al reciclar. Sin respuesta en lo consultado. | Antecedente de lifecycle, no solución ni prueba para Compose. |
| [Changelog Coil 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/CHANGELOG.md), 18-09-2026 | 3.6.3 corrige metadata de módulos Kotlin/R8; 3.6.2 corrige cancelación de DeDupeConcurrentRequestStrategy; 3.6.0 incluye constraints invertidas y GenericViewTarget/R8. | f1 ya usa 3.6.3 y no activa DeDupe. No hay fundamento aquí para actualizar o activar una estrategia nueva. GenericViewTarget no es AsyncImage. |
| [Nuvio #2198](https://github.com/NuvioMedia/NuvioMobile/issues/2198), 08-10-2026; [PR #2207](https://github.com/NuvioMedia/NuvioMobile/pull/2207), 09-10; [PR #2239](https://github.com/NuvioMedia/NuvioMobile/pull/2239), 12-10 | Congelación 10–15 s con posters personalizados y bibliotecas Simkl de ~14k items. Diferir resolución por item y cachear tokenización/remember evita resolver toda la biblioteca por emisión. | La duración se parece, pero el desencadenante es distinto. f1 ya incluye la publicación diferida de biblioteca (`9b550031`, 09-10) y el resolvedor de tokenización optimizado; no basta el parecido temporal para atribuirle el fallo. Comprobar biblioteca y patrón en el teléfono. |
| [luqman #181](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/issues/181), 14-10-2026 | 0.5.10(142), iOS16.6; demora del color dominante. No prueba demora de la imagen. | Relacionado con la historia de muestras de color. Mantener separado color de artwork. |
| [luqman PR #183](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/pull/183), 14-10-2026 | Preload/snapshots y operaciones de arranque; descripción afirma abrir tras 6 s. | f1 contiene modificación posterior `dd31c4dd` que sigue esperando. Se auditó código, no sólo descripción del PR. |
| [luqman PR #187](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/pull/187), 15-10-2026; [#203](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/issues/203), 17-10 | Corrección de flicker de lanzamiento y reporte posterior de Hero omitido. | Muestra que las referencias tampoco son infalibles; no importar sin reproducción. |

Fuentes primarias adicionales examinadas: [AsyncImagePainter 3.6.3](https://github.com/coil-kt/coil/blob/3.6.3/coil-compose-core/src/commonMain/kotlin/coil3/compose/AsyncImagePainter.kt), [MemoryCacheService](https://github.com/coil-kt/coil/blob/3.6.3/coil-core/src/commonMain/kotlin/coil3/memory/MemoryCacheService.kt), [CacheControlCacheStrategy](https://github.com/coil-kt/coil/blob/3.6.3/coil-network-cache-control/src/commonMain/kotlin/coil3/network/cachecontrol/CacheControlCacheStrategy.kt), [documentación ImageLoader](https://github.com/coil-kt/coil/blob/3.6.3/docs/image_loaders.md), [pipeline](https://github.com/coil-kt/coil/blob/3.6.3/docs/image_pipeline.md), y [LazyLayoutItemContentFactory de AndroidX](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutItemContentFactory.kt). Esta última es código actual de referencia, no una comprobación binaria de la versión de Compose instalada.

También se examinaron los 51 issues/PR disponibles de yesnt10, cuatro páginas recientes del oficial (400 entradas), issues/PR relevantes de luqman y las discusiones recientes de Nuvio. No se encontró ahí una corrección probada de este caso concreto. Eso no demuestra ausencia de un reporte en el historial completo.

**Límites de acceso:** los intentos a Reddit, Stack Overflow/API StackExchange, Google, developer.android.com y el sitio publicado de Coil devolvieron `403` del proxy CONNECT. No se presentan resultados de esos foros como si se hubieran leído. Se consultaron documentación y fuentes oficiales disponibles en GitHub. Posteriormente REST/GraphQL de GitHub empezó a responder `401 Bad credentials`; se conserva la investigación ya obtenida, sin solicitar ni copiar tokens.

## E. Caché: qué está verificado

Android usa `NuvioApplication` como `SingletonImageLoader.Factory`; `platformProvidesImageLoader=true` evita reemplazarlo desde App. AsyncImage y enqueue obtienen ese singleton. No se encontró un segundo loader creado por Home/Details. La traza `loader.created` permite comprobarlo en ejecución.

Las políticas de memoria y disco están habilitadas, sin tamaños propios. En Coil 3.6.3 Android, la memoria por defecto es 20% del memoryClass o largeMemoryClass, 15% en low-RAM. Disco: 2% del espacio libre, limitado por defecto a 10–250 MiB. Son reglas de configuración, **no cantidades observadas en el teléfono**; el diagnóstico registra los límites efectivos.

Las URL completas alimentan la clave habitual; extras/transformaciones pueden distinguir claves. Un tamaño diferente puede causar rechazo por compatibilidad del bitmap o sustitución de la variante sin que exista una clave distinta. El disco conserva el recurso descargado, no cada bitmap decodificado. Reiniciar proceso pierde memoria y estado UI; no elimina por sí mismo el disco. Snapshot Home conserva metadatos/URL, no bitmaps.

CacheControlCacheStrategy aplica frescura, no-store, no-cache, validadores y combinación de respuesta 304 con el contenido existente. Con ENABLED, los encabezados todavía pueden impedir escritura o exigir red. NETWORK no significa necesariamente cuerpo completo; DISK también incluye fuentes locales distintas de la caché HTTP; MEMORY no es MEMORY_CACHE.

No se cambian tamaño, política HTTP, URL ni claves para intentar corregir un miss que no se ha observado.

## F. Cambios de esta rama y corrección pendiente

Sólo observación optativa, pruebas y opciones de compilación Debug:

- `core/diagnostics/ArtworkDiagnostics.kt`: reloj monotónico, IDs numéricos y digest SHA-256 truncado; no guarda imágenes ni tareas.
- `ArtworkCoilEvents.kt`: lifecycle de solicitud, tamaño, key anonimizada, fetch/decode, fuente, éxito/error/cancelación y concurrencia.
- `ArtworkCompositionProbe.kt`: entrada/salida, estado y primer envío de dibujo tras Success. No cambia painter/modelo/alpha ni retiene resultados.
- `androidMain/.../ArtworkDiagnostics.android.kt`: se habilita sólo si la app es debuggable y el flag explícito vale true. Observador transparente de NetworkClient para estado HTTP, presencia de validadores y Content-Length declarado. No consume cuerpos ni cambia headers/cacheStrategy/concurrencia.
- Hooks en Application/MainActivity, preloader, HomeRepository/HomeScreen/HomeHeroSection, MetaDetailsRepository/TmdbMetadataService y renderizadores de Details. Conservan sus rutas y decisiones originales.
- Gradle/manifest: flag optativo y filtros ABI/ID sólo Debug. Release fuerza diagnóstico=false. Identidad de producción intacta.

**No hay un commit de corrección funcional**, porque falta evidencia suficiente. Esta decisión aplica la instrucción «si la evidencia no permite determinar la causa, detente antes de implementar cambios especulativos».

## G. Diagnóstico reproducible en el teléfono

Build:

```bash
./gradlew :androidApp:assembleFullDebug \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore \
  -Pavix.artworkDiagnostics=true \
  -Pavix.debug.applicationId=com.eibyx.nuvio.artworkfix.debug \
  -Pavix.debug.abis=arm64-v8a --stacktrace
```

Sin `avix.artworkDiagnostics=true`, no se instalan listener ni cliente de observación, ni efectos de composición del diagnóstico. No hay ajuste que active esto en Release.

La APK conserva el original y debe etiquetarse **Diagnostic**, no presentarse como solución. La instalación independiente tiene datos/caché diferentes: antes de comparar hay que configurar el mismo perfil, idioma, TMDB, addons, biblioteca y preferencias. No copiar directorios privados de otra aplicación. Los esquemas OAuth existentes siguen siendo los originales; si Android pregunta qué app debe abrirlos, seleccionar la adecuada. No se ha certificado OAuth para un nuevo applicationId.

En la APK actual, abrir **Ajustes → Avanzados → Diagnóstico de imágenes**, limpiar, activar, reproducir y después detener/exportar. No cerrar el proceso antes de exportar: el búfer acotado sólo vive en memoria y la captura vuelve a estar desactivada al reiniciar. No borrar datos salvo en el escenario frío elegido explícitamente. Grabar la pantalla al mismo tiempo permite relacionar dibujo enviado con presentación visible. Los eventos anteriores a la activación no se recuperan retrospectivamente.

1. Inicio limpio; luego cierre total y segunda apertura sin borrar datos.
2. Abrir Sword Art Online, Clannad, Re:Zero u otro título afectado desde Home; anotar ID/proveedor sin credenciales y configuración TMDB.
3. Esperar al background. Scroll profundo, mantener abajo, volver arriba. Repetir con tráilers desactivados y activados como pruebas separadas.
4. Volver a Home, abrir otra vez, cambiar banners lentamente y rápido; probar Hero con 1/2/3/8 contenidos cuando la configuración lo permita.
5. Repetir con red lenta y offline después de una carga completa; anotar qué quedó almacenado.
6. Misma secuencia en oficial y yesnt10, mismo dispositivo/configuración/contenido y condiciones de caché equivalentes. Registrar versión/commit APK real de cada referencia.

Interpretación:

| Intervalo / patrón | Qué permite concluir | Siguiente corrección mínima si se confirma |
|---|---|---|
| `application.start` → `activity.setContent`, pasos startup largos | Barrera de arranque, no fetch de imágenes | Diferir sólo el paso secundario medido, respetando perfil/autenticación. |
| `home.refresh` → `home.publish` sin URL compuesta | Catálogo/publicación/restauración | Corregir la condición concreta de publicación/snapshot. |
| `metadata.load` → `metadata.basic` | Identidad/manifiestos/addon | Corregir únicamente la espera medida. |
| `metadata.basic` → `metadata.tmdb.end` → `metadata.publish` | Enriquecimiento antes de publicar | Desacoplar sólo ese enriquecimiento si el artwork básico es válido y la traza lo exige. |
| `details.url`/`compose.image.enter` → `coil.start`/`coil.size` | Lifecycle/constraints antes de fetch | Corregir resolver/identidad sólo si no avanza o no se reinicia. |
| fetch largo con varias solicitudes del mismo digest y tamaños distintos | Competencia o misses de variante posibles | Eliminar exclusivamente el trabajo redundante demostrado. |
| `http.headers status=304` | Revalidación, no descarga completa | No desactivar HTTP por este resultado. |
| `coil.decode` largo sin red nueva | Decodificación/reutilización | Ajustar únicamente tamaño/compatibilidad responsable. |
| dispose → cancel → enter sin nuevo start | Condición de lifecycle sospechosa | Crear reproducción con misma versión/condición antes de restart manual. |
| dispose → enter → MEMORY_CACHE | Reutilización correcta; hay recreación, no descarga | Investigar draw/alpha/superficie si sigue vacío. |
| Success y draw.submitted, pero vídeo negro en captura | Artwork pudo dibujarse bajo otra capa | Comprobar superficie/tráiler/alpha, no aumentar caché. |

`draw.submitted` es observación CPU; no certifica GPU, recorte, alpha, final de crossfade ni pantalla visible. `screen.frame.callback` sólo indica callback del frame de Compose. Content-Length es longitud declarada, **no bytes medidos en el cable**. Los spans HTTP se correlacionan por digest y `load` del modelo lógico; no se inventa una relación con un intento Coil si hay varios sobre el mismo modelo.

## H. Pruebas y limitaciones

`OriginalDetailHeroLifecycleTest` utiliza el `DetailHero` original dentro de LazyColumn y un Fetcher local controlado. Comprueba retorno desde memoria, reinicio de solicitud cancelada y nuevo fetch después de expulsión de memoria. No usa red, transformaciones de usuario ni un reproductor de vídeo. Que pase no prueba que #3379 nunca ocurra en otro dispositivo.

`ArtworkDiagnosticsTest` comprueba desactivación sin evaluar campos y anonimización. `ArtworkDiagnosticNetworkClientTest` comprueba propagación de petición/respuesta 304 y cancelación sin cambiar headers ni filtrar credenciales.

El workflow CI existente admite ejecución manual y push sobre esta rama y genera FullDebug diagnóstico ARM64 sólo cuando la referencia es exactamente `fix/avix-artwork-root-cause`. No se toca el workflow Release. Comando de recuperación: `gh workflow run ci.yml --repo Eibyx/NuvioMobileEnhanced --ref fix/avix-artwork-root-cause`; descargar el artefacto Debug del run correspondiente. No afirmar que existe ese artefacto hasta comprobar su ejecución.

Los resultados ejecutados, APK y checksums se consignan en el informe de entrega y en los artefactos externos al repositorio. No se reutilizan los resultados de 1.828 pruebas de otra rama ni la APK antigua del experimento.

## I. Criterio para una corrección posterior

Una corrección se considerará justificada cuando exista una traza que localice el intervalo o una reproducción automatizada que falle con el original bajo la condición del teléfono y pase con el cambio mínimo. La misma secuencia debe repetirse sin el diagnóstico para medir rendimiento final. No prometer paridad de velocidad a partir de tests JVM.

El objetivo sigue pendiente hasta reproducir y verificar el síntoma real. No hay mejora de tiempos antes/después medida en esta investigación.

## J. Integridad

Rama creada desde f1, sin merges ni importación de los experimentos. Dependencias, versiones, Hero original, gradientes, animaciones, temas, fuentes, navegación, snapshots, Tracking, biblioteca y reproductores permanecen funcionalmente sin cambios. No se editan ExoPlayer/mpvRx/ASS/SSA. El observador no decide qué imagen mostrar, cuándo pedirla ni cuánto conservarla.

## Validación ejecutada y entrega (17-10-2026)

- Suite completa `:composeApp:testAndroidHostTest`: **1.806 pruebas**, 275 clases, **0 fallos, 0 errores, 0 omitidas**. Incluye las ocho pruebas nuevas. XML contados desde `composeApp/build/test-results/testAndroidHostTest/TEST-*.xml`.
- Las tres reproducciones con el DetailHero original pasan. El retorno caliente produce fuentes `MEMORY` (fetcher controlado) y después `MEMORY_CACHE`, con un único fetch. La carga cancelada vuelve a iniciarse al regresar. La expulsión explícita de memoria requiere otro fetch. Esto no prueba rendimiento visual ni descargas HTTP en el teléfono.
- FullDebug compiló en la misma invocación que la suite; Gradle terminó `BUILD SUCCESSFUL`. `verifyMpvlibChecksum` verificó el AAR original y `verifyAvixCxxRuntimeFullDebug` confirmó la copia correcta de C++.
- `processFullReleaseMainManifest` también pasó con la propiedad de diagnóstico=true, verificando que Release mantiene `com.eibyx.nuvio` y fuerza el flag a false. **No se compiló ni firmó una APK Release en esta tarea.**
- APK local FullDebug: **92.116.172 bytes** (87,85 MiB), ID **`com.eibyx.nuvio.artworkfix.debug`**, minSdk24, versión original 0.5.10/142 sin cambios. ZIP íntegro; exclusivamente **arm64-v8a**, 20 bibliotecas verificadas como ELF64/AArch64, incluyendo mpvRx (`libmpv.so`, `libplayer.so`), C++ y ASS (`libass.so`, `libasskt.so`).
- Firma verificada con apksigner: **Android Debug, esquema v2**. SHA-256 del certificado público: `7d14683756bf59cd6a2665233c9758f35cc905a83bbc0233f02792018f25e3fa`.
- SHA-256 del APK local: `83c58b7b9ff3676f046a0d9491676019623551f9ad55355d128139dd8a9e0fc9`.
- Archivo conservado fuera del checkout: `/workspace/artifacts/avix-artwork-root-cause/NuvioEibyx-Original-Artwork-Diagnostic-ARM64-Debug.apk`. El manifiesto de entrega externo registra el commit correspondiente. No se incluye la APK en Git.
- También se validaron sintaxis YAML y todos los scripts bash del workflow CI modificado, y `git diff --check`/`git diff --cached --check`.
- Fallos intermedios corregidos **del diagnóstico/pruebas**: inserción inicial que separaba una expresión Elvis; fetcher simulado inicialmente registrado para String cuando Coil mapea a Uri. No se clasifican como defectos del original. No se eliminaron ni desactivaron pruebas.
- ADB abortó: `Cannot mkdir '/home/agent/.android': Read-only file system`. No hay emulador instalado; no se ejecutó ni instaló la app en Android real. No se certifican aspecto, ausencia de ghosting, funcionamiento audiovisual, OAuth del ID experimental ni mejora de los 11 segundos.
- La comprobación HTTP de ejemplo contra `image.tmdb.org` también fue bloqueada por el proxy (403 CONNECT); no se inventan encabezados de TMDB ni se sustituyen políticas por ese bloqueo.

El resultado verificable es una base original aislada, investigación trazable, reproducciones controladas y una APK de diagnóstico. **La reproducción del fallo real y su corrección quedan pendientes de evidencia del teléfono.**


## Confirmación del usuario y artefacto de GitHub

El usuario confirmó en esta sesión: **reproducción automática de tráilers desactivada** cuando desaparece el background al volver del scroll. Esto descarta la superficie de tráiler de Details como causa de ese caso concreto; no demuestra todavía si el origen está en caché, cancelación, constraints o dibujo. La APK de diagnóstico mantiene esa opción y debe probarse inicialmente desactivada.

CI [39056163707](https://github.com/Eibyx/NuvioMobileEnhanced/actions/runs/39056163707) terminó **success** en sus dos jobs: Unit tests y Debug APK, sobre el commit de código/pruebas **`5b348b732d3871679540681eb80854cd2111cb2e`**. La actualización posterior de este informe no cambia código, tests ni configuración de build; no requiere repetir esas validaciones.

[Descargar artefacto de GitHub](https://github.com/Eibyx/NuvioMobileEnhanced/actions/runs/39056163707/artifacts/12108844465) (`Nuvio-Eibyx-Debug-40`, puede requerir iniciar sesión en GitHub). Contiene el APK, `SHA256SUMS` y `COMMIT.txt`; extraer el ZIP antes de instalar.

Se descargó y verificó también el **APK de CI**, distinto del APK local por configuración y firma Debug de la máquina:

- Nombre: `NuvioEibyx-Original-Artwork-Diagnostic-ARM64-Debug.apk`.
- Variante FullDebug; ID `com.eibyx.nuvio.artworkfix.debug`; exclusivamente arm64-v8a; versión original 0.5.10/142.
- **92.114.927 bytes**; SHA-256 **`4a529600dfb0be682b3d4660b3654a2073282da689b42c5612f2961b60c0d6e3`**. Coincide con `SHA256SUMS` del artefacto.
- Firma Android Debug v2 verificada. Certificado público SHA-256 `490b41d0441048ba1926a8bb077945d8a858c5ec79a9121116a5c89d5e6fd71e`.
- ZIP íntegro, 20 bibliotecas ELF64/AArch64, mpvRx/ASS/C++ presentes. `COMMIT.txt` coincide con el commit validado.
- Copia verificada: `/workspace/artifacts/avix-artwork-root-cause/github-ci/NuvioEibyx-Original-Artwork-Diagnostic-ARM64-Debug.apk`.
- **Usar preferentemente esta APK de CI para las pruebas.** La local se construyó sin configuración de backend ni IDs OAuth/TMDB en entorno/local.properties; no permite asumir equivalencia de cuenta/perfil/enriquecimiento. En CI el paso backend indicó configuración presente (el nombre del artefacto no lleva el sufijo `-sin-cuenta`). No se pudieron verificar todos los IDs OAuth mediante logs, cuyo acceso fue bloqueado; la autenticación real no está ensayada.
- Las dos APK Debug tienen certificados diferentes: elegir una. Si se intenta sustituir una por la otra con el mismo ID, Android puede exigir desinstalar primero **sólo la app de diagnóstico**, perdiendo sus datos/caché. No desinstalar la aplicación original para este procedimiento.

El APK sigue siendo de diagnóstico, no una corrección del fallo. No hay mediciones reales antes/después. No se publicó una release ni se alteraron las ramas originales.
