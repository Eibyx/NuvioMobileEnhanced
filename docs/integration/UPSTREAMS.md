# Fuentes de Nuvio Avix y reglas de actualización

## Remotos

```bash
git remote add luqman   https://github.com/luqmanfadlli/NuvioMobile-Enhanced   # rama enhanced
git remote add upstream https://github.com/NuvioMedia/NuvioMobile              # rama cmp-rewrite
git remote add yesnt    https://github.com/yesnt10/NuvioMobile-Enhanced        # rama enhanced
for r in luqman upstream yesnt; do git config remote.$r.tagOpt --no-tags; done
```

Los tags se descargan con `--no-tags`, porque Luqman y el original usan los mismos nombres (`0.5.10-beta`…) en commits distintos. Los tags propios de Avix tienen la forma `vX.Y.Z-avix.N`.

## Estado integrado

| Fuente | Rama | Último commit integrado | Fecha | Cómo |
|---|---|---|---|---|
| NuvioMedia | `cmp-rewrite` | `808272dcec4be02397282600b70a07111576e0c7` | 2026-10-15 | Padre del fork; `cmp-rewrite` de Eibyx/NuvioMobileEnhanced = este commit |
| Luqmanfadlli | `enhanced` | `2f2e9c1e5388649ea5f12cfb4da52464f32bc53a` | 2026-10-15 | `git merge` en `integration/avix` (DECISIONS.md D-2) |
| Yesnt10 | `enhanced` | revisado hasta `894499115fb1a69cd4bb34d6969a74832f0e5636` | 2026-09-02 | Solo port manual (YESNT_PORT_LEDGER.md) |

## Reglas

1. **Luqman** es la base técnica: `git merge luqman/enhanced` en una rama `sync/luqman-AAAAMMDD` → PR hacia `integration/avix` → CI verde → merge.
2. **Original** solo cuando trae commits que Luqman todavía no tiene: mismo procedimiento con `sync/upstream-…`.
3. **Yesnt10: nunca `git merge`.** Su historial borra en silencio unos 58 archivos de upstream (Simkl, AppGate…). Cada función se reimplementa en un PR propio y se anota en el ledger.
4. Los conflictos recurrentes se resuelven según [DECISIONS.md](DECISIONS.md).
5. El workflow `upstream-watch.yml` automatiza la detección: crea PRs (nunca los fusiona) o issues con los conflictos.

## Bibliotecas multimedia

| Biblioteca | Versión fijada | Origen | Verificación |
|---|---|---|---|
| `app.gyrolet.mpvlib:mpvlib` | 1.1.3 | Releases de GitHub de Riteshp2001/mpvlibAndroid | SHA-256 `1b2161c1…fd7b` comprobado en cada build |
| `io.github.peerless2012:ass-media` | 0.5.1 | Maven Central | Gradle |
| Media3 (fork) | 1.8.0 | `composeApp/libs/lib-*.aar` | En el repositorio |

`upstream-watch.yml` abre un issue cuando aparece un tag nuevo; la actualización es siempre manual y requiere pasar las pruebas del motor (docs/MPVRX_INTEGRATION.md).
