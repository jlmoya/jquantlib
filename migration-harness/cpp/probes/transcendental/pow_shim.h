/* migration-harness/cpp/probes/transcendental/pow_shim.h
 * Phase 2n A.1 — Public C interface for cr_pow.
 *
 * Both pow_shim.c (which compiles as C and includes coremath/pow_shim_body.c)
 * and pow_probe.cpp (which compiles as C++) include this header. The C++
 * probe never sees coremath/pow.c or pow.h directly.
 */

#ifndef JQUANTLIB_HARNESS_POW_SHIM_H
#define JQUANTLIB_HARNESS_POW_SHIM_H

#ifdef __cplusplus
extern "C" {
#endif

/* Correctly-rounded x^y per CORE-MATH cr_pow.
   Bit-exact across all IEEE-754 binary64 inputs in round-to-nearest-even. */
double pow_shim_cr_pow(double x, double y);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* JQUANTLIB_HARNESS_POW_SHIM_H */
