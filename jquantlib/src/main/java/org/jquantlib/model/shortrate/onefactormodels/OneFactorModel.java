/*
Copyright (C)
2008 Praneet Tiwari
2009 Ueli Hofstetter

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
package org.jquantlib.model.shortrate.onefactormodels;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.methods.lattices.Tree;
import org.jquantlib.methods.lattices.TreeLattice1D;
import org.jquantlib.methods.lattices.TrinomialTree;
import org.jquantlib.model.TermStructureFittingParameter;
import org.jquantlib.model.TermStructureFittingParameter.NumericalImpl;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Single-factor short-rate model abstract class
 * 
 * @category shortrate
 * 
 * @author Praneet Tiwari
 */
@QualityAssurance(quality=Quality.Q0_UNFINISHED, version=Version.V097, reviewers="Richard Gomes")
public abstract class OneFactorModel extends ShortRateModel {

    //
    // public constructors
    //

    public OneFactorModel(final int nArguments) {
        super(nArguments);
    }


    //
    // public methods
    //

    /**
     * Return by default a trinomial recombining tree.
     */
    @Override
    public  Lattice tree(final TimeGrid  grid) /* @ReadOnly */ {
        final TrinomialTree trinomial = new TrinomialTree(dynamics().process(), grid);
        //XXX return boost::shared_ptr<Lattice>( new ShortRateTree(trinomial, dynamics(), grid) );
        return new ShortRateTree(trinomial, dynamics(), grid);
    }


    //
    // public abstract methods
    //

    /**
     * Returns the short-rate dynamics
     */
    public abstract ShortRateDynamics dynamics() /* @ReadOnly */ ;


    //
    // private inner classes
    //

    /**
     * <p>Base class describing the short-rate dynamics. </p>
     * <p>
     * Aligned to v1.42.1 onefactormodel.hpp lines 54-55: {@code class
     * OneFactorModel::ShortRateDynamics} is declared {@code public} in C++.
     * Elevated from {@code protected} to {@code public} in Phase 2h WI-1
     * so the modern Fdm framework operators (FdmHullWhiteOp) can refer to
     * the dynamics return type from outside the {@code onefactormodels}
     * package.
     */
    public abstract class ShortRateDynamics {

        //
        // private fields
        //

        private final StochasticProcess1D process_;


        //
        // public constructors
        //

        protected ShortRateDynamics(final StochasticProcess1D process) {
            this.process_ = process;
        }


        //
        // public methods
        //

        /**
         * Returns the risk-neutral dynamics of the state variable.
         */
        public final StochasticProcess1D process() {
            return this.process_;
        }


        //
        // public abstract methods
        //

        /**
         * Compute state variable from short rate.
         */
        public abstract double variable(/* @Time */ double t, /* @Rate */ double r) /* @ReadOnly */ ;

        /**
         * Compute short rate from state variable.
         */
        public abstract /* @Rate */ double shortRate(/* @Time */ double t, double variable) /* @ReadOnly */ ;

    }


    /**
     * Recombining trinomial tree discretizing the state variable.
     * <p>
     * Visibility-widened to {@code public} so cross-package callers
     * (callable-bond engines) can downcast a {@code Lattice} returned from
     * {@link #tree(TimeGrid)} and invoke {@link #setSpread} for OAS pricing.
     * Mirrors C++ v1.42.1 onefactormodel.hpp line 75 where the class is
     * declared {@code public} on {@code OneFactorModel}.
     */
    public class ShortRateTree extends TreeLattice1D { //TODO: <OneFactorModel.ShortRateTree> {

        //
        // private fields
        //

        private final  TrinomialTree tree_;
        private final  ShortRateDynamics dynamics_;
        /**
         * Continuously-compounded spread added to the model short-rate when
         * computing discounts. Mirrors C++ v1.42.1
         * onefactormodel.hpp:113 {@code Spread spread_;}. Default 0.0.
         */
        private double spread_ = 0.0;


        //
        // public constructors
        //

        /**
         * Plain tree build-up from short-rate dynamics.
         */
        public ShortRateTree(
                final TrinomialTree tree,
                final ShortRateDynamics dynamics,
                final TimeGrid timeGrid) {
            super(timeGrid, tree.size(1));
            this.tree_ = tree;
            this.dynamics_ = dynamics;
        }

        /**
         * {@link Tree} build-up + numerical fitting to term-structure.
         */
        public ShortRateTree(
                final TrinomialTree tree,
                final ShortRateDynamics dynamics,
                final TermStructureFittingParameter.NumericalImpl theta,
                final TimeGrid  timeGrid) {

            super(timeGrid, tree.size(1));
            this.tree_ = tree;
            this.dynamics_ = dynamics;
            theta.reset();
            double value = 1.0;
            final double vMin = -100.0;
            final double vMax = 100.0;
            for (int i=0; i<(timeGrid.size() - 1); i++) {
                final double discountBond = theta.termStructure().currentLink().discount(t.get(i+1));
                final Helper finder = new Helper(i, discountBond, theta, this);
                final Brent s1d = new Brent();
                s1d.setMaxEvaluations(1000);
                value = s1d.solve(finder, 1e-7, value, vMin, vMax);
                // vMin = value - 1.0;
                // vMax = value + 1.0;
                theta.change(value);
            }
        }


        //
        // public methods
        //

        @Override
        public int size(final int i) /* @ReadOnly */ {
            return tree_.size(i);
        }

        @Override
        public /* @DiscountFactor */ double discount(final int i, final int index) /* @ReadOnly */ {
            final double x = tree_.underlying(i, index);
            // Mirrors C++ v1.42.1 onefactormodel.hpp:91-94 —
            //   Rate r = dynamics_->shortRate(timeGrid()[i], x) + spread_;
            /*@Rate*/ final double r = dynamics_.shortRate(timeGrid().get(i), x) + spread_;
            return Math.exp(-r*timeGrid().dt(i));
        }

        /**
         * Set a continuously-compounded spread to be added to the model
         * short-rate when computing discount factors. Used by callable-bond
         * engines to support OAS pricing. Mirrors C++ v1.42.1
         * onefactormodel.hpp:105-108 {@code setSpread(Spread)}.
         */
        public void setSpread(final double spread) {
            this.spread_ = spread;
        }

        @Override
        public double underlying(final int i, final int index) /* @ReadOnly */ {
            return tree_.underlying(i, index);
        }

        @Override
        public int descendant(final int i, final int index, final int branch) /* @ReadOnly */ {
            return tree_.descendant(i, index, branch);
        }

        @Override
        public double probability(final int i, final int index, final int branch) /* @ReadOnly */ {
            return tree_.probability(i, index, branch);
        }


        //
        // private inner class
        //

        private class Helper implements Ops.DoubleOp {

            //
            // private fields
            //

            private final int size_;
            private final int i_;
            private final Array  statePrices_;
            private final double discountBondPrice_;
            protected final TermStructureFittingParameter.NumericalImpl theta_;
            private final ShortRateTree tree_;


            //
            // public methods
            //

            private Helper(
                    final int i,
                    final double discountBondPrice,
                    final TermStructureFittingParameter.NumericalImpl theta,
                    final ShortRateTree tree) {
                this.i_ = i;
                this.size_ = tree.size(i);
                this.statePrices_ = tree.statePrices(i);
                this.discountBondPrice_ = discountBondPrice;
                this.theta_ = theta;
                this.tree_ = tree;
                this.theta_.set(tree.timeGrid().get(i), 0.0);
            }

            @Override
            public double op(final double theta) /* @ReadOnly */ {
                double value = discountBondPrice_;
                this.theta_.change(theta);
                for (int j=0; j<size_; j++) {
                    value -= statePrices_.get(j)*tree_.discount(i_,j);
                }
                return value;
            }

        }

    }

}
