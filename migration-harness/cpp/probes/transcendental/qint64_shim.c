/* migration-harness/cpp/probes/transcendental/qint64_shim.c
 * Phase 2n A.0 — C-language shim that includes the canonical CORE-MATH
 * qint.h (whose print_qint uses pre-C++11 string concatenation that the
 * C++ tokenizer rejects), and exposes the qint operations through an
 * extern "C" interface compatible with the C++ probe.
 *
 * Compiled as C (not C++) so the printf in print_qint compiles cleanly.
 */

#include <stdint.h>
#include <stdio.h>
#include <inttypes.h>
#include <stddef.h> /* offsetof */
#include <string.h>
#include <fenv.h>
#include <math.h>
#include <errno.h>

#pragma STDC FENV_ACCESS ON

/* qint.h's body uses cmpu128() but does not declare it (the symbol comes
   from dint.h in the canonical CORE-MATH compilation flow). We need to
   provide it here. The u128 type is defined inside qint.h's
   `#ifndef UINT128_T` block, so we replicate that small section before
   including qint.h. (Or just declare cmpu128 with __int128 once; but
   keeping the type alias matches qint.h's expectation.) */
#if (defined(__clang__) && __clang_major__ >= 14) || (defined(__GNUC__) && __GNUC__ >= 14 && __BITINT_MAXWIDTH__ && __BITINT_MAXWIDTH__ >= 128)
typedef unsigned _BitInt(128) u128;
#else
typedef unsigned __int128 u128;
#endif

#define UINT128_T  /* skip qint.h's own typedef + helpers */

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

static inline int addu_128 (uint128_t a, uint128_t b, uint128_t *r) {
  r->r = a.r + b.r;
  return r->r < a.r;
}

static inline int subu_128 (uint128_t a, uint128_t b, uint128_t *r) {
  r->r = a.r - b.r;
  return r->r > a.r;
}

static inline signed char cmp (int64_t a, int64_t b) {
  return (a > b) - (a < b);
}

static inline signed char cmpu (uint64_t a, uint64_t b) {
  return (a > b) - (a < b);
}

/* qint.h needs cmpu128 — declared/defined by dint.h in the canonical flow.
   We replicate it here. */
static inline int cmpu128 (u128 a, u128 b) { return (a > b) - (a < b); }

/* Bring in the canonical CORE-MATH qint64 support. This file is C, so the
   `"%"PRIx64` constructs in print_qint are perfectly legal. */
#define CORE_MATH_POW
#include "coremath/qint.h"

/* The shim's qint64_t_shim must be layout-compatible with qint.h's
   qint64_t (the second struct of the union). Verify with static asserts. */
#include "qint64_shim.h"

#if defined(__STDC_VERSION__) && __STDC_VERSION__ >= 201112L
_Static_assert(sizeof(qint64_t) == sizeof(qint64_t_shim),
    "qint64_t and qint64_t_shim must be the same size");
_Static_assert(offsetof(qint64_t_shim, ll) == 0, "ll offset");
#endif

typedef union {
  double f;
  uint64_t u;
} shim_f64_u;

static inline void shim_fast_extract (int64_t *e, uint64_t *m, double x) {
  shim_f64_u _x;
  _x.f = x;
  *e = (_x.u >> 52) & 0x7ff;
  *m = (_x.u & (~0ull >> 12)) + (*e ? (1ull << 52) : 0);
  *e = *e - 0x3ff;
}

static inline void shim_qint_fromd_impl (qint64_t *a, double b) {
  shim_fast_extract (&a->ex, &a->hh, b);
  uint32_t t = __builtin_clzll (a->hh);
  a->sgn = b < 0.0;
  a->ex = a->ex - (t > 11 ? t - 12 : 0);
  a->hh = a->hh << t;
  a->lh = 0;
  a->hl = 0;
  a->ll = 0;
}

static inline int64_t shim_qint_toi_impl(const qint64_t *a) {
  if (a->ex < 0)
    return 0ll;
  int64_t r = a->hh >> (63 - a->ex);
  return a->sgn ? -r : r;
}

static inline void shim_subnormalize_qint(qint64_t *a) {
  if (a->ex > -1023)
    return;

  uint64_t ex = -(1011 + a->ex);

  uint64_t hi = a->hh >> ex;
  uint64_t md = (a->hh >> (ex - 1)) & 0x1;
  uint64_t lo = (a->hh & (~0ull >> ex)) || a->hl || a->lh || a->ll;

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

  a->hh = hi << ex;
  a->hl = 0;
  a->lh = 0;
  a->ll = 0;

  if (!a->hh) {
    a->ex++;
    a->hh = (1ull << 63);
  }
}

static inline double shim_qint_tod_impl(qint64_t *a) {
  shim_subnormalize_qint(a);

  shim_f64_u r;
  r.u = (a->hh >> 11) | (0x3ffll << 52);

  double rd = 0.0;
  if (a->hh & 0x400)
    rd += 0x1p-53;

  if (a->hh & 0x3ff || a->hl || a->lh || a->ll)
    rd += 0x1p-54;

  if (a->sgn)
    rd = -rd;

  r.u = r.u | a->sgn << 63;
  r.f += rd;

  shim_f64_u e;

  if (a->ex > -1023) {
    if (a->ex > 1023) {
      if (a->ex == 1024) {
        r.f = r.f * 0x1p+1;
        e.f = 0x1p+1023;
      } else {
        r.f = 0x1.fffffffffffffp+1023;
        e.f = 0x1.fffffffffffffp+1023;
      }
    } else {
      e.u = ((a->ex + 1023) & 0x7ff) << 52;
    }
  } else {
    feraiseexcept (FE_UNDERFLOW);
    if (a->ex < -1074) {
      if (a->ex == -1075) {
        r.f = r.f * 0x1p-1;
        e.f = 0x1p-1074;
      } else {
        r.f = 0x0.0000000000001p-1022;
        e.f = 0x0.0000000000001p-1022;
      }
    } else {
      e.u = 1ll << (a->ex + 1074);
    }
  }

  return r.f * e.f;
}

/* extern "C" wrapper bodies. Cast the shim type to qint.h's qint64_t,
   safe because they are layout-compatible. */

#define R ((qint64_t *) r)
#define A ((const qint64_t *) a)
#define B ((const qint64_t *) b)

void shim_cp_qint(qint64_t_shim *r, const qint64_t_shim *a) {
    cp_qint(R, A);
}

int shim_cmp_qint(const qint64_t_shim *a, const qint64_t_shim *b) {
    return (int) cmp_qint(A, B);
}

int shim_cmp_qint_22(const qint64_t_shim *a, const qint64_t_shim *b) {
    return (int) cmp_qint_22(A, B);
}

void shim_add_qint(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    add_qint(R, A, B);
}

void shim_add_qint_22(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    add_qint_22(R, A, B);
}

void shim_mul_qint(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint(R, A, B);
}

void shim_mul_qint_11(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_11(R, A, B);
}

void shim_mul_qint_21(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_21(R, A, B);
}

void shim_mul_qint_22(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_22(R, A, B);
}

void shim_mul_qint_31(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_31(R, A, B);
}

void shim_mul_qint_33(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_33(R, A, B);
}

void shim_mul_qint_41(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b) {
    mul_qint_41(R, A, B);
}

void shim_mul_qint_2(qint64_t_shim *r, int64_t b, const qint64_t_shim *a) {
    mul_qint_2(R, b, A);
}

void shim_qint_fromd_ext(qint64_t_shim *r, double b) {
    shim_qint_fromd_impl(R, b);
}

int64_t shim_qint_toi_ext(const qint64_t_shim *a) {
    return shim_qint_toi_impl(A);
}

double shim_qint_tod_ext(qint64_t_shim *a) {
    /* Both `a` (input) and the impl take a writable pointer (subnormalize). */
    return shim_qint_tod_impl((qint64_t *) a);
}

#undef R
#undef A
#undef B
