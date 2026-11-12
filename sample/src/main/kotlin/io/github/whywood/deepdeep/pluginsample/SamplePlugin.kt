package io.github.whywood.deepdeep.pluginsample

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.assetIcon
import io.github.whywood.deepdeep.plugin.api.available
import io.github.whywood.deepdeep.plugin.api.contributions
import io.github.whywood.deepdeep.plugin.api.localizedText
import io.github.whywood.deepdeep.plugin.api.pluginPage
import io.github.whywood.deepdeep.plugin.api.pluginTool
import io.github.whywood.deepdeep.plugin.api.textResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SamplePlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = available()

    override fun contributions(host: PluginHost): Contributions = contributions(tools = listOf(echoTool))

    override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = emptyList()

        override val pages: List<PluginPage> = listOf(
            pluginPage(
                id = PAGE_ID,
                title = localizedText(
                    mapOf("default" to "Sample Page", "zh" to "示例页面", "en" to "Sample Page"),
                ),
                icon = assetIcon(ICON_ASSET),
                content = { SamplePage() },
            ),
        )
    }

    private val echoTool = pluginTool(
        name = "sample_echo",
        label = "sample_echo",
        description = "Echo the given text with an `echo:` prefix. Used by the host end-to-end tests.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "Text to echo back.")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("text"))
            }
        },
        promptSnippet = "sample_echo echoes text back",
    ) { call ->
        val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        textResult("echo: $text")
    }

    private companion object {
        const val PAGE_ID = "sample.main"
        const val ICON_ASSET = "icon.png"
    }
}

@Composable
private fun SamplePage() {
    Text(text = "Sample plugin page", modifier = Modifier.padding(20.dp))
}
