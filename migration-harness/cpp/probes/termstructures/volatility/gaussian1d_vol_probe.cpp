// migration-harness/cpp/probes/termstructures/volatility/gaussian1d_vol_probe.cpp
// Phase 2j WI-1.4 — Gaussian1dSmileSection + Gaussian1dSwaptionVolatility oracle data.
//
// Sections:
//   "atm_NNN" — SmileSection atm + annuity (engine-independent; testable in WI-1.4)
//   "vol_NNN" — SwaptionVol surface volatility (engine-dependent; deferred to WI-2)
//   "smile_NNN" — SmileSection vol/optionPrice (engine-dependent; deferred to WI-2)
//
// Oracle: C++ QuantLib v1.42.1.

#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/termstructures/volatility/gaussian1dsmilesection.hpp>
#include <ql/termstructures/volatility/swaption/gaussian1dswaptionvolatility.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/pricingengines/swaption/gaussian1dswaptionengine.hpp>

#include <vector>
#include <cstdio>
#include <cmath>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/volatility/gaussian1d_vol",
                        QL_VERSION, "gaussian1d_vol_probe");

    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;

    Handle<YieldTermStructure> yts(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(today, 0.03, Actual360())));

    std::vector<Date> volStepDates;
    volStepDates.push_back(today + Period(1, Years));
    volStepDates.push_back(today + Period(2, Years));
    std::vector<Real> vols;
    vols.push_back(0.01);
    vols.push_back(0.012);
    vols.push_back(0.015);
    Real reversion = 0.01;

    ext::shared_ptr<Gsr> gsr(new Gsr(yts, volStepDates, vols, reversion));

    // Use 2Y tenor swap index as index base
    ext::shared_ptr<SwapIndex> swapIdx2y(new EuriborSwapIsdaFixA(2 * Years, yts));

    // ─────────────────────────────────────────────────────────────────────────
    // Section A: atm + annuity (engine-independent; directly from Gaussian1dModel)
    // These values are testable in WI-1.4 because they only use swapRate()/swapAnnuity().
    // ─────────────────────────────────────────────────────────────────────────
    {
        // Use a default-constructed DayCounter (Actual365Fixed) for the SmileSection
        // base class. The atm/annuity values are independent of the day counter.
        Period tenors[] = {Period(1, Years), Period(2, Years), Period(5, Years)};
        Period expiries[] = {Period(1, Years), Period(2, Years), Period(3, Years)};

        int idx = 0;
        for (Period exp_p : expiries) {
            for (Period ten : tenors) {
                ext::shared_ptr<SwapIndex> si(new EuriborSwapIsdaFixA(ten, yts));
                Date fixing = si->fixingCalendar().adjust(today + exp_p);

                // Construct SmileSection with no engine (nullptr) → atm+annuity computable
                ext::shared_ptr<Gaussian1dSmileSection> smile(
                    new Gaussian1dSmileSection(fixing, si, gsr, Actual365Fixed()));

                Real atm_val = smile->atmLevel();
                // annuity is not directly accessible via public SmileSection API in C++;
                // compute it separately via model
                Real annuity_val = gsr->swapAnnuity(fixing, ten, Date(), 0.0, si);

                char nm[32];
                std::snprintf(nm, sizeof nm, "atm_%02d", idx++);
                out.addCase(nm,
                    json{{"expiry_serial", static_cast<long>(fixing.serialNumber())},
                         {"tenor_years", ten.length()},
                         {"expiry_period_years", exp_p.length()}},
                    json{{"atm", atm_val}, {"annuity", annuity_val}});
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section B: SmileSection volatility + optionPrice (engine-dependent)
    // Tagged "smile_" prefix; Java WI-2 will activate these cases.
    // ─────────────────────────────────────────────────────────────────────────
    {
        ext::shared_ptr<Gaussian1dSwaptionEngine> eng(
            new Gaussian1dSwaptionEngine(gsr, 64, 7.0, true, false,
                                        swapIdx2y->discountingTermStructure()));

        Date fixing2y = swapIdx2y->fixingCalendar().adjust(today + Period(2, Years));
        ext::shared_ptr<Gaussian1dSmileSection> smile(
            new Gaussian1dSmileSection(fixing2y, swapIdx2y, gsr, Actual365Fixed(), eng));

        Real strikes[] = {0.005, 0.01, 0.015, 0.02, 0.025, 0.03, 0.035, 0.04, 0.05, 0.06};
        int idx = 0;
        for (Real K : strikes) {
            Real v = 0.0;
            Real opt_call = 0.0;
            Real opt_put = 0.0;
            try { v = smile->volatility(K); } catch (...) { v = std::numeric_limits<Real>::quiet_NaN(); }
            try { opt_call = smile->optionPrice(K, Option::Call, 1.0); } catch (...) { opt_call = std::numeric_limits<Real>::quiet_NaN(); }
            try { opt_put  = smile->optionPrice(K, Option::Put,  1.0); } catch (...) { opt_put  = std::numeric_limits<Real>::quiet_NaN(); }

            char nm[32];
            std::snprintf(nm, sizeof nm, "smile_%02d", idx++);
            out.addCase(nm,
                json{{"strike", K}},
                json{{"vol", v}, {"option_call", opt_call}, {"option_put", opt_put}});
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section C: SwaptionVolatility surface (engine-dependent)
    // Tagged "swvol_" prefix; Java WI-2 will activate these cases.
    // ─────────────────────────────────────────────────────────────────────────
    {
        ext::shared_ptr<Gaussian1dSwaptionEngine> eng(
            new Gaussian1dSwaptionEngine(gsr, 64, 7.0, true, false,
                                        swapIdx2y->discountingTermStructure()));

        ext::shared_ptr<Gaussian1dSwaptionVolatility> swvol(
            new Gaussian1dSwaptionVolatility(swapIdx2y->fixingCalendar(), Following,
                                            swapIdx2y, gsr, Actual365Fixed(), eng));

        Period expiries[] = {Period(1, Years), Period(2, Years), Period(5, Years)};
        Period tenors[]   = {Period(2, Years), Period(5, Years), Period(10, Years)};
        Real   strikes[]  = {0.01, 0.02, 0.03, 0.04, 0.05};

        int idx = 0;
        for (Period e : expiries) {
            for (Period t : tenors) {
                for (Real K : strikes) {
                    Date d = swapIdx2y->fixingCalendar().adjust(today + e);
                    Real v = 0.0;
                    try {
                        v = swvol->volatility(d, t, K, true);
                    } catch (...) {
                        v = std::numeric_limits<Real>::quiet_NaN();
                    }
                    char nm[48];
                    std::snprintf(nm, sizeof nm, "swvol_%02d", idx++);
                    out.addCase(nm,
                        json{{"expiry_period_years", e.length()},
                             {"tenor_years", t.length()},
                             {"strike", K}},
                        json{{"vol", v}});
                }
            }
        }
    }

    out.write();
    return 0;
}
