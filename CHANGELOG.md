# Changelog de Nuvio (Avix)

## Sin publicar — `integration/avix`

### Fase 1–3 — Repositorio, base Luqman e identidad Avix
- Estado inicial de Eibyx/NuvioMobileEnhanced: `cmp-rewrite` = `808272dc` (= NuvioMedia/NuvioMobile).
- Merge de Luqman `enhanced@2f2e9c1e` con historial (sin force push). Conflictos iOS/`store.json` resueltos con la versión de Luqman (DECISIONS D-2).
- Identidad Avix: `applicationId` `com.avix.nuvio` (debug `com.avix.nuvio.debug`); nombre visible «Nuvio»; icono, logo y splash originales de Nuvio sin cambios.
- Esquema `avixnuvio://` para deep links, OAuth (Trakt, Simkl, AniList) e Infuse; ya no compite con Nuvio y sus forks por `nuvio://`.
- `CLIENT_ID` de AniList y WeTrakr configurables (antes eran los de la app de Luqman).
- Actualizador y Novedades → releases de `Eibyx/NuvioMobileEnhanced` (versiones `X.Y.Z-avix.N`, canal Beta).
- Los 5 ajustes de reproductor exclusivos de Luqman se guardan solo en el dispositivo (migración sin pérdida de valores remotos existentes).
- `versionCode` configurable desde CI (`-Pavix.versionCode`); APK universal además de las APK por ABI.
- Workflows: `ci.yml` (APK `Nuvio-Avix-Debug.apk`), `mpvrx-tests.yml`, `android-release.yml` (`Nuvio-Avix-Release.apk` + SHA-256), `upstream-watch.yml` (incluye bibliotecas multimedia). Se retiran los heredados.
- Tests heredados de Luqman que no compilaban (`AndroidDownloadTransferTest`, `NextEpisodeCardTest`, `PlayerGestureOverlayTest`, `PlayerSurfaceGesturesTest`) actualizados a las firmas actuales.

### Ports de Yesnt10
- Hora de fin en el reproductor («· termina 22:45») según la velocidad; desactivable en Ajustes de reproducción (solo en el dispositivo).
- Descargas: velocidad suavizada y tiempo restante («2,1 MB/s • quedan 8m 20s»).
- «Recordar mis elecciones» (Ajustes de reproducción): permite desactivar que se guarden y restauren audio/subtítulo por título y el formato de imagen.
- Cambio de calidad (HLS): ya no vuelve a la posición inicial; conserva posición, velocidad, audio y subtítulo.
- Fuente de subtítulos personalizada (.ttf/.otf) para subtítulos de texto con ambos motores; solo en el dispositivo (Ajustes de reproducción).
- Exportar/importar los ajustes locales de Avix (Ajustes → Avanzado). No incluye ni modifica los ajustes de la cuenta de Nuvio.
- «Detrás de cámaras»: los vídeos de la ficha se agrupan en chips por categoría (tráilers, teasers, detrás de cámaras, reportajes, clips…).
- Personas favoritas: corazón en la ficha de cada persona y sección en Profile Insights (fijar, ver todo); por perfil y solo en el dispositivo.
- Degradados de Avix (Messenger, Amethyst, Blossom, Lagoon, Sunset) en Ajustes → Apariencia; solo en el dispositivo, la cuenta conserva su tema.
- Informe local de errores: si la app se cierra inesperadamente, al volver a abrirla se ofrece copiar el informe (sin tokens, claves ni parámetros de URL). También en Ajustes → Avanzado → «Último informe de error». No se envía a ningún servidor.
- Live TV: los perfiles que usan los addons del perfil principal comparten sus listas, favoritos y guía (EPG); al cambiar de perfil se recargan los datos de Live TV.
- «Siguiente episodio» tras ver un especial (temporada 0) que no figura en la lista del addon: ahora propone el primer episodio principal publicado en lugar de nada.
- Barra de estado en el reproductor (opcional, desactivada por defecto): con los controles ocultos muestra hora, batería, red y hora de fin (Ajustes de reproducción).
- Inicio y catálogos (Ajustes → Inicio → «Diseño (este dispositivo)»): 1–4 filas por catálogo, columnas fijas (2–6) o automáticas en «Ver todo», ocultar fechas bajo los pósteres y desactivar el desplazamiento automático del destacado. Solo en el dispositivo.
- «Preparar los streams por adelantado» (opcional, desactivado por defecto): con la ficha abierta se buscan en segundo plano los streams del botón Reproducir para que la reproducción empiece antes.
- Avatares GIF: «Buscar GIF» al editar un perfil (GIPHY con tu propia clave, que solo se guarda en el dispositivo).
- Asistente de IA en las fichas (opcional, desactivado por defecto): pregunta sobre el título con tu propia clave de Groq, Cerebras, Gemini u OpenRouter; búsqueda web opcional con Tavily. Las claves se guardan cifradas solo en el dispositivo.
- Destacado de Inicio: estilos Cinematográfico / Equilibrado / Detallado, datos compactos, mostrar u ocultar valoración y sinopsis, y arte de póster (Ajustes → Inicio → Diseño).
- Deslizar para recargar Inicio con vibración (opcional).
- Catálogos de biblioteca: filtros Todos / Vistos / No vistos con recuento.
- Abrir o compartir con Nuvio listas M3U (se añaden a TV en directo tras confirmar), enlaces de vídeo y enlaces magnet (con P2P activado).
- Fichas de series: «N temporadas · M episodios».
- MyAnimeList (portado de AKRusso/NuvioMobile-Enhanced, GPL-3.0): seguimiento automático de episodios de anime al terminar (≥80 %), solo si el progreso aumenta. Requiere registrar un cliente propio de MAL («Other», redirect `avixnuvio://auth/mal`) y configurar `MAL_CLIENT_ID`; sin él aparece como no configurado.

### Apariencia
- Tema **Claro / Oscuro / Sistema** (Ajustes → Apariencia), sin reiniciar y guardado solo en el dispositivo; por defecto Oscuro. Paleta clara derivada de cada tema (acentos, personalizados y degradados se conservan) con contraste WCAG garantizado; reproductor, mini reproductor y tráileres siempre oscuros; barras del sistema según el modo; AMOLED desactivado en modo claro.
- Revisión de colores fijos en 16 archivos para el modo claro (bordes, tarjetas, separadores, barra de navegación, panel de streams…); los colores sobre imágenes y los de marca se mantienen.

### Reproductor
- Selector de motor en el reproductor: Automático (motor por defecto de Ajustes; olvida el motor recordado del título), ExoPlayer o libmpv (mpvRx). Si el motor elegido falla antes de reproducir, se vuelve al anterior en la misma posición y no se recuerda para el título.
- Motor libmpv sustituido por el de mpvRx (`mpvlib 1.1.3`, SHA-256 verificado); el anterior sigue disponible con `-Pavix.legacyLibmpv=true`.
- Cambio de motor dentro del reproductor conservando posición, velocidad, pista de audio y subtítulo; recordar motor por serie/película; cambio automático a libmpv al elegir un subtítulo ASS/SSA en modo Automático (una vez por elemento). Ajustes: «Preferir libmpv para ASS/SSA», «Recordar motor por título», «Olvidar motores recordados».
- Importar subtítulos locales (SRT, ASS, SSA, VTT, TTML; UTF-8, UTF-8 BOM, UTF-16 LE/BE, Windows-1252) desde Reproductor → Subtítulos → Importar archivo. ASS no se convierte a SRT.
- libmpv: `sub-ass-override=no`, fuentes incrustadas, carpeta de fuentes del usuario (`sub-fonts-dir`); subtítulos externos no UTF-8 se recodifican a UTF-8 antes de `sub-add`.
