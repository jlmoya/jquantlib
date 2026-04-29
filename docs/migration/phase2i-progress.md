# Phase 2i Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2i-plan.md` (commit `4dcbe8f`)
**Design:** `docs/migration/phase2i-design.md` (commit `ad39ee9`)
**Predecessor:** `jquantlib-phase2h-complete` @ `f0256c8`
**Phase 2i start tip on main:** `14fbd49`
**Baseline:** Tests `677/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i-A` | `phase-2i-A-transcendental-lib` | WI-1 transcendental port (4 sub-layers, sequential) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i-B` | `phase-2i-B-call-site-integration` | WI-2 — dispatches AFTER WI-1 lands |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i-C` | `phase-2i-C-tier-promotion-sweep` | WI-3 — dispatches AFTER WI-2 lands |

## Pause-trigger status

- A2 (WI-1 EXACT unreachable): not fired
- A4 (unplanned new class outside `org.jquantlib.math.transcendental`): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12/A14 inactive
- A9 worktree-merge-conflict: not fired
- A13 carried (re-arms for non-transcendental ULP source): not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope, e.g. cosh/sinh): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 NEW (NaN payload divergence): not fired
- A19 NEW (WI-2 promotion fails after correct swap-in): not fired

## Pivots and major findings

- **2026-04-28 — msun ≠ libc++ pivot.** WI-1.1 first dispatch surfaced that FreeBSD msun `e_exp.c` has the same 1-ULP slack as JVM `Math.exp`. Originally believed Apple libm was correctly-rounded, so user chose **Option D**: pivot to CORE-MATH correctly-rounded `exp` only; defer log/sin/cos/pow; drop WI-2 B-2/B-3. Design addendum at commit `9739bc7`. Memory: `project_phase2i_correctly_rounded_pivot.md`.
- **2026-04-28 — A3: Apple libm is NOT always correctly-rounded.** Discovered during DB-coverage testing of the CORE-MATH port: 8 of 51 hard-case DB entries had `std::exp` (Apple libm on macOS arm64) returning 1-ULP-off values vs. the mathematically correct result (verified via 300-bit mpmath). Apple libm is *almost* correctly-rounded but not provably so. **Resolution:** probe oracle switched from `std::exp` to CORE-MATH `cr_exp` directly (commit `a61b920`). Java port is bit-exactly correctly-rounded across all 508 probe cases. Future transcendental ports must use CORE-MATH `cr_*` functions as oracles, not platform libm.

## Test count tracking

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`677/0/0/22`, scanner 0 stubs, tip `4dcbe8f`, submodule pin `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
- L0.2 3 worktrees created off main tip `4dcbe8f`; submodules init'd in each

### L1 — WI-1 sequential (4 sub-layer commits + 1 prep commit on main)

#### Task 1.0 — MathTestSupport bit-pattern helper (lands on main first) ✅

- Commit `2ab7ecf` on main — adds `MathTestSupport.java` (assertBitsEqual w/ NaN-payload canonicalisation, parseHexBits) and `MathTestSupportTest.java` (4 self-tests). Tests `677 → 681`. Spec-compliant; code-review APPROVED (0 findings).

#### Sub-layer 1.1 — exp ✅ (Option D, CORE-MATH oracle)

- `d1c3eda`: CORE-MATH correctly-rounded `exp` ported (`ExpKernel.java` ~430 LOC + `JQuantMath.java` facade + probe + reference + EXACT test). 681 → 682.
- `807ad6c`: facade Javadoc fix — attribution to CORE-MATH/Sibidanov/MIT (was stale msun reference).
- `6a6be46`: test diagnostics — `MathTestSupport.bitsEqual` + collect-all-failures in `JQuantMathExpTest`. 682 → 684.
- `d94aea7`: hard-cases DB probe coverage — 43/51 DB entries added (8 omitted as workaround for then-unknown Apple-libm bug). Surfaced A3 trigger.
- `a61b920`: **A3 resolved**. Apple libm shown not-always-correctly-rounded at 8 DB hard cases (verified via 300-bit mpmath). Switched probe oracle from `std::exp` to CORE-MATH `cr_exp` directly. All 51/51 DB entries restored and pass EXACT.
- Final: tests `684/0/0/22`; scanner `0`. Java port is bit-exactly correctly-rounded across the 508-case probe set.

#### Sub-layer 1.2/1.3/1.4 — log/sin/cos/pow ❌ DEFERRED
_(Out of scope per Option D pivot. Each becomes a separate Phase 2i.5 / 2j decision after WI-2 B-1 outcome.)_

### L2 — WI-2 (Option D: B-1 only)

#### B-1 FdHullWhiteSwaptionEngine LOOSE → within(3e-12) ⚠️ A19 partial

- Commit `305ce24`. 4 compounded `Math.exp` call sites swapped to `JQuantMath.exp` on the FdHullWhite hot path: `OneFactorAffineModel.discountBond`, `HullWhite.A`, `Vasicek.B`, `HullWhite.FittingParameter.Impl.value`.
- **A19 fired (partial):** swap closed essentially zero of the residual gap (pre: ~2.0e-12; post: 1.9935e-12). The Phase 2h thesis that `Math.exp` 1-ULP slack dominates the FdHullWhite floor was **wrong**. Real structural source is likely Douglas ADI scheme rounding or `FdmAffineModelTermStructure` discount projection rounding chain.
- **Tier improvement:** LOOSE (abs+rel 1e-8) → `Tolerance.within(npv, cpp, 3e-12)` — ~3000× tighter than LOOSE but not full TIGHT (`abs 1e-14 + rel 1e-12`, ceiling ~1.96e-12 for this NPV magnitude).
- Implication: future `JQuantMath.log/sin/cos/pow` ports won't flip this test either. Phase 2j seed candidate: investigate Douglas ADI / FdmAffineModelTermStructure floor.

#### B-2/B-3 ❌ DEFERRED
_(Out of scope per Option D pivot — depend on log/sin/cos paths.)_

### L3 — WI-3 audit + tier-flip sweep
_(Pending — dispatches after WI-2 lands)_

### L4 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2i start (`14fbd49`) | 677 | 0 | 0 | 22 | baseline (post-progress-doc) |
| Task 1.0 land (`2ab7ecf`) | 681 | 0 | 0 | 22 | +4 MathTestSupportTest |
| WI-1.1 CORE-MATH `exp` (`d1c3eda`) | 682 | 0 | 0 | 22 | +1 JQuantMathExpTest (459 cases) |
| WI-1.1 test diagnostics (`6a6be46`) | 684 | 0 | 0 | 22 | +2 MathTestSupport.bitsEqual tests |
| WI-1.1 final (`a61b920`) | 684 | 0 | 0 | 22 | unchanged; probe oracle = CORE-MATH cr_exp; 508 cases incl 51/51 DB |

| WI-1 land tip | `a61b920` | tests `684/0/0/22`, scanner `0`, EXACT-tier `JQuantMath.exp` correctly-rounded across 508 probe cases |
|---------------|-----------|---|
