# Phase 2 (forward closure) — Design

**Date:** 2026-05-20
**Author:** Claude (controller), under direct user request after Phase 1 Path A closure
**Prereq:** Phase 1 Path A closed at tag `jquantlib-phase1-true-closure` @ `d56d5f87`
**Status:** **DRAFT — design only**, no execution scheduled yet

---

## Naming note

CLAUDE.md states "Phase 2 (filling gaps beyond the existing 61 packages) is
explicitly deferred; not designed yet." That statement is **stale**: phases
2a–2v have shipped (inflation subsystem, JQuantMath, Gaussian1D family, Fdm
framework, etc.), and Phase 3a–3j shipped the credit subsystem and most of
marketmodels. The "Phase 2" name is taken; this document uses **"Phase 2
forward closure"** for the remaining gap.

If you prefer a different name (e.g., `phase6`, `phaseZ`, `gap-closure`),
rename before kicking off.

---

## Scope audit (as of `d56d5f87`)

| Metric | C++ v1.42.1 | Java | Delta |
|---|---|---|---|
| `ql/` packages (directories) | 114 | 128 | +14 Java (impl-detail subpkgs) |
| Production files (`.hpp`+`.cpp`) | 2,389 | 1,764 (.java) | **−625** |
| Test-suite `.cpp` files | 181 | 581 (.java) | +400 (Java split aggressively per class) |
| `@Test` methods | ~3,400 (BOOST cases) | **3,344+** | ~−56 missing-by-name |
| Suite passing | n/a | **3,221 / 0 / 0 / 20** | clean |

The **−625 file delta** is the headline Phase 2 forward-closure scope. It
breaks down roughly as:
- Intentional Java consolidations (multiple small C++ headers → 1 Java class). Hard to count exactly without per-file mapping. Probably 200-300 of the 625.
- Genuinely missing classes — the real Phase 2 backlog. Probably 300-400 files of work, weighted toward `ql/experimental/` and the long tail of niche engines / utilities.

---

## Layering proposal (topological, leaf-first)

L0. **Audit + classify** the 625-file delta into:
   - **CONSOLIDATED** (Java covers via different class structure — annotate, no port)
   - **MISSING-NEEDED** (Java users would call this; port required)
   - **MISSING-DEFERRED** (niche experimental, no Java consumer; document as carve-out)

L1. **Math + utility primitives** (leaves) — anything in `ql/math/`, `ql/utilities/`, `ql/time/` not yet ported. Estimated 30-60 files.

L2. **Term-structures + indexes** (next layer up) — gaps in `ql/termstructures/`, `ql/indexes/`. Estimated 40-80 files.

L3. **Instruments + pricingengines** (mid layer) — gaps in `ql/instruments/`, `ql/pricingengines/`. Estimated 100-150 files. Largest layer.

L4. **Models** (depends on L1–L3) — gaps in `ql/models/`. Mostly closed in 3a–3j; estimated 20-40 residual.

L5. **Experimental** (top layer, niche) — gaps in `ql/experimental/`. Estimated 50-100 files. Lowest priority unless a specific Java user needs it.

L6. **Test-suite parity** — the remaining BOOST_AUTO_TEST_CASE bodies + sub-test blocks not yet ported. After Phase 1.1 closes, this should be ~10–20 stragglers.

---

## Execution model

Same as Phase 1 Path A — 5 git worktrees in parallel, layer-by-layer
dispatch. Each layer ends with `mvn -pl jquantlib test` green + a commit
tag (`jquantlib-phase2-fwd-L<N>-complete`).

Per-task TDD: probe-before-port for cross-validated numeric changes,
tolerance tier discipline (exact/tight/loose), one stub = one commit.

---

## Definition of done

1. **D5 dimension** → GREEN with **zero** missing-by-name carve-outs.
2. Suite passes `mvn -pl jquantlib test` clean (failures/errors = 0).
3. Each layer landed has a commit tag.
4. Audit doc (`docs/migration/phase2-fwd-audit.md`) lists every C++ file and
   its disposition (CONSOLIDATED / PORTED / DEFERRED-WITH-RATIONALE).
5. Final tag: `jquantlib-phase2-fwd-complete` with completion doc.

---

## Risk register

| Risk | Mitigation |
|---|---|
| L0 audit explodes scope beyond estimate | Time-box L0 to 1 session. Land the classification doc even if not 100% complete; iterate. |
| Java/C++ idiom mismatches surface in L5 experimental (lots of template metaprogramming) | Accept partial ports + DEFERRED-WITH-RATIONALE for genuinely unidiomatic-in-Java code. |
| Parallel-agent merge conflicts cluster on shared base classes (e.g., LazyObject, Quote) | Same playbook as Phase 1 Path A — A7-C/A7-D RelativeDateRateHelper conflict was resolved cleanly. |
| Wall time | Estimated 5-15 sessions of agent dispatch + landing. Not a single-day effort. |

---

## Next step

User decides:
- **Execute L0 now** (audit + classification — 1 session, doc-only output)
- **Hold** for review of this design first
- **Re-scope** the Phase 2 name or layering before execution
