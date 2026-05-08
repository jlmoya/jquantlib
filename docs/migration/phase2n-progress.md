# Phase 2n Progress

| Sub-layer | Status | Commit | Tests delta | Notes |
|-----------|--------|--------|-------------|-------|
| Docs | ✅ | `92c2fef` | — | design + plan revised: A.0 Qint64 + A.1 PowKernel + A.2 integration |
| Vendor sources | ✅ | `71d441e` | — | CORE-MATH pow.c + pow.h + qint.h vendored |
| **A.0 Qint64 infra** | ✅ | `f157abf` | 816 → 817 | 870 LOC Qint64.java + 308 test + 478 probe + 235 C shim. 322 probe cases / 16 ops. EXACT. |
| A.1 PowKernel | ⏳ | — | — | dispatching |
| A.2 Integration | ⏸ | — | — | after A.1 |
| L2 completion | ⏸ | — | — | tag + memory + README + teardown |

## Decisions taken in A.0
- JDK 11 target → ported inline u128 mul-hi from Dint64 (no `Math.unsignedMultiplyHigh`)
- `qint64_shim.c` compiled as C resolves dint.h/qint.h C++-overload conflicts and pre-C99 string-concat in `print_qint`
- Mod-64 shift semantics intentionally preserved (Java JLS §15.19 matches arm64/x86 platform behaviour)
- Surface limited to operations actually called from pow.c — `lshift`/`rshift`/`dint_to_qint`/`qint_to_dint` etc. listed in design were not real CORE-MATH symbols and were skipped
