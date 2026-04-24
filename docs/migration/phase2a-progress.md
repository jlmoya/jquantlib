# Phase 2a — Execution Progress

**Last update:** 2026-04-24
**Tip commit:** `6024497` on `origin/main`
**Baseline test suite:** 612 tests, 25 skipped, 0 failures.
**Scanner:** 62 stubs (6 WIP + 0 NI + 56 suspect). Change from Phase-1 tip: `-1` (TreeLattice2D allowlisted in L1). No WIP stubs filled yet — MINPACK helpers are being added additively alongside the old `MinpackC` inner class.

---

## Done

| Layer | Task | Commit |
|---|---|---|
| L0 | Pre-flight (baseline green, scanner snapshot, harness verified) | — (no commit) |
| L1 | WI-1 scanner allowlist + method-name heuristic fix | `5134555` |
| L2 Task 2.1 | Port `Minpack.enorm` (2-arg + 3-arg overload) | `6e46f82` |
| L2 Task 2.2 | Port `Minpack.qrfac` (public, raw double[]) + bit-exact probe + QRDecomposition kept on old wrapper | `f662410` |
| L2 Task 2.3 | Port `Minpack.qrsolv` + probe | `6024497` |

---

## Next — L2 Task 2.4 (lmpar)

See `docs/migration/phase2a-plan.md` §Layer 2 Task 2.4. Approximate scope:
- Port C++ `MINPACK::lmpar` from `ql/math/optimization/lmdif.cpp` lines 810-1124 (~300 LOC) into `Minpack.java` as a private-static method (it's file-local in C++).
- Depends on `qrsolv` (already landed) and `enorm` (already landed).
- Dedicated probe is optional — the plan calls for a "well-conditioned" sanity test only, because lmpar's full behavior will be exercised via the lmdif-level probes.

---

## Deviations from the original design/plan to be aware of

1. **Class name is `Minpack` (not `MINPACK` as design P2A-5 specified).** Reason: macOS case-insensitive FS means `MINPACK.java` and `Minpack.java` are the same file; the existing `Minpack.java` had a public caller (`QRDecomposition.java:109`), so a rename would have required a synchronized multi-file change. Java-idiom `Minpack` is also more conventional (Java prefers `Json` over `JSON`, etc.). Noted in commit `6e46f82`.

2. **Old `MinpackC` inner class still present.** It holds the 2010-vintage partial MINPACK port with `MinpackC.enorm(n, x, offset)` and `MinpackC.qrfac(double[], …)`. The new top-level `Minpack.enorm`, `Minpack.qrfac`, `Minpack.qrsolv` are additive — old wrapper `public static void qrfac(int, int, Matrix, boolean, …)` still delegates to `MinpackC.qrfac`. The legacy inner class (and old wrapper) should be phased out when `lmdif` lands and QRDecomposition is migrated to the new signature.

3. **Tight tolerance, not exact, for qrfac/qrsolv floating arrays.** Observed ~1-ulp divergence between JVM and C++ on trailing bits of `a_out[4]` / `a_out[5]`. Same algorithm, same inputs, bit-identical `enorm`. Likely JVM-side FMA or strictfp behavior. Per design §4.2, tight tier is acceptable with inline justification — which is included in `MinpackTest.java`. If the next session wants to chase the root cause, probe outputs are bit-stable so the reproducer is the probe cases themselves.

4. **Probe reference capture: use `generate-references.sh` directly.** `verify-harness.sh` regenerates into `references/` and then restores a snapshot on exit via a trap — so a NEW reference file gets created during the run but wiped at script end. For first-time probe authoring, run `./migration-harness/generate-references.sh` directly, then the file persists.

5. **Scanner heuristic now uses `NON_METHOD_NAMES` filter.** Previously the method-name extraction matched `throw new FooException(` as a method declaration (naming the stub after the exception). L1 fixed this by starting the walk-back at `idx-1` plus filtering Java keyword-statements. Stub IDs are now semantically meaningful (`LevenbergMarquardt#LevenbergMarquardt`, `HestonProcess#evolve`, etc.) but one cosmetic imperfection remains: `CapHelper#Period` still picks up a `new Period(...)` line inside the method body. CapHelper is carved to 2b so the imperfection is acceptable.

---

## Remaining work (from `phase2a-plan.md`)

- L2 Task 2.4 lmpar (sanity test)
- L2 Task 2.5 fdjac2 (small)
- L2 Task 2.6 lmdif driver + 4 LM-level probes (largest single port in Phase 2a, ~700 LOC)
- L2 Task 2.7 Fill `LevenbergMarquardt` stubs + adapt `QRDecomposition` to the new MINPACK signature; phase out `MinpackC`
- L2 Tasks 2.8-2.10 Un-skip `SABRInterpolationTest`, `InterpolationTest` LM cases, `OptimizerTest`
- L3 WI-3 HestonProcess QuadraticExponential branch
- L4 WI-4 Audit 56 `numerical_suspect` markers (tiered triage; possibly longest single layer)
- L5 WI-5 Delete `QL.validateExperimentalMode` and call sites
- L6 Completion report + tag `jquantlib-phase2a-complete`

Pause-trigger A7 remains active for WI-4: if Tier-2 divergences exceed 20 of 56, pause for scope reassessment.
