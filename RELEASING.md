# Releasing JQuantLib to Maven Central

Runbook for publishing `cc.sosonline.jquantlib:jquantlib:<version>` to **Maven Central** via
the [Central Portal](https://central.sonatype.com) (the system that replaced OSSRH).

Only the `jquantlib` library module is published to Central. The reactor parent and the
`-contrib` / `-helpers` / `-samples` / `-showcase` modules continue to deploy to the
internal Nexus only (see their `nexus-staging` setup); they are **not** part of the
Central release.

---

## Status — what's already wired (commit `f6521e0f`)

Items 1–4 of the release-readiness audit are done:

- **`-Prelease` profile** (in `jquantlib/pom.xml`) produces the full Central bundle:
  - `maven-source-plugin` 3.3.1 → `jquantlib-<ver>-sources.jar`
  - `maven-javadoc-plugin` 3.12.0 (`doclint=none`, `failOnError=false`) → `jquantlib-<ver>-javadoc.jar`
    *(doclint is disabled because the ported codebase carries hundreds of legacy
    javadoc violations; Central accepts a javadoc jar with warnings, it just has to build)*
  - `maven-gpg-plugin` 3.2.7 → `.asc` signature for every artifact
- **POM metadata** (Central-required): `name`, `description`, `url`, `inceptionYear`,
  `licenses` (BSD-3-Clause), `developers`, `scm`.
- **Root `LICENSE`** file (BSD-3-Clause).
- **Version `1.42.1` is a release** — no `-SNAPSHOT` (Central rejects snapshots).

Verified locally:

```bash
mvn -pl jquantlib -Prelease -DskipTests clean package
# → BUILD SUCCESS; produces main + -sources + -javadoc jars
```

---

## What's left — requires your decisions / secrets

### 1. groupId namespace — DONE ✅

The reactor groupId is **`cc.sosonline.jquantlib`** — a sub-namespace of **`cc.sosonline`**,
verified on the Central Portal via the **`sosonline.cc`** domain. All six POMs were updated
(the parent groupId, every module's `<parent>` reference, and all inter-module dependency
coordinates), so the published library coordinate is
**`cc.sosonline.jquantlib:jquantlib:1.42.1`**.

Only the Maven **groupId** changed — the Java packages remain `org.jquantlib.*` (renaming
those is an unrelated, much larger change that Central does not require).

### 2. Central Portal account + publishing token

1. Sign in at <https://central.sonatype.com> (GitHub login works).
2. Register and **verify your namespace** from step 1.
3. **Account → Generate User Token.**
4. Add the token to `~/.m2/settings.xml`:

   ```xml
   <server>
     <id>central</id>
     <username>TOKEN_USERNAME</username>
     <password>TOKEN_PASSWORD</password>
   </server>
   ```

### 3. GPG signing key

```bash
gpg --gen-key                                       # RSA 4096 recommended
gpg --list-secret-keys --keyid-format=long          # note the long KEYID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>   # publish the PUBLIC key
```

Provide the passphrase to the build via `gpg-agent`, or `-Dgpg.passphrase=…`, or a
`<server>` entry. If you hold multiple keys, set `-Dgpg.keyname=<KEYID>`. On modern GPG in a
non-interactive shell you may also need `-Dgpg.gpgArguments=--pinentry-mode,loopback` (or the
equivalent `<gpgArguments>` in the plugin config).

### 4. Central publishing plugin — DONE ✅

The `central-publishing-maven-plugin` (0.7.0) is wired into the `release` profile in
`jquantlib/pom.xml`:

```xml
<plugin>
  <groupId>org.sonatype.central</groupId>
  <artifactId>central-publishing-maven-plugin</artifactId>
  <version>0.7.0</version>
  <extensions>true</extensions>
  <configuration>
    <publishingServerId>central</publishingServerId>
    <autoPublish>false</autoPublish>  <!-- true = release without the manual Portal click -->
  </configuration>
</plugin>
```

Under `-Prelease` the internal-Nexus deploy is also disabled — the same profile sets
`<skipNexusStagingDeployMojo>true</skipNexusStagingDeployMojo>` on `nexus-staging` — so a
release publishes to Central **only**. A plain `mvn deploy` (no `-Prelease`) still targets the
internal Nexus as before. Verified: `mvn -pl jquantlib -Prelease validate` resolves and loads
the plugin (BUILD SUCCESS); the actual upload just needs the `central` token + GPG key (below).

---

## Publishing

Run from a real terminal (GPG needs a TTY for the passphrase prompt):

```bash
cd /Users/josemoya/Projects/IdeaProjects/jquantlib
export GPG_TTY=$(tty)          # REQUIRED — without it gpg dies with "Inappropriate ioctl for device"
rm -rf jquantlib/target        # instead of `clean` (the parent's ancient clean-plugin 2.1 is flaky)

# Dry run — build, sign, inspect the bundle locally (no upload):
mvn -pl jquantlib -Prelease verify

# Publish to the Central Portal:
mvn -pl jquantlib -Prelease deploy
```

With `autoPublish=false` the bundle is **staged** on the Portal for you to review and click
**Publish**; with `true` it releases automatically once validation passes. After release,
artifacts appear on Central within minutes and sync to `search.maven.org` after indexing.

### Gotchas hit on the first release (1.42.1, 2026-07-03) — all fixed in the POM

1. **`gpg: signing failed: Inappropriate ioctl for device`** — gpg couldn't find the terminal
   for the passphrase prompt. Fix: `export GPG_TTY=$(tty)` in the same shell as `mvn`
   (or install `pinentry-mac`).
2. **Portal rejected the POM** ("Dependency version information is missing", "Failed to get
   coordinates") — the published pom inherited groupId/versions from the *unpublished* reactor
   parent. Fix: `flatten-maven-plugin` (`flattenMode=ossrh`) in the release profile publishes a
   self-contained pom. (Also: junit was missing `<scope>test</scope>` and would have leaked
   into consumers' compile classpaths.)
3. **`Unrecognized field "warnings"` crash after a successful upload** — the Portal API added a
   `warnings` response field that `central-publishing-maven-plugin` 0.7.0 can't parse. The
   deployment itself was fine (VALIDATED on the Portal). Fix: plugin bumped to 0.11.0.

---

## Pre-flight checklist

- [x] Namespace verified — `cc.sosonline` (via the `sosonline.cc` domain)
- [x] `central` server token in `~/.m2/settings.xml`
- [x] GPG key generated and **public key published** to a keyserver
      (RSA 4096, `AE9F9B287616D20911CC9DFB6910857807F21D2C`, keyserver.ubuntu.com)
- [x] `central-publishing-maven-plugin` added to the `release` profile
- [x] `mvn -pl jquantlib -Prelease verify` produces jars + `.asc` + checksums
- [x] `mvn -pl jquantlib -Prelease deploy` → bundle accepted by the Portal
- [x] clicked **Publish** on the Portal

**First release published: `cc.sosonline.jquantlib:jquantlib:1.42.1` — deployment
`7f39706e-491e-4112-be75-87d706e41bac`, VALIDATED → Published 2026-07-03.**

---

## After publishing — Portal indicators (expected behavior for every new release)

The component page on central.sonatype.com shows two indicators that look alarming right
after a publish but are **pending-by-design for new artifacts**. Neither affects Maven
Central availability — the artifact is live and resolvable the moment publishing completes
(clean-room verified for 1.42.1).

- **"Policy Non-Compliant"** — Sonatype's reference *Integrity-Rating policy* treats a
  Release Integrity rating of **Pending** as a violation, and every newly published
  component starts at Pending until their ML-based release-integrity evaluation completes.
  It flips to compliant on its own (hours to a few days; first-time publishers take
  longest). Only practical effect meanwhile: organizations running Sonatype
  Firewall/Lifecycle in policy-compliant-selection mode defer auto-adopting the version.
  If it is still non-compliant after ~a week, or the chip's detail view names a policy
  other than Integrity-Rating, investigate / contact Central support.

- **"Developer Trust Score" unavailable** — the DTS (0–100) is computed from five signals:
  Security, Popularity, Age, Release Stability, Dependency Risk. It appears only after
  Sonatype's data pipeline evaluates the component and some download history accrues
  (days; the Portal API shows `qualityScore: null` until then). Nothing to submit or
  configure. Long-term levers: keep the runtime dependency tree lean (currently just
  `slf4j-api` — excellent), patch promptly if a CVE ever lands, publish stable releases
  (no publish-then-hotfix churn), and keep versions tracking upstream QuantLib releases
  so the component stays fresh.

References: help.sonatype.com → *Release Integrity*, *Policy Constraints*;
central.sonatype.org → *Sonatype Safety Rating FAQ*.
