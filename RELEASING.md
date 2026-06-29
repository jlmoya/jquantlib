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

### 4. Add the Central publishing plugin

Your current `deploy` goes to the **internal Nexus** via `nexus-staging`. To upload to
**Central** instead, add this to the `release` profile's `<build><plugins>` in
`jquantlib/pom.xml` (check for the latest plugin version):

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

Because the parent disables the default `maven-deploy-plugin` (`skip=true`) and the
`jquantlib` module wires `nexus-staging` for the internal Nexus, the cleanest path is to run
the Central deploy **scoped to the `jquantlib` module with `-Prelease`**, and keep the
internal `mvn deploy` separate. (If both deploy mechanisms fire in one run, set
`-Dmaven.deploy.skip=true` / disable the staging execution so only the Central plugin uploads.)

---

## Publishing

```bash
# Dry run — build, sign, and inspect the bundle locally (needs the GPG key):
mvn -pl jquantlib -Prelease clean verify

# Publish to the Central Portal:
mvn -pl jquantlib -Prelease clean deploy
```

With `autoPublish=false` the bundle is **staged** on the Portal for you to review and click
**Publish**; with `true` it releases automatically once validation passes. After release,
artifacts appear on Central and sync to `search.maven.org` (can take a little while).

---

## Pre-flight checklist

- [ ] Namespace chosen and **verified** on the Central Portal
- [ ] `central` server token in `~/.m2/settings.xml`
- [ ] GPG key generated and **public key published** to a keyserver
- [ ] `central-publishing-maven-plugin` added to the `release` profile
- [ ] `mvn -pl jquantlib -Prelease clean verify` produces jars + `.asc` + checksums
- [ ] `mvn -pl jquantlib -Prelease clean deploy` → bundle accepted by the Portal
- [ ] (if `autoPublish=false`) clicked **Publish** on the Portal
