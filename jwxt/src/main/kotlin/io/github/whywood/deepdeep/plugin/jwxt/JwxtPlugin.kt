package io.github.whywood.deepdeep.plugin.jwxt

import android.os.Build
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LinuxService
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import java.io.File

class JwxtPlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability {
        if (SUPPORTED_ABI !in Build.SUPPORTED_ABIS) {
            return PluginAvailability.Unavailable(host.text(TEXT_UNAVAILABLE_DEVICE))
        }
        if (!linuxAvailable(host)) {
            return PluginAvailability.Unavailable(host.text(TEXT_UNAVAILABLE_LINUX))
        }
        return PluginAvailability.Available
    }

    override suspend fun onEnable(host: PluginHost) {
        runCatching { loadStatus(host) }
    }

    override suspend fun uninstall(host: PluginHost) {
        runCatching { host.secrets().clear() }
        val linux = linuxOrNull(host)
        if (linux != null && linux.available) {
            runCatching {
                linux.exec(JwxtCli.logoutCommand() + " && " + JwxtCli.cleanupCommand(), timeoutMs = WRITE_TIMEOUT_MS)
            }
        }
        JwxtStatusStore.clear(host)
    }

    override fun skills(host: PluginHost): List<PluginSkill> =
        listOf(PluginSkill(JwxtCli.SKILL_ID, JwxtContent.skillMarkdown()))

    override fun promptSections(host: PluginHost): List<PromptSection> {
        val stored = JwxtStatusStore.read(host)
        val status = JwxtStatus(
            linuxAvailable = linuxAvailable(host),
            nodeVersion = stored?.nodeVersion,
            installedVersion = stored?.installedVersion,
            account = stored?.account,
            loggedIn = stored?.loggedIn == true,
        )
        return listOf(PromptSection(SECTION_ID, JwxtContent.promptBody(status), PROMPT_PRIORITY))
    }

    override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = emptyList()

        override val pages: List<PluginPage> = listOf(
            PluginPage(
                id = PAGE_ID,
                title = TEXT_PAGE_TITLE,
                icon = IconRef.Asset(ICON_ASSET),
                content = { JwxtPage(this@JwxtPlugin, host) },
            ),
        )
    }

    internal suspend fun loadStatus(host: PluginHost): JwxtStatus {
        if (!linuxAvailable(host)) return JwxtStatus(linuxAvailable = false)
        val linux = linuxOrNull(host) ?: return JwxtStatus(linuxAvailable = false)
        val recorded = JwxtStatusStore.read(host)
        val output = runCatching {
            linux.exec(JwxtCli.statusCommand(), timeoutMs = STATUS_TIMEOUT_MS)
        }.getOrNull()?.stdout.orEmpty()
        val raw = JwxtCli.parseStatus(output)
        val savedNetId = runCatching { host.secrets().get(JwxtCli.SECRET_NET_ID) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val status = JwxtStatus(
            linuxAvailable = true,
            nodeVersion = JwxtCli.parseNodeVersion(raw.node),
            installedVersion = JwxtCli.parseVersion(raw.cli),
            recordedVersion = recorded?.installedVersion,
            account = JwxtCli.netId(raw.credentials) ?: savedNetId,
            loggedIn = JwxtCli.isLoggedIn(raw.session),
        )
        JwxtStatusStore.write(
            host,
            JwxtStoredStatus(
                installedVersion = status.installedVersion,
                nodeVersion = status.nodeVersion,
                account = status.account,
                loggedIn = status.loggedIn,
                checkedAt = System.currentTimeMillis(),
            ),
        )
        return status
    }

    internal suspend fun saveAccount(host: PluginHost, netId: String, password: String) {
        val secrets = host.secrets()
        secrets.put(JwxtCli.SECRET_NET_ID, netId)
        secrets.put(JwxtCli.SECRET_PASSWORD, password)
        val linux = linuxOrNull(host)
        if (linux == null || !linux.available) return
        val previous = runCatching { linux.exec(JwxtCli.credentialsReadCommand(), timeoutMs = WRITE_TIMEOUT_MS) }
            .getOrNull()
            ?.let { JwxtCli.netId(it.stdout) }
        val result = linux.exec(JwxtCli.credentialsCommand(netId, password), timeoutMs = WRITE_TIMEOUT_MS)
        if (result.exitCode != 0) {
            throw JwxtException(
                host.text(TEXT_ERROR_SAVE, JwxtCli.cleanOutput(result.stdout).takeLast(ERROR_TAIL)),
            )
        }
        val changed = previous != null && previous != netId
        if (changed) {
            runCatching { linux.exec(JwxtCli.logoutCommand(), timeoutMs = WRITE_TIMEOUT_MS) }
        }
        val stored = JwxtStatusStore.read(host)
        JwxtStatusStore.write(
            host,
            JwxtStoredStatus(
                installedVersion = stored?.installedVersion,
                nodeVersion = stored?.nodeVersion,
                account = netId,
                loggedIn = if (changed) false else stored?.loggedIn == true,
                checkedAt = System.currentTimeMillis(),
            ),
        )
    }

    internal suspend fun ensureInstalled(host: PluginHost, onProgress: (String) -> Unit): String {
        val linux = requireLinux(host)
        onProgress(host.text(TEXT_STAGE_CHECKING))
        val nodeVersion = JwxtCli.parseNodeVersion(
            linux.exec(JwxtCli.nodeVersionCommand(), timeoutMs = NODE_TIMEOUT_MS).stdout,
        ) ?: throw JwxtException(host.text(TEXT_ERROR_NODE_MISSING))
        val nodeMajor = JwxtCli.parseNodeMajor(nodeVersion)
        if (nodeMajor == null || nodeMajor < JwxtCli.MIN_NODE_MAJOR) {
            throw JwxtException(host.text(TEXT_ERROR_NODE_OLD, nodeVersion))
        }
        val installed = runCatching {
            linux.exec(JwxtCli.versionCommand(), timeoutMs = NODE_TIMEOUT_MS)
        }.getOrNull()
            ?.takeIf { it.exitCode == 0 }
            ?.let { JwxtCli.parseVersion(it.stdout) }
        if (installed == JwxtCli.VERSION) {
            recordInstall(host, installed, nodeVersion)
            return installed
        }
        val assetDir = File(host.assetsDir, JwxtCli.ASSET_ROOT)
        if (!File(assetDir, JwxtCli.ENTRY_RELATIVE).isFile) {
            throw JwxtException(host.text(TEXT_ERROR_ASSET, JwxtCli.ENTRY_RELATIVE))
        }
        onProgress(host.text(TEXT_STAGE_INSTALLING))
        try {
            val result = linux.exec(JwxtCli.installCommand(assetDir.absolutePath), timeoutMs = INSTALL_TIMEOUT_MS)
            val version = JwxtCli.parseVersion(result.stdout)
            if (result.exitCode != 0 || version != JwxtCli.VERSION) {
                throw JwxtException(
                    host.text(TEXT_ERROR_INSTALL, JwxtCli.cleanOutput(result.stdout).takeLast(ERROR_TAIL)),
                )
            }
            recordInstall(host, version, nodeVersion)
            return version
        } catch (error: Exception) {
            runCatching { linux.exec(JwxtCli.cleanupCommand(), timeoutMs = WRITE_TIMEOUT_MS) }
            JwxtStatusStore.clear(host)
            throw error
        }
    }

    internal suspend fun startLogin(host: PluginHost, onProgress: (String) -> Unit): Boolean {
        val linux = requireLinux(host)
        onProgress(host.text(TEXT_STAGE_LOGIN))
        val result = linux.exec(JwxtCli.loginSendCodeCommand(), timeoutMs = LOGIN_TIMEOUT_MS)
        val output = JwxtCli.cleanOutput(result.stdout)
        if (result.timedOut) throw JwxtException(host.text(TEXT_ERROR_LOGIN_TIMEOUT))
        if (result.exitCode != 0) throw loginError(host, output)
        if (JwxtCli.needsCode(output)) return true
        if (!JwxtCli.loginSucceeded(output)) throw loginError(host, output)
        return false
    }

    internal suspend fun submitCode(host: PluginHost, code: String, onProgress: (String) -> Unit) {
        val linux = requireLinux(host)
        if (!JwxtCli.CODE_PATTERN.matches(code)) throw JwxtException(host.text(TEXT_ERROR_CODE_FORMAT))
        onProgress(host.text(TEXT_STAGE_CODE))
        val result = linux.exec(JwxtCli.loginCodeCommand(code), timeoutMs = LOGIN_TIMEOUT_MS)
        val output = JwxtCli.cleanOutput(result.stdout)
        if (result.timedOut) throw JwxtException(host.text(TEXT_ERROR_LOGIN_TIMEOUT))
        if (result.exitCode != 0) throw loginError(host, output)
        if (!loadStatus(host).loggedIn) throw loginError(host, output)
    }

    internal suspend fun logout(host: PluginHost) {
        val linux = requireLinux(host)
        val result = linux.exec(JwxtCli.logoutCommand(), timeoutMs = WRITE_TIMEOUT_MS)
        if (result.exitCode != 0) {
            throw JwxtException(
                host.text(TEXT_ERROR_LOGOUT, JwxtCli.cleanOutput(result.stdout).takeLast(ERROR_TAIL)),
            )
        }
        val stored = JwxtStatusStore.read(host)
        if (stored != null) {
            JwxtStatusStore.write(host, stored.copy(loggedIn = false, checkedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun recordInstall(host: PluginHost, version: String, nodeVersion: String) {
        val stored = JwxtStatusStore.read(host)
        JwxtStatusStore.write(
            host,
            JwxtStoredStatus(
                installedVersion = version,
                nodeVersion = nodeVersion,
                account = stored?.account,
                loggedIn = stored?.loggedIn == true,
                checkedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun linuxAvailable(host: PluginHost): Boolean =
        runCatching { host.linux().available }.getOrDefault(false)

    private fun linuxOrNull(host: PluginHost): LinuxService? = runCatching { host.linux() }.getOrNull()

    private fun requireLinux(host: PluginHost): LinuxService {
        val linux = linuxOrNull(host)
        if (linux == null || !linux.available) throw JwxtException(host.text(TEXT_UNAVAILABLE_LINUX))
        return linux
    }

    private fun loginError(host: PluginHost, output: String): JwxtException {
        val detail = output.takeLast(ERROR_TAIL).trim()
        return JwxtException(
            if (detail.isEmpty()) host.text(TEXT_ERROR_LOGIN_EMPTY) else host.text(TEXT_ERROR_LOGIN, detail),
        )
    }

    private companion object {
        const val PAGE_ID = "main"
        const val SECTION_ID = "jwxt_prompt"
        const val ICON_ASSET = "icon.png"
        const val SUPPORTED_ABI = "arm64-v8a"
        const val PROMPT_PRIORITY = 10

        const val STATUS_TIMEOUT_MS = 30_000L
        const val NODE_TIMEOUT_MS = 20_000L
        const val WRITE_TIMEOUT_MS = 30_000L
        const val INSTALL_TIMEOUT_MS = 60_000L
        const val LOGIN_TIMEOUT_MS = 180_000L
        const val ERROR_TAIL = 500

        val TEXT_UNAVAILABLE_DEVICE = literal(
            "当前设备不支持 Linux 环境",
            "Linux environment is not supported on this device",
        )
        val TEXT_UNAVAILABLE_LINUX = literal(
            "请先在 设置 → 运行环境 安装 Linux 环境",
            "Install the Linux environment in Settings → Runtime first",
        )
        val TEXT_STAGE_CHECKING = literal("正在检查 Node.js…", "Checking Node.js…")
        val TEXT_STAGE_INSTALLING = literal("正在安装教务 CLI…", "Installing the academic CLI…")
        val TEXT_STAGE_LOGIN = literal("正在登录…", "Signing in…")
        val TEXT_STAGE_CODE = literal("正在提交验证码…", "Submitting the verification code…")
        val TEXT_ERROR_NODE_MISSING = literal(
            "没有找到 Node.js：请到 设置 → 运行环境 安装 Node.js",
            "Node.js not found. Install Node.js in Settings → Runtime first.",
        )
        val TEXT_ERROR_NODE_OLD = literal(
            "Node.js 版本过低（%1\$s）：需要 22 或更高，请到 设置 → 运行环境 升级",
            "Node.js is too old (%1\$s). Version 22 or newer is required; upgrade it in Settings → Runtime.",
        )
        val TEXT_ERROR_INSTALL = literal("安装失败：%1\$s", "Install failed: %1\$s")
        val TEXT_ERROR_SAVE = literal("保存凭据失败：%1\$s", "Failed to save credentials: %1\$s")
        val TEXT_ERROR_ASSET = literal(
            "插件资源缺失：%1\$s，请重新安装插件",
            "Plugin asset is missing: %1\$s. Reinstall the plugin.",
        )
        val TEXT_ERROR_LOGIN = literal("登录失败：%1\$s", "Sign-in failed: %1\$s")
        val TEXT_ERROR_LOGIN_EMPTY = literal(
            "登录失败，请检查网络与账号密码后重试",
            "Sign-in failed. Check your network and credentials, then try again.",
        )
        val TEXT_ERROR_LOGIN_TIMEOUT = literal(
            "登录超时，请检查网络后重试",
            "Sign-in timed out. Check your network and try again.",
        )
        val TEXT_ERROR_LOGOUT = literal("退出登录失败：%1\$s", "Sign-out failed: %1\$s")
        val TEXT_ERROR_CODE_FORMAT = literal("请输入 4 位数字验证码", "Enter the 4-digit verification code")
        val TEXT_PAGE_TITLE = literal("中山大学教务", "SYSU Academic")
    }
}

internal class JwxtException(message: String) : Exception(message)

internal fun literal(zh: String, en: String): LocalizedText =
    LocalizedText.Literal(mapOf("default" to zh, "zh" to zh, "en" to en))
