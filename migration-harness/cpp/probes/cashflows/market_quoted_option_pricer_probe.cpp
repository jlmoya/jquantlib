// migration-harness/cpp/probes/cashflows/market_quoted_option_pricer_probe.cpp
//
// Reference values for MarketQuotedOptionPricer's deflator-multiplied
// pricing formula against QuantLib v1.42.1. Phase 5e.5.
//
// MarketQuotedOptionPricer::operator()(strike, type, deflator) is, after
// the smile_->variance(strike) lookup is supplied by the caller, simply
//
//   ShiftedLognormal: deflator * blackFormula(type, strike, fwd, sqrt(variance))
//   Normal:           deflator * bachelierBlackFormula(type, strike, fwd, sqrt(variance))
//
// The probe drives the formulas directly. Unit-coverage of the smile
// section glue (constructor + variance lookup) lives in the Java unit
// test against an in-test ConstantSwaptionVolatility-like fixture; this
// reference covers the closed-form arithmetic exclusively.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/pricingengines/blackformula.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

const char* optionTypeName(Option::Type t) {
    switch (t) {
        case Option::Call: return "Call";
        case Option::Put:  return "Put";
        default: return "?";
    }
}

struct PricerCase {
    const char* name;
    Option::Type type;
    Real strike;
    Real forward;
    Real variance;
    Real deflator;
    bool shifted;          // true => blackFormula, false => bachelier
};

} // namespace

int main() {
    ReferenceWriter out("cashflows/market_quoted_option_pricer",
                        QL_VERSION, "market_quoted_option_pricer_probe");

    std::vector<PricerCase> cases = {
        // Annuity-deflated (CMS context).
        {"call_atm_lognormal_unit",    Option::Call, 0.025, 0.025, 0.10*0.10, 1.00, true},
        {"call_otm_lognormal_unit",    Option::Call, 0.030, 0.025, 0.10*0.10, 1.00, true},
        {"put_atm_lognormal_unit",     Option::Put,  0.025, 0.025, 0.10*0.10, 1.00, true},
        {"call_atm_lognormal_annuity", Option::Call, 0.025, 0.025, 0.10*0.10, 4.50, true},
        {"call_atm_normal_unit",       Option::Call, 0.025, 0.025, 0.005*0.005, 1.00, false},
        {"call_atm_normal_annuity",    Option::Call, 0.025, 0.025, 0.005*0.005, 4.50, false},
        {"put_otm_normal_annuity",     Option::Put,  0.020, 0.025, 0.005*0.005, 4.50, false},
        {"call_neg_strike_normal",     Option::Call,-0.005, 0.005, 0.005*0.005, 4.50, false},
        // Variance scaling sanity: variance=0 -> intrinsic only.
        {"call_atm_variance_zero",     Option::Call, 0.020, 0.025, 0.0,         1.00, true},
    };

    for (const auto& c : cases) {
        const Real stdDev = std::sqrt(c.variance);
        Real px;
        if (c.shifted) {
            px = c.deflator * blackFormula(c.type, c.strike, c.forward, stdDev);
        } else {
            px = c.deflator * bachelierBlackFormula(c.type, c.strike, c.forward, stdDev);
        }
        json inp{
            {"optionType", optionTypeName(c.type)},
            {"strike",     c.strike},
            {"forward",    c.forward},
            {"variance",   c.variance},
            {"deflator",   c.deflator},
            {"shifted",    c.shifted}
        };
        json exp{ {"price", px} };
        out.addCase(c.name, inp, exp);
    }

    out.write();
    return 0;
}
