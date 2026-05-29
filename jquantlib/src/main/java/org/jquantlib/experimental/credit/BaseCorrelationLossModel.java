/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Base Correlation loss model; interpolation is performed by portfolio (live)
 * amount percentage.
 *
 * <p>Java port of QuantLib v1.42.1 templated
 * {@code QuantLib::BaseCorrelationLossModel<BaseModel_T, Corr2DInt_T>}
 * ({@code ql/experimental/credit/basecorrelationlossmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is a template parameterised on the base loss model and the
 * 2D interpolator, with explicit specialisations. Java has no template
 * specialisation, so this class ports the registered vanilla typedef
 * {@code GaussianLHPFlatBCLM = BaseCorrelationLossModel<GaussianLHPLossModel,
 * BilinearInterpolation>}: it uses {@link GaussianLHPLossModel} as the base
 * model and a {@link BaseCorrelationTermStructure} (bilinear or bicubic) as the
 * correlation surface.
 *
 * <p>Mechanics (identical to C++): given a basket, the model computes the
 * remaining attachment/detachment ratios, builds two equity sub-baskets
 * {@code [0, attachRatio]} and {@code [0, detachRatio]}, each driven by a
 * {@link GaussianLHPLossModel} reading a local correlation
 * {@link SimpleQuote}. The expected tranche loss is the difference of the two
 * equity-tranche expected losses, each evaluated at the base correlation
 * interpolated off the surface at that loss level and date:
 * {@code ETL = EL([0,detach]; rho(d,detachRatio)) - EL([0,attach]; rho(d,attachRatio))}.
 *
 * <p>Java vs C++: the C++ {@code GaussianLHPLossModel} registers with its
 * correlation {@code Handle<Quote>} and recomputes its cache on notification.
 * The Java {@link GaussianLHPLossModel} caches on construction but does not
 * self-register, so {@link #setupModels()} explicitly adds each sub-model as an
 * observer of its local quote — making {@code localCorrelation*.setValue(...)}
 * trigger a cache recompute, mirroring the C++ reactive behaviour.
 */
public class BaseCorrelationLossModel extends DefaultLossModel {

    /** Correlation buffer to pick up values from the surface and trigger calculation. */
    private final SimpleQuote localCorrelationAttach_;
    private final SimpleQuote localCorrelationDetach_;
    private final List< Double > recoveries_;
    private final Handle< BaseCorrelationTermStructure > correlTS_;

    private double attachRatio_;
    private double detachRatio_;
    private double remainingNotional_;

    /** Models of equity baskets. */
    private Basket basketAttach_;
    private Basket basketDetach_;
    private GaussianLHPLossModel scalarCorrelModelAttach_;
    private GaussianLHPLossModel scalarCorrelModelDetach_;

    public BaseCorrelationLossModel(final Handle< BaseCorrelationTermStructure > correlTS,
            final List< Double > recoveries) {
        this.localCorrelationAttach_ = new SimpleQuote(0.0);
        this.localCorrelationDetach_ = new SimpleQuote(0.0);
        this.recoveries_ = new ArrayList<>(recoveries);
        this.correlTS_ = correlTS;
        correlTS.addObserver(this);
    }

    /** React to base-correl surface notifications (quotes or reference date). */
    @Override
    public void update() {
        setupModels();
        // tell basket to notify instruments, etc, that we are invalid
        if ( basket != null ) {
            basket.notifyObservers();
        }
    }

    /** Update model caches after basket assignment. Mirrors C++ {@code resetModel()}. */
    @Override
    protected void resetModel() {
        remainingNotional_ = basket.remainingNotional();
        attachRatio_ = basket.remainingAttachmentAmount() / remainingNotional_;
        detachRatio_ = basket.remainingDetachmentAmount() / remainingNotional_;

        basketAttach_ = new Basket(basket.refDate(), basket.remainingNames(), basket.remainingNotionals(),
                basket.pool(), 0.0, attachRatio_, basket.claim());
        basketDetach_ = new Basket(basket.refDate(), basket.remainingNames(), basket.remainingNotionals(),
                basket.pool(), 0.0, detachRatio_, basket.claim());
        setupModels();
    }

    /**
     * Sets up attach/detach models. Gets called on basket update. This is the
     * Java equivalent of the C++ {@code setupModels()} specialisation for
     * {@code BaseCorrelationLossModel<GaussianLHPLossModel, BilinearInterpolation>}.
     */
    protected void setupModels() {
        if ( basketAttach_ == null || basketDetach_ == null ) {
            return; // not yet bound to a basket
        }
        scalarCorrelModelAttach_ = new GaussianLHPLossModel(localCorrelationAttach_, recoveries_);
        scalarCorrelModelDetach_ = new GaussianLHPLossModel(localCorrelationDetach_, recoveries_);

        // C++ GaussianLHPLossModel registers with its correlation Handle<Quote>;
        // the Java model does not self-register, so wire it here so that
        // localCorrelation*.setValue(...) recomputes the cached beta/biphi.
        localCorrelationAttach_.addObserver(scalarCorrelModelAttach_);
        localCorrelationDetach_.addObserver(scalarCorrelModelDetach_);

        basketAttach_.setLossModel(scalarCorrelModelAttach_);
        basketDetach_.setLossModel(scalarCorrelModelDetach_);
    }

    /**
     * Expected tranche loss on the live part of the basket. Mirrors C++
     * {@code BaseCorrelationLossModel::expectedTrancheLoss}.
     */
    @Override
    public double expectedTrancheLoss(final Date d) {
        final double correlK1 = correlTS_.currentLink().correlation(d, attachRatio_);
        final double correlK2 = correlTS_.currentLink().correlation(d, detachRatio_);

        // reset correl and call base models which have the different baskets associated.
        localCorrelationAttach_.setValue(correlK1);
        final double expLossK1 = basketAttach_.expectedTrancheLoss(d);
        localCorrelationDetach_.setValue(correlK2);
        final double expLossK2 = basketDetach_.expectedTrancheLoss(d);
        return expLossK2 - expLossK1;
    }

    /** Read-only access to the local attach-correlation quote (for testing/inspection). */
    public Quote localCorrelationAttach() {
        return localCorrelationAttach_;
    }

    /** Read-only access to the local detach-correlation quote (for testing/inspection). */
    public Quote localCorrelationDetach() {
        return localCorrelationDetach_;
    }
}
