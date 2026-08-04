// migration-harness/cpp/probes/experimental/varianceoption/integral_heston_variance_option_engine_probe.cpp
//
// Reference values for IntegralHestonVarianceOptionEngine
// (ql/experimental/varianceoption/integralhestonvarianceoptionengine.{hpp,cpp}).
//
// v1.43 changed this engine in two ways that matter to a port:
//
//   1. It now REQUIRES the process's dividend handle to be empty
//      (`QL_REQUIRE(process_->dividendYield().empty(), ...)`). The analytic
//      formula has no dividend term, so a supplied curve would be silently
//      ignored — refusing it is the point. A flat-ZERO curve is not accepted
//      either; the handle itself must be empty.
//   2. The internals were retyped from `double` to `Real` throughout.
//
// The market below is upstream's own (test-suite/varianceoption.cpp,
// testIntegralHeston): spot 1.0, a flat ZERO risk-free curve, an empty
// dividend handle, Actual360. That matters — the engine carries an internal
// consistency check on the imaginary part of its inverse transform, and that
// check trips outside the regime upstream exercises. Cases that trip it are
// pinned as `throws: true` rather than dropped, so the boundary is recorded
// instead of quietly avoided.

#include <ql/version.hpp>

#include <ql/experimental/varianceoption/integralhestonvarianceoptionengine.hpp>
#include <ql/experimental/varianceoption/varianceoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual360.hpp>

#include "../../common.hpp"

using namespace jqml_harness;
using namespace QuantLib;

namespace {

const Date kEval(15, June, 2026);

Handle<YieldTermStructure> flatZeroCurve(const DayCounter& dc) {
    return Handle<YieldTermStructure>(ext::make_shared<FlatForward>(kEval, 0.0, dc));
}

struct Scenario {
    const char* name;
    Real v0, kappa, theta, sigma, rho;
    Option::Type type;
    Real strike;
    Real timeToMaturity;
};

} // namespace

int main() {
    ReferenceWriter out("experimental/varianceoption/integral_heston_variance_option_engine",
                        QL_VERSION,
                        "integral_heston_variance_option_engine_probe");

    Settings::instance().evaluationDate() = kEval;

    const DayCounter dc = Actual360();
    const Handle<YieldTermStructure> rTS = flatZeroCurve(dc);
    const Handle<YieldTermStructure> qTS; // deliberately empty — v1.43 requires it
    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(1.0));
    const Real nominal = 1.0;

    // The first two are upstream's own cases, whose expected values (0.9104619
    // and 0.0466796) are published in test-suite/varianceoption.cpp; the rest
    // walk away from that regime to map where the engine still agrees.
    const std::vector<Scenario> scenarios = {
        {"upstream_call_v0_2.0_T_1.5_K_0.05", 2.0, 2.0, 0.01, 0.1, -0.5, Option::Call, 0.05, 1.5},
        {"upstream_put_v0_1.5_T_1.0_K_0.7", 1.5, 2.0, 0.01, 0.1, -0.5, Option::Put, 0.7, 1.0},
        {"call_v0_1.0_T_1.0_K_0.5", 1.0, 2.0, 0.01, 0.1, -0.5, Option::Call, 0.5, 1.0},
        {"call_v0_2.0_T_2.0_K_0.10", 2.0, 2.0, 0.01, 0.1, -0.5, Option::Call, 0.10, 2.0},
        {"call_v0_1.5_T_0.5_K_0.30", 1.5, 2.0, 0.01, 0.1, -0.5, Option::Call, 0.30, 0.5},
        {"put_v0_2.0_T_1.5_K_1.0", 2.0, 2.0, 0.01, 0.1, -0.5, Option::Put, 1.0, 1.5},
        {"call_rho_zero", 2.0, 2.0, 0.01, 0.1, 0.0, Option::Call, 0.05, 1.5},
        {"call_high_kappa", 2.0, 5.0, 0.01, 0.1, -0.5, Option::Call, 0.05, 1.5},
    };

    for (const auto& sc : scenarios) {
        const auto proc = ext::make_shared<HestonProcess>(rTS, qTS, s0, sc.v0, sc.kappa, sc.theta,
                                                          sc.sigma, sc.rho);
        const auto engine = ext::make_shared<IntegralHestonVarianceOptionEngine>(proc);

        const auto days = Integer(360 * sc.timeToMaturity);
        const Date exDate = kEval + days;
        const auto payoff = ext::make_shared<PlainVanillaPayoff>(sc.type, sc.strike);
        VarianceOption option(payoff, nominal, kEval, exDate);
        option.setPricingEngine(engine);

        json inputs = {
            {"v0", sc.v0},
            {"kappa", sc.kappa},
            {"theta", sc.theta},
            {"sigma", sc.sigma},
            {"rho", sc.rho},
            {"type", sc.type == Option::Call ? "Call" : "Put"},
            {"strike", sc.strike},
            {"timeToMaturity", sc.timeToMaturity},
            {"daysToMaturity", days},
            {"nominal", nominal},
            {"riskFreeRate", 0.0},
            {"dividendHandle", "empty"},
            {"dayCounter", "Actual360"},
            {"spot", 1.0},
        };

        json expected;
        try {
            expected = json{{"throws", false}, {"npv", option.NPV()}};
        } catch (const std::exception&) {
            expected = json{{"throws", true}};
        }
        out.addCase(sc.name, inputs, expected);
    }

    // The v1.43 guard itself. A flat-ZERO dividend curve is still rejected,
    // because the requirement is that the handle be empty — not that the yield
    // be zero. That distinction is easy to lose in a port, and losing it turns
    // a loud refusal into a silently ignored input.
    {
        const auto proc = ext::make_shared<HestonProcess>(rTS, flatZeroCurve(dc), s0, 2.0, 2.0,
                                                          0.01, 0.1, -0.5);
        const auto engine = ext::make_shared<IntegralHestonVarianceOptionEngine>(proc);
        const auto payoff = ext::make_shared<PlainVanillaPayoff>(Option::Call, 0.05);
        VarianceOption option(payoff, nominal, kEval, kEval + 540);
        option.setPricingEngine(engine);

        bool threw = false;
        try {
            option.NPV();
        } catch (const std::exception&) {
            threw = true;
        }
        out.addCase("rejects_non_empty_dividend_handle",
                    json{{"dividendHandle", "flat zero curve"},
                         {"note", "empty is required; a zero yield is not the same thing"}},
                    json{{"throws", threw}});
    }

    out.write();
    return 0;
}
