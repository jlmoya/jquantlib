# L3 instruments + pricingengines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Port 76 instruments + pricingengines classes from C++ v1.42.1 to Java.

**Architecture:** Largest L-layer. Pricingengines wire models + processes + numerical schemes. Instruments are payoff and exercise variants. Builds on L1 (math primitives) + L2 (termstructures + indexes).

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope

### instruments (36)
- **StickyRatchet family** — `stickyratchet.hpp`: DoubleStickyRatchetPayoff, RatchetPayoff, StickyPayoff, RatchetMaxPayoff, RatchetMinPayoff, StickyMaxPayoff, StickyMinPayoff, AcyclicVisitor (visitor base — check if Java equivalent already exists)
- Remaining ~28: per audit, mostly payoff and exercise variants — implementer to enumerate and decide port vs SKIP

### pricingengines (40)
- **Swaption family**: G2SwaptionEngine, BlackStyleSwaptionEngine, Black76Spec, BachelierSpec
- **Basket Make-factory family**: MakeMCAmericanBasketEngine, MakeMCEuropeanBasketEngine
- **MCLookbackEngine** (lookback/mclookbackengine.hpp)
- **LatticeShortRateModelEngine** (latticeshortratemodelengine.hpp)
- Remaining ~33: implementer enumerates from full audit

---

## Clusters

- **L3-A**: instruments/stickyratchet family (8-10 classes, well-isolated) — pilot
- **L3-B**: pricingengines/swaption (G2/BlackStyle/Black76Spec/BachelierSpec — 4 classes)
- **L3-C**: pricingengines/basket Make-factories + MCLookbackEngine (3 classes)
- **L3-D**: instruments remainder (28 classes; implementer audits per file)
- **L3-E**: pricingengines remainder (33 classes; implementer audits per file)

---

## Per-class TDD template

Standard 5-step cycle: Read C++ → failing test → verify fail → implement → verify pass → commit with -s.

Cross-validation via C++ probes or existing test-suite tests.

JDK 25 idioms: records for spec DTOs (Black76Spec/BachelierSpec are perfect), sealed payoff hierarchies, switch expressions, var.

---

## Final sweep

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest='*StickyRatchet*Test,*SwaptionEngine*Test,*Basket*Test,*Lookback*Test'
# Expected: green; full regression
```

## Definition of done

- 76 entries ported or SKIP-documented
- Full suite still 3270+/0/0
- Tag `jquantlib-phase2-L3-complete`
