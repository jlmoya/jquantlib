/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

/**
 * Collateralized debt obligation (Hull-White probability-bucketing engine).
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::CDO}
 * ({@code ql/experimental/credit/cdo.{hpp,cpp}}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Prices a mezzanine CDO tranche with loss given default between attachment
 * point {@code D_1} and detachment point {@code D_2 > D_1}. For purchased
 * protection the instrument value is the difference of the protection value
 * {@code V_1} and premium value {@code V_2}, {@code V = V_1 - V_2}.
 *
 * <p>The portfolio loss distribution is built with the probability-bucketing
 * algorithm of Hull-White, "Valuation of a CDO and nth to default CDS without
 * Monte Carlo simulation", Journal of Derivatives 12, 2, 2004 — implemented
 * here via {@link LossDistBucketing} integrated over the systemic factor of a
 * {@link OneFactorCopula}. Varying notionals and per-name default term
 * structures are supported.
 *
 * <p>This is <b>distinct</b> from {@link SyntheticCdo}: {@code SyntheticCdo}
 * delegates the loss distribution to a basket-attached {@link DefaultLossModel}
 * via {@link MidPointCdoEngine}/{@link IntegralCdoEngine}, whereas this class
 * is a self-contained {@link Instrument} that owns its bucketing computation
 * (it overrides {@link #performCalculations()} directly, mirroring the C++
 * {@code CDO} which has no separate pricing engine).
 *
 * <p>Java vs C++: the C++ {@code copula_->integral(LossDistBucketing&, lgds,
 * defProb)} template call is bridged through the explicit
 * {@link OneFactorCopula.DistF} functional interface (the Java equivalent of
 * the C++ {@code template<class F> integral(F&, ...)} idiom).
 */
public class CDO extends Instrument {

    private final double attachment_;
    private final double detachment_;
    private final List< Double > nominals_;
    private final List< Handle< DefaultProbabilityTermStructure > > basket_;
    private final Handle< OneFactorCopula > copula_;
    private final boolean protectionSeller_;

    private final Schedule premiumSchedule_;
    private final double premiumRate_;
    private final DayCounter dayCounter_;
    private final double recoveryRate_;
    private final double upfrontPremiumRate_;
    private final Handle< YieldTermStructure > yieldTS_;
    /** number of buckets up to detachment point. */
    private final int nBuckets_;
    private final Period integrationStep_;

    private final List< Double > lgds_ = new ArrayList<>();

    /** total basket volume (sum of nominals_). */
    private final double nominal_;
    /** maximum loss given default (sum of lgds_). */
    private double lgd_;
    /** tranche detachment point (detachment_ * nominal_). */
    private final double xMax_;
    /** tranche attachment point (attachment_ * nominal_). */
    private final double xMin_;

    private int error_;

    private double premiumValue_;
    private double protectionValue_;
    private double upfrontPremiumValue_;

    /**
     * @param attachment        fraction of the LGD where protection starts
     * @param detachment        fraction of the LGD where protection ends
     * @param nominals          vector of basket nominal amounts
     * @param basket            default basket as a vector of default term structures
     * @param copula            one-factor copula
     * @param protectionSeller  sold protection if {@code true}, purchased otherwise
     * @param premiumSchedule   schedule for premium payments
     * @param premiumRate       annual premium rate, e.g. 0.05 for 5% p.a.
     * @param dayCounter        day count convention for the premium rate
     * @param recoveryRate      recovery rate as a fraction
     * @param upfrontPremiumRate premium as a tranche notional fraction
     * @param yieldTS           yield term structure handle
     * @param nBuckets          number of distribution buckets
     * @param integrationStep   time step for integrating over one premium period
     */
    public CDO(final double attachment, final double detachment, final List< Double > nominals,
            final List< Handle< DefaultProbabilityTermStructure > > basket, final Handle< OneFactorCopula > copula,
            final boolean protectionSeller, final Schedule premiumSchedule, final double premiumRate,
            final DayCounter dayCounter, final double recoveryRate, final double upfrontPremiumRate,
            final Handle< YieldTermStructure > yieldTS, final int nBuckets, final Period integrationStep) {
        this.attachment_ = attachment;
        this.detachment_ = detachment;
        this.nominals_ = new ArrayList<>(nominals);
        this.basket_ = new ArrayList<>(basket);
        this.copula_ = copula;
        this.protectionSeller_ = protectionSeller;
        this.premiumSchedule_ = premiumSchedule;
        this.premiumRate_ = premiumRate;
        this.dayCounter_ = dayCounter;
        this.recoveryRate_ = recoveryRate;
        this.upfrontPremiumRate_ = upfrontPremiumRate;
        this.yieldTS_ = yieldTS;
        this.nBuckets_ = nBuckets;
        this.integrationStep_ = integrationStep;

        QL.require(!basket.isEmpty(), "basket is empty");
        QL.require(attachment_ >= 0 && attachment_ < detachment_ && detachment_ <= 1,
                "illegal attachment/detachment point");

        yieldTS_.addObserver(this);
        copula_.addObserver(this);
        for ( final Handle< DefaultProbabilityTermStructure > i : basket_ ) {
            i.addObserver(this);
        }

        QL.require(nominals_.size() <= basket_.size(), "nominal vector size too large");

        if ( nominals_.size() < basket_.size() ) {
            final int n = basket_.size() - nominals_.size();
            final double back = nominals_.get(nominals_.size() - 1);
            for ( int i = 0; i < n; i++ ) {
                nominals_.add(back);
            }
        }

        QL.require(nominals_.size() == basket_.size(),
                "nominal size " + nominals_.size() + " != basket size " + basket_.size());

        double nominal = 0.0;
        for ( int i = 0; i < nominals_.size(); i++ ) {
            lgds_.add(nominals_.get(i) * (1.0 - recoveryRate_));
            nominal += nominals_.get(i);
            lgd_ += lgds_.get(i);
        }
        this.nominal_ = nominal;
        this.xMax_ = detachment_ * nominal_;
        this.xMin_ = attachment_ * nominal_;
    }

    /** Convenience overload mirroring the C++ default {@code integrationStep = Period(10, Years)}. */
    public CDO(final double attachment, final double detachment, final List< Double > nominals,
            final List< Handle< DefaultProbabilityTermStructure > > basket, final Handle< OneFactorCopula > copula,
            final boolean protectionSeller, final Schedule premiumSchedule, final double premiumRate,
            final DayCounter dayCounter, final double recoveryRate, final double upfrontPremiumRate,
            final Handle< YieldTermStructure > yieldTS, final int nBuckets) {
        this(attachment, detachment, nominals, basket, copula, protectionSeller, premiumSchedule, premiumRate,
                dayCounter, recoveryRate, upfrontPremiumRate, yieldTS, nBuckets, new Period(10, TimeUnit.Years));
    }

    public double nominal() {
        return nominal_;
    }

    public double lgd() {
        return lgd_;
    }

    public double attachment() {
        return attachment_;
    }

    public double detachment() {
        return detachment_;
    }

    public List< Double > nominals() {
        return nominals_;
    }

    public int size() {
        return basket_.size();
    }

    @Override
    public boolean isExpired() {
        // Mirrors C++ detail::simple_event(premiumSchedule_.dates().back())
        //   .hasOccurred(yieldTS_->referenceDate())
        // simple_event::hasOccurred consults Settings::includeReferenceDateEvents().
        final Date last = premiumSchedule_.dates().get(premiumSchedule_.dates().size() - 1);
        final Date refDate = yieldTS_.currentLink().referenceDate();
        if ( new Settings().includeReferenceDateEvents() ) {
            return last.compareTo(refDate) < 0;
        }
        return last.compareTo(refDate) <= 0;
    }

    public double fairPremium() {
        calculate();
        return -premiumRate_ * protectionValue_ / premiumValue_;
    }

    public double premiumValue() {
        calculate();
        return premiumValue_;
    }

    public double protectionValue() {
        calculate();
        return protectionValue_;
    }

    public int error() {
        calculate();
        return error_;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
    }

    private double expectedTrancheLoss(final Date d) {
        if ( d.compareTo(basket_.get(0).currentLink().referenceDate()) <= 0 ) {
            return 0.0;
        }

        final List< Double > defProb = new ArrayList<>(basket_.size());
        for ( int j = 0; j < basket_.size(); j++ ) {
            defProb.add(basket_.get(j).currentLink().defaultProbability(d));
        }

        final LossDistBucketing op = new LossDistBucketing(nBuckets_, xMax_);
        // Bridge the C++ template integral(F&, ...) call through the explicit
        // DistF functional interface; F::operator()(nominals, conditional)
        // maps to LossDistBucketing.op(nominals, conditional).
        final OneFactorCopula.DistF f = new OneFactorCopula.DistF() {
            @Override
            public Distribution evaluate(final List< Double > nominals, final List< Double > conditional) {
                return op.op(nominals, conditional);
            }

            @Override
            public int buckets() {
                return op.buckets();
            }

            @Override
            public double maximum() {
                return op.maximum();
            }
        };
        final Distribution dist = copula_.currentLink().integral(f, lgds_, defProb);

        return dist.trancheExpectedValue(xMin_, xMax_);
    }

    @Override
    protected void performCalculations() {
        QL.require(!yieldTS_.empty(), "no yield term structure set");

        errorEstimate = Constants.NULL_REAL;

        NPV = 0.0;
        premiumValue_ = 0.0;
        protectionValue_ = 0.0;
        error_ = 0;

        /* Expectations e1 and e2 are portfolio loss given default,
           i.e. with recovery already "built in". Multiplication by
           (1-r) is therefore not necessary, neither in premium nor
           protection value calculation. */

        double e1 = 0.0;
        final Date today = yieldTS_.currentLink().referenceDate();
        if ( premiumSchedule_.date(0).compareTo(today) > 0 ) {
            e1 = expectedTrancheLoss(premiumSchedule_.date(0));
        }

        final NullCalendar nullCalendar = new NullCalendar();

        for ( int i = 1; i < premiumSchedule_.size(); i++ ) {
            final Date d2 = premiumSchedule_.date(i);
            if ( d2.compareTo(today) < 0 ) {
                continue;
            }

            final Date d1 = premiumSchedule_.date(i - 1);

            Date d;
            Date d0 = d1;
            do {
                final Date start = d0.compareTo(today) > 0 ? d0 : today;
                d = nullCalendar.advance(start, integrationStep_);
                if ( d.compareTo(d2) > 0 ) {
                    d = d2;
                }

                final double e2 = expectedTrancheLoss(d);

                premiumValue_ += (xMax_ - xMin_ - e2) * premiumRate_ * dayCounter_.yearFraction(d0, d)
                        * yieldTS_.currentLink().discount(d);

                if ( e2 < e1 ) {
                    error_++;
                }

                protectionValue_ -= (e2 - e1) * yieldTS_.currentLink().discount(d);

                d0 = d;
                e1 = e2;
            } while ( d.compareTo(d2) < 0 );
        }

        if ( premiumSchedule_.date(0).compareTo(today) >= 0 ) {
            upfrontPremiumValue_ = (xMax_ - xMin_) * upfrontPremiumRate_ * yieldTS_.currentLink()
                    .discount(premiumSchedule_.date(0));
        } else {
            upfrontPremiumValue_ = 0.0;
        }

        if ( !protectionSeller_ ) {
            premiumValue_ *= -1;
            upfrontPremiumValue_ *= -1;
            protectionValue_ *= -1;
        }

        NPV = premiumValue_ + protectionValue_ + upfrontPremiumValue_;
    }
}
