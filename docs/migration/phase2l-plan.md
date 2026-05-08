# Phase 2l Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Tracks A/B dispatched in parallel after L0; Track C dispatches in parallel and runs its 6 sub-commits sequentially within worktree C.

**Goal:** Close Phase 2h Fdm framework completeness deferrals — BiCGStab/GMRES iterative solvers + Bermudan/American/dividend step conditions + 6 additional schemes. Tests `809 → ~819-825`; scanner WIP=0; tag `jquantlib-phase2l-complete`.

## L0 — Pre-flight + 3 worktrees + progress doc

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2l-A-iterative-solvers /Users/josemoya/eclipse-workspace/jquantlib-2l-A main
git worktree add -b phase-2l-B-step-conditions /Users/josemoya/eclipse-workspace/jquantlib-2l-B main
git worktree add -b phase-2l-C-schemes /Users/josemoya/eclipse-workspace/jquantlib-2l-C main
for wt in A B C; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2l-$wt
  git submodule update --init --recursive
done
```

Init `phase2l-progress.md` + commit.

## Track A — BiCGStab + GMRES (worktree A, 1-2 commits)

- C++: `ql/math/matrixutilities/bicgstab.{hpp,cpp}` (54+94=148 LOC) + `gmres.{hpp,cpp}` (73+147=220 LOC)
- Java target: `org.jquantlib.math.matrixutilities.{BiCGStab,GMRES}` (joins the just-lifted TqrEigenDecomposition)
- Probe: bicgstab + gmres convergence on small symmetric/non-symmetric test matrices
- Test: probe-driven, ONE @Test, TIGHT
- Commits: `infra(math.matrixutilities): port BiCGStab iterative solver (Phase 2l Track A.1)` + `infra(math.matrixutilities): port GMRES iterative solver (Phase 2l Track A.2)` (or one combined commit if cohesive)

## Track B — Step conditions + FdmDividendHandler + vanillaComposite wiring (worktree B, 1 commit)

- C++:
  - `methods/finitedifferences/stepconditions/fdmamericanstepcondition.{hpp,cpp}` (49+50=99 LOC)
  - `methods/finitedifferences/stepconditions/fdmbermudanstepcondition.{hpp,cpp}` (53+66=119 LOC)
  - `methods/finitedifferences/utilities/fdmdividendhandler.{hpp,cpp}` (61+107=168 LOC)
- Java targets: existing `org.jquantlib.methods.finitedifferences.{stepconditions,utilities}` packages
- Wire vanillaComposite branches in Phase 2h's existing FdmInnerValueCalculator code that currently throw `LibraryException` for Bermudan/American/dividend
- Probe: 1-3 probe(s) for the 3 classes — exercise step-condition apply() at varying t, x, y; dividend handler lifecycle
- Test: probe-driven
- Commit: `infra(methods.finitedifferences): port FdmAmericanStepCondition + FdmBermudanStepCondition + FdmDividendHandler + wire vanillaComposite branches (Phase 2l Track B)`

## Track C — 6 schemes (worktree C, sequential within track)

Order chosen for dependency: simpler schemes first, then more complex.

- **C.1** — ExplicitEulerScheme (61+47=108 LOC) — simplest, baseline reference
- **C.2** — CrankNicolsonScheme (67+56=123 LOC) — implicit-explicit blend
- **C.3** — CraigSneydScheme (62+66=128 LOC) — ADI scheme
- **C.4** — ModifiedCraigSneydScheme (67+64=131 LOC) — refined ADI
- **C.5** — MethodOfLinesScheme (62+62=124 LOC) — semi-discrete
- **C.6** — TrBDF2Scheme (152 LOC, header-only template per C++ — must verify) — backward-difference

Each:
- C++ source per filename above
- Java target: `org.jquantlib.methods.finitedifferences.schemes.<SchemeName>`
- Probe + test per scheme: 1D + 2D heat equation convergence on a known analytical solution; tier TIGHT (or LOOSE if scheme is documented-low-accuracy by design)
- Commit: `infra(methods.finitedifferences.schemes): port <SchemeName> (Phase 2l Track C.<n>)`

Sub-commits land sequentially; each lands fast-forward to main before next dispatches.

## L2 — Completion + tag + memory + README + teardown

- `phase2l-completion.md` (Phase 2k shape)
- Tag `jquantlib-phase2l-complete`
- Memory + README per milestone-doc discipline
- Tear down 3 worktrees + delete branches local + remote
