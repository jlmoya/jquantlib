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
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine.CashDividendModel;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Fluent builder for {@link FdBlackScholesVanillaEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}}
 * {@code MakeFdBlackScholesVanillaEngine} (Phase 2 L3-D).
 *
 * <p>C++ defaults: {@code tGrid_=100, xGrid_=100, dampingSteps_=0,
 * scheme=Douglas, localVol=false, illegalLocalVolOverwrite=-Null<Real>, cashDividendModel=Spot}.
 *
 * @see FdBlackScholesVanillaEngine
 */
public class MakeFdBlackScholesVanillaEngine {

    private final GeneralizedBlackScholesProcess process_;
    private DividendSchedule dividends_ = new DividendSchedule();
    private int tGrid_ = 100;
    private int xGrid_ = 100;
    private int dampingSteps_ = 0;
    private FdmSchemeDesc schemeDesc_ = FdmSchemeDesc.Douglas();
    private boolean localVol_ = false;
    private double illegalLocalVolOverwrite_ = Double.NaN;
    private FdmQuantoHelper quantoHelper_ = null;
    private CashDividendModel cashDividendModel_ = CashDividendModel.Spot;

    public MakeFdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeFdBlackScholesVanillaEngine withQuantoHelper(final FdmQuantoHelper quantoHelper) {
        this.quantoHelper_ = quantoHelper;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withTGrid(final int tGrid) {
        this.tGrid_ = tGrid;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withXGrid(final int xGrid) {
        this.xGrid_ = xGrid;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withDampingSteps(final int dampingSteps) {
        this.dampingSteps_ = dampingSteps;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withFdmSchemeDesc(final FdmSchemeDesc schemeDesc) {
        this.schemeDesc_ = schemeDesc;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withLocalVol(final boolean localVol) {
        this.localVol_ = localVol;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withIllegalLocalVolOverwrite(final double illegalLocalVolOverwrite) {
        this.illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withCashDividends(final List< Date > dividendDates,
            final List< Double > dividendAmounts) {
        this.dividends_ = new DividendSchedule();
        for ( final Dividend d : Dividend.DividendVector(dividendDates, dividendAmounts) ) {
            this.dividends_.add(d);
        }
        return this;
    }

    public MakeFdBlackScholesVanillaEngine withCashDividendModel(final CashDividendModel cashDividendModel) {
        this.cashDividendModel_ = cashDividendModel;
        return this;
    }

    public PricingEngine value() {
        return new FdBlackScholesVanillaEngine(process_, dividends_, quantoHelper_, tGrid_, xGrid_, dampingSteps_,
                schemeDesc_, cashDividendModel_, localVol_, illegalLocalVolOverwrite_);
    }
}
