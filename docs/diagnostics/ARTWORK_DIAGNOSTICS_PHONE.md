# Diagnóstico de imágenes desde el teléfono

Esta compilación observa el NuvioEibyx original de `f1ef8013a0f7e50c3cf94440669e03fc65a4af7a`. **No corrige todavía la lentitud ni la desaparición observadas.** No incorpora los experimentos de Hero/caché. El diagnóstico se encuentra en `fix/avix-artwork-root-cause` y no necesita root, ADB, Logcat ni ordenador.

## Uso

1. Instalar la APK **FullDebug ARM64** `NuvioEibyx-Artwork-Diagnostics-ARM64-Debug.apk`. Su ID independiente es `com.eibyx.nuvio.artworkfix.debug`. No desinstalar la aplicación original.
2. Configurar el mismo perfil, idioma, addons, TMDB y preferencias del caso afectado. La instalación independiente tiene sus propios datos y caché: una primera apertura no equivale a la caché caliente del original. Mantener inicialmente los tráilers automáticos **desactivados**, como en el caso confirmado por el usuario.
3. Abrir **Ajustes → Avanzados → Diagnóstico de imágenes**. La captura está **desactivada por defecto**, también tras reiniciar el proceso.
4. Pulsar **Limpiar registros** para empezar una prueba vacía y después **Activar diagnóstico**. Volver a Home y reproducir el problema. Activar la captura no vuelve a cargar las imágenes que ya estén abiertas.
5. Volver a la misma sección y pulsar **Desactivar diagnóstico**. Los registros se conservan; activar otra vez añade eventos hasta que se limpien o termine el proceso.
6. Pulsar **Copiar diagnóstico** para copiar un resumen con los últimos 60 eventos, o **Exportar diagnóstico (.txt)** para compartir el registro completo con el menú nativo de Android. Elegir la aplicación o destino de almacenamiento que se desee. No hay envío automático.

El archivo compartido es texto UTF-8. Se genera fuera del hilo principal, en un único archivo privado de caché, únicamente al exportar. FileProvider concede lectura temporal a la aplicación elegida; no se usa almacenamiento público ni se solicitan permisos de almacenamiento. Limpiar revoca el acceso al archivo previo y elimina la copia privada. No puede borrar copias que el usuario ya haya enviado a otra aplicación.

El búfer en memoria está limitado a **1 MiB y 8.192 eventos**, con descarte de los más antiguos. El resumen indica los descartes. No cerrar ni forzar la detención del proceso antes de exportar: el búfer no es persistente. Para estudiar la restauración posterior, reiniciar, activar de nuevo y repetir la navegación; esta herramienta no captura retrospectivamente el arranque previo a su activación.

## Reproducción recomendada

Hacer una captura separada por escenario, manteniendo contenido/configuración/red equivalentes. Anotar fuera del registro el título, la versión instalada, el perfil usado sin datos personales, idioma, proveedor, caché fría/caliente y hora aproximada de la observación. Una grabación de pantalla opcional ayuda a contrastar el dibujo enviado con lo realmente visible.

- **Hero:** volver a Home, esperar al primer banner, cambiar lentamente de elemento y después hacer varios swipes rápidos. Esperar autorrotación y entrar/salir de Home. Probar tanto una imagen nueva como una vista anteriormente.
- **Details:** abrir desde el Hero un contenido afectado, esperar a que el fondo esté visible, hacer scroll profundo y volver arriba. Repetir sin cambiar de contenido. Después volver a Home y abrirlo otra vez.
- **Red:** repetir con conexión lenta; después de una carga completa, repetir offline cuando sea posible. No borrar datos entre los casos de caché caliente.
- Sword Art Online, Clannad, Re:Zero y Howl's Moving Castle son referencias opcionales; incluir cualquier otro contenido que falle.

## Eventos y correlación

`t=...ms` utiliza un reloj monotónico; se mide desde el inicio del registro vacío o su limpieza. El orden de escritura se serializa entre hilos. No son mediciones inventadas ni relojes de pared.

| Campo/evento | Interpretación |
|---|---|
| `identity`, `op` | Digest del contenido y operación de selección. Las identidades TMDB/addon se vinculan cuando se observa su relación. Volver a seleccionar un contenido puede iniciar otra operación. |
| `resource`, `key`, `memoryKey`, `diskKey` | Digest del recurso o clave; permiten comparar sin revelar URL completas. |
| `load`, `request`, `http`, `instance` | Modelo lógico de imagen, intento Coil, transacción HTTP y componente Compose respectivamente. Un modelo puede originar varios intentos. |
| `hero.selected`, `hero.metadata.basic`, `hero.url`, `home.pager` | Selección y disponibilidad del preview/URL; current, settled y target del carrusel. |
| `hero.metadata.enriched`, `metadata.*`, `tmdb.*` | Datos enriquecidos, identidad, addon, publicación y etapas TMDB del contenido observado. No se cambian sus esperas ni su publicación. |
| `details.selected`, `screen.enter/dispose`, `metadata.available`, `details.url` | Apertura, datos y recurso de Details. `none`/`available=false` permite detectar ausencia de URL. |
| `HERO_VISIBLE`, `HERO_NEIGHBOUR_LAYER`, `NEIGHBOUR_*_PREFETCH`, `HERO_LOGO`, `COLOR_SAMPLE`, `DETAILS_*` | Finalidad de una solicitud existente. El diagnóstico no añade solicitudes. Las capas vecinas ya compuestas se distinguen del enqueue original. |
| `CATALOG`, `ADDON`, `TMDB`, `METADATA_UNKNOWN` | Procedencia observada. No se infiere que una URL sea TMDB sólo por el nombre del servidor; la procedencia puede ser desconocida si se resolvió antes de activar. |
| `coil.start/size/key/fetch.start/fetch.end/decode.start/decode.end` | Intervalos de solicitud, tamaño, clave, fetch y decodificación. Los hits de memoria pueden omitir fetch/decode. |
| `coil.success/error/cancel` | Fuente de resultado, dimensiones, clase de error o cancelación. No se registran mensajes de excepción con posibles secretos. |
| `http.start/headers/end` | Concurrencia, presencia de validadores, estado HTTP, longitud declarada y directivas de caché, sin valores de tokens/headers sensibles ni cuerpos. `load` enlaza el recurso con el modelo lógico. |
| `compose.image.enter/dispose`, `compose.painter.changed`, `compose.image.Loading/Success/Error` | Instancia/composición, identidad numérica del painter y cambios de estado. No se retienen painters o bitmaps para diagnosticar. |
| `details.scroll` | Cambio de primer item, inicio/fin de scroll y regreso a offset cero. No se registra cada píxel/fotograma. |
| `draw.submitted`, `screen.frame.callback` | Observación CPU del dibujo/callback de Compose. **No prueba presentación física, visibilidad, alpha, recorte, GPU o fin de crossfade.** |

`MEMORY_CACHE`, `MEMORY`, `DISK` y `NETWORK` son los valores reales de Coil 3.6.3. `MEMORY` no es un hit de memoria. `NETWORK` no demuestra una descarga completa: puede haber validación 304. `declaredBytes` no mide los bytes transferidos. No se lee el cuerpo para medirlo.

Los intervalos corresponden a callbacks reales de Coil y al cliente HTTP. Si una fuente entrega un stream de lectura diferida, la decodificación puede incluir lecturas de ese stream: no son un perfil puro de CPU o de bytes en la red.

Una captura iniciada a mitad de una carga no puede recuperar sus eventos anteriores. El estado del componente permite observarla, pero la secuencia completa debe reproducirse después de activar. Los hooks de arranque anteriores sólo podrán producir eventos si ya hay captura activa; no se guarda un interruptor persistente ni se activa automáticamente al iniciar.

## Qué permite comprobar

- Selección → metadata → URL: separar catálogo, resolución de identidad y enriquecimiento antes de que exista una solicitud de imagen.
- URL → `coil.start/size`: observar composición/constraints/solicitud.
- Fetch/HTTP → decode → success: distinguir red, validación, disco y decodificación.
- Dispose/cancel → enter/loading → success/draw al volver del scroll: detectar recreación y recuperación sin equiparar una solicitud nueva con descarga.
- Success/draw con fondo todavía vacío en vídeo: investigar estado visual/capas, sin culpar automáticamente a la caché.

## Privacidad y límites de compatibilidad

No se registran títulos, datos de cuenta, tokens, cookies, cabeceras de autenticación, claves API ni URL completas. Se usan hashes truncados y campos controlados; hay redacción defensiva adicional. No existe endpoint de telemetría ni subida automática del diagnóstico. El portapapeles y las aplicaciones elegidas para compartir quedan bajo control del usuario.

La capacidad se compila sólo con `-Pavix.artworkDiagnostics=true` y aplicación debuggable; Release fuerza el flag a false. El interruptor de captura no reconstruye el ImageLoader ni cambia solicitudes, tamaño, prioridad, caché, políticas HTTP, URL o precargas. Las etiquetas utilizan Extras ordinarios, no claves de memoria. El buffer guarda texto y etiquetas acotadas, no imágenes.

Compartir se implementa en Android usando el FileProvider existente. iOS mantiene una implementación no disponible de esta herramienta; no se certifica una interfaz de exportación iOS. Las pruebas Robolectric incluyen Android 16/API36, pero el chooser y el fallo visual siguen requiriendo comprobación en un teléfono real. No se ha demostrado aún la causa del defecto ni se certifica una mejora de velocidad.

## Compilación y entrega

```bash
./gradlew :androidApp:assembleFullDebug \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore \
  -Pavix.artworkDiagnostics=true \
  -Pavix.debug.applicationId=com.eibyx.nuvio.artworkfix.debug \
  -Pavix.debug.abis=arm64-v8a --stacktrace
```

El workflow CI existente, sobre `fix/avix-artwork-root-cause`, genera la misma variante y publica un **artefacto de Actions**, no una release. El ZIP contiene APK, `SHA256SUMS` y `COMMIT.txt`. Verificar el run/commit entregado; las APK anteriores con diagnóstico sólo Logcat no contienen esta interfaz. Los certificados Debug local/CI pueden diferir; si Android rechaza actualizar por firma, desinstalar únicamente la app de diagnóstico (se pierden sus datos/caché), nunca la original.

## Validación ejecutada de esta ampliación (17-10-2026)

- `:composeApp:testAndroidHostTest` completo: **1.824 pruebas / 280 clases**, cero fallos, errores u omisiones. Se conservan las 1.806 anteriores y se añaden 18 casos de diagnóstico. No se reutiliza el recuento de otra rama.
- Controles Compose: activar/desactivar, copiar al portapapeles, limpiar y lanzar el chooser con ACTION_SEND, texto plano y URI de lectura. Pruebas de archivo privado repetido y lectura mediante ContentResolver en API34 y **API36/Android16** simulado.
- Reloj monotónico concurrente, búfer UTF-8 acotado, descarte, limpieza, conservación al parar/reactivar, redacción de secretos/URL y exclusión de metadata ajena.
- Los tags mantienen las claves/opciones del request; una solicitud sin tag reutiliza la misma entrada de memoria. Los posters ajenos no producen eventos Coil. Activar/desactivar no reinicia un backdrop ya cargado.
- `HomeHeroSection` original: selección, metadata básica, URL, estado, éxito, envío de dibujo y disposición, usando fetcher local y dibujo nativo de Robolectric; un único fetch para un contenido. No es una medición del teléfono.
- Se mantienen las tres reproducciones controladas de Details: retorno desde memoria, reinicio después de cancelación y nueva obtención después de expulsión explícita de memoria. No demuestran que el teléfono padezca exactamente esas condiciones.
- Hubo un fallo intermedio de aislamiento: FileProvider retenía entre aplicaciones temporales de Robolectric el directorio de otra prueba. Las nuevas pruebas limpian esa caché estática antes y después. No se cambió FileProvider en producción para esconderlo, ni se tocaron las aserciones existentes de subtítulos.
- `:androidApp:assembleFullDebug` con los flags indicados: **BUILD SUCCESSFUL**. `verifyAvixCxxRuntimeFullDebug` verifica el C++ de mpvlib. Sin actualización de dependencias/versiones ni modificación de motores, subtítulos, Tracking o temas.
- APK **local**, fuera de Git: 93.846.215 bytes; SHA-256 `27b8b7d29997124f0bc74f713a38f1ee1852f902c0e58d9faa7b4b9bfa2cd8a0`. ZIP íntegro; ID independiente correcto; firma Android Debug v2 verificada; 20 bibliotecas ELF64/AArch64, únicamente arm64-v8a, incluyendo mpvRx/ASS/C++. El runtime C++ coincide byte a byte con mpvlib 1.1.3. Esta cifra no debe confundirse con el checksum del APK posterior de CI, que usa otra configuración/firma.

No hay dispositivo/emulador utilizable. **Instalación, menú Compartir en un teléfono real, secuencia visual del fallo y rendimiento siguen pendientes.** No hay mediciones reales antes/después ni una causa funcional demostrada. La APK no se presenta como reparación.
