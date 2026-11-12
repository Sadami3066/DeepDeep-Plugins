# DeepDeep 插件开发

面向外部插件作者的开发文档：工程搭建、契约、打包签名、安装更新与全部扩展面。插件工程从本仓库的 `template/` 起步；官方插件与共享打包脚本在 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)。兼容契约见 [COMPATIBILITY.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/COMPATIBILITY.md)。

DeepDeep 插件是 **Kotlin 代码插件**：实现 `plugin-api` 的 `DeepDeepPlugin`，打包成 `.ddp`，由宿主在 App 进程内加载。插件只从 `.ddp` 安装，不存在随包编译的插件。

**安全前提**：插件与 App 同进程、同 UID、没有沙箱，安装即信任——插件代码等同 App 代码，能读 App 私有数据、反射宿主内部、让 App 崩溃。只安装自己信任的插件；第三方工具源请走 MCP。

## 快速开始

`plugin-api` 是唯一契约工件（Android library，包名 `io.github.whywood.deepdeep.plugin.api`）：SDK 版本以 `deepdeep-maven` 最新发布为准（当前 `1.0.0`），`PLUGIN_API_LEVEL = 6`、`BASELINE_API_LEVEL = 6`、`MIN_SUPPORTED_API_LEVEL = 6`、`HOST_UI_API_LEVEL = 2`。

契约兼容承诺见 [COMPATIBILITY.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/COMPATIBILITY.md)：同一 `apiLevel` 内只做二进制兼容的加法，删除/改名/改签名/改语义必须升 `apiLevel`；宿主拒绝 `apiLevel` 高于自己的插件，1.0.4 起不再接受 level 5。

本仓库自带 `template/`：独立的 Gradle Android 工程，包含一个示例工具、一个示例技能、一段提示词与完整的 `.ddp` 打包/签名/验签实现，复制即可作为插件起点。

前置条件：

- JDK 17、Android SDK（compileSdk 36、minSdk 29）；SDK 位置通过 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 或工程内 `local.properties` 的 `sdk.dir` 指定。
- 依赖从公开 Maven 仓库解析：`https://sadami3066.github.io/deepdeep-maven`（`template/settings.gradle.kts` 已配置，无需本地发布）。
- Gradle 9.8.2（模板自带 wrapper）。

`plugin-api` 是契约（当前 `1.0.0`），`design` 是与 App 同源的 UI 设计系统；两者版本以 `deepdeep-maven` 最新发布为准，`plugin-api` 通过 `api()` 把 `design` 传递进来，插件工程只需声明 `plugin-api`。

新增插件：

```bash
# 1. 复制模板并改名
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
#    build/plugin/my-plugin.unsigned.ddp   未签名（排障用）
#    build/plugin/my-plugin.ddp            可安装的签名包
```

本仓库不再维护根构建；复制 `template/` 到任意目录开发即可，模板自带 wrapper 与打包任务。多个插件放在同一个 Gradle 工程时，参考官方插件仓的 `settings.gradle.kts` 与根 `gradle/plugin-bundle.gradle.kts`。

产物文件名由工程名决定。模块只编 `debug` 变体，`release` 不开 minify；`.ddp` 由 debug APK 重打包而来。

安装：把 `.ddp` 传到手机，插件页「导入插件」选择文件。确认页展示名称/版本/体积/整包 sha256/发布者名称与 `keyId`、将提供的工具/技能/页面/提示词覆盖（声明 `prompt` 时）；首次导入需确认信任发布者，确认后才写入 TOFU 信任库、解包安装并立即启用（`defaultEnabled` 为 true 时）。之后升级必须用同一发布者密钥签名。

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

- 宿主已提供的库只能 `compileOnly`：`io.github.sadami3066.deepdeep:plugin-api`（版本用 `deepdeep-maven` 最新发布，示例为 `1.0.0`；`api()` 传递配套的 `design:0.6.1`，插件 UI 直接用其中的 Dd 组件）、Compose（`ui`/`foundation`/`material3` + BOM）、`kotlinx-coroutines-core`、`kotlinx-serialization-json`。
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
| `minHostVersion` | string | 否 | 最低宿主版本。宿主版本取 `BuildConfig.VERSION_NAME`；比较只看 `major.minor.patch`、忽略后缀，因此当前宿主满足 `minHostVersion: "1.0.4"` |
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
| `capabilities` | string[] | 是 | 插件需要的宿主服务与能力清单（18 项），见下；缺失或含未知项拒绝装载 |
| `defaultEnabled` | bool | 否（默认 true） | 首次安装后是否默认启用 |

清单不支持 `source`/`installable`/`sizeLabel`/`tintIcon` 等字段：来源固定按外部 `.ddp` 处理，体积由宿主按包体展示，图标统一用 asset 图片。未知字段忽略，错误的标量类型退化为默认值后交给校验报错。

`provides` 用于安装前的能力预检与确认页展示：

| 键 | 内容 |
|---|---|
| `tools` | 工具名列表，小写 `snake_case`，如 `template_echo`；不能与宿主保留工具名冲突 |
| `skills` | 技能 id 列表，如 `template-skill` |
| `uiSlots` | 注册的槽位 id，如 `tool.card.content:template_echo`、`chat.message:card`；插件页面不走槽位，见 `pages` |
| `pages` | 插件页面 id 列表 |

`capabilities` 声明插件实际使用的宿主服务；安装确认页与插件详情会展示，宿主只注入声明过的服务。**未声明的服务访问器在运行时抛 `PluginServiceUnavailable`**（不是返回 null）。`prompt` 不暴露访问器，用于开启提示词覆盖（见「技能与提示词段」）。取值：

| wire 名 | 服务访问器 |
|---|---|
| `linux` | `host.linux()` |
| `screen` | `host.screen()` |
| `notifications` | `host.notifications()` |
| `secrets` | `host.secrets()` |
| `network` | `host.network()` |
| `files` | `host.files()` |
| `sessions` | `host.sessions()` |
| `jobs` | `host.jobs()` |
| `timers` | `host.timers()` |
| `goals` | `host.goals()` |
| `miniapp` | `host.miniapp()` |
| `mcp` | `host.mcp()` |
| `ui` | `host.ui()` |
| `app` | `host.app()` |
| `bus` | `host.bus()` |
| `logs` | `host.logs()` |
| `chat` | `host.chat()` |
| `prompt` | 无访问器；声明后宿主才会调用 `PromptOverridePlugin.promptOverride()`，见「提示词覆盖」 |

`capabilities` 是**可用性边界，不是安全沙箱**：插件仍与宿主同进程、同 UID，可以通过 `host.context` 或反射绕过；它用于让插件声明意图、让用户在安装前看清会用到哪些能力，不能阻止恶意代码。至少声明一项；空数组合法但插件几乎无法工作。

`tools`/`skills`/`pages` 的 id 统一匹配 `^[a-z][A-Za-z0-9_-]*$`，**不能包含点号**（技能 id 写 `template-guide`，不是 `template.guide`）；`uiSlots` 的槽位 id 规则见后文。

`LocalizedText` 写法：

```json
"name": { "default": "Plugin Template", "zh": "插件模板", "en": "Plugin Template" }
```

- `default` 为必填回退；`zh`/`en` 是语言标签，宿主按 App 语言取，缺失回退 `default`。
- 也可以给字符串简写（`"name": "Plugin Template"`），等价于只有 `default`。
- `host.text(source, *args)` 支持 `String.format` 占位符（`%s`/`%d`），参数只传字符串/数字（签名保留 `vararg Any`，新代码不要再传其它类型）。
- `LocalizedText` 与 `IconRef` 是只读接口：文本用 `localizedText(default, zh?, en?)` 构造，图标用 `assetIcon("asset:<路径>")` 构造；level 5 的 `Literal`/`Resource` 等构造在 v6 已删除。
- 外部插件没有 App 资源，`host.text()` 负责解析与格式化。

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

`host` 是插件唯一入口（`PluginHost`）：`manifest`、`context`（宿主注入的 App 上下文）、`dataDir`、`assetsDir`、`scope`、`text`、17 个宿主服务访问器（见「宿主服务」）与 `requestContributionsRefresh()`。`manifest` 等清单类型是宿主构造的只读接口，插件不要自行构造。

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

小程序的一次性 Agent 复用同一套 prompt/input/工具钩子与会话绑定，但它的会话事件不经过宿主会话观察事件管线，因此 21 个观察事件与 run 冻结只覆盖主会话运行路径。

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

共 21 个观察回调。

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

宿主固定主壳/导航/聊天骨架，插件可以注册**插件页面**、向两个槽位挂 Compose 绑定，或者贡献输入区动作；不能替换整页、不能注册 Activity。插件页面是插件唯一的自绘界面，页面内容直接写在 `PluginPage` 里。

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

插件 UI 直接使用 App 的设计系统 `io.github.whywood.deepdeep.design`（`plugin-api` 已通过 `api()` 传递，插件工程不需要再显式声明坐标）。组件与 App 完全同源，禁止自造样式：

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

## 宿主服务

`PluginHost` 暴露 17 个服务访问器；宿主没提供该服务时**抛 `PluginServiceUnavailable`**（不是返回 null），插件应 `runCatching` 后降级。服务已装配但当前不可用（如未装 Linux、未授权 ADB）时，通常通过 `available`/`enabled` 字段或明确异常表达。

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
| `notifications()` | `enabled`、`notify(...)`（用 `pluginNotification(...)` 构造）、`updateProgress`、`cancel` | 通知权限未开时 `enabled=false`，发布被静默忽略；`notify` 不返回是否成功 |
| `secrets()` | `get/put/remove/clear`，按插件 id 命名空间加密存储 | 宿主未提供时抛 `PluginServiceUnavailable` |
| `network()` | `request(method,url,headers,body,timeoutMs)`、`download(url,targetPath,expectedSha256,onProgress)`；扩展 `get`/`post` | 网络错误抛异常；响应体上限 2 MB、下载上限 512 MB |
| `files()` | 插件数据/工作区文件：`list/readText/writeText/readBytes/writeBytes/delete/exists/resolve` | 相对路径相对插件 `dataDir`；绝对路径按当前档位解析（Root 档为原生文件系统，见「执行身份与环境」） |
| `sessions()` | `active`、`observe()`（含 busy 变化）、`list()`、`prompt(text, sessionId?)` | 无活动会话时 `prompt` 返回 false |
| `jobs()` | `observe()`、`start(command,label,cwd,sessionId)`、`read(jobId)`、`kill(jobId)` | 无会话/宿主未提供时 `start` 返回 null；没有 `list()`，一次性读取用 `observe().first()` |
| `timers()` | `schedule(TimerRequest)`（用 `timerRequest(...)` + `timerOnce`/`timerCron` 构造）、`list()`、`cancel(id)` | 参数非法/宿主未提供时返回 null 或抛异常 |
| `goals()` | `observe()`、`get/create/edit/pause/resume/complete/clear` | 目标状态非法时返回 null |
| `miniapp()` | 小程序：`scan/load/create/edit/delete/run` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `mcp()` | `servers()`、`probe(name)`、`tools(server)`、`call(server,tool,arguments)` | 服务器不存在时返回错误结果或异常 |
| `ui()` | `openUrl`、`share(ShareRequest)`、`toast`、`openPage(pageId)` | 单项未接线时抛 `PluginServiceUnavailable("ui.xxx")`；`openPage` 只接受本插件的页面 id，不是 `deepdeep://` 深链 |
| `app()` | `info: AppInfo`（versionName/versionCode/pluginApiLevel/uiApiLevel/language/androidSdkInt/deviceModel/installationId） | 始终可用 |
| `bus()` | 插件总线：`publish(event)`（用 `pluginBusEvent(...)` 构造）、`subscribe(topic?)` → `Flow<PluginBusEvent>` | 宿主未提供时抛 `PluginServiceUnavailable` |
| `logs()` | `log(level, message, error?)` + `debug/info/warn/error` | 始终可用 |
| `chat()` | `sendMessage(sessionId, text, options)`：落卡片、起新轮、插话/排队；另有 `postCustomMessage(...)` 只写卡片 | 未知会话抛异常；投递失败返回 false |

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
```

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
- 提示词里的环境说明由宿主按档位生成；插件 `promptSections` 不要写死“已获得 root”之类的假设。

**总线**：`host.bus().publish(pluginBusEvent(topic, payload))` 广播给所有插件（`publisher` 由宿主按插件 id 覆盖）；`subscribe("topic")` 拿 `Flow`，同时 `PluginHooks.busEvent(...)` 观察类回调也能收到。事件只用于插件间协作与轻量通知，不要用它传大载荷或做同步 RPC。

## 页面与状态

插件页面是插件唯一的交互界面，也是插件维护自身状态的地方：

- 页面由 `PluginUiContributions.pages` 声明，`PluginPage.content` 就是页面内容（宿主直接渲染，不经过槽位）；列表里点整行进入第一个页面，多页面在页面内部自行切换（如 `host.ui().openPage(...)` 只能打开本插件自己的页面）。
- 插件数据写在 `host.dataDir`（`host.files()` 相对路径以它为根），机密凭据写 `host.secrets()`，不要在页面里缓存需要跨进程保留的状态。
- 页面里做完安装/登录/开关等会影响工具或技能的动作后调用 `host.requestContributionsRefresh()`：宿主重跑一次物化并触发会话重建，贡献在安全点生效。例如插件在页面里装好 CLI 后再返回工具列表，下一次会话就能看到新工具。
- 页面可以调用 17 个宿主服务；需要独立返回栈或全屏的流程仍由插件自己组织页面层级，不能注册 Activity。
- `PluginPage.content` 不接收 `SlotState`；需要活跃会话/传递上下文时在页面里调 `host.sessions().active` 或自己保存界面状态。
- `host.requestContributionsRefresh()` 是异步触发、不等待完成；需要在界面上确认结果时用插件自己的状态标记，不要假设返回时就已物化。
- `network().get`/`post` 与 `PluginAvailability.isReady` 是扩展函数；显式 import 风格下要分别 import `get`/`post`/`isReady`（或整包 `io.github.whywood.deepdeep.plugin.api.*`）。

## 从 API level 5 迁移到 level 6

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
- 依赖改为 `compileOnly("io.github.sadami3066.deepdeep:plugin-api:1.0.0")`，按宿主 Kotlin/Compose 版本重新编译；`.ddp` 内容变化后必须重新签名。
- manifest 解析、校验与版本比较已移到宿主（宿主仓库 `app/plugin/host/external/`），SDK 里不再有 `PluginManifestCodec` / `PluginValidation` / `PluginVersion`；`PluginManifest` / `Provides` / `UiManifest` / `PublisherInfo` 是宿主构造的只读接口，插件不构造。
- 兼容承诺从 1.0.0 起生效：level 6 内只做二进制兼容的加法；需要删改公开 API 时升 `apiLevel`（见 [COMPATIBILITY.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/COMPATIBILITY.md)）。

## ABI 与版本

- 当前 `plugin-api 1.0.0` / API level 6：`PLUGIN_API_LEVEL = 6`、`BASELINE_API_LEVEL = 6`、`MIN_SUPPORTED_API_LEVEL = 6`。宿主加载时要求 `plugin.apiLevel` 在 `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL`（当前 6..6）内；未写 `apiLevel` 按 `BASELINE_API_LEVEL`（6）处理。宿主 1.0.4 起拒绝 level 5 插件并提示按新 SDK 重编译。
- **兼容契约**：同一 `apiLevel` 内只允许二进制兼容的加法（新增可选接口、带默认实现的方法、value class 常量、工厂函数、manifest 可选字段），删除/改名/改签名/改运行时语义必须升 `apiLevel`。完整规则见 [COMPATIBILITY.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/COMPATIBILITY.md)。
- `minHostVersion` 只比较 `major.minor.patch`、忽略后缀（`1.0.4-dev` 视为 `1.0.4`），避免开发版被自己的门槛卡住；宿主版本号来自 App 的 `BuildConfig.VERSION_NAME`，当前宿主满足 `minHostVersion: "1.0.4"`。
- 门禁：宿主仓库的 `./scripts/api-compat.sh` 导出 `plugin-api` 与 `design` 的公开 ABI，与 `plugin-api/compat/`、`design/compat/` 基线比对，删除或签名变化即失败；`--update` 只在升 level 或首次建基线时用。破坏性变更必须同时升 `apiLevel`、更新基线、写 `plugin-api/CHANGELOG.md` 与宿主 release notes。
- 实验 API 用 `@PluginExperimentalApi`（`@RequiresOptIn`）标记，插件需显式 opt-in；实验期间允许调整，转正时只去注解不改签名。当前实验能力：`PromptOverridePlugin`。
- 插件必须 `compileOnly` 依赖宿主库；宿主 parent-first 保证 `plugin-api` 与 Compose 只存在宿主那一份。
- Compose 没有跨版本二进制 ABI 承诺：插件要按宿主 Kotlin/Compose 版本重新编译；版本不一致只禁用该插件的 UI，不拒绝插件。

## 安全模型

- **同进程、同 UID、无沙箱**：插件代码与 App 代码同等权限，能读写 App 私有数据、访问已授权的系统能力（日历、无障碍、ADB/Root 桥）、反射宿主内部类、起线程、结束进程。同进程没有可信的权限收窄机制，签名与 `capabilities` 都只用于声明和展示，不是沙箱。
- **安装即信任**：首装确认页必须让用户明确「插件拥有 App 全部权限」；签名只解决「包有没有被篡改、是不是同一个发布者」，不解决「作者是否可信」。`capabilities` 只声明插件会用到的服务并限制访问器注入，不是沙箱。
- **适用来源**：可信的第一方/个人插件包；第三方工具源用 MCP，不走 `.ddp`。
- **完整性**：安装记录钉死整包 sha256，每次装载前复核；canonical digest 验签防篡改；更新必须同 `keyId`；失败保留上一可用版本。
- **宿主不暴露的东西**：Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥、pi SDK 内部类型。插件拿到的是 `PluginHost` 契约与 `host.context`。
- **插件作者的义务**：不读与本插件无关的用户数据；不把凭据写日志；删除/覆盖用户数据的动作要可逆或在页面里明确确认；不要用 `System.exit`/`Runtime.halt` 结束进程。
- **崩溃**：工具/回调异常会被宿主隔离成 error/失败记录；但插件在主线程、Compose 槽或工具执行中把进程搞崩仍会带崩 App（没有进程级隔离，也没有槽位崩溃标记兜底）。

## 调试与常见错误

构建/打包/签名（在模板工程目录）：

```bash
./gradlew assemblePlugin
./gradlew signPlugin
./gradlew verifyPlugin
```

排查路径：

- **安装前自查**（`verifyPlugin` 的 `Signature OK` 只验证签名与 digest 一致，**不代表能被安装**；导入前逐项确认）：
  - `id` 满足反向域名正则（至少两段，如 `com.example.myplugin`）；
  - `version` 是 `major.minor.patch[后缀]`；`apiLevel` 为 6（≤ 宿主 `PLUGIN_API_LEVEL`）；`minHostVersion` 不高于宿主版本（当前 1.0.4）；
  - `entryClass` 类名合法、类在某个根 `classes*.dex` 里且有无参构造；
  - `provides` 与实际贡献一致（tools/skills/uiSlots/pages），工具名不与宿主保留名冲突；
  - 槽位 id 过 `SlotIds.matches`（`tool.card.content:<tool>`、`chat.message:<customType>`），插件页面 id 过 `pages` 校验；
  - 包内 `assets/plugin.json` 路径正确、至少一个根 `classes*.dex`。
- **安装被拒**：确认页/提示会给出原因；常见为清单校验码（`INVALID_ID`/`INVALID_VERSION`/`INVALID_PROVIDES` 等）、签名错误（包被改动/公钥与 keyId 不匹配/缺签名文件）、发布者不匹配（升级换了密钥）、工具名冲突（`provides.tools` 与宿主或已装插件冲突）、版本冲突（版本不比已装新）。
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
- 不给插件注册 Activity/Service/权限：平台不允许运行期组件注册；需要独立 UI 只能另装独立 APK。
- 插件不能用 `res/` 资源 id；文案/图标/技能都走清单与 assets。
- 不提供 Provider 凭据/OAuth、ADB 配对流程、宿主内部加密密钥等服务；插件可以直接用 `host.context` 调 Android API，宿主不做拦截。
- 已知的限制：`notifications().notify` 没有回执；`requestContributionsRefresh()` 不返回句柄；`screen().status` 的后台刷新无法由插件停止；`jobs()` 没有 `list()`。这些按当前契约使用即可，刻意不扩接口。
- 不承诺插件崩溃的进程级隔离；进程被系统强杀时不保证调用插件的 `close()`。
- 原生库不加载（`lib/**` 仅进入摘要与解包）。
- 不提供声明式 UI 节点；UI 扩展只有自由 Compose（工具卡/自定义消息两类槽位 + `PluginPage` 整页内容）。

## 参考

- 契约工件：`io.github.sadami3066.deepdeep:plugin-api`（版本以 `deepdeep-maven` 最新发布为准，当前 `1.0.0`；公开 Maven `https://sadami3066.github.io/deepdeep-maven`）
- 兼容契约与变更记录：[COMPATIBILITY.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/COMPATIBILITY.md)、[CHANGELOG.md](https://github.com/Sadami3066/DeepDeep/blob/master/plugin-api/CHANGELOG.md)
- 示例工程与打包签名实现：[template/](template/README.md)
- 插件仓库说明：[README.md](README.md)；上架与分发规则：[MARKET_POLICY.md](MARKET_POLICY.md)
