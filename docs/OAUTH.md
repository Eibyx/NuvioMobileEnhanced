# OAuth en Nuvio Eibyx

Todos los proveedores son **clientes públicos**: la APK solo lleva el **Client ID** (secretos del repositorio, ver BUILDING.md). Ningún Client Secret se compila ni se envía. Los tokens se guardan cifrados con una clave AES-GCM del Android Keystore, por perfil, y nunca se escriben en los registros.

| Proveedor | Secreto | Flujo | Redirect a registrar | Renovación | Desconexión |
|---|---|---|---|---|---|
| AniList | `ANILIST_CLIENT_ID` | Implicit grant (el único que AniList ofrece a apps sin secreto) | `avixnuvio://auth/anilist` | No existe (token de larga duración; al caducar se vuelve a conectar) | Local |
| MyAnimeList | `MAL_CLIENT_ID` (tipo «Other») | Authorization Code + PKCE `plain` (MAL no admite S256) + `state` de un solo uso | `avixnuvio://auth/mal` | `refresh_token`; reintento ante 401; 400/401 al renovar → desconecta | Local (MAL no tiene revocación) |
| Trakt | `TRAKT_CLIENT_ID` | Authorization Code + **PKCE S256**, sin `client_secret`; `state` de 256 bits, comparación en tiempo constante, un solo uso, caduca a los 15 min; callbacks sin autorización pendiente se rechazan | `avixnuvio://auth/trakt` | `refresh_token` (de un solo uso, rotativo); 400 → desconecta | `POST /oauth/revoke` + borrado local |
| Simkl | `SIMKL_CLIENT_ID` (app AUTH V2, tipo móvil) | AUTH V2: `simkl.com/oauth2/authorize` + **PKCE S256** + `state`, alcance `media:read media:write`, sin secreto | `avixnuvio://auth/simkl` | `refresh_token` (180 días, no rotativo) antes de caducar el de acceso (7 días); 400/401 → desconecta | `POST /oauth2/revoke` + borrado local |
| MDBList | `MDBLIST_CLIENT_ID` (client ID de la app, **no** la API key) | Device Code (`/oauth/device-authorization/`), sin secreto | — (no usa redirect) | `refresh_token` | `/oauth/revoke_token/` + borrado local |

WeTrakr no se configura (su código se conserva sin cambios).

## Trakt sin Client Secret

Documentación oficial consultada (octubre de 2026):
- https://developer.trakt.tv/docs/authentication-oauth: «Add PKCE to the Authorization Code Flow. It is the recommended way to sign users in and does not need a `client_secret`.» y «The Client Secret is deprecated for user sign-in».
- https://developer.trakt.tv/docs/pkce: autorización en `https://auth.trakt.tv/oauth/authorize`, solo `S256`, y en el canje «Do not send a `client_secret`».

Cambios: `code_verifier` de 43 caracteres (32 bytes de `SecureRandom`, base64url), `code_challenge = BASE64URL(SHA256(verifier))` sin relleno, `code_challenge_method=S256`, comprobación de `state`, canje con `code_verifier`, renovación y revocación sin secreto. Los tokens de Trakt pasan a guardarse cifrados (Keystore); los guardados en texto plano por versiones anteriores se cifran al leerlos.

**Configuración externa / limitaciones:**
- Trakt **desaconseja** (no prohíbe) los esquemas personalizados como `avixnuvio://` y recomienda Android App Links HTTPS. Para usar un App Link haría falta un dominio propio con `/.well-known/assetlinks.json` firmado con el certificado de la app; **no hay ninguno configurado y no se ha inventado**. Se mantiene `avixnuvio://auth/trakt` (lo que está registrado); PKCE protege el canje aunque otra app intercepte el código.
- El inicio de sesión por código de dispositivo (Trakt y Simkl) no usa PKCE porque la documentación no lo describe para ese flujo.
- Una sesión de Trakt iniciada con una app antigua basada en secreto puede fallar al renovarse (400): la app desconecta y hay que volver a conectar.

## Simkl AUTH V2

Según https://api.simkl.org (guía «Migrating V1 to V2»), un `client_id` de V2 enviado a los endpoints V1 `/oauth/*` devuelve `400 unauthorized_client`. El código anterior usaba V1 (`/oauth/authorize`, `/oauth/token` en JSON, `/oauth/pin`), así que un cliente V2 no funcionaba. Ahora todo usa `/oauth2/authorize`, `/oauth2/token`, `/oauth2/device` y `/oauth2/revoke` con cuerpos `application/x-www-form-urlencoded`. Las sesiones V1 existentes deben reconectarse.

## Estado de las pruebas

- Unitarias (JVM, ejecutadas): reto S256 con el vector de RFC 7636, URL de autorización, cuerpos de canje/renovación/dispositivo/revocación sin `client_secret`, formularios AUTH V2 de Simkl y códigos de error del sondeo.
- En dispositivo, con los client IDs reales: **NOT RUN** (pendiente: conectar, renovar y desconectar cada proveedor).
