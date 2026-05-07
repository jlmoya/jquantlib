# Phase 2j.5 Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Compact plan — Phase 2j precedent + design §3 establish the operational pattern. Track A/B/C dispatched in parallel after L0.

**Goal:** Full Gaussian1D family completion. Land 3 deferred items from Phase 2j (Nonstandard engine track + FloatFloat engine track + MarkovFunctional track). Tests `792 → ~810`; scanner WIP=0; tag `jquantlib-phase2j.5-complete`.

**Architecture:** Same as Phase 2h/2i/2i.5/2i.6/2j — direct commits to `main`, TDD per artifact, probe-before-port, tier-stratified tolerances, JQuantMath.{exp,log,sin,cos} from day one. 3 parallel worktrees per design §3. Pause triggers carry-forward + new A22 (tertiary missing-dep) per design §5.

**Tech Stack:** Java 11 / Maven / JUnit 4; C++17 / CMake / QuantLib v1.42.1 pinned via submodule (`099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner + table-extractor; nlohmann/json for probe output; git worktrees.

---

## Layer 0 — Pre-flight + 3 worktrees + progress doc

### Task 0.1: Pre-flight

- [ ] Confirm `main` clean + tests `792/0/0/22` + scanner `0` + submodule pin
- [ ] Capture predecessor tag `jquantlib-phase2j-complete` @ `8808985`

### Task 0.2: Create 3 worktrees off main tip

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2j5-A-nonstandard /Users/josemoya/eclipse-workspace/jquantlib-2j5-A main
git worktree add -b phase-2j5-B-floatfloat  /Users/josemoya/eclipse-workspace/jquantlib-2j5-B main
git worktree add -b phase-2j5-C-markovfunctional /Users/josemoya/eclipse-workspace/jquantlib-2j5-C main
# Init submodules in each (uses migration-harness)
for wt in A B C; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2j5-$wt
  git submodule update --init --recursive
done
```

### Task 0.3: Init progress doc + commit

Mirror Phase 2j-progress shape; init `docs/migration/phase2j.5-progress.md` with worktree map, pause-trigger status (all "not fired"), test count baseline 792.

```bash
git add docs/migration/phase2j.5-progress.md
git commit -s -m "docs(migration): init phase2j.5-progress log"
git push origin main
```

---

## Track A — Nonstandard engine track (worktree A, 3 sub-commits sequential)

### Task A.1 — Port `NonstandardSwap` instrument

- C++: `ql/instruments/nonstandardswap.{hpp,cpp}` (~LOC: read source for actual count)
- Java target: `org.jquantlib.instruments.NonstandardSwap` (or `org.jquantlib.instruments.nonstandardswap.NonstandardSwap` — match Java side convention)
- Probe: `migration-harness/cpp/probes/instruments/nonstandard_swap_probe.cpp`
- Test: probe-driven, ONE @Test, TIGHT tier
- Commit: `infra(instruments): port NonstandardSwap (Phase 2j.5 Track A.1)`

### Task A.2 — Port `NonstandardSwaption` instrument

- C++: `ql/instruments/nonstandardswaption.{hpp,cpp}`
- Java target: `org.jquantlib.instruments.NonstandardSwaption` (mirror swap path)
- Probe + test as above
- Commit: `infra(instruments): port NonstandardSwaption (Phase 2j.5 Track A.2)`

### Task A.3 — Port `Gaussian1dNonstandardSwaptionEngine`

- C++: `ql/pricingengines/swaption/gaussian1dnonstandardswaptionengine.{hpp,cpp}` (~636 LOC)
- Java target: `org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngine`
- Probe: `gaussian1d_nonstandard_swaption_engine_probe.cpp`
- Test: probe-driven; TIGHT default; LOOSE-with-A19 acceptable per WI-2.1/WI-2.2 precedent
- Commit: `infra(pricingengines.swaption.gaussian1d): port Gaussian1dNonstandardSwaptionEngine (Phase 2j.5 Track A.3)`

---

## Track B — FloatFloat engine track (worktree B, 3 sub-commits sequential)

### Task B.1 — Port `FloatFloatSwap` instrument

- C++: `ql/instruments/floatfloatswap.{hpp,cpp}`
- Java target: `org.jquantlib.instruments.FloatFloatSwap`
- Probe + test; commit `infra(instruments): port FloatFloatSwap (Phase 2j.5 Track B.1)`

### Task B.2 — Port `FloatFloatSwaption` instrument

- C++: `ql/instruments/floatfloatswaption.{hpp,cpp}`
- Java target: `org.jquantlib.instruments.FloatFloatSwaption`
- Probe + test; commit `infra(instruments): port FloatFloatSwaption (Phase 2j.5 Track B.2)`

### Task B.3 — Port `Gaussian1dFloatFloatSwaptionEngine`

- C++: `ql/pricingengines/swaption/gaussian1dfloatfloatswaptionengine.{hpp,cpp}` (~848 LOC, largest engine)
- Java target: `org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dFloatFloatSwaptionEngine`
- Probe + test; LOOSE expected per design §4 risk 2 (deepest integration)
- Commit: `infra(pricingengines.swaption.gaussian1d): port Gaussian1dFloatFloatSwaptionEngine (Phase 2j.5 Track B.3)`

---

## Track C — MarkovFunctional track (worktree C, 3-4 sub-commits sequential)

### Task C.1 — Port `GaussHermiteIntegration` family

Bundle the 4 classes together (they form one cohesive integration unit):
- `GaussianOrthogonalPolynomial` (base)
- `GaussHermitePolynomial` (concrete polynomial)
- `GaussianQuadrature` (Golub-Welsch eigendecomposition, ~200 LOC)
- `GaussHermiteIntegration` (the integration wrapper)

C++ files: `ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}`, `gausshermitepolynomial.{hpp,cpp}`, `gaussianquadrature.{hpp,cpp}`, `gausshermiteintegration.{hpp,cpp}` (~600 LOC total).

Java target: `org.jquantlib.math.integrals` (existing package, add 4 new files).

Probe: `gauss_hermite_integration_probe.cpp` — eigendecomposition correctness (eigenvalues + weights for n=4..32) + integration on standard test integrands. TIGHT tier.

Commit: `infra(math.integrals): port GaussHermiteIntegration family (Phase 2j.5 Track C.1)`

### Task C.2 — Port `AtmSmileSection`

- C++: `ql/termstructures/volatility/atmsmilesection.{hpp,cpp}` (~80 LOC)
- Java target: `org.jquantlib.termstructures.volatilities.AtmSmileSection`
- Probe + test; TIGHT
- Commit: `infra(termstructures.volatilities): port AtmSmileSection (Phase 2j.5 Track C.2)`

### Task C.3 — Port `MarkovFunctional`

- C++: `ql/models/shortrate/onefactormodels/markovfunctional.{hpp,cpp}` (~1710 LOC)
- Java target: `org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional`
- Probe: `markov_functional_probe.cpp` — calibrated sigma vector + forward swap rate fits + numeraire identities. TIGHT (or A20 documented).
- **Critical:** match C++ iteration order strictly. No reordering.
- Commit: `infra(model.shortrate.onefactormodels.gaussian1d): port MarkovFunctional concrete model (Phase 2j.5 Track C.3)`

---

## L2 — Completion + tag + memory + teardown

- [ ] Write `docs/migration/phase2j.5-completion.md` (Phase 2j shape, with per-track summary, A-trigger fire history, decision log additions, Phase 2k seed list refresh)
- [ ] Tag `jquantlib-phase2j.5-complete` on resulting main tip
- [ ] Update `MEMORY.md` + `project_jquantlib_migration.md`
- [ ] Tear down 3 worktrees + delete branches local + remote

---

## Auto-trim policy (controller discretion under autonomous mode)

If a track hits A22 (tertiary missing-dep) or A21 (wall-time), trim that track and defer to Phase 2j.6 mini-phase. Trim order: MarkovFunctional first, then FloatFloat, then Nonstandard.

If multiple A16 fire across tracks, controller may stop the parallel dispatch and re-sequence. The completion doc records actual delivery vs planned.
