/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.math.Complex;
import org.jquantlib.model.equity.BatesModel;
import org.jquantlib.processes.BatesProcess;
import org.jquantlib.processes.HestonProcess;

/**
 * Analytic Bates-model engine — Heston SV plus log-normal jump diffusion.
 *
 * <p>Phase 5h.5-Bates port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/batesengine.{hpp,cpp}}. Extends
 * {@link AnalyticHestonEngine} and overrides {@link #addOnTerm} to inject
 * the Bates jump-diffusion correction into the Gatheral characteristic
 * function. The integrand and quadrature stay identical to the Heston engine.
 *
 * <p>Characteristic function add-on (Sepp 2003):
 * <pre>
 *   addOn(phi, t, j) = t * lambda * ( exp(nu*g + delta^2/2 * g^2) - 1
 *                                     - g * (exp(nu + delta^2/2) - 1) )
 *   where g = i + i*phi if j == 1, else i*phi
 * </pre>
 *
 * <p>The {@link BatesProcess} is supplied separately because (matching
 * the Java {@link AnalyticHestonEngine} constructor pattern) the Java
 * {@code HestonModel} does not currently expose a {@code process()}
 * accessor.
 *
 * @see AnalyticHestonEngine
 * @see BatesModel
 * @see BatesProcess
 */
public class BatesEngine extends AnalyticHestonEngine {

    private final BatesModel batesModel_;

    /**
     * Convenience constructor: Gatheral formula + Gauss-Laguerre 144 (the
     * C++ default). Java currently uses the embedded n=128 quadrature table.
     */
    public BatesEngine(final BatesModel model, final BatesProcess process) {
        this(model, process, 144);
    }

    /** Standard constructor: Gauss-Laguerre quadrature of the requested order. */
    public BatesEngine(final BatesModel model, final BatesProcess process,
                       final int integrationOrder) {
        super(model, (HestonProcess) process, integrationOrder);
        this.batesModel_ = model;
    }

    /** Bates jump-diffusion characteristic-function add-on. */
    @Override
    protected Complex addOnTerm(final double phi, final double t, final int j) {
        final double nu     = batesModel_.nu();
        final double delta2 = 0.5 * batesModel_.delta() * batesModel_.delta();
        final double lambda = batesModel_.lambda();
        final double i      = (j == 1) ? 1.0 : 0.0;
        final Complex g     = new Complex(i, phi);

        // exp(nu*g + delta2*g*g) - 1 - g*(exp(nu+delta2)-1)
        final Complex term1 = g.mul(nu).add(g.mul(g).mul(delta2)).exp().sub(1.0);
        final Complex term2 = g.mul(Math.exp(nu + delta2) - 1.0);
        return term1.sub(term2).mul(t * lambda);
    }

    /** Accessor used by subclasses (BatesDetJumpEngine) to compose add-ons. */
    protected BatesModel batesModel() {
        return batesModel_;
    }
}
