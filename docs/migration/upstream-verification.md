# Re-verification of the ten upstream C++ v1.43 defect claims

**Date:** 2026-08-11
**Checked against:** the fork at `~/Projects/CLionProjects/QuantLib`, on `master`
at `v1.43-213-g5608e3543` (`origin` = `jlmoya/QuantLib`, `upstream` =
`lballabio/QuantLib`).

## Why this pass happened

The drafted `upstream-1-ziggurat.md` claimed the wedge branch was *provably
unreachable*. Checking it before filing showed the claim rested on an off-by-one
in the `normF` index and was false — the branch is reachable, just starved ~30×.
Since one write-up had a load-bearing error, every remaining claim was re-checked
against the current tree rather than trusted.

## Staleness check

None of the nine greppable defect files has been touched since the `v1.43` tag —
`git log v1.43..master -- <file>` is empty for every one:

`zigguratgaussianrng.hpp`, `assetswap.cpp`, `lineartsrpricer.cpp`,
`rangeaccrual.cpp`, `pearsonspreadengine.cpp`, `lagrangeinterpolation.hpp`,
`garch.cpp`, `lmfixedvolmodel.cpp`, `makeyoyinflationcapfloor.hpp`.

So nothing here has been fixed upstream in the intervening 213 commits.

## Results

| # | Claim | Verdict |
|---|---|---|
| 1 | `zigguratgaussianrng.hpp:106` wedge test unreachable | **Real, misdescribed** — reachable; 1.79% vs 54.52% acceptance (~30×). **Fixed**, see below |
| 2 | `assetswap.cpp` `setupArguments` unconditional null deref | **Misdescribed** — a null check exists; the real hazard is elsewhere |
| 3 | `LinearTsrPricer` `PriceThreshold` inert | **Confirmed** (both halves) |
| 4 | `rangeaccrual.cpp` `operator Leg()` returns `2n`, first `n` null | **Confirmed** |
| 5 | `pearsonspreadengine.cpp:73-75` call intrinsic for puts | **Confirmed** |
| 6 | `lagrangeinterpolation.hpp` NaN at a node with abscissa 0 | **Confirmed** |
| 7 | `garch.cpp` `initialGuess2` scores uninitialised `alpha` | **Confirmed** |
| 8 | `lmfixedvolmodel` out-of-bounds read | **Confirmed, conditional** |
| 9 | `FdCIRVanillaEngine` + `ModifiedHundsdorfer` unstable | **Confirmed, reproduced** |
| 10 | `MakeYoYInflationCapFloor::withFirstCapletExcluded()` undefined | **Confirmed** |

### 1 — Ziggurat: real, but not as described

Full corrected analysis in `upstream-1-ziggurat.md`. Fixed on the fork branch
`fix/ziggurat-wedge-acceptance` (`20f398974`) with a deterministic regression
test. Not pushed.

### 2 — AssetSwap: the stated defect is not there

`assetswap.cpp:194-197` does check:

```cpp
auto* arguments = dynamic_cast<AssetSwap::arguments*>(args);
if (arguments == nullptr) // it's a swap engine...
    return;
```

So "unconditional null dereference" of the arguments is wrong.

There *is* a related hazard, but it is a different one and it is conditional:
both coupon loops use `ext::dynamic_pointer_cast` and dereference the result
without a null check —

- `assetswap.cpp:206-211`, cast to `FixedRateCoupon`, then `coupon->date()`
- `assetswap.cpp:215-221`, cast to `FloatingRateCoupon`, then
  `coupon->accrualStartDate()`

If a leg ever holds a coupon of another type the cast yields null and the next
line is UB. Whether that is a bug or an undocumented precondition is a judgment
call. **Not fileable as written**; needs re-drafting or dropping.

### 3 — LinearTsrPricer `PriceThreshold`

Both halves hold.

- `lineartsrpricer.cpp:310` passes `settings_.vegaRatio_` to `strikeFromPrice`,
  where `settings_.priceThreshold_` is intended. Both fields exist —
  `lineartsrpricer.hpp:153` (`vegaRatio_ = 0.01`) and `:154`
  (`priceThreshold_ = 1.0E-8`).
- `strikeFromPrice` (`:262-267`) wraps its `Brent::solve` in a blanket
  `catch (...)` that leaves `k` at the bound assigned before the try. When that
  happens `bound` equals the adjusted bound, so
  `upper = std::min(bound, adjustedUpperBound_)` collapses onto exactly the
  `RateBound` case.

Caveat: "the solve *always* throws" is not established — it depends on whether a
strike with the given price exists inside the bracket. State it as "on solver
failure the strategy silently degrades to `RateBound`, and the wrong field makes
failure far more likely".

### 4 — RangeAccrualLeg::operator Leg()

`rangeaccrual.cpp:643` constructs `Leg leg(n)` — `n` default-constructed (null)
`shared_ptr<CashFlow>` — and the loop then `push_back`s `n` more. The result has
`2n` entries whose first `n` are null. Should be `Leg leg; leg.reserve(n);`.

### 5 — PearsonSpreadEngine

Confirmed as written in `upstream-2-pearson.md`; that document was re-read
against the source and is accurate. The early return when
`effectiveStrike <= 0.0` computes `max(0, F1cond - effectiveStrike)` and ignores
`optionType`, while the very next branch honours it via
`PlainVanillaPayoff(optionType, effectiveStrike)`.

### 6 — LagrangeInterpolation NaN at abscissa 0

`lagrangeinterpolation.hpp`, in `_value`:

```cpp
const Real eps = 10*QL_EPSILON*std::abs(x);
...
if (iter != this->xEnd_ && *iter - x < eps) { return yBegin[...]; }
```

At `x == 0` exactly, `eps == 0`, so the node-snap test becomes `0 < 0` and fails.
Control falls into the barycentric loop where `x - xBegin_[i] == 0` for that
node, `alpha` is infinite, and `n / d` is `inf/inf` → NaN.

### 7 — Garch initialGuess2

`Array(Size)` does **not** value-initialise — `array.hpp:267-268` is
`data_(size != 0U ? new Real[size] : nullptr)`, and `Real` is a POD, so
`Array opt2(3)` holds indeterminate values.

In `initialGuess2`, `beta` and `omega` are assigned unconditionally, but `alpha`
is assigned only inside `if (constraints.test(guess))` within a `try` whose
`catch` comments "failed -- returning initial values". On either failure path
`opt2[1]` is still indeterminate, and the caller immediately does
`fCost2 = cost.value(opt2)` and then picks `fCost1 <= fCost2 ? opt1 : opt2`.
So an uninitialised value is scored and can decide the branch.

### 8 — LmFixedVolatilityModel out-of-bounds

`lmfixedvolmodel.cpp:66`, in the scalar overload:

```cpp
const Size ti = std::upper_bound(...) - startTimes_.begin() - 1;
return volatilities_[i-ti];
```

`Size` is unsigned, so any call with `i < ti` wraps to a huge index and reads out
of bounds. The `Array` overload above it is safe because its loop starts at `ti`.
Reachability depends on callers passing `i < ti`; state it as conditional.

### 9 — FdCIRVanillaEngine + ModifiedHundsdorfer

Reproduced. Setup mirrors `test-suite/fdcir.cpp` (European put, S=36, K=40,
r=0.06, vol=0.20, 1y; CIR speed 1.2188, sigma 0.02438, level 0.0183, r0 0.06,
lambda -0.5726), sweeping `rho` and `tGrid` with `xGrid=100, vGrid=50,
dampingSteps=0`:

```
rho \ tGrid          10          25          50         100
   0.00789       4.2753     4.27518     4.27513      4.2751
   0.10000      4.29716     4.29701     4.29696     4.29693
   0.25000      4.33245     4.30194  -2.7523e+07   1.032e+20
   0.50000      4.72268  -2.719e+14  -2.051e+38   2.352e+81
  -0.25000      4.21345     3.19072  -3.817e+08   2.683e+21
  -0.50000      5.65856   1.815e+15  -3.588e+39  -7.348e+82
```

Stable and converging for `|rho| <= 0.1`; diverges for `|rho| >= 0.25`, and
**refining** the time grid makes it worse. Symmetric in the sign of `rho`. The
exact figures differ from the original note (different parameters) but the
qualitative claim reproduces exactly.

### 10 — MakeYoYInflationCapFloor::withFirstCapletExcluded()

Declared at `makeyoyinflationcapfloor.hpp:49`; `grep -rn withFirstCapletExcluded
ql/` returns that single line. No definition anywhere, so a caller cannot link.

## What this means for filing

- **Ready as drafted:** 5 (pearson).
- **Ready, now corrected:** 1 (ziggurat), with fix + test already committed on
  the fork branch.
- **Ready, need short write-ups:** 3, 4, 6, 7, 8, 9, 10. All verified above; 4,
  6, 9 and 10 are the cleanest (unambiguous, no statistical argument, small
  repros).
- **Not fileable as written:** 2 (assetswap). Either re-draft around the
  unchecked `dynamic_pointer_cast` or drop it.

Nothing has been pushed or filed. Both remain gated on the user's go-ahead.
