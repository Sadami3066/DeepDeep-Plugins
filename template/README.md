# DeepDeep 插件模板

独立 Gradle Android 工程，用来开发外部 DeepDeep 插件并打包签名成 `.ddp`。复制本目录，改插件 id、入口类与包名，即可构建自己的插件。

## 环境

- JDK 17
- Android SDK：compileSdk 36、minSdk 29
- Gradle 9.8.2（已带 wrapper）
- 依赖 `plugin-api:1.0.0`，来自 <https://sadami3066.github.io/deepdeep-maven>

`local.properties` 写 `sdk.dir=/path/to/Android/Sdk`，或设置 `ANDROID_HOME`。

## 构建

```bash
./gradlew generateSigningKey  # 首次：生成 keys/plugin-signing.pem 与 .pub
./gradlew assemblePlugin      # build/plugin/plugin-template.unsigned.ddp
./gradlew signPlugin          # 用 keys/ 的 Ed25519 私钥签名，产出 build/plugin/plugin-template.ddp
./gradlew verifyPlugin        # 校验签名与清单
```

`keys/` 已被 gitignore，**私钥绝不提交**，请自行备份；私钥丢失就无法再发布同名插件的更新。`signPlugin` 只读取密钥，缺失时直接报错并提示生成方式。也可用 `-PpluginSigningKey=keys/plugin-signing` 指定其它密钥位置（PKCS#8 PEM 文件路径或相对项目路径，不带 `.pem`/`.pub` 扩展名）。

换钥等于换发布者身份：市场会视为新发布者，已安装用户无法用新钥升级（PUBLISHER_MISMATCH）。

把 `.ddp` 通过深探 App 的「插件 → 导入插件」安装即可。

## 改成一个新插件

1. `settings.gradle.kts` 的 `rootProject.name`、`build.gradle.kts` 的 `namespace` / `applicationId`、`versionName`。
2. `src/main/assets/plugin.json`：`id`（点分小写，如 `com.example.myplugin`）、`version`、`entryClass`、`publisher.name`（导入页展示的作者名）、名称与描述（`default` 必填，`zh`/`en` 可选）。
3. `src/main/kotlin/.../TemplatePlugin.kt`：改包名与类名，按需实现 `skills()` / `promptSections()` / `contributions()`。
4. 换发布者前删掉旧 `keys/`，重新 `generateSigningKey` 与 `signPlugin`；`signPlugin` 会把密钥文件计入任务输入，换钥后不会复用旧签名。

## 能力与依赖

- 插件只能依赖 Android SDK、`plugin-api` 和宿主提供的 `compileOnly` 库（Compose BOM/UI/foundation/material3、coroutines、kotlinx-serialization-json）。**不要用 `implementation` 引宿主库**，否则会把宿主类打进包。
- 需要系统能力时在 `plugin.json` 的 `capabilities` 声明（如 `linux`、`files`、`network`、`ui`）；清单里的 `capabilities` 必须显式给出，空数组也要写。
- 版本对齐：Kotlin 2.4.21、Compose BOM 2026.03.00、AGP 9.4.1、JVM 17。宿主升级后同步修改本工程。

## 产物结构

`.ddp` 就是重打包后的 APK，只保留插件真正需要的部分：

- `classes*.dex`
- `assets/**`（含 `plugin.json` 与图标、技能 Markdown）
- `lib/**`（如有原生库）

宿主不读取插件 `res/`，界面请用 Compose 或声明的 UI 页面实现。

## 目录

| 路径 | 作用 |
|---|---|
| `build.gradle.kts` | Android 配置与 `generateSigningKey` / `assemblePlugin` / `signPlugin` / `verifyPlugin` 任务（含 Ed25519 与 canonical entry digest 实现） |
| `src/main/assets/plugin.json` | 插件清单 |
| `src/main/assets/skills/template-skill/` | 示例技能：`SKILL.md` + `references/format.md` |
| `src/main/kotlin/.../TemplatePlugin.kt` | 参考实现：`availability`、技能、提示词段、`template_echo` 工具 |

更完整的契约与签名规范见仓库 `docs/PLUGIN_DEV.md`；上架与分发规则见 `MARKET_POLICY.md`。
