// migration-harness/cpp/probes/indexes/v143_indexes_probe.cpp
//
// Reference values for the three interest-rate indexes introduced in C++
// QuantLib v1.43:
//
//   * Nibor   — NOK-NIBOR, an IborIndex (tenor-based, 2 fixing days)
//   * Shir    — Shekel Overnight Interest Rate, an OvernightIndex
//   * Zaronia — South African Rand Overnight Index Average, an OvernightIndex
//
// Each index is a thin constructor wrapper, so what actually needs pinning is
// the exact wiring: name, fixing days, currency, fixing calendar, day counter,
// business-day convention and end-of-month flag. Those are cheap to get subtly
// wrong in a port and invisible until a fixing date or accrual is off, so they
// are captured here explicitly.
//
// Date-arithmetic behaviour is pinned too (valueDate / maturityDate for a
// sample fixing date), since that is where a wrong calendar or convention
// actually shows up.

#include <ql/version.hpp>

#include <ql/indexes/ibor/nibor.hpp>
#include <ql/indexes/ibor/shir.hpp>
#include <ql/indexes/ibor/zaronia.hpp>
#include <ql/time/period.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json describe(const InterestRateIndex& idx) {
    return json{
        {"name", idx.name()},
        {"fixingDays", static_cast<int>(idx.fixingDays())},
        {"currencyCode", idx.currency().code()},
        {"fixingCalendar", idx.fixingCalendar().name()},
        {"dayCounter", idx.dayCounter().name()},
    };
}

json describeIbor(const IborIndex& idx) {
    json j = describe(idx);
    j["businessDayConvention"] = static_cast<int>(idx.businessDayConvention());
    j["endOfMonth"] = idx.endOfMonth();
    j["tenor"] = idx.tenor().length();
    j["tenorUnits"] = static_cast<int>(idx.tenor().units());
    return j;
}

// A fixing date deliberately chosen mid-week so the value/maturity roll is
// driven by the index's own calendar and convention rather than a weekend.
const Date kFixing(15, June, 2026);

json dates(const IborIndex& idx) {
    const Date value = idx.valueDate(kFixing);
    return json{
        {"fixingSerial", kFixing.serialNumber()},
        {"valueDateSerial", value.serialNumber()},
        {"maturityDateSerial", idx.maturityDate(value).serialNumber()},
    };
}

} // namespace

int main() {
    ReferenceWriter out("indexes/v143_indexes", QL_VERSION, "v143_indexes_probe");

    const Nibor nibor3m(Period(3, Months));
    out.addCase("nibor_3m", json{{"tenor", "3M"}}, describeIbor(nibor3m));
    out.addCase("nibor_3m_dates", json{{"fixingDate", "2026-06-15"}}, dates(nibor3m));

    const Nibor nibor6m(Period(6, Months));
    out.addCase("nibor_6m", json{{"tenor", "6M"}}, describeIbor(nibor6m));

    const Shir shir;
    out.addCase("shir", json{}, describeIbor(shir));
    out.addCase("shir_dates", json{{"fixingDate", "2026-06-15"}}, dates(shir));

    const Zaronia zaronia;
    out.addCase("zaronia", json{}, describeIbor(zaronia));
    out.addCase("zaronia_dates", json{{"fixingDate", "2026-06-15"}}, dates(zaronia));

    out.write();
    return 0;
}
