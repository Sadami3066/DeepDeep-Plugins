# 上架审核清单

市场维护者按下述清单审核每个条目，不通过的会说明原因并退回。

## 条目

- [ ] `id` 与制品内 `plugin.json` 一致，且未被其它发布者占用
- [ ] `version` 与制品一致，同 id 上架需高于已发布版本
- [ ] `name` / `summary` / `description` / `tags` 中英双语，不含开发侧说明、内部链接或夸大宣传
- [ ] `artifact.url` 可长期公开下载，`artifact.sha256` 与 `artifact.size` 和文件逐字节一致
- [ ] 图标为 ≤128KB 的 PNG，品牌图标需注明来源

## 制品

- [ ] `.ddp` 通过签名校验：`META-INF/plugin-signature.json` 的 `keyId` 与条目 `publisherKeyId` 一致
- [ ] 首次上架记录发布者 `keyId`；同 id 更新沿用同一密钥，换钥视为新发布者
- [ ] `plugin.json` 的 `capabilities` / `provides` 与实际行为一致，没有未声明的能力
- [ ] 无恶意、破坏性或未告知的行为，不窃取凭据、聊天与文件，不绕过系统权限
- [ ] 体积在限制内：插件 50 MB、技能 20 MB

## 变更

- [ ] 更新条目附变更说明（版本要点、行为变化、必要时的迁移提示）
- [ ] 发布者下架请求说明原因；安全事件按政策移除并保留审计记录
