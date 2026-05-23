/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2013, 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.time.Date;

/**
 * Functor used by {@link MarkovFunctional}'s {@code marketSwapRate} root-finder
 * (Brent) to bracket the strike at which the model digital-call price equals a target
 * market digital price.
 *
 * <p>Java port of the nested C++ class in
 * {@code ql/models/shortrate/onefactormodels/markovfunctional.hpp} lines 480-495 (v1.42.1 @ 099987f0):
 * <pre>
 *   class ZeroHelper {
 *     public:
 *       ZeroHelper(const MarkovFunctional *model, const Date &amp;expiry,
 *                  const CalibrationPoint &amp;p, const Real marketPrice)
 *         : model_(model), marketPrice_(marketPrice), expiry_(expiry), p_(p) {}
 *       Real operator()(Real strike) const {
 *           Real modelPrice = model_-&gt;marketDigitalPrice(
 *               expiry_, p_, Option::Call, strike);
 *           return modelPrice - marketPrice_;
 *       };
 *       const MarkovFunctional *model_;
 *       const Real marketPrice_;
 *       const Date &amp;expiry_;
 *       const CalibrationPoint &amp;p_;
 *   };
 * </pre>
 *
 * <p>Implements {@link Ops.DoubleOp} so it plugs directly into JQuantLib's root-finders
 * ({@code org.jquantlib.math.solvers1D.Brent}, etc).
 *
 * <p>Note: In Java, anonymous inner classes provide the same functionality more concisely
 * (and the existing MarkovFunctional.java {@code marketSwapRate} body uses an anonymous
 * {@code Ops.DoubleOp}). This named class is provided for parity with the C++ structure
 * for review and any future engine that wants to introspect the helper's bound state.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public final class ZeroHelper implements Ops.DoubleOp {

    private final MarkovFunctional model_;
    private final Date expiry_;
    private final MarkovFunctional.CalibrationPoint p_;
    private final double marketPrice_;

    public ZeroHelper(final MarkovFunctional model, final Date expiry,
            final MarkovFunctional.CalibrationPoint p, final double marketPrice) {
        if ( model == null ) {
            throw new IllegalArgumentException("model must not be null");
        }
        if ( expiry == null ) {
            throw new IllegalArgumentException("expiry must not be null");
        }
        if ( p == null ) {
            throw new IllegalArgumentException("calibration point must not be null");
        }
        this.model_ = model;
        this.expiry_ = expiry;
        this.p_ = p;
        this.marketPrice_ = marketPrice;
    }

    /**
     * {@code op(strike) = modelDigitalPrice(strike) - marketPrice}.
     * <p>Brent (or any 1-D bracket-search) drives this to zero by solving for {@code strike}.
     */
    @Override
    public double op(final double strike) {
        final double modelPrice = model_.marketDigitalPrice(expiry_, p_, Option.Type.Call, strike);
        return modelPrice - marketPrice_;
    }
}
