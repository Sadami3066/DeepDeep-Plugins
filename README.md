# DeepDeep 插件

DeepDeep 外部插件的开发仓库：官方插件与签名打包脚本。宿主 App 仓库在 `../app`。

## 前置

宿主契约 `plugin-api` 与 UI 设计系统 `design` 发布到 mavenLocal：

```bash
cd ../app && ./scripts/gradlew-locked :design:publishToMavenLocal :plugin-api:publishToMavenLocal
```

`design` 与 App 同源，插件页面直接使用其中的 Dd 组件（由 `plugin-api` 通过 `api()` 传递）。改了任意一个后重新发布一次。宿主版本与 SDK 版本对齐见 `../app/plugin-api/AGENTS.md`。

## 目录

| 目录 | 说明 |
|---|---|
| `gradle/plugin-bundle.gradle.kts` | 共享打包脚本：`assemblePlugin` 把 debug APK 重打成 `.ddp`，`signPlugin` 用 Ed25519 签名，`verifyPlugin` 校验签名 |
| `feishu` `wecom` `jwxt` `micloud` | DeepDeep 官方插件 |
| `keys/` | 本机开发签名密钥（不入库） |

## 构建与验证

```bash
./scripts/gradlew-locked :feishu:verifyPlugin          # 单个插件
./scripts/check.sh                      # 全部插件打包签名
```

产物在 `<module>/build/plugin/<module>.ddp`。安装：把 `.ddp` 传到手机，在 DeepDeep 插件页「导入插件」选择文件；首次安装会展示发布者与能力清单并要求确认。

## 新增插件

参考现有插件的 `build.gradle.kts`、`plugin.json` 与 Kotlin 入口，按相同结构新增模块，并在 `settings.gradle.kts` 的 include 里注册。
