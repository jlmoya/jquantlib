# Phase 2i.5 Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i.5-plan.md` (commit `a4dcbf0`)
**Design:** `docs/migration/phase2i.5-design.md` (commit `8cf4775`)
**Predecessor:** `jquantlib-phase2i-complete` @ `a4a3b77`
**Phase 2i.5 start tip on main:** `1f2ee97`
**Baseline:** Tests `684/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-A` | `phase-2i5-A-trig-port` | WI-1 cos/sin paired port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-B` | `phase-2i5-B-nccs-rewire` | WI-2 NCCS CDF rewire + EXACT (parallel with A) |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-C` | `phase-2i5-C-trig-tier-flips` | WI-3 GaussLag + GaussLob tier flips (after WI-1 lands) |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A3 (CORE-MATH reference itself wrong): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 (non-cos/sin transcendental): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (tier promotion fails after correct swap-in): **fired in WI-2 NCCS** — Math.log floor (Phase 2j candidate)

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`684/0/0/22`, scanner 0 stubs, tip `a4dcbf0`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 3 worktrees created off main tip `a4dcbf0`; submodules init'd in each

### L1a — WI-1 cos/sin paired port (worktree A) ⚠️ scope-corrected, sub-layered

First dispatch BLOCKED on scope-decision regression: design's "~1500 LOC paired" estimate based on Phase 2i exp's mockingbirdnest C++/MSVC mirror. Canonical CORE-MATH (Inria) `sin.c` + `cos.c` is **~4159 LOC combined** with `unsigned __int128` extended-precision type and ~2000 longs of table data per primitive. Java has no native u128; requires ~500 LOC `dint64_t` emulation layer.

User chose Option 1: full port at corrected scope. Sub-layered into:
- **1.0:** `dint64_t` u128-emulation infrastructure (`73b0a23` + `042468c` followup) — `Dint64.java` 553 LOC; 100 probe cases all bit-exact; 9 ops ported (`fromDouble`, `toDouble`, `copyFrom`, `addAssign`, `mulAssign`, `mul21Assign`, `subnormalize`, `cmpAbs`, `isZero`); aliasing contract locked via tightened Javadoc.
- **1.1:** `SinCosKernel` (`07337e8`) — 1672 LOC; first-compile bit-exact on 2,757 probe cases (1,376 sin + 1,381 cos) including IEEE-754 specials, exact-result inputs, Payne-Hanek stress through 2^50·π, dense [-2π, 2π] @ 0.01, the `sin_accurate` huge-x worst case `0x1.6ac5b262ca1ffp+849`, and 7 hard-rounding exceptions (sin 2 + cos 5; CORE-MATH uses small exception tables triggered when accurate-path 41-ulp confidence band straddles rounding boundary, not a "hard-cases DB" like exp).
- **Insight for future ports:** Python table-extractor (~150 LOC) converted all C tables (T[20], S[256], C[256], PSfast[5], PCfast[5], PS[6], PC[6], SC[256][3]) into Java `static {}` initializers via `Double.longBitsToDouble` — zero hand-transcription error opportunity. Reusable pattern for log/pow.

### L1b — WI-2 NCCS CDF rewire + EXACT attempt (worktree B, parallel with L1a) ✅ A19 partial

- Commit `8f30182`. 3 `Math.exp` call sites in `NonCentralCumulativeChiSquaredDistribution.java` swapped to `JQuantMath.exp`.
- Probe Scenario B (QL NCCS delegates `std::exp` internally; probe has no direct std::exp calls; reference unchanged).
- **A19 fired:** EXACT attempt showed 27-ULP residual (far beyond 1-ULP Math.exp slack). Diagnosis: `Math.log(x2)` at line 86 feeds into `JQuantMath.exp(f2 * Math.log(x2) - x2 - ...)`; Math.log slack dominates and propagates through the Patnaik series.
- Tier kept at TIGHT (max observed diff 2.99e-15, well within `abs 1e-14 + rel 1e-12`).
- **Phase 2j implication:** Math.log is empirically confirmed as the actual NCCS floor. Once `JQuantMath.log` lands, NCCS flips to EXACT with a one-line test change.

### L2 — WI-3 GaussLag + GaussLob tier flips (worktree C, after WI-1 lands)
_(Pending — dispatches after WI-1 lands)_

### L3 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i.5 start (`a4dcbf0`) | 684 | 0 | 0 | 22 | baseline |
| Progress doc init (`1f2ee97`) | 684 | 0 | 0 | 22 | unchanged |
| WI-2 NCCS rewire (`8f30182`) | 684 | 0 | 0 | 22 | A19 partial — TIGHT pinned (Math.log floor); Phase 2j seed |
| WI-1.0 Dint64 (`73b0a23`) | 685 | 0 | 0 | 22 | +1 Dint64Test (90 cases first commit) |
| WI-1.0 followup (`042468c`) | 685 | 0 | 0 | 22 | unchanged; +10 probe cases (mul21 parity + add 128-bit overflow) |
| WI-1.1 SinCosKernel (`07337e8`) | 687 | 0 | 0 | 22 | +2 EXACT tests (cos 1,381 cases + sin 1,376 cases) |

## Pivots and major findings

- **2026-04-28 — WI-1 scope correction.** Design P2I5-2 estimate of ~1500 LOC for paired cos/sin was based on Phase 2i exp's mockingbirdnest C++/MSVC mirror. Canonical CORE-MATH (Inria) cos/sin source is ~4159 LOC combined, uses `unsigned __int128` (Java needs ~500 LOC u128-emulation layer), and has ~2000 longs of table data per primitive. User chose Option 1: full port at corrected scope, sub-layered (1.0 dint64_t infrastructure + 1.1 SinCosKernel). Multi-session expected. **Bonus benefit:** dint64_t infrastructure becomes reusable for future log/pow/etc. ports.
- **2026-04-28 — WI-2 A19: Math.log is the NCCS floor.** Empirically confirmed via 27-ULP residual after Math.exp → JQuantMath.exp swap. Phase 2j highest-priority candidate: CORE-MATH log port. Once available, NCCS flips to EXACT trivially.
