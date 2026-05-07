# Phase 2k Design — Gaussian1D Feature Completion + math.matrixutilities Cleanup

**Status:** approved 2026-05-02 (autonomous mode)
**Predecessor:** `jquantlib-phase2j.5-complete` @ `22f65b8` (tests `801/0/0/22`, scanner WIP=0)
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

---

## 1. Context & Motivation

Phase 2j.5 delivered the Gaussian1D family but with three documented stubs:
- MarkovFunctional `SabrSmile` adjustment throws on `validate()` — needs `SabrInterpolatedSmileSection`
- MarkovFunctional `CustomSmile` adjustment throws on `validate()` — needs `CustomSmileFactory` (an MF inner class in C++)
- Nonstandard/FloatFloat engine basket helpers throw `UnsupportedOperationException` — need `BasketGeneratingEngine`

Plus one technical-debt item flagged in Phase 2j.5 C.1: `TqrEigenDecomposition` is embedded as a private inner class of `GaussianQuadrature`; should be lifted to `org.jquantlib.math.matrixutilities` for broader reuse.

Phase 2k closes all four items in 3 parallel tracks. Modest phase scope (~1500-2500 Java LOC across ~3-5 commits).

### Goals (in scope)

- **Track A — SabrInterpolatedSmileSection** (~338 LOC C++ → ~500-700 Java) + MF SabrSmile branch wiring
- **Track B — BasketGeneratingEngine** (~479 LOC C++ → ~700-900 Java) + Nonstandard/FloatFloat basket-helper wiring
- **Track C — TqrEigenDecomposition lift** to `org.jquantlib.math.matrixutilities` (refactor) + `CustomSmileFactory` MF inner class + MF CustomSmile branch wiring

### Non-goals

- `noarbsabrinterpolatedsmilesection` (lives in `experimental/`, separate Phase 3+ scope)
- `JQuantMath.lgamma` / `pow` (still no path)
- Douglas ADI / FdmAffineModelTermStructure (separate phase)
- Phase 2h Fdm completeness items
- Phase 3+ subsystems

### Outcome forecast

| Metric | Phase 2j.5 tip | Phase 2k target | ceiling |
|--------|----------------|------------------|---------|
| Tests | 801/0/0/22 | ~805-810/0/0/22 (+1 per port + a few MF integration tests) | ~815 |
| Scanner WIP | 0 | 0 | 0 |
| MarkovFunctional smile branches | SabrSmile/CustomSmile throw | All 4 modes (None/Kahale/SabrSmile/CustomSmile) operational | — |
| Nonstandard/FloatFloat basket helpers | throw UnsupportedOperationException | functional via BasketGeneratingEngine | — |

---

## 2. Approach

3 parallel tracks. Same playbook as Phase 2j.5: 3 worktrees, dispatch in parallel after L0.

**Decision (P2K-1):** Approach = 3 parallel tracks (A/B/C, disjoint files).
**Decision (P2K-2):** Source-of-truth = QuantLib v1.42.1 C++ at pin SHA. Standard ports.
**Decision (P2K-3):** `JQuantMath.{exp,log,sin,cos}` from day one in any new code.
**Decision (P2K-4):** Tier per artifact — TIGHT default; LOOSE acceptable per A19 for engine integration.
**Decision (P2K-5):** Each track lands its target port AND its MF/engine wiring in the same commit (delivers a complete unblock). Track C lifts TqrEigen as a separate refactor commit before the CustomSmileFactory commit (clean separation).
**Decision (P2K-6):** Direct-to-main signed `-s` no Co-authored-by.

---

## 3. Worktree Topology

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2k-A` | `phase-2k-A-sabr-smile` | SabrInterpolatedSmileSection + MF SabrSmile wiring |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2k-B` | `phase-2k-B-basket-generating` | BasketGeneratingEngine + Nonstandard/FloatFloat basket helpers |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2k-C` | `phase-2k-C-tqr-customsmile` | TqrEigenDecomposition lift (refactor) + CustomSmileFactory MF inner class + MF CustomSmile wiring |

### Layer ordering

- **L0** — Pre-flight + 3 worktrees + progress doc
- **L1** — Tracks A/B/C dispatch in parallel after L0. Sub-commits sequential within each track.
- **L2** — Completion doc + tag `jquantlib-phase2k-complete` + memory + README + worktree teardown.

---

## 4. Tolerance, Probes & Test Discipline

Same as prior phases. Each track contributes 1-2 new probes:
- `sabr_interpolated_smile_section_probe.cpp`
- `basket_generating_engine_probe.cpp`
- `tqr_eigen_decomposition_probe.cpp` (refactor needs no new probe — existing `gauss_hermite_integration.json` continues to validate)

ONE @Test method with collect-all-failures pattern. TIGHT default; LOOSE acceptable per A19.

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers

Carry-forward from Phase 2j.5: A2/A3/A4/A8/A9/A15/A16/A17/A18/A19/A20/A21/A22. No new triggers expected.

### Decision log

P2K-1..P2K-6 above. Carries forward all prior P2J5-* / P2J-* / P2I-* etc.

### Exit criteria

Phase 2k complete when **all** hold:

1. Track A: SabrInterpolatedSmileSection ported + MF SabrSmile branch operational + tests passing
2. Track B: BasketGeneratingEngine ported + Nonstandard/FloatFloat basket helpers wired + tests passing
3. Track C: TqrEigenDecomposition lifted to math.matrixutilities + CustomSmileFactory inner class + MF CustomSmile branch operational + tests passing
4. Test suite green; scanner WIP=0
5. Tag `jquantlib-phase2k-complete`
6. Completion doc + memory + README updated + worktrees torn down

### Auto-trim policy

If A22 (tertiary missing-dep) or A21 (wall-time) fires within a track, controller may scope-trim that track's MF/engine wiring (port the new class but defer the integration), record the trim in completion doc.
