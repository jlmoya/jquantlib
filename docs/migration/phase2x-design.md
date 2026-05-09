# Phase 2x Design — Small Infrastructure Aligns

**Status:** approved 2026-05-08 (autonomous mode — fourteenth autonomous phase)
**Predecessor:** `jquantlib-phase2v-complete` (tests `950/0/0/39`, scanner WIP=0)

## 1. Context

Phase 2v closed inflation 100% (production + test-suite). 12 currently-@Ignore'd tests across the test-suite are blocked by 4 small Java production aligns to C++. Plus a wall-clock issue: mvn test is now 30+ min due to WeakReferenceObservable cascade.

Phase 2x consolidates the small infrastructure aligns to unblock those tests + improve developer experience.

## 2. Scope

**4 small aligns (~150-300 LOC total):**
1. **`InterpolatedZeroCurve` constructor bug** — `data[0]==1.0` assertion treats raw zero rates as discount factors. Blocks ≥5 tests. ~10 lines fix.
2. **`CPILeg` builder + `CashFlows.npv(Leg, ...)` + `CashFlows.accruedAmount(Leg, ...)` static overloads** — ports from `ql/cashflows/cpicoupon.{hpp,cpp}` + `ql/cashflows/cashflows.{hpp,cpp}`. ~150 LOC.
3. **`IborCoupon.Settings.usingAtParCoupons()` static accessor** — small additive. ~30 LOC.
4. **WeakReferenceObservable cascade fix** — DividendOptionTest + AsianOptionTest hot loops. Investigate root cause; either short-circuit unnecessary notifications OR optimize the WeakReferenceObservable iteration. Target: mvn test wall-clock < 5 min.

**Out of scope (Phase 2y):**
- AbstractTermStructure → LazyObject proper cycle prevention (architectural; major change)
- IndexManager test isolation lint pass (mechanical sweep across many test files)
- Body-fill 5 retained Track F @Ignore'd tests + un-ignore Phase 2v Track C tests post-Phase-2x

## 3. Approach

Single worktree A. 4 sequential sub-commits. After each, attempt to un-ignore tests blocked by the just-landed align.

## 4. Decisions

- **P2X-1:** Each align lands as separate sub-commit + verifies un-ignore for blocked tests
- **P2X-2:** WeakReferenceObservable cascade fix is exploratory — if root cause requires architectural change, scope to a smaller intervention (e.g., notification batching) and document remaining work as Phase 2y
- **P2X-3:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A33. New **A34:** WeakReferenceObservable fix turns out architectural — defer to Phase 2y after acceptable mitigation.

## Outcome forecast

| Metric | Phase 2v tip | Phase 2x target |
|--------|--------------|-----------------|
| Tests | 950/0/0/39 | ~960/0/0/30 (un-ignore 5+ tests, possibly add active equivalents) |
| mvn test wall-clock | 30+ min | <5 min (target) |
| @Ignore'd tests | 39 | ~30 (5+ unblocked) |
