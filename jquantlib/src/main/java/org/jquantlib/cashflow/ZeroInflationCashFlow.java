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
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Cash flow dependent on a zero inflation index ratio.
 *
 * <p>The ratio is taken between fixings observed at the start date and the
 * end date minus the observation lag; that is, if the start and end dates
 * are, e.g., in June and the observation lag is three months, the ratio will
 * be taken between March fixings.
 *
 * <p>Mirrors C++ {@code QuantLib::ZeroInflationCashFlow} at v1.42.1
 * (cashflows/zeroinflationcashflow.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2p A.2)
 */
public class ZeroInflationCashFlow extends IndexedCashFlow {

    //
    // private fields
    //

    private final ZeroInflationIndex zeroInflationIndex_;
    private final CPI.InterpolationType interpolation_;
    private final Date startDate_;
    private final Date endDate_;
    private final Period observationLag_;

    //
    // public constructors
    //

    public ZeroInflationCashFlow(final double notional,
                                 final ZeroInflationIndex index,
                                 final CPI.InterpolationType observationInterpolation,
                                 final Date startDate,
                                 final Date endDate,
                                 final Period observationLag,
                                 final Date paymentDate) {
        this(notional, index, observationInterpolation, startDate, endDate,
             observationLag, paymentDate, false);
    }

    /**
     * The fixing dates for the index are {@code startDate - observationLag}
     * and {@code endDate - observationLag}.
     */
    public ZeroInflationCashFlow(final double notional,
                                 final ZeroInflationIndex index,
                                 final CPI.InterpolationType observationInterpolation,
                                 final Date startDate,
                                 final Date endDate,
                                 final Period observationLag,
                                 final Date paymentDate,
                                 final boolean growthOnly) {
        super(notional, index,
              startDate.sub(observationLag), endDate.sub(observationLag),
              paymentDate, growthOnly);
        this.zeroInflationIndex_ = index;
        this.interpolation_ = observationInterpolation;
        this.startDate_ = startDate.clone();
        this.endDate_ = endDate.clone();
        this.observationLag_ = observationLag;
    }

    //
    // public methods
    //

    public ZeroInflationIndex zeroInflationIndex() {
        return zeroInflationIndex_;
    }

    public CPI.InterpolationType observationInterpolation() {
        return interpolation_;
    }

    //
    // overrides IndexedCashFlow
    //

    @Override
    public double baseFixing() {
        return CPI.laggedFixing(zeroInflationIndex_, startDate_,
                                observationLag_, interpolation_);
    }

    @Override
    public double indexFixing() {
        return CPI.laggedFixing(zeroInflationIndex_, endDate_,
                                observationLag_, interpolation_);
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<ZeroInflationCashFlow> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
