# Fidelidad de subtítulos ASS/SSA

Objetivo: que libmpv (motor mpvRx) en Avix renderice ASS/SSA **igual que mpvRx**. No se afirma equivalencia sin capturas comparables.

## Por qué el motor importa

- **ExoPlayer** (Automático por defecto): el parser SSA de Media3 solo entiende `\pos` y `\an` (sin `\t`, `\clip`, `\fad`, karaoke, rotación…); con libass en modo `CUES`, cada evento se pinta una sola vez de forma estática.
- **libmpv (mpvRx)**: libass completo. Avix en modo Automático cambia una vez a libmpv al seleccionar un subtítulo ASS/SSA (ajuste «Preferir libmpv para ASS/SSA»).

## Procedimiento comparativo

1. Mismo dispositivo, misma resolución y orientación; mpvRx y Avix con la misma versión de `mpvlib`.
2. Mismos archivos (MKV con ASS incrustado y fuentes adjuntas, y ASS externo).
3. En ambos: pausar en el mismo `time-pos` (con mpvRx: `seek` absoluto; en Avix: seek a la misma marca) y capturar pantalla.
4. Comparar visualmente (y, si es posible, con diferencia de imagen). Anotar diferencias.

## Casos

| # | Caso | Archivo / etiqueta | Avix (libmpv) | Avix (ExoPlayer) | Igual a mpvRx |
|---|---|---|---|---|---|
| 1 | Estilos (fuente, tamaño, colores, borde, sombra) | `Style:` | NOT RUN | NOT RUN | NOT RUN |
| 2 | Posición `\pos`, `\an`, márgenes | | NOT RUN | NOT RUN | NOT RUN |
| 3 | Movimiento `\move` | | NOT RUN | NOT RUN | NOT RUN |
| 4 | Animación `\t` (escala, color) | | NOT RUN | NOT RUN | NOT RUN |
| 5 | Rotación `\frz`, `\frx`, `\fry` | | NOT RUN | NOT RUN | NOT RUN |
| 6 | Recortes `\clip`, `\iclip` (rect. y vectorial) | | NOT RUN | NOT RUN | NOT RUN |
| 7 | Fundidos `\fad`, `\fade` | | NOT RUN | NOT RUN | NOT RUN |
| 8 | Karaoke `\k`, `\kf`, `\ko` | | NOT RUN | NOT RUN | NOT RUN |
| 9 | Dibujo vectorial `\p1` | | NOT RUN | NOT RUN | NOT RUN |
| 10 | Capas y eventos superpuestos | | NOT RUN | NOT RUN | NOT RUN |
| 11 | Fuentes incrustadas en el MKV | adjuntos `.ttf/.otf` | NOT RUN | — | NOT RUN |
| 12 | Fuente ausente → fallback (Go Noto Current) | | NOT RUN | — | NOT RUN |
| 13 | Fuente importada por el usuario (`sub-fonts-dir`) | | NOT RUN | — | NOT RUN |
| 14 | CJK / árabe / RTL | | NOT RUN | NOT RUN | NOT RUN |
| 15 | SSA v4 heredado | `.ssa` | NOT RUN | NOT RUN | NOT RUN |
| 16 | ASS externo UTF-16 / Windows-1252 | | NOT RUN | NOT RUN | NOT RUN |
| 17 | ASS importado localmente (sin convertir a SRT) | | NOT RUN | NOT RUN | NOT RUN |

Cobertura automática disponible hoy: `MpvRxEngineSmokeTest` comprueba que los eventos ASS llegan al motor y que la pista se detecta como `ass` (texto, no render). `LocalSubtitleImportTest` comprueba que el contenido ASS importado se conserva byte a byte (etiquetas `\pos`, `\t`, `\clip`, `\k`, `\fad`).

## Pendiente

- Preparar un paquete de muestras con licencia libre (sin material con copyright de terceros) en `docs/ass/samples/` o como artefacto de pruebas.
- Prueba de capturas automatizada (screenshot en emulador con `vo=gpu`) — requiere GPU en el emulador; evaluar con `swiftshader`.
