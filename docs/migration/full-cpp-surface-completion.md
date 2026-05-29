# Full C++ v1.42.1 surface — functional-coverage completion

**Date:** 2026-05-29
**Tag:** `jquantlib-cpp-surface-functional-coverage`
**Main @ completion:** post-`b0858548` (this doc + the tag commit)

## What this milestone IS

Every class in C++ QuantLib **v1.42.1** `ql/` (`099987f0`) is now *accounted for*, mechanically and reproducibly, via `migration-harness/check_coverage.py`:

| Metric | Value |
|---|---|
| C++ distinct class/struct names | 2024 |
| Present in Java (ported) | 1933 |
| Allowlisted (verified false-positives, each with a written rationale) | 91 |
| **Unflagged gaps** | **0** |
| Coverage (ported + allowlisted) | 100.0% |
| Full `jquantlib` suite | **GREEN** — 676 test classes, 3678 tests, 0 failures, 0 errors |

"Ported" for the finance-specific classes added this cycle means **cross-validated against C++ v1.42.1 to tolerance** (probe → reference JSON → Java assertion), not merely name-present. "Allowlisted" means a reviewed entry in `check_coverage.py` with a one-line reason: case-rename, inner-class, C++-only template idiom, traits-folded-into-Java-generics, or commented-out-upstream. Re-runnable any time: `python3 migration-harness/check_coverage.py` → `0 unflagged`.

## What this milestone is NOT

- **Not** a claim that every C++ class has a 1:1 Java twin. Java renames, nests, and folds C++ template machinery; 91 classes are realized differently and documented as such.
- **Not** a "we recreated everything" claim. This is **functional coverage**: the capability exists in JQuantLib (or is provably realized by an existing Java form), validated against C++.
- **Not** a re-baselining away from C++. No test was loosened or re-pinned to a non-C++ value.

## The gap-fill (this cycle): 10 clusters, all C++-cross-validated

Driven from an honest audit (177 raw name-gaps → triaged by a 6-agent verification pass into 42 genuine ports + 93→91 allowlist):

1. **currencies** (42) — AED/crypto/etc., literals bit-exact vs C++ `make_shared<Data>`.
2. **cashflows** (7) — Redemption, AmortizingPayment, TimeBasket, Digital{Cms,Ibor}Coupon+Leg.
3. **quotes** (4) — DerivedQuote, EurodollarFuturesImpliedStdDevQuote, ForwardSwapQuote, LastFixingQuote.
4. **processes** (7) — Black/BlackScholes/GarmanKohlagen, EndEulerDiscretization, G2Process+Forward, JointStochasticProcess.
5. **credit** (4) — CDO, BaseCorrelationLossModel, RandomDefaultModel, GaussianRandomDefaultModel.
6. **vol-surfaces** (6) — CPIVolatilitySurface, ConstantCPIVolatility, CmsMarket, CmsMarketCalibration, ConstantCapFloorTermVolatility, GridModelLocalVolSurface.
7. **FDM** (5) — FdmDirichletBoundary, FdmIndicesOnBoundary, FdmHestonLocalVolatilityVarianceMesher, UniformGridMesher, TRBDF2 (legacy).
8. **RebatedExercise** (1).
9. **mcbasket** (2) — MakeMCAmericanPathEngine, MakeMCPathBasketEngine.
10. **PSO/firefly** (6) — SimpleRandom/Adaptive/LevyFlight inertia, KNeighbors/Clubs topology, DecreasingGaussianWalk.

Each cluster: implemented in an isolated worktree, C++-probe-cross-validated, two-stage reviewed (spec + code-quality), merged direct-to-main, audit re-run to show the count drop.

### Latent-bug & fidelity findings handled honestly (not papered over)
- **CDO::lgd_** is an uninitialized member in C++ v1.42.1 (reads garbage; never used in pricing) — Java initializes it correctly and documents the divergence; all *priced* outputs cross-validated TIGHT.
- **cashflows fixed-coupon default** corrected to C++'s `1.0` (cashflowvectors.hpp:286) with a citing comment + test.
- **ForwardSwapQuote** eval-date now snapshotted by value (C++ semantics) so the rebuild fires; TDD-proven.
- **JointStochasticProcess** diffusion/stdDeviation use `None` (Cholesky) salvaging to match C++ default (not Spectral).
- **FdmDirichletBoundary.xExtreme_** explicitly initialized (C++ leaves it default-init but always assigns on valid paths) — no UB replicated.
- **PSO** (3 fixes): AdaptiveInertia unsigned-counter wrap matched via `Integer.compareUnsigned`; LevyFlightInertia ± sign restored; ClubsTopology 0-based index documented (corrects a v1.42.1 OOB bug that would throw in Java).

### Honestly-stated carve-outs (cross-validation boundaries, not hidden)
- **CmsMarket.reprice() / CmsMarketCalibration.compute()** run a Levenberg-Marquardt optimizer over the full CMS pricing stack; the optimizer path can't be bit-matched against C++, so only the **deterministic transform math** is cross-validated. Ported faithfully + compile/wire-correct; documented in the test javadoc.
- **RNG-driven optimizer pieces** (PSO SimpleRandom/Levy inertia, firefly walks) are cross-validated on their **deterministic envelope** (formula scaling, decay, regime-switch), not RNG-bit-matched.

## Best-of-breed numerics: analysed, and the evidence says KEEP NATIVE

The owner's directive: this is a *functional* migration; do not recreate generic numerics that proven Java libraries do better — **but** the C++ cross-validation is the only objective measure of quality, it applies symmetrically to our ports and to any candidate library, and no test may be re-baselined away from C++. When in doubt, keep what already passes.

Two analyses (market survey + codebase inventory) plus one empirical demo settled it:

- **Our probes pin C++ *internal* algorithmic state** — MINPACK `fjac`/`fvec`/`ipvt`/`qtf`/`diag`, exact Sobol sequences, bit-exact transcendentals, fixed quadrature nodes — not just final answers. A different-algorithm library cannot reproduce that → it fails our existing tests → the evidence rejects it.
- **Empirical demo (Levenberg–Marquardt → Apache Hipparchus 4.0.3)**, the single lineage-match candidate (both are MINPACK `lmder`/`lmdif` translations), run against the real `levenbergmarquardt.json`:
  - Final params: only 2/4 cases pass TIGHT (1e-12); **case 4 fails even LOOSE** (Hipparchus won't honor MINPACK's `maxfev=3` early-stop — it runs to convergence or throws and returns no point, vs C++ `info=5` + partial-`x`).
  - Pinned MINPACK internals (`fjac`, `ipvt`, `qtf`, `diag_out`, `info`, `nfev`): Hipparchus reproduces **none** — no API surface. Our test asserts `info`/`nfev` exactly.
  - Our hand-port reproduces C++ to **2.2e-16** on the tight cases and is the only implementation reproducing case-4's partial-`x` abort.
- **License gate:** Hipparchus/EJML/Commons-RNG are Apache-2.0 (adoptable in principle); Smile (GPLv3) and JDistlib (GPLv2) are blocked; finmath/Strata are competing finance libs, out of scope.
- **PSO/firefly are QuantLib's own reference implementation** (ported from `ql/experimental/math`, authored by the same contributor who wrote them into C++ QuantLib). There is nothing better to refactor toward.

**Decision (evidence-driven, not opinion): keep every native port; adopt nothing; re-baseline nothing.** The architecture is delegation-ready (clean `Optimizer`/`Interpolation`/`Integrator`/`Solver` seams; 15 optimizer instantiation sites) — so if evidence ever favors a library it can be swapped trivially. The evidence currently does not. The only remaining quality avenue consistent with the C++ oracle is **parity-safe performance optimization of our own code** (same algorithm, probes stay green), deferred as optional.

## Reproduce / verify
- `python3 migration-harness/check_coverage.py` → 0 unflagged, 0 stale, 100%.
- `mvn -pl jquantlib test` from repo root → full suite green.
- Per-class evidence: `docs/migration/gap-classification-ledger.md`.
- Allowlist with rationales: the `ALLOWLIST` dict in `migration-harness/check_coverage.py`.
