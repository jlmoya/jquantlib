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
 Copyright (C) 2008 StatPro Italia srl

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

package org.jquantlib.experimental.varianceoption;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Variance option.
 *
 * <p>Phase 4a A.2 port of {@code QuantLib::VarianceOption}
 * (v1.42.1 ql/experimental/varianceoption/varianceoption.{hpp,cpp}).
 *
 * <p>This class does not manage seasoned variance options.
 *
 * @see <a href="http://www.econ.univpm.it/recchioni/finance/w4/">Reference</a>
 *
 * @category instruments
 *
 * @author JQuantLib migration contributors
 */
public class VarianceOption extends Instrument {

    private final Payoff payoff_;
    private final /*@Real*/ double notional_;
    private final Date startDate_;
    private final Date maturityDate_;

    public VarianceOption(
            final Payoff payoff,
            final /*@Real*/ double notional,
            final Date startDate,
            final Date maturityDate) {
        this.payoff_ = payoff;
        this.notional_ = notional;
        this.startDate_ = startDate;
        this.maturityDate_ = maturityDate;
    }

    @Override
    public boolean isExpired() {
        // Mirror of C++ detail::simple_event(maturityDate_).hasOccurred()
        // — default form, which defers to Settings::includeTodaysCashFlows();
        // Java mirror: Event::hasOccurred(d) defers to Settings::isTodaysPayments().
        final Settings s = new Settings();
        final Date eval = s.evaluationDate();
        if (s.isTodaysPayments()) {
            return maturityDate_.lt(eval);
        }
        return maturityDate_.le(eval);
    }

    public Date startDate() {
        return startDate_;
    }

    public Date maturityDate() {
        return maturityDate_;
    }

    public /*@Real*/ double notional() {
        return notional_;
    }

    public Payoff payoff() {
        return payoff_;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        QL.require(VarianceOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final ArgumentsImpl a = (ArgumentsImpl) args;
        a.payoff = payoff_;
        a.notional = notional_;
        a.startDate = startDate_;
        a.maturityDate = maturityDate_;
    }


    //
    // public inner classes
    //

    /**
     * Arguments for forward fair-variance calculation.
     */
    public static class ArgumentsImpl implements Instrument.Arguments {

        // FIXME: public fields here mirror QuantLib's pre-getter style.
        public Payoff payoff;
        public /*@Real*/ double notional;
        public Date startDate;
        public Date maturityDate;

        public ArgumentsImpl() {
            this.notional = Double.NaN;
        }

        @Override
        public void validate() /*@ReadOnly*/ {
            QL.require(payoff != null, "no strike given");
            QL.require(!Double.isNaN(notional), "no notional given");
            QL.require(notional > 0.0, "negative or null notional given");
            QL.require(startDate != null, "null start date given");
            QL.require(maturityDate != null, "null maturity date given");
        }
    }

    /**
     * Results from variance-option calculation.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements Instrument.Results { /* marking class */ }


    /**
     * Base class for variance-option engines.
     */
    public abstract static class EngineImpl
            extends GenericEngine<VarianceOption.ArgumentsImpl, VarianceOption.ResultsImpl> {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
