# Compilar Nuvio (Avix)

## Requisitos

- JDK 17 (CI) o 21 (local: también funciona)
- Android SDK: `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`
- No hace falta NDK: las bibliotecas nativas llegan precompiladas como AAR. El motor libmpv (`app.gyrolet.mpvlib:mpvlib:1.1.3`) se descarga de las releases de GitHub de Riteshp2001/mpvlibAndroid y Gradle verifica su SHA-256 antes de compilar (ver [docs/MPVRX_INTEGRATION.md](docs/MPVRX_INTEGRATION.md)).

## Compilación local

```bash
echo "sdk.dir=/ruta/al/android-sdk" > local.properties    # más los valores opcionales de abajo
./gradlew :composeApp:testAndroidHostTest -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore
./gradlew :androidApp:assembleFullDebug  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore
```

La APK queda en `androidApp/build/outputs/apk/full/debug/`.

El sabor **`full`** es el que usa Avix. Incluye plugins, P2P y la conexión a servidores autoalojados de Nuvio.

## Configuración (`local.properties`)

Todas las claves son **opcionales para compilar**. Si falta una, solo se desactiva la función que la usa.

| Clave | Para qué | Tipo |
|---|---|---|
| `NUVIO_SUPABASE_URL` | URL del backend de cuentas de Nuvio (`https://api.nuvio.tv`) | Config. pública del servicio |
| `NUVIO_SUPABASE_ANON_KEY` | Clave **publicable** (`sb_publishable_…`) de ese backend | Config. pública del servicio |
| `NUVIO_SUPABASE_FALLBACK_URL` | URL de respaldo (opcional) | — |
| `TMDB_API_KEY` | Metadatos de TMDB | Clave de app propia |
| `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, `TRAKT_REDIRECT_URI` | Trakt. Redirect por defecto: `avixnuvio://auth/trakt` | App OAuth propia en trakt.tv |
| `SIMKL_CLIENT_ID`, `SIMKL_REDIRECT_URI`, `SIMKL_APP_NAME` | Simkl. Redirect por defecto: `avixnuvio://auth/simkl` | App propia en simkl.com |
| `ANILIST_CLIENT_ID` | AniList. Redirect a registrar: `avixnuvio://auth/anilist` | Cliente propio en anilist.co |
| `MAL_CLIENT_ID` | MyAnimeList (seguimiento automático de episodios). Registrar un cliente de tipo «Other» con redirect `avixnuvio://auth/mal` | Cliente propio en myanimelist.net |
| `WETRAKR_CLIENT_ID` | WeTrakr (código de dispositivo) | Cliente propio |
| `MDBLIST_CLIENT_ID` | MDBList (código de dispositivo) | Cliente propio |
| `PREMIUMIZE_CLIENT_ID` | Premiumize | Cliente propio |
| `OMDB_API_KEY`, `IMDB_RATINGS_API_BASE_URL`, `IMDB_TAPFRAME_API_BASE_URL`, `INTRODB_API_URL` | Valoraciones y saltar intro | Opcional |
| `SENTRY_DSN` | Informes de errores | Vacío por defecto |

Avix **no reutiliza** las credenciales de aplicación de Nuvio ni de los forks:

- los `CLIENT_ID` de AniList y WeTrakr, que en Luqman estaban fijos en el código, ahora salen de esta configuración;
- el `MAL_CLIENT_ID` de MyAnimeList (portado desde AKRusso) tampoco viene incluido: vacío por defecto;
- sin un cliente propio, esas integraciones aparecen como no disponibles.

## Secretos de GitHub Actions

### Cuenta de Nuvio (iniciar sesión)

Sin `NUVIO_SUPABASE_ANON_KEY` la APK se compila igual, pero **no puede iniciar sesión** (correo ni enlace): la pantalla de inicio de sesión lo indica y el artefacto de CI se llama `Nuvio-Avix-Debug-N-sin-cuenta`. Origen de los valores:

- `NUVIO_SUPABASE_URL`: `https://api.nuvio.tv` (público; ya figura en el código, `ServerDiscovery.kt`).
- `NUVIO_SUPABASE_ANON_KEY`: la clave **publicable** que el propio servidor de Nuvio publica para clientes en su documento de descubrimiento `https://api.nuvio.tv/.well-known/nuvio` (campo `publishable_key`). Es una clave pública de cliente (rol `anon` de Supabase), no una clave privada. No se extrae de ninguna APK.

Se usa la autenticación y la sincronización de Nuvio sin cambios (misma cuenta; sin servidor propio). El uso desde una app derivada no está autorizado expresamente por Nuvio (ver `docs/integration/DECISIONS.md` D-6).

Configurarlo desde el móvil (navegador):
1. Abrir `https://api.nuvio.tv/.well-known/nuvio` y copiar el valor de `publishable_key` (sin comillas).
2. Abrir `https://github.com/Eibyx/NuvioMobileEnhanced/settings/secrets/actions` → **New repository secret**.
3. **Name:** `NUVIO_SUPABASE_ANON_KEY` · **Secret:** pegar el valor → **Add secret**.
4. **Actions** → **CI** → **Run workflow** → rama `integration/avix` → **Run workflow**.

Desde Termux (con `gh` autenticado): `curl -s https://api.nuvio.tv/.well-known/nuvio | jq -r .publishable_key | gh secret set NUVIO_SUPABASE_ANON_KEY -R Eibyx/NuvioMobileEnhanced` (el valor no se muestra en pantalla).

En **Settings → Secrets and variables → Actions** del repositorio:

| Secreto | Contenido | Necesario para |
|---|---|---|
| `NUVIO_SUPABASE_ANON_KEY` | Clave **publicable** del servidor oficial (`publishable_key` de https://api.nuvio.tv/.well-known/nuvio) | **Iniciar sesión con la cuenta de Nuvio** (correo y enlace) |
| `NUVIO_SUPABASE_URL` | Opcional. Por defecto `https://api.nuvio.tv` | Cuenta de Nuvio |
| `NUVIO_LOCAL_PROPERTIES_BASE64` | Opcional. `base64 -w0 local.properties` con el resto de claves de la tabla anterior **sin** `sdk.dir` | Integraciones con clave (TMDB, Trakt, …) |
| `AVIX_RELEASE_KEYSTORE_BASE64` | `base64 -w0 avix-release.jks` | Workflow **Release** |
| `AVIX_RELEASE_STORE_PASSWORD` | Contraseña del keystore | Release |
| `AVIX_RELEASE_KEY_ALIAS` | Alias de la clave | Release |
| `AVIX_RELEASE_KEY_PASSWORD` | Contraseña de la clave | Release |

Crear el keystore (una vez, guardarlo fuera del repositorio; si se pierde no se podrán publicar actualizaciones con la misma firma):

```bash
keytool -genkeypair -v -keystore avix-release.jks -alias avix \
  -keyalg RSA -keysize 4096 -validity 10000
```

Los workflows nunca imprimen los valores de los secretos. El workflow **Release** falla si no hay keystore: nunca firma releases con la clave de depuración.

## Workflows

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | Push a `cmp-rewrite`, `integration/**`, `experiment/**`; PR; manual | Tests unitarios + `Nuvio-Avix-Debug.apk` como artefacto |
| `mpvrx-tests.yml` | Cambios en el reproductor, manual | Pruebas instrumentadas del motor mpvRx en emulador (`MpvRxEngineSmokeTest`) |
| `android-release.yml` | Manual o tag `v*-avix.*` | Tests + APKs release firmadas (`Nuvio-Avix-Release.apk` universal + por ABI) + `SHA256SUMS.txt` + GitHub Release |
| `upstream-watch.yml` | Lunes 06:17 UTC, manual | Propone PRs de sincronización con Luqman y el original hacia `integration/avix` (nunca los fusiona); informa de commits nuevos de Yesnt10 y de versiones nuevas de las bibliotecas multimedia |

`versionCode` en CI: `100000 + número de ejecución` (debug) y `200000 + número de ejecución` (release). `versionName`: la versión de upstream integrada (`iosApp/Configuration/Version.xcconfig`); en release, `X.Y.Z-avix.<versionCode>` (ver [docs/RELEASE.md](docs/RELEASE.md)).

## Iconos y marca

Se usan los recursos gráficos originales de Nuvio sin modificar (no se generan iconos propios). Ver la nota de marca del [README](README.md).
