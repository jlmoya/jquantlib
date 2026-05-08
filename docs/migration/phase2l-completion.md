# Phase 2l Completion — Fdm Framework Completeness

**Status:** complete 2026-05-02 (autonomous mode — third autonomous phase)
**Tag:** `jquantlib-phase2l-complete` @ `<FILL_AT_TAG>`
**Predecessor:** `jquantlib-phase2k-complete` @ `70e5007`
**Plan + Design:** `docs/migration/phase2l-{design,plan}.md` (commit `91ef33c`)

## Final state

| Metric | Phase 2k tip | Phase 2l tip | Δ |
|--------|--------------|--------------|----|
| Tests | 809/0/0/22 | 812/0/0/22 | +3 |
| Scanner WIP | 0 | 0 | unchanged |
| Schemes | 3 (Hundsdorfer + Douglas + ImplicitEuler) | **9** (+ ExplicitEuler + CrankNicolson + CraigSneyd + ModifiedCraigSneyd + MethodOfLines + TrBDF2) | ✅ |
| Step conditions for vanillaComposite | LibraryException for Bermudan/American/dividend | All 3 operational | ✅ |
| Iterative solvers | None | BiCGStab + GMRES | ✅ |
| New packages | — | `math.ode` (added by C.5) | new |

## What landed (9 commits across 3 parallel worktrees)

### Track A — BiCGStab + GMRES iterative solvers ✅

| Commit | Description |
|--------|-------------|
| `0ae77d7` | `BiCGStab.java` (~221 LOC, BiCGStab + Jacobi preconditioner) + `GMRES.java` (~379 LOC, Arnoldi/Modified Gram-Schmidt with Givens rotations + restart) + 33-case probe TIGHT. Pure linear algebra; no transcendentals. |

### Track B — Step conditions + FdmDividendHandler + vanillaComposite wiring ✅

| Commit | Description |
|--------|-------------|
| `bd35f2e` (merged as `949e7fa`) | `FdmAmericanStepCondition.java` (~97 LOC) + `FdmBermudanStepCondition.java` (~107 LOC) + `FdmDividendHandler.java` (~187 LOC, uses `JQuantMath.exp`) + `Uniform1dMesher.java` (62 LOC, prereq for tests). Replaced 3 `LibraryException` throws in vanillaComposite with proper delegation. Smoother-convergence double-stopping-time pattern (t + 1e-5) matching C++. 17 probe cases TIGHT. |

### Track C — 6 schemes (sequential within worktree) ✅

| Commit | Description |
|--------|-------------|
| `908a5d0` | **C.1:** ExplicitEulerScheme (~95 LOC, TIGHT) — baseline |
| `ef291e9` | **C.2:** CrankNicolsonScheme (~99 LOC, TIGHT) — implicit-explicit blend |
| `19471ba` | **C.3:** CraigSneydScheme (~113 LOC, TIGHT) — ADI |
| `ff288d8` | **C.4:** ModifiedCraigSneydScheme (~122 LOC, TIGHT) — refined ADI |
| `5675560` | **C.5:** MethodOfLinesScheme (~130 LOC) + AdaptiveRungeKutta (~217 LOC, new `math.ode` package). Tier: 1e-7 rel (inline-justified — adaptive ODE step-selection diverges between `std::pow` and `Math.pow` at 1-ULP level across 20-step trajectories with eps=1e-6; tighter eps=1e-8 cases pass at 1e-8). |
| `ffbc2b4` | **C.6:** TrBDF2Scheme (~172 LOC, TIGHT). Multi-D path **wired to BiCGStab/GMRES from Track A** (Track A landed before C.6 dispatched, so no deferral needed). |

24-case probe across all 6 schemes; 1D heat equation oracle (backward rollback of `u_t = u_xx` on `[0,π]`, exact solution `sin(x)·exp(-t)`).

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A19 (in spirit)** | C.5 MethodOfLinesScheme | 1e-7 rel inline-justified for `std::pow`-vs-`Math.pow` 1-ULP divergence in adaptive ODE step-selection. Anticipated per Phase 2j-pre B3 "Math.pow stays at the GsrProcessCore site" (this is a second site; new). Future Phase 2k+ candidate to extend `JQuantMath.pow` would close. |

A2/A3/A4/A6/A8/A9/A13/A15/A16/A17/A18/A20/A21/A22 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2L-7** | Track C.5 MethodOfLinesScheme accepts 1e-7 rel for `std::pow`-vs-`Math.pow` ODE step-selection | Documented inline; anticipated per Phase 2j-pre B3 precedent. Tighter eps=1e-8 paths pass at 1e-8. |
| **P2L-8** | Track C.5 introduces new package `math.ode` with `AdaptiveRungeKutta` | C.5 needs it; package-aligned with QuantLib's structure (separate from math.integrals). Future Fdm work may benefit. |
| **P2L-9** | Track B includes `Uniform1dMesher` bonus | Required by step-condition tests (mesher prereq). Pre-existing in QuantLib v1.42.1; not a scope expansion, just a logical bundle for the test infrastructure. |
| **P2L-10** | TrBDF2 multi-D path wired to BiCGStab/GMRES same-phase | Track A landed first; C.6 incorporated rather than deferring. Phase 2l completes the full Fdm scheme story. |

## Phase 2m+ seed list

### Now-unblocked Fdm-dependent engines (Phase 2h carry-forward)

1. **FdHestonHullWhite** — uses Fdm framework + needs both Heston + Hull-White, now possible with full scheme + step-condition coverage
2. **FdSabrVanilla** — needs Sabr + Fdm vanilla composite (Bermudan/American/dividend now operational)
3. **FdConvertibleBond** — needs Bermudan/dividend conditions (now available)
4. **FdAndreasenHugeLocalVol** — needs scheme + interpolation infrastructure (now complete)
5. **FdBlackScholesVanilla** — uses standard Fdm with Bermudan/American/dividend (now available)

### Other carry-forwards

6. **`SABRInterpolation` shifted-strike support** — Phase 2k Track A Scenario C unblocker
7. **`U128.java` shared util extraction** — refactor candidate
8. **`JQuantMath.pow`** — empirical leverage rising (Phase 2l C.5 MethodOfLines, Phase 2j GsrProcessCore — now 2 sites). Worth a small port phase.
9. **`JQuantMath.lgamma`** — still no path; carry-forward
10. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19)

### Phase 3+ subsystem ports

11. **`experimental/`** — large surface
12. **`models/marketmodels/`** — Libor Market Model family
13. **`termstructures/credit/`** — credit term structures + CDS + CDX
14. **`inflation/`** — inflation indexes + curves + linkers + caps/floors
15. **C++ test-suite Java equivalents** — every C++ test needs a Java equivalent
16. **Calibration via market data feeds**

## Out-of-scope (explicit, deferred)

- All Phase 2m+ items above
- `JQuantMath.pow` empirical-leverage threshold reached at 2 sites; consider porting in Phase 2m if a 3rd site surfaces
- BroadieKaya retry — needs pow + lgamma
- NCCS EXACT — needs lgamma
