# Phase 2l Design — Fdm Framework Completeness

**Status:** approved 2026-05-02 (autonomous mode — third autonomous phase)
**Predecessor:** `jquantlib-phase2k-complete` @ `70e5007` (tests `809/0/0/22`, scanner WIP=0)
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

## 1. Context & Motivation

Phase 2h ported the core `Fdm*` finite-difference framework (operators, meshers, solvers, ImplicitEuler/Hundsdorfer/Douglas schemes) but explicitly deferred:
- BiCGStab + GMRES iterative solvers (needed by ImplicitEulerScheme `dampingSteps>0` path)
- Bermudan/American/dividend step-condition classes (vanillaComposite branches throwing LibraryException)
- Schemes beyond Hundsdorfer/Douglas/ImplicitEuler

Phase 2l closes all three. This unblocks several Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, FdBlackScholesVanilla) for future phases.

## 2. Approach

3 parallel tracks, 3 worktrees (precedent: Phase 2j.5/2k pattern).

| WT | Branch | Scope |
|----|--------|-------|
| A | `phase-2l-A-iterative-solvers` | BiCGStab + GMRES (`org.jquantlib.math.matrixutilities`) |
| B | `phase-2l-B-step-conditions` | FdmAmericanStepCondition + FdmBermudanStepCondition + FdmDividendHandler (+ wire vanillaComposite) |
| C | `phase-2l-C-schemes` | 6 schemes sequentially: ExplicitEuler → CrankNicolson → CraigSneyd → ModifiedCraigSneyd → MethodOfLines → TrBDF2 |

**Decision (P2L-1):** 3 parallel tracks; Track C sequential within itself.
**Decision (P2L-2):** Source = QuantLib v1.42.1 C++. Standard ports.
**Decision (P2L-3):** `JQuantMath.{exp,log,sin,cos}` from day one.
**Decision (P2L-4):** Tier per artifact — TIGHT default; LOOSE per A19 acceptable for scheme accuracy.
**Decision (P2L-5):** ImplicitEulerScheme `dampingSteps>0` path wiring deferred to Phase 2l followup mini-phase if A21 fires (it's a wiring task on existing ImplicitEuler, not a port).
**Decision (P2L-6):** Direct-to-main signed `-s` no Co-authored-by.

## 3. Outcome forecast

| Metric | Phase 2k tip | Phase 2l target |
|--------|--------------|-----------------|
| Tests | 809/0/0/22 | ~819-825 (+1 per port + integration) |
| Scanner WIP | 0 | 0 |
| New Java production LOC | — | ~2200-2700 |
| Schemes available | Hundsdorfer + Douglas + ImplicitEuler | 9 schemes (+ 6 new) |
| Step conditions | None for vanillaComposite Bermudan/American/dividend | All 3 operational |
| Iterative solvers | None in math.matrixutilities | BiCGStab + GMRES |

## 4. Pause Triggers

Carry-forward A2/A3/A4/A8/A9/A15/A16/A17/A18/A19/A20/A21/A22 — same patterns as Phase 2j.5/2k.

## 5. Exit Criteria

1. Track A: BiCGStab + GMRES ported + tests passing
2. Track B: 3 step-condition/utility classes ported + vanillaComposite wired + tests passing
3. Track C: 6 schemes ported + tests passing
4. Test suite green; scanner WIP=0
5. Tag `jquantlib-phase2l-complete`
6. Completion doc + memory + README + worktrees torn down
