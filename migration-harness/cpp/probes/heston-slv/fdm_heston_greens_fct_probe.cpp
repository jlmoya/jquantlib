// migration-harness/cpp/probes/heston-slv/fdm_heston_greens_fct_probe.cpp
//
// Reference values for FdmHestonGreensFct (Phase 5h.5-SLV-b).
//
// Sample the Heston Fokker-Planck Green's function on a small uniform 2D
// (x, v) grid for the Plain transformation, with the ZeroCorrelation and
// Gaussian closed-form approximations. SemiAnalytical relies on
// HestonProcess::pdf which JQuantLib has not yet ported, so we skip it.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/utilities/fdmhestongreensfct.hpp>
#include <ql/methods/finitedifferences/operators/fdmsquarerootfwdop.hpp>
#include <ql/methods/finitedifferences/meshers/fdmmeshercomposite.hpp>
#include <ql/methods/finitedifferences/meshers/uniform1dmesher.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/math/array.hpp>
#include <vector>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json arrayToJson(const Array& a) {
    json j = json::array();
    for (Size i = 0; i < a.size(); ++i) j.push_back(a[i]);
    return j;
}

ext::shared_ptr<HestonProcess> makeHestonProcess(double r, double q, double s0,
                                                  double v0, double kappa,
                                                  double theta, double sigma,
                                                  double rho) {
    Date today(15, May, 2026);
    DayCounter dc = Actual365Fixed();
    Handle<YieldTermStructure> rTS(ext::make_shared<FlatForward>(today, r, dc));
    Handle<YieldTermStructure> qTS(ext::make_shared<FlatForward>(today, q, dc));
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(s0));
    return ext::make_shared<HestonProcess>(rTS, qTS, spot, v0, kappa, theta, sigma, rho);
}

ext::shared_ptr<FdmMesher> makeMesher2D(double xMin, double xMax, Size nx,
                                         double vMin, double vMax, Size nv) {
    auto mx = ext::make_shared<Uniform1dMesher>(xMin, xMax, nx);
    auto mv = ext::make_shared<Uniform1dMesher>(vMin, vMax, nv);
    return ext::make_shared<FdmMesherComposite>(mx, mv);
}

} // namespace

int main() {
    ReferenceWriter out("heston-slv/fdm_heston_greens_fct",
                        QL_VERSION,
                        "fdm_heston_greens_fct_probe");

    const double r = 0.03, q = 0.0, s0 = 100.0;
    const double v0 = 0.04, kappa = 2.5, theta = 0.04, sigma = 0.2, rho = -0.5;
    auto process = makeHestonProcess(r, q, s0, v0, kappa, theta, sigma, rho);

    const double xMin = std::log(50.0), xMax = std::log(200.0);
    const Size nx = 6;
    const double vMin = 0.005, vMax = 0.5;
    const Size nv = 6;
    auto mesher = makeMesher2D(xMin, xMax, nx, vMin, vMax, nv);

    const double l0 = 1.0;
    FdmHestonGreensFct gf(mesher, process, FdmSquareRootFwdOp::Plain, l0);

    {
        const Time t = 0.1;
        json inputs = {
            {"r", r}, {"q", q}, {"s0", s0},
            {"v0", v0}, {"kappa", kappa}, {"theta", theta},
            {"sigma", sigma}, {"rho", rho},
            {"xMin", xMin}, {"xMax", xMax}, {"nx", nx},
            {"vMin", vMin}, {"vMax", vMax}, {"nv", nv},
            {"transform", "plain"}, {"l0", l0}, {"t", t}
        };
        json expected = {
            {"zero_correlation", arrayToJson(gf.get(t, FdmHestonGreensFct::ZeroCorrelation))},
            {"gaussian",         arrayToJson(gf.get(t, FdmHestonGreensFct::Gaussian))}
        };
        out.addCase("plain_t01", inputs, expected);
    }

    {
        const Time t = 0.5;
        json inputs = {
            {"r", r}, {"q", q}, {"s0", s0},
            {"v0", v0}, {"kappa", kappa}, {"theta", theta},
            {"sigma", sigma}, {"rho", rho},
            {"xMin", xMin}, {"xMax", xMax}, {"nx", nx},
            {"vMin", vMin}, {"vMax", vMax}, {"nv", nv},
            {"transform", "plain"}, {"l0", l0}, {"t", t}
        };
        json expected = {
            {"zero_correlation", arrayToJson(gf.get(t, FdmHestonGreensFct::ZeroCorrelation))},
            {"gaussian",         arrayToJson(gf.get(t, FdmHestonGreensFct::Gaussian))}
        };
        out.addCase("plain_t05", inputs, expected);
    }

    // Power transform — Jacobian = v^(1 - 2 kappa theta / sigma^2)
    {
        FdmHestonGreensFct gfPow(mesher, process, FdmSquareRootFwdOp::Power, l0);
        const Time t = 0.25;
        json inputs = {
            {"r", r}, {"q", q}, {"s0", s0},
            {"v0", v0}, {"kappa", kappa}, {"theta", theta},
            {"sigma", sigma}, {"rho", rho},
            {"xMin", xMin}, {"xMax", xMax}, {"nx", nx},
            {"vMin", vMin}, {"vMax", vMax}, {"nv", nv},
            {"transform", "power"}, {"l0", l0}, {"t", t}
        };
        json expected = {
            {"zero_correlation", arrayToJson(gfPow.get(t, FdmHestonGreensFct::ZeroCorrelation))},
            {"gaussian",         arrayToJson(gfPow.get(t, FdmHestonGreensFct::Gaussian))}
        };
        out.addCase("power_t025", inputs, expected);
    }

    // Log transform — variance dimension is log(v); Jacobian = v
    {
        const double vLogMin = std::log(0.005), vLogMax = std::log(0.5);
        auto mLog = ext::make_shared<FdmMesherComposite>(
                        ext::make_shared<Uniform1dMesher>(xMin, xMax, nx),
                        ext::make_shared<Uniform1dMesher>(vLogMin, vLogMax, nv));
        FdmHestonGreensFct gfLog(mLog, process, FdmSquareRootFwdOp::Log, l0);
        const Time t = 0.25;
        json inputs = {
            {"r", r}, {"q", q}, {"s0", s0},
            {"v0", v0}, {"kappa", kappa}, {"theta", theta},
            {"sigma", sigma}, {"rho", rho},
            {"xMin", xMin}, {"xMax", xMax}, {"nx", nx},
            {"vLogMin", vLogMin}, {"vLogMax", vLogMax}, {"nv", nv},
            {"transform", "log"}, {"l0", l0}, {"t", t}
        };
        json expected = {
            {"zero_correlation", arrayToJson(gfLog.get(t, FdmHestonGreensFct::ZeroCorrelation))},
            {"gaussian",         arrayToJson(gfLog.get(t, FdmHestonGreensFct::Gaussian))}
        };
        out.addCase("log_t025", inputs, expected);
    }

    out.write();
    return 0;
}
