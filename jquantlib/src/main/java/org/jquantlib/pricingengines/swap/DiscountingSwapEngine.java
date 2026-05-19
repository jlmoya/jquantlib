package org.jquantlib.pricingengines.swap;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class DiscountingSwapEngine extends Swap.EngineImpl implements /* Swap.Engine, */ Observer {

    private final Handle< YieldTermStructure > discountCurve;

    public DiscountingSwapEngine(final Handle< YieldTermStructure > discountCurve) /* @ReadOnly */ {
        this.discountCurve = discountCurve;
        this.discountCurve.addObserver(this);
    }

    /**
     * Earliest leg date — uses {@code Coupon.accrualStartDate()} when the cashflow is a Coupon, otherwise the
     * cashflow's own {@code date()}. Mirrors C++ {@code CashFlows::startDate} (cashflows.cpp:38-50) which always falls
     * back to {@code i->date()} for non-Coupon cashflows.
     *
     * <p>Phase 2q L0 A.2: kept inline to this engine rather than exposed via
     * {@link CashFlows#startDate(Leg)} because the existing Java method silently skips non-Coupon cashflows; fixing
     * that more broadly is a separate align task.
     */
    private static Date legStartDate(final Leg leg) {
        Date d = Date.maxDate();
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            final Date candidate = (cf instanceof Coupon) ? ((Coupon) cf).accrualStartDate() : cf.date();
            d = Date.min(d, candidate);
        }
        return d;
    }

    /**
     * Latest leg date — uses {@code Coupon.accrualEndDate()} when the cashflow is a Coupon, otherwise the cashflow's
     * own {@code date()}. Mirrors C++ {@code CashFlows::maturityDate} (cashflows.cpp:52-64).
     */
    private static Date legMaturityDate(final Leg leg) {
        Date d = Date.minDate();
        for ( int i = 0; i < leg.size(); ++i ) {
            final CashFlow cf = leg.get(i);
            final Date candidate = (cf instanceof Coupon) ? ((Coupon) cf).accrualEndDate() : cf.date();
            d = Date.max(d, candidate);
        }
        return d;
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        QL.require(!discountCurve.empty(), "no discounting term structure set"); // TODO: message

        final Swap.ArgumentsImpl a = (Swap.ArgumentsImpl) arguments_;
        final Swap.ResultsImpl r = (Swap.ResultsImpl) results_;
        r.value = 0.0;
        r.errorEstimate = Constants.NULL_REAL;

        final int n = a.legs.size();
        r.legNPV = new double[n];
        r.legBPS = new double[n];
        // Phase 2q L0 A.2 align: per-leg startDiscounts / endDiscounts.
        // Mirrors C++ v1.42.1 DiscountingSwapEngine::calculate
        // (pricingengines/swap/discountingswapengine.cpp:71-105).
        r.startDiscounts = new double[n];
        r.endDiscounts = new double[n];

        final YieldTermStructure curve = discountCurve.currentLink();
        final Date refDate = curve.referenceDate();

        for ( int i = 0; i < n; ++i ) {
            final Leg leg = a.legs.get(i);
            r.legNPV[i] = a.payer[i] * CashFlows.getInstance().npv(leg, discountCurve);
            r.legBPS[i] = a.payer[i] * CashFlows.getInstance().bps(leg, discountCurve);
            r.value += r.legNPV[i];

            // Per-leg start/end discount factors. Mirrors C++:
            //   if (!leg.empty()) {
            //     d1 = CashFlows::startDate(leg);
            //     startDiscounts[i] = (d1 >= refDate) ? curve->discount(d1) : Null<>();
            //     d2 = CashFlows::maturityDate(leg);
            //     endDiscounts[i]   = (d2 >= refDate) ? curve->discount(d2) : Null<>();
            //   } else { both = Null<>() }
            //
            // Java's CashFlows.startDate / maturityDate currently only handle
            // legs containing Coupons, so we mirror the C++ inline logic
            // here (use Coupon accrual dates if present, otherwise fall back
            // to the cashflow's own date()) without altering CashFlows itself.
            if ( leg != null && leg.size() > 0 ) {
                final Date d1 = legStartDate(leg);
                if ( d1.ge(refDate) ) {
                    r.startDiscounts[i] = curve.discount(d1);
                } else {
                    r.startDiscounts[i] = Constants.NULL_REAL;
                }
                final Date d2 = legMaturityDate(leg);
                if ( d2.ge(refDate) ) {
                    r.endDiscounts[i] = curve.discount(d2);
                } else {
                    r.endDiscounts[i] = Constants.NULL_REAL;
                }
            } else {
                r.startDiscounts[i] = Constants.NULL_REAL;
                r.endDiscounts[i] = Constants.NULL_REAL;
            }
        }
    }

}
