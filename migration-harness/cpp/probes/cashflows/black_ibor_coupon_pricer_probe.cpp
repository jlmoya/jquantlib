// migration-harness/cpp/probes/cashflows/black_ibor_coupon_pricer_probe.cpp
//
// Reference values for BlackIborCouponPricer ports of the pure-formula
// scalar paths (optionletRate, adjustedFixing) against QuantLib v1.42.1.
// Phase 5e.5.
//
// We avoid building a full coupon/index/curve machine by exercising the
// underlying primitives that BlackIborCouponPricer dispatches through:
//
//   * optionletRate (post-fixing branch)  -> max(a-b, 0) on call/put.
//   * optionletRate (pre-fixing branch)   -> blackFormula or
//                                            bachelierBlackFormula(...,1.0)
//                                            depending on volatility type.
//   * adjustedFixing (Black76 inArrears,
//     ShiftedLognormal vs Normal)         -> the analytic formula in
//                                            couponpricer.cpp lines 207-210.
//
// All cases are pure functions of (forward, strike, stdDev, displacement,
// shifted, optionType, tau, [variance]), so they can be reproduced exactly
// in Java without porting IborCoupon, IborIndex, OptionletVolatility, etc.

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

struct OptionletCase {
    const char* name;
    Option::Type type;
    Real strike;
    Real forward;
    Real stdDev;
    Real displacement;
    bool shifted;          // true => blackFormula(shift), false => bachelier
};

struct AdjustedCase {
    const char* name;
    Real fixing;
    Real variance;
    Real tau;              // spanningTimeIndexMaturity
    Real displacement;
    bool shifted;          // true => shifted-lognormal adj, false => normal adj
};

} // namespace

int main() {
    ReferenceWriter out("cashflows/black_ibor_coupon_pricer",
                        QL_VERSION, "black_ibor_coupon_pricer_probe");

    // ----- optionletRate cases (pre-fixing branch) -----
    // Mirror BlackIborCouponPricer::optionletRate (couponpricer.cpp 138-167).
    std::vector<OptionletCase> optCases = {
        {"call_atm_lognormal",   Option::Call, 0.025, 0.025, 0.10, 0.0,   true},
        {"call_otm_lognormal",   Option::Call, 0.030, 0.025, 0.10, 0.0,   true},
        {"put_atm_lognormal",    Option::Put,  0.025, 0.025, 0.10, 0.0,   true},
        {"call_otm_shifted_3pct",Option::Call, 0.030, 0.025, 0.10, 0.03,  true},
        {"put_itm_shifted_3pct", Option::Put,  0.030, 0.025, 0.10, 0.03,  true},
        {"call_atm_normal",      Option::Call, 0.025, 0.025, 0.0050, 0.0, false},
        {"call_otm_normal",      Option::Call, 0.030, 0.025, 0.0050, 0.0, false},
        {"put_atm_normal",       Option::Put,  0.025, 0.025, 0.0050, 0.0, false},
        {"call_neg_strike_norm", Option::Call,-0.005, 0.005, 0.0050, 0.0, false},
    };

    for (const auto& c : optCases) {
        Real rate;
        if (c.shifted) {
            rate = blackFormula(c.type, c.strike, c.forward,
                                c.stdDev, 1.0, c.displacement);
        } else {
            rate = bachelierBlackFormula(c.type, c.strike, c.forward,
                                         c.stdDev, 1.0);
        }
        json inp{
            {"optionType",   optionTypeName(c.type)},
            {"strike",       c.strike},
            {"forward",      c.forward},
            {"stdDev",       c.stdDev},
            {"displacement", c.displacement},
            {"shifted",      c.shifted}
        };
        json exp{ {"rate", rate} };
        out.addCase(c.name, inp, exp);
    }

    // ----- adjustedFixing cases (Black76, in-arrears, post-cutoff) -----
    // Mirror BlackIborCouponPricer::adjustedFixing analytic core
    // (couponpricer.cpp 207-210):
    //   adjustment = shifted ? (f+s)^2 * variance * tau / (1 + f*tau)
    //                        : variance * tau / (1 + f*tau)
    //   return fixing + adjustment
    std::vector<AdjustedCase> adjCases = {
        {"adj_lognormal_typical",   0.025, 0.10*0.10, 0.50, 0.0,   true},
        {"adj_lognormal_high_var",  0.040, 0.30*0.30, 1.00, 0.0,   true},
        {"adj_lognormal_low_fixing",0.005, 0.20*0.20, 0.25, 0.0,   true},
        {"adj_shifted_3pct",        0.025, 0.10*0.10, 0.50, 0.03,  true},
        {"adj_normal_typical",      0.025, 0.005*0.005, 0.50, 0.0, false},
        {"adj_normal_neg_fixing",  -0.002, 0.005*0.005, 0.50, 0.0, false},
        {"adj_normal_long_tau",     0.030, 0.005*0.005, 2.00, 0.0, false},
    };

    for (const auto& c : adjCases) {
        Real adjustment;
        if (c.shifted) {
            adjustment = (c.fixing + c.displacement)
                       * (c.fixing + c.displacement)
                       * c.variance * c.tau / (1.0 + c.fixing * c.tau);
        } else {
            adjustment = c.variance * c.tau / (1.0 + c.fixing * c.tau);
        }
        const Real adjusted = c.fixing + adjustment;
        json inp{
            {"fixing",       c.fixing},
            {"variance",     c.variance},
            {"tau",          c.tau},
            {"displacement", c.displacement},
            {"shifted",      c.shifted}
        };
        json exp{
            {"adjustment", adjustment},
            {"adjusted",   adjusted}
        };
        out.addCase(c.name, inp, exp);
    }

    out.write();
    return 0;
}
