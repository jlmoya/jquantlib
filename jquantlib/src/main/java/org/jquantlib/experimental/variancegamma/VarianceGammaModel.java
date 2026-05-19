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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2010 Adrian O' Neill

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.variancegamma;

import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.ConstantParameter;

/**
 * Variance Gamma model.
 *
 * <p>Phase 4c port of {@code QuantLib::VarianceGammaModel}
 * (v1.42.1 ql/experimental/variancegamma/variancegammamodel.{hpp,cpp}).
 *
 * <p>References:
 * Dilip B. Madan, Peter Carr, Eric C. Chang (1998), "The variance gamma process and option pricing," European Finance
 * Review, 2, 79-105.
 *
 * <p><b>Warning:</b> calibration is not implemented for VG.
 *
 * @category models
 */
public class VarianceGammaModel extends CalibratedModel {

    private VarianceGammaProcess process_;

    public VarianceGammaModel(final VarianceGammaProcess process) {
        super(3);
        this.process_ = process;
        this.arguments_.set(0, new ConstantParameter(process.sigma(), new PositiveConstraint()));
        this.arguments_.set(1, new ConstantParameter(process.nu(), new PositiveConstraint()));
        this.arguments_.set(2, new ConstantParameter(process.theta(), new NoConstraint()));

        generateArguments();

        process_.riskFreeRate().addObserver(this);
        process_.dividendYield().addObserver(this);
        process_.s0().addObserver(this);
    }

    public double sigma() /*@ReadOnly*/ {
        return arguments_.get(0).get(0.0);
    }

    public double nu() /*@ReadOnly*/ {
        return arguments_.get(1).get(0.0);
    }

    public double theta() /*@ReadOnly*/ {
        return arguments_.get(2).get(0.0);
    }

    public VarianceGammaProcess process() /*@ReadOnly*/ {
        return process_;
    }

    @Override
    protected void generateArguments() {
        process_ = new VarianceGammaProcess(process_.s0(), process_.dividendYield(), process_.riskFreeRate(), sigma(),
                nu(), theta());
    }
}
