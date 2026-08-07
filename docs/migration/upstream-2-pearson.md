# `PearsonSpreadEngine` returns the call intrinsic for puts when the effective strike is non-positive

**File:** `ql/pricingengines/basket/pearsonspreadengine.cpp:73-75`
**Affects:** v1.43 (engine introduced in that release). Verified against
`v1.42.1-208-g5111c3a14`.

## The defect

```cpp
const Real effectiveStrike = f2z + strike;

if (effectiveStrike <= 0.0)
    return phi(z) * std::max(0.0, f1 * std::exp(rho_ * sigma1 * z
               - 0.5 * rho_ * rho_ * variance1) - effectiveStrike);
```

The early-return branch computes `max(0, F1cond - effectiveStrike)` — the **call**
intrinsic — and returns it regardless of `optionType`. Every other path through
the integrand respects the option type, via `PlainVanillaPayoff(optionType, ...)`
handed to `BlackCalculator` a few lines below.

## Consequence

For a put, when the conditional effective strike goes non-positive the correct
integrand contribution is

```
max(0, effectiveStrike - F1cond) = 0     (since effectiveStrike <= 0 < F1cond)
```

but the branch instead contributes `max(0, F1cond - effectiveStrike)`, which is
strictly positive and grows as the strike becomes more negative. A put on a spread
with a sufficiently negative strike therefore picks up value that should be zero,
and put-call parity breaks.

Calls are unaffected — for a call the branch happens to compute the right thing,
which is presumably how it survived.

## Why the test suite does not catch it

The upstream test uses strike 5. `effectiveStrike = f2z + strike` only goes
non-positive when the strike is negative enough to dominate `f2z` over the
integration range, so the branch is never entered by the existing cases. Spread
options with negative strikes are ordinary — that is much of the point of a spread
option — so this is reachable in normal use.

## Fix

The branch needs to respect `optionType`, e.g.

```diff
-    if (effectiveStrike <= 0.0)
-        return phi(z) * std::max(0.0, f1 * std::exp(rho_ * sigma1 * z
-                   - 0.5 * rho_ * rho_ * variance1) - effectiveStrike);
+    if (effectiveStrike <= 0.0) {
+        const Real f1Cond = f1 * std::exp(rho_ * sigma1 * z
+                                          - 0.5 * rho_ * rho_ * variance1);
+        return phi(z) * PlainVanillaPayoff(optionType, effectiveStrike)(f1Cond);
+    }
```

which reduces to the current expression for a call and to zero for a put.

## Suggested regression test

Put-call parity on a spread option with a strike negative enough to enter the
branch — e.g. strike -10 and strike -30 on the existing test's market — comparing
the put against `call - discounted(F1 - F2 - K)`.

## Note

Found while porting the engine to a Java/Python port of QuantLib. Both ports pin
the current behaviour with dedicated probe cases (`pearson_a_put_km10_*`,
`pearson_a_put_km30_*`) because the C++ is the reference, and will follow whatever
is decided here.
