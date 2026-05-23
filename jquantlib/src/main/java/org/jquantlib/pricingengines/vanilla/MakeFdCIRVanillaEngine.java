/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Dividend;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.utilities.FdmQuantoHelper;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.CoxIngersollRossProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Fluent builder for {@link FdCIRVanillaEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/fdcirvanillaengine.{hpp,cpp}}
 * {@code MakeFdCIRVanillaEngine} (Phase 2 L3-D).
 *
 * <p>C++ defaults: {@code tGrid_=10, xGrid_=100, rGrid_=100,
 * dampingSteps_=0, scheme=Hundsdorfer}.
 *
 * <p>Java port deviations from C++ v1.42.1:
 * <ul>
 *   <li>{@code withQuantoHelper} accepted but the underlying Java
 *       {@link FdCIRVanillaEngine} does not yet support the quanto
 *       adjustment. Calling {@code value()} with a non-null quanto helper
 *       throws.</li>
 * </ul>
 *
 * @see FdCIRVanillaEngine
 */
public class MakeFdCIRVanillaEngine {

    private final CoxIngersollRossProcess cirProcess_;
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final double rho_;
    private DividendSchedule dividends_ = new DividendSchedule();
    private int tGrid_ = 10;
    private int xGrid_ = 100;
    private int rGrid_ = 100;
    private int dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.Hundsdorfer();
    private FdmQuantoHelper quantoHelper_ = null;

    public MakeFdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
            final GeneralizedBlackScholesProcess bsProcess, final double rho) {
        QL.require(cirProcess != null, "null CIR process");
        QL.require(bsProcess != null, "null BS process");
        this.cirProcess_ = cirProcess;
        this.bsProcess_ = bsProcess;
        this.rho_ = rho;
    }

    public MakeFdCIRVanillaEngine withQuantoHelper(final FdmQuantoHelper quantoHelper) {
        this.quantoHelper_ = quantoHelper;
        return this;
    }

    public MakeFdCIRVanillaEngine withTGrid(final int tGrid) {
        this.tGrid_ = tGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withXGrid(final int xGrid) {
        this.xGrid_ = xGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withRGrid(final int rGrid) {
        this.rGrid_ = rGrid;
        return this;
    }

    public MakeFdCIRVanillaEngine withDampingSteps(final int dampingSteps) {
        this.dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdCIRVanillaEngine withFdmSchemeDesc(final FdmSchemeDesc schemeDesc) {
        this.schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdCIRVanillaEngine withCashDividends(final List< Date > dividendDates,
            final List< Double > dividendAmounts) {
        this.dividends_ = new DividendSchedule();
        for ( final Dividend d : Dividend.DividendVector(dividendDates, dividendAmounts) ) {
            this.dividends_.add(d);
        }
        return this;
    }

    public PricingEngine value() {
        QL.require(quantoHelper_ == null,
                "FdmQuantoHelper is not wired through the Java FdCIRVanillaEngine yet "
                        + "(Phase 2 L3-D port deferred); use a different engine until ported");
        return new FdCIRVanillaEngine(cirProcess_, bsProcess_, dividends_, tGrid_, xGrid_, rGrid_, dampingSteps_, rho_,
                schemeDesc_);
    }
}
