# DeepDeep 插件

DeepDeep 外部插件的开发仓库：官方插件、模板与签名打包脚本。

宿主契约 `plugin-api` 与 UI 设计系统 `design` 由公开 Maven 仓库提供：

```kotlin
repositories {
    maven { url = uri("https://sadami3066.github.io/deepdeep-maven") }
}

dependencies {
    compileOnly("io.github.sadami3066.deepdeep:plugin-api:0.7.0")
}
```

`design` 由 `plugin-api` 通过 `api()` 传递，插件页面直接使用其中的 Dd 组件。

## 目录

| 目录 | 说明 |
|---|---|
| `template/` | 新插件模板，复制后改 applicationId 与 `plugin.json` 即可开始 |
| `gradle/plugin-bundle.gradle.kts` | 共享打包脚本：`assemblePlugin` 把 debug APK 重打成 `.ddp`，`signPlugin` 用 Ed25519 签名，`verifyPlugin` 校验签名 |
| `feishu` `wecom` `jwxt` `micloud` | DeepDeep 官方插件（`jwxt` 现为「中山大学 CLI」）|
| `keys/` | 本机开发签名密钥（不入库） |

## 构建与验证

```bash
./scripts/gradlew-locked :feishu:verifyPlugin          # 单个插件
./scripts/check.sh                      # 全部插件打包签名
```

产物在 `<module>/build/plugin/<module>.ddp`。安装：把 `.ddp` 传到手机，在 DeepDeep 插件页「导入插件」选择文件；首次安装会展示发布者与能力清单并要求确认。

## 发布

插件上架与市场规则见 [MARKET_POLICY.md](MARKET_POLICY.md)；契约变更记录见 [CHANGELOG.md](CHANGELOG.md)。
