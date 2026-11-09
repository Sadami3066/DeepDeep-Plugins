# DeepDeep 插件开发

DeepDeep 插件的公开开发资料：模板工程、开发文档与上架规则。

官方插件（飞书、企业微信、中山大学 CLI、小米云服务）已拆到独立仓库
[DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)，可作为完整参考实现。

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
| `template/` | 独立 Gradle 插件模板，自带打包、签名与校验任务，复制即可开始 |
| `docs/PLUGIN_DEV.md` | 插件开发文档：契约、清单、`.ddp` 格式、签名、生命周期、扩展面与安全模型 |
| `MARKET_POLICY.md` | 插件上架与分发规则 |
| `PR_REVIEW.md` | 上架审核清单 |
| `CHANGELOG.md` | 本仓库变更记录 |
| `LICENSE` | Apache-2.0 |

## 快速开始

```bash
cp -R template my-plugin && cd my-plugin
# 改 settings.gradle.kts 的 rootProject.name、build.gradle.kts 的 namespace / applicationId / versionName
# 改 src/main/assets/plugin.json 的 id / version / entryClass / publisher.name
# 改 src/main/kotlin 下包名与入口类
# 删掉模板 keys/，运行 ./gradlew generateSigningKey 生成自己的签名密钥

./gradlew assemblePlugin   # build/plugin/<插件名>.unsigned.ddp
./gradlew signPlugin       # build/plugin/<插件名>.ddp
./gradlew verifyPlugin     # 校验签名与清单
```

模板细节见 `template/README.md`，完整契约与排错见 `docs/PLUGIN_DEV.md`。

## 发布

插件上架与市场规则见 [MARKET_POLICY.md](MARKET_POLICY.md)；契约变更记录见 [CHANGELOG.md](CHANGELOG.md)。
