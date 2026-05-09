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
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.instruments.Position;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Floating-rate coupon with a digital call/put option payoff.
 * <p>
 * Implementation of the four cash-or-nothing / asset-or-nothing payoffs:
 * <ul>
 *   <li>Cash-or-nothing Digital Call:
 *       {@latex$ R + \xi \cdot K \cdot \mathbf 1_{R > K} }</li>
 *   <li>Cash-or-nothing Digital Put:
 *       {@latex$ R + \xi \cdot K \cdot \mathbf 1_{R < K} }</li>
 *   <li>Asset-or-nothing Digital Call:
 *       {@latex$ R + \xi \cdot R \cdot \mathbf 1_{R > K} }</li>
 *   <li>Asset-or-nothing Digital Put:
 *       {@latex$ R + \xi \cdot R \cdot \mathbf 1_{R < K} }</li>
 * </ul>
 * with {@latex$ \xi = \pm 1 } selecting long/short position. If
 * {@code nakedOption} is true, the underlying rate {@latex$ R } is set to
 * zero in the payoff.
 * <p>
 * The continuous payoff is replicated using a call/put-spread with a small
 * gap around the strike, controlled by the {@link DigitalReplication}
 * parameters (Sub, Central or Super).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/digitalcoupon.hpp/cpp}.
 *
 * <h3>Java port note</h3>
 * The C++ implementation uses {@code LazyObject::performCalculations} +
 * {@code calculate()} to memoise the rate. This Java port computes the rate
 * on every {@link #rate()} call (matching how
 * {@link FloatingRateCoupon#rate()} works in JQuantLib), which is correct
 * but does not benefit from C++ caching.
 *
 * @author Cristina Duminuco (C++ original)
 * @author Giorgio Facchinetti (C++ original)
 */
public class DigitalCoupon extends FloatingRateCoupon {

    //
    // protected fields (mirroring C++ class layout)
    //

    /** Underlying floating-rate coupon. */
    protected final FloatingRateCoupon underlying_;
    /** Strike rate for the call option. */
    protected double callStrike_;
    /** Strike rate for the put option. */
    protected double putStrike_;
    /** Multiplicative factor of call payoff (+1 long, -1 short). */
    protected double callCsi_;
    /** Multiplicative factor of put payoff (+1 long, -1 short). */
    protected double putCsi_;
    /** Inclusion flag of the call payoff if the call option ends at-the-money. */
    protected final boolean isCallATMIncluded_;
    /** Inclusion flag of the put payoff if the put option ends at-the-money. */
    protected final boolean isPutATMIncluded_;
    /** Cash-or-nothing call (true) vs asset-or-nothing call (false). */
    protected boolean isCallCashOrNothing_;
    /** Cash-or-nothing put (true) vs asset-or-nothing put (false). */
    protected boolean isPutCashOrNothing_;
    /** Digital call payoff rate, if any. */
    protected double callDigitalPayoff_;
    /** Digital put payoff rate, if any. */
    protected double putDigitalPayoff_;
    /** Left/right gaps applied in payoff replication for call. */
    protected double callLeftEps_;
    protected double callRightEps_;
    /** Left/right gaps applied in payoff replication for put. */
    protected double putLeftEps_;
    protected double putRightEps_;

    protected boolean hasPutStrike_;
    protected boolean hasCallStrike_;
    /** Type of replication. */
    protected final Replication.Type replicationType_;
    /** Underlying excluded from the payoff. */
    protected final boolean nakedOption_;


    //
    // public constructors
    //

    /** Convenience constructor with all defaults (no call/put options). */
    public DigitalCoupon(final FloatingRateCoupon underlying) {
        this(underlying,
             Constants.NULL_REAL, Position.Long, false, Constants.NULL_REAL,
             Constants.NULL_REAL, Position.Long, false, Constants.NULL_REAL,
             null, false);
    }

    /** Full constructor matching C++ DigitalCoupon ctor. */
    public DigitalCoupon(final FloatingRateCoupon underlying,
                         final double callStrike,
                         final Position callPosition,
                         final boolean isCallATMIncluded,
                         final double callDigitalPayoff,
                         final double putStrike,
                         final Position putPosition,
                         final boolean isPutATMIncluded,
                         final double putDigitalPayoff,
                         final DigitalReplication replication,
                         final boolean nakedOption) {
        super(underlying.date(),
              underlying.nominal(),
              underlying.accrualStartDate(),
              underlying.accrualEndDate(),
              underlying.fixingDays(),
              underlying.index(),
              underlying.gearing(),
              underlying.spread(),
              underlying.referencePeriodStart(),
              underlying.referencePeriodEnd(),
              underlying.dayCounter(),
              underlying.isInArrears());
        this.underlying_ = underlying;
        this.isCallATMIncluded_ = isCallATMIncluded;
        this.isPutATMIncluded_ = isPutATMIncluded;
        this.nakedOption_ = nakedOption;

        // Default DigitalReplication: Central / gap = 1e-4
        final DigitalReplication rep = (replication != null) ? replication : new DigitalReplication();

        QL.require(rep.gap() > 0.0, "Non positive epsilon not allowed");

        // initialize gaps to half-gap (Central)
        callLeftEps_ = callRightEps_ = putLeftEps_ = putRightEps_ = rep.gap() / 2.0;
        replicationType_ = rep.replicationType();

        if (putStrike == Constants.NULL_REAL) {
            QL.require(putDigitalPayoff == Constants.NULL_REAL,
                    "Put Cash rate non allowed if put strike is null");
        }
        if (callStrike == Constants.NULL_REAL) {
            QL.require(callDigitalPayoff == Constants.NULL_REAL,
                    "Call Cash rate non allowed if call strike is null");
        }
        if (callStrike != Constants.NULL_REAL) {
            hasCallStrike_ = true;
            callStrike_ = callStrike;
            switch (callPosition) {
                case Long:
                    callCsi_ = 1.0;
                    break;
                case Short:
                    callCsi_ = -1.0;
                    break;
                default:
                    throw new LibraryException("unsupported position type");
            }
            if (callDigitalPayoff != Constants.NULL_REAL) {
                callDigitalPayoff_ = callDigitalPayoff;
                isCallCashOrNothing_ = true;
            }
        }
        if (putStrike != Constants.NULL_REAL) {
            hasPutStrike_ = true;
            putStrike_ = putStrike;
            switch (putPosition) {
                case Long:
                    putCsi_ = 1.0;
                    break;
                case Short:
                    putCsi_ = -1.0;
                    break;
                default:
                    throw new LibraryException("unsupported position type");
            }
            if (putDigitalPayoff != Constants.NULL_REAL) {
                putDigitalPayoff_ = putDigitalPayoff;
                isPutCashOrNothing_ = true;
            }
        }

        switch (replicationType_) {
            case Central:
                // no-op (gaps already set to gap/2)
                break;
            case Sub:
                if (hasCallStrike_) {
                    switch (callPosition) {
                        case Long:
                            callLeftEps_ = 0.0;
                            callRightEps_ = rep.gap();
                            break;
                        case Short:
                            callLeftEps_ = rep.gap();
                            callRightEps_ = 0.0;
                            break;
                        default:
                            throw new LibraryException("unsupported position type");
                    }
                }
                if (hasPutStrike_) {
                    switch (putPosition) {
                        case Long:
                            putLeftEps_ = rep.gap();
                            putRightEps_ = 0.0;
                            break;
                        case Short:
                            putLeftEps_ = 0.0;
                            putRightEps_ = rep.gap();
                            break;
                        default:
                            throw new LibraryException("unsupported position type");
                    }
                }
                break;
            case Super:
                if (hasCallStrike_) {
                    switch (callPosition) {
                        case Long:
                            callLeftEps_ = rep.gap();
                            callRightEps_ = 0.0;
                            break;
                        case Short:
                            callLeftEps_ = 0.0;
                            callRightEps_ = rep.gap();
                            break;
                        default:
                            throw new LibraryException("unsupported position type");
                    }
                }
                if (hasPutStrike_) {
                    switch (putPosition) {
                        case Long:
                            putLeftEps_ = 0.0;
                            putRightEps_ = rep.gap();
                            break;
                        case Short:
                            putLeftEps_ = rep.gap();
                            putRightEps_ = 0.0;
                            break;
                        default:
                            throw new LibraryException("unsupported position type");
                    }
                }
                break;
            default:
                throw new LibraryException("unsupported replication type");
        }

        this.underlying_.addObserver(this);
    }


    //
    // overrides FloatingRateCoupon
    //

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        if (this.pricer_ != null) {
            this.pricer_.deleteObserver(this);
        }
        this.pricer_ = pricer;
        if (this.pricer_ != null) {
            this.pricer_.addObserver(this);
        }
        update();
        underlying_.setPricer(pricer);
    }

    @Override
    public double rate() {
        QL.require(underlying_.pricer() != null, "pricer not set");

        final Date fixingDate = underlying_.fixingDate();
        final Settings s = new Settings();
        final Date today = s.evaluationDate();
        final boolean enforceTodaysHistoricFixings = s.isEnforcesTodaysHistoricFixings();
        final double underlyingRate = nakedOption_ ? 0.0 : underlying_.rate();

        if (fixingDate.lt(today)
                || (fixingDate.equals(today) && enforceTodaysHistoricFixings)) {
            // must have been fixed
            return underlyingRate + callCsi_ * callPayoff() + putCsi_ * putPayoff();
        } else if (fixingDate.equals(today)) {
            // might have been fixed
            if (hasHistoricalFixing(fixingDate)) {
                return underlyingRate + callCsi_ * callPayoff() + putCsi_ * putPayoff();
            }
            return underlyingRate + callCsi_ * callOptionRate() + putCsi_ * putOptionRate();
        } else {
            return underlyingRate + callCsi_ * callOptionRate() + putCsi_ * putOptionRate();
        }
    }

    @Override
    public double convexityAdjustment() {
        return underlying_.convexityAdjustment();
    }


    //
    // public inspectors
    //

    public double callStrike() {
        return hasCall() ? callStrike_ : Constants.NULL_REAL;
    }

    public double putStrike() {
        return hasPut() ? putStrike_ : Constants.NULL_REAL;
    }

    public double callDigitalPayoff() {
        return isCallCashOrNothing_ ? callDigitalPayoff_ : Constants.NULL_REAL;
    }

    public double putDigitalPayoff() {
        return isPutCashOrNothing_ ? putDigitalPayoff_ : Constants.NULL_REAL;
    }

    public boolean hasPut() {
        return hasPutStrike_;
    }

    public boolean hasCall() {
        return hasCallStrike_;
    }

    public boolean hasCollar() {
        return hasCallStrike_ && hasPutStrike_;
    }

    public boolean isLongPut() {
        return putCsi_ == 1.0;
    }

    public boolean isLongCall() {
        return callCsi_ == 1.0;
    }

    public FloatingRateCoupon underlying() {
        return underlying_;
    }

    /**
     * Call option rate. Multiplied by {@code nominal * accrualPeriod * discount}
     * gives the NPV of the call option.
     */
    public double callOptionRate() {
        double callOptionRate = 0.0;
        if (hasCallStrike_) {
            // Step function
            callOptionRate = isCallCashOrNothing_ ? callDigitalPayoff_ : callStrike_;
            final CappedFlooredCoupon next = new CappedFlooredCoupon(
                    underlying_, callStrike_ + callRightEps_, Constants.NULL_REAL);
            final CappedFlooredCoupon previous = new CappedFlooredCoupon(
                    underlying_, callStrike_ - callLeftEps_, Constants.NULL_REAL);
            callOptionRate *= (next.rate() - previous.rate())
                    / (callLeftEps_ + callRightEps_);
            if (!isCallCashOrNothing_) {
                // Asset-or-nothing call: add (underlying - cap-at-strike)
                final CappedFlooredCoupon atStrike = new CappedFlooredCoupon(
                        underlying_, callStrike_, Constants.NULL_REAL);
                final double call = underlying_.rate() - atStrike.rate();
                callOptionRate += call;
            }
        }
        return callOptionRate;
    }

    /**
     * Put option rate. Multiplied by {@code nominal * accrualPeriod * discount}
     * gives the NPV of the put option.
     */
    public double putOptionRate() {
        double putOptionRate = 0.0;
        if (hasPutStrike_) {
            // Step function
            putOptionRate = isPutCashOrNothing_ ? putDigitalPayoff_ : putStrike_;
            final CappedFlooredCoupon next = new CappedFlooredCoupon(
                    underlying_, Constants.NULL_REAL, putStrike_ + putRightEps_);
            final CappedFlooredCoupon previous = new CappedFlooredCoupon(
                    underlying_, Constants.NULL_REAL, putStrike_ - putLeftEps_);
            putOptionRate *= (next.rate() - previous.rate())
                    / (putLeftEps_ + putRightEps_);
            if (!isPutCashOrNothing_) {
                // Asset-or-nothing put: add (-underlying + floor-at-strike)
                final CappedFlooredCoupon atStrike = new CappedFlooredCoupon(
                        underlying_, Constants.NULL_REAL, putStrike_);
                final double put = -underlying_.rate() + atStrike.rate();
                putOptionRate -= put;
            }
        }
        return putOptionRate;
    }


    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }


    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<DigitalCoupon> v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }


    //
    // private helpers
    //

    /** Used only when the index has fixed (historic). */
    private double callPayoff() {
        double payoff = 0.0;
        if (hasCallStrike_) {
            final double underlyingRate = underlying_.rate();
            if ((underlyingRate - callStrike_) > 1.0e-16) {
                payoff = isCallCashOrNothing_ ? callDigitalPayoff_ : underlyingRate;
            } else {
                if (isCallATMIncluded_) {
                    if (Math.abs(callStrike_ - underlyingRate) <= 1.0e-16) {
                        payoff = isCallCashOrNothing_ ? callDigitalPayoff_ : underlyingRate;
                    }
                }
            }
        }
        return payoff;
    }

    /** Used only when the index has fixed (historic). */
    private double putPayoff() {
        double payoff = 0.0;
        if (hasPutStrike_) {
            final double underlyingRate = underlying_.rate();
            if ((putStrike_ - underlyingRate) > 1.0e-16) {
                payoff = isPutCashOrNothing_ ? putDigitalPayoff_ : underlyingRate;
            } else {
                if (isPutATMIncluded_) {
                    if (Math.abs(putStrike_ - underlyingRate) <= 1.0e-16) {
                        payoff = isPutCashOrNothing_ ? putDigitalPayoff_ : underlyingRate;
                    }
                }
            }
        }
        return payoff;
    }

    /**
     * Returns true iff the underlying index has a historical fixing on the
     * given date. Mirrors C++ {@code Index::hasHistoricalFixing(Date)}, which
     * the Java port doesn't yet expose; we look up the IndexManager history
     * directly.
     */
    private boolean hasHistoricalFixing(final Date fixingDate) {
        try {
            final Double v = IndexManager.getInstance()
                    .getHistory(underlying_.index().name())
                    .get(fixingDate);
            return v != null;
        } catch (final Exception e) {
            return false;
        }
    }
}
