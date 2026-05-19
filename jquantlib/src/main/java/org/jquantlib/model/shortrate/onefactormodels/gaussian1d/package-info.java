/**
 * Gaussian1D family of one-factor short-rate models (Hull-White generalization with arbitrary volatility term
 * structures and smile-aware swaption pricing).
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/models/shortrate/onefactormodels/}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j of the JQuantLib migration. See
 * {@code docs/migration/phase2j-design.md}.
 *
 * <p>Classes (landed across Phase 2j sub-layers):
 * <ul>
 *   <li>{@link org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel} — abstract base (WI-1.1)
 *   <li>{@code Gsr} — Gaussian Short Rate concrete model (WI-1.3, pending)
 *   <li>{@code MarkovFunctional} — calibration-driven concrete model (WI-4, pending)
 * </ul>
 */
package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;
