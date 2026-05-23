# Phase 3 — Truly 100% closure plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Close every pre-existing pending item identified in the post-Phase-2
audit. No `@Ignore`'d tests, no `throw new UnsupportedOperationException` in
production except documented v1.42.1 parity stubs, all TODO/FIXME triaged.

**Audit baseline (per 2026-05-23 post-Phase-2 audit):**

| Pending category | Mainline | Experimental | Total |
|---|---|---|---|
| `@Ignore`'d test methods | 38 | 4 | 66 |
| `UnsupportedOperationException` throws | 121 | 9 | 130 |
| "not implemented" stubs | 13 | 0 | 13 |
| TODO/FIXME markers | 237 | 10 | 247 |

## Triage clusters

### P3-A: Fdm operator toMatrix() family (6 classes)
- `Fdm2dBlackScholesOp.java`
- `FdmBatesOp.java` (documented v1.42.1 parity stub per phase1-carveouts)
- `FdmHestonHullWhiteOp.java`
- `FdmCIROp.java`
- `FdmHestonOp.java`
- `FdmHestonFwdOp.java`

Each throws `UnsupportedOperationException("XYZ.toMatrix() not implemented; use toMatrixDecomp()")`. Must check C++ behavior: if C++ also has no `toMatrix()` and is decomposition-only, document as INTENTIONAL design (mirroring C++) and DELETE the throw in favor of either (a) implementing properly or (b) keeping with explicit `// C++ parity: this op is decomposition-only by design` comment.

### P3-B: mcbasket MultiPath Phase 4i.5 family (5 classes)
- `EuropeanPathMultiPathPricer.op`
- `LongstaffSchwartzMultiPathPricer.{op, calibrate}`
- `MCPathBasketEngine.calculate`
- `MCLongstaffSchwartzPathEngine.calculate`
- `MCAmericanPathEngine.lsmPathPricer`

Phase 4i.5 deferred port — MultiPath generator family. Need to port the
MultiPath generator infrastructure if it's not already present, then wire
these engines.

### P3-C: Coupon Rate/Price stubs (3 files / 5 stubs)
- `OvernightIndexedCouponPricer.{capletRate, capletPrice, floorletRate, floorletPrice}`
- `MultipleResetsPricer.swapletPrice`
- `RangeAccrualPricer.*` (verify)

These are Coupon pricer methods that throw "not implemented". Each needs a
real impl from C++ source.

### P3-D: Gaussian1d swaption engine family
- `Gaussian1dSwaptionEngine` — pricing-engine-missing UOE
- `Gaussian1dFloatFloatSwaptionEngine`
- `Gaussian1dNonstandardSwaptionEngine`

Phase 4-5 carry-forward. Need to verify what's missing vs done.

### P3-E: Phase 4-5 experimental carry-forwards + stale-calendar cleanup
- ZabrFullFd cross-validation (Phase 4f.5c carry-forward)
- DoubleBarrier experimental (Phase 4e.5 carry-forward)
- NthOrderDerivativeOp extrapolation (Phase 5j.5+ scope)
- LPP3 Heston expansion (Phase 5h.5b deferred)
- SquareRootCLVModel (no validation oracle)
- AmericanMaxPathPricer (Phase X carry-forward)
- 3 stale-calendar @Ignore'd tests (SouthKoreaCalendarTest, ChinaCalendarTest, MexicoCalendarTest — replaced by CalendarsTest.* per Phase1-closure-A2-A-548). **Recommend: delete the stale files** since coverage exists.
- 80 empty-message UOEs — audit each: most likely intentional "abstract default" patterns or semantic guards.

### P3-F: TODO/FIXME triage (247 markers)
Walking through each to classify:
- Real TODO needing work
- Cosmetic / style FIXME
- Pre-existing stale (may be obsolete now)
- Decisions / design-time notes (acceptable)

## Sequencing

P3-A, P3-B, P3-C, P3-D, P3-E dispatched in parallel across 5 worktrees per
proven Phase 2 pattern. P3-F sequenced last (large volume, mostly cosmetic).

## Definition of done

- Suite at 3574+/0/0 baseline maintained
- `@Ignore` count in mainline → 0 (modulo slow-test gates and documented C++ parity)
- `UnsupportedOperationException` in mainline → only documented intentional-throw cases
- TODO/FIXME triaged (acceptable items annotated, real items closed)
- Final tag: `jquantlib-truly-complete`
