# DeepDeep-Plugins

**DeepDeep 外部插件的开发模板、契约文档与上架规则。**

[English](README.en.md) · [插件开发文档](docs/PLUGIN_DEV.md) · [官方插件](https://github.com/Sadami3066/DeepDeep-Official-Plugins) · [插件市场](https://github.com/Sadami3066/deepdeep-market)

## 概览

DeepDeep 是运行在 Android 上的个人智能体。插件用 Kotlin 编写，实现 `plugin-api` 的 `DeepDeepPlugin`，打包成签名后的 `.ddp`，由 App 在自身进程内加载；一个插件可以提供工具、技能、提示词段与 Compose UI 页面。

本仓库面向外部插件作者：从 `template/` 起步，按 [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md) 开发，按 [`MARKET_POLICY.md`](MARKET_POLICY.md) 与 [`PR_REVIEW.md`](PR_REVIEW.md) 发布到 DeepDeep 市场。DeepDeep 官方插件（飞书、企业微信、中山大学 CLI、小米云服务）在 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)，可作为完整参考实现。

> 插件与 App 同进程、同权限运行，没有沙箱，安装即信任。只安装自己信任的插件；第三方工具源请使用 MCP。

## 仓库里有什么

| 路径 | 说明 |
|---|---|
| [`template/`](template/) | 独立 Gradle Android 插件模板：示例工具、技能、提示词段，以及完整的打包、签名、校验任务 |
| [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md) | 插件开发文档：宿主契约、`plugin.json` 清单、`.ddp` 包格式、签名与验签、安装更新、生命周期、扩展面与安全模型 |
| [`MARKET_POLICY.md`](MARKET_POLICY.md) | 市场上架政策：可上架内容、禁止内容、提交要求、签名与下架规则 |
| [`PR_REVIEW.md`](PR_REVIEW.md) | 上架审核清单 |
| [`CHANGELOG.md`](CHANGELOG.md) | 本仓库变更记录 |
| [`LICENSE`](LICENSE) | Apache-2.0 |

## 快速开始

### 环境要求

- JDK 17
- Android SDK：compileSdk 36、minSdk 29；SDK 位置通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir` 指定
- Gradle 9.8.2（模板自带 wrapper）
- 能访问公开 Maven 仓库 `https://sadami3066.github.io/deepdeep-maven`（模板已配置，无需本地发布）

宿主契约 `plugin-api` 与 UI 设计系统 `design` 由该公开 Maven 仓库提供；`design` 由 `plugin-api` 通过 `api()` 传递：

模板已在 `settings.gradle.kts` 中配置好该仓库，通常无需改动：

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

### 1. 复制模板并改名

```bash
cp -R template my-plugin
cd my-plugin
```

然后修改：

- `settings.gradle.kts` 的 `rootProject.name`（决定产物文件名）
- `build.gradle.kts` 的 `namespace` / `applicationId` / `versionName`
- `src/main/assets/plugin.json` 的 `id`（点分小写，如 `com.example.myplugin`）、`version`、`entryClass`、`publisher.name` 与名称/描述
- `src/main/kotlin` 下的包名与入口类名

### 2. 生成签名密钥

```bash
./gradlew generateSigningKey
```

在 `keys/` 下生成 `plugin-signing.pem` 与 `.pub`（私钥权限 600）。`keys/` 已被 gitignore，**私钥绝不提交**，请离线备份；丢失后无法再发布该插件的更新。也可以在 `signPlugin` 时用 `-PpluginSigningKey=/path/to/my-key` 指定其它密钥前缀（PKCS#8 PEM，不带 `.pem`/`.pub` 扩展名）。

### 3. 编写插件

入口类实现 `DeepDeepPlugin`，按需实现 `availability()`、`onEnable()` / `onDisable()` / `onUpgrade()`、`skills()`、`promptSections()` 与 `contributions()`。模板自带：

- 工具 `template_echo`（由 `contributions()` 返回）
- 技能 `template-skill`（从 `assets/skills/template-skill/` 读取 `SKILL.md`，含 `references/format.md`）
- 一段提示词（`promptSections()`）

可扩展的能力、UI 槽位与宿主服务见 [`docs/PLUGIN_DEV.md`](docs/PLUGIN_DEV.md)。

### 4. 构建、签名、验证

```bash
./gradlew assemblePlugin   # build/plugin/<插件名>.unsigned.ddp
./gradlew signPlugin       # build/plugin/<插件名>.ddp，用 keys/ 的 Ed25519 私钥签名
./gradlew verifyPlugin     # 按宿主规则验签，输出 Signature OK
```

- `assemblePlugin` 把 debug APK 重打成 `.ddp`，只保留 `classes*.dex`、`assets/**`、`lib/**`；APK 的 manifest 与资源不参与。
- `signPlugin` 生成 `META-INF/plugin-signature.json`，并打印 `publisher keyId`、整包 sha256 与 entry digest。
- 模板未改名时产物为 `plugin-template.*`；修改 `rootProject.name` 后变为自己的名字。

### 5. 安装到手机

把 `.ddp` 传到手机，打开 DeepDeep 的插件页，选择「导入插件」并选中文件。确认页会展示名称、版本、体积、整包 sha256、发布者名称与 `keyId`、能力声明；首次导入确认后写入信任记录（TOFU）并启用。之后的更新必须由同一把私钥签名，否则按「发布者不匹配」拒绝。

### 6. 发布

- **上架 DeepDeep 市场（所有外部作者）**：按 [`MARKET_POLICY.md`](MARKET_POLICY.md) 准备条目与制品，向 [deepdeep-market](https://github.com/Sadami3066/deepdeep-market) 提交 PR。条目需包含与 `plugin.json` 一致的 id、版本、中英双语名称/简介/描述/标签、`artifact.url` / `sha256` / `size`、`publisherKeyId` 与不超过 128 KB 的 PNG 图标；维护者按 [`PR_REVIEW.md`](PR_REVIEW.md) 审核。
- **官方插件仓库（维护者）**：[DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) 用共享脚本 `gradle/plugin-bundle.gradle.kts` 与 `scripts/check.sh` 构建、签名全部官方插件。官方签名密钥不随仓库分发，外部作者无法以官方 `keyId` 发版。

## 使用方式

### 开发者

- 从 `template/` 复制出独立工程即可开始；多个插件放在同一个 Gradle 工程时，参考官方插件仓的 `settings.gradle.kts` 与根 `gradle/plugin-bundle.gradle.kts`。
- 宿主已提供的库只能 `compileOnly` 依赖：`plugin-api`、经它传递的 `design`、Compose（BOM/UI/foundation/material3）、`kotlinx-coroutines`、`kotlinx-serialization-json`。不要用 `implementation` 引宿主库，否则会把宿主类打进插件包。
- 需要系统能力时，在 `plugin.json` 的 `capabilities` 中声明（如 `linux`、`files`、`network`、`sessions`、`mcp`）；`capabilities` 必须显式给出，空数组也要写。
- 技能、图标与文案都放在 `assets/`；插件不能使用 `res/` 资源，也不能加载原生库。

### 用户

- 在 DeepDeep 插件页的「市场」中浏览并安装已上架插件；或通过「导入插件」手动选择 `.ddp`。
- 安装确认页会展示发布者名称与 `keyId`、包体 sha256 与能力声明；更新必须来自同一发布者。
- 插件与 App 同进程、拥有 App 的全部权限，只安装可信来源。

## 风险

- **签名私钥丢失 = 无法更新**：私钥没有找回机制，丢失后只能换钥以新发布者身份重新上架，已安装用户不会自动升级到新钥签名的版本。请离线备份私钥。
- **发布者身份只看 `keyId`**：`plugin.json` 里的 `publisher.name` 只是展示名，可任意填写；真正身份是签名文件里的 `keyId`。首次安装确认页与市场条目都以 `publisherKeyId` 为准，只安装你认识的 `keyId`。
- **同进程、同权限**：插件与 App 同进程、同 UID、无沙箱，能读写 App 私有数据、反射宿主内部类、起线程、结束进程。`capabilities` 只控制访问器注入，不是权限边界；`requiresPrivilege` 只是提示。
- **第三方依赖与服务**：插件可自带第三方库打进 dex，也可访问网络与第三方服务；这些代码与服务的许可证、安全与合规由插件作者和用户承担。
- **崩溃影响范围**：插件的工具/回调异常会被宿主隔离，但插件在主线程、Compose 槽位或工具执行中导致进程崩溃时，仍会带崩 App。
- **发布前无 ABI 兼容承诺**：`plugin-api` 当前处于发布前开发期（SDK 0.7.0、API level 4），类型与签名可能直接删改而不升 API level；请按最新 SDK 重新编译插件。
- **审核不是安全担保**：市场人工审核只核对上架规则与描述一致性，不保证插件无漏洞、无恶意行为。

## 权益与许可

- 本仓库的模板、文档与构建脚本采用 [Apache-2.0](LICENSE)。可以复制、修改与再分发，但需保留版权与许可声明；用模板开发的插件归作者所有，可自行选择许可证。
- 上架市场的插件必须允许公开再分发，并在条目或仓库中保留 LICENSE 或来源链接，要求见 [`MARKET_POLICY.md`](MARKET_POLICY.md)。
- 插件内使用的第三方库、图标与品牌资产遵循各自许可；Apache-2.0 不包含商标授权，请勿以 DeepDeep 名义误导用户。
- 许可证不构成对插件行为、安全性或可用性的担保。

## 声明与反馈

- 本仓库的模板与文档按现状提供；第三方插件由各自发布者维护，与 DeepDeep 维护者无关。
- 开发问题、文档错误与改进建议请到 [Issues](https://github.com/Sadami3066/DeepDeep-Plugins/issues) 反馈；市场条目相关问题请到 [deepdeep-market](https://github.com/Sadami3066/deepdeep-market)。
- 安全或凭据相关问题请勿公开提交，请通过 GitHub 联系维护者。
