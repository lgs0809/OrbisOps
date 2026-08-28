# Skill 自动沉淀与维护：源码核对

日期：2026-09-27。状态：用户已同意采用简单方案；同方法语义整理、引用内容计量和页面回滚已实施并做隔离验收。来源分层读取已部署并通过协议与真实组件回归，真实大来源模型补读结果另记。

本次读取五个官方开源仓库的相关源码，共保存 22 个文件及内容 SHA-256。快照清单位于本次 Codex 工作目录的 `work/skill-evolution-research-20260927/sources.json`。只做源码审阅，没有安装、执行这些项目或复现论文指标。

## 结论

OrbisOps 的后台异步整理、来源追踪、语义归组、不可变版本、正文与索引一致发布、权限隔离与回滚值得保留。调研时 COMPRESS 只删除相邻且完全相同的普通段落；现已改为模型提出语义整理、程序保护结构与边界、另一次模型调用复核，再进入原有发布流程。真实模型返回审查通过，也不等于实际任务效果无损。

需要分别记录四种事实：方法有来源、内容检查通过、版本发布成功、实际使用有效。任意前一项都不能替代后一项。

## 官方项目对照

| 项目／核对版本 | 源码观察 | 与本项目的关系 |
| --- | --- | --- |
| AutoSkill `94c47ca` | 候选检索后决定新增、融合或放弃；融合按能力与意图判断，生成语义整合后的方法并保留版本及资源。 | 最接近日常沉淀；可参考语义整理，不直接照搬其启发式兜底。 |
| Hermes Agent `a0fe806` | 后台 Curator 整理同类方法及附属文件，受控工具记账并可恢复；模型整合默认关闭。 | 可参考完整包维护和可追溯修改；不能照搬其归档策略及合并数量要求。 |
| EvoSkill `36f6f04` | 分析失败轨迹、提出修改、在验证集评分，以 Git 分支保留候选配置。 | 可参考失败驱动和真实任务评估；不是线上每个对话结束都完整跑一次。 |
| Voyager `55e45a8` | 先在环境执行，依据环境事件进行成功判断，再保存可执行技能和描述索引。 | 可参考先执行验证后复用；Minecraft 的反馈条件不能直接等同生产运维。 |
| SkillRL `4d984d4` | 从成功与失败中蒸馏技能，并在强化学习训练中根据验证失败更新技能库。 | 学习其区分方法和失败教训；不为此引入模型训练。 |

源码入口：

- [AutoSkill 维护与合并](https://github.com/ECNU-ICALK/AutoSkill/blob/94c47ca488d4ba4117d20272e66d49b9877e68cf/autoskill/management/maintenance.py)：`_upsert_candidate`、`_should_merge`、`_merge_with_llm`。
- [AutoSkill 提取](https://github.com/ECNU-ICALK/AutoSkill/blob/94c47ca488d4ba4117d20272e66d49b9877e68cf/autoskill/management/extraction.py)：主要针对有用户依据的可复用要求，不应直接当作运维成功验收器。
- [Hermes Curator](https://github.com/NousResearch/hermes-agent/blob/a0fe806c46e729894c60e5dd25154400d179b231/agent/curator.py)：`CURATOR_REVIEW_PROMPT` 和受控维护流程；[Curator 说明](https://github.com/NousResearch/hermes-agent/blob/a0fe806c46e729894c60e5dd25154400d179b231/website/docs/user-guide/features/curator.md) 区分确定性归档与可选模型整合。
- [EvoSkill 执行循环](https://github.com/sentient-agi/EvoSkill/blob/36f6f04952293d7054145550c2b9f0b0411bff1c/src/loop/runner.py)、[候选配置管理](https://github.com/sentient-agi/EvoSkill/blob/36f6f04952293d7054145550c2b9f0b0411bff1c/src/registry/manager.py)。候选进入容量未满的 frontier 不一定优于当前最佳版本，不能混为“每次修改均提升”。
- [Voyager 环境执行及入库](https://github.com/MineDojo/Voyager/blob/55e45a880755d0c8c66ca7fb5fe7962ac8974f89/voyager/voyager.py)、[技能库](https://github.com/MineDojo/Voyager/blob/55e45a880755d0c8c66ca7fb5fe7962ac8974f89/voyager/agents/skill.py)。
- [SkillRL 更新器](https://github.com/aiming-lab/SkillRL/blob/4d984d455b9b02f0bad283cbe9ab3d66a7e5b89d/agent_system/memory/skill_updater.py)。

另需区分 **AutoSkill 的 SkillEvo 模块**与独立项目 **EvoSkill**：前者冻结回放池，分开开发和晋升评测，比较当前优胜版本与候选；当前说明明确尚未自动写回主 SkillBank。[执行代码](https://github.com/ECNU-ICALK/AutoSkill/blob/94c47ca488d4ba4117d20272e66d49b9877e68cf/SkillEvo/runner.py)，[模块边界](https://github.com/ECNU-ICALK/AutoSkill/blob/94c47ca488d4ba4117d20272e66d49b9877e68cf/SkillEvo/README.md)。

## 调研时发现的问题

1. **机械去重覆盖过窄。** `SkillMaintenancePolicy.deduplicate` 不处理不同措辞的同义规则、散落重复项、相似步骤和例外分支。它可作预清理，不能作为语义维护的主实现。
2. **长度检查漏了实际输入。** `SkillMaintenanceApplicationService.scanBatch` 仅统计入口 `content`。自动生成的入口可能很短，但要求读取 `resources/method.json`；完整使用成本需同时观察这些必读内容，不能靠移入资源文件制造变短。
3. **来源数量不是质量分数。** 三个独立来源、两种条件，以及 2,000 token／五次修改触发检查，来自本项目已确认方案。此次源码审阅没有发现它们是这些项目共同采用的行业标准；也未做参数有效性实验。保持可配置并明确证据边界，不以数目达标声称普遍有效。
4. **模型审查仍可能同源误判。** 单独一次 Terra 调用只是职责分开，不能叫独立事实验证。结构、依赖、权限和引用由程序检查；内容判定与实际效果分开保存。
5. **原始证据归档与模型输入应区分。** 当前超大工具结果导致后台输入超限。完整保存审计材料有必要，但每轮重复塞入全部原文并不是良好的成本控制。可以设计带来源定位的分层读取；关键条件、冲突和反例必须可回查，不能静默截断后声称已完整审阅。
6. **开源实现本身也有取舍。** Hermes 当前提示包含较激进的合并数量要求，AutoSkill 某些失败路径采用启发式合并；EvoSkill 有缩减失败轨迹输入的兜底。这些行为不自动适用于本项目的权限、来源和绑定约束。

## 已接受的调整方向

继续采用“Episode → 有结果依据的经验 → 同方法归组 → 比较已有 Skill → 最小必要改动 → 检查 → 版本发布”。改进重点放在方法内容，而不是继续增加发布状态和数量门槛。

- **语义整理：** 在同一方法内融合同义规则，把条件不同的步骤整理成明确分支；常规步骤留在入口，按条件需要的细节放到有主题的资源中。每条改动说明保留、融合、移动或删除了什么，以及来源。不能为了短而删掉停止条件。
- **准确分类改动：** 只有表述等价的整理才属于 COMPRESS；改变适用范围、步骤或验收标准仍属于 PATCH，按原来源规则处理。无法证明等价就保留旧版，不让人工审核成为正常对话的等待点。
- **来源输入分层：** 完整原始记录保留，先读可核验经验与证据目录，再按引用补读大结果；输入不足显式暂存。已实现受限目录补读及审计，验收范围见[大证据分层读取](大证据分层读取验收.md)，不能把输入缩小直接写成真实经验沉淀通过。
- **针对性评估：** 利用已有来源任务、旧适用任务、边界和失败样本做平台或后台评估，记录有效性与成本。这里没有建议恢复每份 Skill 固定 10／20 题或灰度审批门槛，也没有声称模型自评可替代环境执行。

## 本地事实与未完成项

本轮隔离合成输入通过真实 API 创建，Terra 实际审查后 v1 → v2；正文 25,304 → 12,690 字节，资源 Hash 不变，PG 索引 READY，MySQL 活动指针一致。重复初始化未覆盖版本。证据：`work/ops-grouping-20260926/maintenance-active.json` 及同名 SQL、`maintenance-seed-repeat.json`、`maintenance-active-browser.png`。

`maintenance-package-4.log`：原机械整理批次 39 项通过、0 失败、0 跳过，包含真实 MySQL／PG 发布测试和全局关闭开关测试。后续语义维护及回滚修复已重新部署，当前验收版本见 `semantic-maintenance-deploy-2.json`。

语义维护批次已完成：短入口引用长方法触发整理，Terra 合并不同措辞的重复规则，经另一次 Terra 内容复核，正常发布 v2；浏览器操作回滚发现历史包丢失路由元数据，修复后重新回滚生成 v4，正文、资源 Hash 和历史适用边界一致，向量索引 READY，运行指针一致。全部历史版本保留。

后端 `semantic-maintenance-package-2.log`：63 项通过，0 失败，0 跳过；前端 `semantic-maintenance-web-5.log`：210 项通过，类型检查和构建通过。证据与可重跑命令见 [语义维护与回滚验收](语义维护与回滚验收.md)。

以上属于合成输入的整理、发布及实际页面回滚验收。真实运维任务效果无损、来源分层输入、平台全部评估仍未完成，不能计为通过。
