/* CORE-MATH dint64_t extended-precision support — extracted from
   src/binary64/sin/sin.c (Inria; canonical version verified bit-identical
   against /tmp/coremath-fetch/sin_inria.c at HTTP fetch on 2026-04-28).

   The dint64_t type and all associated arithmetic / conversion primitives
   live inline in CORE-MATH's sin.c and cos.c (per their "code copied from
   dint.h and pow.[ch]" header comment). This file consolidates that block
   into a standalone header so the dint64_probe can include it without
   pulling in the full sin/cos kernel — and so future probes (log, pow, …)
   can reuse the same support primitives.

   Source: CORE-MATH project, https://core-math.gitlabpages.inria.fr/
   Copyright (c) 2022-2025 Paul Zimmermann and Tom Hubrecht.
   MIT License — see https://gitlab.inria.fr/core-math/core-math/-/blob/master/COPYING

   Phase 2i.5 WI-1.0 — pure-Java Dint64 port cross-validates against this
   reference. Do NOT modify the algorithms here; bit-exactness with this
   header is the test contract.
*/

#ifndef JQUANTLIB_HARNESS_COREMATH_DINT_H
#define JQUANTLIB_HARNESS_COREMATH_DINT_H

#include <stdint.h>
#include <inttypes.h>
#include <fenv.h> /* fegetround, FE_TONEAREST, FE_DOWNWARD, FE_UPWARD */

#pragma STDC FENV_ACCESS ON

#if (defined(__clang__) && __clang_major__ >= 14) || (defined(__GNUC__) && __GNUC__ >= 14 && __BITINT_MAXWIDTH__ && __BITINT_MAXWIDTH__ >= 128)
typedef unsigned _BitInt(128) u128;
#else
typedef unsigned __int128 u128;
#endif

/* The dint64_t structure represents a 128-bit number:
   (-1)^sgn*(hi/2^64+lo/2^128)*2^ex */
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
typedef union {
  struct {
    u128 r;
    int64_t _ex;
    uint64_t _sgn;
  };
  struct {
    uint64_t lo;
    uint64_t hi;
    int64_t ex;
    uint64_t sgn;
  };
} dint64_t;
#else
typedef union {
  struct {
    u128 r;
    int64_t _ex;
    uint64_t _sgn;
  };
  struct {
    uint64_t hi;
    uint64_t lo;
    int64_t ex;
    uint64_t sgn;
  };
} dint64_t;
#endif

#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
typedef union {
  u128 r;
  struct {
    uint64_t l;
    uint64_t h;
  };
} uint128_t;
#else
typedef union {
  u128 r;
  struct {
    uint64_t h;
    uint64_t l;
  };
} uint128_t;
#endif

typedef union {
  double f;
  uint64_t u;
} f64_u;

/* Extract both the mantissa and exponent of a double. */
static inline void fast_extract (int64_t *e, uint64_t *m, double x) {
  f64_u _x = {.f = x};

  *e = (_x.u >> 52) & 0x7ff;
  *m = (_x.u & (~0ull >> 12)) + (*e ? (1ull << 52) : 0);
  *e = *e - 0x3fe;
}

/* Return non-zero if a = 0. */
static inline int
dint_zero_p (const dint64_t *a)
{
  return a->hi == 0;
}

static inline int cmp(int64_t a, int64_t b) { return (a > b) - (a < b); }

static inline int cmpu128 (u128 a, u128 b) { return (a > b) - (a < b); }

/* ZERO is a dint64_t representation of 0, which ensures that
   dint_tod(ZERO) = 0. */
static const dint64_t ZERO = {.hi = 0x0, .lo = 0x0, .ex = -1076, .sgn = 0x0};

/* MAGIC is a dint64_t representation of 1/2^11 (kept in the support header
   for parity with the canonical CORE-MATH source even though no probe uses
   it directly). */
static const dint64_t MAGIC = {.hi = 0x8000000000000000, .lo = 0x0, .ex = -10, .sgn = 0x0};

/* Compare the absolute values of a and b.
   Return -1 if |a| < |b|, 0 if |a| = |b|, +1 if |a| > |b|. */
static inline signed char
cmp_dint_abs (const dint64_t *a, const dint64_t *b) {
  if (dint_zero_p (a))
    return dint_zero_p (b) ? 0 : -1;
  if (dint_zero_p (b))
    return +1;
  char c1 = cmp (a->ex, b->ex);
  return c1 ? c1 : cmpu128 (a->r, b->r);
}

/* Copy a dint64_t value. */
static inline void cp_dint(dint64_t *r, const dint64_t *a) {
  r->ex = a->ex;
  r->r = a->r;
  r->sgn = a->sgn;
}

/* Add two dint64_t values, with error bounded by 2 ulps (ulp_128)
   (more precisely 1 ulp when a and b have same sign, 2 ulps otherwise).
   Moreover, when Sterbenz theorem applies, i.e., |b| <= |a| <= 2|b|
   and a,b are of different signs, there is no error, i.e., r = a-b. */
static inline void
add_dint (dint64_t *r, const dint64_t *a, const dint64_t *b) {
  if (!(a->hi | a->lo)) {
    cp_dint (r, b);
    return;
  }

  switch (cmp_dint_abs (a, b)) {
  case 0:
    if (a->sgn ^ b->sgn) {
      cp_dint (r, &ZERO);
      return;
    }

    cp_dint (r, a);
    r->ex++;
    return;

  case -1: /* |A| < |B| */
    {
      /* swap operands */
      const dint64_t *tmp = a; a = b; b = tmp;
      break; /* fall through to |A| > |B| case */
    }
  }

  /* From now on, |A| > |B| thus a->ex >= b->ex. */

  u128 A = a->r, B = b->r;
  uint64_t k = a->ex - b->ex;

  if (k > 0) {
    B = (k < 128) ? B >> k : 0;
  }

  u128 C;
  unsigned char sgn = a->sgn;

  r->ex = a->ex; /* tentative exponent for the result */

  if (a->sgn ^ b->sgn) {
    C = A - B;
    uint64_t ch = C >> 64;
    uint64_t ex = ch ? __builtin_clzll(ch) : 64 + __builtin_clzll(C);
    if (ex > 0)
    {
      if (k == 1) /* Sterbenz case */
        C = (A << ex) - (b->r << (ex - 1));
      else
        C = (A << ex) - (B << ex);
      r->ex -= ex;
      ex = __builtin_clzll (C >> 64);
      /* Fall through with the code for ex = 0. */
    }
    C = C << ex;
    r->ex -= ex;
  } else {
    C = A + B;
    if (C < A)
    {
      C = ((u128) 1 << 127) | (C >> 1);
      r->ex ++;
    }
  }

  r->sgn = sgn;
  r->r = C;
}

/* Multiply two dint64_t numbers, with error bounded by 6 ulps
   on the 128-bit floating-point numbers. Overlap between r and a allowed. */
static inline void
mul_dint (dint64_t *r, const dint64_t *a, const dint64_t *b) {
  u128 bh = b->hi, bl = b->lo;

  /* compute the two middle terms */
  u128 m1 = (u128)(a->hi) * bl;
  u128 m2 = (u128)(a->lo) * bh;

  /* put the 128-bit product of the high terms in r */
  r->r = (u128)(a->hi) * bh;

  r->r += (m1 >> 64) + (m2 >> 64);

  /* Ensure that r->hi starts with a 1 */
  uint64_t ex = r->hi >> 63;
  r->r = r->r << (1 - ex);

  r->ex = a->ex + b->ex + ex - 1;
  r->sgn = a->sgn ^ b->sgn;
}

/* Multiply two dint64_t numbers, assuming the low part of b is zero,
   with error bounded by 2 ulps. */
static inline void
mul_dint_21 (dint64_t *r, const dint64_t *a, const dint64_t *b) {
  u128 bh = b->hi;
  u128 hi = (u128) (a->hi) * bh;
  u128 lo = (u128) (a->lo) * bh;

  r->r = hi;
  r->r += lo >> 64;

  uint64_t ex = r->hi >> 63;
  r->r = r->r << (1 - ex);

  r->ex = a->ex + b->ex + ex - 1;
  r->sgn = a->sgn ^ b->sgn;
}

/* Convert a non-zero double to the corresponding dint64_t value. */
static inline void dint_fromd (dint64_t *a, double b) {
  fast_extract (&a->ex, &a->hi, b);

  uint32_t t = __builtin_clzll (a->hi);

  a->sgn = b < 0.0;
  a->hi = a->hi << t;
  a->ex = a->ex - (t > 11 ? t - 12 : 0);
  a->lo = 0;
}

static inline void subnormalize_dint(dint64_t *a) {
  if (a->ex > -1023)
    return;

  uint64_t ex = -(1011 + a->ex);

  uint64_t hi = a->hi >> ex;
  uint64_t md = (a->hi >> (ex - 1)) & 0x1;
  uint64_t lo = (a->hi & (~0ull >> ex)) || a->lo;

  switch (fegetround()) {
  case FE_TONEAREST:
    hi += lo ? md : hi & md;
    break;
  case FE_DOWNWARD:
    hi += a->sgn & (md | lo);
    break;
  case FE_UPWARD:
    hi += (!a->sgn) & (md | lo);
    break;
  }

  a->hi = hi << ex;
  a->lo = 0;

  if (!a->hi) {
    a->ex++;
    a->hi = (1ull << 63);
  }
}

/* Convert a dint64_t value to a double. */
static inline double dint_tod(dint64_t *a) {
  subnormalize_dint (a);

  f64_u r = {.u = (a->hi >> 11) | (0x3ffll << 52)};

  double rd = 0.0;
  if ((a->hi >> 10) & 0x1)
    rd += 0x1p-53;

  if (a->hi & 0x3ff || a->lo)
    rd += 0x1p-54;

  if (a->sgn)
    rd = -rd;

  r.u = r.u | a->sgn << 63;
  r.f += rd;

  f64_u e;

  if (a->ex > -1022) { /* The result is a normal double */
    if (a->ex > 1024)
      if (a->ex == 1025) {
        r.f = r.f * 0x1p+1;
        e.f = 0x1p+1023;
      } else {
        r.f = 0x1.fffffffffffffp+1023;
        e.f = 0x1.fffffffffffffp+1023;
      }
    else
      e.u = ((a->ex + 1022) & 0x7ff) << 52;
  } else {
    if (a->ex < -1073) {
      if (a->ex == -1074) {
        r.f = r.f * 0x1p-1;
        e.f = 0x1p-1074;
      } else {
        r.f = 0x0.0000000000001p-1022;
        e.f = 0x0.0000000000001p-1022;
      }
    } else {
      e.u = 1l << (a->ex + 1073);
    }
  }

  return r.f * e.f;
}

#endif /* JQUANTLIB_HARNESS_COREMATH_DINT_H */
