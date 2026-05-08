/* migration-harness/cpp/probes/transcendental/qint64_shim.h
 * Phase 2n A.0 — Public C interface for the qint64_t support.
 *
 * Both qint64_shim.c (which compiles as C and includes coremath/qint.h)
 * and qint64_probe.cpp (which compiles as C++) include this header. The
 * C++ probe never sees coremath/qint.h directly, side-stepping the
 * pre-C++11 string concatenation issue in qint.h's print_qint.
 */

#ifndef JQUANTLIB_HARNESS_QINT64_SHIM_H
#define JQUANTLIB_HARNESS_QINT64_SHIM_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* qint64_t struct: a tagged 256-bit signed-magnitude mantissa with signed
   exponent. Layout MUST match the union in coremath/qint.h. We expose the
   non-union variant — the union variant is only used internally to qint.h's
   own arithmetic; for probe purposes we only need the four 64-bit fields
   plus exponent and sign. */
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
typedef struct {
  uint64_t ll;
  uint64_t lh;
  uint64_t hl;
  uint64_t hh;
  int64_t  ex;
  uint64_t sgn;
} qint64_t_shim;
#else
typedef struct {
  uint64_t lh;
  uint64_t ll;
  uint64_t hh;
  uint64_t hl;
  int64_t  ex;
  uint64_t sgn;
} qint64_t_shim;
#endif

/* The actual qint64_t (from qint.h) is a union of two structs and is
   layout-compatible with qint64_t_shim. We pass through pointers only.
   The C-side shim file already includes qint.h before this header, which
   defines qint64_t. The C++ probe doesn't need qint64_t — only the shim
   type. */

/* Function declarations (extern "C") — the C++ probe sees these.
   The C shim file includes qint.h and provides the bodies, casting to
   qint.h's true qint64_t type. */
void shim_cp_qint(qint64_t_shim *r, const qint64_t_shim *a);
int  shim_cmp_qint(const qint64_t_shim *a, const qint64_t_shim *b);
int  shim_cmp_qint_22(const qint64_t_shim *a, const qint64_t_shim *b);
void shim_add_qint(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_add_qint_22(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_11(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_21(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_22(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_31(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_33(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_41(qint64_t_shim *r, const qint64_t_shim *a, const qint64_t_shim *b);
void shim_mul_qint_2(qint64_t_shim *r, int64_t b, const qint64_t_shim *a);
void shim_qint_fromd_ext(qint64_t_shim *r, double b);
int64_t shim_qint_toi_ext(const qint64_t_shim *a);
double shim_qint_tod_ext(qint64_t_shim *a);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* JQUANTLIB_HARNESS_QINT64_SHIM_H */
