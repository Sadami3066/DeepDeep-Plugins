---
name: template-skill
description: 插件模板自带的示例技能，说明 external 插件如何把 markdown 与附加文件作为技能交给 agent；当用户问插件模板技能如何工作、或需要演示技能加载时使用。
---

# 插件模板技能

这是 `plugin-template` 工程自带的示例技能，用来演示外部插件如何提供技能：

1. `assets/skills/template-skill/SKILL.md` 是本文件的来源，插件在 `skills(host)` 里从 `host.assetsDir` 读取它，作为 `PluginSkill.markdown` 返回。
2. `assets/skills/template-skill/references/format.md` 作为附加文件，通过 `PluginSkillFile(assetPath = ...)` 声明，物化后与 SKILL.md 放在同一技能目录。
3. 宿主只在插件处于可贡献相位（`ACTIVE` / `DEGRADED` / `PENDING_RESTART`）时把技能写入 agent 技能目录；插件停用或卸载后按归属清理。

## 何时使用

- 用户在开发 DeepDeep 插件、需要看技能目录结构时，可以加载本技能作为最小示例。
- 需要演示「技能 + 附加引用文件」时使用；不要把本技能当成真实业务能力。

## 要求

- 技能 markdown 必须带 YAML frontmatter（`name`、`description`），`name` 与 `PluginSkill.id` 保持一致。
- 附加文件路径必须是相对路径，不能包含 `..`、反斜杠或绝对路径；内容与 `assetPath` 二选一。
