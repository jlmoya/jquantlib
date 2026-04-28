# Phase 2i Design — Transcendental Library Port

**Status:** approved 2026-04-28
**Predecessor:** `jquantlib-phase2h-complete` @ `f0256c8` (tests `677/0/0/22`, scanner WIP=0)
**Working directory:** `/Users/josemoya/eclipse-workspace/jquantlib`
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`
**Algorithm source-of-truth:** FreeBSD msun (libm) `e_*.c` (BSD-licensed)

---

## 1. Context & Motivation

Phase 2f surfaced a structural floor across the Java port: JVM `Math.exp` carries up to ~1 ULP of slack relative to libc++ `std::exp`. That slack propagates through every transcendental-bearing computation — Heston Fourier inversion, Hull-White FD step kernels, NCCS distribution chains — and limits the EXACT tier across a swath of the test suite.

Phase 2g resolved the Brent solver structural floor (pre-loop init alignment) and unlocked 19 LOOSE→TIGHT promotions. The remaining floor is the transcendental one. Phase 2h confirmed it concretely: FdHullWhiteSwaptionEngine could only reach LOOSE 2e-12 against probe, with the residual gap attributable to compounded `Math.exp` calls inside the Hundsdorfer step.

Phase 2i removes the floor by porting libc++'s transcendental algorithms (msun-derived) to pure Java. The port is greenfield infrastructure — it does not resolve any stub but unblocks tier promotions across previously-pinned tests.

### Goals (in scope)

- **WI-1:** Port `exp`, `log`, `sin`, `cos`, `pow` to pure Java in a new package `org.jquantlib.math.transcendental`. Bit-exact agreement with `std::exp` etc. on Linux x86-64.
- **WI-2:** Surgical integration at three high-leverage call-site clusters: FdHullWhiteSwaptionEngine (LOOSE → TIGHT), Heston BroadieKaya asset-leg (5e-3 → LOOSE), NCCS distribution chain (TIGHT → EXACT attempt).
- **WI-3:** Tier-promotion audit + sweep across the suite. No new code — only tier flips on existing tests, with classification of any residual non-transcendental floors.

### Non-goals (explicit out-of-scope)

- `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot`, etc. Not needed by WI-2 sites; if surfaced (A16), defer.
- Performance benchmarking. `JQuantMath.*` will be slower than `Math.*` (no JIT intrinsics). Acceptable for this phase.
- Codebase-wide mechanical `Math.* → JQuantMath.*` swap. Only the three named WI-2 sites are rewired.
- `Math.sqrt`, `Math.fma`, basic arithmetic — JVM matches libc++ here.
- Phase 2h Fdm completeness items (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion). Carried forward to Phase 2j+.

---

## 2. Approach Comparison

| Approach | Description | Verdict |
|----------|-------------|---------|
| **A. Pure-Java msun port** *(chosen)* | Transcribe libc++'s msun-derived algorithms (`e_exp.c`, `e_log.c`, etc.) to Java. Static methods on `JQuantMath`. Bit-exact match by construction. | Highest leverage; one port unblocks 3 tier-promotion clusters and structurally neutralizes A13 across the suite. |
| **B. Per-call-site Taylor refinement** | Locally compensate JVM `Math.exp` slack at each high-leverage call site (Kahan-style correction terms). | Rejected: doesn't generalize, accumulates in compound expressions, doesn't help future call sites. |
| **C. JNI to libc++/libm directly** | Native bridge to `std::exp` etc. | Rejected: portability nightmare (Linux/macOS/Windows divergence), build complexity, defeats "pure Java" project posture. |
| **D. Accept the floor** | Pin all transcendental-bearing tests at TIGHT/LOOSE permanently; document A13 as immutable. | Rejected: structural — caps EXACT-tier reach for the entire project. User goal is "fully ported QuantLib"; ULP-floored ports are not faithful ports. |

**Decision (P2I-1):** Approach A. Phase 2h seed-list ranked it as headline candidate; user picked it ("A") at the scope-decision gate.

---

## 3. Worktree Topology & Layer Ordering

Three git worktrees, linear WI dependency, no within-phase parallelism (each WI strictly depends on the prior).

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i-A` | `phase-2i-A-transcendental-lib` | WI-1 (4 sequential sub-layer commits: 1.1 exp, 1.2 log, 1.3 sin/cos paired, 1.4 pow) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i-B` | `phase-2i-B-call-site-integration` | WI-2 (3 sub-tasks: B-1 FdHullWhite, B-2 Heston BroadieKaya, B-3 NCCS) — dispatches AFTER WI-1 lands |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2i-C` | `phase-2i-C-tier-promotion-sweep` | WI-3 audit + tier-flip sweep — dispatches AFTER WI-2 lands |

### Layer ordering

- **L0** — pre-flight + worktree setup (confirm baseline `677/0/0/22`, scanner WIP=0, tip `f0256c8`; create 3 worktrees off main tip).
- **L1** — WI-1 sequential (4 sub-commits in worktree A; each lands fast-forward to main with EXACT tests passing before next dispatch).
- **L2** — WI-2 sequential (3 sub-tasks in worktree B; B-1 → B-2 → B-3, each commits independently). Worktree B rebases on main tip after L1.
- **L3** — WI-3 sweep (audit-then-flip in worktree C). Worktree C rebases on main tip after L2.
- **L4** — completion doc + tag `jquantlib-phase2i-complete` on resulting main tip.

### Why no parallelism

WI-2 needs WI-1's `JQuantMath` to swap into. WI-3 needs WI-2's tier-flip results to know which tests are still pinned by transcendental floor vs other structural sources. Worktrees provide branch isolation and clean rebase points, not concurrency.

### Sub-layer ordering inside WI-1

| Sub | Primitive | LOC est. | Why this order |
|-----|-----------|----------|----------------|
| 1.1 | `exp` | ~600 | Standalone. Foundational — used by 1.4 (pow = exp(y*log(x))). |
| 1.2 | `log` | ~700 | Standalone. Used by 1.4. |
| 1.3 | `sin` + `cos` | ~1500 | Paired (shared Payne-Hanek argument reduction). Independent of 1.1/1.2. |
| 1.4 | `pow` | ~1000 | Depends on 1.1 + 1.2. Special-case dense (IEEE-754 corner cases). |

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers

| Tier | Threshold | Phase 2i usage |
|------|-----------|-----------------|
| **EXACT** | bit-identical (`Double.doubleToRawLongBits` match) | WI-1 unit tests for `JQuantMath.{exp,log,sin,cos,pow}` against the msun reference. The defining correctness criterion for the port. |
| **TIGHT** | `abs 1e-14 + rel 1e-12` | WI-2 promotions where downstream non-trivial algebra introduces additional rounding beyond a single transcendental call. |
| **LOOSE** | `abs 1e-8 + rel 1e-8` | Fallback for WI-2 sites where TIGHT proves unreachable. Document why inline. |
| **per-test exception** | numeric value + one-line justification | Reserved for WI-3 sites where a residual gap > LOOSE survives. |

**EXACT-tier rule for transcendentals:** comparison is on `long` bit pattern, not value. A 1-ULP value difference may flip downstream branches; bit-exact is the only meaningful correctness criterion.

### Probes

**WI-1 probes** — new under `migration-harness/cpp/probes/transcendental/`:

| Probe | Inputs (~count) | Captured |
|-------|-----------------|----------|
| `exp_probe.cpp` | ~600: subnormals, ±0, ±denorm boundary, ±1, ±log(2), ±log(2)/2, ±π, ±88, ±709 (overflow boundary), ±745 (underflow), ±NaN, ±∞, dense [-10, 10] @ 0.01, sparse [-700, 700] | `long` raw bits of `std::exp(x)` |
| `log_probe.cpp` | ~600: subnormals, +0 (→ -∞), 1.0 (→ +0), powers of 2, e, dense (0, 10], sparse (0, 1e308], +∞, NaN, all negatives → NaN | `long` raw bits of `std::log(x)` |
| `sin_cos_probe.cpp` | ~800 (x, sin, cos) triples: 0, ±π/6, ±π/4, ±π/3, ±π/2, ±π, ±2π, ±π·2^k for k=10..50 (Payne-Hanek stress), dense [-2π, 2π], ±NaN, ±∞ → NaN | `long` raw bits of both `sin` and `cos` |
| `pow_probe.cpp` | ~1000 (b, e) pairs: IEEE-754 special cases (0^0=1, 1^anything=1, ±0 with various exponents, NaN propagation), integer exponents [-50, 50], dense fractional grid, large exponents | `long` raw bits of `std::pow(b, e)` |

JSON schema:
```json
{
  "function": "exp",
  "samples": [
    {"x": 1.0, "y_bits": "0x4005bf0a8b145769"}
  ]
}
```

Java tests parse via `Long.parseUnsignedLong(s.substring(2), 16)` → `Double.longBitsToDouble(bits)` for the expected value, then assert via `Double.doubleToRawLongBits(actual) == expectedBits`. NaN handled via mask-off-payload comparison (decision P2I-12, see §5).

**WI-2 regenerated probes** (no new C++ source — re-run existing probes against pinned submodule, byte-identical JSONs):

| Existing probe | Phase | WI-2 retarget |
|----------------|-------|---------------|
| `references/pricingengines/swaption/fdhullwhite_*.json` | 2h | LOOSE 2e-12 → TIGHT |
| `references/pricingengines/vanilla/heston_broadiekaya_*.json` | 2f | 5e-3 per-test → LOOSE |
| `references/pricingengines/vanilla/heston_nccs_*.json` | 2f | TIGHT → EXACT attempt |

### Test discipline (binding)

1. **Probe-before-port.** Java tests load values via `ReferenceReader.load(path)` — never invent expected values inline.
2. **No backfilling green via tolerance.** If `JQuantMath.exp(x)` differs from probe at one input, fix the algorithm — not the tier.
3. **One sub-layer = one commit.** Each WI-1 sub (exp / log / sin+cos / pow) lands as one commit with passing EXACT tests for that primitive.
4. **Inline justification required** for any LOOSE or per-test exception that survives WI-3.
5. **Bit-pattern comparison helper** lives in `org.jquantlib.testsuite.util.MathTestSupport.assertBitsEqual(double expected, double actual)` — handles NaN payload normalization and -0/+0 distinction.
6. **Probe density requirement.** Each WI-1 probe must include all IEEE-754 special cases plus the algorithm's argument-reduction breakpoints (e.g. `log(2)` for exp's range reduction). Reviewers verify coverage.
7. **No "EXACT-except-NaN" wiggle room.** NaN bit pattern compared after payload mask (P2I-12).

### Test count expectations

| Event | Δ tests | Notes |
|-------|---------|-------|
| WI-1.1 exp | +1 EXACT | parameterized, ~600 cases in one test |
| WI-1.2 log | +1 EXACT | same shape |
| WI-1.3 sin + cos | +2 EXACT | one per primitive, shared probe |
| WI-1.4 pow | +1 EXACT | same shape |
| WI-2 B-1 / B-2 / B-3 | 0 each (tier flips) | |
| WI-3 sweep | 0–3 (gap-pinning if needed) | |

**Aggregate target:** `677 → 682`, ceiling `685`. Scanner WIP unchanged at 0.

### JVM-vs-libc++ ULP-slack mitigation (concrete)

- **Inside `JQuantMath.*`:** bit-exact with libc++ by construction (msun algorithm transcribed).
- **At call sites:** `Math.exp(x)` → `JQuantMath.exp(x)` swap removes the structural floor on EXACT-tier propagation.
- **Beyond Phase 2i:** tests pinned at TIGHT *because of* `Math.exp` slack become EXACT-promotion candidates in WI-3. Tests pinned at TIGHT for *other* reasons (Brent residual rounding, scheme rounding, etc.) stay TIGHT.

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers

| ID | Condition | Phase 2i interpretation | Action |
|----|-----------|--------------------------|--------|
| A1 | Scanner WIP > 1000 | Inactive (we're at 0; greenfield) | n/a |
| A2 | Tolerance looser than `1e-8` needed | If WI-1 EXACT fails for any primitive at any input | **Pause** — port is wrong, not the test |
| A3 | Cross-validation suggests v1.42.1 / msun internally inconsistent | **Pause** — surface to user |
| A4 | Stub needs new class outside existing 61 packages | Phase 2i creates exactly one new package (`org.jquantlib.math.transcendental`) — that's the design, not a trigger. Disabled for the planned package; armed for any *other* unplanned new class. |
| A6 | End-of-layer ack | **Disabled** per `feedback_phase2a_no_a6.md` |
| A8 | Test suite red unrelated to current work | **Pause** — investigate before continuing |
| A9 | Worktree merge conflict | **Pause** — controller resolves manually |
| A13 | JVM transcendental ULP slack vs libc++ | The trigger Phase 2i was *designed to neutralize*. Re-arms only if a *non-transcendental* JVM-libc++ floor surfaces (`Math.sqrt`, `Math.fma`, etc.) | If surfaced: document as A13-extension, defer |
| A15 | Previously-hidden bug surface | **Pause** — bundle fix as `align(...)` commit, then continue |
| A16 | Missing dependency outside planned scope | E.g. WI-2 needs `cosh`/`sinh` not in WI-1 scope | **Pause** — decide scope-add vs phase-defer |
| A17 | >2 unplanned `align(...)` commits during port | Cumulative across A/B/C | **Pause** — re-evaluate scope |
| **A18** *(new)* | Platform-specific NaN bit pattern divergence | **Pause** — document portability surface, decide canonicalization. Default fallback: mask off NaN payload bits before comparison. |
| **A19** *(new)* | WI-2 promotion fails after correct `JQuantMath` swap-in | **Pause** — distinguishes "transcendental was the floor" from "another structural source exists". If A19 fires for one site: stay LOOSE, document, continue. If A19 fires for all three: design-level finding — Phase 2i delivered WI-1 but integration thesis needs revision. |

Inactive: A10/A11/A12/A14.

### Decision log

| # | Decision | Rationale |
|---|----------|-----------|
| P2I-1 | Scope = transcendental library port (Option A from Phase 2h seed list) | Highest leverage; unblocks 3 tier-promotion clusters with one port |
| P2I-2 | Source-of-truth = FreeBSD msun (libm) `e_*.c` files, transcribed faithfully | BSD-licensed, well-documented, matches libc++ on Linux x86-64 |
| P2I-3 | New package `org.jquantlib.math.transcendental` with single facade class `JQuantMath` (static methods, mirrors `java.lang.Math` API) | Drop-in replacement at call sites; package-private kernel classes |
| P2I-4 | WI-1 sub-layered into 4 sequential sub-commits (exp / log / sin+cos / pow) | Each primitive independently verifiable; mirrors Phase 2h's sub-layer pattern |
| P2I-5 | Sin and cos paired in sub-commit 1.3 | Share Payne-Hanek argument reduction (~600 LOC); splitting duplicates |
| P2I-6 | EXACT-tier comparison via `Double.doubleToRawLongBits` bit pattern | 1-ULP value difference may flip downstream branches; bit-exact is only meaningful correctness criterion |
| P2I-7 | WI-2 surgical (3 named sites), NOT codebase-wide swap | Surgical at high-leverage sites; WI-3 sweep evaluates broader-swap justification |
| P2I-8 | WI-3 = audit + tier-flip sweep, no new code | Validation phase; new structural code surfaced → defer |
| P2I-9 | 3 worktrees, linear WI dependency, no within-phase parallelism | No parallelism opportunity (each WI strictly depends on prior); worktrees for branch isolation |
| P2I-10 | Test count target 677 → ~682, ceiling 685 | One parameterized EXACT test per primitive (5 total); WI-2/WI-3 are tier flips |
| P2I-11 | Direct-to-main commits, signed `-s`, no `Co-authored-by: Claude` trailer | Standing project rule |
| P2I-12 | A18 NaN-payload divergence handled via mask-off-payload comparison | Pragmatic — IEEE-754 only specifies NaN-ness, not payload |

### Exit criteria

Phase 2i is complete when **all** hold:

1. **WI-1**: 5 EXACT-tier parameterized tests (exp, log, sin, cos, pow) pass on Linux x86-64 against probe JSONs. Bit-exact for ≥99.9% of probe inputs; any non-EXACT result investigated and documented inline.
2. **WI-2**:
   - B-1 FdHullWhite: LOOSE 2e-12 → TIGHT, OR A19 fired and documented.
   - B-2 Heston BroadieKaya: 5e-3 per-test → LOOSE, OR A19 fired and documented.
   - B-3 NCCS: TIGHT → EXACT attempt completed; result documented.
3. **WI-3**: Audit report committed listing every TIGHT or per-test-exception test, classified as: (a) flipped tighter this phase, (b) blocked by non-transcendental floor (with reason), (c) candidate for future flip pending other work.
4. **Test suite**: `mvn -pl jquantlib test` reports `0 failures, 0 errors`. Test count in `[677, 685]` with delta documented in completion doc.
5. **Scanner**: WIP count unchanged at 0.
6. **Documentation**: `docs/migration/phase2i-completion.md` written following Phase 2g/2h shape.
7. **Tag**: `jquantlib-phase2i-complete` on resulting main tip.
8. **Memory**: `MEMORY.md` and `project_jquantlib_migration.md` updated.

### Out-of-scope (explicit)

- `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot`, etc.
- Performance benchmarking.
- Codebase-wide `Math.* → JQuantMath.*` swap (only the 3 named WI-2 sites).
- `Math.sqrt`, `Math.fma`, basic arithmetic.
- Phase 2h Fdm completeness items (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion) — Phase 2j+.

---

## Appendix A — Reference algorithm sources

| Primitive | msun source | libc++ wrapper | Algorithm summary |
|-----------|-------------|----------------|-------------------|
| `exp` | `e_exp.c` | `<cmath>` `std::exp` | Range reduction `x = k·ln(2) + r`, polynomial approx of `expm1(r)`, reconstruct `2^k · (1 + expm1(r))` |
| `log` | `e_log.c` | `<cmath>` `std::log` | `x = 2^k · m`, m ∈ [1, 2); `log(x) = k·ln(2) + log(m)` via polynomial in `(m-1)/(m+1)` |
| `sin`/`cos` | `s_sin.c`, `s_cos.c`, `e_rem_pio2.c` | `<cmath>` | Payne-Hanek argument reduction to ±π/4, then minimax polynomial |
| `pow` | `e_pow.c` | `<cmath>` `std::pow` | Decompose `x^y = exp(y · log(x))` with extended-precision intermediate; dense IEEE-754 special-case table |

All msun files BSD-licensed (FreeBSD origin). Transcription is at the algorithm/sequence level — Java arithmetic primitives match IEEE-754 binary64 semantics on basic ops, so the same operation sequence produces the same result.

---

## Appendix B — Outcome forecast

| Metric | Phase 2h tip | Phase 2i target | Phase 2i ceiling |
|--------|--------------|-----------------|-------------------|
| Tests | 677/0/0/22 | 682/0/0/22 | 685/0/0/22 |
| Scanner WIP | 0 | 0 | 0 |
| EXACT-tier transcendental coverage | none | exp, log, sin, cos, pow (bit-exact at probe inputs) | same |
| FdHullWhite swaption tier | LOOSE 2e-12 | TIGHT (`abs 1e-14 + rel 1e-12`) | EXACT if A19 doesn't fire and additional floors absent |
| Heston BroadieKaya asset-leg tier | 5e-3 per-test | LOOSE | TIGHT |
| NCCS distribution chain tier | TIGHT (A13) | EXACT or TIGHT (A13-residual) | EXACT |
| Net tier promotions across suite (WI-3) | n/a | ≥3 (the 3 named sites) | ≥6 (named sites + sweep finds) |

If A19 fires for all three named sites, Phase 2i is still successful as WI-1 — `JQuantMath` lands and is correct — but the integration thesis (transcendentals as the dominant floor) is wrong, and follow-up phases would need to investigate other structural sources. This outcome would be surfaced in completion doc and seed list for Phase 2j.

---

## Addendum (2026-04-28) — Option D pivot: msun is NOT correctly rounded

During WI-1.1 dispatch, the implementer transcribed FreeBSD msun `e_exp.c` to Java verbatim and discovered **the algorithm choice was wrong**. Evidence at `x = 1.0`:

| Source | Bit pattern | Decimal |
|--------|-------------|---------|
| Apple libc++ `std::exp(1.0)` (probe ground-truth on macOS) | `0x4005bf0a8b145769` | 2.71828182845904509... ← correctly rounded |
| JVM `Math.exp(1.0)` | `0x4005bf0a8b14576a` | 2.71828182845904553... ← 1 ULP up |
| FreeBSD msun `e_exp.c` (verbatim, traced in C) | `0x4005bf0a8b14576a` | same as JVM |
| Java port of msun (WI-1.1 attempt) | `0x4005bf0a8b14576a` | algorithmically faithful to msun |

**The Phase 2f A13 finding was real but the prescription was wrong.** msun (FreeBSD libm) and JVM `Math.exp` are both members of the *non-correctly-rounded ~1-ULP family*. Apple libm (which is what `<cmath>` `std::exp` resolves to on macOS) is *correctly-rounded*. Porting msun to Java reproduces JVM's existing 1-ULP slack — does NOT close the gap to libc++.

### Revised scope (Option D)

| Item | Original (msun, all 5 primitives) | Revised (CORE-MATH, exp only) |
|------|-----------------------------------|-------------------------------|
| **WI-1** | Port `exp`, `log`, `sin`, `cos`, `pow` from msun (~5K LOC total) | Port `exp` ONLY from CORE-MATH (correctly-rounded; `~250-400 LOC` per primitive in CORE-MATH due to double-double arithmetic + lookup tables; ~2-3× the LOC of msun's exp) |
| **WI-2** | 3 sites: B-1 FdHullWhite + B-2 BroadieKaya + B-3 NCCS | 1 site: B-1 FdHullWhite only. B-2/B-3 deferred — they need log/sin/cos which are no longer in scope. |
| **WI-3** | Suite-wide tier-promotion sweep | Lighter audit — confirm B-1 outcome, classify remaining transcendental-floored tests as candidates for future Phase 2i.5 (log/sin/cos/pow) or Phase 2j. |

### Why Option D over alternatives

- **A — All 5 correctly-rounded primitives:** ~10-15K LOC port. Multi-month effort. Premature given exp alone hasn't been proven to flip B-1 yet.
- **B — Pin probes to musl libm (msun-derived):** Solves WI-1 cosmetically but breaks all existing TIGHT-tier alignments built against Apple libm.
- **C — Abandon Phase 2i:** Throws away the FdHullWhite TIGHT promotion goal entirely.
- **D (chosen) — Correctly-rounded `exp` only:** Lowest-risk path that preserves the highest-leverage promotion (FdHullWhite). Each subsequent primitive becomes a separate decision after seeing whether `exp` alone is sufficient.

### Updated decision log

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I-13** | Pivot to Option D (correctly-rounded `exp` only via CORE-MATH) after WI-1.1 surfaced msun ≠ libc++ | msun has same 1-ULP slack as JVM; Apple libm is correctly-rounded. Original thesis required a different algorithm family. |
| **P2I-14** | Algorithm source = CORE-MATH `src/binary64/exp/exp.c` (BSD-licensed, correctly-rounded by design) | Modern (2021+), specifically targets bit-exact correctness across all rounding modes. Repository: `https://gitlab.inria.fr/core-math/core-math` |
| **P2I-15** | Drop WI-2 B-2 (BroadieKaya) and B-3 (NCCS) from this phase scope | Both rely on log/sin/cos paths beyond `exp`. Not addressable with exp-only port. |
| **P2I-16** | Keep WI-1.1 WIP artifacts (probe + reference + facade + test); only `ExpKernel.java` discarded as wrong-algorithm | Probe and reference are correct (Apple libm = correctly-rounded ground truth). Java algorithm just needs replacement, not the surrounding scaffolding. |

### Revised exit criteria

1. **WI-1**: 1 EXACT-tier parameterized test (exp) passes against Apple-libm probe via CORE-MATH algorithm transcription.
2. **WI-2 B-1 only**: FdHullWhite LOOSE 2e-12 → TIGHT, OR A19 fired and documented.
3. **WI-3 lighter**: Audit-only report classifying remaining transcendental-floored tests.
4. Test suite green; scanner WIP unchanged at 0.
5. Tag `jquantlib-phase2i-complete` and completion doc.

### Revised test count target

| Metric | Phase 2h tip | Revised Phase 2i target |
|--------|--------------|--------------------------|
| Tests | 677/0/0/22 | 682/0/0/22 (+1 exp + +4 helper = +5) |
| Scanner WIP | 0 | 0 |
| FdHullWhite tier | LOOSE 2e-12 | TIGHT (success) or LOOSE-with-A19 (other floor) |
| Other transcendental-floored sites | TIGHT/per-test | unchanged this phase; deferred |
