# Release signing & publishing

Open Pulse release APKs must always be signed with **the same keystore**. The in-app
updater refuses any APK whose signing certificate differs from the installed app, and
Android itself refuses to update an app with a different key. If the keystore is lost,
existing installs can no longer be updated — keep secure backups of it.

Nothing secret is ever committed: `*.jks`, `*.keystore`, `*.p12` and `keystore.properties`
are listed in `.gitignore`.

## 1. Generate a keystore (once)

```bash
keytool -genkeypair -v \
  -keystore open-pulse-release.jks \
  -alias openpulse \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` asks for a keystore password, a key password and a distinguished name.
Store the `.jks` file and both passwords in a password manager **outside** this repository.

## 2. Configure GitHub repository secrets

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret                      | Value                                                        |
|-----------------------------|--------------------------------------------------------------|
| `ANDROID_KEYSTORE_BASE64`   | Output of `base64 -w0 open-pulse-release.jks` (macOS: `base64 -i open-pulse-release.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password                                            |
| `ANDROID_KEY_ALIAS`         | Key alias (e.g. `openpulse`)                                 |
| `ANDROID_KEY_PASSWORD`      | Key password                                                 |

Delete any temporary base64 file afterwards. For extra protection you can move these
secrets into a GitHub *environment* with required reviewers and add
`environment: <name>` to the `release` job in `.github/workflows/release.yml`.

## 3. Publish a release

Either push a tag:

```bash
git tag v1.1.0
git push origin v1.1.0
```

or run **Actions → Release → Run workflow** and enter the version (`1.1.0`).

The workflow:

1. runs all unit tests,
2. builds `assembleRelease` with `-Popenpulse.versionName=<version>` (the Android
   `versionCode` is derived from it: `major*1_000_000 + minor*10_000 + patch*100 + 99`,
   pre-releases omit the `+99`; minor and patch must be below 100),
3. verifies the signature with `apksigner`,
4. publishes a GitHub Release containing `OpenPulse-v<version>.apk` and
   `OpenPulse-v<version>.apk.sha256`.

The keystore is decoded into the runner's temp directory only for the build and is
deleted afterwards, even if the build fails.

Pre-release versions such as `1.2.0-rc.1` are marked as pre-releases on GitHub, and the
in-app updater ignores them (it reads the latest *stable* release).

## 4. Signing locally (optional)

Create `keystore.properties` in the project root (git-ignored, never commit it):

```properties
storeFile=/absolute/path/to/open-pulse-release.jks
storePassword=<your keystore password>
keyAlias=openpulse
keyPassword=<your key password>
```

Or export environment variables instead of using a file:

```bash
export OPENPULSE_KEYSTORE_PATH=/absolute/path/to/open-pulse-release.jks
export OPENPULSE_KEYSTORE_PASSWORD=...   # read from your password manager
export OPENPULSE_KEY_ALIAS=openpulse
export OPENPULSE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Environment variables take precedence over `keystore.properties`. Without either, the
release build is produced **unsigned** (and a message is logged), which is fine for local
testing but cannot be installed as an update.

## 5. Pointing the updater at a fork

The updater reads `openpulse.updateRepoOwner` and `openpulse.updateRepoName` from
`gradle.properties` (or `-P` flags). Forks should change these and use their own keystore.
