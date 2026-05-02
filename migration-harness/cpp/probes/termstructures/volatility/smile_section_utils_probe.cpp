// migration-harness/cpp/probes/termstructures/volatility/smile_section_utils_probe.cpp
// Reference values for SmileSectionUtils: moneyGrid, strikeGrid, callPrices,
// atmLevel, arbitragefreeRegion, arbitragefreeIndices.
// Phase 2j WI-4.0b (QuantLib v1.42.1)

#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/volatility/smilesectionutils.hpp>
#include <ql/termstructures/volatility/flatsmilesection.hpp>
#include <ql/termstructures/volatility/interpolatedsmilesection.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/date.hpp>

using namespace jqml_harness;
using namespace QuantLib;

static void emitArrayCase(ReferenceWriter& out, const std::string& name,
                          const json& inputs, const std::vector<Real>& v) {
    json arr = json::array();
    for (Real x : v) arr.push_back(x);
    out.addCase(name, inputs, json{{"values", arr}});
}

int main() {
    ReferenceWriter out("termstructures/volatility/smile_section_utils",
                        QL_VERSION, "smile_section_utils_probe");

    DayCounter dc = Actual365Fixed();

    // -----------------------------------------------------------------------
    // Case A: FlatSmileSection, ShiftedLognormal, default moneyness grid
    //   ATM=0.05, vol=0.20, exerciseTime=1.0, shift=0
    // -----------------------------------------------------------------------
    {
        Real atm_a = 0.05;
        FlatSmileSection sec_a(1.0, 0.20, dc, atm_a);
        SmileSectionUtils utils_a(sec_a, {}, Null<Real>(), false);

        auto [kL, kR] = utils_a.arbitragefreeRegion();
        auto [iL, iR] = utils_a.arbitragefreeIndices();

        out.addCase("flat_lognorm_default_atmLevel",
                    json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}, {"shift", 0.0}},
                    json{{"value", utils_a.atmLevel()}});

        out.addCase("flat_lognorm_default_af_region",
                    json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}},
                    json{{"kL", kL}, {"kR", kR}});

        out.addCase("flat_lognorm_default_af_indices",
                    json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});

        emitArrayCase(out, "flat_lognorm_default_moneyGrid",
                      json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}},
                      utils_a.moneyGrid());
        emitArrayCase(out, "flat_lognorm_default_strikeGrid",
                      json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}},
                      utils_a.strikeGrid());
        emitArrayCase(out, "flat_lognorm_default_callPrices",
                      json{{"atm", atm_a}, {"vol", 0.20}, {"T", 1.0}},
                      utils_a.callPrices());
    }

    // -----------------------------------------------------------------------
    // Case B: FlatSmileSection, custom moneyness grid
    //   ATM=0.03, vol=0.15, exerciseTime=0.5
    // -----------------------------------------------------------------------
    {
        Real atm_b = 0.03;
        FlatSmileSection sec_b(0.5, 0.15, dc, atm_b);
        std::vector<Real> customGrid = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        SmileSectionUtils utils_b(sec_b, customGrid, Null<Real>(), false);

        auto [kL, kR] = utils_b.arbitragefreeRegion();
        auto [iL, iR] = utils_b.arbitragefreeIndices();

        out.addCase("flat_lognorm_custom_atmLevel",
                    json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                    json{{"value", utils_b.atmLevel()}});
        out.addCase("flat_lognorm_custom_af_region",
                    json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                    json{{"kL", kL}, {"kR", kR}});
        out.addCase("flat_lognorm_custom_af_indices",
                    json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});
        emitArrayCase(out, "flat_lognorm_custom_moneyGrid",
                      json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                      utils_b.moneyGrid());
        emitArrayCase(out, "flat_lognorm_custom_strikeGrid",
                      json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                      utils_b.strikeGrid());
        emitArrayCase(out, "flat_lognorm_custom_callPrices",
                      json{{"atm", atm_b}, {"vol", 0.15}, {"T", 0.5}},
                      utils_b.callPrices());
    }

    // -----------------------------------------------------------------------
    // Case C: FlatSmileSection, explicit ATM override in constructor
    //   section ATM=Null, override atm=0.04
    // -----------------------------------------------------------------------
    {
        Real atm_c = 0.04;
        FlatSmileSection sec_c(1.0, 0.18, dc, atm_c);
        std::vector<Real> grid_c = {0.75, 1.0, 1.25, 1.5, 2.0, 3.0};
        SmileSectionUtils utils_c(sec_c, grid_c, 0.04, false);

        auto [kL, kR] = utils_c.arbitragefreeRegion();
        auto [iL, iR] = utils_c.arbitragefreeIndices();

        out.addCase("flat_lognorm_atm_override_atmLevel",
                    json{{"atm", atm_c}, {"vol", 0.18}, {"T", 1.0}},
                    json{{"value", utils_c.atmLevel()}});
        out.addCase("flat_lognorm_atm_override_af_indices",
                    json{{"atm", atm_c}, {"vol", 0.18}, {"T", 1.0}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});
        emitArrayCase(out, "flat_lognorm_atm_override_strikeGrid",
                      json{{"atm", atm_c}, {"vol", 0.18}, {"T", 1.0}},
                      utils_c.strikeGrid());
        emitArrayCase(out, "flat_lognorm_atm_override_callPrices",
                      json{{"atm", atm_c}, {"vol", 0.18}, {"T", 1.0}},
                      utils_c.callPrices());
    }

    // -----------------------------------------------------------------------
    // Case D: FlatSmileSection, Normal volatility, default moneyness grid
    //   ATM=0.02, vol=0.005 (normal vol 50bps), exerciseTime=1.0
    // -----------------------------------------------------------------------
    {
        Real atm_d = 0.02;
        FlatSmileSection sec_d(1.0, 0.005, dc, atm_d, Normal);
        SmileSectionUtils utils_d(sec_d, {}, Null<Real>(), false);

        auto [kL, kR] = utils_d.arbitragefreeRegion();
        auto [iL, iR] = utils_d.arbitragefreeIndices();

        out.addCase("flat_normal_default_atmLevel",
                    json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}, {"type", "Normal"}},
                    json{{"value", utils_d.atmLevel()}});
        out.addCase("flat_normal_default_af_region",
                    json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}},
                    json{{"kL", kL}, {"kR", kR}});
        out.addCase("flat_normal_default_af_indices",
                    json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});
        emitArrayCase(out, "flat_normal_default_moneyGrid",
                      json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}},
                      utils_d.moneyGrid());
        emitArrayCase(out, "flat_normal_default_strikeGrid",
                      json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}},
                      utils_d.strikeGrid());
        emitArrayCase(out, "flat_normal_default_callPrices",
                      json{{"atm", atm_d}, {"vol", 0.005}, {"T", 1.0}},
                      utils_d.callPrices());
    }

    // -----------------------------------------------------------------------
    // Case E: FlatSmileSection, ShiftedLognormal with shift=0.02
    //   ATM=0.02, vol=0.15, exerciseTime=1.0, shift=0.02
    // -----------------------------------------------------------------------
    {
        Real atm_e = 0.02;
        Real shift_e = 0.02;
        FlatSmileSection sec_e(1.0, 0.15, dc, atm_e, ShiftedLognormal, shift_e);
        SmileSectionUtils utils_e(sec_e, {}, Null<Real>(), false);

        auto [kL, kR] = utils_e.arbitragefreeRegion();
        auto [iL, iR] = utils_e.arbitragefreeIndices();

        out.addCase("flat_shiftedlognorm_atmLevel",
                    json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                    json{{"value", utils_e.atmLevel()}});
        out.addCase("flat_shiftedlognorm_af_region",
                    json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                    json{{"kL", kL}, {"kR", kR}});
        out.addCase("flat_shiftedlognorm_af_indices",
                    json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});
        emitArrayCase(out, "flat_shiftedlognorm_moneyGrid",
                      json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                      utils_e.moneyGrid());
        emitArrayCase(out, "flat_shiftedlognorm_strikeGrid",
                      json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                      utils_e.strikeGrid());
        emitArrayCase(out, "flat_shiftedlognorm_callPrices",
                      json{{"atm", atm_e}, {"vol", 0.15}, {"T", 1.0}, {"shift", shift_e}},
                      utils_e.callPrices());
    }

    // -----------------------------------------------------------------------
    // Case F: deleteArbitragePoints=true, still flat (no points removed)
    // -----------------------------------------------------------------------
    {
        Real atm_f = 0.05;
        FlatSmileSection sec_f(1.0, 0.20, dc, atm_f);
        std::vector<Real> grid_f = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        SmileSectionUtils utils_f(sec_f, grid_f, Null<Real>(), true);

        auto [kL, kR] = utils_f.arbitragefreeRegion();
        auto [iL, iR] = utils_f.arbitragefreeIndices();

        out.addCase("flat_deleteArb_af_region",
                    json{{"atm", atm_f}, {"vol", 0.20}, {"T", 1.0}},
                    json{{"kL", kL}, {"kR", kR}});
        out.addCase("flat_deleteArb_af_indices",
                    json{{"atm", atm_f}, {"vol", 0.20}, {"T", 1.0}},
                    json{{"iL", (int)iL}, {"iR", (int)iR}});
        emitArrayCase(out, "flat_deleteArb_strikeGrid",
                      json{{"atm", atm_f}, {"vol", 0.20}, {"T", 1.0}},
                      utils_f.strikeGrid());
        emitArrayCase(out, "flat_deleteArb_callPrices",
                      json{{"atm", atm_f}, {"vol", 0.20}, {"T", 1.0}},
                      utils_f.callPrices());
    }

    out.write();
    return 0;
}
