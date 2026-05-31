# Parity-safe performance optimization — observer registry

**Date:** 2026-05-31
**Commit:** `5baabea2` (direct-to-`main`, on top of functional-coverage milestone `0646ee75`)
**Scope:** `org.jquantlib.util.DefaultObservable` + `org.jquantlib.util.WeakReferenceObservable`
**Outcome:** `LiborMarketModelTest` **537.3 s → 5.94 s (~90×)**; full suite **~21 min → ~12 min**; all 3678 tests **bit-identical**, 0 regressions.

---

## Why this thread exists

The full C++ v1.42.1 `ql/` surface functional-coverage milestone (tag `jquantlib-cpp-surface-functional-coverage`) was complete. The only remaining *optional* thread the owner authorized was **parity-safe performance optimization of our own code**: optimize the same algorithm with **no floating-point reordering**, so results stay bit-identical and the cross-validation probes stay green *by construction*.

It was **benchmark-gated** by the owner: keep a change only if **(a)** the full 3678-test suite stays bit-green **AND (b)** it is measurably faster than baseline; otherwise revert. Fully reversible; the evidence decides.

This is distinct from — and explicitly does **not** touch — the numerics. No external library was adopted (those are evidence-proven to fail our C++-internal-state probes; see [`full-cpp-surface-completion.md`](full-cpp-surface-completion.md)), and no test was re-baselined away from C++.

## The finding — profiling overturned the hypothesis

`LiborMarketModelTest` ran for ~537 s and was by far the slowest test in the suite. The natural hypothesis was the LFM/LMM math — the covariance proxy and the Levenberg–Marquardt calibration loop.

**Profiling proved that wrong.** A JFR recording (JDK 25 built-in; `settings=profile`, execution + allocation sampling) showed:

- `java.lang.Object[]` was the **#1 allocation by a wide margin** (~40,000 samples), originating from `CopyOnWriteArrayList` mutations under `LazyObject.calculate` → `BlackSwaptionEngine.calculate`.
- `org.jquantlib.time.Date.clone()` was the #1 CPU *leaf* (46 self-samples) and #1 allocation leaf site (142 samples).
- The LFM covariance / LM optimizer math was nowhere near the top.

**Root cause.** `DefaultObservable` — the base class behind *every* `Observable` in the library (quotes, term structures, instruments, coupons) — stored its observers in a `java.util.concurrent.CopyOnWriteArrayList`. CoW copies its **entire backing array on every `add`/`remove`**. During calibration each `LazyObject.calculate()` re-registers observers, so observer lists grow and registration becomes **O(n²)** in the number of observers. Across thousands of calibration evaluations, the cumulative array-copy cost dominated the whole test.

## The fix (parity-safe)

Replace the `CopyOnWriteArrayList` with a plain `java.util.ArrayList`, and provide the iteration-safety CoW gave by a different mechanism:

- Every mutation/read of the list is `synchronized` on the observable instance.
- `notifyObservers(arg)` takes a **one-shot snapshot** (`observers.toArray()`) under the lock, then dispatches **outside** the lock. This preserves the exact CoW semantics that the dispatch pass is unaffected by concurrent mutation — *including* an observer that (de)registers inside its own `update()`.
- Dispatching outside the lock avoids deadlock (an observer's `update()` runs arbitrary code and may re-enter the observable) and avoids serialising unrelated work.
- `WeakReferenceObservable.compact()` / `deleteObserver()` use a new locked, in-place `removeObserversIf(Predicate)` — no live-view iterate-and-mutate (which a plain `ArrayList` rejects with `ConcurrentModificationException`), no per-call snapshot copy.

`add`/`remove` are now **O(1) amortised** instead of O(n) per call.

**Thread-safety is a tested requirement, not an assumption.** `ObservableTest.testMultiThreadingGlobalSettings` hammers a shared `Quote` from multiple threads and asserts no `ConcurrentModificationException`. An earlier *naive* ArrayList swap (no synchronization) failed exactly this test — which is why the final fix synchronizes all access and snapshots under the lock.

**Zero floating-point touched** → results are bit-identical → all cross-validation probes stay green by construction.

## Evidence — the benchmark gate

Both measurements **isolated, cold-start, no JFR** — identical conditions, so the comparison is apples-to-apples:

| Measurement | Before (`CopyOnWriteArrayList`) | After (synchronized `ArrayList` + snapshot) | Factor |
|---|---|---|---|
| `LiborMarketModelTest` (isolated) | 537.3 s | 5.94 s | **~90×** |
| Full suite (676 classes / 3678 tests) | green | green, **bit-identical** | 0 regressions |
| Full-suite wall-clock | ~21 min | ~12 min | ~1.7× |

The 90× was deliberately re-measured under the same cold-start isolation as the baseline (rather than relying on the warm-JIT full-suite figure) to rule out a measurement artifact. A separate check confirmed the original 537 s was *not* a JFR-profiling artifact: the JFR-instrumented run was 580 s, only ~8% above the clean 537 s baseline — the CoW really did cost ~531 s.

## Methodology (reusable)

1. **Profile before optimizing.** The initial hypothesis (the LMM math) was wrong; the profiler pinned the real cost (observer-registry array churn). Measure first, always.
2. **Parity-safe only.** Same algorithm, no FP reorder → bit-identical → probes green by construction. No tolerance touched, nothing re-baselined.
3. **Benchmark-gate keep-vs-revert** on a clean-baseline-vs-after comparison under *identical* conditions. Keep only if bit-green **and** faster; else revert.

## Remaining candidates (not pursued; pursue iff asked)

The same JFR profile flagged further parity-safe targets — notably `org.jquantlib.time.Date.clone()` (the #1 CPU leaf). These remain open. The methodology above is ready to repeat. Nothing is pending unless the owner asks.
