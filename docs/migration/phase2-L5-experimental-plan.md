# L5 experimental Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Port 73 experimental/ classes from C++ v1.42.1 to Java, OR document as
"deferred — no Java consumer / experimental in C++ too" with rationale.

**Architecture:** Lowest priority. C++ experimental/ is the staging ground for
work not yet promoted to mainline. Java equivalents may not need full coverage.

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope (73)

Per audit, dominated by:
- **experimental/credit**: RandomLM, Root, RecursiveLossModel, BaseCorrelationLossModel, ESFIntegrator, AffineHazardRate, simEvent (×2)
- Many others across experimental/{barrier, asian, copulas, default, exoticoptions, finitedifferences, etc.}

---

## Triage approach

The implementer should:
1. For each experimental/ entry, check if Java has any caller using it
2. If Java has NO caller AND C++ classifies as experimental → SKIP with rationale
3. If Java has callers but missing the class → port
4. Cluster by sub-package for batched commits

---

## Clusters

- **L5-A**: experimental/credit (RandomLM, RecursiveLossModel, etc.)
- **L5-B**: experimental/finitedifferences + experimental/asian + experimental/barrier
- **L5-C**: experimental/exoticoptions + experimental/copulas
- **L5-D**: experimental/default + experimental/processes
- **L5-E**: remaining experimental subpackages

---

## Definition of done

- All 73 entries triaged: ported OR documented SKIP with no-Java-caller rationale
- Full suite still 3270+/0/0
- Tag `jquantlib-phase2-L5-complete`
- Phase 2 closure declared after L5: tag `jquantlib-migration-complete`
