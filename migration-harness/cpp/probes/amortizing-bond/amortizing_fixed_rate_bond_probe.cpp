// migration-harness/cpp/probes/amortizing-bond/amortizing_fixed_rate_bond_probe.cpp
// Reference values for AmortizingFixedRateBond against QuantLib v1.42.1.
// Phase 5d.5-Bonds.
//
// Reproduces the testAmortizingFixedRateBond Excel-derived totals (one
// fixed PMT per coupon-rate scenario) and emits per-cashflow coupon and
// principal amounts for cross-validation. Each rate uses sinkingSchedule
// (30Y monthly) and sinkingNotionals(rate, 100.0).

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/instruments/bonds/amortizingfixedratebond.hpp>
#include <ql/cashflows/fixedratecoupon.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/settings.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("instruments/amortizing_fixed_rate_bond",
                        QL_VERSION,
                        "amortizing_fixed_rate_bond_probe");

    Real rates[] = {0.0, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07,
                    0.08, 0.09, 0.10, 0.11, 0.12};
    Real expectedPmt[] = {
        0.277777778, 0.321639520, 0.369619473, 0.421604034,
        0.477415295, 0.536821623, 0.599550525, 0.665302495,
        0.733764574, 0.804622617, 0.877571570, 0.952323396,
        1.028612597
    };
    Frequency freq = Monthly;

    Date refDate = Settings::instance().evaluationDate();

    for (Size i = 0; i < std::size(rates); ++i) {
        Schedule schedule = sinkingSchedule(refDate, Period(30, Years),
                                            freq, NullCalendar());
        std::vector<Real> notionals = sinkingNotionals(
            Period(30, Years), freq, rates[i], 100.0);
        AmortizingFixedRateBond bond(0, notionals, schedule, {rates[i]},
                                     ActualActual(ActualActual::ISMA));

        const Leg& cfs = bond.cashflows();
        // Pair-by-pair (coupon, principal) totals.
        Size nPairs = cfs.size() / 2;
        std::vector<Real> coupons; coupons.reserve(nPairs);
        std::vector<Real> principals; principals.reserve(nPairs);
        for (Size k = 0; k < nPairs; ++k) {
            coupons.push_back(cfs[2*k]->amount());
            principals.push_back(cfs[2*k+1]->amount());
        }

        json inp{
            {"rate", rates[i]},
            {"frequency", "Monthly"},
            {"tenor_years", 30},
            {"calendar", "NullCalendar"},
            {"dayCounter", "ActualActual.ISMA"},
            {"settlementDays", 0},
            {"initialNotional", 100.0}
        };
        json exp{
            {"expectedPmt", expectedPmt[i]},
            {"nPairs", nPairs},
            {"firstFiveCoupons", json::array({
                coupons[0], coupons[1], coupons[2],
                coupons[3], coupons[4]
            })},
            {"firstFivePrincipals", json::array({
                principals[0], principals[1], principals[2],
                principals[3], principals[4]
            })},
            {"firstFiveNotionals", json::array({
                notionals[0], notionals[1], notionals[2],
                notionals[3], notionals[4]
            })}
        };

        char nm[64]; std::snprintf(nm, sizeof(nm), "rate_%g", rates[i]);
        out.addCase(nm, inp, exp);
    }

    out.write();
    return 0;
}
