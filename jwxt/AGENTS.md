# jwxt · 中山大学教务插件

外部 `.ddp` 插件模块：把 Node.js 教务 CLI 与脚本随包分发，安装到 proot 后由技能与提示词驱动查询课表/成绩/考试等。打包/签名由根 `gradle/plugin-bundle.gradle.kts` 提供。

## 文件

| 文件 | 职责 |
|---|---|
| `src/main/assets/plugin.json` | 清单：id/version/apiLevel=4/capabilities/entryClass/provides（skills `deepdeep-jwxt`、page `main`） |
| `src/main/assets/jwxt/**` | Node.js 教务 CLI（bin 与 src 脚本），安装时从 `host.assetsDir` 拷入 proot |
| `src/main/kotlin/.../JwxtCli.kt` | 安装路径、Node 检查、登录/查询命令与输出解析 |
| `src/main/kotlin/.../JwxtStatus.kt` | `dataDir/status.json`：安装状态、登录账号 |
| `src/main/kotlin/.../JwxtPlugin.kt` | `DeepDeepPlugin` 入口：availability / skills / promptSections / ui |
| `src/main/kotlin/.../JwxtPage.kt` | 插件自绘页：学号/密码表单、保存并登录、安装与登录状态 |
| `src/main/kotlin/.../JwxtContent.kt` | zh/en 文案与技能 markdown |

## 要点

- 凭证存 `host.secrets()`（netid/password），页面只回显 netid，密码不回显。
- 安装：检查 Node 版本 → 从 `host.assetsDir/jwxt/**` 复制脚本到 proot（`/root/projects/jwxt` 与 `/usr/local/bin/jwxt`）。
- 页面动作结束调用 `host.requestContributionsRefresh()`；页面用 `:design` 的 Dd 组件（`DdGroupCard`/`DdListRow`/`DdPrimaryButton`），内容直接挂在 `PluginPage.content`，不用槽位；安装/登录失败的错误文案带命令输出尾部。
