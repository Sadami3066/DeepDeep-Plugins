package io.github.whywood.deepdeep.plugintemplate

import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginSkillFile
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PromptSection
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class TemplatePlugin : DeepDeepPlugin {

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override fun skills(host: PluginHost): List<PluginSkill> = listOf(
        PluginSkill(
            id = SKILL_ID,
            markdown = skillMarkdown(host),
            files = listOf(
                PluginSkillFile(
                    path = "references/format.md",
                    assetPath = "$SKILL_ASSET_DIR/references/format.md",
                ),
            ),
        ),
    )

    override fun promptSections(host: PluginHost): List<PromptSection> = listOf(
        PromptSection(
            id = PROMPT_SECTION_ID,
            body = "Template plugin is installed. Call `template_echo` when you need to echo a short text back.",
        ),
    )

    override fun contributions(host: PluginHost): Contributions = Contributions(tools = listOf(echoTool))

    private fun skillMarkdown(host: PluginHost): String {
        val fromAssets = runCatching {
            host.assetsDir.resolve("$SKILL_ASSET_DIR/SKILL.md").readText()
        }.getOrNull()
        if (!fromAssets.isNullOrBlank()) return fromAssets
        return host.context.assets.open("$SKILL_ASSET_DIR/SKILL.md").bufferedReader().use { it.readText() }
    }

    private val echoTool = PluginTool(
        name = "template_echo",
        label = "template_echo",
        description = "Echo the provided text back, prefixed with the plugin name. Useful to verify that an external plugin is installed and its tool is callable.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "Text to echo back.")
                }
            }
            putJsonArray("required") {
                add(kotlinx.serialization.json.JsonPrimitive("message"))
            }
        },
        promptGuidelines = listOf("Call template_echo only when the user asks to verify the template plugin."),
    ) { call ->
        val message = call.args["message"]?.jsonPrimitive?.content.orEmpty()
        PluginToolResult.text("template_echo: $message")
    }

    private companion object {
        const val SKILL_ID = "template-skill"
        const val SKILL_ASSET_DIR = "skills/template-skill"
        const val PROMPT_SECTION_ID = "template-plugin"
    }
}
