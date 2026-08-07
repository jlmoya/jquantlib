# `ZigguratGaussianRng` wedge test never accepts — misplaced parenthesis

**File:** `ql/math/randomnumbers/zigguratgaussianrng.hpp:106`
**Affects:** every release that ships `ZigguratGaussianRng` (added 2024). Verified
against `v1.42.1-208-g5111c3a14`; the file is unchanged between v1.42.1 and v1.43.

## The defect

```cpp
if (normF(i + 1) + (normF(i) - normF(i + 1) * uint64Generator_.nextReal()) < pdf(x)) {
    return x;
}
```

The closing parenthesis is one term too early. This evaluates

```
f[i+1] + f[i] - f[i+1]·U
```

The Marsaglia–Tsang wedge acceptance test is

```
f[i+1] + (f[i] - f[i+1])·U
```

i.e. the uniform draw should scale the *whole* difference `f[i] - f[i+1]`, not
just `f[i+1]`.

## Why it is not merely inaccurate — the branch is unreachable

Solve the written expression for acceptance:

```
f[i+1] + f[i] - f[i+1]·U  <  pdf(x)
                       U  >  (f[i+1] + f[i] - pdf(x)) / f[i+1]
```

Since `U ∈ [0,1)`, acceptance requires

```
(f[i+1] + f[i] - pdf(x)) / f[i+1]  <  1
                   f[i] - pdf(x)   <  0
                            f[i]   <  pdf(x)
```

But control only reaches this line when `|x| >= normX(i+1)`, which by
construction of the ziggurat means `pdf(x) <= f[i]`. So `f[i] < pdf(x)` is never
satisfied and **the test never accepts, for any layer, for any draw.**

Brute-force confirmation over the whole admissible parameter space
(`f[i+1] < f[i]`, `pdf(x) ∈ [f[i+1], f[i]]`, `U ∈ [0,1)`), 2,000,000 samples:

```
accepts (as written)   0        (0.0000%)
accepts (correct)      999819   (49.9909%)
```

## Consequence

The wedge branch is dead code. Every draw that lands in a wedge is rejected and
the generator loops to draw a fresh `(i, u)` pair. Accepted samples therefore come
only from the rectangle fast path (`|x| < normX(i+1)`) and from `zeroCase` in the
tail — the wedge regions between `normX(i+1)` and `normX(i)` are never sampled.

The generated stream is consequently **not standard normal**: it is missing the
wedge mass at every layer boundary, renormalised by rejection. It also costs extra
iterations per sample. Anyone using `ZigguratGaussianRng` for simulation is
affected.

The existing tests do not catch this because they check moments and aggregate
statistics, where the missing wedge mass is small; the defect is structural rather
than large in any single moment.

## Fix

```diff
-            if (normF(i + 1) + (normF(i) - normF(i + 1) * uint64Generator_.nextReal()) < pdf(x)) {
+            if (normF(i + 1) + (normF(i) - normF(i + 1)) * uint64Generator_.nextReal() < pdf(x)) {
                 return x;
             }
```

## Suggested regression test

A test that would have caught it: assert that the wedge branch is reachable at
all — e.g. instrument the acceptance count, or compare a Kolmogorov–Smirnov
statistic against `InverseCumulativeNormal`-transformed uniforms at a sample size
where the missing wedge mass exceeds the KS critical value.

## Note

Found while porting `ZigguratGaussianRng` to a Java/Python port of QuantLib. Both
ports currently reproduce the behaviour bug-for-bug and pin it with a test,
because the C++ is the reference; both will follow whatever is decided here.
