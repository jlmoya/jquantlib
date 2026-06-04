package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.BondResponse;
import org.jquantlib.showcase.dto.BondResponse.Cashflow;
import org.jquantlib.showcase.dto.BondResponse.PriceYield;

/**
 * Prices a fixed-rate coupon bond with JQuantLib: builds a coupon {@link Schedule},
 * a {@link FixedRateBond}, and discounts it on a flat curve via a
 * {@link DiscountingBondEngine}, then reports price, yield, accrued interest, the
 * cash flows, and a price-vs-yield curve.
 */
@Service
public class BondService {

    public BondResponse price(final double face, final double couponPercent, final int couponsPerYear,
                              final int years, final double ratePercent, final int settlementDays,
                              final String calendarName) {
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Calendar calendar = ShowcaseCalendars.byName(calendarName);
            final DayCounter dc = new ActualActual(ActualActual.Convention.Bond);
            final Frequency frequency = frequencyOf(couponsPerYear);
            final double coupon = couponPercent / 100.0;
            final double rate = ratePercent / 100.0;

            final Date issue = today;
            final Date maturity = today.add(new Period(years, TimeUnit.Years));
            final Schedule schedule = new Schedule(issue, maturity, new Period(frequency), calendar,
                    BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                    DateGeneration.Rule.Backward, false);

            final FixedRateBond bond = new FixedRateBond(settlementDays, face, schedule,
                    new double[] {coupon}, dc, BusinessDayConvention.ModifiedFollowing, 100.0, issue);

            final Handle<YieldTermStructure> discount = new Handle<>(new FlatForward(today, rate, dc));
            bond.setPricingEngine(new DiscountingBondEngine(discount));

            final double npv = bond.NPV();
            final double clean = bond.cleanPrice();
            final double dirty = bond.dirtyPrice();
            final double accrued = bond.accruedAmount();
            final double yieldPct = bond.yield(dc, Compounding.Compounded, frequency) * 100.0;
            final Date settlement = bond.settlementDate();

            final List<Cashflow> cashflows = new ArrayList<>();
            for (final CashFlow cf : bond.cashflows()) {
                cashflows.add(new Cashflow(cf.date().isoDate().toString(), round(cf.amount())));
            }

            final List<Double> ys = new ArrayList<>();
            final List<Double> prices = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                final double y = 0.12 * i / 48.0;
                ys.add(round(y * 100.0));
                prices.add(round(bond.cleanPrice(y, dc, Compounding.Compounded, frequency, settlement)));
            }

            final String summary = ("%.1f%% %s bond, %d-year, face %.0f. Discounted at %.2f%% flat: "
                    + "clean %.4f, dirty %.4f, accrued %.4f, yield %.4f%%.")
                    .formatted(couponPercent, frequency, years, face, ratePercent, clean, dirty, accrued, yieldPct);

            return new BondResponse(summary, round(npv), round(clean), round(dirty), round(accrued),
                    round(yieldPct), settlement.isoDate().toString(), maturity.isoDate().toString(), cashflows,
                    new PriceYield(ys, prices));
        });
    }

    private Frequency frequencyOf(final int couponsPerYear) {
        return switch (couponsPerYear) {
            case 1 -> Frequency.Annual;
            case 4 -> Frequency.Quarterly;
            case 12 -> Frequency.Monthly;
            default -> Frequency.Semiannual;
        };
    }

    private static double round(final double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return Math.round(v * 1e6) / 1e6;
    }
}
