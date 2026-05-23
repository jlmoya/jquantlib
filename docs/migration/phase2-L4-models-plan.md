# L4 models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Port 11 models classes from C++ v1.42.1 to Java (smallest L-layer).

**Architecture:** Models wire shortrate / equity / marketmodels. Builds on L1+L2+L3.

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope (11)

- **FellerConstraint** — `models/equity/hestonmodel.hpp` — Feller condition for Heston (`2κθ ≥ σ²`)
- **HistoricalForwardRatesAnalysis** — `models/marketmodels/historicalforwardratesanalysis.hpp`
- **HistoricalRatesAnalysis** — `models/marketmodels/historicalratesanalysis.hpp`
- **SobolBrownianGeneratorBase** — `models/marketmodels/browniangenerators/sobolbrowniangenerator.hpp`
- **Burley2020SobolBrownianGenerator** + **Burley2020SobolBrownianGeneratorFactory** — Sobol-based Brownian generator using Burley2020 scrambling (depends on L1-C Burley2020SobolBrownianBridgeRsg)
- **CachedSwapKey + CachedSwapKeyHasher** — `models/shortrate/onefactormodels/gaussian1dmodel.hpp` — Gaussian1D model cache key (use JDK 25 record)
- Remaining: implementer audits

---

## Clusters

- **L4-A**: FellerConstraint + Sobol Brownian generator family (existing L1-C Burley2020 RNG infrastructure available)
- **L4-B**: HistoricalForwardRatesAnalysis + HistoricalRatesAnalysis
- **L4-C**: Gaussian1D cache structures (CachedSwapKey as record)

---

## Per-class TDD template

Standard cycle. JDK 25 records for CachedSwapKey/SwapKeyHasher (perfect fit).

---

## Definition of done

- 11 entries ported
- Full suite still 3270+/0/0
- Tag `jquantlib-phase2-L4-complete`
