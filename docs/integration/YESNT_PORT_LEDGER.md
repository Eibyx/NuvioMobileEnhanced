# Registro de ports desde Yesnt10

**Revisado hasta:** `894499115fb1a69cd4bb34d6969a74832f0e5636` (2026-09-02, «Update Enhanced release to 0.4.13»).

**Regla:** nunca `git merge yesnt/enhanced`. Cada función se reimplementa sobre la arquitectura actual (Navigation3, CMP 1.12, modelos de Luqman), en un PR propio que se registra aquí.

| Función (Yesnt10) | Archivos de referencia en Yesnt10 | Equivalente en Luqman | Decisión | Estado | PR/commit |
|---|---|---|---|---|---|
| Controles de catálogo (columnas, tamaño, layout, 1-4 filas, ocultar fechas, smart shelf, compact hero) | `features/settings/NuvioEnhancedSettingsPage.kt`, `NuvioEnhancedSettingsRepository.kt` | Parcial (cuadrícula densa) | Portar | PENDIENTE | — |
| Concierge / secciones premium de inicio | `features/home/components/HomePremiumSections.kt`, `HomePremiumModels.kt` | No | Portar | PENDIENTE | — |
| Personas favoritas | `features/details/FavoritePeopleRepository.kt` | No | Portar | PENDIENTE | — |
| Selector «Behind the Scenes» | `features/details/components/DetailTrailersSection.kt` | No | Portar | PENDIENTE | — |
| Temas en degradado MESSENGER, AMETHYST, BLOSSOM, LAGOON, SUNSET | `core/ui/AppTheme.kt`, `ThemeColors.kt` | Degradado de upstream | Portar solo las paletas (el motor `AnimatedTheme` está superado); tema guardado en local | PENDIENTE | — |
| Hora estimada de fin | commit `49f3bf7e` | No | Portado junto al tiempo del reproductor (no existe el overlay de estado de Yesnt10); tiene en cuenta la velocidad; interruptor local | PORTADO (tests `PlaybackEndTimeTest`) | ver CHANGELOG |
| Recordar elecciones (audio, subtítulos, ajuste) | `settings_playback_remember_choices` | Luqman ya lo hace siempre | Interruptor local «Recordar mis elecciones» (activo por defecto) que controla guardar y restaurar audio/subtítulo por título y el formato de imagen | PORTADO | ver CHANGELOG |
| Conservar pistas al cambiar de calidad | commit `8d75c294` | No: la recarga volvía a la posición inicial de la sesión | Ampliado: al cambiar de calidad se conservan posición, velocidad, audio y subtítulo (`preserveStateForSourceReload`) | PORTADO (prueba en dispositivo: NOT RUN) | ver CHANGELOG |
| Velocidad y ETA de descargas + foreground service | `402a1599`, `DownloadsForegroundService.kt` | No | Velocidad (media móvil) y ETA portadas a la lista de descargas; Live Activity de iOS y foreground service pendientes | PARCIAL (tests `DownloadSpeedEtaTest`) | ver CHANGELOG |
| Importar fuente de subtítulos | `SubtitleFontFileBridge.*` | No | Portado solo en local (no se guarda en el estilo sincronizado): ExoPlayer usa el Typeface; libmpv usa `sub-font` con el nombre de familia leído de la tabla `name`; ficheros en la carpeta de fuentes de libmpv | PORTADO (tests `SubtitleFontFileTest`; dispositivo NOT RUN) | ver CHANGELOG |
| Copia de seguridad y restauración de ajustes | `NuvioEnhancedBackupFileBridge.*` | No | Portar y extender a los ajustes locales de Avix | PENDIENTE | — |
| Diagnóstico de crashes | `core/diagnostics/CrashDiagnostics.kt` | No (Luqman tiene InAppLogger) | Portar | PENDIENTE | — |
| Live TV (filtros, alcance por perfil) | `LiveTvFilterBar.kt`, `LiveTvProfileScope.kt` | Live TV más completo (EPG) | Portar solo lo que falte | PENDIENTE | — |
| Calendario de biblioteca (diseño alternativo) | `LibraryScreen.kt` | Calendario propio | Revisar; portar solo lo útil | PENDIENTE | — |
| Profile Insights | `ProfileInsightsSettingsPage.kt` | Más completo | Revisar métricas que falten | PENDIENTE | — |
| Avisos de contenido | `68b79b46` | ParentalGuide | Revisar el solapamiento | PENDIENTE | — |
| Prefetch de streams en segundo plano | `nuvio_enhanced_background_stream_prefetch_*` | Precarga del siguiente episodio | Evaluar el solapamiento | PENDIENTE | — |
| Avatares GIF (Tenor/Giphy) | `ProfileGifSearchService.kt` | No | Portar desactivado sin clave | PENDIENTE | — |
| Asistente de IA (Cerebras/Groq/OpenRouter/Gemini + Tavily) | `features/ai/*` | No | Portar desactivado por defecto (clave del usuario) | PENDIENTE | — |
| Selector de calidad HLS | `PlayerQualityPanel.kt` | **Sí** (mismo origen, más reciente) | No portar | DESCARTADA (duplicada) | — |
| Volume boost (`LoudnessEnhancer`) | — | **Sí**, más robusto (`AudioProcessor`) | No portar | DESCARTADA (duplicada) | — |
| CloudStream | `features/cloudstream/*`, AAR `cloudstream-runtime-api` | No | Experimental y desactivado; requiere procedencia verificable del AAR y controles de seguridad | BLOQUEADA (seguridad) | — |
| Telegram | `features/telegram/*` (solo iOS) | No | Sin implementación Android que reutilizar | FUERA DE ALCANCE v1 | — |
| DoH | `DnsOverHttps*.kt` (sin UI) | No | Evaluar más adelante | PENDIENTE | — |
