/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2015 Klaus Spanderen
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Dividend;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.utilities.FdmQuantoHelper;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Fluent builder for {@link FdHestonVanillaEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/fdhestonvanillaengine.{hpp,cpp}}
 * {@code MakeFdHestonVanillaEngine} (Phase 2 L3-D).
 *
 * <p>C++ defaults: {@code tGrid_=100, xGrid_=100, vGrid_=50, dampingSteps_=0,
 * scheme=Hundsdorfer}.
 *
 * <p>Java port deviations from C++ v1.42.1:
 * <ul>
 *   <li>{@code withQuantoHelper} accepted but the underlying Java
 *       {@link FdHestonVanillaEngine} does not yet support the quanto
 *       adjustment (see its JavaDoc). Calling {@code value()} with a
 *       non-null quanto helper throws — the call site should branch on
 *       quanto and use a different engine until {@code FdmQuantoHelper} is
 *       wired through Heston.</li>
 *   <li>{@code mixingFactor} is fixed at 1.0 by the C++ builder; the Java
 *       engine exposes it as a constructor parameter but
 *       {@code MakeFdHestonVanillaEngine} mirrors C++ and locks it to 1.0.</li>
 * </ul>
 *
 * @see FdHestonVanillaEngine
 */
public class MakeFdHestonVanillaEngine {

    private final HestonModel hestonModel_;
    private DividendSchedule dividends_ = new DividendSchedule();
    private int tGrid_ = 100;
    private int xGrid_ = 100;
    private int vGrid_ = 50;
    private int dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.Hundsdorfer();
    private LocalVolTermStructure leverageFct_ = null;
    private FdmQuantoHelper quantoHelper_ = null;

    public MakeFdHestonVanillaEngine(final HestonModel hestonModel) {
        QL.require(hestonModel != null, "null Heston model");
        this.hestonModel_ = hestonModel;
    }

    public MakeFdHestonVanillaEngine withQuantoHelper(final FdmQuantoHelper quantoHelper) {
        this.quantoHelper_ = quantoHelper;
        return this;
    }

    public MakeFdHestonVanillaEngine withTGrid(final int tGrid) {
        this.tGrid_ = tGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withXGrid(final int xGrid) {
        this.xGrid_ = xGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withVGrid(final int vGrid) {
        this.vGrid_ = vGrid;
        return this;
    }

    public MakeFdHestonVanillaEngine withDampingSteps(final int dampingSteps) {
        this.dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdHestonVanillaEngine withFdmSchemeDesc(final FdmSchemeDesc schemeDesc) {
        this.schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdHestonVanillaEngine withLeverageFunction(final LocalVolTermStructure leverageFct) {
        this.leverageFct_ = leverageFct;
        return this;
    }

    public MakeFdHestonVanillaEngine withCashDividends(final List< Date > dividendDates,
            final List< Double > dividendAmounts) {
        this.dividends_ = new DividendSchedule();
        for ( final Dividend d : Dividend.DividendVector(dividendDates, dividendAmounts) ) {
            this.dividends_.add(d);
        }
        return this;
    }

    public PricingEngine value() {
        QL.require(quantoHelper_ == null,
                "FdmQuantoHelper is not wired through the Java FdHestonVanillaEngine yet "
                        + "(Phase 2 L3-D port deferred); use a different engine until ported");
        return new FdHestonVanillaEngine(hestonModel_, hestonModel_.process(), dividends_, tGrid_, xGrid_, vGrid_,
                dampingSteps_, schemeDesc_, 1.0, leverageFct_);
    }
}
