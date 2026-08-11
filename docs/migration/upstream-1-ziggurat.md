# `ZigguratGaussianRng` wedge acceptance test is wrong — misplaced parenthesis

**File:** `ql/math/randomnumbers/zigguratgaussianrng.hpp:106`
**Affects:** every release that ships `ZigguratGaussianRng` (added 2024). The file
is unchanged between v1.42.1, v1.43 and current master (`v1.43-213-g5608e3543`);
`git log v1.43..master` on it is empty.

> **Revision, 2026-08-11.** An earlier draft of this document claimed the wedge
> branch was **provably unreachable** ("0 acceptances in 2,000,000 draws"). That
> was wrong — see [Correction](#correction-to-the-earlier-draft) at the end. The
> defect is real, but it starves the branch rather than killing it.

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

## Consequence — the wedge is starved

Control reaches this line when `|x| >= normX(i+1)` and `i >= 1`, i.e. the draw
landed in the wedge between `normX(i+1)` and `normX(i)`.

Monte Carlo over the generator's own sampling measure, counting only draws that
actually reach the wedge test (2,000,000 of them):

```
shipped accepts :    35706  ( 1.7853%)
correct accepts :  1090327  (54.5164%)
```

So the wedge accepts roughly **30× too rarely**. Rejected draws send the loop
back for a fresh `(i, u)` pair, so the emitted stream under-weights the wedge
region at every layer boundary and costs extra iterations per sample.

## Why the existing tests do not catch it

`testStatisticsOfNextReal` checks mean, variance, skewness and kurtosis over
10,000,000 draws and passes both before and after the fix.

A Kolmogorov–Smirnov test against the standard normal CDF does not separate them
either at practical sample sizes — at `n = 1,000,000`:

```
before fix : D = 0.00164276
after fix  : D = 0.00135042
critical   = 0.00194900   (99.9%)
```

Both are below the critical value. (The pre-fix figure is about 1.9× the
`0.86/sqrt(n)` expected of a correct sampler, so there is real signal; it would
reject at larger `n`. But KS is not a practical regression guard here.)

## Fix

```diff
-            if (normF(i + 1) + (normF(i) - normF(i + 1) * uint64Generator_.nextReal()) < pdf(x)) {
+            if (normF(i + 1) + (normF(i) - normF(i + 1)) * uint64Generator_.nextReal() < pdf(x)) {
                 return x;
             }
```

## Regression test

Because the statistical signal is weak, the test is **deterministic**.
`ZigguratGaussianRng` is a template over its uniform source and documents the
interface it needs, so a scripted RNG can drive one exact draw.

Layer 1 spans `normX(2) = 3.449278` to `normX(1) = 3.654153`. Take `x = 3.5`,
comfortably inside that band, and feed the wedge test `u = 0.5`:

| | acceptance threshold | vs `pdf(3.5) = 0.002187491` | outcome |
|---|---|---|---|
| correct | 0.001934679 | `<` by 2.5e-04 | **accepts** |
| shipped | 0.002564822 | `>` by 3.8e-04 | **rejects** |

The generator must return 3.5. The shipped code instead falls through to the
next scripted draw (layer 200, `x = 0.10350`), which the test distinguishes by
value. Implemented in `test-suite/zigguratgaussian.cpp` as
`testWedgeDrawIsAccepted`; it fails before the fix and passes after.

## Correction to the earlier draft

The earlier draft argued:

> acceptance requires `f[i] < pdf(x)`. But control only reaches this line when
> `|x| >= normX(i+1)`, which by construction of the ziggurat means
> `pdf(x) <= f[i]`. So the test never accepts.

The algebra reducing acceptance to `f[i] < pdf(x)` is correct. The table claim
after it is not. Verified numerically against the shipped tables:

- `normF(i) == pdf(normX(i))` exactly (max deviation `2.8e-17` over all `i`).
- `normX` is **decreasing**, so `normF` is **increasing**:
  `X(93) = 1.784034 > X(94) = 1.776464`, `F(93) = 0.203643 < F(94) = 0.206405`.
- In a wedge, `X(i+1) <= |x| < X(i)`, so `pdf(x)` lies in `(F(i), F(i+1)]` —
  that is, `pdf(x) > F(i)`, the **opposite** of the draft's assertion. True at
  4845 / 4845 sampled wedge points.

Since `f[i] < pdf(x)` is essentially always true in a wedge, acceptance is
possible; it just needs `U` very close to 1, which is why the rate collapses to
1.79% instead of to zero.

## Note

Found while porting `ZigguratGaussianRng` to a Java/Python port of QuantLib.
Both ports currently reproduce the behaviour bug-for-bug and pin it with a test,
because the C++ is the reference; both will follow whatever is decided here.
