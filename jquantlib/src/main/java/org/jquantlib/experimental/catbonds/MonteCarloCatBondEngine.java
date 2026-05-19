/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Monte Carlo pricing engine for catastrophe bonds.
 *
 * <p>Port of {@code ql/experimental/catbonds/montecarlocatbondengine.hpp/.cpp}
 * {@code MonteCarloCatBondEngine}.
 *
 * <p>The engine runs up to {@code MAX_PATHS} simulated catastrophe paths,
 * discounts the cash flows along each path using the provided discount curve, and averages the results to obtain NPV,
 * loss probability, exhaustion probability, and expected loss.
 */
public class MonteCarloCatBondEngine extends CatBond.EngineImpl {

    private static final int MAX_PATHS = 10_000;

    private final CatRisk catRisk_;
    private final Handle< YieldTermStructure > discountCurve_;
    // null means "use Settings default"
    private final Boolean includeSettlementDateFlows_;

    public MonteCarloCatBondEngine(final CatRisk catRisk, final Handle< YieldTermStructure > discountCurve) {
        this(catRisk, discountCurve, null);
    }

    public MonteCarloCatBondEngine(final CatRisk catRisk, final Handle< YieldTermStructure > discountCurve,
            final Boolean includeSettlementDateFlows) {

        this.catRisk_ = catRisk;
        this.discountCurve_ = discountCurve;
        this.includeSettlementDateFlows_ = includeSettlementDateFlows;
        discountCurve_.addObserver(this);
    }

    public Handle< YieldTermStructure > discountCurve() {
        return discountCurve_;
    }

    @Override
    public void calculate() {
        QL.require(!discountCurve_.empty(), "discounting term structure handle is empty");

        final CatBond.ArgumentsImpl a = arguments_;
        final CatBond.ResultsImpl r = results_;

        final Date valuationDate = discountCurve_.currentLink().referenceDate();

        // C++: bool includeRefDateFlows = includeSettlementDateFlows_ ?
        //     *includeSettlementDateFlows_ : Settings::instance().includeReferenceDateEvents();
        // Java: fall back to isTodaysPayments() as the closest equivalent.
        final boolean includeRefDateFlows = (includeSettlementDateFlows_ != null)
                ? includeSettlementDateFlows_
                : new Settings().isTodaysPayments();

        final double[] outLP = { 0.0 };
        final double[] outEP = { 0.0 };
        final double[] outEL = { 0.0 };

        r.value = npv(includeRefDateFlows, valuationDate, valuationDate, outLP, outEP, outEL);
        r.lossProbability = outLP[0];
        r.exhaustionProbability = outEP[0];
        r.expectedLoss = outEL[0];

        // Settlement value
        if ( !includeRefDateFlows && valuationDate.equals(a.settlementDate) ) {
            r.settlementValue = r.value;
        } else {
            r.settlementValue = npv(includeRefDateFlows, a.settlementDate, a.settlementDate, outLP, outEP, outEL);
        }
    }

    // ------------------------------------------------------------------
    // protected helpers (mirroring C++ protected API)
    // ------------------------------------------------------------------

    protected double npv(final boolean includeSettlementDateFlows, final Date settlementDate, final Date npvDate,
            final double[] lossProbability, final double[] exhaustionProbability, final double[] expectedLoss) {

        lossProbability[0] = 0.0;
        exhaustionProbability[0] = 0.0;
        expectedLoss[0] = 0.0;

        final Leg cashflows = arguments_.cashflows;
        if ( cashflows.isEmpty() ) {
            return 0.0;
        }

        final Date sd = (settlementDate == null || settlementDate.isNull())
                ? new Settings().evaluationDate()
                : settlementDate;

        final Date nd = (npvDate == null || npvDate.isNull()) ? sd : npvDate;

        final Date effectiveDate = Date.max(arguments_.startDate, sd);
        final Date maturityDate = cashflows.last().date();

        final CatSimulation catSimulation = catRisk_.newSimulation(effectiveDate, maturityDate);
        final List< DateRealPair > eventsPath = new ArrayList<>();
        final NotionalPath notionalPath = new NotionalPath();

        final double riskFreeNPV = pathNpv(includeSettlementDateFlows, sd, notionalPath);

        double totalNPV = 0.0;
        int pathCount = 0;

        while ( catSimulation.nextPath(eventsPath) && pathCount < MAX_PATHS ) {
            arguments_.notionalRisk.updatePath(eventsPath, notionalPath);

            if ( notionalPath.loss() > 0.0 ) {
                totalNPV += pathNpv(includeSettlementDateFlows, sd, notionalPath);
                lossProbability[0] += 1.0;
                if ( notionalPath.loss() == 1.0 ) {
                    exhaustionProbability[0] += 1.0;
                }
                expectedLoss[0] += notionalPath.loss();
            } else {
                totalNPV += riskFreeNPV;
            }
            pathCount++;
        }

        lossProbability[0] /= pathCount;
        exhaustionProbability[0] /= pathCount;
        expectedLoss[0] /= pathCount;

        return totalNPV / (pathCount * discountCurve_.currentLink().discount(nd));
    }

    protected double pathNpv(final boolean includeSettlementDateFlows, final Date settlementDate,
            final NotionalPath notionalPath) {

        double totalNPV = 0.0;
        for ( final CashFlow cf : arguments_.cashflows ) {
            if ( !cf.hasOccurred(settlementDate, includeSettlementDateFlows) ) {
                final double amount = cashFlowRiskyValue(cf, notionalPath);
                totalNPV += amount * discountCurve_.currentLink().discount(cf.date());
            }
        }
        return totalNPV;
    }

    protected double cashFlowRiskyValue(final CashFlow cf, final NotionalPath notionalPath) {
        return cf.amount() * notionalPath.notionalRate(cf.date());
    }
}
