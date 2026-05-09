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
import org.jquantlib.model.equity.BatesDetJumpModel;
import org.jquantlib.processes.BatesProcess;

/**
 * BatesEngine variant for jump-diffusion with deterministic jump intensity.
 *
 * <p>Phase 5h.5-Bates port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/batesengine.{hpp,cpp}}
 * {@code BatesDetJumpEngine}.
 *
 * <p>The deterministic-intensity addOn term scales the BatesEngine addOn
 * by a Riccati-type kernel involving {@code kappaLambda} (mean-reversion)
 * and {@code thetaLambda} (long-term mean):
 * <pre>
 *   addOn = (kL*t - 1 + exp(-kL*t)) * thetaL * l / (kL * t * lambda)
 *         + (1 - exp(-kL*t)) * l / (kL * t)
 * </pre>
 * where {@code l} is the BatesEngine addOn, {@code kL = kappaLambda},
 * {@code thetaL = thetaLambda}.
 */
public class BatesDetJumpEngine extends BatesEngine {

    private final BatesDetJumpModel detJumpModel_;

    public BatesDetJumpEngine(final BatesDetJumpModel model, final BatesProcess process) {
        this(model, process, 144);
    }

    public BatesDetJumpEngine(final BatesDetJumpModel model, final BatesProcess process,
                              final int integrationOrder) {
        super(model, process, integrationOrder);
        this.detJumpModel_ = model;
    }

    @Override
    protected Complex addOnTerm(final double phi, final double t, final int j) {
        final Complex l = super.addOnTerm(phi, t, j);

        final double lambda      = detJumpModel_.lambda();
        final double kappaLambda = detJumpModel_.kappaLambda();
        final double thetaLambda = detJumpModel_.thetaLambda();

        final double expNegKLt = Math.exp(-kappaLambda * t);
        final double scaleA = (kappaLambda * t - 1.0 + expNegKLt) * thetaLambda
                / (kappaLambda * t * lambda);
        final double scaleB = (1.0 - expNegKLt) / (kappaLambda * t);

        return l.mul(scaleA).add(l.mul(scaleB));
    }
}
