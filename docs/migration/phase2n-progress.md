# Phase 2n Progress

| Sub-layer | Status | Commit | Tests delta | Notes |
|-----------|--------|--------|-------------|-------|
| Docs | ✅ | `92c2fef` | — | design + plan revised: A.0 Qint64 + A.1 PowKernel + A.2 integration |
| Vendor sources | ✅ | `71d441e` | — | CORE-MATH pow.c + pow.h + qint.h vendored |
| **A.0 Qint64 infra** | ✅ | `f157abf` | 816 → 817 | 870 LOC Qint64.java + 308 test + 478 probe + 235 C shim. 322 probe cases / 16 ops. EXACT. |
| A.1.a PowKernel probe | ✅ | `6ebf44d` | — | 2,763 reference cases / 745KB pow.json |
| A.1.b PowKernel scaffold | ✅ | `db75406` | 817 → 818 | specials path + JQuantMath.pow facade. 38 specials cases bit-exact. |
| A.1.b/1 stage-1 + tables | ✅ | `6496f6b` | — | 738 LOC. 99.93% (2,761/2,763) bit-exact. 2 hard-rounding cases fall through. |
| A.1.c stage-2 Dint64 Ziv | ✅ | `e831fd7` | — | 2,285 LOC. **100% (2,763/2,763) bit-exact** vs CORE-MATH cr_pow. |
| **A.2 Integration** | ✅ | `e4b1230` | — | 29 files / 57 sites Math.pow → JQuantMath.pow |
| L2 completion | ⏳ | — | — | tag + memory + README + teardown |

## Outcome

- **Tests:** 818/0/0/22
- **Scanner WIP:** 0
- **JQuantMath primitives:** exp/log/sin/cos/**pow** (5 of 5 transcendentals correctly-rounded)
- **u128 emulation:** Dint64 (2×64) + Qint64 (4×64)
- **Phase 2l A19 reframe:** MOL_TOL 1e-7 floor is platform FP ordering, not Math.pow

## Decisions
- A.0: JDK 11 target → inline u128 mul-hi; C shim resolves C/C++ conflicts; mod-64 shift semantics preserved
- A.1: stage-2 Dint64 ops inlined into PowKernel (avoids cross-pollution with sin/cos's dint.h variant)
- A.1: stage-3 (Qint64 chain + exact_pow) deferred — stage-2 alone closes all 2,763 reference cases
- A.2: skipped `Math.pow(2.0, K_constant)` (compile-time constant, no leverage)
