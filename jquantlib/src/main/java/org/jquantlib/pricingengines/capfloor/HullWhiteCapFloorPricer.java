/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Banca Profilo S.p.A.
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.time.Date;

/**
 * Hull-White path pricer for cap/floor under the forward-measure simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/capfloor/mchullwhiteengine.{hpp,cpp}}
 * {@code detail::HullWhiteCapFloorPricer} (Phase 2 L3-D). The path carries the short-rate trajectory under the
 * {@code Tb}-forward measure ({@code Tb = endTimes.back()}); for each caplet/floorlet the pricer evaluates the
 * forward-measure-implied LIBOR rate {@code (P(fix,start)/P(fix,end) - 1)/tau}, applies the cap/floor payoff,
 * discounts to {@code Tb} via {@code 1/P(end,Tb)}, and accumulates. The terminal NPV is multiplied by the deterministic
 * {@code P(0,Tb)} factor to convert back to the spot measure.
 */
public class HullWhiteCapFloorPricer extends PathPricer< Path > {

    private final CapFloor.ArgumentsImpl args_;
    private final HullWhite model_;
    private final double forwardMeasureTime_;
    private final double endDiscount_;
    private final double[] startTimes_;
    private final double[] endTimes_;
    private final double[] fixingTimes_;

    public HullWhiteCapFloorPricer(final CapFloor.ArgumentsImpl args, final HullWhite model,
            final double forwardMeasureTime) {
        this.args_ = args;
        this.model_ = model;
        this.forwardMeasureTime_ = forwardMeasureTime;
        this.endDiscount_ = model_.termStructure().currentLink().discount(forwardMeasureTime_);

        final Date referenceDate = model_.termStructure().currentLink().referenceDate();
        final DayCounter dayCounter = model_.termStructure().currentLink().dayCounter();

        this.startTimes_ = new double[args.startDates.length];
        for ( int i = 0; i < startTimes_.length; ++i ) {
            startTimes_[i] = dayCounter.yearFraction(referenceDate, args.startDates[i]);
        }
        this.endTimes_ = new double[args.endDates.length];
        for ( int i = 0; i < endTimes_.length; ++i ) {
            endTimes_[i] = dayCounter.yearFraction(referenceDate, args.endDates[i]);
        }
        this.fixingTimes_ = new double[args.fixingDates.length];
        for ( int i = 0; i < fixingTimes_.length; ++i ) {
            fixingTimes_[i] = dayCounter.yearFraction(referenceDate, args.fixingDates[i]);
        }
    }

    @Override
    public Double op(final Path path) {
        final boolean isCap = args_.type == CapFloor.Type.Cap;
        double npv = 0.0;
        final double Tb = forwardMeasureTime_;

        int pastFixings = 0;
        for ( int i = 0; i < fixingTimes_.length; i++ ) {
            final double tau = args_.accrualTimes[i];
            final double start = startTimes_[i];
            final double end = endTimes_[i];
            final double fixing = fixingTimes_[i];
            if ( end <= 0.0 ) {
                // fixing in the past, caplet expired
                pastFixings++;
            } else {
                double currentLibor;
                final double ri_2;
                if ( fixing <= 0.0 ) {
                    // current caplet — fixing past, so deterministic
                    pastFixings++;
                    currentLibor = args_.forwards[i];
                    ri_2 = path.get(i - pastFixings + 2);
                } else {
                    // future caplet
                    final double ri_1 = path.get(i - pastFixings + 1);
                    ri_2 = path.get(i - pastFixings + 2);
                    final double d1 = model_.discountBond(fixing, start, ri_1);
                    final double d2 = model_.discountBond(fixing, end, ri_1);
                    currentLibor = (d1 / d2 - 1.0) / tau;
                }
                final double accrualFactor = 1.0 / model_.discountBond(end, Tb, ri_2);
                final double strike = isCap ? args_.capRates[i] : args_.floorRates[i];
                final double payoff = isCap ? Math.max(currentLibor - strike, 0.0)
                        : Math.max(strike - currentLibor, 0.0);
                npv += payoff * tau * args_.gearings[i] * args_.nominals[i] * accrualFactor;
            }
        }
        npv *= endDiscount_;
        return npv;
    }
}
