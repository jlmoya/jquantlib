/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for CPICoupon + CPICouponPricer against
 QuantLib v1.42.1 via
 migration-harness/references/cashflows/cpi_coupon.json
 (Phase 2q L1 Track C — Section A cases A1..A6).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CPICoupon;
import org.jquantlib.cashflow.CPICouponPricer;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link CPICoupon} (Section A from cpi_coupon_probe).
 *
 * <p>Validates both base-CPI and base-date constructor variants, all three
 * observation interpolations (AsIndex/Flat/Linear), past + future end-dates,
 * and a non-trivial fixedRate (gearing). Tier: TIGHT — every output is
 * deterministic from the seeded fixings + closed-form curve interpolation.
 */
public class CPICouponTest {

    private static final String REF_GROUP = "cashflows/cpi_coupon";

    @Test
    public void cpiCoupon_matchesCpp() {
        // ---------- Match probe setup exactly ----------
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] nodeDates = new Date[] {
                new Date(1, Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[] { 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };

        final InterpolatedZeroInflationCurve<Linear> zeroCurve =
                new InterpolatedZeroInflationCurve<>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        zeroCurve.enableExtrapolation();

        final Handle<ZeroInflationTermStructure> ts =
                new Handle<ZeroInflationTermStructure>(zeroCurve);
        final UKRPI ukRpi = new UKRPI(Frequency.Monthly, false, false, ts);

        final Date[] fixDates = new Date[] {
                new Date(1, Month.January,   2005), new Date(1, Month.February,  2005),
                new Date(1, Month.March,     2005), new Date(1, Month.April,     2005),
                new Date(1, Month.May,       2005), new Date(1, Month.June,      2005),
                new Date(1, Month.July,      2005), new Date(1, Month.August,    2005),
                new Date(1, Month.September, 2005), new Date(1, Month.October,   2005),
                new Date(1, Month.November,  2005), new Date(1, Month.December,  2005),
                new Date(1, Month.January,   2006), new Date(1, Month.February,  2006),
                new Date(1, Month.March,     2006), new Date(1, Month.April,     2006),
                new Date(1, Month.May,       2006), new Date(1, Month.June,      2006),
                new Date(1, Month.July,      2006), new Date(1, Month.August,    2006),
                new Date(1, Month.September, 2006), new Date(1, Month.October,   2006),
                new Date(1, Month.November,  2006), new Date(1, Month.December,  2006),
                new Date(1, Month.January,   2007), new Date(1, Month.February,  2007),
                new Date(1, Month.March,     2007), new Date(1, Month.April,     2007),
                new Date(1, Month.May,       2007), new Date(1, Month.June,      2007),
                new Date(1, Month.July,      2007),
        };
        final double[] fixVals = new double[] {
                189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
                193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
                201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
        };
        for (int i = 0; i < fixDates.length; ++i) {
            ukRpi.addFixing(fixDates[i], fixVals[i], true);
        }

        // Nominal yield TS — flat 5% for pricer discount.
        final Handle<YieldTermStructure> nominalTs =
                new Handle<YieldTermStructure>(new FlatForward(refDate, 0.05, dc));
        final CPICouponPricer pricer = new CPICouponPricer(nominalTs);

        // ---------- Cross-validate every Section A case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("A")) continue; // Section B is in CPICashFlowTest
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, ukRpi, swapObsLag, dc, pricer, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkCase(final String name, final Case c,
                                  final UKRPI ukRpi,
                                  final Period swapObsLag,
                                  final DayCounter dc,
                                  final CPICouponPricer pricer,
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final String baseCPIstr = inputs.getString("baseCPI");
        final long baseDateSerial = inputs.getLong("baseDate_serial");
        final double notional = inputs.getDouble("notional");
        final long startSerial = inputs.getLong("startDate_serial");
        final long endSerial = inputs.getLong("endDate_serial");
        final long paymentSerial = inputs.getLong("paymentDate_serial");
        final String interpStr = inputs.getString("observationInterpolation");
        final double fixedRate = inputs.getDouble("fixedRate");

        final boolean baseCPIIsNull = "null".equals(baseCPIstr);
        final double baseCPI = baseCPIIsNull ? Constants.NULL_REAL : Double.parseDouble(baseCPIstr);
        final Date baseDate = baseDateSerial < 0 ? new Date() : new Date(baseDateSerial);
        final Date startDate = new Date(startSerial);
        final Date endDate = new Date(endSerial);
        final Date paymentDate = new Date(paymentSerial);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        final CPICoupon coupon;
        if (!baseCPIIsNull && baseDate.isNull()) {
            coupon = new CPICoupon(baseCPI, paymentDate, notional, startDate, endDate,
                    ukRpi, swapObsLag, interp, dc, fixedRate);
        } else if (baseCPIIsNull && !baseDate.isNull()) {
            coupon = new CPICoupon(baseDate, paymentDate, notional, startDate, endDate,
                    ukRpi, swapObsLag, interp, dc, fixedRate, new Date(), new Date(), new Date());
        } else {
            coupon = new CPICoupon(baseCPI, baseDate, paymentDate, notional, startDate, endDate,
                    ukRpi, swapObsLag, interp, dc, fixedRate, new Date(), new Date(), new Date());
        }
        coupon.setPricer(pricer);

        // date_serial — exact
        {
            final long exp = expected.getLong("date_serial");
            final long act = coupon.date().serialNumber();
            if (!Tolerance.exact(act, exp)) {
                mismatches.add(name + ".date_serial: expected=" + exp + " actual=" + act);
            }
        }

        // indexFixing — TIGHT
        {
            final double exp = expected.getDouble("indexFixing");
            final double act = coupon.indexFixing();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".indexFixing", exp, act));
            }
        }

        // indexRatio_at_endDate — TIGHT
        {
            final double exp = expected.getDouble("indexRatio_at_endDate");
            final double act = coupon.indexRatio(coupon.accrualEndDate());
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".indexRatio_at_endDate", exp, act));
            }
        }

        // rate — TIGHT (gearing * indexRatio)
        {
            final double exp = expected.getDouble("rate");
            final double act = coupon.rate();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".rate", exp, act));
            }
        }

        // amount — TIGHT (rate * accrualPeriod * nominal)
        {
            final double exp = expected.getDouble("amount");
            final double act = coupon.amount();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".amount", exp, act));
            }
        }

        // adjustedIndexGrowth — TIGHT (rate / fixedRate)
        {
            final double exp = expected.getDouble("adjustedIndexGrowth");
            final double act = coupon.adjustedIndexGrowth();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".adjustedIndexGrowth", exp, act));
            }
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}
