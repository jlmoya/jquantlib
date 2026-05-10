// migration-harness/cpp/probes/pricingengines/bachelier-impl/bachelier_implied_vol_probe.cpp
//
// Phase 5g.5b WI-1: emit C++ v1.42.1 reference values for
// bachelierBlackFormulaImpliedVol — round-trip via bachelierBlackFormula
// followed by the inverse (Choi explicit + Jaeckel inverse-PhiTilde paths).
//
// Java port BlackFormula.bachelierBlackFormulaImpliedVol must match within
// LOOSE tolerance (1e-6 rel) since both engines use Brent-style or
// rational-approximation iterations; ATM closed form is bit-exact.

#include <ql/version.hpp>
#include <ql/pricingengines/blackformula.hpp>
#include <ql/instruments/payoffs.hpp>

#include "../../common.hpp"

#include <cmath>
#include <vector>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct Case {
    std::string name;
    Option::Type type;
    Real strike;
    Real forward;
    Real tte;
    Real bachelierVol;   // input absolute vol
    Real discount;
};

void runCase(ReferenceWriter& out, const Case& c) {
    Real stddev = c.bachelierVol * std::sqrt(c.tte);
    Real price = bachelierBlackFormula(c.type, c.strike, c.forward,
                                       stddev, c.discount);
    Real impliedVolJaeckel = bachelierBlackFormulaImpliedVol(
        c.type, c.strike, c.forward, c.tte, price, c.discount);
    Real impliedVolChoi = bachelierBlackFormulaImpliedVolChoi(
        c.type, c.strike, c.forward, c.tte, price, c.discount);

    json inputs = {
        {"option_type",   (c.type == Option::Call ? "Call" : "Put")},
        {"strike",        c.strike},
        {"forward",       c.forward},
        {"tte",           c.tte},
        {"bachelier_vol", c.bachelierVol},
        {"discount",      c.discount},
        {"price",         price}
    };
    json expected = {
        {"implied_vol_jaeckel", impliedVolJaeckel},
        {"implied_vol_choi",    impliedVolChoi}
    };
    out.addCase(c.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("pricingengines/bachelier-impl/bachelier_implied_vol",
                        QL_VERSION, "bachelier_implied_vol_probe");

    std::vector<Case> cases = {
        // ATM (closed-form path): strike == forward
        {"atm_call_1y",  Option::Call, 0.05, 0.05, 1.0, 0.01,  1.0},
        {"atm_put_1y",   Option::Put,  0.05, 0.05, 1.0, 0.01,  1.0},
        {"atm_call_5y",  Option::Call, 0.05, 0.05, 5.0, 0.01,  0.95},
        // OTM call (strike > forward) — solver path
        {"otm_call_1y",  Option::Call, 0.06, 0.05, 1.0, 0.01,  1.0},
        {"otm_call_2y",  Option::Call, 0.07, 0.05, 2.0, 0.012, 0.98},
        // OTM put (strike < forward)
        {"otm_put_1y",   Option::Put,  0.04, 0.05, 1.0, 0.01,  1.0},
        {"otm_put_3y",   Option::Put,  0.03, 0.05, 3.0, 0.015, 0.97},
        // ITM call (strike < forward)
        {"itm_call_1y",  Option::Call, 0.04, 0.05, 1.0, 0.01,  1.0},
        {"itm_call_5y",  Option::Call, 0.03, 0.05, 5.0, 0.012, 0.95},
        // ITM put (strike > forward)
        {"itm_put_1y",   Option::Put,  0.06, 0.05, 1.0, 0.01,  1.0},
        // Higher vols / longer expiries
        {"high_vol_call", Option::Call, 0.06, 0.05, 1.0, 0.05, 1.0},
        {"low_vol_call",  Option::Call, 0.06, 0.05, 1.0, 0.001, 1.0},
        // Negative-rate territory (Bachelier is defined for negatives)
        {"neg_fwd_call",  Option::Call, 0.005, -0.005, 1.0, 0.01, 1.0},
        {"neg_fwd_put",   Option::Put,  -0.01, -0.005, 1.0, 0.01, 1.0},
    };

    for (const auto& c : cases) {
        runCase(out, c);
    }

    out.write();
    return 0;
}
