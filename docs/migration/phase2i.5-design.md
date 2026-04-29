# Phase 2i.5 Design — CORE-MATH `cos`/`sin` Port + Audit Tier Discharge

**Status:** approved 2026-04-28
**Predecessor:** `jquantlib-phase2i-complete` @ `a4a3b77` (tests `684/0/0/22`, scanner WIP=0)
**Working directory:** `/Users/josemoya/eclipse-workspace/jquantlib`
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`
**Algorithm source-of-truth:** CORE-MATH `src/binary64/sin/sin.c` and `cos/cos.c` (BSD/MIT)

---

## 1. Context & Motivation

Phase 2i (Option D pivot) ported CORE-MATH correctly-rounded `exp` and integrated it at one site (FdHullWhite). The audit at `docs/migration/phase2i-tier-audit.md` identified four transcendental-floored tests — three of which can be flipped *now* with the addition of `cos`/`sin` and one which only needs wiring.

Phase 2i.5 is a **small bridge phase** between Phase 2i (foundational `exp`) and Phase 2j (Gaussian1D family). Its purpose:

1. **Prepare the toolkit** for Phase 2j by adding correctly-rounded `cos`/`sin` to `JQuantMath`. Gaussian1D engines almost certainly use both heavily (Gaussian quadrature, vol surface).
2. **Discharge the audit's three immediately-flippable transcendental candidates** — NCCS CDF, GaussLaguerre cos-integrand, GaussLobatto trig integrands — so Phase 2j starts from a clean tier-promotion baseline.

### Goals (in scope)

- **WI-1:** Port CORE-MATH correctly-rounded `cos` and `sin` (paired) to `org.jquantlib.math.transcendental.JQuantMath.cos` and `.sin`. Bit-exact agreement with CORE-MATH `cr_cos`/`cr_sin`.
- **WI-2:** Rewire NCCS CDF (`NonCentralChiSquaredDistribution`) to use `JQuantMath.exp`. Regenerate NCCS probe references against CORE-MATH `cr_exp` (per Phase 2i A3 finding). Attempt tier promotion TIGHT → EXACT; if A19 fires, document and stay TIGHT.
- **WI-3:** Rewire GaussLaguerre cos-integrand and GaussLobatto trig integrands to `JQuantMath.cos`/`sin`. Flip tiers per audit.

### Non-goals (explicit)

- BroadieKaya asset-leg — audit predicts ~A19 likely; not worth speculative effort here.
- `JQuantMath.log` and `.pow` — neither cited in the audit. Defer to Phase 2j or later.
- Codebase-wide `Math.* → JQuantMath.*` swap — surgical only.
- Non-transcendental floor investigations (Douglas ADI, FdmAffineModelTermStructure) — Phase 2j scope.
- Gaussian1D family port itself — Phase 2j.

### Outcome forecast

| Metric | Phase 2i tip | Phase 2i.5 target | Phase 2i.5 ceiling |
|--------|--------------|--------------------|---------------------|
| Tests | 684/0/0/22 | 686/0/0/22 (+2 EXACT cos+sin tests) | 686 |
| Scanner WIP | 0 | 0 | 0 |
| `JQuantMath` primitives | exp | exp + cos + sin | same |
| NCCS CDF tier | TIGHT (vs `std::exp` oracle) | EXACT (vs CORE-MATH oracle) OR TIGHT-with-A19 | EXACT |
| GaussLaguerre cos-integrand tier | TIGHT/per-test | TIGHT (or tighter) | TIGHT |
| GaussLobatto trig integrands tier | TIGHT/per-test | TIGHT (or tighter) | TIGHT |

---

## 2. Approach Comparison

| Approach | Description | Verdict |
|----------|-------------|---------|
| **A. Paired CORE-MATH `cos`+`sin` port** *(chosen)* | Single `SinCosKernel.java` housing both primitives; share Payne-Hanek argument reduction (~600 LOC of the total). Public methods on `JQuantMath` for `cos(double)` and `sin(double)`. | Standard for paired trig; matches Phase 2i.exp shape. Smallest LOC for the two primitives combined. |
| **B. Two separate kernels** (`CosKernel`, `SinKernel`) | Independent per primitive. Each duplicates Payne-Hanek (~600 LOC each). | Rejected — duplicates ~600 LOC of the trickiest code. |
| **C. Defer `sin` (cos only)** | Only port `cos` first; add `sin` as a separate sub-phase later. | Rejected — Payne-Hanek is the bulk of either port; landing one without the other doesn't save real work, and audit candidates (GaussLobatto) need both. |

**Decision (P2I5-1):** Approach A.

**Decision (P2I5-2):** Algorithm source = CORE-MATH `src/binary64/sin/sin.c` and `src/binary64/cos/cos.c`. Repository: `https://gitlab.inria.fr/core-math/core-math` (mirror at `https://raw.githubusercontent.com/mockingbirdnest/core-math/master/...` per Phase 2i finding).

**Decision (P2I5-3):** Probe oracle = CORE-MATH `cr_sin`/`cr_cos` directly (NOT `std::sin`/`std::cos`), per Phase 2i A3 lesson. Probes `#include "coremath/sin.c"` and `#include "coremath/cos.c"` and call the `cr_*` functions for ground truth.

**Decision (P2I5-4):** Probe shape mirrors Phase 2i `exp_probe.cpp` — IEEE-754 special cases + Payne-Hanek stress (π·2^k for k=10..50) + dense [-2π, 2π] + per-primitive corner cases (e.g. `cos(π/2)` near-zero values, `sin(π)` near-zero values). Same JSON schema with `y_bits` raw long form.

---

## 3. Worktree Topology & Layer Ordering

Three worktrees. Maximize parallelism by running WI-1 (cos/sin port) and WI-2 (NCCS rewire) concurrently — they touch disjoint files. WI-3 depends on WI-1 landing.

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-A` | `phase-2i5-A-trig-port` | WI-1 cos/sin paired port (single commit) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-B` | `phase-2i5-B-nccs-rewire` | WI-2 NCCS CDF rewire + EXACT tier attempt (single commit, possibly +1 doc commit if A19) |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i5-C` | `phase-2i5-C-trig-tier-flips` | WI-3 GaussLaguerre + GaussLobatto tier flips — dispatches AFTER WI-1 lands |

### Layer ordering

- **L0** — Pre-flight + 3 worktrees + progress doc (1 commit on main).
- **L1** — Parallel:
  - **L1a (WI-1):** cos/sin paired port in worktree A (1 commit; ~1500 LOC algorithm + ~800 LOC probe).
  - **L1b (WI-2):** NCCS CDF rewire + probe regen + EXACT attempt in worktree B (1 commit, +1 doc commit if A19 fires).
- **L2 (WI-3):** GaussLaguerre + GaussLobatto rewire + tier flips in worktree C, after L1a lands (rebase first). 1-2 commits.
- **L3** — Completion doc + tag `jquantlib-phase2i.5-complete` + memory + worktree teardown.

### Why three worktrees, not two

Could fold WI-3 into worktree A. Three worktrees keeps the commit message scopes clean: WI-1 is `infra(math.transcendental)`, WI-3 is `align(math.integrals)` — different package families, different commits, different reviewer foci. Marginal extra setup cost is small.

### Sub-task ordering inside WI-1 (single commit)

Unlike Phase 2i WI-1, **no sub-layering**. Reasons:
- `cos` and `sin` *cannot* land separately because they share Payne-Hanek argument reduction. Landing one without the other means landing dead code.
- The CORE-MATH `cr_sin.c` and `cr_cos.c` together are ~1500 LOC paired. Phase 2i's exp was ~430 LOC alone; we don't need finer granularity here.

The implementer transcribes both primitives + their shared reduction in a single commit. Two probe sources (`sin_probe.cpp`, `cos_probe.cpp`) — separate for clarity even though they share input grids.

### Sub-task ordering inside WI-2 (NCCS)

1. Identify all `Math.exp` call sites in the NCCS distribution chain (CDF + PDF + InverseCDF if exists).
2. Surgical swap to `JQuantMath.exp`.
3. Regenerate NCCS probe references — CRITICAL: probe must be modified to use `cr_exp` directly via `#include "coremath/exp.c"` (mirror Phase 2i WI-1.1 final probe pattern). Existing NCCS probe currently uses `std::exp` — that's the change.
4. Run NCCS test → expect TIGHT to still pass against new oracle.
5. Attempt EXACT-tier flip via `MathTestSupport.assertBitsEqual`.
6. If A19 fires (some accumulated rounding still dominates), back off to TIGHT-with-doc.

### Sub-task ordering inside WI-3 (after WI-1 lands)

1. Rebase worktree C on main tip post-WI-1.
2. Identify `Math.cos`/`Math.sin` call sites in `GaussLaguerreIntegration` and `GaussLobattoIntegral` test code (the integrands are typically lambda functions in tests, not in production code).
3. Swap to `JQuantMath.cos`/`sin` in those integrand definitions.
4. Run tests, observe new tier achievable.
5. Flip tier annotations.

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers

| Tier | Threshold | Phase 2i.5 usage |
|------|-----------|--------------------|
| **EXACT** | bit-identical (`Double.doubleToRawLongBits` match, NaN-payload-canonicalised) | WI-1 unit tests for `JQuantMath.cos` and `.sin` against CORE-MATH `cr_cos`/`cr_sin`. WI-2 NCCS CDF target (ambitious path). |
| **TIGHT** | `abs 1e-14 + rel 1e-12` | WI-2 NCCS fallback if A19 fires. WI-3 target for GaussLaguerre + GaussLobatto if EXACT proves out of reach. |
| **LOOSE** | `abs 1e-8 + rel 1e-8` | Not expected; if WI-3 needs it that's an A19 outcome — document. |
| **per-test exception** | numeric value + one-line justification | Same as before; document inline. |

### Probes

#### WI-1: new probes under `migration-harness/cpp/probes/transcendental/`

**`cos_probe.cpp`** — ~800 inputs:
- ±0, ±NaN, ±∞ (→ NaN)
- ±π/6, ±π/4, ±π/3, ±π/2, ±π, ±2π, ±3π/2 (special values where `cos` evaluates to exactly 0, ±1, or ±√2/2)
- Payne-Hanek stress: ±π·2^k for k=10..50 (40 cases each direction = 80)
- Dense [-2π, 2π] grid at 0.01 spacing (~1257 cases)
- Tiny inputs near 0: ±2^-54, ±2^-30, ±0x1p-52 (where `cos(x) → 1.0 - x²/2`)
- Hard-rounding cases from CORE-MATH's hard-cases DB (transcribe from `cos.c` source, similar to Phase 2i exp DB pattern)

**`sin_probe.cpp`** — ~800 inputs (similar shape):
- ±0, ±NaN, ±∞
- ±π/6, ±π/4, ±π/3, ±π/2, ±π, ±2π (special values)
- Payne-Hanek stress
- Dense [-2π, 2π] @ 0.01
- Tiny inputs (where `sin(x) → x` to first order)
- Hard-rounding DB cases from CORE-MATH `sin.c`

**Probe oracle:** each probe `#include "coremath/sin.c"` (or `cos.c`) and the necessary helper headers, then calls `cr_sin(x)` / `cr_cos(x)` for ground truth — NOT `std::sin`/`std::cos`. Mirror Phase 2i WI-1.1 final pattern (commit `a61b920`).

JSON schema same as Phase 2i:
```json
{"name": "...", "inputs": {"x": ...}, "expected": {"y_bits": "0x..."}}
```

#### WI-2: NCCS probe regeneration

Existing NCCS probe (`migration-harness/cpp/probes/math/distributions/non_central_chi_squared.cpp` or similar) currently uses `std::exp`. Modify it to:
- `#include "../../transcendental/coremath/exp.c"` (relative path back to the CORE-MATH source already vendored in Phase 2i)
- Replace each `std::exp(...)` call with `cr_exp(...)`

Regenerate the JSON via `migration-harness/generate-references.sh`. Expected: most case bit patterns unchanged (`std::exp` and `cr_exp` agree everywhere except the ~50 hard cases per 2^64 — and NCCS probe inputs likely don't hit those). Any case where the JSON does change is evidence Apple libm was rounding wrong for that NCCS input.

### Test discipline (carry-forward + new)

Carry-over from Phase 1/2a-2i (binding):
1. **Probe-before-port** — Java tests load via `ReferenceReader.load`; never invent expected values inline.
2. **No backfilling green via tolerance** — if `JQuantMath.cos(x)` differs from probe at one input, fix the algorithm.
3. **One sub-task = one commit** — WI-1 (cos+sin together), WI-2 (NCCS rewire), WI-3 (Lag+Lob) each land as ONE commit.
4. **Inline justification** required for any LOOSE / per-test exception.
5. **Bit-pattern comparison** for transcendental EXACT — `MathTestSupport.assertBitsEqual` (or `bitsEqual` non-throwing variant for collect-all-failures pattern).

New for Phase 2i.5:
6. **Reuse Phase 2i WI-1.1 patterns:** the test class for cos and sin should mirror `JQuantMathExpTest` shape — collect all mismatches and report at end (not first-failure throw). Already provided by `MathTestSupport.bitsEqual`.
7. **DB coverage requirement:** if CORE-MATH's `cos.c` and `sin.c` have hard-cases databases (they do, similar to exp's 51-entry DB), the probe must include all DB inputs explicitly. Phase 2i WI-1.1 missed this initially and had to backfill — don't repeat.

### Test count expectations

| Event | Δ tests | Notes |
|-------|---------|-------|
| WI-1 cos + sin port | +2 EXACT | one parameterized test per primitive (each iterates ~800 cases) |
| WI-2 NCCS rewire | 0 | tier flip on existing NCCS test |
| WI-3 GaussLag + GaussLob flips | 0 | tier flips on existing tests |

**Aggregate target:** `684 → 686`. Scanner WIP unchanged at 0.

### Risk: hard-cases DB transcription

Phase 2i WI-1.1 hit two issues with the exp DB: first the probe didn't exercise it (caught in code review), then 8/51 entries failed because Apple libm was the wrong oracle (A3). The cos/sin DB ports are likely to surface similar issues. **Pre-emptive mitigation:**
- Probe targets DB entries from day one (don't rely on dense grid coverage to randomly hit them).
- Probe oracle is `cr_cos`/`cr_sin` directly from the start — no `std::cos`/`std::sin`.

If a DB entry mismatches: same diagnosis sequence as Phase 2i WI-1.1 (mpmath cross-check; if Java port is correctly-rounded and oracle disagrees, oracle is the bug).

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers

| ID | Condition | Phase 2i.5 interpretation | Action |
|----|-----------|----------------------------|--------|
| A2 | Tolerance looser than `1e-8` needed | If WI-1 cos/sin EXACT fails for any probe input | **Pause** — port wrong, fix algorithm |
| A3 | Cross-validation suggests reference itself wrong | If `cr_cos`/`cr_sin` disagrees with mpmath at high precision (extremely unlikely; CORE-MATH is provably correctly-rounded) | **Pause** — surface to user |
| A4 | New class outside planned scope | Disabled for `org.jquantlib.math.transcendental` (planned new helper classes for cos/sin); armed for *other* unplanned packages | n/a expected |
| A6 | End-of-layer ack | **Disabled** per memory | Run all layers end-to-end |
| A8 | Test suite red unrelated | If WI-2 NCCS rewire breaks an unrelated test | **Pause** — investigate before continuing |
| A9 | Worktree merge conflict | A & B run parallel and could conflict on shared files | **Coordinate** — controller resolves |
| A13 | JVM transcendental ULP slack | Re-arms only for *non-cos/sin* primitives we encounter accidentally | n/a expected |
| A15 | Previously-hidden bug surface | If cos/sin port exposes some other latent issue | **Pause** — bundle as `align(...)` commit |
| A16 | Missing dependency outside planned scope | E.g. WI-3 needs `JQuantMath.tan` (not planned) | **Pause** — decide scope-add vs phase-defer |
| A17 | >2 unplanned `align(...)` commits | Cumulative across A/B/C | **Pause** — re-evaluate scope |
| A18 | NaN payload divergence | Same mitigation as Phase 2i (canonicalisation in `MathTestSupport`) | n/a expected |
| A19 | Tier promotion fails after correct swap-in | NCCS EXACT attempt is the obvious A19 target. Likely fires for one of the two GaussLag/Lob tests too | **Document inline, back off one tier, continue** |

A1/A10/A11/A12/A14 inactive.

### Decision log

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I5-1** | Single `SinCosKernel.java` housing both primitives | Shared Payne-Hanek argument reduction (~600 LOC); duplicating into separate kernels wastes 600 LOC of the trickiest bit-math |
| **P2I5-2** | Algorithm source = CORE-MATH `src/binary64/sin/sin.c` and `cos/cos.c` | BSD/MIT-licensed, correctly-rounded by design. Same family Phase 2i used for exp. |
| **P2I5-3** | Probe oracle = CORE-MATH `cr_sin`/`cr_cos` directly (NOT `std::sin`/`std::cos`) | Phase 2i A3 lesson: Apple libm not always correctly-rounded |
| **P2I5-4** | DB coverage in probe from day one | Phase 2i WI-1.1 had to retrofit DB coverage; pre-emptive |
| **P2I5-5** | NCCS probe regenerated against CORE-MATH `cr_exp` | Same A3 reason — re-aligns the NCCS oracle with the correctly-rounded source-of-truth |
| **P2I5-6** | NCCS attempt EXACT, fall back to TIGHT-with-A19-doc if needed | User chose ambitious. EXACT either succeeds (clear win) or A19 documents the actual residual floor (still informative for Phase 2j). |
| **P2I5-7** | 3 worktrees: A=WI-1, B=WI-2, C=WI-3 (after WI-1) | Maximize parallelism; A & B touch disjoint files; C blocks on A's `JQuantMath.cos/sin` |
| **P2I5-8** | WI-1 = single commit (no sub-layer split) | cos and sin can't land separately; ~1500 LOC paired is comparable to Phase 2i exp's single commit |
| **P2I5-9** | Direct-to-main, signed `-s`, no `Co-authored-by` | Standing project rule |

### Exit criteria

Phase 2i.5 is complete when **all** hold:

1. **WI-1**: 2 EXACT-tier parameterized tests (cos, sin) pass against CORE-MATH `cr_*` probe oracles. All probe inputs incl. DB hard-cases bit-exact.
2. **WI-2**: NCCS CDF code uses `JQuantMath.exp`. NCCS probe regenerated against `cr_exp`. Tier outcome is one of:
   - EXACT (success path); OR
   - TIGHT (with inline A19 documentation citing residual floor source).
3. **WI-3**:
   - GaussLaguerre cos-integrand test rewired to `JQuantMath.cos`; tier flipped to TIGHT (or tighter), OR A19 documented.
   - GaussLobatto trig-integrand tests rewired to `JQuantMath.cos`/`sin`; tier flipped, OR A19 documented.
4. **Test suite**: `mvn -pl jquantlib test` reports `0 failures, 0 errors`. Test count `686/0/0/22`.
5. **Scanner**: WIP unchanged at 0.
6. **Documentation**: `docs/migration/phase2i.5-completion.md` written following Phase 2i shape (test count tracking, per-WI summary, A-trigger fire history, decision log, Phase 2j seed list refresh).
7. **Tag**: `jquantlib-phase2i.5-complete` on resulting main tip.
8. **Memory**: `MEMORY.md` and `project_jquantlib_migration.md` updated.

### Out-of-scope (explicit)

- BroadieKaya asset-leg retry (Low priority per audit).
- `JQuantMath.log` and `.pow` (not cited in audit).
- `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot`.
- Codebase-wide `Math.* → JQuantMath.*` mechanical swap (only the 4 named WI-2/WI-3 sites).
- Non-transcendental floor investigations (Douglas ADI, FdmAffineModelTermStructure) — Phase 2j scope.
- Gaussian1D family port — Phase 2j scope.
