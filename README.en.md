# DeepDeep-Plugins

**Templates, contract documentation and marketplace rules for DeepDeep external plugins.**

[简体中文](README.md) · [Plugin development guide](docs/PLUGIN_DEV.md) · [Official plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) · [Marketplace](https://github.com/Sadami3066/deepdeep-market)

## Overview

DeepDeep is a personal AI agent on Android. Plugins are written in Kotlin, implement `DeepDeepPlugin` from `plugin-api`, and ship as signed `.ddp` bundles that the app loads in its own process. A plugin can contribute tools, skills, prompt sections and Compose UI pages.

This repository is for external plugin authors: start from `template/`, develop against [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md), and publish through deepdeep-market following [`MARKET_POLICY.md`](MARKET_POLICY.md) and [`PR_REVIEW.md`](PR_REVIEW.md). Official plugins (Feishu, WeCom, SYSU CLI, Mi Cloud) live in [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) and serve as complete reference implementations.

> Plugins run in the same process and with the same permissions as the app. There is no sandbox, and installing a plugin means trusting it. Only install plugins you trust; use MCP for third-party tool sources.

## What is in this repository

| Path | Description |
|---|---|
| [`template/`](template/) | Standalone Gradle Android plugin template: sample tool, skill and prompt section, plus the full build, signing and verification tasks |
| [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md) | Plugin development guide: host contract, `plugin.json` manifest, `.ddp` package format, signing and verification, install/update, lifecycle, extension points and security model |
| [`MARKET_POLICY.md`](MARKET_POLICY.md) | Marketplace policy: accepted content, prohibited content, submission requirements, signing and takedown rules |
| [`PR_REVIEW.md`](PR_REVIEW.md) | Marketplace review checklist |
| [`CHANGELOG.md`](CHANGELOG.md) | Repository changelog |
| [`LICENSE`](LICENSE) | Apache-2.0 |

## Quick start

### Requirements

- JDK 17
- Android SDK: compileSdk 36, minSdk 29; point to it with `ANDROID_HOME` / `ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`
- Gradle 9.8.2 (wrapper included in the template)
- Network access to the public Maven repository `https://sadami3066.github.io/deepdeep-maven` (already configured in the template; no local publishing needed)

The host contract `plugin-api` and the `design` UI system come from that public Maven repository; `design` is exposed transitively through `api()`:

The template already configures this repository in `settings.gradle.kts`; normally you do not need to change it:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://sadami3066.github.io/deepdeep-maven")
            content { includeGroup("io.github.sadami3066.deepdeep") }
        }
        google()
        mavenCentral()
    }
}
```

```kotlin
dependencies {
    compileOnly("io.github.sadami3066.deepdeep:plugin-api:0.7.0")
}
```

### 1. Copy the template and rename it

```bash
cp -R template my-plugin
cd my-plugin
```

Then update:

- `rootProject.name` in `settings.gradle.kts` (this determines artifact names)
- `namespace` / `applicationId` / `versionName` in `build.gradle.kts`
- `id` (dotted lowercase, e.g. `com.example.myplugin`), `version`, `entryClass`, `publisher.name`, name and description in `src/main/assets/plugin.json`
- the package name and entry class under `src/main/kotlin`

### 2. Generate signing keys

```bash
./gradlew generateSigningKey
```

This creates `keys/plugin-signing.pem` and `.pub` (private key mode 600). `keys/` is gitignored — **never commit the private key**. Back it up offline: if you lose it, you can no longer publish updates for this plugin. To keep keys elsewhere, pass `-PpluginSigningKey=/path/to/my-key` to `signPlugin` (PKCS#8 PEM path prefix, without the `.pem`/`.pub` extension).

### 3. Write the plugin

The entry class implements `DeepDeepPlugin` and can override `availability()`, `onEnable()` / `onDisable()` / `onUpgrade()`, `skills()`, `promptSections()` and `contributions()`. The template already includes:

- a tool, `template_echo` (returned from `contributions()`)
- a skill, `template-skill` (read from `assets/skills/template-skill/`, with `references/format.md`)
- a prompt section (from `promptSections()`)

See [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md) for the full set of extension points, UI slots and host services.

### 4. Build, sign and verify

```bash
./gradlew assemblePlugin   # build/plugin/<name>.unsigned.ddp
./gradlew signPlugin       # build/plugin/<name>.ddp, signed with the Ed25519 key in keys/
./gradlew verifyPlugin     # verify against host rules; prints Signature OK
```

- `assemblePlugin` repacks the debug APK into a `.ddp`, keeping only `classes*.dex`, `assets/**` and `lib/**`; the APK manifest and resources are not used.
- `signPlugin` writes `META-INF/plugin-signature.json` and prints the publisher `keyId`, bundle sha256 and entry digest.
- With the template's default name the artifacts are `plugin-template.*`; change `rootProject.name` to use your own.

### 5. Install on a phone

Transfer the `.ddp` to the phone, open DeepDeep's plugin page, choose the import action and select the file. The confirmation page shows the name, version, size, bundle sha256, publisher name and `keyId`, and declared capabilities. After the first import the host records trust on first use (TOFU) and enables the plugin. Future updates must be signed with the same private key, otherwise they are rejected as a publisher mismatch.

### 6. Publish

- **DeepDeep Marketplace (all external authors)**: prepare the entry and artifact according to [`MARKET_POLICY.md`](MARKET_POLICY.md) and open a PR against [deepdeep-market](https://github.com/Sadami3066/deepdeep-market). An entry contains the plugin id (matching `plugin.json`), version, bilingual name/summary/description/tags, `artifact.url` / `sha256` / `size`, `publisherKeyId` and a PNG icon no larger than 128 KB. Maintainers review entries against [`PR_REVIEW.md`](PR_REVIEW.md).
- **Official plugins repository (maintainers)**: [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) builds and signs all official plugins with the shared `gradle/plugin-bundle.gradle.kts` and `scripts/check.sh`. The official signing key is not distributed with the repository, so external authors cannot publish under the official `keyId`.

## Usage

### For developers

- Copy `template/` into its own project and start there. When several plugins share one Gradle project, follow the official repository's `settings.gradle.kts` and root `gradle/plugin-bundle.gradle.kts`.
- Libraries provided by the host must be `compileOnly`: `plugin-api`, the transitive `design`, Compose (BOM/UI/foundation/material3), `kotlinx-coroutines` and `kotlinx-serialization-json`. Do not use `implementation` for host libraries, or host classes will be bundled into your plugin.
- Declare the host services you need in `plugin.json` under `capabilities` (e.g. `linux`, `files`, `network`, `sessions`, `mcp`). `capabilities` is required; write an empty array if you need none.
- Skills, icons and copy live under `assets/`. Plugins cannot use `res/` resources and cannot load native libraries.

### For users

- Browse and install published plugins from the Marketplace in DeepDeep's plugin page, or import a `.ddp` file manually.
- The confirmation page shows the publisher name and `keyId`, bundle sha256 and declared capabilities; updates must come from the same publisher.
- Plugins run in the app's process with the app's full permissions. Only install what you trust.

## Risks

- **Losing the signing key means no more updates**: there is no key recovery. You would have to switch keys and republish as a new publisher, and existing users will not automatically upgrade to builds signed with the new key. Back up the private key offline.
- **Publisher identity is the `keyId` alone**: `publisher.name` in `plugin.json` is a display name that anyone can set. The real identity is the `keyId` in the signature file. Both the first-install confirmation page and marketplace entries rely on `publisherKeyId`; only install `keyId`s you recognise.
- **Same process, same permissions**: plugins share the app's process and UID with no sandbox. They can read the app's private data, reflect into host internals, start threads and kill the process. `capabilities` only controls accessor injection, not a permission boundary; `requiresPrivilege` is a hint.
- **Third-party dependencies and services**: plugins may bundle third-party libraries into the dex and may call networks or third-party services. Their licenses, security and compliance are the responsibility of the plugin author and the user.
- **Blast radius of crashes**: tool/callback exceptions are isolated by the host, but a crash caused by a plugin on the main thread, in a Compose slot or during tool execution still takes down the app.
- **No ABI compatibility promise before release**: `plugin-api` is still pre-release (SDK 0.7.0, API level 4); types and signatures may change without raising the API level. Recompile plugins against the latest SDK.
- **Review is not a security guarantee**: marketplace review checks submission rules and description consistency; it does not guarantee that a plugin is free of vulnerabilities or malicious behaviour.

## Licensing

- The template, documentation and build scripts in this repository are licensed under [Apache-2.0](LICENSE). You may copy, modify and redistribute them provided you keep the copyright and license notices. Plugins built from the template belong to their authors, who may choose their own license.
- Plugins submitted to the marketplace must be redistributable publicly and must keep a LICENSE or source link in the entry or repository; see [`MARKET_POLICY.md`](MARKET_POLICY.md).
- Third-party libraries, icons and brand assets used by a plugin remain under their respective licenses. Apache-2.0 does not grant trademark rights; do not use the DeepDeep name in a misleading way.
- The license does not warrant a plugin's behaviour, security or availability.

## Support

- The template and documentation here are provided as-is. Third-party plugins are maintained by their publishers and are not the responsibility of the DeepDeep maintainers.
- Report development issues, documentation errors and suggestions via [Issues](https://github.com/Sadami3066/DeepDeep-Plugins/issues); for marketplace entry issues, use [deepdeep-market](https://github.com/Sadami3066/deepdeep-market).
- Please do not report security or credential issues publicly; contact the maintainers through GitHub.
