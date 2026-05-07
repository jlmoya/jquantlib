# JQuantLib

> A 100%-Java port of [QuantLib](https://www.quantlib.org/) — the de-facto open-source library for quantitative finance — being systematically rebuilt from C++ v1.42.1 with bit-exact precision guarantees.

[![Tag](https://img.shields.io/badge/tag-jquantlib--phase2j--complete-blue)](#migration-status)
[![Tests](https://img.shields.io/badge/tests-792%2F0%2F0%2F22-success)](#migration-status)
[![Scanner](https://img.shields.io/badge/scanner_WIP-0-success)](#migration-status)
[![C%2B%2B%20pin](https://img.shields.io/badge/C%2B%2B%20pin-v1.42.1-informational)](#ground-truth)
[![License](https://img.shields.io/badge/license-BSD-green)](#license)

---

## What is JQuantLib?

JQuantLib provides Java developers and quants with the mathematical, statistical, and modelling toolset needed to value equities, options, futures, swaps, fixed-income instruments, and a wide range of derivatives. It mirrors QuantLib's C++ API as closely as Java idioms allow, offering a smooth bridge for organizations running JVM-based pricing infrastructure.

The original Java port began ~2007, stalled in 2021, and is now being **revived as a multi-phase, precision-first migration from C++ QuantLib v1.42.1**.

## Project posture (2026-04 → present)

This is not a maintenance branch. It is a **systematic, full-fidelity port** with five operating principles:

| Principle | What it means in practice |
|-----------|---------------------------|
| **C++ is source of truth** | Where Java diverges from QuantLib v1.42.1 — signatures, implementations, constants, behavior — the Java code is updated to match C++. The pre-existing Java API is starting material, not a design to preserve. |
| **Cross-validation before commit** | Every functional change is backed by a C++ "probe" (a small program linked against the pinned QuantLib submodule) that emits reference values to JSON. Java tests load those JSONs and assert against them. No expected value is invented inline. |
| **Tier-stratified tolerances** | Comparisons land in one of three tiers — **EXACT** (bit-identical via `Double.doubleToRawLongBits`), **TIGHT** (`abs 1e-14 + rel 1e-12`), **LOOSE** (`abs 1e-8 + rel 1e-8`). Per-test exceptions require an inline written justification. |
| **Bulletproof, not fast** | Every commit compiles and passes the full suite. One stub fix = one commit. No `--no-verify`, no skipped hooks. Mid-port architectural divergence becomes a separate `align(...)` commit, never bundled. |
| **Direct-to-main** | Solo single-owner repo; no PR overhead. Each phase ends with a signed git tag (`jquantlib-phase<N>-complete`) and a completion document under `docs/migration/`. |

## Migration status

| Phase | Tag | What landed | Tests | Date |
|-------|-----|-------------|-------|------|
| 1 | `jquantlib-phase1-complete` | Finished 80 started stubs across 61 existing packages | — | 2026-04-24 |
| 2a | `jquantlib-phase2a-complete` | MINPACK + LM, Heston QuadraticExponential, 56 numerical-suspect markers swept | 626/0/0/25 | 2026-04-24 |
| 2b | `jquantlib-phase2b-complete` | One-factor model Parameter-ref refactor (Vasicek, HullWhite, BK, CIR), Simplex 1D, SABR sentinel fix | 632/0/0/22 | 2026-04-25 |
| 2c | `jquantlib-phase2c-complete` | Chi-squared CDF + CIR.discountBondOption, SABR α-default, HullWhite latent items, BK tree-pricing | 649/0/0/22 | 2026-04-25 |
| 2d | `jquantlib-phase2d-complete` | CapHelper via BlackCalibrationHelper, Heston NCCS scheme, SABR Halton via XABR | 649/0/0/22 | 2026-04-26 |
| 2e | `jquantlib-phase2e-complete` | G2 model body (**scanner WIP: 1 → 0**), BlackCapFloorEngine, full Swaption infra (Black/Tree/Discretized), SwaptionHelper | 656/0/0/22 | 2026-04-26 |
| 2f | `jquantlib-phase2f-complete` | Cap engines (Bachelier branch), G2.swaption integral, Heston BroadieKaya + NCCS tightening | 675/0/0/22 | 2026-04-26 |
| 2g | `jquantlib-phase2g-complete` | Brent.solveImpl pre-loop alignment → **+19 LOOSE→TIGHT promotions**; FdHullWhite/FdG2 deferred to 2h on framework gap | 675/0/0/22 | 2026-04-26 |
| 2h | `jquantlib-phase2h-complete` | Full `Fdm*` finite-difference framework (~3826 LOC, 30 classes); FdHullWhiteSwaptionEngine + **FdG2 TIGHT 8.4e-15 (bit-exact)** + bonus BicubicSpline Address-mapping fix | 677/0/0/22 | 2026-04-27 |
| 2i | `jquantlib-phase2i-complete` | **CORE-MATH correctly-rounded `JQuantMath.exp`**; FdHullWhite tier LOOSE → `within(3e-12)`; A3 finding (Apple libm not always correctly-rounded) | 684/0/0/22 | 2026-04-28 |
| 2i.5 | `jquantlib-phase2i.5-complete` | `JQuantMath.cos` + `.sin` via Dint64 (u128 emulation); NCCS A19 partial — Math.log floor identified | 687/0/0/22 | 2026-04-28 |
| 2i.6 | `jquantlib-phase2i.6-complete` | `JQuantMath.log` (CORE-MATH, 1203-case bit-exact first-shot); NCCS A19 re-fire pinned `gammaFunction_.logValue` Lanczos as actual residual | 688/0/0/22 | 2026-04-30 |
| **2j** | **`jquantlib-phase2j-complete`** | **Gaussian1D family partial (P2J-10 trim): full model layer + 3 of 5 engines (Standard SwaptionEngine LOOSE, CapFloorEngine LOOSE, Jamshidian TIGHT) + 3 MF prereqs (MfStateProcess + SmileSectionUtils + KahaleSmileSection); Nonstandard + FloatFloat + MarkovFunctional deferred to Phase 2j.5 (4× A16 fires)** | **792/0/0/22** | **2026-05-02** |

**Current tip:** `efa487b` on `main`. **Scanner WIP-stub count:** `0`. **Active phase:** Phase 2j.5 (autonomous mode).

> Each phase has a binding **design** doc, an executable **plan** doc, a **progress** log, and a **completion** doc — all under [`docs/migration/`](docs/migration/).

## Architecture of a phase

Every phase follows a uniform shape, refined since Phase 2a:

```
brainstorm → design → plan → execute (subagent-driven) → review → tag → memory
```

### 1. Brainstorm & design
A binding spec (`docs/migration/phase<N>-design.md`) is approved before any code is written. Sections include scope (in/out), approach comparison, worktree topology, tolerance & probe discipline, pause triggers (A1–A19), decision log.

### 2. Plan
A bite-sized, checkbox-tracked task list (`docs/migration/phase<N>-plan.md`) with exact file paths, code snippets, and expected test-count deltas per task. No "TODO" or "TBD" — every step is concrete.

### 3. Execute
Each phase runs across **2–4 git worktrees** (named `jquantlib-<phase>-A`, `-B`, `-C`, ...). A controller dispatches one fresh subagent per task with two-stage review:

1. **Spec compliance review** — does the code match the spec exactly?
2. **Code quality review** — strengths, blockers, and improvements.

Independent worktrees run in parallel; dependent ones serialize.

### 4. Pause triggers
Eighteen-and-counting documented conditions where the controller stops and asks the human. The most consequential have been:

- **A3** *(reference is itself wrong)* — fired in Phase 2i; Apple libm `std::exp` shown not always correctly-rounded; resolution = use CORE-MATH `cr_exp` directly as oracle.
- **A4** *(needs new class outside scoped packages)* — fired in Phase 2e (Swaption was a near-empty stub) and 2g (Fdm framework absent), each cleanly redirected.
- **A13** *(transcendental ULP slack)* — fired in Phase 2f; led to Phase 2i's CORE-MATH transcendental track.
- **A19** *(post-swap tier promotion fails)* — fired in Phase 2i (FdHullWhite — non-transcendental floor) and Phase 2i.5 (NCCS — Math.log floor); each pinpoints the next residual to address.

### 5. Tag + memory
Phase ends with `git tag -a jquantlib-phase<N>-complete`, a completion doc, and a refresh of long-lived project memory.

## Precision track — the JQuantMath story

A recurring discovery from Phase 2f onwards: **the JVM's `java.lang.Math` transcendentals carry up to ~1 ULP of slack relative to a correctly-rounded reference**. That slack compounds through Fourier inversions, ADI schemes, and root-finders, and limits how tight an EXACT-tier comparison can ever be.

Phase 2i and 2i.5 attack the floor head-on by porting **CORE-MATH** — an academic correctly-rounded transcendental library (BSD/MIT, Sibidanov et al., Inria) — into a new package `org.jquantlib.math.transcendental.JQuantMath`.

| Primitive | Status | Source | Note |
|-----------|--------|--------|------|
| `JQuantMath.exp` | ✅ Phase 2i | CORE-MATH `src/binary64/exp/exp.c` | Bit-exact across 508 probe cases including all 51 hard-cases DB entries |
| `JQuantMath.cos` | ✅ Phase 2i.5 | CORE-MATH `src/binary64/cos/cos.c` | Bit-exact across 1,381 probe cases incl. Payne-Hanek stress through 2^50·π |
| `JQuantMath.sin` | ✅ Phase 2i.5 | CORE-MATH `src/binary64/sin/sin.c` | Bit-exact across 1,376 probe cases |
| `JQuantMath.log` | ✅ Phase 2i.6 | CORE-MATH `src/binary64/log/log.c` | Bit-exact across 1,203 probe cases first-shot. Sister `long[4]` dint64-style helpers (log_dint.h is bit-incompatible with sin/cos's dint.h) |
| `JQuantMath.pow` | ⚪ Future (BroadieKaya prereq) | CORE-MATH `src/binary64/pow/pow.c` | Depends on log + exp; deferred per Phase 2j-pre B3 (low empirical leverage at 1 site) |
| `JQuantMath.lgamma` | ❌ Blocked | No correctly-rounded source available | CORE-MATH does not have lgamma; msun/glibc options either non-correctly-rounded or license-incompatible. NCCS EXACT remains blocked. |

A supporting type — `Dint64` — emulates `unsigned __int128` (the extended-precision `dint64_t` CORE-MATH uses for accurate-path arithmetic). It's package-private, validated against 100 probe cases of `add`/`mul`/`mul21`/`fromDouble`/`toDouble`/etc., and **reusable across future log/pow ports**.

### Why CORE-MATH and not msun, glibc, or Apple libm?

This was a hard-won lesson:

- **JVM `Math.exp` (HotSpot)** ≈ 1-ULP slack
- **FreeBSD msun `e_exp.c`** ≈ same 1-ULP slack as JVM (Phase 2i pre-pivot BLOCKED finding)
- **Apple libm `std::exp`** (macOS arm64) — *almost* correctly-rounded but not provably so; ~50/2^64 hard cases are still 1 ULP off (Phase 2i A3 finding, verified via 300-bit mpmath)
- **CORE-MATH `cr_exp`** — correctly-rounded by design across all IEEE-754 rounding modes

Probe oracles for transcendentals therefore `#include "coremath/exp.c"` directly and call `cr_exp(x)` — not the platform `<cmath>`.

## Ground truth

| Resource | Detail |
|----------|--------|
| Pinned C++ submodule | QuantLib **v1.42.1** @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` |
| Migration harness | [`migration-harness/`](migration-harness/) — CMake-built C++17 probes linked against the submodule |
| Probe references | [`migration-harness/references/`](migration-harness/references/) — JSON files keyed by `test_group`, consumed via `ReferenceReader` |
| Java module under port | [`jquantlib/`](jquantlib/) (Maven; Java 11; JUnit 4) |
| Stub scanner | [`tools/stub-scanner/scan_stubs.py`](tools/stub-scanner/) — emits `docs/migration/stub-inventory.json` and `worklist.md` |
| Phase docs | [`docs/migration/`](docs/migration/) — design, plan, progress, completion per phase |

## Repository structure

```
jquantlib/                              ← this repo root
├── jquantlib/                          ← Maven module under active port
│   ├── src/main/java/org/jquantlib/   ← production code
│   │   └── math/transcendental/       ← Phase 2i+ (JQuantMath, ExpKernel, SinCosKernel, Dint64)
│   └── src/test/java/                 ← JUnit 4 tests cross-validated against probes
├── jquantlib-helpers/                  ← helper classes
├── jquantlib-contrib/                  ← third-party contributions (legacy)
├── jquantlib-samples/                  ← sample applications
├── jquantlib-parent/                   ← parent POM
│
├── migration-harness/                  ← C++ ground-truth scaffolding
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── quantlib/                  ← pinned QuantLib v1.42.1 submodule
│   │   └── probes/                    ← per-feature C++ probes emitting JSON
│   │       └── transcendental/
│   │           └── coremath/          ← vendored CORE-MATH algorithm sources
│   ├── references/                     ← generated reference JSONs
│   ├── setup.sh / generate-references.sh / verify-harness.sh
│   └── README.md
│
├── docs/migration/                     ← phase design, plan, progress, completion docs
├── tools/stub-scanner/                 ← Python scanner for in-progress stubs
└── README.md                           ← (this file)
```

## Quick start

### Build & test

```bash
# Test the Java module (run from the inner module — not the root)
cd jquantlib && mvn test
# Expected: Tests run: 687, Failures: 0, Errors: 0, Skipped: 22

# Snapshot the stub scanner
python3 tools/stub-scanner/scan_stubs.py
# Expected: 0 stubs

# Verify the C++ harness is functional
./migration-harness/verify-harness.sh
```

### Regenerate probe references

```bash
# (re-)build the C++ harness and emit all references/*.json
bash migration-harness/setup.sh
bash migration-harness/generate-references.sh
```

### Run a sample

```bash
cd jquantlib-parent
mvn clean verify install
# Sample apps live under jquantlib-samples/
```

## What the next phase looks like

**Phase 2j.5** is in flight (autonomous-mode execution per 2026-05-02 directive — controller decides scope/sequencing without per-phase user gates). Three parallel tracks:

1. **Track A — Nonstandard engine track** — NonstandardSwap + NonstandardSwaption instruments + `Gaussian1dNonstandardSwaptionEngine`
2. **Track B — FloatFloat engine track** — FloatFloatSwap + FloatFloatSwaption instruments + `Gaussian1dFloatFloatSwaptionEngine`
3. **Track C — MarkovFunctional track** — `GaussHermiteIntegration` family + `AtmSmileSection` + `MarkovFunctional`

**After Phase 2j.5:** Douglas ADI / FdmAffineModelTermStructure investigation, U128.java shared util refactor, Phase 2h Fdm completeness items (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion), then Phase 3+ subsystems (`experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`, etc.). Binding exit criterion: every C++ class, function, header, and test from QuantLib v1.42.1 has a faithful Java equivalent. Realistic path to "done" is ~50-100+ phases.

## Documentation links

- [Original JQuantLib homepage](http://www.jquantlib.org)
- [Developer's guide](http://www.jquantlib.org/en/latest/developersguide.html)
- [QuantLib (C++ upstream)](https://www.quantlib.org/)
- [CORE-MATH project](https://core-math.gitlabpages.inria.fr/)

## Modules

| Module | Role |
|--------|------|
| `jquantlib-parent` | Parent POM for unified build |
| `jquantlib` | Main module — actively being ported (mirrors QuantLib/C++) |
| `jquantlib-helpers` | Helper classes |
| `jquantlib-contrib` | Third-party contributions |
| `jquantlib-samples` | Sample code |

## Contributing

This repository is currently a single-owner, direct-to-main migration project. Outside contributions are welcomed via issues, but the operational model assumes one engineer driving the port through subagent-assisted pipelines. If you'd like to help with a specific package or audit a phase, open an issue first to coordinate.

## License

JQuantLib is released under the BSD License — see [LICENSE.TXT](http://www.jquantlib.org/index.php/LICENSE.TXT). Vendored CORE-MATH source files retain their original MIT license headers under `migration-harness/cpp/probes/transcendental/coremath/`.

## Acknowledgements

- The [QuantLib](https://www.quantlib.org/) team — foundational C++ codebase and ongoing reference.
- The [CORE-MATH](https://core-math.gitlabpages.inria.fr/) project (Sibidanov et al., Inria) — provably correctly-rounded transcendental algorithms.
- The original JQuantLib contributors (~2007–2021) whose work forms the starting baseline.
