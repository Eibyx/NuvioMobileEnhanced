# Próximos pasos (Nuvio Avix)

Actualizado en cada sesión de trabajo. Estado detallado: [FEATURES.md](FEATURES.md). Resultados de pruebas: [docs/TESTING.md](docs/TESTING.md).

## Hecho
- Fase 1: repositorio Eibyx/NuvioMobileEnhanced, rama `integration/avix`, estado inicial registrado (DECISIONS D-0).
- Fase 2: merge de Luqman con historial.
- Fase 3: identidad Avix (`com.avix.nuvio`, `avixnuvio://`, recursos originales de Nuvio).
- Fase 4: primera APK debug compilada localmente; CI configurado.

## En curso
- Fase 5: motor mpvRx (`mpvlib 1.1.3`) en sustitución del libmpv anterior + `MpvRxEngineSmokeTest` + `mpvrx-tests.yml`.

## Pendiente
1. **Motor mpvRx** — pruebas 1–20 de docs/MPVRX_INTEGRATION.md en emulador y dispositivo real (ARM64). Nada se marca PASS sin ejecución.
2. **Selector de motor** — ajuste de motor por defecto con las tres opciones (Automático / ExoPlayer / libmpv (mpvRx)) dentro del reproductor; prioridad manual; recuperar el motor anterior si el nuevo falla.
3. **Subtítulos locales** — verificación en dispositivo con ambos motores; iOS no soportado en v1.
4. **Fuentes** — empaquetar Go Noto Current (OFL 1.1) solo si el `subfont.ttf` de mpvlib no basta; UI para importar fuentes (port de Yesnt10).
5. **Ports de Yesnt10** — ver docs/integration/YESNT_PORT_LEDGER.md (catálogo, inicio, Concierge, Behind the Scenes, personas favoritas, degradados, hora de fin, recordar elecciones, conservar pistas, velocidad/ETA de descargas, importar fuentes, copia de seguridad, diagnóstico, Live TV/calendario, avisos de contenido, prefetch, avatares GIF, asistente de IA).
6. **Tema Claro/Oscuro/Sistema** — `ThemeMode` local, tokens semánticos en todas las pantallas, sin reinicio, pruebas de contraste.
7. **Sincronización** — revisar `ProviderCredentialSync` en el código real; confirmar que los tokens no se registran en logs; prueba con cuenta real (con la configuración autorizada del propietario).
8. **Valoraciones IMDb de episodios** junto a OMDb (DECISIONS D-4).
9. **Release** — crear keystore y secretos `AVIX_RELEASE_*`; primera release `vX.Y.Z-avix.N` solo tras validar en dispositivo.

## Bloqueado / fuera de alcance
- CloudStream: experimental y desactivado hasta verificar la procedencia y la seguridad del runtime.
- Telegram: no existe implementación Android que portar (futuro).
- OAuth con clientes propios: requiere que el propietario registre apps en Trakt/Simkl/AniList/WeTrakr/MDBList con `avixnuvio://auth/...`.
