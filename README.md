# Nuvio (Avix)

Rama de integración **Nuvio Avix** de [Eibyx/NuvioMobileEnhanced](https://github.com/Eibyx/NuvioMobileEnhanced), fork de [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile).

Es una aplicación Android (Kotlin Multiplatform + Compose Multiplatform) que parte de **Nuvio Mobile Enhanced (Luqman)**, conserva todas sus funciones, añade funciones exclusivas portadas a mano desde Yesnt10 y sustituye el libmpv del reproductor por el **motor de mpvRx** (`mpvlib`).

> **Proyecto no oficial.** No está afiliado a Nuvio Media ni a los autores de los forks en los que se basa. Los errores se reportan en este repositorio.

## Proyectos de origen

| Proyecto | Papel |
|---|---|
| [NuvioMedia/NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) (`cmp-rewrite`) | Proyecto original y padre del fork (`cmp-rewrite` de este repo lo sigue) |
| [luqmanfadlli/NuvioMobile-Enhanced](https://github.com/luqmanfadlli/NuvioMobile-Enhanced) (`enhanced`) | **Base técnica** (fusionada con historial) |
| [yesnt10/NuvioMobile-Enhanced](https://github.com/yesnt10/NuvioMobile-Enhanced) (`enhanced`) | Funciones exclusivas **portadas a mano** (nunca se fusiona su historial) |
| [Riteshp2001/mpvlibAndroid](https://github.com/Riteshp2001/mpvlibAndroid) `mpvlib 1.1.3` | Motor libmpv (el mismo que usa mpvRx). Solo el motor: sin UI, sin shaders, sin Anime4K |

Hashes integrados y reglas de actualización: [docs/integration/UPSTREAMS.md](docs/integration/UPSTREAMS.md).

## Instalación junto a otras versiones de Nuvio

| App | `applicationId` |
|---|---|
| **Nuvio (Avix)** | `com.avix.nuvio` (debug: `com.avix.nuvio.debug`) |
| Nuvio (original) | `com.nuvio.app` |
| Nuvio Enhanced (Yesnt10) | `com.nuvio.enhanced` |
| Nuvio Enhanced (Luqman) | `com.nuvio.media` (debug `com.nuviodebug.com`) |

El nombre visible es **«Nuvio»** y se conservan el icono, el logotipo y el splash originales de Nuvio. Para no abrir otra app al volver de un inicio de sesión, Avix usa su propio esquema de enlaces: **`avixnuvio://`** (p. ej. `avixnuvio://auth/trakt`).

## Descargar la APK

- **Debug (cada push a `integration/avix` y cada PR):** pestaña **Actions** → workflow **CI** → la ejecución más reciente en verde → **Artifacts** → `Nuvio-Avix-Debug-…`. El ZIP contiene `Nuvio-Avix-Debug.apk` y su SHA-256.
- **Release firmada:** pestaña **Releases** (`Nuvio-Avix-Release.apk` universal + APK por ABI + `SHA256SUMS.txt`), cuando se configure la firma. Ver [docs/RELEASE.md](docs/RELEASE.md).

## Cuenta de Nuvio

Se conserva sin cambios la autenticación y sincronización de Luqman/Nuvio (Supabase): perfiles, addons, biblioteca, progreso, historial y colecciones. No hay servidor propio ni cuentas nuevas.

- Para conectarse, la compilación necesita la configuración pública del backend (`NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY`) en el secreto `NUVIO_LOCAL_PROPERTIES_BASE64` (ver [BUILDING.md](BUILDING.md)). Sin ella, la app funciona en modo **«Continuar sin cuenta»**. Que la cuenta funciona solo se declarará cuando se haya probado.
- Los ajustes exclusivos de Avix y los de Luqman que el original no conoce se guardan **solo en el dispositivo** (ver [DECISIONS.md](docs/integration/DECISIONS.md), D-5).

## Documentación

| Documento | Contenido |
|---|---|
| [FEATURES.md](FEATURES.md) | Estado real de cada función (no se marca nada como probado sin evidencia) |
| [BUILDING.md](BUILDING.md) | Compilar, configuración y secretos |
| [NEXT_STEPS.md](NEXT_STEPS.md) | Trabajo pendiente por fases |
| [CHANGELOG.md](CHANGELOG.md) | Cambios |
| [docs/TESTING.md](docs/TESTING.md) | Pruebas y resultados (PASS/FAIL/SKIPPED/BLOCKED/NOT RUN) |
| [docs/MPVRX_INTEGRATION.md](docs/MPVRX_INTEGRATION.md) | Motor mpvRx: dependencia, verificación, diferencias de API |
| [docs/ASS_SSA_TESTS.md](docs/ASS_SSA_TESTS.md) | Pruebas de fidelidad de subtítulos ASS/SSA |
| [docs/RELEASE.md](docs/RELEASE.md) | Publicar versiones e instalar/actualizar |
| [docs/integration/](docs/integration/) | Upstreams, decisiones y registro de ports de Yesnt10 |

## Compilar

```bash
echo "sdk.dir=/ruta/al/android-sdk" > local.properties
./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore
```

## Licencia, atribución y marca

Obra derivada distribuida bajo **GNU GPL v3** (ver [LICENSE](LICENSE)), como los proyectos en los que se basa.

- Código original: © NuvioMedia / tapframe y contribuidores de NuvioMobile.
- Mejoras Enhanced: © Luqman Fadlli y contribuidores de luqmanfadlli/NuvioMobile-Enhanced.
- Funciones portadas: © contribuidores de yesnt10/NuvioMobile-Enhanced.
- Motor de reproducción: `mpvlib` (wrapper MIT de Riteshp2001/mpvlibAndroid) con mpv (LGPL/GPL), FFmpeg (LGPL/GPL según configuración), libass (ISC), HarfBuzz, FreeType, fribidi y demás componentes nativos, cada uno con su licencia. mpvRx (AGPL-3.0-or-later) se usa solo como referencia; no se copia su código de aplicación. Ver [docs/MPVRX_INTEGRATION.md](docs/MPVRX_INTEGRATION.md).
- Media3 (Apache-2.0), ass-media, QuickJS (MIT), etc.

**Marca:** el nombre «Nuvio», su icono y su logotipo pertenecen a sus titulares. Esta rama los conserva por petición expresa, para uso personal junto a las demás variantes. **Antes de cualquier distribución pública** (tiendas, webs, releases abiertas) hay que obtener permiso de los titulares o sustituir nombre, icono y logotipo por una identidad propia.

Cada APK corresponde a un commit público de este repositorio, que es su código fuente correspondiente según la GPL v3. La app muestra metadatos y reproduce contenido de fuentes que instala el propio usuario; no aloja ni distribuye contenido.
