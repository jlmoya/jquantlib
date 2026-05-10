// migration-harness/cpp/probes/experimental/credit-base-corr/base_correlation_term_structure_probe.cpp
// Reference values for ql/experimental/credit/basecorrelationstructure.hpp
// (BaseCorrelationTermStructure<Interpolator2D>).
//
// We probe both Bilinear and Bicubic specialisations on a small 3x3 grid of
// (loss-level, tranche-tenor, correlation) values, then evaluate at a mix of
// grid-points (TIGHT) and interior queries (LOOSE).

#include <ql/version.hpp>
#include <ql/experimental/credit/basecorrelationstructure.hpp>
#include <ql/math/interpolations/bilinearinterpolation.hpp>
#include <ql/math/interpolations/bicubicsplineinterpolation.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/businessdayconvention.hpp>
#include <ql/time/period.hpp>
#include <ql/settings.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

template <class I2D>
void emitCases(ReferenceWriter& out,
               const std::string& interpName,
               const std::vector<Period>& tenors,
               const std::vector<Real>& losses,
               const std::vector<std::vector<Real>>& correlVals,
               const Date& asof) {
    Settings::instance().evaluationDate() = asof;

    const Calendar cal = NullCalendar();
    const DayCounter dc = Actual360();
    const BusinessDayConvention bdc = ModifiedFollowing;
    const Natural settlementDays = 0;

    // Build the Handle<Quote> matrix.
    std::vector<std::vector<Handle<Quote>>> hs(losses.size(),
            std::vector<Handle<Quote>>(tenors.size()));
    for (Size i = 0; i < losses.size(); ++i) {
        for (Size j = 0; j < tenors.size(); ++j) {
            hs[i][j] = Handle<Quote>(ext::shared_ptr<Quote>(new SimpleQuote(correlVals[i][j])));
        }
    }

    BaseCorrelationTermStructure<I2D> ts(
        settlementDays, cal, bdc, tenors, losses, hs, dc);

    json inputs = {
        {"asof", asof.serialNumber()},
        {"interpolator", interpName},
        {"tenors_months", json::array()},
        {"losses", losses},
        {"correlsRowMajor", json::array()}
    };
    for (auto& t : tenors) inputs["tenors_months"].push_back(t.length());
    for (auto& row : correlVals) {
        for (auto v : row) inputs["correlsRowMajor"].push_back(v);
    }

    // Reference / max date checks.
    out.addCase(interpName + "_settlementDays", inputs, (double) settlementDays);
    out.addCase(interpName + "_correlationSize", inputs, 1.0);

    // Grid-point evaluation (TIGHT — these must equal input correlation values).
    // BaseCorrelationTermStructure stores correlations_[i][j] with i = loss, j = tenor.
    // The interpolation is built on (trancheTimes (j), lossLevel (i)) with the
    // correlations_ matrix; calling correlation(time, lossLevel) should hit
    // the grid value for grid points.
    for (Size i = 0; i < losses.size(); ++i) {
        for (Size j = 0; j < tenors.size(); ++j) {
            Date d = cal.advance(asof, tenors[j], bdc);
            Real expected = correlVals[i][j];
            Real got = ts.correlation(d, losses[i], true);
            std::string name = interpName + "_grid_i" + std::to_string(i)
                             + "_j" + std::to_string(j);
            // Use 'got' so we capture exactly what C++ produced (probe is the
            // ground truth; if there's a parametrisation surprise, the Java
            // test will see it mirror).
            (void) expected;
            out.addCase(name, inputs, got);
        }
    }

    // Halfway-between interior queries (LOOSE — interpolated values).
    if (losses.size() >= 2 && tenors.size() >= 2) {
        Date dHalf = cal.advance(asof,
                                 Period((tenors[0].length() + tenors[1].length()) / 2,
                                        tenors[0].units()),
                                 bdc);
        Real lossHalf = 0.5 * (losses[0] + losses[1]);
        Real interp = ts.correlation(dHalf, lossHalf, true);
        out.addCase(interpName + "_interior_half", inputs, interp);
    }
}

} // namespace

int main() {
    ReferenceWriter out("experimental/credit-base-corr/base_correlation_term_structure",
                        QL_VERSION,
                        "base_correlation_term_structure_probe");

    Date asof(15, June, 2010);

    // Test grid: 4 tenors, 3 loss levels.
    // Bilinear needs >= 2x2; bicubic needs >= 4x4 -> use 4x4 to satisfy both.
    std::vector<Period> tenors4 = {
        Period(12, Months),
        Period(36, Months),
        Period(60, Months),
        Period(84, Months)
    };
    std::vector<Real> losses4 = {0.03, 0.07, 0.12, 0.22};
    std::vector<std::vector<Real>> correls4 = {
        {0.30, 0.32, 0.34, 0.36},   // loss = 0.03
        {0.40, 0.42, 0.44, 0.46},   // loss = 0.07
        {0.55, 0.57, 0.60, 0.63},   // loss = 0.12
        {0.70, 0.72, 0.75, 0.78}    // loss = 0.22
    };

    emitCases<BilinearInterpolation>(out, "bilinear", tenors4, losses4, correls4, asof);
    emitCases<BicubicSpline>(out, "bicubic", tenors4, losses4, correls4, asof);

    // Smaller grid for bilinear-only path, 2x2 minimum.
    std::vector<Period> tenors2 = {Period(12, Months), Period(60, Months)};
    std::vector<Real> losses2 = {0.05, 0.20};
    std::vector<std::vector<Real>> correls2 = {
        {0.40, 0.50},
        {0.65, 0.78}
    };
    emitCases<BilinearInterpolation>(out, "bilinear2x2", tenors2, losses2, correls2, asof);

    out.write();
    return 0;
}
