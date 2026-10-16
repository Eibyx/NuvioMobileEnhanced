# Próximos pasos (Nuvio Avix)

Actualizado en cada sesión de trabajo. Estado detallado: [FEATURES.md](FEATURES.md). Resultados de pruebas: [docs/TESTING.md](docs/TESTING.md).

## Hecho
- Repositorio Eibyx/NuvioMobileEnhanced, rama `integration/avix` (DECISIONS D-0); merge de Luqman con historial; identidad Avix (`com.avix.nuvio`, `avixnuvio://`, recursos originales de Nuvio).
- CI en marcha: «Debug APK» y «mpvRx engine tests» en verde; la APK de CI incluye la configuración del backend de Nuvio (inicio de sesión con la cuenta original).
- Motor mpvRx (`mpvlib 1.1.3`), selector de motor en el reproductor, subtítulos locales, fuente de subtítulos personalizada.
- Tema Claro/Oscuro/Sistema y degradados de Avix.
- Errores de autenticación por tipo y redacción de tokens en logs y Sentry.
- Ports de Yesnt10: hora de fin, velocidad/ETA de descargas, recordar elecciones, conservar estado al cambiar de calidad, copia de ajustes locales, personas favoritas, «Detrás de cámaras», informe local de errores, Live TV compartido por perfil.
- Inventario completo de Yesnt10 frente a Avix en docs/integration/YESNT_PORT_LEDGER.md (duplicados y funciones retiradas en origen marcados como DESCARTADA).

## Pendiente
1. **Fase 1 — ports de Yesnt10: completada.** Todas las funciones Android compatibles están portadas o documentadas como duplicadas, retiradas en origen, excluidas por el propietario o bloqueadas (docs/integration/YESNT_PORT_LEDGER.md). MyAnimeList portado desde AKRusso.
2. **Fase 2 (en curso)** — corregir los 23 fallos heredados de tests unitarios (y el test intermitente `ExternalPlaybackProgressTest`) hasta que «CI / Unit tests», «CI / Debug APK» y «mpvRx engine tests» estén en verde en el mismo commit.
3. **Fase 3 (reservada, no iniciada)** — rediseño del reproductor; no abrir mini reproductor/PiP automáticamente al volver; fuente del sistema en toda la app; contraste de texto sobre imágenes.
4. Pruebas en dispositivo real (motor, subtítulos ASS, tema claro, ports, cuenta). Nada se marca PASS sin ejecución.
5. Valoraciones IMDb de episodios junto a OMDb (DECISIONS D-4).
6. Release — keystore y secretos `AVIX_RELEASE_*`; primera release solo tras validar en dispositivo.

## Bloqueado / fuera de alcance
- CloudStream: experimental y desactivado hasta verificar la procedencia y la seguridad del runtime.
- Telegram: no existe implementación Android que portar (futuro).
- OAuth con clientes propios: requiere que el propietario registre apps en Trakt/Simkl/AniList/WeTrakr/MDBList con `avixnuvio://auth/...`.
