// migration-harness/cpp/probes/experimental/inflation/generic_indexes_probe.cpp
// Reference values for GenericRegion, GenericCPI, YYGenericCPI
// (ql/experimental/inflation/genericindexes.hpp).
//
// Captures: region name, region code, index family name, index name.
// These are string metadata — tested at TIGHT (bit-exact string match).

#include <ql/version.hpp>
#include <ql/experimental/inflation/genericindexes.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/time/frequency.hpp>
#include <ql/time/period.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("experimental/inflation/generic_indexes",
                        QL_VERSION,
                        "generic_indexes_probe");

    // GenericRegion metadata
    {
        GenericRegion region;
        json inputs = {{"class", "GenericRegion"}};
        json expected = {
            {"name", region.name()},
            {"code", region.code()}
        };
        out.addCase("generic_region_metadata", inputs, expected);
    }

    // GenericCPI metadata (frequency Monthly, not revised, lag 3m, EUR)
    {
        GenericCPI cpi(Monthly, false, 3*Months, EURCurrency());
        json inputs = {
            {"class", "GenericCPI"},
            {"frequency", "Monthly"},
            {"revised", false},
            {"lag_months", 3},
            {"currency", "EUR"}
        };
        json expected = {
            {"familyName", cpi.familyName()},
            {"name", cpi.name()}
        };
        out.addCase("generic_cpi_metadata", inputs, expected);
    }

    // YYGenericCPI metadata (frequency Monthly, not revised, lag 3m, EUR)
    {
        YYGenericCPI yycpi(Monthly, false, 3*Months, EURCurrency());
        json inputs = {
            {"class", "YYGenericCPI"},
            {"frequency", "Monthly"},
            {"revised", false},
            {"lag_months", 3},
            {"currency", "EUR"}
        };
        json expected = {
            {"familyName", yycpi.familyName()},
            {"name", yycpi.name()}
        };
        out.addCase("yy_generic_cpi_metadata", inputs, expected);
    }

    out.write();
    return 0;
}
