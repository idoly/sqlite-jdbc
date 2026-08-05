# Releasing

Releases are built from Git tags and published through the Sonatype Central Portal. The release workflow builds and tests every native classifier on its target operating system before any publication starts.

## Repository setup

Create a protected GitHub environment named `release` and add these secrets:

- `CENTRAL_USERNAME`: Central Portal user token username
- `CENTRAL_PASSWORD`: Central Portal user token password
- `MAVEN_GPG_PRIVATE_KEY`: ASCII-armored private signing key
- `MAVEN_GPG_PASSPHRASE`: passphrase for the signing key

The `io.github.idoly` namespace must be verified for the Central Portal publishing account. Configure required reviewers on the `release` environment when publication needs manual approval.

## Publish

The repository POM remains on the next `-SNAPSHOT` version. A release tag supplies the immutable release version to every matrix job:

```bash
git tag -s v0.1.0 -m "sqlite-jdbc 0.1.0"
git push origin v0.1.0
```

The workflow performs these steps:

1. Changes the reactor version from `0.1.0-SNAPSHOT` to the tag version in each isolated runner workspace.
2. Builds and tests all seven native classifiers on Linux, Windows, and macOS.
3. Verifies that only the 29 prefixed `sqlitejdbc_*` symbols are exported.
4. Downloads exactly one JAR for every supported platform into the publish job.
5. Builds Java binary, sources, and Javadoc JARs.
6. Signs POMs and artifacts with GPG and uploads one deployment to Central Portal.
7. Waits until Central reports the deployment as published.

A failed platform build prevents the publish job from starting. Central credentials and the GPG key are only exposed to the protected publish job.

## Local release checks

Generate the same Java release attachments without signing or publishing:

```bash
./mvnw --batch-mode --no-transfer-progress clean package \
  -Prelease -DskipTests -Dgpg.skip=true
```

Publishing is intentionally not supported from an arbitrary developer build because a complete release requires native artifacts produced by all target runners.
