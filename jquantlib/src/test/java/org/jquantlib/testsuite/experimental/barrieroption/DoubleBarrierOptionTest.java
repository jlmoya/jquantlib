/*
 Copyright (C) 2026 JQuantLib migration

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
 */

package org.jquantlib.testsuite.experimental.barrieroption;

import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.barrieroption.BinomialDoubleBarrierEngine;
import org.jquantlib.experimental.barrieroption.DiscretizedDermanKaniDoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DiscretizedDoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierType;
import org.jquantlib.experimental.barrieroption.SuoWangDoubleBarrierEngine;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
import org.jquantlib.pricingengines.barrier.AnalyticDoubleBarrierEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the experimental double-barrier option family.
 * <p>
 * Mirrors a subset of {@code QuantLib::DoubleBarrierOptionTests} from
 * {@code test-suite/doublebarrieroption.cpp} (v1.42.1). Reference values
 * come from E.G. Haug, "The complete guide to option pricing formulas",
 * 2nd Ed, p.156 (the same source the C++ suite uses).
 *
 * <p>Per CLAUDE.md tolerance tiers, the binomial-tree engine is checked
 * with the C++ tolerance of 0.28 (CRR with 300 steps; expected as the
 * binomial converges slowly across barriers); the Derman-Kani enhancement
 * tightens to 0.033; SuoWang analytical engine is checked at 1e-4 — same
 * as Haug's reported value precision.
 *
 * <p>Note: {@link AnalyticDoubleBarrierEngine} (Ikeda/Kunitomo) lives in
 * {@code ql/pricingengines/barrier/} and is out of Phase 4e scope; tests
 * exercising that engine are marked {@code @Ignore} (Phase 4e.5 carry-forward).
 */
public class DoubleBarrierOptionTest {

    public DoubleBarrierOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static class HaugDouble {
        final DoubleBarrierType barrierType;
        final double barrierLo;
        final double barrierHi;
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        HaugDouble(final DoubleBarrierType barrierType, final double barrierLo, final double barrierHi,
                   final Option.Type type, final double strike, final double s,
                   final double q, final double r, final double t, final double v,
                   final double result, final double tol) {
            this.barrierType = barrierType;
            this.barrierLo = barrierLo;
            this.barrierHi = barrierHi;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    /** Subset of Haug values exercising both KnockOut and KnockIn for the SuoWang engine. */
    private static final HaugDouble[] SUOWANG_VALUES = new HaugDouble[] {
            // KnockOut Calls — 80/120 cluster (in-the-money side)
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 3.7516, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 2.6387, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35, 1.4903, 1.0e-4),
            // KnockOut Calls — 90/110 cluster (tight barriers)
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 1.2055, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 0.3098, 1.0e-4),
            // KnockOut Puts
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 1.8600, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 2.6866, 1.0e-4),
            // KnockIn Calls
            new HaugDouble(DoubleBarrierType.KnockIn, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 0.5999, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 3.6158, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 3.1460, 1.0e-4),
    };

    /**
     * Full Haug value set used by C++ {@code testEuropeanHaugValues} — KnockOut
     * calls/puts and KnockIn calls for the Ikeda/Kunitomo engine. Faithful to
     * {@code test-suite/doublebarrieroption.cpp} (v1.42.1).
     */
    private static final HaugDouble[] IKEDA_VALUES = new HaugDouble[] {
            // KnockOut Calls
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  4.3515, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  6.1644, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  7.0373, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  6.9853, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  7.9336, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  6.5088, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  4.3505, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  5.8500, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  5.7726, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  6.8082, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  6.3383, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  4.3841, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  4.3139, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  4.8293, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  3.7765, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  5.9697, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  4.0004, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  2.2563, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  3.7516, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  2.6387, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  1.4903, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  3.5805, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  1.5098, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  0.5635, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  1.2055, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  0.3098, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  0.0477, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  0.5537, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  0.0441, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  0.0011, 1.0e-4),

            // KnockOut Puts
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.15,  1.8825, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.25,  3.7855, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.35,  5.7191, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.15,  2.1374, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.25,  4.7033, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 50.0, 150.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.35,  7.1683, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.15,  1.8825, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.25,  3.7845, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.35,  5.6060, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.15,  2.1374, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.25,  4.6236, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 60.0, 140.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.35,  6.1062, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.15,  1.8825, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.25,  3.7014, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.35,  4.6472, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.15,  2.1325, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.25,  3.8944, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 70.0, 130.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.35,  3.5868, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.15,  1.8600, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.25,  2.6866, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.35,  2.0719, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.15,  1.8883, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.25,  1.7851, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.35,  0.8244, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.15,  0.9473, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.25,  0.3449, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.25, 0.35,  0.0578, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.15,  0.4555, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.25,  0.0491, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, Option.Type.Put,  100, 100.0, 0.0, 0.1, 0.50, 0.35,  0.0013, 1.0e-4),

            // KnockIn Calls
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  0.0000, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  0.0900, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  1.1537, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  0.0292, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  1.6487, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  50.0, 150.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  5.7321, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  0.0010, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  0.4045, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  2.4184, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  0.2062, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  3.2439, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  60.0, 140.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  7.8569, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  0.0376, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  1.4252, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  4.4145, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  1.0447, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  5.5818, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  70.0, 130.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35,  9.9846, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  0.5999, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  3.6158, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  6.7007, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  3.4340, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  8.0724, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35, 11.6774, 1.0e-4),

            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15,  3.1460, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25,  5.9447, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.35,  8.1432, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.15,  6.4608, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.25,  9.5382, 1.0e-4),
            new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.50, 0.35, 12.2398, 1.0e-4),
    };

    /** Subset for binomial CRR — wider tolerance per C++ test suite (0.28 plain, 0.033 Derman). */
    private static final HaugDouble[] BINOMIAL_VALUES = new HaugDouble[] {
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 3.7516, 0.28),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 2.6387, 0.28),
            new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, Option.Type.Put, 100, 100.0, 0.0, 0.1, 0.25, 0.15, 1.8600, 0.28),
            new HaugDouble(DoubleBarrierType.KnockIn, 80.0, 120.0, Option.Type.Call, 100, 100.0, 0.0, 0.1, 0.25, 0.25, 3.6158, 0.28),
    };

    @Test
    public void testSuoWangValues() {
        QL.info("Testing SuoWangDoubleBarrierEngine against Haug's values...");
        runEngineCheck("SuoWang", SUOWANG_VALUES, EngineKind.SUOWANG);
    }

    @Test
    public void testBinomialCRRValues() {
        QL.info("Testing BinomialDoubleBarrierEngine (CoxRossRubinstein, plain) against Haug's values...");
        runEngineCheck("Binomial CRR", BINOMIAL_VALUES, EngineKind.BINOMIAL_PLAIN);
    }

    @Test
    public void testBinomialDermanKaniValues() {
        QL.info("Testing BinomialDoubleBarrierEngine (Derman-Kani enhancement) against Haug's values...");
        // C++ uses tol 0.033 for Derman-Kani; we relax our subset slightly (still tighter than plain).
        final HaugDouble[] dk = new HaugDouble[BINOMIAL_VALUES.length];
        for (int i = 0; i < dk.length; i++) {
            final HaugDouble s = BINOMIAL_VALUES[i];
            dk[i] = new HaugDouble(s.barrierType, s.barrierLo, s.barrierHi, s.type, s.strike,
                    s.s, s.q, s.r, s.t, s.v, s.result, 0.033);
        }
        runEngineCheck("Binomial DermanKani", dk, EngineKind.BINOMIAL_DERMAN);
    }

    @Test
    public void testIkedaKunitomoValues() {
        QL.info("Testing AnalyticDoubleBarrierEngine (Ikeda/Kunitomo) against Haug's values...");
        runEngineCheck("Ikeda/Kunitomo", IKEDA_VALUES, EngineKind.IKEDA);
    }

    @Test
    @Ignore("Phase 4e.5: VannaVolgaBarrierEngine and VannaVolgaDoubleBarrierEngine require "
            + "DeltaVolQuote port from ql/quotes/ and experimental fxvolatility helpers.")
    public void testVannaVolgaValues() {
        // Carry-forward: port DeltaVolQuote + VannaVolga{Barrier,DoubleBarrier}Engine.
    }

    @Test
    @Ignore("Phase 4e.5: PerturbativeBarrierOptionEngine is 1550 LOC of scientific code "
            + "and was reported in v1.42.1 to fail tests on Mac OS X 10.8.4 — "
            + "low value vs effort. Ported on demand only.")
    public void testPerturbativeValues() {
        // Carry-forward: port PerturbativeBarrierOptionEngine (heavy NR code).
    }

    @Test
    @Ignore("Phase 4e.5: McDoubleBarrierEngine requires multi-asset MC infrastructure "
            + "(MultiAssetMCEngine) not yet ported in JQuantLib.")
    public void testMonteCarloValues() {
        // Carry-forward: port McDoubleBarrierEngine on top of MultiAssetMCEngine.
    }


    //
    // Helpers
    //

    private enum EngineKind {
        SUOWANG, BINOMIAL_PLAIN, BINOMIAL_DERMAN, IKEDA
    }

    private void runEngineCheck(final String label, final HaugDouble[] values, final EngineKind kind) {
        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final HaugDouble value : values) {
            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final DoubleBarrierOption opt = new DoubleBarrierOption(
                    value.barrierType, value.barrierLo, value.barrierHi,
                    0.0, // no rebate
                    payoff, exercise);

            switch (kind) {
                case IKEDA:
                    opt.setPricingEngine(new AnalyticDoubleBarrierEngine(stochProcess));
                    break;
                case SUOWANG:
                    opt.setPricingEngine(new SuoWangDoubleBarrierEngine(stochProcess));
                    break;
                case BINOMIAL_PLAIN:
                    opt.setPricingEngine(new BinomialDoubleBarrierEngine<CoxRossRubinstein, DiscretizedDoubleBarrierOption>(
                            CoxRossRubinstein.class, DiscretizedDoubleBarrierOption.class,
                            stochProcess, 300));
                    break;
                case BINOMIAL_DERMAN:
                    opt.setPricingEngine(new BinomialDoubleBarrierEngine<CoxRossRubinstein, DiscretizedDermanKaniDoubleBarrierOption>(
                            CoxRossRubinstein.class, DiscretizedDermanKaniDoubleBarrierOption.class,
                            stochProcess, 300));
                    break;
                default:
                    throw new IllegalStateException("unknown engine kind");
            }

            final double calculated = opt.NPV();
            final double expected = value.result;
            final double error = Math.abs(calculated - expected);
            final String msg = String.format(
                    "%s: barrier=[%.0f,%.0f] type=%s strike=%.1f s=%.1f vol=%.2f t=%.2f -> "
                            + "expected=%.4f calculated=%.4f error=%.4g (tol=%.4g)",
                    label, value.barrierLo, value.barrierHi, value.type, value.strike,
                    value.s, value.v, value.t, expected, calculated, error, value.tol);
            assertTrue(msg, error <= value.tol);
        }
    }
}
