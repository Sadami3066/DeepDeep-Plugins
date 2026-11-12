# Changelog

## 2026-11-12

- feat: 模板与示例迁移到 `plugin-api:1.0.0`（API level 6、`ui.apiLevel` 2），清单改为 `apiLevel` 6 / `minHostVersion` 1.0.4，改用顶层工厂与接口；中英文 README 的 SDK 版本表同步 [依赖] [模板] [破坏性]
- feat: 新增 `sample/` 测试夹具（`sample_echo` 工具 + `sample.main` 页面，随仓库提交仅用于开发的 Ed25519 签名密钥），供宿主仓库 `scripts/plugin-e2e.sh` 在设备上回归外部插件的安装、启用、停用、升级与卸载 [工具] [模板]
- chore: 模板升到 `plugin-api:0.8.1`（消费端 R8 keep 规则补齐），模板清单 `minHostVersion` 改为 `1.0.1`；README 与开发文档的版本表同步 [依赖] [文档]

## 2026-11-10

- feat(api): 插件 API 升到 v2（`PLUGIN_API_LEVEL` / `BASELINE_API_LEVEL` 4→5）；删除 `host.privilege()` / `PrivilegeService` 与清单字段 `requiresPrivilege`；`host.linux()` / `host.files()` 语义不变，执行身份由用户选择的访问档位（LOCAL/ADB/ROOT）决定 [api] [破坏性]
- docs: 同步更新 `docs/PLUGIN_DEV.md` 的清单字段、capabilities 列表、宿主服务、ABI 与安全模型；模板 `plugin.json` 的 `apiLevel` 改为 5；中英文 README 的 API level 与特权说明同步 [文档] [模板]

## 2026-11-09

- refactor: 官方插件拆到独立仓库 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)（feishu 1.0.1 / wecom 1.0.1 / sysu 1.0.0 / micloud 1.0.2）；`jwxt` 全面更名 `sysu`（目录、Kotlin 包与类、插件 id `io.github.whywood.deepdeep.plugin.sysu`）；本仓库保留模板、开发文档与上架规则 [拆分] [破坏性]

- feat(jwxt): 插件对外更名为「中山大学 CLI」，内置 `sysu` 多站点 CLI（教务 / 在线教学 / 体育场馆 / 网费 / 统一认证），技能 id 改为 `deepdeep-sysu`；安装路径迁移到 `/root/projects/sysu` 与 `/usr/local/bin/sysu`，清理时兼容删除旧 `jwxt` 路径 [jwxt] [破坏性]

- feat: 新增公开插件模板 `template/`：独立 Gradle 工程，`plugin-api:0.7.0` 从公开 Maven 解析，自带 `assemblePlugin` / `signPlugin` / `verifyPlugin` 与 Ed25519 签名实现，附示例工具、技能与提示词段；本地构建、签名、校验全部通过 [工具] [模板]
- docs: 新增公开文档 `docs/PLUGIN_DEV.md`（契约、清单、`.ddp` 格式、签名算法、生命周期、工具/技能/UI/服务、ABI 与安全模型）；新增 `MARKET_POLICY.md` 与 `PR_REVIEW` 上架规则说明；README 改为公开 Maven 用法 [文档]
- chore: 依赖坐标切换到 `io.github.sadami3066.deepdeep`（`plugin-api:0.7.0`、`design:0.6.0`），仓库改从 `https://sadami3066.github.io/deepdeep-maven` 解析；四个插件产物哈希不变，市场条目无需重发 [构建]
- docs: 插件与接口 AGENTS 中的 mavenLocal 发布说明改为公开 Maven 发布流程 [文档]

## 历史版本

此前插件（飞书、企业微信、中山大学教务、小米云服务）的版本变更记录在各插件目录与市场 registry 中；本文件从公开仓库启用时开始记录。
