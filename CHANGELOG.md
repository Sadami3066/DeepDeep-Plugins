# Changelog

## 2026-11-09

- refactor: 官方插件拆到独立仓库 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)（feishu 1.0.1 / wecom 1.0.1 / sysu 1.0.0 / micloud 1.0.2）；`jwxt` 全面更名 `sysu`（目录、Kotlin 包与类、插件 id `io.github.whywood.deepdeep.plugin.sysu`）；本仓库保留模板、开发文档与上架规则 [拆分] [破坏性]

- feat(jwxt): 插件对外更名为「中山大学 CLI」，内置 `sysu` 多站点 CLI（教务 / 在线教学 / 体育场馆 / 网费 / 统一认证），技能 id 改为 `deepdeep-sysu`；安装路径迁移到 `/root/projects/sysu` 与 `/usr/local/bin/sysu`，清理时兼容删除旧 `jwxt` 路径 [jwxt] [破坏性]

- feat: 新增公开插件模板 `template/`：独立 Gradle 工程，`plugin-api:0.7.0` 从公开 Maven 解析，自带 `assemblePlugin` / `signPlugin` / `verifyPlugin` 与 Ed25519 签名实现，附示例工具、技能与提示词段；本地构建、签名、校验全部通过 [工具] [模板]
- docs: 新增公开文档 `docs/PLUGIN_DEV.md`（契约、清单、`.ddp` 格式、签名算法、生命周期、工具/技能/UI/服务、ABI 与安全模型）；新增 `MARKET_POLICY.md` 与 `PR_REVIEW` 上架规则说明；README 改为公开 Maven 用法 [文档]
- chore: 依赖坐标切换到 `io.github.sadami3066.deepdeep`（`plugin-api:0.7.0`、`design:0.6.0`），仓库改从 `https://sadami3066.github.io/deepdeep-maven` 解析；四个插件产物哈希不变，市场条目无需重发 [构建]
- docs: 插件与接口 AGENTS 中的 mavenLocal 发布说明改为公开 Maven 发布流程 [文档]

## 历史版本

此前插件（飞书、企业微信、中山大学教务、小米云服务）的版本变更记录在各插件目录与市场 registry 中；本文件从公开仓库启用时开始记录。
