// migration-harness/cpp/probes/experimental/barrieroption/vanna_volga_interpolation_probe.cpp
//
// Emits QuantLib::VannaVolgaInterpolation::operator()(k) directly, over a
// strike grid that spans and overshoots the three quoted strikes.
//
// WHY THIS EXISTS: C++ hides the arithmetic in
// detail::VannaVolgaInterpolationImpl (ql/experimental/barrieroption/
// vannavolgainterpolation.hpp:82), which JQuantLib folds into the single class
// org.jquantlib.experimental.barrieroption.VannaVolgaInterpolation. Before this
// probe the ONLY Java coverage of that arithmetic was indirect: barrier-option
// NPVs at 1e-4, several transformations downstream (smile vol -> Black price ->
// vanna/volga survival-weighted correction -> barrier price). A sign error in
// one Lagrange weight can hide under that much smoothing. Here the interpolated
// volatility itself is the assertion.
//
// The grid deliberately includes strikes outside [x0, x2]: both Java engines
// and both C++ engines call enableExtrapolation() on this interpolation, so the
// extrapolated branch is production behaviour, not an edge case.

#include <ql/version.hpp>

#include <ql/experimental/barrieroption/vannavolgainterpolation.hpp>

#include <cmath>
#include <string>
#include <vector>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct Smile {
    const char* key;
    Real spot;
    Rate rd;          // domestic rate, continuously compounded
    Rate rf;          // foreign rate
    Time T;
    std::vector<Real> strikes; // exactly 3, sorted
    std::vector<Volatility> vols;
};

void addSmile(ReferenceWriter& out, const Smile& s) {
    const DiscountFactor dDiscount = std::exp(-s.rd * s.T);
    const DiscountFactor fDiscount = std::exp(-s.rf * s.T);

    VannaVolgaInterpolation interp(s.strikes.begin(), s.strikes.end(), s.vols.begin(),
                                  s.spot, dDiscount, fDiscount, s.T);
    interp.enableExtrapolation();
    interp.update();

    // The grid is laid out in standard deviations of log-moneyness around the
    // forward, z in [-2, 2], NOT as a flat percentage of the strike range.
    //
    // This matters. value(k) ends in blackFormulaImpliedStdDev, a NewtonSafe
    // solve that stops at accuracy 1e-6 *on the option price*; the implied
    // standard deviation is therefore only determined to 1e-6/vega. Beyond
    // roughly 3 standard deviations the option is worth its intrinsic value,
    // vega collapses, and the recovered volatility becomes arbitrary to several
    // decimal places — a number that would record the solver's stopping point
    // rather than the vanna-volga arithmetic under test. Inside |z| <= 2,
    // phi(d1) >= ~0.05, so vega stays bounded away from zero and the inversion
    // is well conditioned.
    //
    // z = +/-2 still overshoots the three quoted strikes in every case here, so
    // the extrapolated branch the engines rely on (they all call
    // enableExtrapolation) is genuinely exercised.
    const Real fwd = s.spot * fDiscount / dDiscount;
    const Real atmStdDev = s.vols[1] * std::sqrt(s.T);
    json rows = json::array();
    for (int i = 0; i <= 20; ++i) {
        const Real z = -2.0 + 4.0 * i / 20.0;
        const Real k = fwd * std::exp(z * atmStdDev);
        rows.push_back(json{{"k", k}, {"z", z}, {"vol", interp(k, /*allowExtrapolation=*/true)}});
    }
    // and exactly at the three quoted strikes, where the interpolation must
    // reproduce the quotes it was built from
    for (Size i = 0; i < s.strikes.size(); ++i) {
        rows.push_back(json{{"k", s.strikes[i]},
                            {"vol", interp(s.strikes[i], true)},
                            {"quotedVol", s.vols[i]}});
    }

    out.addCase(s.key,
                json{{"spot", s.spot},
                     {"rd", s.rd},
                     {"rf", s.rf},
                     {"T", s.T},
                     {"dDiscount", dDiscount},
                     {"fDiscount", fDiscount},
                     {"strikes", s.strikes},
                     {"vols", s.vols}},
                json{{"rows", rows}});
}

} // namespace

int main() {
    ReferenceWriter out("experimental/barrieroption/vanna_volga_interpolation", QL_VERSION,
                        "vanna_volga_interpolation_probe");

    // A symmetric FX smile, an asymmetric (risk-reversal skewed) one, a short
    // expiry and a long expiry — the vega weighting is a function of both the
    // moneyness spread and sqrt(T), so both need pinning.
    addSmile(out, {"fx_symmetric_6m", 1.0, 0.02, 0.01, 0.5,
                   {0.9, 1.0, 1.1}, {0.115, 0.100, 0.120}});
    addSmile(out, {"fx_skewed_1y", 1.30, 0.03, 0.005, 1.0,
                   {1.15, 1.30, 1.50}, {0.145, 0.110, 0.098}});
    addSmile(out, {"fx_short_1m", 100.0, 0.01, 0.04, 1.0 / 12.0,
                   {95.0, 100.0, 106.0}, {0.22, 0.18, 0.21}});
    addSmile(out, {"fx_long_5y", 1.0, 0.04, 0.02, 5.0,
                   {0.75, 1.0, 1.4}, {0.19, 0.15, 0.17}});

    out.write();
    return 0;
}
