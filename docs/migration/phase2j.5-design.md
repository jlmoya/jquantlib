# Phase 2j.5 Design — Full Gaussian1D Family Completion

**Status:** approved 2026-05-02 (autonomous mode per user directive)
**Predecessor:** `jquantlib-phase2j-complete` @ `8808985` (tests `792/0/0/22`, scanner WIP=0)
**Working directory:** `/Users/josemoya/eclipse-workspace/jquantlib`
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

---

## 1. Context & Motivation

Phase 2j shipped the Gaussian1D family **partially** per P2J-10 trim (3 of 5 engines + 3 of 4 MF prereqs landed; 4× A16 fired). Phase 2j.5 closes the loop in one phase — three independent tracks dispatched in parallel.

### Goals (in scope)

- **Track A — Nonstandard engine track:** NonstandardSwap (instrument) + NonstandardSwaption (instrument) + Gaussian1dNonstandardSwaptionEngine
- **Track B — FloatFloat engine track:** FloatFloatSwap (instrument) + FloatFloatSwaption (instrument) + Gaussian1dFloatFloatSwaptionEngine
- **Track C — MarkovFunctional track:** GaussHermiteIntegration family (GaussianOrthogonalPolynomial + GaussHermitePolynomial + GaussianQuadrature Golub-Welsch + GaussHermiteIntegration) + AtmSmileSection + MarkovFunctional

### Outcome forecast

| Metric | Phase 2j tip | Phase 2j.5 target | ceiling |
|--------|--------------|--------------------|---------|
| Tests | 792/0/0/22 | ~802-810/0/0/22 (+10 per track) | ~825 |
| Scanner WIP | 0 | 0 | 0 |
| New Java production LOC | — | ~7000-8500 | ~10000 |
| Engines landed (Gaussian1D family) | 3 of 5 | 5 of 5 | 5 of 5 |
| MarkovFunctional | not landed | ✅ landed | ✅ landed |

### Non-goals

`JQuantMath.lgamma` / `pow`, U128.java refactor, BroadieKaya retry, Douglas ADI, other Fdm-dependent engines, Phase 2h Fdm completeness items, Phase 3+ subsystems.

---

## 2. Approach

| # | Approach | Verdict |
|---|----------|---------|
| 1 | **3 parallel tracks (A=Nonstandard, B=FloatFloat, C=MF chain), 3 worktrees, sub-layered within each track** *(chosen)* | Maximum parallelism; tracks have ~zero cross-dependency at the file level |
| 2 | Sequential track-by-track | Rejected — wall-time triple |
| 3 | One mega-worktree with all changes | Rejected — A17 cap, unmanageable |

**Decision (P2J5-1):** Approach 1.
**Decision (P2J5-2):** Source-of-truth = QuantLib v1.42.1 C++ at pin SHA. Standard engine ports.
**Decision (P2J5-3):** Use `JQuantMath.{exp,log,sin,cos}` from day one. `Math.pow` stays at any pre-existing 1-site uses (Phase 2j-pre B3 precedent).
**Decision (P2J5-4):** Probe-driven with TIGHT default; LOOSE per A19 acceptable for engine NPVs (Phase 2j precedent).
**Decision (P2J5-5):** Direct-to-main signed `-s` no Co-authored-by. Standing rule.

---

## 3. Worktree Topology

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-A` | `phase-2j5-A-nonstandard` | NonstandardSwap → NonstandardSwaption → Gaussian1dNonstandardSwaptionEngine (3 sub-commits) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-B` | `phase-2j5-B-floatfloat` | FloatFloatSwap → FloatFloatSwaption → Gaussian1dFloatFloatSwaptionEngine (3 sub-commits) |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2j5-C` | `phase-2j5-C-markovfunctional` | GaussHermite family → AtmSmileSection → MarkovFunctional (3-4 sub-commits) |

### Layer ordering

- **L0** — Pre-flight + 3 worktrees + progress doc (1 commit on main).
- **L1** — Tracks A, B, C dispatch in parallel after L0. Within each track, sub-commits sequential (instruments before engine; prereqs before MF).
- **L2** — Completion doc + tag `jquantlib-phase2j.5-complete` + memory + worktree teardown.

---

## 4. Tolerance, Probes & Test Discipline

Same as Phase 2j: EXACT/TIGHT/LOOSE tiers, probe-before-port, `MathTestSupport.bitsEqual` for transcendental EXACT, `Tolerance.tight/loose` for engine NPVs. ONE @Test method with collect-all-failures (per Phase 2i.5 precedent — avoid the WI-1.2/WI-4.0c style of 95-tests-per-probe).

### Probes (~10 new)

- `nonstandard_swap_probe.cpp`, `nonstandard_swaption_probe.cpp`, `gaussian1d_nonstandard_swaption_engine_probe.cpp`
- `floatfloat_swap_probe.cpp`, `floatfloat_swaption_probe.cpp`, `gaussian1d_float_float_swaption_engine_probe.cpp`
- `gauss_hermite_integration_probe.cpp`, `atm_smile_section_probe.cpp`, `markov_functional_probe.cpp`

### Test count expectations

`792 → ~802-810` (+10, one per port). Ceiling `~825` if calibration/integration probes split into multiple tests.

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers (carry-forward + new)

Same as Phase 2j with one new addition:

| ID | Condition | Action |
|----|-----------|--------|
| A2/A3/A8/A9/A15/A16/A17/A18/A19/A20/A21 | Carry-forward | Per Phase 2j precedent |
| **A22** *(new)* | A track surfaces tertiary missing dependencies (e.g. NonstandardSwap needs another instrument missing from Java) | Track gets paused; controller decides scope-trim or expansion within autonomous discretion |

### Decision log

Carries P2J-1..P2J-16 from Phase 2j, plus P2J5-1..P2J5-5 above.

### Exit criteria

Phase 2j.5 complete when **all** hold:

1. Track A landed: NonstandardSwap, NonstandardSwaption, Gaussian1dNonstandardSwaptionEngine + tests
2. Track B landed: FloatFloatSwap, FloatFloatSwaption, Gaussian1dFloatFloatSwaptionEngine + tests
3. Track C landed: GaussHermite family, AtmSmileSection, MarkovFunctional + tests
4. Test suite green; scanner WIP=0
5. Tag `jquantlib-phase2j.5-complete`
6. Completion doc + memory updated + worktrees torn down

### Auto-trim policy (controller discretion)

If A21 (wall-time) or repeated A16 (missing deps) fires within a track, controller may scope-trim that track and defer remainder to a Phase 2j.6 mini-phase. Trim order within Phase 2j.5: MarkovFunctional first (largest, most deps), then FloatFloat (deeper integration), then Nonstandard (smallest). Same priority as P2J-10.
