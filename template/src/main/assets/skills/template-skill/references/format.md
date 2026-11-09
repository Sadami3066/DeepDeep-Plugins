# 技能附加文件示例

初始版本：`1.0.0`

这个文件通过 `PluginSkillFile(path = "references/format.md", assetPath = "skills/template-skill/references/format.md")` 声明，
宿主会把 `assets/skills/template-skill/references/format.md` 物化到技能目录的 `references/format.md`。

规则：

- `path` 是技能目录内的相对路径。
- `assetPath` 是插件包 `assets/` 内的相对路径。
- 同一个文件只能用 `content`（内联字符串）或 `assetPath`（资源文件）其中一种方式提供。
