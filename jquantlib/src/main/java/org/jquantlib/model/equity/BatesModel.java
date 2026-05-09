/*
Copyright (C)
2009 Ueli Hofstetter

This source code is release under the BSD License.

This file is part of JQuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://jquantlib.org/

JQuantLib is free software: you can redistribute it and/or modify it
under the terms of the JQuantLib license.  You should have received a
copy of the license along with this program; if not, please email
<jquant-devel@lists.sourceforge.net>. The license is also available online at
<http://www.jquantlib.org/index.php/LICENSE.TXT>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the license for more details.

JQuantLib is based on QuantLib. http://quantlib.org/
When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.model.equity;

import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.NullParameter;
import org.jquantlib.processes.HestonProcess;

/**
 * Bates stochastic-volatility model (Heston SV plus jump diffusion).
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/models/equity/batesmodel.{hpp,cpp}}
 * BatesModel. The C++ ctor calls {@code arguments_.resize(8)} after
 * {@code HestonModel(process)} sets 5 slots; the Java port appends three
 * NullParameter slots to extend the inherited size-5 list before assigning
 * nu/delta/lambda. (Pre-Phase 5h.5 the Java code attempted
 * {@code arguments_.set(5,...)} which would have thrown IOOBE — fixed in
 * the align commit prior to BatesEngine port.)
 */
public class BatesModel extends HestonModel {

    public BatesModel(final HestonProcess process, final double lambda, final double nu, final double delta) {
        super(process);
        // Match C++ arguments_.resize(8): extend by 3 NullParameter slots.
        while (arguments_.size() < 8) {
            arguments_.add(new NullParameter());
        }
        arguments_.set(5, new ConstantParameter(nu, new NoConstraint()));
        arguments_.set(6, new ConstantParameter(delta, new PositiveConstraint()));
        arguments_.set(7, new ConstantParameter(lambda, new PositiveConstraint()));

    }

    public BatesModel(final HestonProcess process) {
        this(process, 0.1, 0.0, 0.1);
    }


    public double nu() {
        return arguments_.get(5).get(0.0);
    }

    public double delta() {
        return arguments_.get(6).get(0.0);
    }

    public double lambda() {
        return arguments_.get(7).get(0.0);
    }

}
