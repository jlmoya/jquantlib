// migration-harness/cpp/probes/pricingengines/basket/deng_li_zhou_basket_engine_probe.cpp
//
// Phase 5e.5b-CFC-d-104 — emit reference values for DengLiZhouBasketEngine
// (Java port: org.jquantlib.pricingengines.basket.DengLiZhouBasketEngine).
//
// Two cases mirror the C++ test-suite test_dengLiZhou tests verbatim, plus
// emit the Deng-Li-Zhou analytic value to use as a PDE-free reference for
// the testDengLiZhouVsPDE Java test (Phase 5k.5b PDE engine is not yet
// ported; we cross-validate against the C++ analytic value, not the PDE).

#include <ql/version.hpp>
#include <ql/settings.hpp>
#include <ql/exercise.hpp>
#include <ql/instruments/basketoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/math/matrix.hpp>
#include <ql/pricingengines/basket/denglizhoubasketengine.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/period.hpp>

#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<YieldTermStructure> flatRate(const Date& today, Rate r, const DayCounter& dc) {
    return ext::make_shared<FlatForward>(today, r, dc);
}

ext::shared_ptr<BlackVolTermStructure> flatVol(const Date& today, Volatility v, const DayCounter& dc) {
    return ext::make_shared<BlackConstantVol>(today, NullCalendar(), v, dc);
}

void emitVsPDE(ReferenceWriter& out) {
    // Mirrors testDengLiZhouVsPDE in C++ test-suite/basketoption.cpp.
    const DayCounter dc = Actual365Fixed();
    const Date today(25, March, 2024);
    Settings::instance().evaluationDate() = today;
    const Date maturity = today + Period(6, Months);

    const std::vector<Real> underlyings({50, 11, 55, 200});
    const std::vector<Real> volatilities({0.2, 0.6, 0.4, 0.3});
    const std::vector<Real> q({0.075, 0.05, 0.08, 0.04});
    const Rate r = 0.05;

    const auto rTS = Handle<YieldTermStructure>(flatRate(today, r, dc));
    const auto exercise = ext::make_shared<EuropeanExercise>(maturity);

    std::vector<ext::shared_ptr<GeneralizedBlackScholesProcess> > processes;
    processes.reserve(4);
    for (Size d=0; d < 4; ++d)
        processes.push_back(
            ext::make_shared<BlackScholesMertonProcess>(
                Handle<Quote>(ext::make_shared<SimpleQuote>(underlyings[d])),
                Handle<YieldTermStructure>(flatRate(today, q[d], dc)), rTS,
                Handle<BlackVolTermStructure>(flatVol(today, volatilities[d], dc))
            )
        );

    Matrix rho(4, 4);
    for (Size i=0; i < 4; ++i)
        for (Size j=i; j < 4; ++j)
            rho[i][j] = rho[j][i] =
                std::exp(-0.5*std::abs(Real(i)-Real(j)) - ((i!=j) ? 0.02*(i+j): 0.0));

    const Real strike = 5.0;

    BasketOption option(
        ext::make_shared<AverageBasketPayoff>(
            ext::make_shared<PlainVanillaPayoff>(Option::Put, strike),
            Array({-1.0, -5.0, -2.0, 1.0})
        ),
        exercise
    );

    option.setPricingEngine(ext::make_shared<DengLiZhouBasketEngine>(processes, rho));
    const Real npv = option.NPV();

    out.addCase("vsPDE",
        json{
            {"today", "2024-03-25"},
            {"maturity_period_months", 6},
            {"day_counter", "Actual365Fixed"},
            {"underlyings", underlyings},
            {"volatilities", volatilities},
            {"q", q},
            {"r", r},
            {"weights", json::array({-1.0, -5.0, -2.0, 1.0})},
            {"strike", strike},
            {"option_type", "Put"}
        },
        json{ {"npv", npv} }
    );
}

void emitNegativeStrike(ReferenceWriter& out) {
    // Mirrors testDengLiZhouWithNegativeStrike in C++ test-suite/basketoption.cpp.
    const DayCounter dc = Actual365Fixed();
    const Date today(27, May, 2024);
    Settings::instance().evaluationDate() = today;
    const Date maturity = today + Period(6, Months);

    const std::vector<Real> underlyings({220.0, 105.0, 45.0, 1e-12});
    const std::vector<Real> volatilities({0.4, 0.25, 0.3, 0.25});
    const std::vector<Real> q({0.04, 0.075, 0.05, 0.1});
    const Rate r = 0.03;

    const auto rTS = Handle<YieldTermStructure>(flatRate(today, r, dc));
    const auto exercise = ext::make_shared<EuropeanExercise>(maturity);

    std::vector<ext::shared_ptr<GeneralizedBlackScholesProcess> > processes;
    processes.reserve(4);
    for (Size d=0; d < 4; ++d)
        processes.push_back(
            ext::make_shared<BlackScholesMertonProcess>(
                Handle<Quote>(ext::make_shared<SimpleQuote>(underlyings[d])),
                Handle<YieldTermStructure>(flatRate(today, q[d], dc)), rTS,
                Handle<BlackVolTermStructure>(flatVol(today, volatilities[d], dc))
            )
        );

    Matrix rho(4, 4, 0.0);
    rho[0][1] = rho[1][0] = 0.8;
    rho[0][2] = rho[2][0] = -0.2;
    rho[1][2] = rho[2][1] = 0.3;
    rho[0][0] = rho[1][1] = rho[2][2] = rho[3][3] = 1.0;
    rho[1][3] = rho[3][1] = 0.3;

    const Real strike = -2.0;

    BasketOption option(
        ext::make_shared<AverageBasketPayoff>(
            ext::make_shared<PlainVanillaPayoff>(Option::Call, strike),
            Array({0.5, -2.0, 2.0, -0.75})
        ),
        exercise
    );

    option.setPricingEngine(
        ext::make_shared<DengLiZhouBasketEngine>(processes, rho));
    const Real npv = option.NPV();

    out.addCase("negativeStrike",
        json{
            {"today", "2024-05-27"},
            {"maturity_period_months", 6},
            {"day_counter", "Actual365Fixed"},
            {"underlyings", underlyings},
            {"volatilities", volatilities},
            {"q", q},
            {"r", r},
            {"weights", json::array({0.5, -2.0, 2.0, -0.75})},
            {"strike", strike},
            {"option_type", "Call"}
        },
        json{ {"npv", npv} }
    );
}

} // namespace

int main() {
    ReferenceWriter out(
        "pricingengines/basket/deng_li_zhou_basket_engine",
        QL_VERSION,
        "deng_li_zhou_basket_engine_probe.cpp (Phase 5e.5b-CFC-d-104)");

    emitVsPDE(out);
    emitNegativeStrike(out);

    out.write();
    return 0;
}
