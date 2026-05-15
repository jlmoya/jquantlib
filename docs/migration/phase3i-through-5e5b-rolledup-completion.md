# Phase 3i → 5e.5b-CFC-c Rolled-Up Completion

**Status:** retroactive rolled-up summary
**Tag:** `jquantlib-phase5e5b-CFC-c-checkpoint` @ `98ac66fd`
**Predecessor (last per-phase doc):** `jquantlib-phase3h-complete` @ `80ff375`
**Range:** 545 commits between `phase3h-complete..HEAD`
**Author:** rolled-up retro, 2026-05-14

---

## Why this doc exists

Per CLAUDE.md binding rule "update memory + completion doc + README after every phase tag",
each phase normally gets its own `phase<N>-completion.md`. From Phase 3i onward this discipline
slipped (autonomous-mode + multi-worktree throughput outpaced doc cadence). This doc is the
rolled-up retro covering everything that landed since `jquantlib-phase3h-complete`.

It deliberately does **not** attempt to reconstruct per-sub-phase completion docs at the level
of detail of `phase3h-completion.md`. Instead it summarizes deliverables by phase group, captures
the current baseline, and lists carry-forwards.

---

## Final state at `98ac66fd`

| Metric | Phase 3h tip | Current tip | Δ |
|--------|-------------:|------------:|---:|
| Tests run | 1087 | **2959** | +1872 |
| Failures | 0 | 0 | unchanged ✓ |
| Errors | 0 | 0 | unchanged ✓ |
| Skipped (@Ignore'd) | 38 | **598** | +560 (skeleton-port-then-body-fill cadence) |
| `mvn test` wall-clock | 62.0 s | **161.6 s** | +99.6 s (~3× test count, ~2.6× wall) |
| Scanner WIP | 0 | 0 | unchanged ✓ |

Test growth: ~+1,872 active tests + ~+560 @Ignore'd skeletons = ~2,432 net new test cases
across the 545-commit range, an average of ~4.5 cases per commit — typical for skeleton-port +
body-fill cadence (test-suite rigor directive 2026-05-08).

---

## Phase groupings

### Phase 3i (~7 commits) — Marketmodels evolvers
- 9 evolvers (lognormal/normal × Pc/Euler/Balland) + volprocesses + ConstrainedEvolver
- Sobol BrownianGenerator, CovarianceDecomposition

### Phase 3j (~22 commits) — Marketmodels concrete models + products
- PiecewiseConstantVariance + concrete CTSMM/FlatVol/AbcdVol model variants
- MarketModelMultiProduct concrete instances (caplets, swaptions, RatchetCaps)

### Phase 3k + 3k.5 (~23 commits) — Marketmodels callability + pathwise
- Callability framework, pathwise greeks, MarketModelDiscounter refinements

### Phase 4 META (`phase4-meta-design.md`) — `ql/experimental/` port roadmap
24 subdirectories, ~63,700 LOC C++. Decomposed into Phases 4a-4o.

| Sub-phase | Commits | Deliverable |
|-----------|--------:|-------------|
| 4a | 5 | experimental/credit (CDO basket, latent models, CDS option, NTD) |
| 4b | 2 | (small) follow-ups |
| 4c | 1 | (small) follow-ups |
| 4d | 6 | experimental/volatility (NoArbSABR, SVI, ZABR, surface hierarchy) |
| 4e + 4e.5 | 6 | experimental/math (copulas, PSO/firefly/HybridSA, latent model base) |
| 4f + 4f.5/.5b/.5c | 5 | experimental/exoticoptions partial |
| 4g | 0 | tagged-only checkpoint (carry-forward consolidation) |
| 4h + 4h.5/.5b/.5c | 6 | experimental/varianceoption + variancegamma |
| 4i + 4i.5/.5b/.5c | 1 | experimental/finitedifferences extensions |
| 4j | 6 | experimental/coupons |
| 4k | 6 | experimental/swaptions |
| 4l | 0 | (planned but absorbed into 4m) |
| 4m + 4m.5/.6/.7/.7b/.7c | 5 | experimental/inflation (already done in 2s; minor gap-fill) |
| 4n + 4n.5/.5b/.5c/.5d | 6 | experimental/shortrate (GeneralizedHullWhite, GeneralizedOUProcess) |
| 4o + 4o.5 | 8 | experimental/forward (PartialTimeBarrier, SoftBarrier carry-forwards) |

**Tagged Phase 4 milestones:** `phase4b-complete`, `phase4c-complete`, `phase4d-complete`, `phase4g-complete`. Phases 4a, 4e–4o are untagged but their commits exist in the log (grep `--grep="Phase 4<x>"`).

### Phase 5 META (`phase5-meta-design.md`) — `test-suite/*.cpp` rolled port
129 C++ files, ~79,284 LOC. Decomposed into Phases 5a-5k.

| Sub-phase | Commits | Theme |
|-----------|--------:|-------|
| 5a | 10 | utilities + first batch of small tests |
| 5b + 5b.5/.5b | 5 | medium tests, body-fill cadence established |
| 5c | 5 | termstructures tests (zerocurve, spreaded, piecewise) |
| 5d + 5d.5 | 7 | indexes/ibor + OvernightIndex family port + OvernightIndexedCoupon |
| 5e + 5e.5 + 5e.5b | 10 | cashflows/ tests; CappedFlooredOvernightIndexedCoupon (CFC) bring-up |
| 5f + 5f.5 | 12 | instruments/ tests (Asian, barrier, lookback, double-barrier) |
| 5g + 5g.5/.5b/.5c/.5d/.5e/.5f | 5 | pricingengines/ tests (Black/Bachelier calculators, BlackFormula) |
| 5h + 5h.5 | 8 | MC infra + AnalyticHestonEngine integration tests |
| 5i + 5i.5/.5b | (in flight) | LMM/Heston coverage extensions |
| 5j + 5j.5 | (planned) | model-specific test coverage |
| 5k + 5k.5 | (planned) | residual long-tail tests |

**Tagged Phase 5 milestones:** none yet. `jquantlib-phase5h5-checkpoint` @ `198f8987` was a session-snapshot tag (not a true completion); superseded by `phase5e5b-CFC-c-checkpoint` @ `98ac66fd` after the BlackON Schedule.dedup fix landed.

---

## Notable architectural fixes during the range

1. **`align(time.Schedule)` — dedup post-BDC dates in Backward/Forward loops** (`98ac66fd`, this session)
   - C++ `schedule.cpp:229-233 / :326-330` deduplicates dates that adjust to the same business day
     during the loop; Java was missing this check, generating one entry per calendar day for
     1-day-tenor schedules and leaving silent duplicates after post-loop BDC application.
   - Surfaced by Phase 5e.5b-CFC-c BlackON cap/floor body-fills (3-day overshoot in fixingDates.back
     → 6.7e-7 drift in Black premium → cap rate failure at 1e-8 tolerance).
   - Fix: 20-LOC change in `Schedule.java`. Cross-validated via new BlackON probe (`7ffcd478`).
   - Test impact: `2 newly green / 0 regression` across 2959 tests.

2. **Phase 3f `Array(double[])` reference semantics** (pre-3i) — improved testIsdaEngine bootstrap
   precision 100×.

3. **MersenneTwister unsigned-long mask** (Phase 3h B.7-align) — pre-existing latent bug; fixed
   inline when first exercised by Brownian generator.

4. **WeakReferenceObservable batched notification** (Phase 2x A.4, pre-3i) — `mvn test` 30+ min →
   59.5s (32× speedup); still in effect.

---

## Carry-forwards (open at this checkpoint)

**Test-suite gaps:**
- Phase 5i+ remaining test-suite port (LMM, Heston, model-specific coverage)
- 598 currently-skipped tests — many are skeleton ports awaiting body-fill in their respective
  parent phases

**Production gaps:**
- Pure-Java transcendental library port (Approach B from Phase 2g brainstorm) — would unlock
  several TIGHT-tier promotions; carry-forward from Phase 2i pivot (CORE-MATH `exp` only landed)
- BroadieKaya retry, NCCS EXACT — both blocked on `JQuantMath.lgamma`
- Several Phase 4o/4o.5 follow-ups (PartialTimeBarrier, SoftBarrier engine refinements)

**Doc/process gaps:**
- Per-sub-phase completion docs for Phase 3i-5h.5 — intentionally not back-filled in this retro
  (rolled up here). Per-phase docs resume from Phase 5e.5b-CFC-c onward.
- README.md not yet updated with current test-count baseline (2959/0/0/598)

---

## Out of scope (explicit)

- All Phase 5i+ items above
- Per-sub-phase completion docs for the 545-commit retro range
- Verification re-runs of every phase tag's expected baseline (the current baseline is the
  authoritative one going forward)
