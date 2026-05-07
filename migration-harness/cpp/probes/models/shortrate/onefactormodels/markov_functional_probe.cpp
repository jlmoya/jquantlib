// migration-harness/cpp/probes/models/shortrate/onefactormodels/markov_functional_probe.cpp
// Phase 2j.5 Track C.3 — emit MarkovFunctional fingerprints
// (numeraireTime, post-construction sigma readback, calibrated numeraire on
// flat-flat setup, zerobond at calibration grid times).
//
// Uses a deterministic flat-yield + flat-swaption-vol fixture that mirrors
// (a slimmed version of) the C++ test-suite testCalibrationOneInstrumentSet
// configuration — sufficient to detect A20 iteration-order drift without
// pulling in the full md0Yts / SABR / Kahale infrastructure.

#include <ql/version.hpp>
#include "../../../common.hpp"

#include <ql/models/shortrate/onefactormodels/markovfunctional.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/swaption/swaptionconstantvol.hpp>
#include <ql/termstructures/volatility/optionlet/constantoptionletvol.hpp>
#include <ql/indexes/swap/eurliborswap.hpp>
#include <ql/indexes/ibor/eurlibor.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/period.hpp>

#include <vector>
#include <cstdio>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("models/shortrate/onefactormodels/markov_functional",
                        QL_VERSION, "markov_functional_probe");

    const Date referenceDate(14, November, 2012);
    Settings::instance().evaluationDate() = referenceDate;

    Handle<YieldTermStructure> flatYts(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(0, TARGET(), 0.03, Actual365Fixed())));

    Handle<SwaptionVolatilityStructure> flatSwaptionVts(
        ext::shared_ptr<SwaptionVolatilityStructure>(
            new ConstantSwaptionVolatility(
                0, TARGET(), ModifiedFollowing, 0.20, Actual365Fixed())));

    Handle<OptionletVolatilityStructure> flatOptionletVts(
        ext::shared_ptr<OptionletVolatilityStructure>(
            new ConstantOptionletVolatility(
                0, TARGET(), ModifiedFollowing, 0.20, Actual365Fixed())));

    ext::shared_ptr<SwapIndex> swapIndexBase(new EurLiborSwapIsdaFixA(1 * Years));
    ext::shared_ptr<IborIndex> iborIndex(new EURLibor6M());

    // ── Swaption-calibrated MarkovFunctional, single calibration expiry
    // (5y) and a single 10y tenor — mirrors a 1-element basket. AdjustNone
    // (no Kahale, no SABR, no Custom) keeps the smile = AtmSmileSection
    // wrapping a FlatSmileSection.
    {
        std::vector<Date> volStepDates;
        std::vector<Real> vols = {0.01};

        std::vector<Date> expiries;
        expiries.push_back(referenceDate + Period(5, Years));
        std::vector<Period> tenors;
        tenors.push_back(Period(10, Years));

        ext::shared_ptr<MarkovFunctional> mf(new MarkovFunctional(
            flatYts, 0.01, volStepDates, vols, flatSwaptionVts,
            expiries, tenors, swapIndexBase,
            MarkovFunctional::ModelSettings()
                .withYGridPoints(64)
                .withYStdDevs(7.0)
                .withGaussHermitePoints(32)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional::ModelSettings::AdjustNone)));

        // Trigger calibration via numeraireTime() access path.
        out.addCase("swaption_numeraire_time", json{},
            json{{"value", mf->numeraireTime()}});

        // Post-init sigma readback (the basket has only 1 vol → only 1 sigma).
        const Array& sigma = mf->volatility();
        out.addCase("swaption_sigma_size", json{},
            json{{"value", static_cast<long>(sigma.size())}});
        for (Size i = 0; i < sigma.size(); ++i) {
            char nm[40]; std::snprintf(nm, sizeof nm, "swaption_sigma_%02zu", i);
            out.addCase(nm, json{{"i", static_cast<long>(i)}},
                json{{"value", sigma[i]}});
        }

        // Numeraire fingerprint — exercises calibration tabulation.
        // (t, y) grid; values are normalized post-discount-factor numbers.
        int idx = 0;
        Real ts[] = {0.0, 0.5, 1.0, 2.0, 3.0, 4.0};
        Real ys[] = {-2.0, -1.0, 0.0, 1.0, 2.0};
        for (Real t : ts) {
            for (Real y : ys) {
                Real n = mf->numeraire(t, y);
                char nm[40]; std::snprintf(nm, sizeof nm, "swaption_num_%03d", idx++);
                out.addCase(nm,
                    json{{"t", t}, {"y", y}},
                    json{{"value", n}});
            }
        }

        // Zerobond fingerprints for (T, t, y) triples — broader coverage.
        int zidx = 0;
        Real Ts[] = {1.0, 2.0, 5.0, 10.0};
        Real ts2[] = {0.0, 0.5, 1.0};
        Real ys2[] = {-1.0, 0.0, 1.0};
        for (Real t : ts2) {
            for (Real T : Ts) {
                if (T <= t) continue;
                for (Real y : ys2) {
                    Real p = mf->zerobond(T, t, y);
                    char nm[40]; std::snprintf(nm, sizeof nm, "swaption_zb_%03d", zidx++);
                    out.addCase(nm,
                        json{{"t", t}, {"T", T}, {"y", y}},
                        json{{"value", p}});
                }
            }
        }

        // Model outputs zerorate fit (for each calibration expiry).
        const MarkovFunctional::ModelOutputs &mo = mf->modelOutputs();
        for (Size i = 0; i < mo.expiries_.size(); ++i) {
            char nm1[48]; std::snprintf(nm1, sizeof nm1, "swaption_mkt_zr_%02zu", i);
            out.addCase(nm1, json{{"i", static_cast<long>(i)}},
                json{{"value", mo.marketZerorate_[i]}});
            char nm2[48]; std::snprintf(nm2, sizeof nm2, "swaption_mdl_zr_%02zu", i);
            out.addCase(nm2, json{{"i", static_cast<long>(i)}},
                json{{"value", mo.modelZerorate_[i]}});
        }
    }

    // ── Caplet-calibrated MarkovFunctional, two expiries, AdjustNone.
    {
        std::vector<Date> volStepDates;
        std::vector<Real> vols = {0.01};

        std::vector<Date> expiries;
        expiries.push_back(referenceDate + Period(2, Years));
        // Single-expiry to keep volatilities sized 1.

        ext::shared_ptr<MarkovFunctional> mf(new MarkovFunctional(
            flatYts, 0.01, volStepDates, vols, flatOptionletVts,
            expiries, iborIndex,
            MarkovFunctional::ModelSettings()
                .withYGridPoints(64)
                .withYStdDevs(7.0)
                .withGaussHermitePoints(32)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional::ModelSettings::AdjustNone)));

        out.addCase("caplet_numeraire_time", json{},
            json{{"value", mf->numeraireTime()}});

        const Array& sigma = mf->volatility();
        out.addCase("caplet_sigma_size", json{},
            json{{"value", static_cast<long>(sigma.size())}});
        for (Size i = 0; i < sigma.size(); ++i) {
            char nm[40]; std::snprintf(nm, sizeof nm, "caplet_sigma_%02zu", i);
            out.addCase(nm, json{{"i", static_cast<long>(i)}},
                json{{"value", sigma[i]}});
        }

        // Numeraire and zerobond fingerprints.
        int idx = 0;
        Real ts[] = {0.0, 0.5, 1.0, 1.5};
        Real ys[] = {-1.0, 0.0, 1.0};
        for (Real t : ts) {
            for (Real y : ys) {
                Real n = mf->numeraire(t, y);
                char nm[40]; std::snprintf(nm, sizeof nm, "caplet_num_%03d", idx++);
                out.addCase(nm,
                    json{{"t", t}, {"y", y}},
                    json{{"value", n}});
            }
        }

        int zidx = 0;
        Real Ts[] = {1.0, 2.0, 3.0};
        Real ts2[] = {0.0, 0.5};
        Real ys2[] = {-1.0, 0.0, 1.0};
        for (Real t : ts2) {
            for (Real T : Ts) {
                if (T <= t) continue;
                for (Real y : ys2) {
                    Real p = mf->zerobond(T, t, y);
                    char nm[40]; std::snprintf(nm, sizeof nm, "caplet_zb_%03d", zidx++);
                    out.addCase(nm,
                        json{{"t", t}, {"T", T}, {"y", y}},
                        json{{"value", p}});
                }
            }
        }
    }

    out.write();
    return 0;
}
