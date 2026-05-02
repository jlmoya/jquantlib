/**
 * Gaussian Short Rate stochastic process for the Gsr model.
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/processes/gsrprocess*.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j WI-1.2.
 *
 * <ul>
 *   <li>{@link org.jquantlib.processes.gsr.GsrProcessCore} — analytical
 *       drift/diffusion/expectation/variance, revZero-aware caching</li>
 *   <li>{@link org.jquantlib.processes.gsr.GsrProcess} — ForwardMeasureProcess1D
 *       wrapper for Monte Carlo / lattices</li>
 * </ul>
 */
package org.jquantlib.processes.gsr;
