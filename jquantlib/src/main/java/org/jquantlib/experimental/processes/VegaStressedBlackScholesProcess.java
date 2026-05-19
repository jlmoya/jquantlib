/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2010 Michael Heckl
 */
package org.jquantlib.experimental.processes;

import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Black-Scholes process which supports local vega stress tests.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/processes/vegastressedblackscholesprocess.{hpp,cpp}}.
 * <p>
 * The diffusion is shifted upward by {@code stressLevel} when both time {@code t} is in
 * {@code [lowerTimeBorder, upperTimeBorder]} and asset {@code x} is in {@code [lowerAssetBorder, upperAssetBorder]};
 * outside the stress region the underlying BlackVol value is returned unmodified.
 *
 * @author Phase 4n WI port
 */
public class VegaStressedBlackScholesProcess extends GeneralizedBlackScholesProcess {

    private double lowerTimeBorderForStressTest_;
    private double upperTimeBorderForStressTest_;
    private double lowerAssetBorderForStressTest_;
    private double upperAssetBorderForStressTest_;
    private double stressLevel_;

    public VegaStressedBlackScholesProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > dividendTS, final Handle< YieldTermStructure > riskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS) {
        this(x0, dividendTS, riskFreeTS, blackVolTS, 0.0, 1000000.0, 0.0, 1000000.0, 0.0);
    }

    public VegaStressedBlackScholesProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > dividendTS, final Handle< YieldTermStructure > riskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS, final double lowerTimeBorderForStressTest,
            final double upperTimeBorderForStressTest, final double lowerAssetBorderForStressTest,
            final double upperAssetBorderForStressTest, final double stressLevel) {
        super(x0, dividendTS, riskFreeTS, blackVolTS);
        this.lowerTimeBorderForStressTest_ = lowerTimeBorderForStressTest;
        this.upperTimeBorderForStressTest_ = upperTimeBorderForStressTest;
        this.lowerAssetBorderForStressTest_ = lowerAssetBorderForStressTest;
        this.upperAssetBorderForStressTest_ = upperAssetBorderForStressTest;
        this.stressLevel_ = stressLevel;
    }

    public double getLowerTimeBorderForStressTest() {
        return lowerTimeBorderForStressTest_;
    }

    public void setLowerTimeBorderForStressTest(final double LTB) {
        lowerTimeBorderForStressTest_ = LTB;
        update();
    }

    public double getUpperTimeBorderForStressTest() {
        return upperTimeBorderForStressTest_;
    }

    public void setUpperTimeBorderForStressTest(final double UTB) {
        upperTimeBorderForStressTest_ = UTB;
        update();
    }

    public double getLowerAssetBorderForStressTest() {
        return lowerAssetBorderForStressTest_;
    }

    public void setLowerAssetBorderForStressTest(final double LAB) {
        lowerAssetBorderForStressTest_ = LAB;
        update();
    }

    public double getUpperAssetBorderForStressTest() {
        return upperAssetBorderForStressTest_;
    }

    public void setUpperAssetBorderForStressTest(final double UBA) {
        upperAssetBorderForStressTest_ = UBA;
        update();
    }

    public double getStressLevel() {
        return stressLevel_;
    }

    public void setStressLevel(final double SL) {
        stressLevel_ = SL;
        update();
    }

    @Override
    public double diffusion(final double t, final double x) {
        if ( lowerTimeBorderForStressTest_ <= t && t <= upperTimeBorderForStressTest_
                && lowerAssetBorderForStressTest_ <= x && x <= upperAssetBorderForStressTest_ ) {
            return super.diffusion(t, x) + stressLevel_;
        } else {
            return super.diffusion(t, x);
        }
    }
}
