# DeepDeep 插件开发

面向外部插件作者的开发文档：工程搭建、契约、打包签名、安装更新与全部扩展面。官方插件与模板分别在 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins) 和 [DeepDeep-Plugins](https://github.com/Sadami3066/DeepDeep-Plugins)；宿主仓库只保留契约与真机回归脚本。产品能力见 [USER_GUIDE.md](USER_GUIDE.md)，构建与模块见 [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)。

DeepDeep 插件是 **Kotlin 代码插件**：实现 `plugin-api` 的 `DeepDeepPlugin`，打包成 `.ddp`，由宿主在 App 进程内加载。插件只从 `.ddp` 安装，不存在随包编译的插件。

**安全前提**：插件与 App 同进程、同 UID、没有沙箱，安装即信任——插件代码等同 App 代码，能读 App 私有数据、反射宿主内部、让 App 崩溃。只安装自己信任的插件；第三方工具源请走 MCP。

## 快速开始

`plugin-api` 是唯一契约工件（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）：当前 `1.2.0`（以 `deepdeep-maven` 最新发布为准），其传递依赖为 `design 0.7.0`，`design 0.8.0`/`0.9.0` 单独发布；`PLUGIN_API_LEVEL = 6`、`BASELINE_API_LEVEL = 6`、`MIN_SUPPORTED_API_LEVEL = 6`、`HOST_UI_API_LEVEL = 2`。

`plugin-api 1.2.0` 是 level 6 内的纯加法：已发布、只用 1.0.x/1.1.x 能力的插件不需要重新编译；使用 1.2.0 新能力的插件要写 `minHostVersion: "1.0.8"`（宿主 1.0.8+）。契约兼容承诺见 `plugin-api/COMPATIBILITY.md`：同一 `apiLevel` 内只做二进制兼容的加法，删除/改名/改签名/改语义必须升 `apiLevel`；宿主拒绝 `apiLevel` 高于自己的插件，1.0.4 起不再接受 level 5。

插件工程从 [DeepDeep-Plugins](https://github.com/Sadami3066/DeepDeep-Plugins) 的 `template/` 起步：独立 Gradle Android 工程，包含一个示例工具、一个示例技能、一段提示词与完整的 `.ddp` 打包/签名/验签实现，复制或改名即起点。官方插件实现见 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)。

前置条件：

- JDK 17+ 与 Android SDK；SDK 位置通过 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 环境变量或工程内 `local.properties` 的 `sdk.dir` 指定。
- 插件依赖公开 Maven 仓库 `https://sadami3066.github.io/deepdeep-maven`（group `io.github.sadami3066.deepdeep`，当前 `plugin-api 1.2.0` / `design 0.9.0`，以仓库最新发布为准），不需要本地 mavenLocal 发布。

`plugin-api` 是契约，`design` 是与 App 同源的 UI 设计系统（插件 UI 直接用它，`plugin-api` 通过 `api()` 把它传递进来）。发布新版本：

```bash
./scripts/gradlew-locked :design:publishAllPublicationsToPagesRepository :plugin-api:publishAllPublicationsToPagesRepository
# 产物在 app/build/maven-repo/io/github/sadami3066/deepdeep/
```

把产物同步到 `deepdeep-maven` 仓库对应目录并推送，Pages 会自动更新；SDK 版本升级时同步改各模块 `build.gradle.kts` 的 `compileOnly` 与模板版本。

新增插件：

```bash
# 1. 复制模板并改名（在插件工程仓库根目录执行）
cp -R template my-plugin && cd my-plugin
#    改 settings.gradle.kts 的 rootProject.name（决定 .ddp 文件名）
#    改 build.gradle.kts 的 namespace/applicationId/versionName
#    改 src/main/assets/plugin.json 的 id/version/entryClass/name/summary/provides
#    改 src/main/kotlin 下包名与入口类

# 2. 打包 + 签名 + 自检
./gradlew assemblePlugin
./gradlew signPlugin
./gradlew verifyPlugin

# 3. 产物
#    build/plugin/<rootProject.name>.unsigned.ddp   未签名（排障用）
#    build/plugin/<rootProject.name>.ddp            可安装的签名包
```

模板工程未改名时 `rootProject.name` 是 `plugin-template`，产物在 `template/build/plugin/plugin-template.ddp`（未签名是 `plugin-template.unsigned.ddp`）；改名后文件名随之变化。模块只编 `debug` 变体，`release` 不开 minify；`.ddp` 由 debug APK 重打包而来。把模板作为多模块工程的一个模块（如 `:template`）时，任务按模块执行，如 `:template:verifyPlugin`，产物在 `<module>/build/plugin/<module>.ddp`。

安装：把 `.ddp` 传到手机，可以直接在文件管理器里用 DeepDeep 打开、把文件分享给 DeepDeep，或在插件页「导入插件」选择文件。确认页展示名称/版本/体积/整包 sha256/发布者名称与 `keyId`、将提供的工具/技能/页面/提示词覆盖（声明 `prompt` 时）、能力/权限/组件声明；首次导入需确认信任发布者，确认后才写入 TOFU 信任库、解包安装并立即启用（`defaultEnabled` 为 true 时）。之后升级必须用同一发布者密钥签名。

设备回归由宿主仓库根目录的脚本驱动（连好设备后执行）：`scripts/plugin-bundles-e2e.sh` 用五个官方插件包回归安装与激活；`scripts/plugin-e2e.sh` 用插件仓库的 `sample` 测试夹具回归安装、运行、停用与升级。脚本用 `adb install` 装 debug 包，把 `.ddp` 推进 App 外部私有目录，不需要 root，普通设备或模拟器都能跑。

## 工程与依赖

插件就是一个普通 Android application 工程，产物只需要 `classes*.dex` 与 `assets/**`（见「`.ddp` 包格式」），APK 的 manifest、资源与签名不参与。

| 项 | 值 |
|---|---|
| AGP | 9.4.1 |
| Kotlin（Compose compiler 插件同版本） | 2.4.21 |
| Compose BOM | 2026.03.00 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.12.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 29（插件 dex 的 minApi 不能高于宿主） |
| JVM target | 17 |

依赖规则：

- 宿主已提供的库只能 `compileOnly`：`io.github.sadami3066.deepdeep:plugin-api`（版本用 `deepdeep-maven` 最新发布；`api()` 传递配套的 `design:0.7.0`，插件 UI 直接用其中的 Dd 组件）、Compose（`ui`/`foundation`/`material3` + BOM）、`kotlinx-coroutines-core`、`kotlinx-serialization-json`。
- 宿主 release 对插件的运行时依赖做了 R8 keep（`plugin-api`、`design`、Kotlin、协程、Compose，以及插件 UI 常用的 `androidx.lifecycle`/`androidx.activity`/`androidx.savedstate`），这些库在混淆包里不会缺类。keep 只保 ABI 名字，不等于版本兼容承诺：插件仍需按宿主 SDK 版本重编译，白名单之外的宿主库不保证可用。
- **禁止** `implementation` 这些库：宿主 parent-first，`plugin-api` 与 Compose 只存在宿主那一份，插件包里重复打包会触发类冲突。
- 插件可以带自己的第三方库，但必须一起打进 `classes*.dex`，并避开宿主已占用的包/类名。
- 只暴露 `plugin-api`；不要反射 App 内部实现（升级会坏）。
- 原生库：`.ddp` 里的 `lib/<abi>/**` 进入签名摘要，但宿主不把它加入 classloader 的库搜索路径，`System.loadLibrary` 不可用。
- 没有 `res/`、没有 `R`：文案、图标、技能文件都走清单 + assets。

## plugin.json 清单

清单固定放在包内 `assets/plugin.json`，由宿主解析与校验。字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 反向域名小写，至少两段，正则 `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`（如 `com.example.myplugin`）；宿主用它做安装目录与唯一标识 |
| `version` | string | 是 | `major.minor.patch`，可带 `-dev`/`+build` 后缀；宿主记录安装版本、判断更新 |
| `apiLevel` | int | 否（默认 6） | 插件要求的契约等级；必须在 `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL`（当前 6..6）内，否则拒绝装载。不写时按 `BASELINE_API_LEVEL`（6）处理；宿主 1.0.4 起 level 5 插件会被拒绝 |
| `minHostVersion` | string | 否 | 最低宿主版本。宿主版本取 `BuildConfig.VERSION_NAME`；比较只看 `major.minor.patch`、忽略后缀。只用 1.0.x 已有能力的插件可写 `"1.0.4"`；使用 1.1.0 新能力（含 design 0.7.0 组件）的插件写 `"1.0.6"`；使用 design 0.8.0 组件的插件写 `"1.0.7"`；使用 design 0.9.0 组件或 plugin-api 1.2.0 新能力的插件写 `"1.0.8"` |
| `entryClass` | string | 是 | 实现 `DeepDeepPlugin` 的类全名，必须有无参构造 |
| `name` | LocalizedText | 是 | 插件名，至少含 `default` |
| `summary` | LocalizedText | 是 | 一句话简介，至少含 `default` |
| `description` | LocalizedText | 否 | 详情说明 |
| `homepage` | string | 否 | 必须是 `http(s)://` 链接 |
| `changelog` | string | 否 | 更新说明 |
| `icon` | string | 否 | 只支持 `asset:<包内相对路径>`，如 `asset:icon.png`；列表按显示尺寸降采样、不染色，缺失或损坏回退占位图标 |
| `ui` | object | 否 | `{ "apiLevel": 2, "kotlin"?: "2.4", "compose"?: "1.10.5" }`；参与 Compose 槽兼容判定，见「UI 扩展」 |
| `publisher` | object | 否 | `{ "name": ..., "keyId": ... }`。`name` 是展示名（如 `milous`），导入确认与已安装信息里直接显示；缺失时界面回落到签名 `keyId` 前 12 位。`keyId` 只作说明，发布者身份与信任始终以签名文件 `META-INF/plugin-signature.json` 里的 `keyId` 为准 |
| `provides` | object | 否 | 贡献预检，见下；必须与实际贡献一致 |
| `capabilities` | string[] | 是 | 插件需要的宿主服务与能力清单（38 项），见下；缺失或含未知项拒绝装载 |
| `permissions` | string[] | 否 | 插件会申请的系统权限，取值见「权限声明」；必须与 `capabilities` 成对声明 |
| `components` | object | 否 | 需要宿主代理的系统组件能力，见「组件声明」；每个开启项都要求对应 capability |
| `defaultEnabled` | bool | 否（默认 true） | 首次安装后是否默认启用 |

清单不支持 `source`/`installable`/`sizeLabel`/`tintIcon` 等字段：来源固定按外部 `.ddp` 处理，体积由宿主按包体展示，图标统一用 asset 图片。未知字段忽略，错误的标量类型退化为默认值后交给校验报错。

`provides` 用于安装前的能力预检与确认页展示：

| 键 | 内容 |
|---|---|
| `tools` | 工具名列表，小写 `snake_case`，如 `template_echo`；不能与宿主保留工具名冲突 |
| `skills` | 技能 id 列表，如 `template-skill` |
| `uiSlots` | 注册的槽位 id，如 `tool.card.content:template_echo`、`chat.message:card`；插件页面不走槽位，见 `pages` |
| `pages` | 插件页面 id 列表 |
| `themes` | 主题 id 列表；与 `assets/themes.json` 中的 `id` 一一对应，见「主题包」 |

`capabilities` 声明插件实际使用的宿主服务与能力；安装确认页与插件详情会展示，宿主只注入声明过的服务。**未声明的服务访问器在运行时抛 `PluginServiceUnavailable`**（不是返回 null）。1.2.0 共 38 项，分四组；标「实验」的访问器需要 `@OptIn(PluginExperimentalApi::class)`。

**基础能力（1.0 起）**

| wire 名 | 访问器 | 能做什么 | 不可用时 |
|---|---|---|---|
| `linux` | `host.linux()` | Linux 环境命令执行与流式输出 | `available=false`；Root 档准备中/失败时抛 `IllegalStateException` |
| `screen` | `host.screen()` | 屏幕观测、截图、点击/滑动/输入与虚拟屏 | 能力字段标记不可用，调用抛异常 |
| `notifications` | `host.notifications()` | 发布/更新/取消通知；通知监听与动作 | `enabled=false` 时通知被静默忽略；监听未授权时相关方法返回空/false |
| `secrets` | `host.secrets()` | 按插件 id 隔离的加密键值存储 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `network` | `host.network()` | HTTP 请求、下载与 `get`/`post` 扩展 | 网络错误抛异常；响应体 2 MB、下载 512 MB 上限 |
| `files` | `host.files()` | 插件数据目录与工作区文件读写 | 相对路径以 `dataDir` 为根；绝对路径随档位变化 |
| `sessions` | `host.sessions()` | 会话列表/历史/提示/停止/新建/重命名/删除/归档 | 无活动会话时 `active=null`、`prompt` 返回 false |
| `jobs` | `host.jobs()` | 后台任务启动、读取与终止 | 无会话时 `start` 返回 null |
| `timers` | `host.timers()` | 一次性/Cron 定时提醒 | 参数非法返回 null |
| `goals` | `host.goals()` | 目标创建、编辑、暂停/恢复/完成/清理 | 非法状态返回 null |
| `miniapp` | `host.miniapp()` | 小程序扫描、加载、创建、编辑、运行、删除 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `mcp` | `host.mcp()` | MCP 服务器探测、工具列表与调用 | 服务器不存在时返回错误结果 |
| `ui` | `host.ui()` | 打开 URL/分享/页面、交互选择器、Snackbar、主题 | 未接线时抛 `PluginServiceUnavailable("ui.xxx")` |
| `app` | `host.app()` | App 版本、语言、设备等只读信息；App 前后台事件 | 始终可用 |
| `bus` | `host.bus()` | 插件间轻量事件总线 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `logs` | `host.logs()` | 写入宿主日志 | 始终可用 |
| `chat` | `host.chat()` | 向会话投递消息/卡片/附件 | 未知会话抛异常；投递失败返回 false |
| `prompt` | 无访问器 | 启用 `PromptOverridePlugin` 提示词覆盖（实验） | 未声明时不调用 `promptOverride()` |

**设备与系统（1.1.0 新增）**

| wire 名 | 访问器 | 能做什么 | 不可用时 |
|---|---|---|---|
| `settings` | `host.settings()` | 插件级键值/JSON 设置持久化与变更流 | 始终可用（宿主存储） |
| `device` | `host.device()` | 设备信息、电量/网络/屏幕状态流、振动、跳系统设置页 | `observe` 在状态不可得时发默认快照；跳转失败返回 false |
| `clipboard` | `host.clipboard()` | 读写剪贴板文本与 URI | 后台访问受限时返回 null 或忽略写入 |
| `permissions` | `host.permissions()` | 查询/申请运行时权限，查询特殊授权状态并跳系统设置 | `request` 返回各权限是否授予；特殊授权未开时 `granted=false` |
| `packages` | `host.packages()` | 应用列表/详情、安装/卸载 APK、启动、打开应用信息、读取图标 | 包名不存在返回 null；静默安装需 Root/ADB 档，否则返回待用户确认 |
| `privilege` | `host.privilege()` | 当前档位与 Root/ADB 状态、指定档位执行命令、静默装卸、无线调试配对 | 档位不足时 `exec` 返回非 0/错误，配对接口返回失败原因 |
| `intents` | `host.intents()` | 任意 Intent 启动、广播、服务、解析与查询 | 无匹配返回 false/null；后台启动 Activity 受系统限制 |
| `components` | `host.components()`（实验） | 打开宿主代理的独立插件窗口、注册动态广播接收 | 未声明 components 时不注入；窗口/接收失败返回 false |
| `background` | `host.background()` | WorkManager/Alarm 后台任务、开机回调、前台服务、电池优化 | `schedule` 返回 false 表示被系统拒绝；任务触发受 Doze 影响 |
| `location` | `host.location()` | 最后位置、单次定位、位置更新流、地理编码 | `available=false` 或权限未授予时返回 null |
| `contacts` | `host.contacts()` | 联系人查询、搜索、创建、修改、删除 | 权限未授予时抛 `SecurityException`；`permissionGranted=false` |
| `sms` | `host.sms()` | 短信线程/消息读取、发送、标记已读、删除 | `canRead`/`canSend` 为 false 时对应操作返回 false 或抛异常 |
| `calendar` | `host.calendar()` | 日历与事件读写 | 权限未授予时抛 `SecurityException`；`permissionGranted=false` |
| `media` | `host.media()` | 保存到下载/相册、扫描、打开、分享、预览、图片读取、URI 导入 | 路径不可读返回 null/false；需要所有文件访问时另申请 `manage-storage` |
| `speech` | `host.speech()` | TTS 朗读与语音识别 | `speakAvailable`/`recognizeAvailable` 为 false 时返回 false/错误结果 |
| `web` | `host.web()` | 宿主网页搜索与正文抓取 | `available=false` 时返回空列表/null |
| `credentials` | `host.credentials()`（实验） | 读取宿主服务商与 MCP 的凭据信息 | 宿主未配置时返回空列表/null；只能读、不能改 |
| `accessibility` | `host.accessibility()`（实验） | 无障碍事件流与全局动作 | `enabled=false` 时事件流为空、动作返回 false |
| `share` | 无访问器 | 接收系统分享（配合 `components.share`） | 未声明组件时不会派发 `shareReceived` |
| `deeplink` | 无访问器 | 接收深链路径（配合 `components.deeplinks`） | 路径不匹配或未声明时不会派发 `deepLinkReceived` |

`capabilities` 是**可用性边界，不是安全沙箱**：插件仍与宿主同进程、同 UID，可以通过 `host.context` 或反射绕过；它用于让插件声明意图、让用户在安装前看清会用到哪些能力，不能阻止恶意代码。至少声明一项；空数组合法但插件几乎无法工作。

`tools`/`skills`/`pages` 的 id 统一匹配 `^[a-z][A-Za-z0-9_-]*$`，**不能包含点号**（技能 id 写 `template-guide`，不是 `template.guide`）；`uiSlots` 的槽位 id 规则见后文。

### 权限声明（`permissions`）

`permissions` 列出插件会申请的系统权限，取值为 `PluginPermission` 常量（都是字符串）。安装期校验权限与 capability 的对应关系：任何权限都必须有对应 capability，否则报 `PERMISSION_WITHOUT_CAPABILITY`；未知或重复的取值报 `INVALID_PERMISSION`。安装确认页会展示权限清单。

**运行时权限**（`permissions().request(...)` 弹系统授权框）：

| wire 名 | 对应权限 | 用途 | 需要 capability |
|---|---|---|---|
| `location.coarse` | `ACCESS_COARSE_LOCATION` | 大致位置 | `location` |
| `location.fine` | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | 精确位置 | `location` |
| `contacts.read` | `READ_CONTACTS` | 读取联系人 | `contacts` |
| `contacts.write` | `READ_CONTACTS` + `WRITE_CONTACTS` | 修改联系人 | `contacts` |
| `sms.read` | `READ_SMS` | 读取短信 | `sms` |
| `sms.send` | `SEND_SMS` | 发送短信 | `sms` |
| `calendar.read` | `READ_CALENDAR` | 读取日历 | `calendar` |
| `calendar.write` | `READ_CALENDAR` + `WRITE_CALENDAR` | 修改日历 | `calendar` |
| `camera` | `CAMERA` | 拍照（图片选择器） | `ui` |
| `microphone` | `RECORD_AUDIO` | 语音识别 | `ui` |
| `notifications.post` | `POST_NOTIFICATIONS` | 发布通知 | `notifications` |

**特殊授权**（没有运行时弹窗，由用户在系统设置里开启；用 `permissions().special(...)` 查询状态、`openSettings(...)` 跳转）：

| wire 名 | 对应设置 | 用途 | 需要 capability |
|---|---|---|---|
| `notifications.listen` | 通知使用权 | 读取/管理通知（通知监听） | `notifications` |
| `accessibility` | 无障碍服务 | 读取无障碍事件、执行全局动作 | `accessibility` |
| `overlay` | 悬浮窗 | 显示悬浮内容 | `permissions` |
| `usage-stats` | 使用情况访问 | 读取应用使用统计 | `permissions` |
| `battery-optimization` | 忽略电池优化 | 后台任务不被系统限制 | `permissions` |
| `install-packages` | 安装未知应用 | 安装/更新 APK | `packages` |
| `manage-storage` | 所有文件访问权限 | 直接读写共享存储 | `media` |

### 组件声明（`components`）

`components` 声明需要宿主静态代理的系统组件能力。插件不能自己注册 Activity/Service/Receiver，宿主用固定的代理组件转接；每个开启项都必须有对应 capability，否则报 `COMPONENT_WITHOUT_CAPABILITY`；结构或布尔类型非法报 `COMPONENT_WITHOUT_CAPABILITY`。

| 字段 | 类型 | 用途 | 需要 capability |
|---|---|---|---|
| `background` | bool | WorkManager/Alarm 后台任务与开机重注册 | `background` |
| `foregroundService` | bool | 宿主前台服务代理，展示常驻进度 | `background` |
| `notificationListener` | bool | 通知监听：`notificationReceived` 回调与 `recent`/`received`/`removed` 查询 | `notifications`（另需 `permissions: ["notifications.listen"]`） |
| `accessibility` | bool | 无障碍代理：`accessibility()` 事件与全局动作 | `accessibility`（另需 `permissions: ["accessibility"]`） |
| `share` | bool | 接收系统分享，触发 `shareReceived` | `share` |
| `deeplinks` | string[] | 接收的深链路径列表，如 `["sync", "sync/start"]`；匹配「路径相等或以 `声明/` 开头」，最长 128 字符，正则 `^[a-z0-9][a-z0-9/._-]*$`，重复/非法报 `INVALID_DEEPLINK` | `deeplink` |

示例：

```json
"capabilities": ["background", "share", "deeplink", "notifications"],
"permissions": ["notifications.listen"],
"components": {
  "foregroundService": true,
  "share": true,
  "deeplinks": ["sync"],
  "notificationListener": true
}
```

`LocalizedText` 写法：

```json
"name": { "default": "Plugin Template", "zh": "插件模板", "en": "Plugin Template" }
```

- `default` 为必填回退；`zh`/`en` 是语言标签，宿主按 App 语言取，缺失回退 `default`。
- 也可以给字符串简写（`"name": "Plugin Template"`），等价于只有 `default`。
- `host.text(source, *args)` 支持 `String.format` 占位符（`%s`/`%d`），参数只传字符串/数字（签名保留 `vararg Any`，新代码不要再传其它类型）。
- `LocalizedText` 与 `IconRef` 是只读接口：文本用 `localizedText(default, zh?, en?)` 构造，图标用 `assetIcon("asset:<路径>")` 构造；level 5 的 `Literal`/`Resource` 等构造在 v6 已删除。
- 外部插件没有 App 资源，`host.text()` 负责解析与格式化。

### 主题包（`assets/themes.json`）

插件可以贡献固定浅色或深色主题，用户在「设置 → 主题」里切换后全局生效。主题包只读取安装目录里的 JSON，不执行插件代码；插件停用或文件缺失时，主题选择回退「跟随系统」。`plugin.json` 的 `provides.themes` 声明主题 id，`assets/themes.json` 给出定义，两边必须完全一致。

```json
{
  "schema": 1,
  "themes": [
    {
      "id": "aurora",
      "name": { "default": "Aurora", "zh": "极光", "en": "Aurora" },
      "dark": false,
      "base": "santorini",
      "tokens": {
        "paper": "#F7F8F8",
        "accent": "#7C3AED",
        "ink": "#1E3A47"
      }
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schema` | int | 是 | 固定为 `1` |
| `themes` | array | 是 | 1–24 个主题，id 不能重复 |
| `id` | string | 是 | 主题 id，正则 `^[a-z][a-z0-9_-]*$`，最长 64 字符；与 `provides.themes` 对应 |
| `name` | object | 是 | `default` 必填，`zh`/`en` 可选；按 App 语言取，缺失回退 `default`；每项最长 64 字符 |
| `dark` | bool | 是 | 主题明暗；决定色块标记、状态栏图标与插件的 `ui.isDark` |
| `base` | string | 是 | `deepdeep`、`santorini` 或 `oceanic`，提供未覆盖 token 的默认色 |
| `tokens` | object | 是 | 颜色覆盖；键必须在下方白名单内，值只能是 `#RRGGBB` 或 `#AARRGGBB` |

`base` 只作为底稿：`deepdeep` 为默认浅色，`santorini` 为浅色，`oceanic` 为深色；`dark` 可以与 `base` 的明暗不同，最终明暗以 `dark` 为准。未写的 token 沿用 `base`。

可用 token：

```
paper surface raised ink inkSecondary inkTertiary accent accentPressed onAccent
accentSoft danger success warning hairline wash composer scrim
codeBackground inlineCodeBackground codeKeyword codeString codeComment
codeNumber codeFunction codeType
```

对比度在最终配色上校验：

| 组合 | 最低对比度 |
|---|---|
| `ink` / `paper` | 4.5:1 |
| `ink` / `surface` | 4.5:1 |
| `inkSecondary` / `paper` | 4.5:1 |
| `onAccent` / `accent` | 3:1 |

安装时读取并校验主题文件；文件超过 64 KiB、schema 或字段非法、token 未知、颜色格式错误、对比度不足、id 重复、主题数超过 24、`provides.themes` 与文件不一致，都会以 `THEMES_INVALID` 拒绝安装，并在确认页/提示里给出具体原因。主题文件通过校验后随 `assets/**` 解包到安装目录，宿主启动时只读取该文件，不依赖插件运行。

校验错误码（宿主在安装时执行，`PluginValidation`）：

| 错误码 | 含义 |
|---|---|
| `INVALID_ID` | id 不是反向域名小写格式 |
| `INVALID_VERSION` | version 不是 `major.minor.patch[后缀]` |
| `INVALID_MIN_HOST_VERSION` | minHostVersion 解析失败 |
| `HOST_VERSION_UNSUPPORTED` | 宿主版本低于 minHostVersion |
| `API_LEVEL_UNSUPPORTED` | apiLevel 不在 `MIN_SUPPORTED_API_LEVEL..宿主等级`（1.0.4 起 level 5 在此被拒） |
| `UI_API_LEVEL_UNSUPPORTED` | `ui.apiLevel` 与宿主 `HOST_UI_API_LEVEL`（2）不匹配（**警告**：只禁用 Compose 绑定，插件仍可加载） |
| `ENTRY_CLASS_MISSING` / `INVALID_ENTRY_CLASS` | 缺入口类或类名非法 |
| `MISSING_TEXT` / `INVALID_TEXT` | name/summary 缺 `default`，或用了资源文本 |
| `INVALID_ICON` / `INVALID_URL` | 图标路径/主页不合法 |
| `INVALID_PROVIDES` | provides 条目非法或重复 |
| `MISSING_CAPABILITIES` | 清单没有 `capabilities` 字段 |
| `INVALID_CAPABILITY` | capabilities 含未知服务名 |
| `INVALID_UI_SLOT` | uiSlots 中的槽位 id 未知或缺实例后缀 |
| `INVALID_SKILL` | 插件返回的技能文件不合法 |
| `INVALID_PERMISSION` | `permissions` 含未知或重复取值 |
| `PERMISSION_WITHOUT_CAPABILITY` | 声明的权限没有对应 capability |
| `COMPONENT_WITHOUT_CAPABILITY` | `components` 结构或字段类型非法 |
| `COMPONENT_WITHOUT_CAPABILITY` | 开启的组件没有对应 capability |
| `INVALID_DEEPLINK` | 深链路径格式非法或重复 |

## `.ddp` 包格式

`.ddp` 就是一个 zip（扩展名不参与判断）：

```
plugin.ddp
├── classes.dex                      # 必需，至少一个；多个按 classes.dex, classes2.dex, ... 排序装载
├── classes2.dex
├── assets/
│   ├── plugin.json                  # 必需，且只能在这个路径
│   ├── icon.png                     # 可选，清单 icon 引用
│   └── skills/...                   # 可选，技能 markdown 与附加文件
├── lib/<abi>/*.so                   # 可选；进入签名摘要，当前不加载
└── META-INF/plugin-signature.json   # 安装必需
```

约束：

- 只有 dex/assets/lib/签名四类条目；目录条目忽略。
- 条目名必须用 `/`，不能是绝对路径，不能含 `..`、`\`、`:`、NUL；同名条目拒绝。
- `assets/plugin.json` 只能是这一个路径。
- 整包 ≤ 100 MB、解压后总量 ≤ 256 MB；`classes*.dex` 序号不能重复。
- dex 由 D8/R8 产出（AGP 的 `assembleDebug` 已处理），`minSdk = 29` 保证 dex minApi 不高于宿主。
- 宿主用内存 ClassLoader 装载 `classes*.dex`；安装时把整包复制到 `files/plugins/<id>/<version>/bundle/`，`assets` 解包到同版本目录。
- 插件运行数据固定放 `files/plugins/<id>/data`（对应 `host.dataDir`），升级不丢；`host.assetsDir` 是当前版本解包后的只读 assets。
- 版本目录按版本保留：升级后的上一版本目录作为回滚点，直到下一次成功升级或卸载。

## 构建、签名与更新

### 打包与签名任务

模板工程的 `build.gradle.kts` 内置四个打包任务；多模块工程（如官方插件仓）把这部分抽到根 `gradle/plugin-bundle.gradle.kts` 共享。

| 任务 | 作用 |
|---|---|
| `generateSigningKey` | 生成 `keys/plugin-signing.pem` 与 `.pub`；已存在则报错，不会覆盖 |
| `assemblePlugin` | 把 `build/outputs/apk/debug/<module>-debug.apk` 重打成 `build/plugin/<module>.unsigned.ddp`，只保留 `classes*.dex`、`assets/**`、`lib/**`，条目时间固定以便复现 |
| `signPlugin` | 读取 Ed25519 密钥，对 unsigned 包签名并写入 `META-INF/plugin-signature.json`；打印 `publisher keyId`、整包 sha256、entry digest |
| `verifyPlugin` | 重新按宿主规则验签，输出 `Signature OK` |

密钥默认在工程根 `keys/plugin-signing.pem`/`.pub`，由 `generateSigningKey` 生成（私钥权限收敛为 `600`，`.gitignore` 忽略）；`signPlugin` 只读取密钥，缺失时直接报错并提示生成方式。也可以用 Gradle 属性指定别的前缀（不带扩展名）：

```bash
./gradlew -PpluginSigningKey=/secure/keys/my-plugin signPlugin
```

- 私钥**不入版本库、不外发**；丢了就无法再发同名更新。
- 换钥等于换发布者身份，已装用户无法用新钥升级。
- `keys/` 下的仓库密钥只用于本机开发与官方插件签名。

### 签名算法（ddp-1）

安装必须带 `META-INF/plugin-signature.json`，算法 Ed25519（宿主优先平台 Provider，旧平台回退 BouncyCastle）。

**canonical entry digest**：把 zip 内所有非目录条目按**条目名的 UTF-8 字节序**升序排列，排除所有以 `META-INF/` 开头的条目；每个条目一行：

```
<sha256-hex-lowercase><SP><uncompressed-size-decimal><SP><entry-name>\n
```

sha256 对条目的**未压缩内容**计算，行与行直接拼接、最后一行以 `\n` 结尾；整段 UTF-8 字节即待签名消息。宿主验签时用同一算法重算。

**签名文件**正好 4 个字段（未知字段忽略）：

```json
{
  "algorithm": "Ed25519",
  "keyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213",
  "publicKey": "<base64(std) of X.509 SubjectPublicKeyInfo DER>",
  "signature": "<base64(std) of 64 字节 Ed25519 签名，签 canonical entry digest 字节>"
}
```

**keyId 必须由公钥派生**：`keyId = "ed25519:" + 小写十六进制(SHA-256(SPKI DER))`。宿主要求签名里的 `keyId` 与公钥重算结果一致；该值同时作为发布者指纹与 TOFU 信任库的键。

### 安装、信任与更新

安装入口只有一个：插件页「导入插件」选择本地 `.ddp`。宿主先做包读取与验签（失败直接拒绝），确认页展示清单与发布者信息；用户确认后写入 TOFU 信任库、解包到版本目录、记录安装并立即装载启用。

- 首装是唯一信任决策点：确认页展示发布者名/`keyId`、整包 sha256、体积、将提供的能力，并写明「插件拥有 App 全部权限」。确认后 `keyId → publicKey` 记入 TOFU 信任库。
- 每次装载前用安装记录里的 `bundleSha256` 复核包体，并重算 canonical digest 验签；不匹配则拒绝并记入插件行的错误文本。
- **更新必须同钥**：`id` 相同的新版本必须用同一 `keyId` 签名，否则按「发布者不匹配」拒绝。
- 更新流程：选择新包 → 校验 sha256 + 签名（同钥）→ 暂存到新版本目录；当前没有忙碌会话时立即激活，有忙碌会话时相位保持 `PENDING_RESTART`，等会话全部结束后自动激活（进程重启后也会继续激活）；激活走准备 → 校验 → 提交 → 换装的阶段序列，校验阶段会调用 `onUpgrade(fromVersion)` 并预生成贡献；准备/校验失败会丢弃暂存版本并删除坏版本目录，旧版本继续可用；提交写盘失败保留暂存版本到下次启动重试。
- 安装记录保留 `previousVersion` 作为回滚点。恢复规则：上次激活中断 → 下次启动跳过自动激活并提示「上次激活中断」；同一版本连续 2 次启动失败 → 自动停用（`enabled=false`），用户手动启用即重新授权。

### 市场预览图

市场条目的 `plugin.previews` 字段配置详情页预览图，`plugin.json` 清单本身不含该字段：

- 竖版 9:20 图片 URL 列表，建议 1080×2400；只接受 `https://`，同一 URL 去重，最多 6 张。
- 市场详情页以 144×320dp 卡片按 9:20 零裁切展示，可点开全屏左右滑动查看；没有 `previews` 时不展示预览区块。

### 市场权限标注

市场条目的 `plugin.requiredAccess` 字段声明插件运行需要的系统访问档位，`plugin.json` 清单本身不含该字段：

- 取值 `normal`（缺省）/ `adb` / `root`，对应「启动权限」的普通、ADB、Root 三档。
- 按插件实际依赖如实标注：只有确实需要 ADB 或 Root 下的系统能力（例如工具依赖 root 才能执行）才标高，不要虚标。
- 客户端按当前启用档位显示彩色标签（普通绿 / ADB 黄 / Root 红），当前档位低于要求时在市场条目和已安装插件行提示部分功能可能不可用；字段缺失或非枚举值按 `normal` 处理，兼容旧索引。

## 生命周期与相位

宿主为每个插件维护相位（插件页与信息弹窗里可见）：

| 相位 | 含义 |
|---|---|
| `INSTALLED` | 已安装但未装载（例如等待下次启动） |
| `ACTIVE` | 已启用且运行正常，贡献全部生效 |
| `DEGRADED` | 已启用但 `availability()` 返回不可用：技能不启用、错误原因可见；工具/提示词/UI/hooks 由插件自己决定是否继续提供 |
| `FAILED` | 装载/启用/激活失败，原因见错误文本 |
| `DISABLED` | 用户关闭了插件（「用户选择 ≠ 加载成功」） |
| `PENDING_RESTART` | 有暂存的新版本，重启后激活 |

只有已装载运行的插件（`ACTIVE`/`DEGRADED`）会聚合贡献；`availability()` 不可用时会停用技能并显示原因，**但不会自动清空插件的工具/提示词/UI/hooks**——不可用的能力请在 `contributions()`/`ui()` 里按 availability 自行返回空。

`DeepDeepPlugin` 生命周期方法（`availability()` 必须实现，其余方法都有默认空实现；回调异常由宿主隔离，不会拖垮 App）：

| 方法 | 调用时机与责任 |
|---|---|
| `availability(host)` | 每次刷新调用；返回 `Unavailable(reason)` 时相位 `DEGRADED`、原因可见，技能停用 |
| `onEnable(host)` / `onDisable(host)` | 用户启/停插件；启动时对已启用插件也会调用 `onEnable`，停用时先 `onDisable` 再取消插件 scope |
| `onUpgrade(host, fromVersion)` | 新版本激活的校验阶段调用，`fromVersion` 是当前在用版本 |
| `skills(host)` | 提供技能；每次刷新调用 |
| `promptSections(host)` | 追加系统提示词段 |
| `contributions(host)` | 提供全局工具 + hooks |
| `contributionsFor(host, session)` | 每会话物化一次：返回该会话生效的工具 + hooks（hooks 按 `pluginId` 覆盖该插件的全局 hooks）；`session` 是 live handle（可空），只用来读会话快照；默认委托 `contributions` |
| `ui(host)` | 槽位/页面/输入区动作贡献 |
| `uninstall(host)` | 用户删除插件前，清理插件自己的外部副作用（宿主随后删安装目录、技能覆盖） |
| `close()` | 停用/卸载/更新/进程退出时调用（宿主先取消插件 scope，最多等 5s）；`close` 之后不要再异步回调 |

实现 `PromptOverridePlugin` 的插件（需在清单声明 `prompt` capability）另有 `promptOverride(host)`：宿主在物化贡献时调用一次，见「提示词覆盖」。

插件的登录、CLI 安装、连接状态等全部由插件自己管理：状态写在 `host.dataDir`/`host.secrets()`，界面画在插件页面里；宿主不再提供安装/连接/设置/状态/动作/详情行回调，也没有面向模型的插件连接工具。页面里的操作改变能力后调用 `host.requestContributionsRefresh()`，宿主会重新物化贡献并在安全点重建会话（忙碌会话在本轮结束后生效），工具/技能/提示词/页面随即更新。

`host` 是插件唯一入口（`PluginHost`）：`manifest`、`context`（宿主注入的 App 上下文）、`dataDir`、`assetsDir`、`scope`、`text`、35 个宿主服务访问器（见「宿主服务」）与 `requestContributionsRefresh()`。`manifest` 等清单类型是宿主构造的只读接口，插件不要自行构造。

### 每插件作用域

- 每个插件有独立的 `SupervisorJob` 与 `CoroutineScope`，即 `host.scope`。插件里的后台协程应挂在这个 scope 上（`host.scope.launch { ... }`）。
- 停用、卸载、更新、进程退出统一走：取消 scope → `close()`（5s 超时）→ 释放安装句柄 → 移除注册/清贡献 → 同步技能；任一步异常都会记录并继续完成剩余收尾。
- 进程被强杀时不保证 `close()` 被调用，需要跨进程存续的状态请及时写进 `host.dataDir`。
- 重新启用会创建新的 scope 与新的 `PluginHost`，插件对象会重新装载。**不要缓存 host 或 scope 到插件实例字段**，每次回调都以参数为准。
- 协程里必须处理取消（`CancellationException` 直接向上抛，不要吞）；长循环里用 `isActive` 或在阶段边界检查。
- 同一会话 run 开始后宿主冻结当时的 hooks 集合（见「事件与 hooks」），本轮内停用插件仍可能收到剩余事件，回调要能容忍状态已变；资源清理写在 `close()`/`onDisable` 里。

### 一次物化

宿主每次刷新对每个插件最多调用一次贡献方法，结果聚合成不可变快照；工具装配、提示词、技能同步、UI 槽位、冲突诊断、插件页都消费同一份快照，不会在消费路径重复调用插件。

- 每次刷新都会调用 `availability()`、`skills()`；启用且已安装时再调用 `contributions()`、`ui()`、`promptSections()`，声明 `prompt` capability 时还调用 `promptOverride()`。
- `contributionsFor()` 每个会话只调用一次（同一会话绑定从建立到关闭），该会话的工具与会话 hooks 都从这次结果取用；会话重建后才重新物化。
- 不要在这些方法里做重 IO 或依赖会变化的外部状态：耗时工作放 `host.scope` 异步做（如 `onEnable` 里启动轮询、`onDisable`/`close` 时靠 scope 取消停止）。

### 失败恢复

装载/激活/启用失败时相位为 `FAILED`，`availability()` 不可用时为 `DEGRADED`。插件页的行内错误文本会展示原因；行尾「⋯」菜单按状态提供恢复入口：

| 入口 | 行为 |
|---|---|
| 「重试激活」 | 清掉激活中断标记与启动计数后重新执行激活：已装载的插件重新注入服务并调用 `onEnable`，未装载/被自动禁用的插件重新装载并启用；有暂存版本会完整重跑激活序列。成功后刷新并清除错误，失败保留状态并显示新的错误文本 |
| 「回滚到 <previousVersion>」 | 记录里有上一版本时出现：把安装版本切回上一版本、清空暂存记录并清理失败版本目录 |
| 启用开关 | 关闭再启用即重新执行 `onEnable`，用于手动恢复被自动停用的插件 |

- 同一版本连续 2 次启动失败会被自动禁用（相位 `DISABLED`），手动重新启用即重新授权并清空启动计数。
- 槽位渲染崩溃只禁用对应 Compose 绑定并回退宿主视图，不改变插件相位；升级插件版本后自动重试渲染。装载失败才走上面的重试/回滚/重新启用。

## 工具

```kotlin
override fun contributions(host: PluginHost) = contributions(
    tools = listOf(
        pluginTool(
            name = "template_echo",
            label = "Template Echo",
            description = "Echo text back, streaming one partial update.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("text") { put("type", "string") } }
                put("required", buildJsonArray { add(JsonPrimitive("text")) })
            },
            promptSnippet = "template_echo echoes text",
            promptGuidelines = listOf("Use it only in development demos."),
        ) { call ->
            val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            call.partial(textResult("received ${text.length} chars"))
            textResult("echo: $text")
        },
    ),
)
```

| 字段 | 说明 |
|---|---|
| `name` | 全局工具名，小写 `snake_case`；不能与宿主保留名冲突（见下），跨插件重名时先注册者保留 |
| `label` | 工具卡片标题 |
| `description` | 给模型的工具描述，写清做什么/何时用/边界 |
| `parameters` | JSON Schema（`{"type":"object","properties":{...},"required":[...]}`），宿主原样交给 SDK |
| `promptSnippet` | 系统提示里的一句话摘要（可选） |
| `promptGuidelines` | 追加给模型的工具使用规则（可选） |
| `exposure` | `PluginToolExposure.DIRECT`（默认，工具常驻并直接可调）或 `DEFERRED`（不进入常驻工具列表，模型先 `tool_search` 找到、再 `tool_execute` 执行） |
| `execute` | `suspend (PluginToolCall) -> PluginToolResult` |

#### 工具暴露方式

工具默认 `DIRECT`：名称、描述、参数会出现在模型的常驻工具列表里。工具数量多、或只偶尔使用的工具可以声明 `PluginToolExposure.DEFERRED`：

```kotlin
pluginTool(
    name = "calendar_sync",
    label = "同步日历",
    description = "同步指定时间范围的日历事件",
    parameters = syncSchema,
    exposure = PluginToolExposure.DEFERRED,
) { call -> ... }
```

`DEFERRED` 工具不会常驻系统提示，只出现在宿主 `tool_search` 的目录里。模型调用 `tool_search(query)` 拿到工具名与参数说明后，再用 `tool_execute(name, arguments)` 执行，宿主不会把工具加载进会话。是否折叠由插件自己决定；插件也可以在页面里提供开关，通过 `requestContributionsRefresh()` 在新会话生效（当前 run 的贡献集合在 run 开始时冻结）。

宿主保留的工具名（安装期即拒绝冲突）包括：引擎默认工具 `read`/`write`/`edit`/`bash`；App 自带工具 `ask_user_question`/`present`/`system_shell`/`get_goal`/`create_goal`/`update_goal`/`job_*`/`timer_*`/`miniapp_*`/`web_search`/`web_fetch`/`calendar`/`screen_*`/`virtual_screen`/`todo_write`；宿主按需工具 `tool_search`/`tool_execute` 与 SDK 的 `list_mcp_resources`/`list_mcp_resource_templates`/`read_mcp_resource`；以及所有 `mcp__` 前缀。

`PluginToolCall`：

- `callId`：本次调用 id，只用于日志/关联。
- `args: JsonObject`：模型传入参数。
- `sessionId`：宿主在 `execute` 时解析出的当前会话 id；没有活动会话时为空字符串。
- `signal: PluginRunSignal?`：取消信号；长任务在阶段边界调用 `signal?.throwIfCancelled()`，或用 `signal?.onCancel { ... }` 注册清理，不要吞取消异常。
- `partial(result)`：流式中间结果，有实时工具卡时展示；`execute` 的返回值才是最终结果。

`PluginToolResult`：

| 构造 | 用途 |
|---|---|
| `textResult(text, details?, isError?)` | 普通文本结果；`details` 是宿主侧结构化元数据（给工具卡/日志用，不进模型上下文），其中 `deepdeep.fileRefs` 是宿主保留键，插件不要读写 |
| `richResult(blocks, details?, isError?)` | 多块结果：`textBlock(text)`、`imageBlock(data, mimeType)`（base64 进模型图片内容）、`fileRefBlock(path, mimeType?)`（content 保留一行 `file: <path> (<mime>)` 文本供模型读取，宿主同时把 `{path, mimeType}` 写入 details 保留键 `deepdeep.fileRefs`，工具卡与历史消息渲染为可点击的文件行，重开会话仍可还原） |
| 结果字段 | `PluginToolResult` 是只读接口（`text`/`blocks`/`details`/`isError`），用上面两个工厂构造；旧的 `PluginToolResult.Text`/`Rich` 构造已移除 |

`details` 里除 `deepdeep.fileRefs` 外由你写入的字段原样透传给工具卡与自定义消息卡；`deepdeep.fileRefs` 由宿主按 `richResult(...)` 里的 `fileRefBlock(...)` 生成，插件不要覆盖它。

`isError = true` 表示一次失败的工具结果（模型可重试/解释）；`execute` 抛异常（取消除外）也会转成 error 结果，所以不要用异常传业务错误。

工具默认随启用插件进入会话；需要会话信息时用 `contributionsFor` 的 live handle：

```kotlin
override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?) = contributions(
    tools = listOf(
        pluginTool(
            name = "session_info",
            label = "Session Info",
            description = "Return the active session id and working directory.",
            parameters = buildJsonObject { put("type", "object") },
        ) { _ ->
            val snapshot = session?.snapshot()
            if (snapshot == null) {
                textResult("No session is bound.", isError = true)
            } else {
                textResult("session=${snapshot.sessionId} cwd=${snapshot.cwd}")
            }
        },
    ),
)
```

- `contributionsFor` 的 `session` 是**会话 live handle**：不要缓存/拷贝字段，也不要假设调用时已有活动会话。
- 在工具 `execute` 里调用 `session?.snapshot()` 取最新的 `PluginSessionSnapshot(sessionId, file, cwd)`；没有活动会话时返回 `null`。
- `session.supportsChatDelivery` 表示该会话是否支持投递自定义卡片（小程序一次性 Agent 等场景不支持）。

## 技能与提示词段

`pluginSkill(id, markdown, files)`：

- `id`：小写 `[a-z][a-z0-9_-]*`；全局技能 id 冲突时先注册者保留。
- `markdown`：完整 SKILL.md 内容，必须带 YAML frontmatter（`name`、`description`），正文写给模型用。
- `files`：附加文件，用 `pluginSkillFile(path, content?, assetPath?)` 构造 —— `path` 是技能目录内相对路径，`content`（内联字符串）与 `assetPath`（包内 `assets/<path>`）二选一；`assetPath` 会在宿主侧读成文本。
- 宿主在插件可贡献时把技能物化到 Agent 技能目录，并用归属标记在停用/卸载时清理；不要在 `skills()` 里自己写文件。

`promptSections(host)` 返回 `promptSection(id, body, priority)` 列表，追加到系统提示词；按 `priority` 升序拼接（小的在前），**同一插件内 id 需唯一**。

### 提示词覆盖（`PromptOverridePlugin`，实验）

v6 新增的实验能力：插件在清单声明 `prompt` capability 后实现 `PromptOverridePlugin`，可以覆盖 Agent 系统提示词的 preamble 与 rules，而不只是追加段落。API 标 `@PluginExperimentalApi`，需要显式 opt-in，实验期间可能调整。

```kotlin
import io.github.whywood.deepdeep.plugin.api.promptOverride as promptOverrideOf

class MyPlugin : DeepDeepPlugin, PromptOverridePlugin {
    override fun promptOverride(host: PluginHost): PromptOverride = promptOverrideOf(
        preamble = "你是一个同步助手。",
        rules = "- 同步前先确认目标目录。",
        sections = listOf(promptSection("sync", "同步完成后汇报文件数。")),
        priority = 10,
    )
}
```

- 清单必须声明 `capabilities: ["prompt", ...]`；未声明时宿主不会调用 `promptOverride()`。
- 合并规则：多个插件按 `priority` 从高到低取 `preamble` / `rules`（高优先级生效，冲突写宿主日志）；`sections` 按 `priority` 升序拼接，与 `promptSections()` 的段落一起进入系统提示词的插件段。
- 预算：单个插件的 `preamble`、`rules`、`sections` 合计 32 KiB，所有插件合计 64 KiB；超出的插件贡献整条跳过并记日志。
- 覆盖只作用于 Agent 的 preamble 与 rules；宿主生成的工作区/环境段落不参与覆盖，始终保留。
- 回调异常由宿主隔离，只记日志，不中断会话。
- 与 `promptSections()` 的分工：`promptSections()` 只追加段落、任何插件都能用；提示词覆盖需要声明 `prompt` capability，用于替换 Agent 的默认身份与规则。

## 事件与 hooks

`PluginHooks` 的全部回调都是 `suspend` 函数，且有默认空实现，只覆盖需要的。hooks 在 `contributions(host)`（全局）或 `contributionsFor(host, session)`（会话级）里返回，两者汇入同一条派发管线。观察事件与决策类型都是宿主实现的只读接口，插件不要自行实现或构造。

**会话级 hooks**：`contributionsFor(host, session)` 返回的 `Contributions` 是该会话生效的工具 + hooks，其中 hooks 按 `pluginId` 覆盖同一插件的全局 hooks。默认实现委托 `contributions(host)`，所以只在 `contributions()` 里返回 hooks 的插件行为不变（每个插件仍只有一条）；只在 `contributionsFor` 里按会话加工具时，返回 `contributions(tools = ...)` 即可（`hooks` 保持默认 `PluginHooks.None` 时沿用该插件的全局 hooks）。会话贡献返回默认的 `PluginHooks.None`（未显式提供 hooks）时沿用该插件的全局 hooks，不会因为写了 `contributionsFor` 就让 hooks 静默失效。每个会话只物化一次：同一会话绑定从建立到关闭只调用一次 `contributionsFor`，工具与会话 hooks 共用这次结果；插件能力变化（启停/页面动作后刷新）会触发宿主在安全点重建会话，重建后才重新物化。不同会话的物化互不阻塞、可能并发调用，插件实现不要依赖跨会话的共享可变状态。

**run 级冻结**：每轮 run 开始时（`RunStartedEvent`）宿主冻结当时生效的 hooks 集合，本轮所有观察事件与变换/闸门都走这份集合：冻结后再启停插件、刷新贡献、新增插件都不影响本轮——已冻结的插件即使被停用，本轮剩余事件仍会派发给它；新启用的插件从下一轮开始生效。run 结束（`RunFinishedEvent`；失败结束同样以 `RunFinishedEvent(error)` 到达）并派发完本轮事件后解冻，下一轮按最新插件快照重新冻结。

注意：冻结期间被停用的插件仍可能收到本轮剩余事件，回调必须能容忍插件已被 `close`/状态已变，不要依赖回调时机做资源清理（清理写在 `close()`/`onDisable` 里）。前提是同一会话同一时刻只有一个活跃 run。

小程序的一次性 Agent 复用同一套 prompt/input/工具钩子与会话绑定，但它的会话事件不经过宿主会话观察事件管线，因此 21 个会话观察事件与 run 冻结只覆盖主会话运行路径；另有 6 个系统事件见下。

### 观察事件（只通知，不能改变流程）

按插件注册顺序串行派发，单个 handler 超时 5s（`PluginHooks.OBSERVE_TIMEOUT_MS`）；超时/异常记录后继续，不影响会话。会话内事件按到达顺序逐条派发，结构化事件优先于流式 delta（delta 过载时丢弃最旧的）。

| 回调（`PluginHooks` 方法） | 事件类型 | 可用字段 | 时机 |
|---|---|---|---|
| `sessionStarted(event: SessionStartedEvent)` | `SessionStartedEvent` | `sessionId`、`title?`、`workspace?` | 会话创建 |
| `sessionClosed(event: SessionClosedEvent)` | `SessionClosedEvent` | `sessionId` | 会话关闭 |
| `runStarted(event: RunStartedEvent)` | `RunStartedEvent` | `sessionId`、`runId` | 一轮 Agent run 开始 |
| `runFinished(event: RunFinishedEvent)` | `RunFinishedEvent` | `sessionId`、`runId`、`error?`（`succeeded`） | run 结束 |
| `turnStarted(event: TurnStartedEvent)` | `TurnStartedEvent` | `sessionId`、`turnId` | 模型回合开始 |
| `turnFinished(event: TurnFinishedEvent)` | `TurnFinishedEvent` | `sessionId`、`turnId`、`failed` | 模型回合结束 |
| `messageStarted(event: MessageStartedEvent)` | `MessageStartedEvent` | `sessionId`、`messageId`、`role`（`USER/ASSISTANT/TOOL/SYSTEM`） | 消息开始 |
| `messageDelta(event: MessageDeltaEvent)` | `MessageDeltaEvent` | `sessionId`、`messageId`、`delta` | 流式增量 |
| `messageFinished(event: MessageFinishedEvent)` | `MessageFinishedEvent` | `sessionId`、`messageId`、`text?` | 消息完成 |
| `toolExecutionUpdate(event: ToolExecutionUpdateEvent)` | `ToolExecutionUpdateEvent` | `sessionId`、`callId`、`toolName`、`phase`（`STARTED/PARTIAL/FINISHED/FAILED`）、`partial?`（`PluginToolResult`） | 工具执行状态 |
| `compactionStarted(event: CompactionStartedEvent)` | `CompactionStartedEvent` | `sessionId` | 上下文压缩开始 |
| `compactionFinished(event: CompactionFinishedEvent)` | `CompactionFinishedEvent` | `sessionId`、`tokensBefore`、`tokensAfter` | 压缩完成 |
| `compactionFailed(event: CompactionFailedEvent)` | `CompactionFailedEvent` | `sessionId`、`error` | 压缩失败 |
| `modelChanged(event: ModelChangedEvent)` | `ModelChangedEvent` | `sessionId`、`model`（`provider/model`） | 模型切换 |
| `thinkingLevelChanged(event: ThinkingLevelChangedEvent)` | `ThinkingLevelChangedEvent` | `sessionId`、`level` | 思考档切换 |
| `retryScheduled(event: RetryScheduledEvent)` | `RetryScheduledEvent` | `sessionId`、`attempt`、`delayMs`、`reason?` | 安排重试 |
| `retrySucceeded(event: RetrySucceededEvent)` | `RetrySucceededEvent` | `sessionId`、`attempt` | 重试成功 |
| `retryExhausted(event: RetryExhaustedEvent)` | `RetryExhaustedEvent` | `sessionId`、`attempts`、`error` | 重试耗尽 |
| `mcpStatusChanged(event: McpStatusChangedEvent)` | `McpStatusChangedEvent` | `server`、`status`（`OK/AUTH_REQUIRED/FAILED/UNKNOWN`） | MCP 状态变化 |
| `mcpToolsChanged(event: McpToolsChangedEvent)` | `McpToolsChangedEvent` | `server`、`tools`（`List<String>`） | MCP 工具变化 |
| `busEvent(event: BusEventReceived)` | `BusEventReceived` | `event`（`PluginBusEvent`：`topic`/`publisher`/`payload`/`publishedAt`） | 插件总线事件 |

共 21 个会话观察回调。

### 系统事件（无会话上下文）

以下 6 个观察事件由宿主在系统侧事件到达时派发，不绑定会话，也不经过 run 冻结；目标是所有在 `contributions()` 里返回了全局 hooks、且满足门槛的插件。单个 handler 超时同样是 5s，超时/异常记录后继续。

| 回调 | 事件类型 | 字段 | 触发条件与门槛 |
|---|---|---|---|
| `notificationReceived(event: NotificationReceivedEvent)` | `NotificationReceivedEvent` | `notification`（`PluginNotificationRecord`）、`removed` | 通知监听收到或移除通知；`notifications` + `components.notificationListener` |
| `notificationAction(event: NotificationActionEvent)` | `NotificationActionEvent` | `notificationId`、`actionId`、`payload` | 用户点击插件自己发出的通知上的动作按钮；只需 `notifications` 能力 |
| `backgroundTask(event: BackgroundTaskEvent)` | `BackgroundTaskEvent` | `taskId`、`kind`、`firedAtMs` | 插件注册的后台任务到点或开机重放；`background` + `components.background` 或 `components.foregroundService` |
| `shareReceived(event: ShareReceivedEvent)` | `ShareReceivedEvent` | `text?`、`streams`（`SharedStream`：`path`/`mimeType`/`name`）、`title?` | 用户把内容分享到 DeepDeep；`share` + `components.share` |
| `deepLinkReceived(event: DeepLinkReceivedEvent)` | `DeepLinkReceivedEvent` | `path`、`arguments` | 收到的深链路径命中 `components.deeplinks` 声明；`deeplink` |
| `appLifecycle(event: AppLifecycleEvent)` | `AppLifecycleEvent` | `foreground` | App 进入前台/后台；`app` |

- 系统事件只发给全局 hooks（`contributions()` 返回的 `PluginHooks`），会话级 hooks 收不到。
- 门槛不满足时事件不会派发，也不会报错：通知监听、无障碍、分享、深链、后台任务都还要求用户在系统里授予对应授权或安装时同意组件声明。

### 变换与闸门（可改变流程）

按插件注册顺序串行执行，单个 handler 超时 2s（`PluginHooks.TRANSFORM_TIMEOUT_MS`）；超时/异常按各阶段的失败策略处理：

| 方法 | 输入 → 输出 | 失败策略 |
|---|---|---|
| `promptTransform(request: PromptRequest): String` | 会话 id + prompt → 新 prompt，链式传递 | fail-open：保留上一个值 |
| `inputTransform(request: InputRequest): InputDecision` | 输入文本 + 图片路径 → `inputContinue(text, imagePaths)` 继续（链式），`inputHandled()` 短路并丢弃本轮输入 | fail-open：保留当前输入 |
| `beforeToolCall(request: ToolCallRequest): ToolCallDecision` | 工具调用（含 MCP/动态工具）→ `toolContinue()` / `toolBlock(reason)` / `toolRewrite(args)`（后续插件基于改写后的 args 继续） | **fail-closed**：超时/异常按阻止调用处理并给出原因 |
| `context(request: PluginContextRequest): String?` | 返回要注入系统提示的检索/记忆文本；多个插件按注册顺序拼接 | fail-open：超时/异常跳过；单插件 8 KiB、总量 32 KiB 截断 |
| `afterToolExecution(request: ToolExecutionResult): ToolResultDecision` | 工具结果 → `resultKeep()` / `resultReplace(result)`（链式替换） | keep：保留上一个结果 |

注意：

- 这些回调在会话关键路径上，必须快速返回；耗时工作丢到 `host.scope` 异步做，不要在里面做网络/重 IO。
- 不要在 transform 里阻塞等待用户；需要用户确认的闸门只做同步判定。
- `inputTransform` 返回的 `imagePaths` 会真正生效：路径对应的图片会被读取并替换/加入本轮输入（无法读取的路径被忽略），无图片时返回空列表。
- `PluginHooks.None` 可用于占位；事件顺序 = 当前生效集合里的加载/注册顺序（按插件安装注册顺序）。
- 用户取消导致的 `CancellationException` 会向上传播，不要吞。

## UI 扩展

宿主固定主壳/导航/聊天骨架，插件可以注册**插件页面**、向两个槽位挂 Compose 绑定，或者贡献输入区动作；不能替换整页，也不能在清单里注册 Activity/Service。插件页面是插件唯一的自绘界面：默认在当前导航栈内渲染，需要独立窗口时用宿主代理的插件窗口（`components().startActivity`）。

### 槽位目录

| slotId | 场景 |
|---|---|
| `tool.card.content:<toolName>` | 指定工具调用卡片的内容区 |
| `chat.message:<customType>` | 自定义消息类型的内容区 |

`slotBinding(slotId, priority, content)`：同一槽位只渲染一个绑定——按 `priority` 从大到小取第一个可渲染的（同优先级按插件注册顺序），其余绑定被覆盖；没有可渲染绑定时宿主用默认视图兜底。

`SlotIds` 提供 helper：

```kotlin
SlotIds.toolCard("template_echo")            // "tool.card.content:template_echo"
SlotIds.chatMessage("card")                  // "chat.message:card"
SlotIds.matches(slotId)                      // 校验完整槽位 id
```

页面与输入区动作是贡献，不是槽位：`PluginUiContributions.pages`（用 `pluginPage(id, title, icon?, content)` 构造）声明插件页面并内联页面内容，`PluginUiContributions.composerActions`（用 `composerAction(id, label, icon?)` 构造）声明输入区动作。`PluginUiContributions` 是只读接口，`pages` / `composerActions` 有默认空实现，插件只实现 `bindings`（以及需要的成员）。插件页列表里有 `pages` 的行点整行进入第一个页面；输入区动作 chip 在聊天输入区显示，点击打开该插件页面（动作 id 与页面 id 相同时打开对应页面，否则打开第一个页面）。

### 页面与参数

`pluginPage(id, title, icon?) { ... }` 声明页面。1.1.0 起页面可以带参数打开，并能从页面带结果返回：

```kotlin
host.ui().openPageWithArguments(
    pageId = "detail",
    arguments = buildJsonObject { put("id", "42") },
    newTask = false,
)
```

- `newTask = true` 时页面在独立的宿主代理窗口（`PluginHostActivity`）打开，适合扫码、拍照、长流程；`false` 在当前导航栈内打开。
- 页面内用 `host.ui().pageContext()` 或 Compose 的 `LocalPluginPageContext.current` 读取 `PluginPageContext(pageId, arguments)`；不在插件页内调用时返回 null。
- 关闭页面用 `host.ui().closePage(result)`，`result` 是可选的 JSON；独立窗口关闭后回到宿主界面。
- 打开别人的页面不行：`openPage(...)`/`openPageWithArguments(...)` 只接受本插件声明的页面 id。
1.2.0 起还可以把页面作为 sheet 直接叠在聊天上打开，适合“点聊天卡片看详情”这类不该离开当前会话的场景：

```kotlin
host.ui().openPageSheet(
    pageId = "detail",
    arguments = buildJsonObject { put("threadId", threadId) },
)
```

- 宿主用底部 sheet 承载同一个 `PluginPage.content()`，不会 push 导航页；下滑、点遮罩、返回或插件调用 `closePage()` 都会关闭 sheet，回到聊天。
- 页面内用 `LocalPluginPageContext.current?.presentation == PluginPagePresentation.SHEET` 判断当前呈现方式；`pageContext().presentation` 默认是 `PAGE`，普通页面与独立窗口不受影响。
- 同一时刻只允许一个插件页 sheet；sheet 内再打开 `DdSheet`（例如写信、选择器）是允许的，关闭顺序由最上层开始。
- 需要完整页面时仍用 `openPage(...)`/`openPageWithArguments(...)`；插件卡片里常见的做法是「点内容开 sheet，footer 打开完整页面」。
- `isDark` 跟随宿主当前主题（内置或插件主题），不再只看系统夜间模式。


### 交互与选择器

`UiService` 提供宿主风格的系统交互，插件不要自己弹 Material 弹窗；这些方法需要页面在前台，后台或工具回调里调用可能返回 null/false：

| 方法 | 返回 | 说明 |
|---|---|---|
| `confirm(uiConfirmRequest(title, message?, confirmLabel?, cancelLabel?, destructive))` | `Boolean` | 确认弹窗，`destructive` 用危险色 |
| `prompt(uiPromptRequest(title, message?, initial, placeholder?, multiline))` | `String?` | 文本输入；取消返回 null |
| `choose(uiChooseRequest(title, listOf(uiChoice(id, label, description?)), message?))` | `String?` | 单选列表，返回所选 `id` |
| `pickDate(uiDatePickRequest(initialMs?, minMs?, maxMs?))` | `Long?` | 日期选择 |
| `pickTime(uiTimePickRequest(initialHour?, initialMinute?, is24Hour))` | `Int?` | 时间选择，返回「小时 * 60 + 分钟」 |
| `pickFiles(uiFilePickRequest(mimeTypes, multiple, maxCount))` | `List<PluginPickedFile>` | 系统文件选择；取消返回空列表 |
| `pickImage(uiImagePickRequest(source = UiImageSource.GALLERY/CAMERA, frontCamera))` | `PluginPickedFile?` | 相册或拍照；取消返回 null |
| `scanQr()` | `String?` | 扫码并返回文本 |
| `pickDirectory()` | `String?` | 选择目录 |
| `snackbar(message, actionLabel?, action?)` | — | 底部轻提示，可带一个动作 |
| `shareFiles(paths, mimeType?)` | — | 分享插件自己的文件（走宿主 FileProvider） |
| `isDark` | `Boolean` | 当前主题是否深色 |
| `openUrl` / `share(shareRequest(...))` / `toast(message)` | — | 打开链接、分享文本、Toast（只用于轻反馈） |

`PluginPickedFile` 字段：`path`（宿主可读的本地路径）、`uri`、`name`、`sizeBytes`、`mimeType`。

```kotlin
val picked = host.ui().pickImage(uiImagePickRequest(source = UiImageSource.CAMERA))
val note = host.ui().prompt(uiPromptRequest(title = "备注", placeholder = "可留空")) ?: return
host.ui().snackbar("已导入", actionLabel = "查看") { host.ui().openPage("home") }
```

- 拍照/相册/语音的运行时权限由宿主统一申请，插件在清单 `permissions` 里声明 `camera`/`microphone` 即可（capability `ui`）。
- `shareFiles` 只接受插件自己可读的路径；分享其他应用的文件请先复制到 `host.dataDir`。

### Compose 渲染

`SlotContent` 是只读接口，用 `composeSlot(render)` 工厂构造；`SlotState` 取决于槽位族：

| 槽位族 | 状态 | 字段 |
|---|---|---|
| 工具卡 | `ToolCardSlotState` | callId/toolName/args/result（结构化参数与结果） |
| 自定义消息 | `CustomMessageSlotState` | messageId/customType/payload |
| 无状态时 | `NoSlotState` | — |

插件页面内容不经过 `SlotState`：`pluginPage(...)` 的 `content` 是普通 Composable（`@Composable () -> Unit`），需要宿主或页面状态直接在闭包里捕获。

兼容判定（任一不满足只禁用该插件的 UI，不拒绝插件本身）：

- 清单 `ui.apiLevel <= HOST_UI_API_LEVEL`（未写 `ui` 块时按宿主等级放行）。
- 清单若填了 `ui.kotlin`，需与宿主 Kotlin **主次版本**一致（如 `2.4`）。
- 清单若填了 `ui.compose`，需与宿主 Compose runtime 版本**完全一致**（不是 Compose BOM 版本）；宿主探测不到自己的 Compose 版本时同样禁用 UI。不确定宿主版本时不要填这两个字段，只写 `ui.apiLevel`。
- UI 被禁用时 `pagesOf` 返回空、槽位不渲染，插件的工具/技能/hooks 仍照常生效。

```kotlin
override fun ui(host: PluginHost) = object : PluginUiContributions {
    override val bindings = listOf(
        slotBinding(
            slotId = SlotIds.toolCard("template_echo"),
            content = composeSlot { state -> MyToolCard(state) },
        ),
    )

    override val pages = listOf(
        pluginPage(id = "home", title = localizedText("Home", "首页"), icon = assetIcon("asset:icon.png")) {
            MyPage(host)
        },
    )
}
```

- 槽位与页面内容都在 Compose 主线程渲染；不要在 composable 里做阻塞 IO，用 `remember` + `LaunchedEffect` 加载数据并处理取消。
- 槽位与页面没有进程级隔离：Compose 内容抛异常会带崩 App（与工具/回调异常一致），插件作者需自行验证。不兼容的绑定只记录禁用原因并回退宿主视图，不影响插件其它能力。
- 外部插件不能引用 App 资源；图标用 `assetIcon("asset:<包内路径>")`，页面标题也可以不带图标。

### 插件界面只使用 `:design`

插件 UI 直接使用 App 的设计系统 `io.github.whywood.deepdeep.design`（`plugin-api` 已通过 `api()` 传递 `design 0.7.0`，插件工程不需要再显式声明坐标）。组件与 App 完全同源，禁止自造样式；新增组件见下表第二、三段：

| 组件 | 用途 |
|---|---|
| `DdGroupCard` | 白色圆角分组卡（20dp 圆角、内边距 16dp、行间发丝线） |
| `DdListRow` | 卡内列表行（标题/副标题/右侧值/图标/可点/trailing） |
| `DdSectionLabel` | 区块小标题 |
| `DdPrimaryButton` | 主按钮（52dp 高、accent 填充，一屏最多一个） |
| `DdTextAction` | 文字按钮（次要动作用 accent/inkSecondary） |
| `DdTextField` / `DdTextArea` | 输入框（52dp 高、聚焦 accent 边框） |
| `DdSwitch` | 开关（52x32、accent 轨道） |
| `DdChip` | 标签/过滤器 |
| `DdSheet` + `DdSheetHeader` | 底部弹层与头部 |
| `DdDialog` | 居中确认弹窗 |
| `DdMenu` / `DdMenuItem` | 下拉菜单 |
| `DdEmptyState` / `DdLoadingBlock` / `DdSpinner` | 空态与加载 |
| `DdHairline` | 分割线 |
| `DdType` | 字号档：`pageTitle`/`heading`/`body`/`bodySmall`/`label`/`button` |
| `deepDeepColors()` | 颜色 token：`paper`/`surface`/`raised`/`ink`/`inkSecondary`/`inkTertiary`/`accent`/`accentSoft`/`danger`/`success`/`warning`/`hairline`/`wash` |

`design 0.7.0` 新增：

| 组件 | 用途 |
|---|---|
| `DdMarkdown(text, live, onFileClick, onLinkClick, onImage)` | Markdown 渲染（标题/列表/表格/代码/图片/脚注）；`live` 用于流式文本，`onImage` 接管图片点击 |
| `DdHtmlText(html, onLinkClick?, imageLabel?, style, color, linkColor)` | 受限 HTML 文本 |
| `DdWebView(url, state = DdWebViewState())` / `DdWebView(DdWebContent.Url/Html, ...)` | 内嵌网页；`state` 提供 `canGoBack`/`goBack`/`reload`/`evaluateJavascript`/`dispose` |
| `DdImage(path / bytes / bitmap, contentDescription, contentScale, shape, maxLongEdge, failure)` | 本地图片加载，带占位与失败态 |
| `DdImageViewer(path / bytes / bitmap, onDismiss)` | 全屏图片查看（Dialog） |
| `DdPullToRefresh(isRefreshing, onRefresh) { ... }` | 下拉刷新容器 |
| `DdSnackbarHostState` + `DdSnackbarHost(state)` | 页面内轻提示；`showSnackbar(...)` 返回结果 |
| `DdFileRow(name, subtitle?, icon, onClick?, enabled, trailing)` | 文件/附件行 |
| `DdDatePicker(selected: LocalDate, onSelect, onDismiss, title, previousMonthLabel, nextMonthLabel)` | 日期选择弹层 |
| `DdTimePicker(selected: LocalTime, onSelect, onDismiss, title, doneLabel)` | 时间选择弹层 |

`design 0.8.0` 新增（需要显式依赖与 `minHostVersion: "1.0.7"`，见下）：

| 组件 | 用途 |
|---|---|
| `DdSheet(onDismiss, fullBleed?, containerColor?, content)` | 重写后的底部弹层：只向下拖拽、遮罩点击/返回均先播完退出动画，高度上限为窗口高减状态栏；内容超长时由内容自己滚动（`verticalScroll`/`LazyColumn`），不要再写 `fillMaxHeight(0.94f)` |
| `DdSwipeRow(key, actions, openKey, onOpenChange, enabled, content)` + `DdSwipeAction(icon, label, tint, background, onClick)` | 列表行滑动操作：双锚点吸附、同一时刻只展开一行、跨阈值触感；行内容自己负责不透明背景 |
| `DdHtmlBlocks(html, loader, onLinkClick, onImageClick, imageLabel, ...)` + `DdHtmlImageLoader` | 带图片的 HTML 正文：按 `<img>` 切块，文本块沿用 `DdHtmlText` 的格式支持；loader 返回 `ImageBitmap`，失败显示占位 |
| `DdInfoTooltip(text, contentDescription?, icon?)` | 行尾 info 图标 + 点击/长按提示，替代常驻说明小字 |
| `LocalDdMotionEnabled` | 系统「减少动态效果」开关；自定义动画在它为 false 时跳过 |

```kotlin
compileOnly("io.github.sadami3066.deepdeep:design:0.8.0")
```

```kotlin
DdHtmlBlocks(
    html = detail.htmlBody.orEmpty(),
    loader = myImageLoader,
    imageLabel = host.t("含图片", "Image"),
    onImageClick = { source -> /* 打开查看器 */ },
)
```

`design 0.9.0` 新增（需要显式依赖与 `minHostVersion: "1.0.8"`，见下）：

| 组件 / API | 用途 |
|---|---|
| `DdStatusRow(label, value, tone, busy)` | 状态摘要行：左侧标签、右侧值，`DdStatusTone` 控制圆点/加载圈颜色；刷新时保留旧值只让该行 busy |
| `DdNoticeBar(text, tone, actionLabel?, onAction?)` | 页面内联提示：`DdNoticeTone.INFO/SUCCESS/WARNING/DANGER`，适合错误 + 重试、未登录说明、操作结果 |
| `DdProgressBar(progress: Float?)` | 下载/安装/同步进度；`null` 为不确定进度，确定性进度传 0..1；尊重减少动态效果 |
| `DeepDeepTheme(colors, fontStyle)` | 用自定义 `DeepDeepColors` 包裹页面（宿主已统一设置，插件通常不需要主动调用） |
| `DdTheme.lightDeepDeep()` | 默认浅色调色板，可作为主题包 `base` 或自定义配色的起点 |
| `DdStatusTone` / `DdNoticeTone` | 上面的状态/提示色调 |

```kotlin
DdGroupCard {
    DdStatusRow("运行环境", if (linuxAvailable) "已就绪" else "不可用", if (linuxAvailable) DdStatusTone.SUCCESS else DdStatusTone.DANGER)
    DdStatusRow("登录状态", if (connected) "已连接" else "未连接", if (connected) DdStatusTone.SUCCESS else DdStatusTone.NEUTRAL, busy = checking)
}
if (error != null) {
    DdNoticeBar(error, DdNoticeTone.DANGER, actionLabel = "重试") { retry() }
}
DdProgressBar(progress = if (downloading) percent else null)
```




直接 `import io.github.whywood.deepdeep.design.DdMarkdown` 等组件（或整包 import），不需要额外声明依赖。

```kotlin
DdPullToRefresh(isRefreshing = refreshing, onRefresh = { refreshing = true }) {
    DdMarkdown(
        text = state.markdown,
        onLinkClick = { host.ui().openUrl(it) },
        onImage = { image -> viewer = image.source },
    )
}
if (viewer != null) DdImageViewer(path = viewer, onDismiss = { viewer = null })
```

日期/时间组件使用 `java.time`（`LocalDate`/`LocalTime`）；使用 design 0.7.0 新组件的插件要写 `minHostVersion: "1.0.6"`，使用 design 0.8.0 组件要写 `"1.0.7"` 并在构建脚本显式声明 `design:0.8.0`；使用 design 0.9.0 组件要写 `"1.0.8"` 并声明 `design:0.9.0`。

约定：

- 不写死颜色与字号，只用 `DdType.*` 与 `deepDeepColors()`；不要直接用 Material3 的 `Card`/`Button`/`OutlinedButton`/`TextButton`/`Switch`/`OutlinedTextField`——它们和 App 的视觉不一致。
- 页面自己是滚动容器：`Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp))`；不要加顶栏标题（宿主已有 64dp 顶栏），需要底部留白时加 24dp。
- 状态变化要稳定：初始化时不要把已知值清空成 loading，刷新时保留旧值只标记局部 busy，避免页面闪。
- 错误就近内联显示（`DdType.bodySmall` + `danger` 色），带上 CLI/接口的最后一段输出；不要只显示退出码，也不要用 Toast。

```kotlin
@Composable
private fun StatusCard(host: PluginHost, status: Status) {
    val colors = deepDeepColors()
    DdGroupCard {
        DdListRow(title = "运行环境", value = status.runtime)
        DdListRow(title = "登录状态", value = if (status.authorized) "已连接" else "未连接")
        if (status.cliVersion != null) {
            DdListRow(title = "CLI", value = status.cliVersion)
        }
    }
    if (status.error != null) {
        Text(status.error, color = colors.danger, style = DdType.bodySmall)
    }
}
```

### 插件界面规范

同一轮升级里所有官方插件按这套骨架收敛，第三方插件也可以照做：

- 页面骨架：横向 20dp、顶部 12dp、**底部 24dp**、组间 16dp；宿主已有 64dp 顶栏，页面内不再重复标题。
- 首屏先说状态：用 `DdStatusRow` 组成摘要（运行环境、组件/CLI、账号、登录状态），值写人类可读的「已就绪 / 待更新 / 未连接」，不写版本号、路径、布尔值；刷新保留旧值，只有该行显示 busy。
- 操作层级：每屏最多一个 `DdPrimaryButton`；次要操作放文字按钮或菜单；退出、删除这类不可逆操作走 `host.ui().confirm(destructive = true)`。
- 反馈：错误就近用 `DdNoticeBar`（危险色）+「重试」，重试要重跑刚才失败的动作，不要只刷新页面；成功/撤销可用 `snackbar` 或结果卡，不要只用 Toast 报路径。
- 进度：安装、下载、同步这类超过一两秒的操作给 `DdProgressBar` + 阶段文字；可取消的提供取消，取消后停在可重试状态。
- 子流程：登录、扫码、验证码、WebView 作为子页面进入和退出要有过渡，系统返回要先退子页；表单就地校验、键盘避让、密码可显示/隐藏。
- 动效：时长与曲线走 `DdMotion`，并尊重 `LocalDdMotionEnabled`；不做布局跳动，不用动画掩盖状态变化。
- 文案：中英成对，不出现 CLI、路径、proot、版本号等开发用语；需要解释的实现细节删掉，确实必要的用 `InfoTooltip`。
- 图标：统一线性 `DdIcons`，不用 emoji；不新增一次性控件，能用 `DdGroupCard`/`DdListRow`/`DdTextAction` 就不用自绘。

### 自主能力

插件工具就是 Agent 的自主路径，页面是给人的可选路径，不要只把能力做在 UI 里：

- 用户明确要求某个动作时，Agent 应该能直接调用工具完成，而不是要求用户进插件页点按钮；例如邮箱提供 `mail_draft` + `mail_send`，发送不需要用户再点一次。
- 工具执行默认不需要每次确认；不要在工具里加“等待用户点一下”的闸门，否则定时、后台任务、目标回合会被卡住。需要更谨慎的动作，把后果写进描述/提示词，让模型在调用前自行判断，或由用户在对话里明确要求。
- UI 升级不能删除或隐藏工具、不能把已有能力改成只有 UI 能做；对每个页面改动检查“Agent 还能不能完成同样的事”。
- 不可逆操作仍要留痕：优先用「先生成对象（草稿/预览）再执行」的工具组合，让用户能在对话历史或卡片里看到将要发生什么。

### 自定义消息卡

在工具执行里用 `host.chat().sendMessage(...)` 把消息写进当前会话，可选择只显示卡片、插话或起新轮：

```kotlin
val result = host.chat().sendMessage(
    sessionId = call.sessionId,
    text = "同步完成，共 12 个文件",
    options = chatMessageOptions(
        triggerTurn = false,
        display = chatDisplay(
            customType = "my.card",
            payload = buildJsonObject { put("title", "同步完成") },
            text = "卡片摘要",
        ),
    ),
)
```

- `sessionId` 用 `PluginToolCall.sessionId`（宿主在 execute 时解析）或 `session.snapshot()?.sessionId`。
- `options.triggerTurn = true`（默认）：会话空闲时起新轮并把 `text` 作为用户消息；忙碌时按 `options.deliverAs` 插话（`STEER`，默认）或排队（`FOLLOW_UP`），返回 `kind = ChatSendKind.QUEUED` 的结果。
- `options.display` 非空时先写卡片消息（等价于 `postCustomMessage`），再触发轮次；`triggerTurn = false` 只落卡片。
- 返回 `ChatSendResult`，`kind` 为 `ChatSendKind.STARTED`/`QUEUED`/`DISPLAYED`；未知会话抛 `IllegalArgumentException`。
- 卡片写入 pi JSONL 的 `custom_message` 条目，重开会话后仍可还原，由 `chat.message:<customType>` 槽位渲染；`postCustomMessage` 仍保留，适合只落卡片。
- 卡片在有工具调用进行时投递，会作为该回合的一个节点按发生顺序内联渲染（不会堆到回答末尾）；会话空闲时投递的卡片仍作为独立消息出现在回合边界，重开会话后的顺序与实时一致。

## 宿主服务

`PluginHost` 暴露 35 个服务访问器（1.0 有 17 个，1.1.0 新增 18 个），其中 `components()` / `credentials()` / `accessibility()` 是实验 API，需要显式 opt-in：

```kotlin
@OptIn(PluginExperimentalApi::class)
val components = host.components()
```

宿主没提供该服务时**抛 `PluginServiceUnavailable`**（不是返回 null），插件应 `runCatching` 后降级。服务已装配但当前不可用（如未装 Linux、未授权 ADB、权限被拒绝）时，通常通过 `available`/`enabled`/`permissionGranted` 字段、返回 null/false 或明确异常表达。

```kotlin
val linux = runCatching { host.linux() }.getOrNull()
if (linux == null || !linux.available) {
    return textResult("Linux 环境不可用", isError = true)
}
```

| 访问器 | 主要能力 | 不可用/失败时 |
|---|---|---|
| `linux()` | `available`、`exec(command, cwd, timeoutMs)`、`stream(command)` → `LinuxResult`/`LinuxChunk` | 未安装/未启用运行环境时 `available=false`；`exec` 记录超时与输出截断；stderr 并入 stdout（`LinuxResult.stderr` 恒为空，失败看 `exitCode` 与输出末尾） |
| `screen()` | `status`、`startVirtual/stopVirtual`、`observe`、`screenshot`、`apps`、`launch`、`tap/tapNode/swipe/scroll/type/key`、`waitFor` | 未授权时能力标记为不可用，调用抛异常；`status` 是同步快照，首次读取会启动宿主侧后台刷新（无停止接口） |
| `notifications()` | `enabled`、`notify(...)`、`notifyChecked(...)`、`updateProgress`、`cancel`；通知监听 `listenerGranted`/`recent`/`received`/`removed`/`dismiss`（见「通知与通知监听」） | 通知权限未开时 `enabled=false`：`notify` 静默忽略、`notifyChecked` 返回 null；点击通知打开插件在 manifest 里声明的第一个页面 |
| `secrets()` | `get/put/remove/clear`，按插件 id 命名空间加密存储 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `network()` | `request(method,url,headers,body,timeoutMs)`、`download(url,targetPath,expectedSha256,onProgress)`；扩展 `get`/`post` | 网络错误抛异常；响应体上限 2 MB、下载上限 512 MB |
| `files()` | 插件数据/工作区文件：`rootDir`、`workspaceDir`、`list/readText/writeText/readBytes/writeBytes/delete/exists/resolve` | 相对路径相对插件 `dataDir`；绝对路径按当前档位解析（Root 档为原生文件系统，见「执行身份与环境」） |
| `sessions()` | `active`、`observe()`（含 busy 变化）、`list()`、`prompt(text, sessionId?)`、`history(sessionId, limit)`、`stop(sessionId)`、`create(title?)`、`rename(sessionId, title)`、`delete(sessionId)`、`open(sessionId)`、`archive(sessionId, archived)` | 无活动会话时 `active=null`、`prompt` 返回 false；非法 id 返回 null/false |
| `jobs()` | `observe()`、`start(command,label,cwd,sessionId)`、`read(jobId)`、`kill(jobId)` | 无会话/宿主未提供时 `start` 返回 null；没有 `list()`，一次性读取用 `observe().first()` |
| `timers()` | `schedule(TimerRequest)`（用 `timerRequest(...)` + `timerOnce`/`timerCron` 构造）、`list()`、`cancel(id)` | 参数非法/宿主未提供时返回 null 或抛异常 |
| `goals()` | `observe()`、`get/create/edit/pause/resume/complete/clear` | 目标状态非法时返回 null |
| `miniapp()` | 小程序：`scan/load/create/edit/delete/run` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `mcp()` | `servers()`、`probe(name)`、`tools(server)`、`call(server,tool,arguments)` | 服务器不存在时返回错误结果或异常 |
| `ui()` | 打开 URL/分享/Toast、页面与参数、交互选择器、Snackbar、`isDark`（见「UI 扩展」） | 单项未接线时抛 `PluginServiceUnavailable("ui.xxx")`；`openPage` 只接受本插件的页面 id，不是 `deepdeep://` 深链 |
| `app()` | `info: AppInfo`（versionName/versionCode/pluginApiLevel/uiApiLevel/language/androidSdkInt/deviceModel/installationId） | 始终可用 |
| `bus()` | 插件总线：`publish(event)`（用 `pluginBusEvent(...)` 构造）、`subscribe(topic?)` → `Flow<PluginBusEvent>` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `logs()` | `log(level, message, error?)` + `debug/info/warn/error` | 始终可用 |
| `chat()` | `sendMessage(...)`：落卡片、起新轮、插话/排队；`sendMessageWithAttachments(..., attachments = listOf(chatAttachment(path, mimeType, name)))`；`postCustomMessage(...)` 只写卡片 | 未知会话抛异常；投递失败返回 false |

会话与聊天的补充模型：`sessions().history(...)` 返回 `PluginMessage(id, role, text, timestampMs, toolName?, images)`；聊天附件用 `chatAttachment(path, mimeType?, name?)` 构造后传给 `sendMessageWithAttachments(...)`。

各服务最小用法：

```kotlin
host.linux().exec("ls -la", cwd = host.dataDir.absolutePath)
host.screen().status.virtual.available
host.notifications().notify(pluginNotification(id = "sync", title = "同步", body = "完成"))
host.secrets().put("token", value)
host.network().get("https://example.com/api")
host.files().writeText("state.json", "{}")
host.sessions().prompt("继续", sessionId)
host.jobs().start("sleep 5", "demo", cwd = null)
host.timers().schedule(timerRequest("提醒", "喝水", timerOnce(System.currentTimeMillis() + 60_000)))
host.goals().get(sessionId)
host.miniapp().scan()
host.mcp().servers()
host.ui().toast("已复制")
host.app().info.versionName
host.bus().publish(pluginBusEvent(topic = "demo.sync", payload = buildJsonObject { }))
host.logs().info("plugin ready")
host.chat().sendMessage(call.sessionId, "同步完成", chatMessageOptions(triggerTurn = false, display = chatDisplay(customType = "my.card", payload = buildJsonObject { }, text = "卡片摘要")))

host.settings().put("last-sync", "2026-11-13T10:00:00Z")
host.device().state.batteryPercent
host.clipboard().setText("已复制")
```

### 通知与通知监听

发布通知用 `pluginNotification` / `pluginNotificationWithActions` 构造；`notifyChecked` 在通知权限关闭时返回 null：

```kotlin
val handle = host.notifications().notifyChecked(
    pluginNotificationWithActions(
        id = "sync",
        title = "同步中",
        body = "已处理 3/12",
        progress = 25,
        ongoing = true,
        actions = listOf(notificationAction("pause", "暂停"), notificationAction("cancel", "取消")),
        payload = buildJsonObject { put("task", "sync") },
    ),
)
host.notifications().updateProgress("sync", 50, "已处理 6/12")
host.notifications().cancel("sync")
```

- `notifyChecked` 返回 `PluginNotificationHandle(id, key)`；`enabled=false` 时返回 null，`notify` 则静默忽略。
- `actions` 的按钮由宿主通知服务接管；声明 `notifications` 能力的插件会在 `notificationAction` hook 收到 `notificationId`/`actionId`/`payload`，不要求通知监听组件。
- 通知主体点击会打开插件在 `provides.pages` / `ui()` 里的第一个页面。

通知监听需要 `permissions: ["notifications.listen"]` + `components.notificationListener`，并由用户在系统「通知使用权」里授予：

| 成员 | 说明 |
|---|---|
| `listenerGranted` | 通知使用权是否已授予 |
| `recent(limit = 50)` | 最近的通知记录 |
| `received()` | 新通知/移除记录的 `Flow<PluginNotificationRecord>` |
| `removed()` | 被移除通知 key 的 `Flow<String>` |
| `dismiss(key)` | 请求移除某条通知（系统允许时） |

`PluginNotificationRecord` 字段：`key`、`packageName`、`appLabel`、`title`、`text`、`bigText`、`subText`、`channelId`、`category`、`postedAtMs`、`ongoing`、`clearable`、`progress`、`actions`。通知内容涉及其他应用的隐私：只申请确实需要的范围，不要持久化与本插件无关的通知内容。

### 执行身份与环境

`host.linux()` 与 `host.files()` 的实际执行身份由用户在「系统访问」页选择的档位决定（普通 / 无线调试（ADB）/ Root），插件不能自行选择或提升：

| 档位 | `linux()` | `files()` |
|---|---|---|
| 普通（LOCAL） | App 沙箱内的 PRoot Alpine，与 App 同 UID | 插件 `dataDir` 与工作区；绝对路径仍受 Android 沙箱约束 |
| 无线调试（ADB） | 同上；系统命令改由宿主 `system_shell` 走 ADB | 同上 |
| Root | 真 root chroot（uid 0），Android 路径是原生路径（`/data`、`/system`、`/sdcard`） | 原生文件系统，绝对路径直接生效 |

- 代码要能在三档下运行：不要自行提权，也不要假设当前一定是 root；命令退出码非 0、文件读写失败都按普通失败处理并给出可读原因。
- Root 档启动时可能在「准备中」或「准备失败」，此时调用 `linux()`/`files()` 会抛 `IllegalStateException`（消息含 `Root Linux is being prepared` / `Root Linux preparation failed`）；提示用户稍后重试，不要崩溃。
- 清单不再声明 `requiresPrivilege`；需要系统能力时只声明对应 `capabilities`（如 `screen`），由宿主在用户选择的档位下注入服务。
- 插件不能自行提权：`privilege().exec(...)` 也只能在用户当前选定档位内执行（`AUTO` 会自动选当前可用档位，显式要求 `ROOT`/`ADB` 而档位不足时返回失败）；不要在代码里假设某个档位一定可用。
- 提示词里的环境说明由宿主按档位生成；插件 `promptSections` 不要写死“已获得 root”之类的假设。

### 设置存储

`host.settings()` 是按插件 id 隔离的键值存储，适合跨进程保留的小状态；机密信息仍放 `secrets()`：

| 方法 | 说明 |
|---|---|
| `get(key)` / `put(key, value)` / `remove(key)` / `clear()` | 字符串键值 |
| `keys()` / `snapshot()` | 全部键 / 全部键值快照 |
| `getJson(key)` / `putJson(key, value)` | JSON 对象读写 |
| `observe(key? = null)` | 设置变化的 `Flow<Map<String, String>>`，`key` 为空时观察全部 |

```kotlin
host.settings().putJson("state", buildJsonObject { put("lastSync", now) })
val json = host.settings().getJson("state")
host.settings().observe("state").collect { state -> render(state) }
```

始终可用；不要存大对象，也不要把用户凭据写进去（用 `secrets()`）。

### 设备与剪贴板

`host.device()`：

| 成员 | 说明 |
|---|---|
| `info: DeviceInfo` | 厂商/品牌/型号/设备代号/Android 版本/SDK/ABI/是否模拟器/语言/时区/屏幕尺寸与密度/刷新率/内存与存储总量/安装 id |
| `state: DeviceState` | 电量/充电/省电/网络类型与连接/亮屏/锁屏/语言/时区/开机时长/剩余内存与存储/当前执行档位 |
| `observe(): Flow<DeviceState>` | 状态变化流 |
| `vibrate(durationMs = 200)` | 振动一次 |
| `openSettings(section: DeviceSettings)` | 跳系统设置页，返回是否可跳转 |

`DeviceSettings`：`APP` / `NOTIFICATIONS` / `ACCESSIBILITY` / `NOTIFICATION_LISTENER` / `OVERLAY` / `USAGE_STATS` / `BATTERY_OPTIMIZATION` / `INSTALL_PACKAGES` / `STORAGE` / `DEVELOPER` / `WIRELESS_DEBUGGING` / `LOCATION`。

`host.clipboard()`：`text()` / `setText(text)` / `uri()` / `setUri(uri)` / `has()` / `clear()`。Android 10+ 后台读写剪贴板受限，返回 null 或被系统忽略，不要依赖剪贴板做跨应用传输。

```kotlin
host.device().observe().collect { state -> if (state.batteryPercent < 20) warnLowBattery() }
host.device().vibrate(80)
host.clipboard().setText("已复制")
```

### 权限与特殊授权

`host.permissions()`：

| 方法 | 说明 |
|---|---|
| `granted(permission)` | 查询某个 `PluginPermission` 常量的授权状态 |
| `suspend request(vararg permissions)` | 申请运行时权限，返回「权限 → 是否授予」 |
| `special(access)` | 查询特殊授权：`PluginAccessState(granted, available, reason)` |
| `openSettings(access)` | 跳对应系统设置页，返回是否可跳转 |

`PluginSpecialAccess`：`NOTIFICATION_LISTENER` / `ACCESSIBILITY` / `OVERLAY` / `USAGE_STATS` / `BATTERY_OPTIMIZATION` / `INSTALL_PACKAGES` / `MANAGE_STORAGE`。运行时权限与特殊授权的清单声明见「权限声明」。

```kotlin
if (!host.permissions().granted(PluginPermission.CAMERA)) {
    host.permissions().request(PluginPermission.CAMERA)
}
val overlay = host.permissions().special(PluginSpecialAccess.OVERLAY)
if (!overlay.granted) host.permissions().openSettings(PluginSpecialAccess.OVERLAY)
```

特殊授权没有权限弹窗，只能引导用户去系统设置；不要在启动时反复跳设置。

### 应用管理

`host.packages()`：

| 方法 | 说明 |
|---|---|
| `suspend list(includeSystem = false)` | 已安装应用列表 |
| `suspend get(packageName)` | 单个应用信息，找不到返回 null |
| `suspend install(apkPath, silent = false)` | 安装 APK；`silent` 需要 Root/ADB 档 |
| `suspend uninstall(packageName, keepData = false)` | 卸载应用 |
| `launch(packageName)` / `openAppInfo(packageName)` | 启动应用 / 打开应用信息页 |
| `suspend icon(packageName, sizePx = 256)` | 读取应用图标 PNG 字节 |

`PluginPackageInfo`：包名/名称/版本/版本号/是否系统应用/是否启用/安装与更新时间；`PackageInstallResult`：`accepted`、`pendingUserAction`、`error`。

```kotlin
val apps = host.packages().list()
val result = host.packages().install(apkPath)
if (!result.accepted) host.ui().snackbar(result.error ?: "安装失败")
```

`silent = true` 只在 Root/ADB 档可用；普通档会走系统安装器并等待用户确认。

### Root / ADB 执行（privilege）

`host.privilege()` 提供三档内的系统级执行，不会越过用户当前选择的档位：

| 成员 | 说明 |
|---|---|
| `access: HostAccessLevel` | 当前生效档位：`LOCAL` / `ADB` / `ROOT` |
| `root: RootState` / `adb: AdbState` | Root 是否授权、ADB 是否已配对/连接及错误原因 |
| `suspend probeRoot()` | 主动探测 Root 状态 |
| `suspend exec(command, level = AUTO, timeoutMs = 30_000)` | 执行命令，返回 `exitCode`/`stdout`/`stderr`/`timedOut`/`truncated` |
| `suspend install(apkPath, silent = true)` / `uninstall(packageName)` | 静默装卸（需对应档位） |
| `openAccessSettings()` / `openWirelessDebugging()` | 跳「系统访问」/无线调试设置 |
| `suspend startAdbPairing()` / `submitAdbPairingCode(code)` / `connectAdb()` | 无线调试配对与连接 |

`PrivilegeLevel`：`AUTO` / `ROOT` / `ADB`。`AUTO` 自动选择当前可用档位；显式要求更高档位而当前不满足时返回失败，不会自动提权。

```kotlin
val root = host.privilege().root
if (root.granted) {
    val result = host.privilege().exec("id", level = PrivilegeLevel.ROOT)
    host.logs().info("exit=${result.exitCode} out=${result.stdout.trim()}")
}
```

命令失败看 `exitCode`/`stderr`，不要对输出做字符串匹配；ADB 配对需要用户在系统里开启无线调试并确认配对码。

### Intent 与动态广播

`host.intents()`：

| 方法 | 说明 |
|---|---|
| `startActivity(intent)` / `sendBroadcast(intent)` | 启动 Activity / 发送广播 |
| `suspend startActivityForResult(intent, timeoutMs = 120_000)` | 等待结果，返回 `PluginActivityResult(resultCode, data, cancelled, timedOut)` |
| `startService(intent)` / `stopService(intent)` | 启动/停止服务（受系统后台限制） |
| `canResolve(intent)` / `suspend resolve(intent)` / `suspend queryActivities(intent)` | 解析目标 `ResolvedIntent(packageName, className, label, exported)` |

动态广播用 `host.components().registerReceiver(filter)`（实验 API）：

```kotlin
@OptIn(PluginExperimentalApi::class)
val handle = host.components().registerReceiver(IntentFilter(Intent.ACTION_BATTERY_CHANGED))
host.scope.launch { handle.events().collect { intent -> onBattery(intent) } }
// onDisable()/close(): handle.close()
```

- `PluginReceiverHandle`：`id`、`events(): Flow<Intent>`、`close()`；插件停用/退出前必须 `close()`。
- Android 10+ 限制后台启动 Activity/Service；从页面或前台流程调用，失败返回 false。
- 只解析/启动用户可见、与插件功能相关的 Intent，不要扫描无关应用。

### 独立窗口与前台服务（实验）

插件不能自己注册 Activity/Service；需要独立窗口或长驻服务时用宿主代理：

```kotlin
@OptIn(PluginExperimentalApi::class)
host.components().startActivity(
    pluginActivityOptions(pageId = "scanner", arguments = buildJsonObject { }, newTask = true),
)
```

- `pluginActivityOptions(pageId, arguments, newTask)` 让宿主在 `PluginHostActivity` 里打开本插件的页面；页面仍是 `ui()` 声明的 `PluginPage`，`pageContext()` 能读到传入参数。
- 前台服务：`host.background().startForeground(foregroundServiceOptions(id, title, text, ongoing, progress))`、`updateForeground(...)`、`stopForeground(id)`；需要 `components.foregroundService: true` + capability `background`。
- 代理组件只为插件页面与前台任务服务：不能自定义 Activity 布局、不能替换宿主主题、不能注册清单组件。

### 后台任务与开机回调

`host.background()`：

| 方法 | 说明 |
|---|---|
| `suspend schedule(id, kind, triggerAtMs?, intervalMs?, requiresNetwork = false, requiresCharging = false)` | 注册任务，返回是否成功 |
| `suspend cancel(id)` / `suspend tasks()` | 取消 / 列出任务 |
| `startForeground` / `updateForeground` / `stopForeground` | 前台服务 |
| `batteryOptimizationIgnored` / `requestIgnoreBatteryOptimizations()` / `openBatteryOptimizationSettings()` | 电池优化状态与引导 |

`PluginBackgroundKind`：

| 值 | 语义 |
|---|---|
| `ONCE` | 一次性触发（触发时间或延迟） |
| `PERIODIC` | 周期任务（WorkManager，系统最小间隔约 15 分钟） |
| `EXACT` | 精确定时（AlarmManager，受系统省电与精确闹钟权限限制） |
| `BOOT` | 开机后触发一次，也用于重启后重新注册周期任务 |

```kotlin
host.background().schedule(
    id = "digest",
    kind = PluginBackgroundKind.PERIODIC,
    intervalMs = 6 * 60 * 60 * 1000,
    requiresNetwork = true,
)
```

任务触发时派发 `backgroundTask` hook（`taskId`/`kind`/`firedAtMs`），需要 capability `background` 且 `components.background: true`。Doze/省电策略会延后任务；需要稳定执行时引导用户忽略电池优化。

### 定位

`host.location()`：`available`、`permissionGranted`、`lastKnown(): PluginLocation?`、`current(timeoutMs = 10_000): PluginLocation?`、`updates(minIntervalMs = 1_000, minDistanceM = 0f): Flow<PluginLocation>`、`geocode(query, limit = 5): List<PluginGeocode>`、`reverseGeocode(location): PluginGeocode?`。

`PluginLocation`：纬度/经度/精度/来源/时间，可选海拔/速度/方向；`PluginGeocode`：经纬度/名称/国家/行政区/城市。需要 capability `location` + `permissions` 里的 `location.coarse`/`location.fine`；未授权时返回 null/空列表。

```kotlin
val here = host.location().current() ?: return textResult("定位不可用", isError = true)
val place = host.location().reverseGeocode(here)
```

### 联系人

`host.contacts()`：`permissionGranted`、`suspend list(limit = 0)`、`search(query, limit = 20)`、`get(id)`、`create(draft)`、`update(id, draft)`、`delete(id)`；草稿用 `pluginContactDraft(displayName, phones, emails, organization)` 构造。

`PluginContact`：id/显示名/电话/邮箱/组织/备注/星标；`PluginContactField`：value/label/type。需要 capability `contacts` + `permissions` 里的 `contacts.read`/`contacts.write`；未授权抛 `SecurityException`。

```kotlin
val contacts = host.contacts().search("张")
val created = host.contacts().create(pluginContactDraft(displayName = "张三", phones = listOf("13800000000")))
```

### 短信

`host.sms()`：`available`、`canRead`、`canSend`、`suspend threads(limit = 20)`、`suspend messages(threadId?, address?, limit = 50)`、`suspend send(address, text, subscriptionId?)`、`suspend markRead(threadId)`、`suspend delete(threadId)`。

`PluginSmsThread`：会话 id/号码/联系人名/摘要/时间/未读数；`PluginSmsMessage`：id/会话 id/号码/正文/时间/是否接收/是否已读。需要 capability `sms` + `permissions` 里的 `sms.read`/`sms.send`。

- 发送是敏感操作：先在页面里确认内容与号码，失败返回 false 并提示原因（默认短信应用/运营商限制可能阻止发送）。
- 不要批量读取或删除用户短信；只处理用户明确指定的会话。

```kotlin
val thread = host.sms().threads(limit = 5).firstOrNull() ?: return
host.sms().messages(threadId = thread.id, limit = 20)
if (host.sms().canSend) host.sms().send(thread.address, "收到，稍后回复")
```

### 日历

`host.calendar()`：`permissionGranted`、`suspend calendars()`、`suspend events(fromMs, toMs, calendarIds = emptyList(), limit = 200)`、`suspend get(eventId)`、`suspend create(draft)`、`suspend update(eventId, draft)`、`suspend delete(eventId)`；草稿用 `pluginCalendarEventDraft(title, startMs, endMs, allDay = false, description?, location?, calendarId?, timeZone?, reminders = emptyList())` 构造。

`PluginCalendar`：id/名称/账号/显示名/颜色/可见/默认；`PluginCalendarEvent`：id/日历 id/标题/起止/全天/描述/地点/时区/提醒。需要 capability `calendar` + `permissions` 里的 `calendar.read`/`calendar.write`。

```kotlin
val draft = pluginCalendarEventDraft(
    title = "周会",
    startMs = start,
    endMs = start + 3_600_000,
    reminders = listOf(10),
)
host.calendar().create(draft)
```

### 媒体

`host.media()`：

| 方法 | 说明 |
|---|---|
| `saveToDownloads(fileName, mimeType, bytes)` | 字节写入下载目录，返回路径 |
| `saveFileToDownloads(path, fileName?, mimeType?)` | 已有文件复制到下载目录 |
| `saveImageToGallery(fileName, mimeType, bytes)` | 图片写入系统相册 |
| `scanFile(path)` | 触发媒体扫描 |
| `open(path)` / `share(path, mimeType?)` / `preview(path)` | 打开/分享/预览文件 |
| `readImage(path, maxSizePx = 2_048)` / `thumbnail(path, maxSizePx = 512)` | 图片解码（有尺寸上限） |
| `importUri(uri, fileName?)` | 把外部 URI 导入插件目录，返回本地路径 |

需要 capability `media`；是否还需要额外授权取决于宿主实现与系统版本，需要直接访问共享存储时声明 `permissions` 里的 `manage-storage`（用户在系统设置开启「所有文件访问」）。

```kotlin
val saved = host.media().saveToDownloads("report.csv", "text/csv", bytes)
host.media().share(saved!!, "text/csv")
```

### 语音

`host.speech()`：`speakAvailable`、`recognizeAvailable`、`suspend speak(text)`、`stopSpeaking()`、`speaking(): Flow<Boolean>`、`suspend recognize(timeoutMs = 30_000): PluginSpeechResult(text, error)`、`openVoiceSettings()`。

需要 capability `speech`；语音识别另需 `permissions` 里的 `microphone`（capability `ui`）。TTS 引擎或识别服务缺失时对应 `available=false`，返回 false/错误结果。

```kotlin
if (host.speech().speakAvailable) host.speech().speak("同步完成")
val heard = host.speech().recognize()
```

### 网页

`host.web()`：`available`、`suspend search(query, limit = 8): List<PluginWebResult(title, url, snippet?)>`、`suspend fetch(url, maxChars = 20_000): PluginWebPage(url, title?, text, truncated)`。

需要 capability `web`；搜索/抓取走宿主的网页服务与解析，失败返回空列表/null。已经用 `network()` 自己请求时优先用自己的实现（可控超时与解析）。

```kotlin
val results = host.web().search("Android 通知监听")
val page = results.firstOrNull()?.let { host.web().fetch(it.url) }
```

### 宿主凭据（实验）

`host.credentials()`（`@PluginExperimentalApi`）只读宿主里已配置的凭据，供插件在用户知情的前提下复用：

| 方法 | 说明 |
|---|---|
| `providers(): List<PluginProviderCredential>` / `provider(id)` | 服务商配置：id/名称/baseUrl/apiKey/authKind |
| `mcpTokens(): List<PluginMcpToken>` / `mcpToken(server)` | MCP 令牌：server/url/accessToken/refreshToken/tokenType/expiresAtMs |
| `hostValues(): Map<String, String>` | 宿主通用凭据键值 |

**敏感性**：这些是用户真实凭据。只在执行用户请求时读取，不落盘、不写日志、不发给第三方；安装确认页与插件能力列表会展示该 capability，让用户知道插件会读取宿主凭据。

```kotlin
@OptIn(PluginExperimentalApi::class)
val provider = host.credentials().providers().firstOrNull()
val mcp = host.credentials().mcpTokens().firstOrNull()
```

### 无障碍（实验）

`host.accessibility()`（`@PluginExperimentalApi`）：

| 成员 | 说明 |
|---|---|
| `enabled` | 无障碍服务是否已开启 |
| `events(): Flow<PluginAccessibilityEvent>` | 无障碍事件流：eventType/packageName/className/text/contentDescription/timeMs |
| `performGlobalAction(action)` | 全局动作：`BACK`/`HOME`/`RECENTS`/`NOTIFICATIONS`/`QUICK_SETTINGS`/`POWER_DIALOG` |

需要 capability `accessibility` + `permissions: ["accessibility"]` + `components.accessibility: true`，并由用户在系统无障碍设置里开启 DeepDeep 的服务。只读取功能必需的事件，不要采集或外发其他应用的文字。

```kotlin
@OptIn(PluginExperimentalApi::class)
if (host.accessibility().enabled) {
    host.accessibility().events().collect { event -> onAccessibilityEvent(event) }
    host.accessibility().performGlobalAction(PluginGlobalAction.BACK)
}
```

**总线**：`host.bus().publish(pluginBusEvent(topic, payload))` 广播给所有插件（`publisher` 由宿主按插件 id 覆盖）；`subscribe("topic")` 拿 `Flow`，同时 `PluginHooks.busEvent(...)` 观察类回调也能收到。事件只用于插件间协作与轻量通知，不要用它传大载荷或做同步 RPC。

## 页面与状态

插件页面是插件唯一的交互界面，也是插件维护自身状态的地方：

- 页面由 `PluginUiContributions.pages` 声明，`pluginPage(...)` 的 `content` 就是页面内容（宿主直接渲染，不经过槽位）；列表里点整行进入第一个页面，页面之间用 `host.ui().openPage(...)` / `openPageWithArguments(...)` 切换（只能打开本插件自己的页面）。
- 需要独立窗口或长流程时用 `openPageWithArguments(..., newTask = true)` 或 `components().startActivity(pluginActivityOptions(...))`：宿主在 `PluginHostActivity` 里渲染同一页面，页面内从 `pageContext()` / `LocalPluginPageContext` 读参数，关闭时 `closePage(result)` 携带 JSON 结果。
- 插件数据写在 `host.dataDir` 与 `host.settings()`（`host.files()` 相对路径以 `dataDir` 为根），机密凭据写 `host.secrets()`；不要在页面里缓存需要跨进程保留的状态。
- 页面里做完安装/登录/开关等会影响工具或技能的动作后调用 `host.requestContributionsRefresh()`：宿主重跑一次物化并触发会话重建，贡献在安全点生效。例如插件在页面里装好 CLI 后再返回工具列表，下一次会话就能看到新工具。
- 页面可以调用所有已声明的宿主服务；交互优先用 `host.ui()` 的确认/输入/选择器（见「交互与选择器」），不要自造 Material 弹窗，与 App 视觉不一致。
- `PluginPage.content` 不接收 `SlotState`；需要活跃会话时调 `host.sessions().active`，需要页面参数时读 `pageContext()`。
- `host.requestContributionsRefresh()` 是异步触发、不等待完成；需要在界面上确认结果时用插件自己的状态标记，不要假设返回时就已物化。
- 页面离开后宿主会取消页面内的 `LaunchedEffect` 等协程；长任务挂 `host.scope`，不要依赖页面生命周期保活。
- `network().get`/`post`、`PluginAvailability.isReady` 是扩展函数；显式 import 风格下要分别 import `get`/`post`/`isReady`（或整包 `io.github.whywood.deepdeep.plugin.api.*`）。

## 兼容与迁移

### 1.1.x → 1.2.0（纯加法）

`plugin-api 1.2.0` 不升 apiLevel：现有插件不需要改代码或重新编译。使用新能力（主题包、`openPageSheet`、页面呈现模式、design 0.9.0 组件）时：依赖升到 `plugin-api 1.2.0`/`design 0.9.0`，清单写 `minHostVersion: "1.0.8"`；主题包在 `provides.themes` 声明 id 并放 `assets/themes.json`。

### 1.0.x → 1.1.0（纯加法）

`plugin-api 1.1.0` 不升 apiLevel：已发布的 1.0.x 插件不需要改代码或重新编译，在 1.0.4+ 宿主上照常运行。只有使用 1.1.0 新能力（需要宿主 1.0.6+）时才需要迁移：

1. 依赖升到 `1.1.0`（配套 `design 0.7.0`，经 `plugin-api` 的 `api()` 传递，插件工程仍只需声明 `plugin-api`）。
2. `minHostVersion` 升到 `"1.0.6"`；只在旧宿主上运行、且不调用 1.1.0 新增 API 的插件可以继续用原来的门槛（如 `"1.0.4"`）。
3. 在 `capabilities` 里声明新能力；运行时权限写 `permissions`，系统组件写 `components`，并与对应 capability 成对声明（见「权限声明」「组件声明」）。
4. 使用实验 API（`components()` / `credentials()` / `accessibility()`、`PromptOverridePlugin`）时加 `@OptIn(PluginExperimentalApi::class)`。
5. 页面参数、交互选择器、通知动作、系统事件按新章节接入；design 0.7.0 组件直接从 `io.github.whywood.deepdeep.design` import。
6. 升插件版本、重新签名并更新市场条目；运行门槛由 `minHostVersion` 与宿主实际能力保证。

### 从 API level 5 迁移到 level 6

`plugin-api 1.0.0` 是破坏性升级：宿主 1.0.4 起只接受 `apiLevel = 6`，level 5 插件在安装时被拒绝并提示更新。迁移按下面的对照改代码，然后重编译并升清单（`apiLevel: 6`、`minHostVersion: "1.0.4"`、`ui.apiLevel: 2`）；用同一发布者密钥重新签名，已装用户才能升级。

| level 5（`0.8.1`）写法 | level 6（`1.0.0`）写法 |
|---|---|
| `Contributions(...)` | `contributions(...)` |
| `PluginTool(...)` | `pluginTool(...)`（构造器不再公开） |
| `PluginToolResult.Text(...)` / `.Rich(...)` | `textResult(...)` / `richResult(...)` |
| `ToolBlock.Text` / `.Image` / `.FileRef` | `textBlock(...)` / `imageBlock(...)` / `fileRefBlock(...)` |
| `PluginSkill(...)` / `PluginSkillFile(...)` | `pluginSkill(...)` / `pluginSkillFile(...)` |
| `PromptSection(...)` | `promptSection(...)` |
| `LocalizedText.Literal(...)`（或 `literal(...)`） | `localizedText(...)` |
| `IconRef.Asset(...)` | `assetIcon(...)` |
| `SlotBinding(...)` / `SlotContent.Compose { }` | `slotBinding(...)` / `composeSlot { }` |
| `PluginPage(...)` / `ComposerAction(...)` | `pluginPage(...)` / `composerAction(...)` |
| `PluginNotification(...)` | `pluginNotification(...)` |
| `TimerRequest(...)` + `TimerTrigger.Once/Cron` | `timerRequest(...)` + `timerOnce(...)` / `timerCron(...)` |
| `PluginBusEvent(...)` | `pluginBusEvent(...)` |
| `ChatMessageOptions(...)` / `ChatDisplay(...)` | `chatMessageOptions(...)` / `chatDisplay(...)` |
| `PluginAvailability` 直接构造 | `available()` / `unavailable(reason)` |
| `PluginEvent.X`（sealed 子类） | 顶层接口 `XEvent`（如 `SessionStartedEvent`） |
| `InputDecision.Continue/Handled` | `inputContinue(...)` / `inputHandled()` |
| `ToolCallDecision.Continue/Block/Rewrite` | `toolContinue()` / `toolBlock(reason)` / `toolRewrite(args)` |
| `ToolResultDecision.Keep/Replace` | `resultKeep()` / `resultReplace(result)` |
| `PluginCapability` 枚举成员 | value class 常量（wire 名不变，`plugin.json` 不用改） |
| `MessageRole` / `ToolPhase` 等枚举 | value class 常量（`USER` / `STARTED` 等名字不变） |
| `PluginUiContributions` 实现对象 | 接口不变，`bindings` / `pages` 用上面工厂构造 |
| `host.text(source, *args)` | 不变（`vararg Any` 保留，参数只传字符串/数字） |

清单与工程：

- `apiLevel: 6`、`minHostVersion: "1.0.4"`、`ui.apiLevel: 2`；`entryClass` 与入口类无参构造不变。
- `capabilities` 可选新增 `prompt`（提示词覆盖）；其余 wire 名不变。
- 依赖改为 `compileOnly("io.github.sadami3066.deepdeep:plugin-api:<版本>")`（`<版本>` 取 `deepdeep-maven` 最新发布），按宿主 Kotlin/Compose 版本重新编译；`.ddp` 内容变化后必须重新签名。
- manifest 解析、校验与版本比较已移到宿主（宿主仓库 `app/plugin/host/external/`），SDK 里不再有 `PluginManifestCodec` / `PluginValidation` / `PluginVersion`；`PluginManifest` / `Provides` / `UiManifest` / `PublisherInfo` 是宿主构造的只读接口，插件不构造。
- 兼容承诺从 1.0.0 起生效：level 6 内只做二进制兼容的加法；需要删改公开 API 时升 `apiLevel`（见 `plugin-api/COMPATIBILITY.md`）。

## ABI 与版本

- 当前 `plugin-api 1.1.0` / API level 6：`PLUGIN_API_VERSION = "1.1.0"`、`PLUGIN_API_LEVEL = 6`、`BASELINE_API_LEVEL = 6`、`MIN_SUPPORTED_API_LEVEL = 6`。宿主加载时要求 `plugin.apiLevel` 在 `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL`（当前 6..6）内；未写 `apiLevel` 按 `BASELINE_API_LEVEL`（6）处理。宿主 1.0.4 起拒绝 level 5 插件并提示按新 SDK 重编译。
- 兼容矩阵：

| apiLevel | plugin-api / design | 宿主 | 说明 |
|---|---|---|---|
| 5 | 0.8.1 / 0.6.1 | ≤1.0.3 | 历史契约，1.0.4 起拒绝 |
| 6 | 1.0.x / 0.6.1 | 1.0.4+ | 工厂式构造、事件接口、prompt 覆盖实验能力 |
| 6 | 1.1.x / 0.7.x | 1.0.6+ | 加法：38 项 capability、18 个新服务访问器、permissions/components、通知监听与系统事件、页面参数与交互选择器、design 0.7.0 组件 |

- **兼容契约**：同一 `apiLevel` 内只允许二进制兼容的加法（新增可选接口、带默认实现的方法、value class 常量、工厂函数、manifest 可选字段），删除/改名/改签名/改运行时语义必须升 `apiLevel`。完整规则见 `plugin-api/COMPATIBILITY.md`。
- `minHostVersion` 只比较 `major.minor.patch`、忽略后缀（`1.0.4-dev` 视为 `1.0.4`），避免开发版被自己的门槛卡住；宿主版本号来自 App 的 `BuildConfig.VERSION_NAME`，当前宿主版本为 1.0.6+；旧能力的门槛（如 `1.0.4`）继续有效。
- 门禁：`./scripts/api-compat.sh` 导出 `plugin-api` 与 `design` 的公开 ABI，与 `plugin-api/compat/`、`design/compat/` 基线比对，删除或签名变化即失败；`--update` 只在升 level 或首次建基线时用。破坏性变更必须同时升 `apiLevel`、更新基线、写 `plugin-api/CHANGELOG.md` 与宿主 release notes。
- 实验 API 用 `@PluginExperimentalApi`（`@RequiresOptIn`）标记，插件需显式 opt-in；实验期间允许调整，转正时只去注解不改签名。当前实验能力：`PromptOverridePlugin`、`components()`、`credentials()`、`accessibility()`。
- `design` 与 `plugin-api` 一起受 ABI 门禁保护；使用 design 0.7.0 新组件的插件要按宿主版本写 `minHostVersion`（1.0.6+）。
- 插件必须 `compileOnly` 依赖宿主库；宿主 parent-first 保证 `plugin-api` 与 Compose 只存在宿主那一份。
- Compose 没有跨版本二进制 ABI 承诺：插件要按宿主 Kotlin/Compose 版本重新编译；版本不一致只禁用该插件的 UI，不拒绝插件。

## 安全模型

- **同进程、同 UID、无沙箱**：插件代码与 App 代码同等权限，能读写 App 私有数据、访问宿主已授权的系统能力、反射宿主内部类、起线程、结束进程。同进程没有可信的权限收窄机制；签名、`capabilities`、`permissions`、`components` 都只用于声明、展示与门禁，不是沙箱。
- **安装即信任**：首装确认页让用户看到发布者、能力、权限与组件声明，并明确「插件拥有 App 全部权限」；签名只解决包是否被篡改、是否同一发布者，不解决作者是否可信。
- **权限与特殊授权**：运行时权限由宿主统一申请；通知使用权、无障碍、悬浮窗、使用情况、安装未知应用、所有文件访问等特殊授权必须在系统设置里由用户手动开启，插件只能引导跳转，不能静默获得。
- **凭据（实验）**：`credentials()` 能读到宿主真实的服务商与 MCP 凭据；只在执行用户请求时读取，不落盘、不写日志、不外发。能力页与安装确认会展示该 capability。
- **适用来源**：可信的第一方/个人插件包；第三方工具源用 MCP，不走 `.ddp`。
- **完整性**：安装记录钉死整包 sha256，每次装载前复核；canonical digest 验签防篡改；更新必须同 `keyId`；失败保留上一可用版本。
- **宿主不暴露的东西**：宿主内部加密密钥、ADB 配对私钥、pi SDK 内部类型。插件拿到的是 `PluginHost` 契约与 `host.context`；`host.context` 仍是 App 的 Context，直接调用 Android API 的风险由插件承担。
- **原生网络**：HTTP 用 `host.network()`；非常规协议（IMAP/SMTP、WebSocket 等）可用 JVM 标准库直连，须在清单声明 `network`，保留 TLS 证书校验，并遵守系统明文 HTTP 策略；不要在页面或工具里阻塞主线程。
- **插件作者的义务**：不读与本插件无关的用户数据；不把凭据写日志；删除/覆盖用户数据的动作要可逆或在页面里明确确认；不要用 `System.exit`/`Runtime.halt` 结束进程。
- **崩溃**：工具/回调异常会被宿主隔离成 error/失败记录；但插件在主线程、Compose 槽或工具执行中把进程搞崩仍会带崩 App（没有进程级隔离）。

## 调试与常见错误

构建/打包/签名（模板工程在 DeepDeep-Plugins 的 `template/`，产物 `template/build/plugin/plugin-template.ddp`）：

```bash
cd template && ./gradlew assemblePlugin signPlugin verifyPlugin

# 官方插件仓（DeepDeep-Official-Plugins 根）
./scripts/check.sh        # 构建、签名并验签全部五个官方插件
```

排查路径：

- **安装前自查**（`verifyPlugin` 的 `Signature OK` 只验证签名与 digest 一致，**不代表能被安装**；导入前逐项确认）：
  - `id` 满足反向域名正则（至少两段，如 `com.example.myplugin`）；
  - `version` 是 `major.minor.patch[后缀]`；`apiLevel` 为 6（≤ 宿主 `PLUGIN_API_LEVEL`）；`minHostVersion` 不高于宿主版本（1.1.0 新能力写 `1.0.6`，只用旧能力可保持 `1.0.4`）；
  - `permissions`/`components` 与实际使用一致，且与 `capabilities` 成对（安装期校验）；
  - `entryClass` 类名合法、类在某个根 `classes*.dex` 里且有无参构造；
  - `provides` 与实际贡献一致（tools/skills/uiSlots/pages），工具名不与宿主保留名冲突；
  - `provides.themes` 与 `assets/themes.json` 的 id 完全一致，主题字段/颜色/对比度/体积过校验；
  - 槽位 id 过 `SlotIds.matches`（`tool.card.content:<tool>`、`chat.message:<customType>`），插件页面 id 过 `pages` 校验；
  - 包内 `assets/plugin.json` 路径正确、至少一个根 `classes*.dex`。
- **安装被拒**：确认页/提示会给出原因；常见为清单校验码（`INVALID_ID`/`INVALID_VERSION`/`INVALID_PROVIDES` 等）、签名错误（包被改动/公钥与 keyId 不匹配/缺签名文件）、发布者不匹配（升级换了密钥）、工具名冲突（`provides.tools` 与宿主或已装插件冲突）、版本冲突（版本不比已装新）、主题包错误（`THEMES_INVALID`：schema/token/颜色/对比度/体积/id 不一致）。
- **安装被拒（权限/组件）**：`INVALID_PERMISSION` 权限取值未知或重复；`PERMISSION_WITHOUT_CAPABILITY` 权限没有对应 capability；`COMPONENT_WITHOUT_CAPABILITY` components 字段类型/结构非法；`COMPONENT_WITHOUT_CAPABILITY` 开启了组件但 capability 缺失；`INVALID_DEEPLINK` 深链路径格式非法或重复。
- **调用服务抛 `PluginServiceUnavailable`**：清单 `capabilities` 没有声明该服务访问器；capability 在安装期生效，运行中不能补声明，补上后重新安装。
- **权限/特殊授权拿不到**：运行时权限用 `permissions().request(...)`；通知使用权、无障碍、悬浮窗等特殊授权只能 `permissions().openSettings(...)` 引导用户在系统设置开启，`special(...).granted` 才会变 true。
- **通知监听没数据**：需要 `permissions: ["notifications.listen"]`、`components.notificationListener: true` 与 capability `notifications`，并在系统「通知使用权」里授权；`listenerGranted=false` 时 `recent`/`received` 为空。
- **分享/深链/后台任务不触发**：检查 capability、`components` 开关与 `deeplinks` 路径匹配（相等或以 `声明/` 开头）；系统事件只发给全局 hooks。
- **后台任务延迟或不动**：WorkManager 周期任务受 Doze 与省电策略影响（最小约 15 分钟）；需要稳定执行时申请忽略电池优化并引导用户。
- **实验 API 编译报错**：`components()`/`credentials()`/`accessibility()` 与 `PromptOverridePlugin` 需要 `@OptIn(PluginExperimentalApi::class)`。
- **页面参数读不到**：`pageContext()`/`LocalPluginPageContext` 只在插件页面渲染期间有效，要在页面内容里读取。
- **用旧 API 编译失败**：`Unresolved reference` 落在 `PluginTool(` / `PluginToolResult.Text` / `PluginEvent.SessionStarted` / `literal(` / `IconRef.Asset` 等旧符号上 → 按「从 API level 5 迁移到 level 6」逐项换工厂与接口。
- **level 5 插件被拒**：1.0.4 起宿主只接受 `apiLevel = 6`；旧包必须用 `plugin-api 1.0.0` 重新编译、升 `apiLevel` 与 `minHostVersion` 并重新签名。
- **装载失败**：`ClassNotFoundException` → `entryClass` 写错或被 minify；`NoSuchMethodException` → 缺无参构造；`NoClassDefFoundError` → 宿主版本不一致或把宿主依赖打进了包。
- **行为不符**：`host.logs()` 会进宿主日志（logcat tag `DeepDeepPlugin/<pluginId>`）；插件抛出的异常宿主会隔离并显示在插件行错误里。
- **刷新后工具/技能没变**：页面里改完状态要调用 `host.requestContributionsRefresh()`；忙碌会话在本轮结束后才重建，当前轮不会中途变化。
- **文件行不显示**：`fileRefBlock(...)` 的 path 按插件给出的绝对路径原样使用；文件行由宿主从 details 保留键 `deepdeep.fileRefs` 还原，不要自己写这个键；若在 `afterToolExecution` 里 `resultReplace(...)` 换掉了结果并丢掉 details，文件行会随之消失。
- **hooks 没生效 / 停用后仍收到事件**：hooks 在 `contributions()` 或 `contributionsFor()` 返回都生效；只写 `contributionsFor` 且返回默认 `PluginHooks.None` 时沿用全局 hooks；会话级贡献每个会话只物化一次，插件启停/刷新在安全点重建会话（本轮结束后）才重新物化；当前 run 内按冻结集合派发，停用/卸载的插件仍会收到本轮剩余事件，属预期行为。
- **技能没出现**：插件必须启用且 `availability()` 为 `Available`（不可用时技能不启用）；SKILL.md 必须有 frontmatter，`PluginSkill.id` 与 `name` 一致；`files` 路径必须相对且安全。
- **槽位/页面不显示**：槽位 id 必须过 `SlotIds.matches`；`provides.uiSlots` 与实际绑定一致（运行期以 `ui()` 为准）；页面必须出现在 `PluginUiContributions.pages`；检查清单 `ui` 字段（`ui.apiLevel`/`ui.kotlin`/`ui.compose`）是否与宿主版本匹配；不兼容时整块 UI 被禁用，回退宿主默认视图或显示不可用提示，`host.logs()` 会记录禁用原因。
- **更新失败**：确认签名 `keyId` 与首装一致、整包 sha256 与记录一致；换密钥只能当新发布者重装。

## 限制（当前明确不做）

- 不做第三方插件商店/审核/沙箱：同进程插件无隔离，只能靠来源可信 + 安装确认 + 签名/TOFU。
- 插件不能在清单里注册 Activity/Service/Receiver/权限：平台组件来自宿主静态清单；需要独立窗口用 `PluginHostActivity` 代理，前台服务用 `PluginHostService`，广播用动态 receiver，通知监听/无障碍/分享/深链用宿主代理组件（见「组件声明」）。
- 插件不能用 `res/` 资源 id；文案/图标/技能都走清单与 assets。
- 不提供声明式 UI 节点；UI 扩展只有自由 Compose（工具卡/自定义消息两类槽位、`PluginPage` 整页内容、输入区动作）。
- 原生库不加载（`lib/**` 仅进入摘要与解包）。
- 不承诺插件崩溃的进程级隔离；进程被系统强杀时不保证 `close()` 与未完成回调被调用。
- 插件不能自行提权，也不能改变用户选定的执行档位；Root/ADB 只在用户已授权的档位内可用。
- 系统级限制仍然存在：Android 10+ 后台启动 Activity、Doze/省电对后台任务的延迟、精确闹钟权限、通知/短信/电话等系统授权与默认应用策略。
- 已知契约限制：`notifications().notify` 没有回执（需要回执用 `notifyChecked`）；`requestContributionsRefresh()` 不返回句柄；`screen().status` 的后台刷新无法由插件停止；`jobs()` 没有 `list()`。这些按当前契约使用即可，刻意不扩接口。

## 参考

- 契约源码：宿主仓库 `plugin-api/src/main/kotlin/io/github/whywood/deepdeep/plugin/api/`；兼容契约与变更记录：`plugin-api/COMPATIBILITY.md`、`plugin-api/CHANGELOG.md`；ABI 门禁：`./scripts/api-compat.sh`
- 设计系统：宿主仓库 `design/`（`design 0.7.0` 组件与用法，插件经 `plugin-api` 传递使用）
- 插件工程：[DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)（官方实现）与 [DeepDeep-Plugins](https://github.com/Sadami3066/DeepDeep-Plugins)（模板与开发资料）
- 产品能力：[USER_GUIDE.md](USER_GUIDE.md)；构建与模块：[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
