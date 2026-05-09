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
import org.jquantlib.model.equity.BatesDoubleExpModel;
import org.jquantlib.processes.BatesProcess;
import org.jquantlib.processes.HestonProcess;

/**
 * Bates engine variant for asymmetric double-exponential jumps (Kou).
 *
 * <p>Phase 5h.5-Bates port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/batesengine.{hpp,cpp}}
 * {@code BatesDoubleExpEngine}.
 *
 * <p>Jump magnitude has the asymmetric double-exponential density
 * {@code omega(J) = p/eta_u * exp(-J/eta_u) 1[J>0] + q/eta_d * exp(J/eta_d) 1[J<0]}
 * with {@code p + q = 1}. The characteristic-function add-on is
 * <pre>
 *   addOn = t * lambda * ( p/(1 - g*nuUp) + q/(1 + g*nuDown) - 1
 *                          - g*(p/(1 - nuUp) + q/(1 + nuDown) - 1) )
 *   where g = i + i*phi if j == 1, else i*phi
 * </pre>
 */
public class BatesDoubleExpEngine extends AnalyticHestonEngine {

    private final BatesDoubleExpModel doubleExpModel_;

    public BatesDoubleExpEngine(final BatesDoubleExpModel model, final BatesProcess process) {
        this(model, process, 144);
    }

    public BatesDoubleExpEngine(final BatesDoubleExpModel model, final BatesProcess process,
                                final int integrationOrder) {
        super(model, (HestonProcess) process, integrationOrder);
        this.doubleExpModel_ = model;
    }

    @Override
    protected Complex addOnTerm(final double phi, final double t, final int j) {
        final double pp     = doubleExpModel_.p();
        final double qq     = 1.0 - pp;
        final double nuDown = doubleExpModel_.nuDown();
        final double nuUp   = doubleExpModel_.nuUp();
        final double lambda = doubleExpModel_.lambda();
        final double i      = (j == 1) ? 1.0 : 0.0;
        final Complex g     = new Complex(i, phi);

        // p/(1 - g*nuUp) + q/(1 + g*nuDown) - 1
        final Complex one = Complex.ONE;
        final Complex term1 = one.div(g.mul(-nuUp).add(1.0)).mul(pp)
                .add(one.div(g.mul(nuDown).add(1.0)).mul(qq))
                .sub(1.0);
        // g * (p/(1 - nuUp) + q/(1 + nuDown) - 1)
        final double scalarConst = pp / (1.0 - nuUp) + qq / (1.0 + nuDown) - 1.0;
        final Complex term2 = g.mul(scalarConst);

        return term1.sub(term2).mul(t * lambda);
    }

    /** Accessor for subclasses (BatesDoubleExpDetJumpEngine). */
    protected BatesDoubleExpModel doubleExpModel() {
        return doubleExpModel_;
    }
}
