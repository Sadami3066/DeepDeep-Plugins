package io.github.whywood.deepdeep.plugin.jwxt

internal object JwxtContent {
    fun skillMarkdown(): String = SKILL_MARKDOWN

    fun promptBody(status: JwxtStatus?): String {
        if (status == null) {
            return "中山大学教务插件状态未知。账号密码、登录与重新登录都在 DeepDeep 的「中山大学教务」插件页完成；不要向用户索要账号密码或验证码。"
        }
        if (!status.linuxAvailable || status.nodeVersion == null || status.nodeTooOld) {
            val reason = when {
                !status.linuxAvailable -> "缺少 Linux 运行环境"
                status.nodeVersion == null -> "缺少 Node.js"
                else -> "Node.js 版本过低（${status.nodeVersion}，需要 22 或更高）"
            }
            return "中山大学教务插件当前不可用：$reason。让用户到 设置 → 运行环境 安装或升级，然后在插件页「中山大学教务」填写账号密码并登录。"
        }
        if (!status.installed || status.outdated) {
            return "中山大学教务 CLI 尚未安装或版本不是最新的（当前 ${status.installedVersion ?: "未安装"}，期望 ${JwxtCli.VERSION}）：让用户打开插件页「中山大学教务」，填写账号密码后点「保存并登录」，页面会自动安装。"
        }
        return if (status.loggedIn) {
            buildString {
                append("中山大学教务插件已安装并已登录")
                if (!status.account.isNullOrBlank()) append("（账号 ${status.account}）")
                appendLine("。命令用法见技能 deepdeep-jwxt；不熟悉时先跑 `jwxt --help`。")
                appendLine("成绩、绩点、请假记录与个人信息仅限本机会话；不要输出账号、密码、验证码或 session.json。")
                appendLine("请假前先向用户确认并 `--dry-run` 预览，确认后才加 `--yes`；附件名只能 ASCII。")
                appendLine("会话失效时让用户在插件页点「重新登录」；接口报错不要反复重试，说明现象即可。")
            }
        } else {
            "中山大学教务插件已安装，当前未登录。让用户打开插件页填写 NetID 与密码并点「保存并登录」；需要验证码时页面会提示。不要向用户索要账号密码或验证码。"
        }
    }

    private val SKILL_MARKDOWN = """
        ---
        name: deepdeep-jwxt
        description: 中山大学教务系统 CLI——查课表、成绩、学分、考试、教学大纲、公告、空闲教室、请假记录与提交。用户问上课时间、某门课怎么样、成绩绩点、考试安排、空教室、教学大纲、请假审批时用；在 bash 中运行 jwxt。
        ---

        # 中山大学教务（jwxt）

        在 bash 中直接运行 `jwxt`。默认输出排版文本，加 `--json` 出结构化数据；完整选项看 `jwxt --help`。

        - 本周课表：`jwxt`
        - 指定周：`jwxt schedule --week 8`；`--today` / `--tomorrow` 只看今天或明天
        - 成绩：`jwxt scores`；`--all` 全部学期；`--semester 2025-2` 指定学期
        - 毕业学分进度：`jwxt credits`
        - 考试安排：`jwxt exams`
        - 一门课：`jwxt course <课程码或课名>`；`--intro` 简介、考核方式与教材；`--syllabus` 逐周进度
        - 公告：`jwxt notice`；`jwxt notice <id>` 看全文；`--num N` 条数
        - 空闲教室：`jwxt room`；`--building` `--room` `--week` `--day`；`--sections 9-11` 反过来找这节空着的教室
        - 助教信息：`jwxt ta`
        - 请假记录：`jwxt leave`

        ## 登录

        会话失效时会用 TGC 静默续期；真失效时让用户打开 DeepDeep 的「中山大学教务」插件页，点「重新登录」：页面会向企业微信发送 4 位验证码并提示输入。账号密码也只在插件页填写，不要在对话里向用户索要账号密码或验证码。

        首次使用或提示未安装时，让用户在插件页填写账号密码后点「保存并登录」，页面会自动安装 CLI。

        ## 要记住的

        - **`leave apply` 是唯一的写操作，提交后撤不回来**，撤单只能找辅导员。先在 bash 用 `--dry-run` 预览，再用 ask_user_question 向用户确认，确认后才加 `--yes`。
        - 请假必须带附件（jpg/jpeg/png/gif/pdf），**附件名只能 ASCII**，中文文件名会被存成一串问号；附件放在工作区，用 proot 内可读的绝对路径传给 `--file`。
        - **课名会撞车**：同名课各学院各有一份。课程码最可靠；只用课名时，能唯一确定才给结果，否则列候选、让用户给课程码，不要自己猜一个。
        - **绩点是自算口径**（只算有绩点的课，缓考与 P/F 不计），不是学校官方口径，不要当权威数字汇报，说明口径即可。
        - `exams` 和 `course` 要几秒才出结果，不是卡死。
        - TGC 静默续期可能顶掉浏览器里的登录，告诉用户刷新一下就能免密回去。
        - 接口改版、证书链错误或一直报错时不要反复重试：说明报错内容，提示用户检查网络或更新插件。
        - 成绩、绩点、请假记录与个人信息只在本机会话使用，不得写到对外内容或外发；不要输出凭据、验证码与 session.json 内容。
        - 不要自行创建定时任务或总结流程，除非用户明确要求。
    """.trimIndent()
}
