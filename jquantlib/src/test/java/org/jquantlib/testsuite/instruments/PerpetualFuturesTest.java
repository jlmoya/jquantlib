/*
 Copyright (C) 2026 Jose Moya

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
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.futures.DiscountingPerpetualFuturesEngine;
import org.jquantlib.experimental.futures.PerpetualFutures;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Port of QuantLib v1.42.1 test-suite/perpetualfutures.cpp (177 LOC).
 *
 * <p>Phase 5e.5b-CFC-d-192: testPerpetualFuturesValues bodied
 * ({@code PerpetualFutures} + {@code DiscountingPerpetualFuturesEngine} ported
 * under {@code org.jquantlib.experimental.futures}).
 *
 * <p>Exercises the analytic value of perpetual futures across
 * {Linear, Inverse} payoff types and {FundingWithPreviousSpot,
 * FundingWithCurrentSpot} funding types in both discrete-time (3M) and
 * continuous-time (0M) regimes, against Equation (12), Proposition 2/3/4
 * of Ackerer-Hugonnier-Jermann (2024).
 *
 * <p>Reference: test-suite/perpetualfutures.cpp:66-173.
 *
 * @author Jose Moya
 */
public class PerpetualFuturesTest {

    public PerpetualFuturesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final class Data {
        final PerpetualFutures.PayoffType payoffType;
        final PerpetualFutures.FundingType fundingType;
        final Period fundingFreq;
        final double s;       // spot
        final double r;       // risk-free rate
        final double q;       // asset yield
        final double k;       // funding rate
        final double iDiff;   // interest rate differential
        final double relTol;  // relative tolerance

        Data(final PerpetualFutures.PayoffType payoffType,
             final PerpetualFutures.FundingType fundingType,
             final Period fundingFreq,
             final double s, final double r, final double q,
             final double k, final double iDiff, final double relTol) {
            this.payoffType = payoffType;
            this.fundingType = fundingType;
            this.fundingFreq = fundingFreq;
            this.s = s;
            this.r = r;
            this.q = q;
            this.k = k;
            this.iDiff = iDiff;
            this.relTol = relTol;
        }
    }

    @Test
    public void testPerpetualFuturesValues() {
        QL.info("Testing perpetual futures value against analytic form for constant parameters...");

        final Data[] values = new Data[] {
            // Discrete time (3 Months)
            new Data(PerpetualFutures.PayoffType.Linear,
                     PerpetualFutures.FundingType.FundingWithPreviousSpot,
                     new Period(3, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.01, 0.005, 1.0e-6),
            new Data(PerpetualFutures.PayoffType.Linear,
                     PerpetualFutures.FundingType.FundingWithCurrentSpot,
                     new Period(3, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.01, 0.005, 1.0e-6),
            new Data(PerpetualFutures.PayoffType.Inverse,
                     PerpetualFutures.FundingType.FundingWithPreviousSpot,
                     new Period(3, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.01, 0.005, 1.0e-6),
            new Data(PerpetualFutures.PayoffType.Inverse,
                     PerpetualFutures.FundingType.FundingWithCurrentSpot,
                     new Period(3, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.01, 0.005, 1.0e-6),
            new Data(PerpetualFutures.PayoffType.Linear,
                     PerpetualFutures.FundingType.FundingWithPreviousSpot,
                     new Period(3, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.01, 0.005, 1.0e-6),
            // Continuous time (0 Months)
            new Data(PerpetualFutures.PayoffType.Linear,
                     PerpetualFutures.FundingType.FundingWithPreviousSpot,
                     new Period(0, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.2, 0.005, 1.0e-6),
            new Data(PerpetualFutures.PayoffType.Inverse,
                     PerpetualFutures.FundingType.FundingWithPreviousSpot,
                     new Period(0, TimeUnit.Months),
                     10000.0, 0.04, 0.02, 0.2, 0.005, 1.0e-6),
        };

        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Calendar cal = new NullCalendar();
        final Date today = new Settings().evaluationDate();

        for (final Data v : values) {
            final PerpetualFutures trade = new PerpetualFutures(
                    v.payoffType, v.fundingType, v.fundingFreq, cal, dc);
            final Handle<YieldTermStructure> domCurve =
                    new Handle<YieldTermStructure>(Utilities.flatRate(today, v.r, dc));
            final Handle<YieldTermStructure> forCurve =
                    new Handle<YieldTermStructure>(Utilities.flatRate(today, v.q, dc));
            final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(v.s));
            final double[] fundingTimes      = new double[] { 0.0 };
            final double[] fundingRates      = new double[] { v.k };
            final double[] interestRateDiffs = new double[] { v.iDiff };
            final PricingEngine engine = new DiscountingPerpetualFuturesEngine(
                    domCurve, forCurve, spot,
                    fundingTimes, fundingRates, interestRateDiffs);
            trade.setPricingEngine(engine);
            final double calculated = trade.NPV();

            // analytic (Ackerer-Hugonnier-Jermann 2024)
            double dt = 0.0;
            switch (v.fundingFreq.units()) {
                case Years:
                    dt = v.fundingFreq.length();
                    break;
                case Months:
                    dt = v.fundingFreq.length() / 12.0;
                    break;
                case Weeks:
                    dt = v.fundingFreq.length() * 7.0 / 365.0;
                    break;
                case Days:
                    dt = v.fundingFreq.length() / 365.0;
                    break;
                default:
                    fail("Unknown fundingFrequency unit: " + v.fundingFreq.units());
            }

            double expected = 0.0;
            if (v.fundingFreq.length() > 0) {
                // Discrete time
                if (v.payoffType == PerpetualFutures.PayoffType.Linear) {
                    if (v.fundingType == PerpetualFutures.FundingType.FundingWithPreviousSpot) {
                        // Eq. (12)
                        expected = v.s * (v.k - v.iDiff) * Math.exp(v.q * dt)
                                / (Math.exp(v.q * dt) - Math.exp(v.r * dt)
                                   + v.k * Math.exp(v.q * dt));
                    } else if (v.fundingType == PerpetualFutures.FundingType.FundingWithCurrentSpot) {
                        // end of "3 Perpetual futures pricing"
                        expected = v.s * (v.k - v.iDiff) * Math.exp(v.r * dt)
                                / (Math.exp(v.q * dt) - Math.exp(v.r * dt)
                                   + v.k * Math.exp(v.r * dt));
                    }
                } else if (v.payoffType == PerpetualFutures.PayoffType.Inverse) {
                    if (v.fundingType == PerpetualFutures.FundingType.FundingWithPreviousSpot) {
                        // Proposition 2
                        expected = v.s
                                * (Math.exp(v.r * dt) - Math.exp(v.q * dt)
                                   + v.k * Math.exp(v.r * dt))
                                / (v.k - v.iDiff) / Math.exp(v.r * dt);
                    } else if (v.fundingType == PerpetualFutures.FundingType.FundingWithCurrentSpot) {
                        expected = v.s
                                * (Math.exp(v.r * dt) - Math.exp(v.q * dt)
                                   + v.k * Math.exp(v.q * dt))
                                / (v.k - v.iDiff) / Math.exp(v.q * dt);
                    }
                }
            } else {
                // Continuous time
                if (v.payoffType == PerpetualFutures.PayoffType.Linear) {
                    // Proposition 3
                    expected = v.s * (v.k - v.iDiff) / (v.q - v.r + v.k);
                } else if (v.payoffType == PerpetualFutures.PayoffType.Inverse) {
                    // Proposition 4
                    expected = v.s * (v.r - v.q + v.k) / (v.k - v.iDiff);
                }
            }

            final double relError = Math.abs(calculated / expected - 1.0);
            if (relError > v.relTol) {
                fail(v.payoffType + " perpetual futures with " + v.fundingType
                        + " funding type:\n"
                        + "    spot value:                      " + v.s + "\n"
                        + "    risk-free rate:                  " + v.r + "\n"
                        + "    asset yield:                     " + v.q + "\n"
                        + "    funding rate:                    " + v.k + "\n"
                        + "    interest rate differential:      " + v.iDiff + "\n"
                        + "    funding frequency:               " + v.fundingFreq + "\n"
                        + "    reference date:                  " + today + "\n"
                        + "    expected   value: " + expected + "\n"
                        + "    calculated value: " + calculated + "\n"
                        + "    rel error: " + relError + "\n"
                        + "    tolerance: " + v.relTol);
            }
        }
    }
}
