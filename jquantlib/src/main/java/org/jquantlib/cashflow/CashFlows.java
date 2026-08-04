/*
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2009 Richard Gomes

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

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.ZeroSpreadedTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Cashflow-analysis functions
 *
 * @author Ueli Hofstetter
 * @author Richard Gomes
 */

//
// =================== W A R N I N G ================
//
// This class requires a total rewrite. See: http://bugs.jquantlib.org/view.php?id=357

public class CashFlows {

    private static final double basisPoint_ = 1.0e-4;
    /**
     * Singleton instance for the whole application.
     * <p>
     * In an application server environment, it could be by class loader depending on scope of the JQuantLib library to
     * the module.
     *
     * @see <a href="http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html">The "Double-Checked Locking
     * is Broken" Declaration </a>
     */
    private static volatile CashFlows instance = null;
    private final String not_enough_information_available = "not enough information available";
    private final String no_cashflows = "no cashflows";
    private final String unsupported_compounding_type = "unsupported compounding type";
    private final String compounded_rate_required = "compounded rate required";
    private final String unsupported_frequency = "unsupported frequency";
    private final String unknown_duration_type = "unsupported duration type";
    private final String infeasible_cashflow = "the given cash flows cannot result in the given market price due to their sign";

    //
    // private constructors
    //

    private CashFlows() {
        // cannot be directly instantiated
    }

    //
    // public static methods
    //

    public static CashFlows getInstance() {
        if ( instance == null ) {
            synchronized ( CashFlows.class ) {
                if ( instance == null ) {
                    instance = new CashFlows();
                }
            }
        }
        return instance;
    }

    //
    // Coupon-pricer leg helpers
    //
    // Mirror C++ free functions in ql/cashflows/couponpricer.{hpp,cpp}:
    //
    //   void setCouponPricer(const Leg&, const ext::shared_ptr<FloatingRateCouponPricer>&);
    //   void setCouponPricers(const Leg&, const std::vector<ext::shared_ptr<FloatingRateCouponPricer> >&);
    //
    // JQL keeps the visitor implementation in PricerSetter; these statics
    // are thin facades so callers can use the C++-style API surface.
    //

    /**
     * Apply {@code pricer} to every coupon in {@code leg}.
     * <p>
     * Mirrors C++ {@code QuantLib::setCouponPricer(const Leg&, const ext::shared_ptr<FloatingRateCouponPricer>&)}.
     */
    public static void setCouponPricer(final Leg leg, final FloatingRateCouponPricer pricer) {
        PricerSetter.setCouponPricer(leg, pricer);
    }

    /**
     * Apply pricers element-wise to {@code leg}; the last pricer is re-used for any trailing coupons when
     * {@code pricers.size() < leg.size()}.
     * <p>
     * Mirrors C++ {@code QuantLib::setCouponPricers(const Leg&, const std::vector<...> &)} in
     * ql/cashflows/couponpricer.cpp lines 468-484.
     */
    public static void setCouponPricers(final Leg leg, final java.util.List< FloatingRateCouponPricer > pricers) {
        final int nCashFlows = leg.size();
        QL.require(nCashFlows > 0, "no cashflows");
        final int nPricers = pricers.size();
        QL.require(nCashFlows >= nPricers,
                "mismatch between leg size (" + nCashFlows + ") and number of pricers (" + nPricers + ")");
        for ( int i = 0; i < nCashFlows; i++ ) {
            final FloatingRateCouponPricer p = i < nPricers ? pricers.get(i) : pricers.get(nPricers - 1);
            final PricerSetter setter = new PricerSetter(p);
            leg.get(i).accept(setter);
        }
    }

    //
    // public methods
    //

    /**
     * NPV of the cash flows. Mirrors C++
     * {@code CashFlows::npv(leg, discountCurve, includeSettlementDateFlows, settlementDate, npvDate)}
     * (ql/cashflows/cashflows.cpp:424-447).
     *
     * <p>Skips any cashflow that has already occurred or for which
     * {@link CashFlow#tradingExCoupon(Date) tradingExCoupon(settlementDate)}
     * is {@code true} (cashflows.cpp:441-443) — the ex-coupon filter that
     * goes hand-in-hand with {@code hasOccurred} in the C++ reference.
     * Phase 5e.5b-CFC-d-302.
     *
     * @param leg                        the cash-flow leg
     * @param discountCurve              discount-curve handle (raw, like the C++ reference)
     * @param includeSettlementDateFlows whether a flow on the settlement date is treated as still-pending (true) or as
     *                                   already paid (false)
     * @param settlementDate             the settlement date; if null, the curve's reference date is used
     * @param npvDate                    the date the NPV is discounted to; if null, the result is the dirty present
     *                                   value at {@code settlementDate}
     */
    public static double npv(final Leg leg, final YieldTermStructure discountCurve,
            final boolean includeSettlementDateFlows, final Date settlementDate, final Date npvDate) {
        Date date = settlementDate;
        if ( date == null || date.isNull() ) {
            date = discountCurve.referenceDate();
        }

        double totalNPV = 0.0;
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( !cf.hasOccurred(date, includeSettlementDateFlows) && !cf.tradingExCoupon(date) ) {
                totalNPV += cf.amount() * discountCurve.discount(cf.date());
            }
        }

        if ( npvDate == null || npvDate.isNull() ) {
            return totalNPV;
        }
        return totalNPV / discountCurve.discount(npvDate);
    }

    /**
     * NPV and BPS of the cash flows, computed together.
     * <p>
     * C++ parity: {@code CashFlows::npvbps} (cashflows.cpp). Upstream computes
     * both in a single pass "for performance reason" and returns a
     * {@code std::pair<Real,Real>}; Java returns a two-element array
     * {@code {npv, bps}} to avoid introducing a tuple type here.
     * <p>
     * New in v1.43 usage: required by
     * {@code DiscountingConstNotionalCrossCurrencySwapEngine}.
     *
     * @param leg the cash-flow sequence
     * @param discountCurve the discounting term structure
     * @param includeSettlementDateFlows whether flows on the settlement date count
     * @param settlementDate settlement date; null//null-date defaults to the evaluation date
     * @param npvDate date to which the result is discounted; null/null-date defaults to settlementDate
     * @return {@code {npv, bps}}
     */
    public static double[] npvbps(final Leg leg, final YieldTermStructure discountCurve,
            final boolean includeSettlementDateFlows, final Date settlementDate, final Date npvDate) {
        double npv = 0.0;
        double bps = 0.0;
        if ( leg == null || leg.isEmpty() ) {
            return new double[] { npv, bps };
        }

        Date settlement = settlementDate;
        if ( settlement == null || settlement.isNull() ) {
            settlement = new Settings().evaluationDate();
        }
        Date npvDt = npvDate;
        if ( npvDt == null || npvDt.isNull() ) {
            npvDt = settlement;
        }

        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( !cf.hasOccurred(settlement, includeSettlementDateFlows) && !cf.tradingExCoupon(settlement) ) {
                final double df = discountCurve.discount(cf.date());
                npv += cf.amount() * df;
                if ( cf instanceof Coupon ) {
                    final Coupon cp = (Coupon) cf;
                    bps += cp.nominal() * cp.accrualPeriod() * df;
                }
            }
        }

        final double d = discountCurve.discount(npvDt);
        npv /= d;
        bps = basisPoint_ * bps / d;
        return new double[] { npv, bps };
    }

    /**
     * Stepwise time-to-discount for one cashflow. Mirrors C++ {@code getStepwiseDiscountTime} (cashflows.cpp:568-600).
     * For a {@link Coupon} we accumulate using the coupon's reference period; for any other cashflow, fall back to
     * {@code dc.yearFraction(lastDate, cashFlowDate)}.
     */
    private static double stepwiseDiscountTime(final CashFlow cashFlow, final DayCounter dc, final Date npvDate,
            final Date lastDate) {
        final Date cashFlowDate = cashFlow.date();
        Date refStartDate;
        Date refEndDate;
        final Coupon coupon = (cashFlow instanceof Coupon) ? (Coupon) cashFlow : null;
        if ( coupon != null ) {
            refStartDate = coupon.referencePeriodStart();
            refEndDate = coupon.referencePeriodEnd();
        } else {
            if ( lastDate.equals(npvDate) ) {
                // No previous coupon date — fake a year before.
                refStartDate = cashFlowDate.sub(new Period(1, TimeUnit.Years));
            } else {
                refStartDate = lastDate;
            }
            refEndDate = cashFlowDate;
        }

        if ( coupon != null && !lastDate.equals(coupon.accrualStartDate()) ) {
            final double couponPeriod = dc.yearFraction(coupon.accrualStartDate(), cashFlowDate, refStartDate,
                    refEndDate);
            final double accruedPeriod = dc.yearFraction(coupon.accrualStartDate(), lastDate, refStartDate, refEndDate);
            return couponPeriod - accruedPeriod;
        }
        return dc.yearFraction(lastDate, cashFlowDate, refStartDate, refEndDate);
    }

    /**
     * Stepwise IRR-discounted NPV. Mirrors C++
     * {@code CashFlows::npv(leg, InterestRate, includeSettlementDateFlows, settlementDate, npvDate)}
     * (cashflows.cpp:811-853): walk the leg in order, accumulating the stepwise time
     * {@code t += getStepwiseDiscountTime(...)} between consecutive cashflows and discount each amount by
     * {@code y.discountFactor(t)}.
     *
     * @param leg                        the cash-flow leg
     * @param y                          the constant-yield interest rate
     * @param includeSettlementDateFlows whether a flow on the settlement date is still pending (true) or already paid
     *                                   (false)
     * @param settlementDate             the settlement date; if null, the evaluation date is used
     * @param npvDate                    the date the NPV is discounted to; if null, defaults to {@code settlementDate}
     */
    public static double npv(final Leg leg, final InterestRate y, final boolean includeSettlementDateFlows,
            Date settlementDate, Date npvDate) {
        if ( leg.isEmpty() ) {
            return 0.0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }

        double npv = 0.0;
        double discount = 1.0;
        Date lastDate = npvDate;
        final DayCounter dc = y.dayCounter();
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf.hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                continue;
            }
            // tradingExCoupon not modelled in the JQuant Bond instance —
            // QuantLib defaults the field to zero ex-dividend days, so the
            // path is equivalent to "always include" here.
            final double amount = cf.amount();
            final double b = y.discountFactor(stepwiseDiscountTime(cf, dc, npvDate, lastDate));
            discount *= b;
            lastDate = cf.date();
            npv += amount * discount;
        }
        return npv;
    }

    /**
     * Stepwise modified duration. Mirrors C++ private helper {@code modifiedDuration} (cashflows.cpp:642-707).
     */
    private static double modifiedDurationStepwise(final Leg leg, final InterestRate y,
            final boolean includeSettlementDateFlows, Date settlementDate, Date npvDate) {
        if ( leg.isEmpty() ) {
            return 0.0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }
        double P = 0.0;
        double t = 0.0;
        double dPdy = 0.0;
        final double r = y.rate();
        final int N = y.frequency().toInteger();
        Date lastDate = npvDate;
        final DayCounter dc = y.dayCounter();
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf.hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                continue;
            }
            final double c = cf.amount();
            t += stepwiseDiscountTime(cf, dc, npvDate, lastDate);
            final double B = y.discountFactor(t);
            P += c * B;
            switch ( y.compounding() ) {
            case Simple:
                dPdy -= c * B * B * t;
                break;
            case Compounded:
                dPdy -= c * t * B / (1.0 + r / N);
                break;
            case Continuous:
                dPdy -= c * B * t;
                break;
            case SimpleThenCompounded:
                if ( t <= 1.0 / N ) {
                    dPdy -= c * B * B * t;
                } else {
                    dPdy -= c * t * B / (1.0 + r / N);
                }
                break;
            default:
                throw new IllegalArgumentException("unknown compounding (" + y.compounding() + ")");
            }
            lastDate = cf.date();
        }
        if ( P == 0.0 ) {
            return 0.0;
        }
        return -dPdy / P;
    }

    /**
     * Stepwise simple duration. Mirrors C++ private helper {@code simpleDuration} (cashflows.cpp:620-640).
     */
    private static double simpleDurationStepwise(final Leg leg, final InterestRate y,
            final boolean includeSettlementDateFlows, Date settlementDate, Date npvDate) {
        if ( leg.isEmpty() ) {
            return 0.0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }
        double P = 0.0;
        double dPdy = 0.0;
        double t = 0.0;
        Date lastDate = npvDate;
        final DayCounter dc = y.dayCounter();
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf.hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                continue;
            }
            final double c = cf.amount();
            t += stepwiseDiscountTime(cf, dc, npvDate, lastDate);
            final double B = y.discountFactor(t);
            P += c * B;
            dPdy += t * c * B;
            lastDate = cf.date();
        }
        if ( P == 0.0 ) {
            return 0.0;
        }
        return dPdy / P;
    }

    /**
     * Stepwise duration dispatcher. Mirrors C++
     * {@code CashFlows::duration(leg, InterestRate, Duration, includeSettlementDateFlows, settlementDate, npvDate)}
     * (cashflows.cpp:924-955).
     */
    public static double duration(final Leg leg, final InterestRate y, final Duration type,
            final boolean includeSettlementDateFlows, final Date settlementDate, final Date npvDate) {
        switch ( type ) {
        case Simple:
            return simpleDurationStepwise(leg, y, includeSettlementDateFlows, settlementDate, npvDate);
        case Modified:
            return modifiedDurationStepwise(leg, y, includeSettlementDateFlows, settlementDate, npvDate);
        case Macaulay: {
            QL.require(y.compounding() == Compounding.Compounded, "compounded rate required");
            final double mod = modifiedDurationStepwise(leg, y, includeSettlementDateFlows, settlementDate, npvDate);
            return (1.0 + y.rate() / y.frequency().toInteger()) * mod;
        }
        default:
            throw new IllegalArgumentException("unknown duration type");
        }
    }

    /**
     * Stepwise convexity. Mirrors C++
     * {@code CashFlows::convexity(leg, InterestRate, includeSettlementDateFlows, settlementDate, npvDate)}
     * (cashflows.cpp:957-1041).
     */
    public static double convexity(final Leg leg, final InterestRate y, final boolean includeSettlementDateFlows,
            Date settlementDate, Date npvDate) {
        if ( leg.isEmpty() ) {
            return 0.0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }
        final DayCounter dc = y.dayCounter();
        double P = 0.0;
        double t = 0.0;
        double d2Pdy2 = 0.0;
        final double r = y.rate();
        final int N = y.frequency().toInteger();
        Date lastDate = npvDate;
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf.hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                continue;
            }
            final double c = cf.amount();
            t += stepwiseDiscountTime(cf, dc, npvDate, lastDate);
            final double B = y.discountFactor(t);
            P += c * B;
            switch ( y.compounding() ) {
            case Simple:
                d2Pdy2 += c * 2.0 * B * B * B * t * t;
                break;
            case Compounded:
                d2Pdy2 += c * B * t * (N * t + 1) / (N * (1 + r / N) * (1 + r / N));
                break;
            case Continuous:
                d2Pdy2 += c * B * t * t;
                break;
            case SimpleThenCompounded:
                if ( t <= 1.0 / N ) {
                    d2Pdy2 += c * 2.0 * B * B * B * t * t;
                } else {
                    d2Pdy2 += c * B * t * (N * t + 1) / (N * (1 + r / N) * (1 + r / N));
                }
                break;
            default:
                throw new IllegalArgumentException("unknown compounding (" + y.compounding() + ")");
            }
            lastDate = cf.date();
        }
        if ( P == 0.0 ) {
            return 0.0;
        }
        return d2Pdy2 / P;
    }

    //
    // C++-style static overloads (mirror QuantLib::CashFlows static API)
    //

    /**
     * Iterator-style "next cash flow" helper used by {@link #accruedAmount(Leg, boolean, Date)}; returns the index of
     * the first {@code CashFlow} not yet occurred relative to {@code settlementDate}, or {@code leg.size()} if none.
     *
     * <p>Mirrors C++ {@code CashFlows::nextCashFlow}
     * (cashflows.cpp:101-117).
     */
    public static int nextCashFlow(final Leg leg, final boolean includeSettlementDateFlows, Date settlementDate) {
        if ( leg.isEmpty() ) {
            return 0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        for ( int i = 0; i < leg.size(); ++i ) {
            if ( !leg.get(i).hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                return i;
            }
        }
        return leg.size();
    }

    //
    // C++-style stepwise NPV / duration / convexity overloads.
    //
    // Faithful Java port of the private helpers in C++
    // {@code ql/cashflows/cashflows.cpp:567-707} (getStepwiseDiscountTime,
    // npv(InterestRate), modifiedDuration, simpleDuration, macaulayDuration,
    // convexity). The legacy {@link #npv(Leg, InterestRate, Date)} /
    // {@link #duration(Leg, InterestRate, Duration, Date)} /
    // {@link #convexity(Leg, InterestRate, Date)} instance methods use the
    // additive {@code yearFraction(settlement, paymentDate)} formulation,
    // which diverges from the C++ stepwise accumulation for non-additive
    // day counters such as {@link org.jquantlib.daycounters.Thirty360} (USA
    // convention) when settlement falls on the 31st of a month
    // (test-suite/bonds.cpp::testThirty360BondWithSettlementOn31st).
    //
    // The legacy methods are retained intact for backwards compatibility:
    // other callers (HaganIrregularSwaptionEngine, BlackCallableFixedRate
    // BondEngine, CapFloor strike helpers) depend on the additive form.
    //
    // Phase 5e.5b-CFC-d-35: promoted from private helpers previously in
    // {@code BondFunctions} so callers can share the stepwise semantics.
    //

    /**
     * Accrued amount of a leg. Mirrors C++
     * {@code CashFlows::accruedAmount(leg, includeSettlementDateFlows, settlementDate)} (cashflows.cpp:376-393).
     *
     * <p>Sums {@link Coupon#accruedAmount(Date)} across all coupons whose
     * payment date equals the next cash-flow's payment date.
     */
    public static double accruedAmount(final Leg leg, final boolean includeSettlementDateFlows, Date settlementDate) {
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        final int idx = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if ( idx >= leg.size() ) {
            return 0.0;
        }
        final Date paymentDate = leg.get(idx).date();
        double result = 0.0;
        for ( int i = idx; i < leg.size() && leg.get(i).date().equals(paymentDate); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf instanceof Coupon ) {
                result += ((Coupon) cf).accruedAmount(settlementDate);
            }
        }
        return result;
    }

    /**
     * Accrued amount with default settlement date. Mirrors C++
     * {@code CashFlows::accruedAmount(leg, includeSettlementDateFlows)} (header default
     * {@code settlementDate = Date()}) which dispatches to the three-arg form and falls back to
     * {@link Settings#evaluationDate()}. Phase 5e.5b-CFC-d-97.
     */
    public static double accruedAmount(final Leg leg, final boolean includeSettlementDateFlows) {
        return accruedAmount(leg, includeSettlementDateFlows, new Date());
    }

    /**
     * Accrued time-period of a leg. Mirrors C++
     * {@code CashFlows::accruedPeriod(leg, includeSettlementDateFlows, settlementDate)} (cashflows.cpp:340-356).
     * Returns the day-count fraction from the active coupon's accrual-start date up to {@code settlementDate}, using
     * the coupon's day counter and reference period; returns 0 when no still-pending cashflow exists.
     *
     * <p>Inlines C++ {@code Coupon::accruedPeriod(d)} (coupon.cpp:57-69)
     * since the JQL {@link Coupon} base class doesn't yet expose that accessor. Phase 5e.5b-CFC-d-97.
     */
    public static double accruedPeriod(final Leg leg, final boolean includeSettlementDateFlows, Date settlementDate) {
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        final int idx = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if ( idx >= leg.size() ) {
            return 0.0;
        }
        final Date paymentDate = leg.get(idx).date();
        for ( int i = idx; i < leg.size() && leg.get(i).date().equals(paymentDate); ++i ) {
            final CashFlow cf = leg.get(i);
            if (cf instanceof Coupon cp) {
                if ( settlementDate.le(cp.accrualStartDate()) || settlementDate.gt(cp.date()) ) {
                    return 0.0;
                }
                final Date exDate = cp.exCouponDate();
                final boolean tradingEx = exDate != null && !exDate.isNull() && settlementDate.ge(exDate);
                if ( tradingEx ) {
                    final Date hi = settlementDate.ge(cp.accrualEndDate()) ? settlementDate : cp.accrualEndDate();
                    return -cp.dayCounter()
                            .yearFraction(settlementDate, hi, cp.referencePeriodStart(), cp.referencePeriodEnd());
                }
                final Date hi = settlementDate.le(cp.accrualEndDate()) ? settlementDate : cp.accrualEndDate();
                return cp.dayCounter()
                        .yearFraction(cp.accrualStartDate(), hi, cp.referencePeriodStart(), cp.referencePeriodEnd());
            }
        }
        return 0.0;
    }

    /**
     * Accrued time-period with default settlement date. Mirrors C++ header default {@code settlementDate = Date()}.
     * Phase 5e.5b-CFC-d-97.
     */
    public static double accruedPeriod(final Leg leg, final boolean includeSettlementDateFlows) {
        return accruedPeriod(leg, includeSettlementDateFlows, new Date());
    }

    /**
     * Accrued days of a leg. Mirrors C++
     * {@code CashFlows::accruedDays(leg, includeSettlementDateFlows, settlementDate)} (cashflows.cpp:358-374). Returns
     * the day count from the active coupon's accrual-start date up to {@code settlementDate}, using the coupon's day
     * counter; returns 0 when no still-pending cashflow exists.
     *
     * <p>Inlines C++ {@code Coupon::accruedDays(d)} (coupon.cpp:71-78).
     * Phase 5e.5b-CFC-d-97.
     */
    public static long accruedDays(final Leg leg, final boolean includeSettlementDateFlows, Date settlementDate) {
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        final int idx = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if ( idx >= leg.size() ) {
            return 0L;
        }
        final Date paymentDate = leg.get(idx).date();
        for ( int i = idx; i < leg.size() && leg.get(i).date().equals(paymentDate); ++i ) {
            final CashFlow cf = leg.get(i);
            if (cf instanceof Coupon cp) {
                if ( settlementDate.le(cp.accrualStartDate()) || settlementDate.gt(cp.date()) ) {
                    return 0L;
                }
                final Date hi = settlementDate.le(cp.accrualEndDate()) ? settlementDate : cp.accrualEndDate();
                return cp.dayCounter().dayCount(cp.accrualStartDate(), hi);
            }
        }
        return 0L;
    }

    /**
     * Accrued days with default settlement date. Mirrors C++ header default {@code settlementDate = Date()}. Phase
     * 5e.5b-CFC-d-97.
     */
    public static long accruedDays(final Leg leg, final boolean includeSettlementDateFlows) {
        return accruedDays(leg, includeSettlementDateFlows, new Date());
    }

    /**
     * NPV of the cash flows under a Z-spread on the discount curve. Mirrors C++
     * {@code CashFlows::npv(leg, discountCurve, zSpread, comp, freq, includeSettlementDateFlows, settlementDate,
     * npvDate)} (cashflows.cpp:1144-1173). The spread is added on top of the curve's zero rate under the given
     * {@code (compounding, frequency)} pair.
     *
     * <p>Phase 5e.5b-CFC-d-98.
     */
    public static double npv(final Leg leg, final YieldTermStructure discountCurve, final double zSpread,
            final Compounding compounding, final Frequency frequency, final boolean includeSettlementDateFlows,
            Date settlementDate, Date npvDate) {
        if ( leg.isEmpty() ) {
            return 0.0;
        }
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }
        final Handle< YieldTermStructure > discountCurveHandle = new Handle< YieldTermStructure >(discountCurve);
        final Handle< Quote > zSpreadQuoteHandle = new Handle< Quote >(new SimpleQuote(zSpread));
        final ZeroSpreadedTermStructure spreadedCurve = new ZeroSpreadedTermStructure(discountCurveHandle,
                zSpreadQuoteHandle, compounding, frequency);
        return npv(leg, spreadedCurve, includeSettlementDateFlows, settlementDate, npvDate);
    }

    /**
     * Implied Z-spread. Mirrors C++
     * {@code CashFlows::zSpread(leg, npv, discount, compounding, frequency, includeSettlementDateFlows, settlementDate,
     * npvDate, accuracy, maxIterations, guess)} (cashflows.cpp:1188-1223): root-finds the spread such that the
     * z-spread-discounted NPV matches the target {@code npv}, using {@link Brent} with the canonical defaults
     * {@code (accuracy=1e-10, maxIterations=100, guess=0.0, step=0.01)}.
     *
     * <p>Phase 5e.5b-CFC-d-98.
     */
    public static double zSpread(final Leg leg, final double npv, final YieldTermStructure discount,
            final Compounding compounding, final Frequency frequency, final boolean includeSettlementDateFlows,
            Date settlementDate, Date npvDate, final double accuracy, final int maxIterations, final double guess) {
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        if ( npvDate == null || npvDate.isNull() ) {
            npvDate = settlementDate;
        }
        final SimpleQuote zSpreadQuote = new SimpleQuote(0.0);
        final Handle< YieldTermStructure > discountHandle = new Handle< YieldTermStructure >(discount);
        final Handle< Quote > zSpreadHandle = new Handle< Quote >(zSpreadQuote);
        final ZeroSpreadedTermStructure spreadedCurve = new ZeroSpreadedTermStructure(discountHandle, zSpreadHandle,
                compounding, frequency);
        final Date sd = settlementDate;
        final Date nd = npvDate;
        final Ops.DoubleOp objFunction = new Ops.DoubleOp() {
            @Override
            public double op(final double s) {
                zSpreadQuote.setValue(s);
                final double calc = npv(leg, spreadedCurve, includeSettlementDateFlows, sd, nd);
                return npv - calc;
            }
        };
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxIterations);
        final double step = 0.01;
        return solver.solve(objFunction, accuracy, guess, step);
    }

    /**
     * Convenience overload with the C++ canonical defaults {@code (accuracy=1e-10, maxIterations=100, guess=0.0)}.
     */
    public static double zSpread(final Leg leg, final double npv, final YieldTermStructure discount,
            final Compounding compounding, final Frequency frequency, final boolean includeSettlementDateFlows,
            final Date settlementDate, final Date npvDate) {
        return zSpread(leg, npv, discount, compounding, frequency, includeSettlementDateFlows, settlementDate, npvDate,
                1.0e-10, 100, 0.0);
    }

    public Date startDate(final Leg cashflows) {
        Date d = Date.maxDate();
        for ( int i = 0; i < cashflows.size(); ++i ) {
            final Coupon c = (Coupon) cashflows.get(i);
            if ( c != null ) {
                d = Date.min(c.accrualStartDate(), d);
            }
        }
        QL.ensure(d.lt(Date.maxDate()), not_enough_information_available); // QA:[RG]::verified
        return d;
    }

    public Date maturityDate(final Leg cashflows) {
        Date d = Date.minDate();
        for ( int i = 0; i < cashflows.size(); i++ ) {
            d = Date.max(d, cashflows.get(i).date());
        }
        QL.ensure(d.gt(Date.minDate()), no_cashflows);
        return d;
    }

    public double npv(final Leg cashflows, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate) {
        return npv(cashflows, discountCurve, settlementDate, npvDate, 0);
    }

    /**
     * NPV of the cash flows.
     * <p>
     * The NPV is the sum of the cash flows, each discounted according to the given term structure.
     *
     * @param cashflows
     * @param discountCurve
     * @param settlementDate
     * @param npvDate
     * @param exDividendDays
     * @return
     */
    public double npv(final Leg cashflows, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate, final int exDividendDays) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = discountCurve.currentLink().referenceDate();
        }

        double totalNPV = 0.0;
        for ( int i = 0; i < cashflows.size(); ++i ) {
            if ( !cashflows.get(i).hasOccurred(date.add(exDividendDays)) ) {
                totalNPV += cashflows.get(i).amount() * discountCurve.currentLink().discount(cashflows.get(i).date());
            }
        }

        if ( npvDate.isNull() )
            return totalNPV;
        else
            return totalNPV / discountCurve.currentLink().discount(npvDate);
    }

    //
    // Z-spread functions
    //
    // Faithful Java port of CashFlows::npv(zSpread) and CashFlows::zSpread
    // from ql/cashflows/cashflows.cpp v1.42.1 (lines 1144-1240). Both wrap
    // a {@link ZeroSpreadedTermStructure} over the supplied discount curve
    // and re-use the static NPV machinery already in this class.
    //

    public double npv(final Leg leg, final Handle< YieldTermStructure > discountCurve) {
        return npv(leg, discountCurve, new Date(), new Date(), 0);
    }

    /**
     * NPV of the cash flows.
     * <p>
     * The NPV is the sum of the cash flows, each discounted according to the given constant interest rate. The result
     * is affected by the choice of the interest-rate compounding and the relative frequency and day counter.
     */
    public double npv(final Leg cashflows, final InterestRate irr, final Date settlementDate) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = new Settings().evaluationDate();
        }

        final YieldTermStructure flatRate = new FlatForward(date, irr.rate(), irr.dayCounter(), irr.compounding(),
                irr.frequency());
        return npv(cashflows, new Handle< YieldTermStructure >(flatRate), date, date, 0);
    }

    public double npv(final Leg leg, final InterestRate interestRate) {
        return npv(leg, interestRate, new Date());
    }

    /*
     * BPS Functions implied from quantlib default variables
     * since we cannot assign variables to defaults in the parameter lists of functions,
     * we use function chaining to effectively assign a single default at each level.
     */

    public double bps(final Leg cashflows, final Handle< YieldTermStructure > discountCurve) {
        // default variable of settlement date
        return bps(cashflows, discountCurve, new Settings().evaluationDate());
    }

    public double bps(final Leg cashflows, final Handle< YieldTermStructure > discountCurve,
            final Date settlementDate) {
        // C++ default: npvDate = Date() (null) — no NPV-date normalization.
        // Previously passed settlementDate as npvDate, which caused
        // BPSCalculator.result() to divide by discount(settlementDate). When
        // the curve reference date is after the settlementDate (e.g. one-day
        // offset for InterpolatedZeroCurve) this threw "date before reference
        // date". Matches C++ CashFlows::bps(leg, curve, settlementDate) which
        // defaults npvDate = Date(). Phase 2y A.1 align.
        return bps(cashflows, discountCurve, settlementDate, new Date());
    }

    public double bps(final Leg cashflows, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate) {
        // default variable of ex-dividend days
        return bps(cashflows, discountCurve, settlementDate, npvDate, 0);
    }


    /*
     * Acutal BPS Functions ported from quantlib
     */

    /**
     * Basis-point sensitivity of the cash flows.
     * <p>
     * The result is the change in NPV due to a uniform 1-basis-point change in the rate paid by the cash flows. The
     * change for each coupon is discounted according to the given term structure.
     */
    public double bps(final Leg cashflows, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate, final int exDividendDays) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = discountCurve.currentLink().referenceDate();
        }

        final BPSCalculator calc = new BPSCalculator(discountCurve, npvDate);
        for ( int i = 0; i < cashflows.size(); ++i ) {
            if ( !cashflows.get(i).hasOccurred(date.add(exDividendDays)) ) {
                cashflows.get(i).accept(calc);
            }
        }
        return basisPoint_ * calc.result();
    }

    /**
     * Basis-point sensitivity of the cash flows.
     * <p>
     * The result is the change in NPV due to a uniform 1-basis-point change in the rate paid by the cash flows. The
     * change for each coupon is discounted according to the given term structure.
     */
    public double bps(final Leg cashflows, final InterestRate irr, Date settlementDate) {
        if ( settlementDate.isNull() ) {
            settlementDate = new Settings().evaluationDate();
        }
        final YieldTermStructure flatRate = new FlatForward(settlementDate, irr.rate(), irr.dayCounter(),
                irr.compounding(), irr.frequency());
        return bps(cashflows, new Handle< YieldTermStructure >(flatRate), settlementDate, settlementDate);
    }

    /**
     * Non-sensitive NPV: the present value of all cash-flows that are not {@link Coupon}s (typically the redemption
     * leg), i.e. the portion of the NPV that does not move with a uniform 1-bp shift in the coupon rate. Mirrors the
     * {@code nonSensNPV_} accumulator inside C++ {@code BPSCalculator::visit(CashFlow&)} (cashflows.cpp:410-413),
     * divided by {@code discount(npvDate)} to keep parity with {@link #npv(Leg, Handle, Date, Date, int)} and
     * {@link #bps(Leg, Handle, Date, Date, int)}.
     *
     * <p>Phase 5e.5b-CFC-d-118 — extracted so {@link #atmRate} can subtract
     * the redemption NPV per C++ {@code CashFlows::atmRate} (cashflows.cpp: 509-551).
     */
    public double nonSensNPV(final Leg leg, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate, final int exDividendDays) {
        Date date = settlementDate;
        if ( date.isNull() ) {
            date = discountCurve.currentLink().referenceDate();
        }
        double total = 0.0;
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            if ( cf.hasOccurred(date.add(exDividendDays)) ) {
                continue;
            }
            // C++ BPSCalculator visits CashFlow (non-Coupon) here. Coupons go
            // through the Coupon-overload (which only fills bps_).
            if ( !(cf instanceof Coupon) ) {
                total += cf.amount() * discountCurve.currentLink().discount(cf.date());
            }
        }
        if ( npvDate.isNull() ) {
            return total;
        }
        return total / discountCurve.currentLink().discount(npvDate);
    }

    /**
     * At-the-money rate of the cash flows.
     * <p>
     * The result is the fixed rate for which a fixed rate cash flow vector, equivalent to the input vector, has the
     * required NPV according to the given term structure. If the required NPV is not given, the input cash flow
     * vector's NPV is used instead.
     *
     * <p>Phase 5e.5b-CFC-d-118 align: mirrors C++ {@code CashFlows::atmRate}
     * (cashflows.cpp:509-551) by splitting out the non-sensitive (redemption) NPV before dividing by BPS. Previously
     * returned {@code basisPoint_ * npv / bps} which double-counted the redemption leg when the leg contained a
     * redemption flow.
     */
    public double atmRate(final Leg leg, final Handle< YieldTermStructure > discountCurve, final Date settlementDate,
            final Date npvDate, final int exDividendDays, double npv) {
        final double bps = bps(leg, discountCurve, settlementDate, npvDate, exDividendDays);
        if ( npv == 0 ) {
            npv = npv(leg, discountCurve, settlementDate, npvDate, exDividendDays);
        }
        final double nonSens = nonSensNPV(leg, discountCurve, settlementDate, npvDate, exDividendDays);
        QL.require(bps != 0.0, "null bps: impossible atm rate");
        return basisPoint_ * (npv - nonSens) / bps;
    }

    public double atmRate(final Leg leg, final Handle< YieldTermStructure > discountCurve) {
        return atmRate(leg, discountCurve, new Date(), new Date(), 0, 0);
    }

    /**
     * Internal rate of return.
     * <p>
     * The IRR is the interest rate at which the NPV of the cash flows equals the given market price. The function
     * verifies the theoretical existance of an IRR and numerically establishes the IRR to the desired precision.
     */
    public double irr(final Leg cashflows, final double marketPrice, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlementDate, final double tolerance,
            final int maxIterations, final double guess) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = new Settings().evaluationDate();
        }

        // depending on the sign of the market price, check that cash
        // flows of the opposite sign have been specified (otherwise
        // IRR is nonsensical.)

        int lastSign = sign(-marketPrice), signChanges = 0;
        for ( int i = 0; i < cashflows.size(); ++i ) {
            if ( !cashflows.get(i).hasOccurred(date) ) {
                final int thisSign = sign(cashflows.get(i).amount());
                if ( lastSign * thisSign < 0 ) {
                    signChanges++;
                }
                if ( thisSign != 0 ) {
                    lastSign = thisSign;
                }
            }
        }

        QL.ensure(signChanges > 0, infeasible_cashflow); // QA:[RG]::verified

        /*
         * THIS COMMENT COMES UNMODIFIED FROM QL/C++ SOURCES
         *
         * The following is commented out due to the lack of a QL_WARN macro
         *
         * if (signChanges > 1) { // Danger of non-unique solution // Check the
         * aggregate cash flows (Norstrom) Real aggregateCashFlow = marketPrice;
         * signChanges = 0; for (Size i = 0; i < cashflows.size(); ++i) { Real
         * nextAggregateCashFlow = aggregateCashFlow + cashflows[i]->amount();
         * if (aggregateCashFlow * nextAggregateCashFlow < 0.0) signChanges++;
         * aggregateCashFlow = nextAggregateCashFlow; } if (signChanges > 1)
         * QL_WARN( "danger of non-unique solution"); }
         */

        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxIterations);
        return solver.solve(new IRRFinder(cashflows, marketPrice, dayCounter, compounding, frequency, date), tolerance,
                guess, guess / 10.0);
    }

    public double irr(final Leg leg, final double marketPrice, final DayCounter dayCounter,
            final Compounding compounding) {
        return irr(leg, marketPrice, dayCounter, compounding, Frequency.NoFrequency, new Date(), 1.0e-10, 10000, 0.05);
    }

    /**
     * Cash-flow duration.
     * <p>
     * The simple duration of a string of cash flows is defined as
     * {@latex[ D_ \mathrm{simple} = \frac{\sum t_i c_i B(t_i)}{\sum c_i B(t_i)} } where {@latex$ c_i } is the amount of
     * the {@latex$ i }-th cash flow, {@latex$ t_i } is its payment time, and {@latex$ B(t_i) } is the corresponding
     * discount according to the passed yield.
     * <p>
     * The modified duration is defined as {@latex[ D_ \mathrm{modified} = -\frac{1}{P} \frac{\partial P}{\partial y} }
     * where {@latex$ P }is the present value of the cash flows according to the given IRR {@latex$ y }.
     * <p>
     * The Macaulay duration is defined for a compounded IRR as
     * {@latex[ D_ \mathrm{Macaulay} = \left( 1 + \frac{y}{N} \right) D_{\mathrm{modified}} } where {@latex$ y } is the
     * IRR and {@latex$ N } is the number of cash flows per year.
     */
    public double duration(final Leg leg, final InterestRate y, final Duration duration, final Date settlementDate) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = new Settings().evaluationDate();
        }

        switch ( duration ) {
        case Simple:
            return simpleDuration(leg, y, date);
        case Modified:
            return modifiedDuration(leg, y, date);
        case Macaulay:
            return macaulayDuration(leg, y, date);
        default:
            throw new LibraryException(unknown_duration_type); // QA:[RG]::verified
        }
    }

    public double duration(final Leg leg, final InterestRate y) {
        return duration(leg, y, Duration.Modified, new Date());
    }

    /**
     * Cash-flow convexity
     * <p>
     * The convexity of a string of cash flows is defined as
     * {@latex[ C = \frac{1}{P} \frac{\partial^2 P}{\partial y^2} } where {@latex$ P } is the present value of the cash
     * flows according to the given IRR {@latex$ y }.
     */
    public double convexity(final Leg cashFlows, final InterestRate rate, final Date settlementDate) {

        Date date = settlementDate;
        if ( date.isNull() ) {
            date = new Settings().evaluationDate();
        }

        final DayCounter dayCounter = rate.dayCounter();

        double P = 0.0;
        double d2Pdy2 = 0.0;
        final double y = rate.rate();
        final int N = rate.frequency().toInteger();

        for ( int i = 0; i < cashFlows.size(); ++i ) {
            if ( !cashFlows.get(i).hasOccurred(date) ) {
                final double t = dayCounter.yearFraction(date, cashFlows.get(i).date());
                final double c = cashFlows.get(i).amount();
                final double B = rate.discountFactor(t);

                P += c * B;
                switch ( rate.compounding() ) {
                case Simple:
                    d2Pdy2 += c * 2.0 * B * B * B * t * t;
                    break;
                case Compounded:
                    d2Pdy2 += c * B * t * (N * t + 1) / (N * (1 + y / N) * (1 + y / N));
                    break;
                case Continuous:
                    d2Pdy2 += c * B * t * t;
                    break;
                case SimpleThenCompounded:
                default:
                    throw new LibraryException(unsupported_compounding_type); // QA:[RG]::verified
                }
            }
        }

        if ( P == 0.0 )
            return 0.0; // no cashflows
        return d2Pdy2 / P;
    }

    public double convexity(final Leg leg, final InterestRate y) {
        return convexity(leg, y, new Date());
    }

    private double simpleDuration(final Leg cashflows, final InterestRate rate, final Date settlementDate) {

        double P = 0.0;
        double tP = 0.0;

        for ( int i = 0; i < cashflows.size(); ++i ) {
            if ( !cashflows.get(i).hasOccurred(settlementDate) ) {
                final double t = rate.dayCounter().yearFraction(settlementDate, cashflows.get(i).date());
                final double c = cashflows.get(i).amount();
                final double B = rate.discountFactor(t);

                P += c * B;
                tP += t * c * B;
            }
        }

        if ( P == 0.0 )
            // no cashflows
            return 0.0;

        return tP / P;
    }

    private double modifiedDuration(final Leg cashflows, final InterestRate rate, final Date settlementDate) {

        double P = 0.0;
        double dPdy = 0.0;
        final double y = rate.rate();
        final int N = rate.frequency().toInteger();

        for ( int i = 0; i < cashflows.size(); ++i ) {
            if ( !cashflows.get(i).hasOccurred(settlementDate) ) {
                final double t = rate.dayCounter().yearFraction(settlementDate, cashflows.get(i).date());
                final double c = cashflows.get(i).amount();
                final double B = rate.discountFactor(t);

                P += c * B;
                switch ( rate.compounding() ) {
                case Simple:
                    dPdy -= c * B * B * t;
                    break;
                case Compounded:
                    dPdy -= c * B * t / (1 + y / N);
                    break;
                case Continuous:
                    dPdy -= c * B * t;
                    break;
                case SimpleThenCompounded:
                default:
                    throw new LibraryException(unsupported_compounding_type); // QA:[RG]::verified
                }
            }
        }

        if ( P == 0.0 )
            // no cashflows
            return 0.0;
        return -dPdy / P;
    }

    private double macaulayDuration(final Leg cashflows, final InterestRate rate, final Date settlementDate) {

        final double y = rate.rate();
        final int N = rate.frequency().toInteger();
        QL.require(rate.compounding().equals(Compounding.Compounded), compounded_rate_required);
        QL.require(N >= 1, unsupported_frequency);
        return (1 + y / N) * modifiedDuration(cashflows, rate, settlementDate);
    }

    private int sign(final double x) {
        if ( x == 0 )
            return 0;
        else if ( x > 0 )
            return 1;
        else
            return -1;
    }

    final public int previousCashFlow(final Leg leg) {
        return previousCashFlow(leg, new Date());
    }

    final public int previousCashFlow(final Leg leg, Date refDate) {
        if ( refDate.isNull() ) {
            refDate = new Settings().evaluationDate();
        }

        if ( !(leg.get(0).hasOccurred(refDate)) )
            return leg.size();

        final int i = nextCashFlowIndex(leg, refDate);
        final Date beforeLastPaymentDate = leg.get(i - 1).date();// (*--i)->date()-1;
        return nextCashFlowIndex(leg, beforeLastPaymentDate);
    }

    final public double previousCouponRate(final Leg cashFlows) {
        return previousCouponRate(cashFlows, new Date());
    }

    final public double previousCouponRate(final Leg cashFlows, final Date settlement) {
        final int cf = previousCashFlow(cashFlows, settlement);
        return couponRate(cashFlows, cashFlows, cf);
    }

    final public double nextCouponRate(final Leg leg) {
        return nextCouponRate(leg, new Date());
    }

    final public double nextCouponRate(final Leg cashFlows, final Date settlement) {
        final int cf = nextCashFlowIndex(cashFlows, settlement);
        return couponRate(cashFlows, cashFlows, cf);
    }

    /**
     * NOTE: should return null when no cashflow could be found!
     *
     * @param cashFlows
     * @param settlement
     * @return
     */
    final public CashFlow nextCashFlow(final Leg cashFlows, Date settlement) {
        if ( settlement.isNull() ) {
            settlement = new Settings().evaluationDate();
        }
        for ( int i = 0; i < cashFlows.size(); ++i ) {
            // the first coupon paying after d is the one we're after
            if ( !cashFlows.get(i).hasOccurred(settlement) )
                return cashFlows.get(i);
        }
        return null;// cashFlows.get(cashFlows.size());
    }

    /**
     * NOTE: returns the index! for cashflow.end() the returned index would throw a index out of bounds exception
     *
     * @param cashFlows
     * @param settlement
     * @return
     */
    final public int nextCashFlowIndex(final Leg cashFlows, Date settlement) {
        if ( settlement.isNull() ) {
            settlement = new Settings().evaluationDate();
        }
        for ( int i = 0; i < cashFlows.size(); ++i ) {
            // the first coupon paying after d is the one we're after
            if ( !cashFlows.get(i).hasOccurred(settlement) )
                return i;
        }
        return cashFlows.size();
    }

    final public CashFlow nextCashFlow(final Leg cashFlows) {
        return nextCashFlow(cashFlows, new Date());
    }

    /**
     * Yield value of a basis point The yield value of a one basis point change in price is the derivative of the yield
     * with respect to the price multiplied by 0.01
     *
     * @param leg
     * @param y
     * @param settlmentDate
     * @return
     */
    final public double yieldValueBasisPoint(final Leg leg, final InterestRate y, final Date settlementDate) {
        final double shift = 0.01;

        final double dirtyPrice = npv(leg, y, settlementDate);
        final double modifiedDuration = duration(leg, y, Duration.Modified, settlementDate);

        return (1.0 / (-dirtyPrice * modifiedDuration)) * shift;
    }

    final public double yieldValueBasisPoint(final Leg leg, final InterestRate y) {
        return yieldValueBasisPoint(leg, y, new Date());

    }

    // utility functions
    final public double couponRate(final Leg leg, final Leg iteratorLeg, final int iteratorIndex) {
        if ( iteratorLeg.size() <= iteratorIndex )
            return 0.0;

        final Date paymentDate = iteratorLeg.get(iteratorIndex).date();
        boolean firstCouponFound = false;
        /* @Real */
        double nominal = Constants.NULL_REAL;
        /* @Time */
        double accrualPeriod = Constants.NULL_TIME;
        DayCounter dc = null;
        /* @Rate */
        double result = 0.0;

        for ( int i = iteratorIndex; i < leg.size(); i++ ) {
            final CashFlow cf = iteratorLeg.get(i);
            if ( cf.date().eq(paymentDate) ) {
                if (cf instanceof Coupon cp) {
                    if ( firstCouponFound ) {
                        QL.require(
                                nominal == cp.nominal() && accrualPeriod == cp.accrualPeriod() && dc == cp.dayCounter(),
                                "cannot aggregate two different coupons");
                    } else {
                        firstCouponFound = true;
                        nominal = cp.nominal();
                        accrualPeriod = cp.accrualPeriod();
                        dc = cp.dayCounter();
                    }
                    result += cp.rate();
                }
            }
        }
        QL.ensure((firstCouponFound), "next cashflow (" + paymentDate + ") is not a coupon");
        return result;
    }

    //
    // private methods
    //

    /**
     * Basis-point value Obtained by setting dy = 0.0001 in the 2nd-order Taylor series expansion.
     *
     * @param leg
     * @param y
     * @param settlementDate
     * @return
     */
    final private double basisPointValue(final Leg leg, final InterestRate y, final Date settlementDate) {
        /* @Real */
        final double shift = 0.0001;
        /* @Real */
        final double dirtyPrice = npv(leg, y, settlementDate);
        /* @Real */
        final double modifiedDuration = duration(leg, y, Duration.Modified, settlementDate);
        /* @Real */
        final double convexity = convexity(leg, y, settlementDate);

        /* @Real */
        double delta = -modifiedDuration * dirtyPrice;

        /* @Real */
        double gamma = (convexity / 100.0) * dirtyPrice;

        delta *= shift;
        gamma *= shift * shift;

        return delta + 0.5 * gamma;
    }

    final private double basisPointValue(final Leg leg, final InterestRate y) {
        return basisPointValue(leg, y, new Date());
    }

    //
    // public Enums
    //

    /**
     * Duration type
     */
    public enum Duration {
        Simple, Macaulay, Modified
    }

    //
    // private inner classes
    //

    private class IRRFinder implements Ops.DoubleOp {

        private final Leg cashflows_;
        private final double marketPrice_;
        private final DayCounter dayCounter_;
        private final Compounding compounding_;
        private final Frequency frequency_;
        private final Date settlementDate_;

        public IRRFinder(final Leg cashflows, final double marketPrice, final DayCounter dayCounter,
                final Compounding compounding, final Frequency frequency, final Date settlementDate) {
            this.cashflows_ = cashflows;
            this.marketPrice_ = marketPrice;
            this.dayCounter_ = dayCounter;
            this.compounding_ = compounding;
            this.frequency_ = frequency;
            this.settlementDate_ = settlementDate;
        }

        @Override
        public double op(final double guess) {
            final InterestRate rate = new InterestRate(guess, dayCounter_, compounding_, frequency_);
            final double NPV = npv(cashflows_, rate, settlementDate_);
            return marketPrice_ - NPV;
        }
    }

    private class BPSCalculator implements PolymorphicVisitor {

        private static final String UNKNOWN_VISITABLE = "unknow visitable object";

        private final Handle< YieldTermStructure > termStructure;
        private final Date npvDate;

        private double result;

        public BPSCalculator(final Handle< YieldTermStructure > termStructure, final Date npvDate) {
            this.termStructure = termStructure;
            this.npvDate = npvDate;
            this.result = 0.0;
        }

        public double result() {
            if ( npvDate.isNull() )
                return result;
            else
                return result / termStructure.currentLink().discount(npvDate);
        }

        //
        // private inner classes
        //

        @Override
        @SuppressWarnings("unchecked") // raw cast to the method-level CashFlow type-param;
                                        // the runtime isAssignableFrom guards verify type
                                        // compatibility at the call site.
        public < CashFlow > Visitor< CashFlow > visitor(final Class< ? extends CashFlow > klass) {

            // Coupon is a CashFlow, therefore any Coupon types will never
            // reach the CashFlowVisitor branch — they always match the
            // Coupon branch first.
            if ( Coupon.class.isAssignableFrom(klass) )
                return (Visitor< CashFlow >) new CouponVisitor();
            if ( org.jquantlib.cashflow.CashFlow.class.isAssignableFrom(klass) )
                return (Visitor< CashFlow >) new CashFlowVisitor();
            throw new LibraryException(UNKNOWN_VISITABLE); // QA:[RG]::verified
        }

        private class CashFlowVisitor implements Visitor< CashFlow > {
            @Override
            public void visit(final CashFlow o) {
                // nothing
            }
        }

        //
        // implements PolymorphicVisitor
        //

        private class CouponVisitor implements Visitor< CashFlow > {
            @Override
            public void visit(final CashFlow o) {
                final Coupon c = (Coupon) o;
                result += c.accrualPeriod() * c.nominal() * termStructure.currentLink().discount(c.date());
            }
        }
    }
}
