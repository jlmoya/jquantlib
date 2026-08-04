// migration-harness/cpp/probes/methods/v143_smilesection_rnd_probe.cpp
//
// Reference values for SmileSectionRNDCalculator, new in C++ QuantLib v1.43
// (ql/methods/finitedifferences/utilities/smilesectionrndcalculator.{hpp,cpp}).
//
// It derives the risk-neutral terminal density implied by a SmileSection via
// Breeden-Litzenberger, and implements RiskNeutralDensityCalculator:
//
//     Real pdf(Real x, Time t) const override;     // x = ln(S)
//     Real cdf(Real x, Time t) const override;
//     Real invcdf(Real p, Time t) const override;
//     Real pdf(Real x) const;                      // t = smile->exerciseTime()
//     Real cdf(Real x) const;
//     Real invcdf(Real p) const;
//
// Constructor:
//     SmileSectionRNDCalculator(ext::shared_ptr<SmileSection> smile,
//                               Size nStrikes = 200,
//                               Real nStd = 5.0);
//
// Behaviour a port has to reproduce, none of it obvious from the signatures
// ------------------------------------------------------------------------
//  1. pdf() and cdf() are DIRECT smile evaluations and never touch the strike
//     grid, so nStrikes / nStd are irrelevant to them:
//         pdf(x, t) = exp(x) * smile->density(exp(x), 1.0)          [gap 1e-4]
//         cdf(x, t) = 1 - smile->digitalOptionPrice(exp(x), Call, 1.0)  [gap 1e-5]
//     Both of those SmileSection helpers are finite differences of
//     SmileSection::optionPrice with the DEFAULT gaps; a port that uses a
//     different gap, or an analytic derivative, will not match in the wings.
//     Case `*_grid_independent_pdf_cdf` pins this independence explicitly.
//  2. Only invcdf() builds the grid, lazily, once, in initialize():
//         forward  = smile->atmLevel()             (must not be Null<Real>())
//         sigmaAtm = smile->volatility(forward)
//         logStd   = sigmaAtm * sqrt(T)
//         kMin     = max(forward * exp(-nStd * logStd), QL_EPSILON)
//         kMax     = forward * exp( nStd * logStd)
//         K_i      = kMin + (kMax - kMin) * i / (nStrikes - 1),  i in [0, nStrikes)
//         cdf_i    = clamp(1 - digitalOptionPrice(K_i, Call, 1.0), 0, 1)
//     then a RUNNING MAXIMUM is applied and points whose monotonised cdf gains
//     <= 1e-12 over the previous kept point are DROPPED, so the abscissa handed
//     to the spline is strictly increasing and generally shorter than nStrikes.
//     Fewer than 4 surviving points is a hard error.
//  3. The quantile function is a MonotonicCubicNaturalSpline over
//     (cdf_i -> K_i), i.e. cdf is the abscissa and strike the ordinate.
//     invcdf(p) requires 0 < p < 1, then CLAMPS p into
//     [cdf_.front(), cdf_.back()] and returns log(spline(p)).
//     Consequently invcdf(1e-12) returns exactly log(kMin_kept) and
//     invcdf(1 - 1e-12) returns exactly log(kMax_kept) -- the sharpest
//     available pin on the grid construction, captured by `*_grid_endpoints`.
//  4. checkTime() requires close_enough(t, smile->exerciseTime()); the 2-arg
//     overloads are not a way to reprice at another maturity.
//  5. Ordering inside invcdf() is checkTime -> initialize -> p-range check.
//     So on a smile with a Null atm level, invcdf(-1.0) reports the missing
//     atmLevel, NOT the invalid probability. `invcdf_atm_check_precedes_p_check`
//     pins that ordering.
//
// Smiles used
// -----------
//   flat       FlatSmileSection(2026-03-01, 0.20, Actual365Fixed, 2025-03-01, atm=100)
//   svi1       SviSmileSection(T=1, fwd=100, {0.04, 0.10, 0.30, -0.40,  0.00})
//   svi2       SviSmileSection(T=1, fwd= 96, {0.02, 0.08, 0.25, -0.30,  0.00})
//   sviSteep   SviSmileSection(T=1, fwd=100, {0.03, 0.25, 0.15, -0.75, -0.10})
//
// svi1/svi2 are the two parameter sets the v1.43 test-suite uses in
// testGaussianCopulaSpreadEngineSVI, so the reference values here line up with
// the spread-engine probe. sviSteep adds a strongly skewed, off-centre smile
// where a wrong wing discretisation shows up first. Forwards are exact decimals
// so no input value has to be reproduced through a transcendental function.
//
// The offsets go out to +-1.2 in log-moneyness, i.e. roughly +-6 ATM standard
// deviations, well outside the nStd = 5 grid, because that is where a wrong
// finite-difference gap or a wrong extrapolation first becomes visible.

#include <ql/qldefines.hpp>
#include <ql/version.hpp>

#include <ql/errors.hpp>
#include <ql/experimental/volatility/svismilesection.hpp>
#include <ql/methods/finitedifferences/utilities/smilesectionrndcalculator.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/volatility/atmsmilesection.hpp>
#include <ql/termstructures/volatility/flatsmilesection.hpp>
#include <ql/termstructures/volatility/smilesection.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "common.hpp"

#include <algorithm>
#include <cmath>
#include <exception>
#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// 2025-03-01 -> 2026-03-01 is exactly 365 days, so Actual365Fixed gives an
// exercise time of exactly 1.0 and the date-based and time-based smile
// constructors agree bit for bit.
const Date kToday(1, March, 2025);
const Date kMaturity(1, March, 2026);
const Time kT = 1.0;

const DayCounter& dayCounter() {
    static const DayCounter dc = Actual365Fixed();
    return dc;
}

using Smile = ext::shared_ptr<SmileSection>;

Smile flatSmile(Real atm) {
    return ext::make_shared<FlatSmileSection>(kMaturity, 0.20, dayCounter(), kToday, atm);
}

Smile flatSmileWithoutAtm() {
    return ext::make_shared<FlatSmileSection>(kMaturity, 0.20, dayCounter(), kToday);
}

Smile sviSmile(Real forward, const std::vector<Real>& params) {
    return ext::make_shared<SviSmileSection>(kT, forward, params);
}

struct SmileSpec {
    const char* tag;
    Smile smile;
    Real forward;
};

std::vector<SmileSpec> smiles() {
    return {
        {"flat", flatSmile(100.0), 100.0},
        {"svi1", sviSmile(100.0, {0.04, 0.10, 0.30, -0.40, 0.0}), 100.0},
        {"svi2", sviSmile(96.0, {0.02, 0.08, 0.25, -0.30, 0.0}), 96.0},
        {"svi_steep", sviSmile(100.0, {0.03, 0.25, 0.15, -0.75, -0.10}), 100.0},
    };
}

struct Offset {
    const char* tag;
    Real value;
};

const std::vector<Offset>& offsets() {
    static const std::vector<Offset> v = {
        {"m120", -1.2}, {"m090", -0.9}, {"m060", -0.6}, {"m030", -0.3},
        {"m010", -0.1}, {"000", 0.0},   {"p010", 0.1},  {"p030", 0.3},
        {"p060", 0.6},  {"p090", 0.9},  {"p120", 1.2},
    };
    return v;
}

struct Prob {
    const char* tag;
    Real value;
};

// Includes 1e-12 and 1 - 1e-12, which land on the clamp to the grid endpoints,
// and 1e-6 / 0.9999 which sit outside the region the nStd = 5 grid resolves
// well -- exactly where a wrong discretisation first shows up.
const std::vector<Prob>& probabilities() {
    static const std::vector<Prob> v = {
        {"p1em12", 1e-12},   {"p1em6", 1e-6},   {"p1em4", 1e-4},
        {"p1em3", 1e-3},     {"p001", 0.01},    {"p005", 0.05},
        {"p010", 0.10},      {"p025", 0.25},    {"p050", 0.50},
        {"p075", 0.75},      {"p090", 0.90},    {"p095", 0.95},
        {"p099", 0.99},      {"p0999", 0.999},  {"p09999", 0.9999},
        {"p1m1em12", 1.0 - 1e-12},
    };
    return v;
}

json baseInputs(const SmileSpec& s, Size nStrikes, Real nStd) {
    return json{{"smile", s.tag},
                {"forward", s.forward},
                {"exercise_time", s.smile->exerciseTime()},
                {"n_strikes", static_cast<int>(nStrikes)},
                {"n_std", nStd}};
}

std::string name(const std::string& a, const std::string& b) { return a + "_" + b; }

// ---------------------------------------------------------------------------
// pdf / cdf over a log-moneyness ladder. Grid parameters are irrelevant here,
// so the default calculator is used.
// ---------------------------------------------------------------------------
void emitPdfCdf(ReferenceWriter& out, const SmileSpec& s) {
    const SmileSectionRNDCalculator calc(s.smile);
    const Real logFwd = std::log(s.forward);

    for (const Offset& o : offsets()) {
        const Real x = logFwd + o.value;
        json in = baseInputs(s, 200, 5.0);
        in["log_forward"] = logFwd;
        in["offset"] = o.value;
        in["x"] = x;
        in["strike"] = std::exp(x);
        in["t"] = s.smile->exerciseTime();

        out.addCase(name(s.tag, std::string("pdf_cdf_") + o.tag), in,
                    json{{"pdf", calc.pdf(x, s.smile->exerciseTime())},
                         {"cdf", calc.cdf(x, s.smile->exerciseTime())},
                         {"smile_volatility", s.smile->volatility(std::exp(x))}});
    }
}

// ---------------------------------------------------------------------------
// invcdf over a probability ladder, for an arbitrary (nStrikes, nStd) grid.
// ---------------------------------------------------------------------------
void emitInvCdf(ReferenceWriter& out, const SmileSpec& s, Size nStrikes, Real nStd,
                const std::string& prefix, const std::vector<Prob>& probs) {
    const SmileSectionRNDCalculator calc(s.smile, nStrikes, nStd);
    for (const Prob& p : probs) {
        json in = baseInputs(s, nStrikes, nStd);
        in["p"] = p.value;
        in["t"] = s.smile->exerciseTime();
        const Real x = calc.invcdf(p.value, s.smile->exerciseTime());
        out.addCase(name(prefix, std::string("invcdf_") + p.tag), in,
                    json{{"invcdf", x}, {"strike", std::exp(x)}});
    }
}

// ---------------------------------------------------------------------------
// The grid initialize() builds, reconstructed from the public formula, plus the
// two clamped endpoints of invcdf. If a port's kMin/kMax or its dedup rule
// differ, `invcdf_at_p_min` / `invcdf_at_p_max` diverge immediately.
// ---------------------------------------------------------------------------
void emitGridEndpoints(ReferenceWriter& out, const SmileSpec& s, Size nStrikes, Real nStd,
                       const std::string& prefix) {
    const SmileSectionRNDCalculator calc(s.smile, nStrikes, nStd);
    const Time t = s.smile->exerciseTime();
    const Real forward = s.smile->atmLevel();
    const Real sigmaAtm = s.smile->volatility(forward);
    const Real logStd = sigmaAtm * std::sqrt(t);
    const Real kMin = std::max(forward * std::exp(-nStd * logStd), Real(QL_EPSILON));
    const Real kMax = forward * std::exp(nStd * logStd);

    const Real invAtPMin = calc.invcdf(1e-12, t);
    const Real invAtPMax = calc.invcdf(1.0 - 1e-12, t);

    out.addCase(name(prefix, "grid_endpoints"), baseInputs(s, nStrikes, nStd),
                json{{"exercise_time", t},
                     {"atm_level", forward},
                     {"sigma_atm", sigmaAtm},
                     {"log_std", logStd},
                     {"k_min", kMin},
                     {"k_max", kMax},
                     {"cdf_at_k_min", calc.cdf(std::log(kMin), t)},
                     {"cdf_at_k_max", calc.cdf(std::log(kMax), t)},
                     {"invcdf_at_p_min", invAtPMin},
                     {"invcdf_at_p_max", invAtPMax},
                     {"strike_at_p_min", std::exp(invAtPMin)},
                     {"strike_at_p_max", std::exp(invAtPMax)}});
}

// ---------------------------------------------------------------------------
// The 1-arg overloads must equal the 2-arg ones at t = smile->exerciseTime().
// ---------------------------------------------------------------------------
void emitOverloadAgreement(ReferenceWriter& out, const SmileSpec& s) {
    const SmileSectionRNDCalculator calc(s.smile);
    const Time t = s.smile->exerciseTime();
    const Real x = std::log(s.forward) + 0.15;
    const Real p = 0.3;

    json in = baseInputs(s, 200, 5.0);
    in["x"] = x;
    in["p"] = p;
    in["t"] = t;

    out.addCase(name(s.tag, "overloads_agree"), in,
                json{{"pdf_1arg", calc.pdf(x)},
                     {"pdf_2arg", calc.pdf(x, t)},
                     {"cdf_1arg", calc.cdf(x)},
                     {"cdf_2arg", calc.cdf(x, t)},
                     {"invcdf_1arg", calc.invcdf(p)},
                     {"invcdf_2arg", calc.invcdf(p, t)}});
}

// ---------------------------------------------------------------------------
// pdf / cdf must be identical across wildly different grid parameters, because
// neither ever calls initialize().
// ---------------------------------------------------------------------------
void emitGridIndependence(ReferenceWriter& out, const SmileSpec& s) {
    const SmileSectionRNDCalculator wide(s.smile, 1000, 6.0);
    const SmileSectionRNDCalculator narrow(s.smile, 4, 0.25);
    const Time t = s.smile->exerciseTime();
    const Real x = std::log(s.forward) - 0.4;

    json in = baseInputs(s, 200, 5.0);
    in["x"] = x;
    in["t"] = t;
    in["grid_a"] = json{{"n_strikes", 1000}, {"n_std", 6.0}};
    in["grid_b"] = json{{"n_strikes", 4}, {"n_std", 0.25}};

    out.addCase(name(s.tag, "grid_independent_pdf_cdf"), in,
                json{{"pdf_grid_a", wide.pdf(x, t)},
                     {"pdf_grid_b", narrow.pdf(x, t)},
                     {"cdf_grid_a", wide.cdf(x, t)},
                     {"cdf_grid_b", narrow.cdf(x, t)}});
}

// ---------------------------------------------------------------------------
// Guard clauses. Both the fact that C++ throws and the message it throws are
// pinned: the substring lives in the case inputs, so a port can assert on the
// same wording without this file having to embed a full, build-dependent
// exception string.
// ---------------------------------------------------------------------------
struct ThrowInfo {
    bool threw = false;
    bool found = false;
};

template <class F>
ThrowInfo probeThrow(F&& f, const std::string& needle) {
    try {
        f();
        return ThrowInfo{false, false};
    } catch (const std::exception& e) {
        const std::string msg = e.what();
        return ThrowInfo{true, msg.find(needle) != std::string::npos};
    }
}

void emitGuard(ReferenceWriter& out, const std::string& caseName, const std::string& needle,
               const ThrowInfo& info, json extraInputs = json::object()) {
    json in = extraInputs;
    in["expected_message_substring"] = needle;
    out.addCase(caseName, in,
                json{{"throws", info.threw}, {"message_contains_substring", info.found}});
}

} // namespace

int main() {
    Settings::instance().evaluationDate() = kToday;

    ReferenceWriter out("methods/v143_smilesection_rnd", QL_VERSION,
                        "v143_smilesection_rnd_probe");

    const std::vector<SmileSpec> all = smiles();
    const SmileSpec& flat = all[0];
    const SmileSpec& svi1 = all[1];

    // -----------------------------------------------------------------------
    // Per-smile: pdf/cdf ladder, invcdf ladder, grid endpoints, overloads,
    // grid independence -- all on the DEFAULT calculator (nStrikes 200, nStd 5).
    // -----------------------------------------------------------------------
    for (const SmileSpec& s : all) {
        emitPdfCdf(out, s);
        emitInvCdf(out, s, 200, 5.0, s.tag, probabilities());
        emitGridEndpoints(out, s, 200, 5.0, s.tag);
        emitOverloadAgreement(out, s);
        emitGridIndependence(out, s);
    }

    // -----------------------------------------------------------------------
    // Non-default grids. Only invcdf is affected, and it is affected a lot:
    //   nStd = 2   -> grid spans only +-2 ATM std, so extreme p clamp hard
    //   nStrikes 8 -> coarse spline, large and very characteristic bias
    //   nStrikes 4 -> the documented minimum
    //   1000/6.0   -> fine and wide, the converged end of the scale
    // -----------------------------------------------------------------------

    const std::vector<Prob> shortLadder = {
        {"p1em12", 1e-12}, {"p1em3", 1e-3}, {"p005", 0.05},  {"p050", 0.50},
        {"p095", 0.95},    {"p0999", 0.999}, {"p1m1em12", 1.0 - 1e-12},
    };
    const std::vector<Prob> midLadder = {
        {"p001", 0.01}, {"p010", 0.10}, {"p025", 0.25}, {"p050", 0.50},
        {"p075", 0.75}, {"p090", 0.90}, {"p099", 0.99},
    };
    const std::vector<Prob> tinyLadder = {
        {"p025", 0.25}, {"p050", 0.50}, {"p075", 0.75},
    };

    emitInvCdf(out, svi1, 200, 2.0, "svi1_nstd2", shortLadder);
    emitGridEndpoints(out, svi1, 200, 2.0, "svi1_nstd2");

    emitInvCdf(out, svi1, 8, 5.0, "svi1_nstrikes8", midLadder);
    emitGridEndpoints(out, svi1, 8, 5.0, "svi1_nstrikes8");

    emitInvCdf(out, svi1, 4, 5.0, "svi1_nstrikes4", tinyLadder);
    emitGridEndpoints(out, svi1, 4, 5.0, "svi1_nstrikes4");

    emitInvCdf(out, flat, 4, 5.0, "flat_nstrikes4", tinyLadder);
    emitInvCdf(out, flat, 1000, 6.0, "flat_nstrikes1000_nstd6", shortLadder);
    emitGridEndpoints(out, flat, 1000, 6.0, "flat_nstrikes1000_nstd6");

    // A very narrow grid (nStd = 0.5) makes the clamp dominate: every p outside
    // [cdf(kMin), cdf(kMax)] collapses onto the same two strikes.
    emitInvCdf(out, flat, 200, 0.5, "flat_nstd05", shortLadder);
    emitGridEndpoints(out, flat, 200, 0.5, "flat_nstd05");

    // -----------------------------------------------------------------------
    // AtmSmileSection is the documented way to supply a missing atm level.
    // Wrapping the atm-less flat smile at 100 must reproduce the direct
    // FlatSmileSection(atm = 100) results exactly.
    // -----------------------------------------------------------------------
    {
        const Smile wrapped = ext::make_shared<AtmSmileSection>(flatSmileWithoutAtm(), 100.0);
        const SmileSectionRNDCalculator wrappedCalc(wrapped);
        const SmileSectionRNDCalculator directCalc(flat.smile);
        const Time t = flat.smile->exerciseTime();
        const Real x = std::log(100.0) + 0.25;

        out.addCase("flat_via_atmsmilesection_matches_direct",
                    json{{"smile", "AtmSmileSection(FlatSmileSection(vol=0.20, atm=Null), 100)"},
                         {"forward", 100.0},
                         {"x", x},
                         {"p", 0.65},
                         {"t", t},
                         {"n_strikes", 200},
                         {"n_std", 5.0}},
                    json{{"wrapped_exercise_time", wrapped->exerciseTime()},
                         {"wrapped_atm_level", wrapped->atmLevel()},
                         {"pdf_wrapped", wrappedCalc.pdf(x, t)},
                         {"pdf_direct", directCalc.pdf(x, t)},
                         {"cdf_wrapped", wrappedCalc.cdf(x, t)},
                         {"cdf_direct", directCalc.cdf(x, t)},
                         {"invcdf_wrapped", wrappedCalc.invcdf(0.65, t)},
                         {"invcdf_direct", directCalc.invcdf(0.65, t)}});
    }

    // -----------------------------------------------------------------------
    // Constructor guards.
    // -----------------------------------------------------------------------
    emitGuard(out, "ctor_rejects_null_smile", "null SmileSection",
              probeThrow([] { SmileSectionRNDCalculator c{Smile()}; },
                         "null SmileSection"),
              json{{"smile", "null"}});

    emitGuard(out, "ctor_rejects_n_strikes_3", "at least 4 strikes required",
              probeThrow([&] { SmileSectionRNDCalculator c{flat.smile, 3, 5.0}; },
                         "at least 4 strikes required"),
              json{{"smile", "flat"}, {"n_strikes", 3}, {"n_std", 5.0}});

    // nStrikes == 4 is the documented minimum and must be accepted, so this
    // case is expected to report throws = false.
    emitGuard(out, "ctor_accepts_n_strikes_4", "at least 4 strikes required",
              probeThrow([&] { SmileSectionRNDCalculator c{flat.smile, 4, 5.0}; },
                         "at least 4 strikes required"),
              json{{"smile", "flat"},
                   {"n_strikes", 4},
                   {"n_std", 5.0},
                   {"note", "4 is the documented minimum; the ctor must accept it"}});

    emitGuard(out, "ctor_rejects_n_std_zero", "nStd must be positive",
              probeThrow([&] { SmileSectionRNDCalculator c{flat.smile, 200, 0.0}; },
                         "nStd must be positive"),
              json{{"smile", "flat"}, {"n_strikes", 200}, {"n_std", 0.0}});

    emitGuard(out, "ctor_rejects_n_std_negative", "nStd must be positive",
              probeThrow([&] { SmileSectionRNDCalculator c{flat.smile, 200, -1.0}; },
                         "nStd must be positive"),
              json{{"smile", "flat"}, {"n_strikes", 200}, {"n_std", -1.0}});

    // -----------------------------------------------------------------------
    // invcdf probability range: strictly inside (0, 1).
    // -----------------------------------------------------------------------
    emitGuard(out, "invcdf_rejects_p_zero", "p must be in (0, 1)",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.invcdf(0.0, kT);
              }, "p must be in (0, 1)"),
              json{{"smile", "flat"}, {"p", 0.0}, {"t", kT}});

    emitGuard(out, "invcdf_rejects_p_one", "p must be in (0, 1)",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.invcdf(1.0, kT);
              }, "p must be in (0, 1)"),
              json{{"smile", "flat"}, {"p", 1.0}, {"t", kT}});

    emitGuard(out, "invcdf_rejects_p_negative", "p must be in (0, 1)",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.invcdf(-0.25, kT);
              }, "p must be in (0, 1)"),
              json{{"smile", "flat"}, {"p", -0.25}, {"t", kT}});

    // -----------------------------------------------------------------------
    // checkTime(): every entry point rejects a time other than the smile's own
    // exercise time (compared with close_enough, not ==).
    // -----------------------------------------------------------------------
    emitGuard(out, "pdf_rejects_time_mismatch", "does not match smile exercise time",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.pdf(std::log(100.0), 0.5);
              }, "does not match smile exercise time"),
              json{{"smile", "flat"}, {"t", 0.5}, {"smile_exercise_time", kT}});

    emitGuard(out, "cdf_rejects_time_mismatch", "does not match smile exercise time",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.cdf(std::log(100.0), 0.5);
              }, "does not match smile exercise time"),
              json{{"smile", "flat"}, {"t", 0.5}, {"smile_exercise_time", kT}});

    emitGuard(out, "invcdf_rejects_time_mismatch", "does not match smile exercise time",
              probeThrow([&] {
                  SmileSectionRNDCalculator c(flat.smile);
                  c.invcdf(0.5, 2.0);
              }, "does not match smile exercise time"),
              json{{"smile", "flat"}, {"t", 2.0}, {"smile_exercise_time", kT}});

    // -----------------------------------------------------------------------
    // Missing atm level. invcdf reports it from initialize(); cdf/pdf hit the
    // *SmileSection*'s own requirement inside optionPrice() instead, so the two
    // messages differ. This mirrors the v1.43 test-suite case
    // testSmileSectionRNDMissingAtmLevel in riskneutraldensitycalculator.cpp.
    // -----------------------------------------------------------------------
    emitGuard(out, "invcdf_rejects_missing_atm_level", "wrap with AtmSmileSection",
              probeThrow([] {
                  SmileSectionRNDCalculator c(flatSmileWithoutAtm());
                  c.invcdf(0.5, kT);
              }, "wrap with AtmSmileSection"),
              json{{"smile", "FlatSmileSection(vol=0.20, atm=Null)"}, {"p", 0.5}, {"t", kT}});

    emitGuard(out, "cdf_rejects_missing_atm_level", "smile section must provide atm level",
              probeThrow([] {
                  SmileSectionRNDCalculator c(flatSmileWithoutAtm());
                  c.cdf(std::log(100.0), kT);
              }, "smile section must provide atm level"),
              json{{"smile", "FlatSmileSection(vol=0.20, atm=Null)"}, {"t", kT}});

    emitGuard(out, "pdf_rejects_missing_atm_level", "smile section must provide atm level",
              probeThrow([] {
                  SmileSectionRNDCalculator c(flatSmileWithoutAtm());
                  c.pdf(std::log(100.0), kT);
              }, "smile section must provide atm level"),
              json{{"smile", "FlatSmileSection(vol=0.20, atm=Null)"}, {"t", kT}});

    // invcdf runs checkTime -> initialize -> p-range check, so on an atm-less
    // smile an out-of-range p is reported as the atmLevel failure.
    {
        const ThrowInfo atmInfo = probeThrow([] {
            SmileSectionRNDCalculator c(flatSmileWithoutAtm());
            c.invcdf(-1.0, kT);
        }, "wrap with AtmSmileSection");
        const ThrowInfo pInfo = probeThrow([] {
            SmileSectionRNDCalculator c(flatSmileWithoutAtm());
            c.invcdf(-1.0, kT);
        }, "p must be in (0, 1)");
        out.addCase("invcdf_atm_check_precedes_p_check",
                    json{{"smile", "FlatSmileSection(vol=0.20, atm=Null)"},
                         {"p", -1.0},
                         {"t", kT},
                         {"atm_substring", "wrap with AtmSmileSection"},
                         {"p_range_substring", "p must be in (0, 1)"}},
                    json{{"throws", atmInfo.threw},
                         {"message_contains_atm_substring", atmInfo.found},
                         {"message_contains_p_range_substring", pInfo.found}});
    }

    out.write();
    return 0;
}
