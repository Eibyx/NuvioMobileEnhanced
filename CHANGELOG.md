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

### Reproductor
- Cambio de motor dentro del reproductor conservando posición, velocidad, pista de audio y subtítulo; recordar motor por serie/película; cambio automático a libmpv al elegir un subtítulo ASS/SSA en modo Automático (una vez por elemento). Ajustes: «Preferir libmpv para ASS/SSA», «Recordar motor por título», «Olvidar motores recordados».
- Importar subtítulos locales (SRT, ASS, SSA, VTT, TTML; UTF-8, UTF-8 BOM, UTF-16 LE/BE, Windows-1252) desde Reproductor → Subtítulos → Importar archivo. ASS no se convierte a SRT.
- libmpv: `sub-ass-override=no`, fuentes incrustadas, carpeta de fuentes del usuario (`sub-fonts-dir`); subtítulos externos no UTF-8 se recodifican a UTF-8 antes de `sub-add`.
