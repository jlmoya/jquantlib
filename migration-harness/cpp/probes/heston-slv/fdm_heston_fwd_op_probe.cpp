// migration-harness/cpp/probes/heston-slv/fdm_heston_fwd_op_probe.cpp
//
// Reference values for FdmHestonFwdOp (Phase 5h.5-SLV).
//
// Apply on the (x, v) grid for the Plain transformation, after a setTime call,
// for a couple of input vectors (constant 1, log-S sin pattern).

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/operators/fdmhestonfwdop.hpp>
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
    ReferenceWriter out("heston-slv/fdm_heston_fwd_op",
                        QL_VERSION,
                        "fdm_heston_fwd_op_probe");

    const double r = 0.03, q = 0.0, s0 = 100.0;
    const double v0 = 0.04, kappa = 2.5, theta = 0.04, sigma = 0.2, rho = -0.5;
    auto process = makeHestonProcess(r, q, s0, v0, kappa, theta, sigma, rho);

    const double xMin = std::log(50.0), xMax = std::log(200.0);
    const Size nx = 8;
    const double vMin = 0.005, vMax = 0.5;
    const Size nv = 8;
    auto mesher = makeMesher2D(xMin, xMax, nx, vMin, vMax, nv);

    FdmHestonFwdOp op(mesher, process, FdmSquareRootFwdOp::Plain);
    op.setTime(0.0, 1.0);

    const Size N = nx * nv;
    Array p1(N, 1.0);
    Array p2(N);
    Array vSpread(N);
    Array vConcen(N);
    for (Size j = 0; j < nv; ++j) {
        for (Size i = 0; i < nx; ++i) {
            const double x = mesher->locations(0)[i + j*nx];
            const double v = mesher->locations(1)[i + j*nx];
            p2[i + j*nx] = std::sin(x);
            vSpread[i + j*nx] = std::exp(-(x - std::log(s0)) * (x - std::log(s0)));
            vConcen[i + j*nx] = std::exp(-(v - 0.04) * (v - 0.04) / 0.001);
        }
    }

    {
        json inputs = {
            {"r", r}, {"q", q}, {"s0", s0},
            {"v0", v0}, {"kappa", kappa}, {"theta", theta},
            {"sigma", sigma}, {"rho", rho},
            {"xMin", xMin}, {"xMax", xMax}, {"nx", nx},
            {"vMin", vMin}, {"vMax", vMax}, {"nv", nv},
            {"transform", "plain"},
            {"setTime", json::array({0.0, 1.0})}
        };
        json expected = {
            {"apply_constant", arrayToJson(op.apply(p1))},
            {"apply_sin",      arrayToJson(op.apply(p2))},
            {"apply_xspread",  arrayToJson(op.apply(vSpread))},
            {"apply_vconcen",  arrayToJson(op.apply(vConcen))},
            {"applyMixed_constant", arrayToJson(op.apply_mixed(p1))},
            {"applyDirection0",     arrayToJson(op.apply_direction(0, p2))},
            {"applyDirection1",     arrayToJson(op.apply_direction(1, p2))}
        };
        out.addCase("plain_apply", inputs, expected);
    }

    out.write();
    return 0;
}
