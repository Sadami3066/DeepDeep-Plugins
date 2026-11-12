# DeepDeep Plugin Sample

宿主仓库端到端回归用的测试夹具（不是可上架插件）：一个 `sample_echo` 工具与一个 `sample.main` 页面，用来验证外部插件的安装、启用、停用、升级与卸载。

- 插件 id：`io.github.whywood.deepdeep.pluginsample`
- 入口类：`io.github.whywood.deepdeep.pluginsample.SamplePlugin`
- 构建：`./gradlew verifyPlugin`，产物在 `build/plugin/sample.ddp`
- 签名密钥：`signing/dev-ed25519.pem` 仅供本夹具使用，随仓库提交；不要用它发布任何真实插件

宿主仓库执行 `scripts/plugin-e2e.sh` 时会把本目录构建出的 `sample.ddp` 推到设备并跑 `ExternalPluginE2ETest`。
