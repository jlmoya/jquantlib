/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.4.

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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.products;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Composition of two or more market-model products.
 * <p>
 * Mirrors C++ {@code class MarketModelComposite}
 * (ql/models/marketmodels/products/compositeproduct.{hpp,cpp} v1.42.1).
 * <p>
 * Instances of this class build a market-model product by composing one or
 * more sub-products. <b>All sub-products must have the same rate times.</b>
 * After all sub-products have been added via {@link #add} or
 * {@link #subtract}, {@link #finalizeComposite} must be called to compute
 * the merged evolution and cash-flow times.
 *
 * <p>P3K-3: C++ {@code Clone<MarketModelMultiProduct>} maps to direct
 * reference + a {@code .clone()} call at construction time.
 * P3K-4: C++ {@code std::valarray<bool>} maps to {@code boolean[]}.
 *
 * @author Jose Moya
 */
public abstract class MarketModelComposite extends MarketModelMultiProduct {

    /** Per sub-product working state — mirrors the C++ {@code SubProduct} struct. */
    protected static final class SubProduct {
        public MarketModelMultiProduct product;
        public double multiplier;
        public int[] numberOfCashflows;
        public CashFlow[][] cashflows;
        public int[] timeIndices;
        public boolean done;

        public SubProduct() { /* default-init */ }

        /** Shallow copy used by composite clone(). */
        public SubProduct copyDeep() {
            final SubProduct s = new SubProduct();
            s.product = this.product.clone();
            s.multiplier = this.multiplier;
            s.numberOfCashflows = (this.numberOfCashflows == null) ? null : this.numberOfCashflows.clone();
            if (this.cashflows == null) {
                s.cashflows = null;
            } else {
                s.cashflows = new CashFlow[this.cashflows.length][];
                for (int i = 0; i < this.cashflows.length; ++i) {
                    s.cashflows[i] = new CashFlow[this.cashflows[i].length];
                    for (int j = 0; j < this.cashflows[i].length; ++j) {
                        s.cashflows[i][j] = new CashFlow(
                                this.cashflows[i][j].timeIndex, this.cashflows[i][j].amount);
                    }
                }
            }
            s.timeIndices = (this.timeIndices == null) ? null : this.timeIndices.clone();
            s.done = this.done;
            return s;
        }
    }

    protected final List<SubProduct> components_ = new ArrayList<>();
    protected double[] rateTimes_;
    protected double[] evolutionTimes_;
    protected EvolutionDescription evolution_;
    protected boolean finalized_ = false;
    protected int currentIndex_;
    protected double[] cashflowTimes_;
    protected final List<double[]> allEvolutionTimes_ = new ArrayList<>();
    protected boolean[][] isInSubset_;

    protected MarketModelComposite() { /* default-init */ }

    @Override
    public final EvolutionDescription evolution() {
        QL.require(finalized_, "composite not finalized");
        return evolution_;
    }

    @Override
    public final int[] suggestedNumeraires() {
        QL.require(finalized_, "composite not finalized");
        return EvolutionDescription.terminalMeasure(evolution_);
    }

    @Override
    public final double[] possibleCashFlowTimes() {
        QL.require(finalized_, "composite not finalized");
        return cashflowTimes_;
    }

    @Override
    public final void reset() {
        for (final SubProduct sub : components_) {
            sub.product.reset();
            sub.done = false;
        }
        currentIndex_ = 0;
    }

    /**
     * Add a sub-product with a positive multiplier (default 1.0).
     * The product is cloned at insertion (P3K-3).
     */
    public final void add(final MarketModelMultiProduct product, final double multiplier) {
        QL.require(!finalized_, "product already finalized");
        final EvolutionDescription d = product.evolution();
        if (!components_.isEmpty()) {
            final EvolutionDescription d1 = components_.get(0).product.evolution();
            final double[] rt1 = d1.rateTimes();
            final double[] rt2 = d.rateTimes();
            QL.require(rt1.length == rt2.length && Arrays.equals(rt1, rt2),
                    "incompatible rate times");
        }
        final SubProduct sp = new SubProduct();
        sp.product = product.clone();
        sp.multiplier = multiplier;
        sp.done = false;
        components_.add(sp);
        allEvolutionTimes_.add(d.evolutionTimes().clone());
    }

    public final void add(final MarketModelMultiProduct product) {
        add(product, 1.0);
    }

    /** Subtract — equivalent to {@code add(product, -multiplier)}. */
    public final void subtract(final MarketModelMultiProduct product, final double multiplier) {
        add(product, -multiplier);
    }

    public final void subtract(final MarketModelMultiProduct product) {
        subtract(product, 1.0);
    }

    /**
     * Finalize the composite — compute merged evolution / cash-flow time vectors.
     * Must be called once after all sub-products have been added.
     */
    public final void finalizeComposite() {
        QL.require(!finalized_, "product already finalized");
        QL.require(!components_.isEmpty(), "no sub-product provided");

        final EvolutionDescription description = components_.get(0).product.evolution();
        rateTimes_ = description.rateTimes().clone();

        final Utilities.MergeResult merge = Utilities.mergeTimes(allEvolutionTimes_);
        evolutionTimes_ = merge.mergedTimes();
        isInSubset_ = merge.isPresent();

        // collect all sub-product cash-flow times into one big list
        final List<Double> allCashflowTimes = new ArrayList<>();
        for (final SubProduct sub : components_) {
            final double[] cft = sub.product.possibleCashFlowTimes();
            for (final double t : cft) {
                allCashflowTimes.add(t);
            }
            // allocate working vectors
            final int np = sub.product.numberOfProducts();
            final int nc = sub.product.maxNumberOfCashFlowsPerProductPerStep();
            sub.numberOfCashflows = new int[np];
            sub.cashflows = new CashFlow[np][nc];
            for (int p = 0; p < np; ++p) {
                for (int j = 0; j < nc; ++j) {
                    sub.cashflows[p][j] = new CashFlow();
                }
            }
        }

        // sort + uniq
        final double[] arr = new double[allCashflowTimes.size()];
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = allCashflowTimes.get(i);
        }
        Arrays.sort(arr);
        int u = 0;
        for (int i = 0; i < arr.length; ++i) {
            if (i == 0 || arr[i] != arr[i - 1]) {
                arr[u++] = arr[i];
            }
        }
        cashflowTimes_ = Arrays.copyOf(arr, u);

        // map each sub-product's cash-flow time into the merged vector
        for (final SubProduct sub : components_) {
            final double[] productTimes = sub.product.possibleCashFlowTimes();
            sub.timeIndices = new int[productTimes.length];
            for (int j = 0; j < productTimes.length; ++j) {
                int idx = -1;
                for (int k = 0; k < cashflowTimes_.length; ++k) {
                    if (cashflowTimes_[k] == productTimes[j]) {
                        idx = k;
                        break;
                    }
                }
                sub.timeIndices[j] = (idx < 0) ? cashflowTimes_.length : idx;
            }
        }

        evolution_ = new EvolutionDescription(rateTimes_, evolutionTimes_);
        finalized_ = true;
    }

    public final int size() { return components_.size(); }

    public final MarketModelMultiProduct item(final int i) { return components_.get(i).product; }

    public final double multiplier(final int i) { return components_.get(i).multiplier; }
}
