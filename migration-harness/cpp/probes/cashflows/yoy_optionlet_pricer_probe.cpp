// migration-harness/cpp/probes/cashflows/yoy_optionlet_pricer_probe.cpp
// Reference values for Black/UnitDisplaced/Bachelier YoY-inflation coupon
// optionlet pricers against QuantLib v1.42.1.
// Phase 2r Track C C.3.
//
// Each pricer's optionletPriceImp is a pure formula on
// (Option::Type, strike, forward, stdDev). We probe the formula directly
// (bypasses YoYOptionletVolatilitySurface) by computing the expected value
// via the underlying blackFormula / bachelierBlackFormula. This gives us
// reference values that don't depend on Track B's vol surface.
//
// Inputs: optionType, strike, forward, stdDev
// Expected: rate (which mirrors what optionletPriceImp would return on
//           the same arguments)

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/pricingengines/blackformula.hpp>
#include <ql/instruments/payoffs.hpp>

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
    Real stdDev;
};

} // namespace

int main() {
    ReferenceWriter out("cashflows/yoy_optionlet_pricer",
                        QL_VERSION, "yoy_optionlet_pricer_probe");

    std::vector<PricerCase> cases = {
        // (name, type, strike, forward, stdDev)
        {"call_atm",          Option::Call, 0.025, 0.025, 0.10},
        {"call_otm",          Option::Call, 0.030, 0.025, 0.10},
        {"call_itm",          Option::Call, 0.020, 0.025, 0.10},
        {"put_atm",           Option::Put,  0.025, 0.025, 0.10},
        {"put_otm",           Option::Put,  0.020, 0.025, 0.10},
        {"put_itm",           Option::Put,  0.030, 0.025, 0.10},
        {"call_low_vol",      Option::Call, 0.025, 0.025, 0.01},
        {"call_high_vol",     Option::Call, 0.025, 0.025, 0.50},
        {"call_zero_strike",  Option::Call, 0.001, 0.025, 0.10},
    };

    for (const auto& c : cases) {
        const Real black = blackFormula(c.type, c.strike, c.forward, c.stdDev);
        const Real udb = blackFormula(c.type, c.strike + 1.0, c.forward + 1.0, c.stdDev);
        const Real bach = bachelierBlackFormula(c.type, c.strike, c.forward, c.stdDev);

        json inp{
            {"optionType", optionTypeName(c.type)},
            {"strike", c.strike},
            {"forward", c.forward},
            {"stdDev", c.stdDev}
        };
        json exp{
            {"black", black},
            {"unitDisplacedBlack", udb},
            {"bachelier", bach}
        };
        out.addCase(c.name, inp, exp);
    }

    out.write();
    return 0;
}
