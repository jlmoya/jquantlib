// migration-harness/cpp/probes/experimental/volatility/dumas_parametric_vol_surface_probe.cpp
// Reference values for DumasParametricVolSurface (Java port of the inline
// reference helper from v1.42.1 test-suite/riskneutraldensitycalculator.cpp).
//
// QuantLib does not ship a public DumasParametricVolSurface; the formula
// lives only inside the test suite. This probe pins reference values for
// the formula
//   blackVol(t, K) = b1 + b2*mn + b3*mn^2 + b4*t + b5*mn*t,
//   mn = ln(F/K)/sqrt(t)
// using the parameter set from the testLocalVolatilityRND case.
//
// References were initially produced via /tmp/dumas_compute.cpp (no
// QuantLib dependency — purely the formula above with std::math) and
// pinned in references/experimental/volatility/dumas_parametric_vol_surface.json.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/voltermstructures.hpp>
#include "../../common.hpp"

// Inline copy of the C++ reference test class so the probe doesn't depend
// on test-suite headers at link time.
#include <cmath>
#include <utility>
namespace {
using namespace QuantLib;
class DumasParametricVolSurface : public BlackVolatilityTermStructure {
public:
    DumasParametricVolSurface(Real b1, Real b2, Real b3, Real b4, Real b5,
                              ext::shared_ptr<Quote> spot,
                              const ext::shared_ptr<YieldTermStructure>& rTS,
                              ext::shared_ptr<YieldTermStructure> qTS)
    : BlackVolatilityTermStructure(0, NullCalendar(), Following,
                                   rTS->dayCounter()),
      b1_(b1), b2_(b2), b3_(b3), b4_(b4), b5_(b5),
      spot_(std::move(spot)), rTS_(rTS), qTS_(std::move(qTS)) {}
    Date maxDate() const override { return Date::maxDate(); }
    Rate minStrike() const override { return 0.0; }
    Rate maxStrike() const override { return QL_MAX_REAL; }
protected:
    Volatility blackVolImpl(Time t, Real strike) const override {
        QL_REQUIRE(t >= 0.0, "t must be >= 0");
        if (t < QL_EPSILON) return b1_;
        const Real fwd = spot_->value() * qTS_->discount(t) / rTS_->discount(t);
        const Real mn  = std::log(fwd/strike) / std::sqrt(t);
        return b1_ + b2_*mn + b3_*mn*mn + b4_*t + b5_*mn*t;
    }
private:
    const Real b1_, b2_, b3_, b4_, b5_;
    const ext::shared_ptr<Quote> spot_;
    const ext::shared_ptr<YieldTermStructure> rTS_;
    const ext::shared_ptr<YieldTermStructure> qTS_;
};
}

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("experimental/volatility/dumas_parametric_vol_surface",
                        QL_VERSION,
                        "dumas_parametric_vol_surface_probe");

    const Date today(15, January, 2026);
    const DayCounter dc = Actual365Fixed();

    auto spot = ext::make_shared<SimpleQuote>(100.0);
    auto rTS  = ext::shared_ptr<YieldTermStructure>(
                    new FlatForward(today, 0.015, dc));
    auto qTS  = ext::shared_ptr<YieldTermStructure>(
                    new FlatForward(today, 0.025, dc));

    // Same params as testLocalVolatilityRND.
    DumasParametricVolSurface s(0.25, 0.03, 0.005, -0.02, -0.005,
                                spot, rTS, qTS);
    DumasParametricVolSurface flat(0.30, 0.0, 0.0, 0.0, 0.0,
                                   spot, rTS, qTS);

    struct Spec { double t; double K; const char* name; bool flat; };
    const Spec specs[] = {
        {0.0,        100.0, "atm_t0",      false},
        {7.0/365.0,   50.0, "K50_1w",      false},
        {7.0/365.0,   95.0, "K95_1w",      false},
        {7.0/365.0,  100.0, "atm_1w",      false},
        {7.0/365.0,  105.0, "K105_1w",     false},
        {7.0/365.0,  200.0, "K200_1w",     false},
        {1.0/12.0,   100.0, "atm_1m",      false},
        {0.25,       100.0, "atm_3m",      false},
        {0.5,        100.0, "atm_6m",      false},
        {0.5,         80.0, "K80_6m",      false},
        {0.5,        120.0, "K120_6m",     false},
        {1.0,        100.0, "atm_1y",      false},
        {1.0,         50.0, "K50_1y",      false},
        {1.0,        150.0, "K150_1y",     false},
        {1.5,        100.0, "atm_18m",     false},
        {2.0,        100.0, "atm_2y",      false},
        {3.0,        100.0, "atm_3y",      false},
        {3.0,        400.0, "K400_3y",     false},
        {1.0,        100.0, "flat_atm_1y", true },
    };
    for (const auto& sp : specs) {
        const double v = sp.flat ? flat.blackVol(sp.t, sp.K, true)
                                 : s.blackVol(sp.t, sp.K, true);
        out.addCase(sp.name,
            json{ {"t", sp.t}, {"K", sp.K}, {"flat", sp.flat} },
            json{ {"blackVol", v} });
    }

    out.write();
    return 0;
}
