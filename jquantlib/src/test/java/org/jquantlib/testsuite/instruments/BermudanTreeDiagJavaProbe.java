/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorModel;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swap.TreeVanillaSwapEngine;
import org.jquantlib.pricingengines.swap.DiscretizedSwap;
import org.jquantlib.pricingengines.swaption.DiscretizedSwaption;
import org.jquantlib.pricingengines.swaption.FdHullWhiteSwaptionEngine;
import org.jquantlib.pricingengines.swaption.JamshidianSwaptionEngine;
import org.jquantlib.pricingengines.swaption.TreeSwaptionEngine;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Mirrors {@code bermudan_tree_state_probe.cpp} on the Java side — capture
 * tree mechanics state for the same fixture so divergences can be pinpointed.
 * Phase 5e.5b-CFC-d-261 diag.
 *
 * <p>Run with {@code mvn -pl ../jquantlib test -Dtest=BermudanTreeDiagJavaProbe}
 * to dump tree state, time grid, mandatory times, swap leg coupons, and
 * trace the underlying swap rollback to exTime for direct comparison with
 * C++ refs at {@code migration-harness/references/instruments/bermudan_tree_state.json}.
 *
 * <p>Ignored in normal test runs — this is a diagnostic probe, not a
 * pass/fail assertion. Used by Phase 5e.5b-CFC-d-261 investigation.
 */
@Ignore("diagnostic probe; not a regression test")
public class BermudanTreeDiagJavaProbe {

    @Test
    public void dumpJavaTreeState() {
        final Date today = new Date(15, Month.February, 2002);
        final Date settle = new Date(19, Month.February, 2002);
        new Settings().setEvaluationDate(today);

        final RelinkableHandle<YieldTermStructure> ts =
                new RelinkableHandle<YieldTermStructure>();
        final IborIndex idx = new Euribor6M(ts);
        final Calendar cal = idx.fixingCalendar();
        final Actual365Fixed dc = new Actual365Fixed();   // engine day-counter
        ts.linkTo(new FlatForward(settle, 0.04875825, dc));

        final Date start = cal.advance(settle, new Period(1, TimeUnit.Years));
        final Date maturity = cal.advance(start, new Period(5, TimeUnit.Years));
        final Schedule fixSched = new Schedule(start, maturity,
                new Period(Frequency.Annual), cal,
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);
        final Schedule fltSched = new Schedule(start, maturity,
                new Period(Frequency.Semiannual), cal,
                BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Thirty360 fixDc = new Thirty360(Thirty360.Convention.BondBasis);

        final VanillaSwap swap0 = new VanillaSwap(VanillaSwap.Type.Payer, 1000.0,
                fixSched, 0.0, fixDc, fltSched, idx, 0.0, idx.dayCounter());
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atm = swap0.fairRate();

        final VanillaSwap atmSwap = new VanillaSwap(VanillaSwap.Type.Payer, 1000.0,
                fixSched, atm, fixDc, fltSched, idx, 0.0, idx.dayCounter());
        atmSwap.setPricingEngine(new DiscountingSwapEngine(ts));

        final List<Date> exDates = new ArrayList<Date>();
        for (final CashFlow cf : atmSwap.fixedLeg()) {
            exDates.add(((Coupon) cf).accrualStartDate());
        }

        final HullWhite hw = new HullWhite(ts, 0.048696, 0.0058904);
        final TreeSwaptionEngine treeEng = new TreeSwaptionEngine(hw, 50, ts);

        System.out.println("=== Java BermudanTreeDiag dump ===");
        System.out.println("atm rate = " + atm);

        // C++ reference values from bermudan_tree_diag.json:
        final double[] cppExp = {
                8.617668248529641,
                11.046898808110544,
                12.190341081425066,
                12.722663767147466,
                12.906866329650406
        };

        for (int n = 1; n <= exDates.size(); n++) {
            final List<Date> subset = new ArrayList<Date>(exDates.subList(0, n));
            final Exercise berm = new BermudanExercise(subset.toArray(new Date[0]));
            final Swaption sBerm = new Swaption(atmSwap, berm);
            sBerm.setPricingEngine(treeEng);
            final double npvTree = sBerm.NPV();
            System.out.printf("berm_%dex: java=%.15g cpp=%.15g diff=%+.6g%n",
                    n, npvTree, cppExp[n - 1], npvTree - cppExp[n - 1]);
        }

        // n_ex=1 is European-equivalent — test all 3 engines
        {
            final List<Date> sub1 = new ArrayList<Date>(exDates.subList(0, 1));
            final Exercise euro = new EuropeanExercise(sub1.get(0));
            final Exercise berm = new BermudanExercise(sub1.toArray(new Date[0]));

            final Swaption sETree = new Swaption(atmSwap, euro);
            sETree.setPricingEngine(treeEng);
            final double npvETree = sETree.NPV();

            final Swaption sBTree = new Swaption(atmSwap, berm);
            sBTree.setPricingEngine(treeEng);
            final double npvBTree = sBTree.NPV();

            final Swaption sEFdm = new Swaption(atmSwap, euro);
            sEFdm.setPricingEngine(new FdHullWhiteSwaptionEngine(hw));
            final double npvEFdm = sEFdm.NPV();

            final Swaption sEJam = new Swaption(atmSwap, euro);
            sEJam.setPricingEngine(new JamshidianSwaptionEngine(hw, ts));
            final double npvEJam = sEJam.NPV();

            System.out.printf("n=1 EUROPEAN tree=%.15g BERM tree=%.15g FDM(euro)=%.15g JAM(euro)=%.15g%n",
                    npvETree, npvBTree, npvEFdm, npvEJam);
        }

        // Trace: initialize swaption at exTime, dump values
        {
            final List<Date> sub1 = new ArrayList<Date>(exDates.subList(0, 1));
            final org.jquantlib.instruments.Swaption.ArgumentsImpl args2 =
                    new org.jquantlib.instruments.Swaption.ArgumentsImpl();
            args2.swap = atmSwap;
            args2.exercise = new BermudanExercise(sub1.toArray(new Date[0]));
            args2.settlementType = org.jquantlib.instruments.Settlement.Type.Physical;
            args2.settlementMethod = org.jquantlib.instruments.Settlement.Method.PhysicalOTC;

            final DiscretizedSwaption discSwap2 = new DiscretizedSwaption(args2, today, dc);
            final List<Double> mand2 = discSwap2.mandatoryTimes();
            final TimeGrid grid2 = new TimeGrid(mand2, 50);
            final Lattice latt2 = hw.tree(grid2);

            final double exTime = dc.yearFraction(today, sub1.get(0));
            final DiscretizedSwaption traceSw = new DiscretizedSwaption(args2, today, dc);
            traceSw.initialize(latt2, exTime);
            traceSw.rollback(exTime);

            final double tracePv = traceSw.presentValue();
            System.out.printf("trace: PV@exTime=%.15g  exTime=%.15g  values.size=%d%n",
                    tracePv, exTime, traceSw.values().size());
            for (int j = 0; j < traceSw.values().size(); j++) {
                System.out.printf("   v[%d]=%.15g%n", j, traceSw.values().get(j));
            }
        }

        // Tree-vanilla swap NPV (no exercise)
        {
            final VanillaSwap copy = new VanillaSwap(VanillaSwap.Type.Payer, 1000.0,
                    fixSched, atm, fixDc, fltSched, idx, 0.0, idx.dayCounter());
            copy.setPricingEngine(new TreeVanillaSwapEngine(copy, hw, 50, ts));
            final double tnpv = copy.NPV();

            final VanillaSwap copy2 = new VanillaSwap(VanillaSwap.Type.Payer, 1000.0,
                    fixSched, atm, fixDc, fltSched, idx, 0.0, idx.dayCounter());
            copy2.setPricingEngine(new DiscountingSwapEngine(ts));
            final double dnpv = copy2.NPV();
            System.out.printf("Tree VanillaSwap NPV=%.15g  Discounting NPV=%.15g (atm = 0)%n",
                    tnpv, dnpv);
        }

        // ---- Re-derive what the engine would see: build a Swaption args
        //      then a DiscretizedSwaption, query mandatoryTimes, build TimeGrid,
        //      build tree, dump state.
        System.out.println();
        System.out.println("--- Tree state @ n_ex=1 (engine path with dc=" + dc.name() + ") ---");
        // Build Swaption.ArgumentsImpl by hand
        final org.jquantlib.instruments.Swaption.ArgumentsImpl args =
                new org.jquantlib.instruments.Swaption.ArgumentsImpl();
        args.swap = atmSwap;
        final List<Date> sub1 = new ArrayList<Date>(exDates.subList(0, 1));
        args.exercise = new BermudanExercise(sub1.toArray(new Date[0]));
        args.settlementType = org.jquantlib.instruments.Settlement.Type.Physical;
        args.settlementMethod = org.jquantlib.instruments.Settlement.Method.PhysicalOTC;

        final DiscretizedSwaption discSwap = new DiscretizedSwaption(args, today, dc);

        // Print fixed/float reset+pay times directly from the swap
        System.out.println("Fixed leg coupons (from atmSwap):");
        for (final CashFlow cf : atmSwap.fixedLeg()) {
            final Coupon c = (Coupon) cf;
            System.out.printf("  resetT=%.15g payT=%.15g amt=%.15g%n",
                    dc.yearFraction(today, c.accrualStartDate()),
                    dc.yearFraction(today, c.date()),
                    c.amount());
        }
        System.out.println("Float leg coupons (from atmSwap):");
        for (final CashFlow cf : atmSwap.floatingLeg()) {
            final Coupon c = (Coupon) cf;
            System.out.printf("  resetT=%.15g payT=%.15g accr=%.15g%n",
                    dc.yearFraction(today, c.accrualStartDate()),
                    dc.yearFraction(today, c.date()),
                    c.accrualPeriod());
        }

        final List<Double> mand = discSwap.mandatoryTimes();
        System.out.println("mandatory_times_n1.size = " + mand.size());
        for (final double t : mand) {
            System.out.printf("  %.15g%n", t);
        }

        final TimeGrid grid = new TimeGrid(mand, 50);
        System.out.println("grid.size = " + grid.size());
        for (int i = 0; i < grid.size(); i++) {
            System.out.printf("  t[%d] = %.15g%n", i, grid.at(i));
        }

        final Lattice latt = hw.tree(grid);
        if (latt instanceof OneFactorModel.ShortRateTree) {
            final OneFactorModel.ShortRateTree srt = (OneFactorModel.ShortRateTree) latt;
            System.out.println("Tree sizes per step:");
            for (int i = 0; i < Math.min(10, grid.size()); i++) {
                System.out.printf("  size(%d)=%d%n", i, srt.size(i));
            }
            System.out.println("Underlying x[i,j]:");
            for (int i = 0; i < Math.min(10, grid.size()); i++) {
                System.out.printf("  i=%d:", i);
                for (int j = 0; j < Math.min(7, srt.size(i)); j++) {
                    System.out.printf(" %.15g", srt.underlying(i, j));
                }
                System.out.println();
            }
            System.out.println("Discount[i,j]:");
            for (int i = 0; i < Math.min(10, grid.size()); i++) {
                System.out.printf("  i=%d:", i);
                for (int j = 0; j < Math.min(7, srt.size(i)); j++) {
                    System.out.printf(" %.15g", srt.discount(i, j));
                }
                System.out.println();
            }
        }
    }
}
