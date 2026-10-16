# Publicar e instalar Nuvio (Avix)

## Versionado

| Tipo | `versionName` | `versionCode` | Tag |
|---|---|---|---|
| Debug (CI) | versión de upstream integrada (`iosApp/Configuration/Version.xcconfig`) | `100000 + run_number` | — |
| Release | `X.Y.Z-avix.<versionCode>` | `200000 + run_number` | `vX.Y.Z-avix.<versionCode>` |

`X.Y.Z` es la versión de upstream integrada. El sufijo `-avix.N` es un *prerelease* semver: el actualizador de la app lo trata como canal **Beta** y lo ofrece en orden (N creciente). Una release estable sin sufijo solo se publicará cuando exista una versión validada en dispositivo.

## Requisitos (una vez)

1. Crear el keystore y guardarlo **fuera** del repositorio (si se pierde, no se pueden publicar actualizaciones instalables encima):
   ```bash
   keytool -genkeypair -v -keystore avix-release.jks -alias avix -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Secretos del repositorio (Settings → Secrets and variables → Actions): `AVIX_RELEASE_KEYSTORE_BASE64` (`base64 -w0 avix-release.jks`), `AVIX_RELEASE_STORE_PASSWORD`, `AVIX_RELEASE_KEY_ALIAS`, `AVIX_RELEASE_KEY_PASSWORD`, y opcionalmente `NUVIO_LOCAL_PROPERTIES_BASE64` (ver BUILDING.md).

## Publicar

- **Manual:** Actions → *Android release* → *Run workflow* (marcado como pre-release por defecto).
- **Por tag:** `git tag v0.5.10-avix.200001 && git push origin v0.5.10-avix.200001`.

El workflow ejecuta los tests, compila las APK firmadas, **rechaza** cualquier APK firmada con certificado de depuración, genera `SHA256SUMS.txt` y crea la GitHub Release con:

- `Nuvio-Eibyx-Release.apk` (universal)
- `Nuvio-Eibyx-Release-arm64-v8a.apk`, `-armeabi-v7a`, `-x86`, `-x86_64`
- `SHA256SUMS.txt`

No se publica una release como estable sin haber superado las pruebas de docs/TESTING.md en dispositivo.

## Instalar

1. Descargar la APK (universal o la de la ABI del teléfono; casi todos los móviles actuales son `arm64-v8a`).
2. Comprobar el SHA-256: `sha256sum Nuvio-Eibyx-Release.apk` y compararlo con `SHA256SUMS.txt`.
3. Abrir la APK y permitir «instalar apps desconocidas» para el navegador o el gestor de archivos.

Se instala junto a Nuvio original, Yesnt10 y Luqman (`applicationId` distinto). Debug (`com.eibyx.nuvio.debug`) y release (`com.eibyx.nuvio`) también pueden convivir.

## Actualizar

- Desde la app: el actualizador integrado (canal Beta). Descarga la APK de la ABI del dispositivo desde las releases de Eibyx/NuvioMobileEnhanced.
- Manualmente: instalar la APK nueva encima. Solo funciona si está firmada con **la misma clave**; una APK de CI (debug) no actualiza una release ni al revés.

## Cambio de paquete `com.avix.nuvio` → `com.eibyx.nuvio`

Nuvio Eibyx usa un `applicationId` nuevo (D-13). Para Android es una aplicación distinta:

- **No actualiza** a una instalación de `com.avix.nuvio` / `com.avix.nuvio.debug`: se instala **al lado**. El actualizador integrado de la versión antigua descargará la APK nueva, pero Android la instalará como otra app.
- **No se migran datos automáticamente.** La instalación nueva empieza vacía. No se copian ni se borran los datos de la antigua; si ya no se necesita, se desinstala a mano.

| Datos | ¿Qué pasa en Nuvio Eibyx? |
|---|---|
| Cuenta de Nuvio (sesión de Supabase) | Hay que **volver a iniciar sesión**. |
| Perfiles, addons, biblioteca, progreso, historial y colecciones de la cuenta | Vuelven al iniciar sesión, porque se sincronizan con la cuenta (igual que en Nuvio original). |
| Perfil seleccionado, caché local de historial, addons solo locales | No se conservan; se eligen o añaden de nuevo. |
| Ajustes solo del dispositivo (tema, diseño de Inicio, reproductor, motor por título, subtítulos, DNS…) | No se conservan. Se pueden llevar con **Ajustes → Avanzado → Exportar** en la app antigua e **Importar** en la nueva (mismo formato). |
| Preferencias del reproductor guardadas en el dispositivo | Igual que la fila anterior. |
| Tokens OAuth (Trakt, Simkl, AniList, MyAnimeList, MDBList) | No se conservan (están cifrados con una clave del Keystore ligada a la app antigua). Hay que **volver a conectar** cada servicio. Los redirects `avixnuvio://auth/…` siguen siendo los mismos. |
| Claves de API propias (GIPHY, IA, Tavily) | No se conservan (cifradas con el Keystore de la app antigua); se introducen de nuevo. |
| Descargas | Quedan en la app antigua; no se trasladan. |

En resumen: hace falta reconfigurar (iniciar sesión, reconectar servicios, importar la copia de ajustes locales si se quiere).
