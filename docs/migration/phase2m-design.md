# Phase 2m Design — Fdm-Dependent Engines + AndreasenHuge LocalVol

**Status:** approved 2026-05-02 (autonomous mode — fourth autonomous phase)
**Predecessor:** `jquantlib-phase2l-complete` @ `9dab878` (tests `812/0/0/22`, scanner WIP=0)

## 1. Context

Phase 2l completed Fdm framework features (BiCGStab/GMRES + Bermudan/American/dividend step conditions + 6 schemes). Phase 2m delivers the engines that depend on this framework completeness.

**Note on Phase 2h seed-list correction:** `FdConvertibleBond` was on the seed list but does NOT exist as a Fdm engine in v1.42.1 (only `BinomialConvertibleEngine`, tree-based). Dropping. `FdAndreasenHugeLocalVol` is not a separate engine; it's a vol-surface family in `termstructures/volatility/equityfx/`. Porting that family enables Andreasen-Huge use with the existing `FdBlackScholesVanillaEngine`.

## 2. Goals (in scope)

| Track | Scope | LOC C++ |
|-------|-------|---------|
| A | `FdBlackScholesVanillaEngine` (`pricingengines/vanilla/`) | 447 |
| B | `FdHestonHullWhiteVanillaEngine` (`pricingengines/vanilla/`) — needs cross-product process | 341 |
| C | `FdSabrVanillaEngine` (`pricingengines/vanilla/`) | 207 |
| D | AndreasenHuge LocalVol family (`termstructures/volatility/equityfx/`): `andreasenhugevolatilityinterpl.{hpp,cpp}` (152+633=785) + `andreasenhugevolatilityadapter.{hpp,cpp}` (57+69=126) + `andreasenhugelocalvoladapter.{hpp,cpp}` (57+63=120) | 1031 |

Total: ~2026 LOC C++ → ~2700-3500 Java across ~4-6 commits.

## 3. Worktree Topology

| WT | Branch | Scope |
|----|--------|-------|
| A | `phase-2m-A-fd-black-scholes` | FdBlackScholesVanillaEngine |
| B | `phase-2m-B-fd-heston-hullwhite` | FdHestonHullWhiteVanillaEngine (may surface HestonHullWhiteProcess as A16 prereq) |
| C | `phase-2m-C-fd-sabr` | FdSabrVanillaEngine |
| D | `phase-2m-D-andreasenhuge` | 3 AndreasenHuge files (sequential within track: VolatilityInterpl → VolatilityAdapter → LocalVolAdapter) |

All 4 dispatch in parallel after L0. D is sequential within (3 sub-commits); A/B/C are 1 commit each.

## 4. Decisions

- **P2M-1:** 4 parallel tracks.
- **P2M-2:** Source = QL v1.42.1 C++. Standard ports.
- **P2M-3:** `JQuantMath.{exp,log,sin,cos}` from day one.
- **P2M-4:** Tier per artifact — TIGHT default, LOOSE/A19 acceptable for engine NPVs (Phase 2j precedent).
- **P2M-5:** If Track B surfaces HestonHullWhiteProcess as A16 prereq, port as part of the track (additive).
- **P2M-6:** Drop `FdConvertibleBond` from seed list (does not exist in v1.42.1).
- **P2M-7:** Direct-to-main signed `-s` no Co-authored-by.

## 5. Pause Triggers, Exit Criteria

Carry-forward all prior triggers. New: A23 (engine port reveals deeper-than-expected dependency, e.g. `MakeMcVanillaEngine`-style helper not yet in Java) — handle per A22 pattern (defer or expand within track).

**Exit criteria:** all 4 tracks land + tests passing + tag `jquantlib-phase2m-complete` + completion + memory + README + teardown.

**Outcome forecast:** tests `812 → ~820-825` (+1 per track + integration); scanner WIP=0.
