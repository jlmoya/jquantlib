/*
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

/*
 Copyright (C) 2005 Joseph Wang
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.pricingengines.vanilla.finitedifferences;

import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Finite-differences pricing engine for dividend American options
 *
 * @author Richard Gomes
 * @test the correctness of the returned greeks is tested by reproducing numerical derivatives.
 * @test the invariance of the results upon addition of null dividends is tested.
 * @bug results are not overly reliable.
 * @bug method impliedVolatility() utterly fails
 * @category vanillaengines
 */


/*
 * C++ v1.42.1 declares three typedef aliases:
 *   typedef FDEngineAdapter<FDAmericanCondition<FDDividendEngine>, DividendVanillaOption::engine> FDDividendAmericanEngine;
 *   typedef FDEngineAdapter<FDAmericanCondition<FDDividendEngineMerton73>, DividendVanillaOption::engine> FDDividendAmericanEngineMerton73;
 *   typedef FDEngineAdapter<FDAmericanCondition<FDDividendEngineShiftScale>, DividendVanillaOption::engine> FDDividendAmericanEngineShiftScale;
 * Java port currently materialises only the first; the Merton73 and ShiftScale
 * variants remain unported.
 */
public class FDDividendAmericanEngine
        extends FDEngineAdapter< FDAmericanCondition< FDDividendEngine >, DividendVanillaOption.Engine >
        implements DividendVanillaOption.Engine {

    //
    // public constructors
    //

    public FDDividendAmericanEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 100, 100, false);
        super.impl = new Impl(this);
    }

    public FDDividendAmericanEngine(final GeneralizedBlackScholesProcess process, final int timeSteps) {
        this(process, timeSteps, 100, false);
        super.impl = new Impl(this);
    }

    public FDDividendAmericanEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int gridPoints) {
        this(process, timeSteps, gridPoints, false);
        super.impl = new Impl(this);
    }

    public FDDividendAmericanEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int gridPoints, final boolean timeDependent) {
        super(FDAmericanCondition.class, DividendVanillaOption.Engine.class, process, timeSteps, gridPoints,
                timeDependent);
        super.impl = new Impl(this);
    }

    //
    // private inner classes
    //

    @Override
    public Arguments getArguments() {
        return super.impl.getArguments();
    }

    //
    // implements OneAssetOption.Engine
    //

    @Override
    public Results getResults() {
        return super.impl.getResults();
    }

    @Override
    public void reset() {
        super.impl.reset();
    }

    @Override
    public void update() {
        super.impl.update();
    }

    //
    // implements Observer
    //

    @Override
    public void addObserver(final Observer observer) {
        super.impl.addObserver(observer);
    }

    //
    // implements Observable
    //

    @Override
    public int countObservers() {
        return super.impl.countObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        super.impl.deleteObserver(observer);
    }

    @Override
    public void deleteObservers() {
        super.impl.deleteObservers();
    }

    @Override
    public List< Observer > getObservers() {
        return super.impl.getObservers();
    }

    @Override
    public void notifyObservers() {
        super.impl.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        super.impl.notifyObservers(arg);
    }

    private class Impl extends DividendVanillaOption.EngineImpl {

        private final FDDividendAmericanEngine engine;

        private Impl(final FDDividendAmericanEngine engine) {
            this.engine = engine;
        }

        @Override
        public void calculate() {
            // calls FDEngineAdapter#calculate()
            engine.calculate();
        }
    }

}
