/* migration-harness/cpp/probes/transcendental/pow_shim.c
 * Phase 2n A.1 — C-language shim that includes the canonical CORE-MATH
 * pow.c (which uses pre-C++11 string concatenation in some places and
 * relies on pow_dint.h tables — the full upstream variant). Compiled as C
 * (not C++) so it integrates cleanly.
 *
 * The C++ probe (pow_probe.cpp) calls cr_pow(double, double) through the
 * shim's extern "C" boundary, side-stepping the C++ tokenizer issues.
 *
 * This shim builds against:
 *   - coremath/pow_shim.h (renamed from pow.h to use pow_dint.h)
 *   - coremath/pow_shim_body.c (renamed from pow.c to include pow_shim.h)
 *   - coremath/pow_dint.h (full upstream dint.h with stage-2 pow tables)
 *   - coremath/qint.h (already vendored)
 */

#include <stdint.h>

/* Pull in cr_pow's body. pow_shim_body.c includes pow_shim.h, which pulls
   in pow_dint.h (with stage-2 tables) and qint.h (with stage-3 tables). */
#include "coremath/pow_shim_body.c"

/* Public extern "C" entry point — the only thing the C++ probe sees. */
double pow_shim_cr_pow(double x, double y) {
    return cr_pow(x, y);
}
