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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Chiara Fornarola
 Copyright (C) 2007 Katiuscia Manzoni
*/

package org.jquantlib.model.marketmodels.correlations;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.CovarianceDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.SwapForwardMappings;

/**
 * Coterminal-swap-rate correlation structure derived from a forward-rate correlation structure via the zed-matrix
 * mapping.
 *
 * <p>Faithful port of {@code ql/models/marketmodels/correlations/cotswapfromfwdcorrelation.{hpp,cpp}} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>For each step {@code k} where the underlying forward correlation has a matrix {@code F_k}, the coterminal-swap
 * correlation is computed as {@code Decomp(Z * F_k * Z^T).correlationMatrix()}, where {@code Z} is the zed matrix
 * returned by {@link SwapForwardMappings#coterminalSwapZedMatrix(CurveState, double)}. Off-diagonal entries
 * corresponding to expired rates (where {@code corrTimes[k] > rateTimes[j]}) are zeroed.
 *
 * @see SwapForwardMappings#coterminalSwapZedMatrix(CurveState, double)
 * @see CovarianceDecomposition
 */
public class CotSwapFromFwdCorrelation extends PiecewiseConstantCorrelation {

    private final PiecewiseConstantCorrelation fwdCorr_;
    private final int numberOfRates_;
    private final List<Matrix> swapCorrMatrices_;

    public CotSwapFromFwdCorrelation(final PiecewiseConstantCorrelation fwdCorr,
                                     final CurveState curveState,
                                     final double displacement) {
        this.fwdCorr_ = fwdCorr;
        this.numberOfRates_ = fwdCorr.numberOfRates();
        this.swapCorrMatrices_ = new ArrayList<Matrix>(fwdCorr.correlations().size());

        QL.require(numberOfRates_ == curveState.numberOfRates(),
                "mismatch between number of rates in fwdCorr (" + numberOfRates_
                        + ") and curveState (" + curveState.numberOfRates() + ")");

        final Matrix zed = SwapForwardMappings.coterminalSwapZedMatrix(curveState, displacement);
        final Matrix zedT = zed.transpose();
        final List<Matrix> fwdCorrMatrices = fwdCorr.correlations();
        final double[] rateTimes = curveState.rateTimes();
        final List<Double> corrTimes = fwdCorr_.times();
        for (int k = 0; k < fwdCorrMatrices.size(); ++k) {
            final Matrix covariance = zed.mul(fwdCorrMatrices.get(k)).mul(zedT);
            final Matrix swapCorr = new CovarianceDecomposition(covariance).correlationMatrix();
            // zero expired rates' correlation coefficients
            for (int i = 0; i < numberOfRates_; ++i) {
                for (int j = 0; j <= i; ++j) {
                    if (corrTimes.get(k) > rateTimes[j]) {
                        swapCorr.set(i, j, 0.0);
                        swapCorr.set(j, i, 0.0);
                    }
                }
            }
            swapCorrMatrices_.add(swapCorr);
        }
    }

    @Override
    public List<Double> times() {
        return fwdCorr_.times();
    }

    @Override
    public List<Double> rateTimes() {
        return fwdCorr_.rateTimes();
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public List<Matrix> correlations() {
        return swapCorrMatrices_;
    }
}
