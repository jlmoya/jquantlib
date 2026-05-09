/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.12.

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
 Copyright (C) 2009 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.pathwise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Pathwise (adjoint-Greeks) multi-product that wraps an underlying product
 * with an exercise strategy and an optional rebate.
 *
 * <p>When the strategy fires ({@code exercise(currentState) == true}) the
 * underlying product stops generating cash flows and the rebate product takes
 * over for the remaining steps. Until that point the underlying runs normally.
 *
 * <p>Mirrors C++ {@code CallSpecifiedPathwiseMultiProduct}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductcallspecified.{hpp,cpp}
 * v1.42.1).
 *
 * <p>Depends on {@link ExerciseStrategy} (L0.1) and
 * {@link MarketModelPathwiseCashRebate} (Phase 3k Track C C.1).
 *
 * @author Jose Moya
 */
public class CallSpecifiedPathwiseMultiProduct extends MarketModelPathwiseMultiProduct {

    private final MarketModelPathwiseMultiProduct underlying_;
    private final ExerciseStrategy strategy_;
    private MarketModelPathwiseMultiProduct rebate_;

    private final EvolutionDescription evolution_;
    private final boolean[][] isPresent_;   // [4][mergedSteps]: indexed by source
    private final double[] cashFlowTimes_;
    private final int rebateOffset_;

    // path-varying state
    private boolean wasCalled_;
    private int currentIndex_;
    private boolean callable_;

    // dummy workspace for rebate calls when not yet called
    private final int[] dummyCashFlowsThisStep_;
    private final CashFlow[][] dummyCashFlowsGenerated_;

    /**
     * Constructs a call-specified pathwise multi-product with an optional rebate.
     *
     * @param underlying  the underlying pathwise product (e.g. a pathwise swap or caplet)
     * @param strategy    exercise strategy based on {@link CurveState}
     * @param rebate      optional rebate product paid on exercise; if {@code null}
     *                    a zero-cash-flow rebate is generated automatically
     */
    public CallSpecifiedPathwiseMultiProduct(final MarketModelPathwiseMultiProduct underlying,
                                              final ExerciseStrategy strategy,
                                              final MarketModelPathwiseMultiProduct rebate) {
        this.underlying_ = underlying.clone();
        this.strategy_   = strategy.clone();
        this.callable_   = true;
        this.wasCalled_  = false;
        this.currentIndex_ = 0;

        final int products = underlying_.numberOfProducts();
        final EvolutionDescription d1 = underlying_.evolution();
        final double[] rateTimes1     = d1.rateTimes();
        final double[] evolutionTimes1 = d1.evolutionTimes();
        final double[] exerciseTimes  = strategy_.exerciseTimes();

        MarketModelPathwiseMultiProduct resolvedRebate = rebate;
        if (resolvedRebate == null) {
            // Create a zero-cash rebate for all exercise times
            final EvolutionDescription rebateDesc = new EvolutionDescription(rateTimes1, exerciseTimes);
            final Matrix amounts = new Matrix(products, exerciseTimes.length);
            // amounts is all-zero by default
            resolvedRebate = new MarketModelPathwiseCashRebate(
                    rebateDesc, exerciseTimes, amounts, products);
        } else {
            // Validate compatibility
            final EvolutionDescription d2 = resolvedRebate.evolution();
            final double[] rateTimes2 = d2.rateTimes();
            QL.require(rateTimes1.length == rateTimes2.length
                    && Arrays.equals(rateTimes1, rateTimes2),
                    "incompatible rate times");
            QL.require(underlying_.alreadyDeflated() == resolvedRebate.alreadyDeflated(),
                    "incompatible deflations");
        }
        this.rebate_ = resolvedRebate;

        // Merge all relevant time vectors
        final List<double[]> allEvolutionTimes = new ArrayList<>(4);
        allEvolutionTimes.add(evolutionTimes1);
        allEvolutionTimes.add(exerciseTimes);
        allEvolutionTimes.add(this.rebate_.evolution().evolutionTimes());
        allEvolutionTimes.add(strategy_.relevantTimes());

        final Utilities.MergeResult merged = Utilities.mergeTimes(allEvolutionTimes);
        final double[] mergedTimes = merged.mergedTimes();
        this.isPresent_ = merged.isPresent();

        this.evolution_ = new EvolutionDescription(rateTimes1, mergedTimes);

        // Build combined cashFlowTimes_: underlying first, then rebate
        final double[] underlyingCFT = underlying_.possibleCashFlowTimes();
        final double[] rebateCFT     = this.rebate_.possibleCashFlowTimes();
        this.rebateOffset_ = underlyingCFT.length;
        this.cashFlowTimes_ = new double[underlyingCFT.length + rebateCFT.length];
        System.arraycopy(underlyingCFT, 0, cashFlowTimes_, 0,              underlyingCFT.length);
        System.arraycopy(rebateCFT,     0, cashFlowTimes_, rebateOffset_,  rebateCFT.length);

        // Allocate dummy workspace for rebate advance calls when !wasCalled_
        final int maxRebateCF = this.rebate_.maxNumberOfCashFlowsPerProductPerStep();
        this.dummyCashFlowsThisStep_ = new int[products];
        this.dummyCashFlowsGenerated_ = new CashFlow[products][];
        final int nRates = d1.numberOfRates();
        for (int i = 0; i < products; ++i) {
            dummyCashFlowsGenerated_[i] = new CashFlow[maxRebateCF];
            for (int j = 0; j < maxRebateCF; ++j) {
                dummyCashFlowsGenerated_[i][j] = new CashFlow(0, new double[nRates + 1]);
            }
        }
    }

    /** Convenience constructor with no rebate (uses auto zero-rebate). */
    public CallSpecifiedPathwiseMultiProduct(final MarketModelPathwiseMultiProduct underlying,
                                              final ExerciseStrategy strategy) {
        this(underlying, strategy, null);
    }

    // ---- MarketModelPathwiseMultiProduct interface ----

    @Override
    public boolean alreadyDeflated() {
        return underlying_.alreadyDeflated();
    }

    @Override
    public int[] suggestedNumeraires() {
        return underlying_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return cashFlowTimes_.clone();
    }

    @Override
    public int numberOfProducts() {
        return underlying_.numberOfProducts();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return Math.max(underlying_.maxNumberOfCashFlowsPerProductPerStep(),
                        rebate_.maxNumberOfCashFlowsPerProductPerStep());
    }

    @Override
    public void reset() {
        underlying_.reset();
        rebate_.reset();
        strategy_.reset();
        currentIndex_ = 0;
        wasCalled_    = false;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final CashFlow[][] cashFlowsGenerated) {
        final boolean isUnderlyingTime       = isPresent_[0][currentIndex_];
        final boolean isExerciseTime         = isPresent_[1][currentIndex_];
        final boolean isRebateTime           = isPresent_[2][currentIndex_];
        final boolean isStrategyRelevantTime = isPresent_[3][currentIndex_];

        // Advance strategy before checking exercise
        if (!wasCalled_ && isStrategyRelevantTime) {
            strategy_.nextStep(currentState);
        }

        // Exercise check
        if (!wasCalled_ && isExerciseTime && callable_) {
            wasCalled_ = strategy_.exercise(currentState);
        }

        boolean done = false;

        if (wasCalled_) {
            // After exercise: only rebate generates cash flows
            if (isRebateTime) {
                done = rebate_.nextTimeStep(currentState,
                        numberCashFlowsThisStep, cashFlowsGenerated);
                // Shift timeIndex by rebateOffset_ so indices are into the combined cashFlowTimes_
                for (int i = 0; i < numberCashFlowsThisStep.length; ++i) {
                    for (int j = 0; j < numberCashFlowsThisStep[i]; ++j) {
                        cashFlowsGenerated[i][j].timeIndex += rebateOffset_;
                    }
                }
            } else {
                // Zero out flows on non-rebate steps
                for (int i = 0; i < numberOfProducts(); ++i) {
                    numberCashFlowsThisStep[i] = 0;
                }
            }
        } else {
            // Before exercise: advance rebate silently (it must consume its steps)
            if (isRebateTime) {
                rebate_.nextTimeStep(currentState,
                        dummyCashFlowsThisStep_, dummyCashFlowsGenerated_);
            }
            if (isUnderlyingTime) {
                done = underlying_.nextTimeStep(currentState,
                        numberCashFlowsThisStep, cashFlowsGenerated);
            } else {
                for (int i = 0; i < numberOfProducts(); ++i) {
                    numberCashFlowsThisStep[i] = 0;
                }
            }
        }

        ++currentIndex_;
        return done || currentIndex_ == evolution_.evolutionTimes().length;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        return new CallSpecifiedPathwiseMultiProduct(underlying_, strategy_, rebate_);
    }

    // ---- extra accessors (mirror C++) ----

    /** Returns the underlying product. */
    public MarketModelPathwiseMultiProduct underlying() {
        return underlying_;
    }

    /** Returns the exercise strategy. */
    public ExerciseStrategy strategy() {
        return strategy_;
    }

    /** Returns the rebate product. */
    public MarketModelPathwiseMultiProduct rebate() {
        return rebate_;
    }

    /** Re-enables callability (exercise is checked again on subsequent steps). */
    public void enableCallability()  { callable_ = true; }

    /** Disables callability (exercise check is skipped). */
    public void disableCallability() { callable_ = false; }
}
