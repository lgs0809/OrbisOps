# 2026-11-04：工作流页面、显式审批与工具依赖恢复

本批关联 OPS-02、OPS-03；不更改三阶段生产写权限边界、模型或后台 Episode 规则。后端仍为已验证的 e4f5bb1490741b65de8aaf8a3f0e6a59b2f6299efb3d791685d4d012aa8dd535，本批只部署新的前端静态制品。

## 页面修复

- 保存、绑定校验、发布及版本操作失败后，在编辑器持续显示可读错误，后台刷新不会立即抹掉错误。
- 复用已有 Semi Banner 与 ApiRequestError；仅400拓扑校验的明确领域说明进入产品提示，其他传输、URL等诊断仍隐藏。2个新增错误转换回归验证领域说明及隐私边界。
- 停用MCP在动作概览中标注“已停用”，选择器禁用对应选项；不改变原工具启停状态或放宽服务端授权。

`cd web && npm run verify`：卫生检查、类型检查、**458/458**、构建通过。前两次456项构建及发布也保留；最后移除末尾空行并重新验证，最终制品以第四次verify与第三次deployment记录为准。静态文件逐个匹配，代理与后端超时契约一致，后端未重启。

```sh
(cd web && npm run verify) > "$EVIDENCE_DIR/web-verify.log" 2>&1
python3 scripts/deploy-tested-web-acceptance.py \
  --test-log "$EVIDENCE_DIR/web-verify.log" --output "$EVIDENCE_DIR/web-deployment.json"
```

## 实际创建、发布与恢复

在真实浏览器从标准只读工作流复制独立副本，保存说明，校验发布；名称为“11月4日 · 只读工具与审批核对”，标识 `workflow-11-4`。旧v1～v3草稿引用已停用且未绑定的工具，被服务端正确拒绝，失败与草稿保留。副本通过编辑器绑定当前已启用工具，并同步作者侧动作配置，正常发布v4。没有启用旧工具或更改其他工作流。

自然语言提问：

> 请执行当前工作流的只读核对，保留实际工具回执。

第一次真实运行 `chat-chat-session-43e6b4bd-9a7f-4a2a-a3ef-0d840439e107-5cfd5486` 失败，原因是协议MCP侧车已停止；页面正确展示未完成，原失败不删除。按原Compose恢复依赖、保留原SQLite数据卷：

```sh
docker compose --project-name orbisops-acceptance --env-file deploy/.env.acceptance \
  -f compose.yml -f deploy/compose.acceptance.yml \
  up -d --no-deps --no-build --wait --wait-timeout 60 mcp-acceptance
```

同一会话再次自然语言执行，Run `chat-chat-session-43e6b4bd-9a7f-4a2a-a3ef-0d840439e107-7556584b` 成功。MySQL运行记录、冻结v4/hash、唯一实际远端调用、SQLite目标状态与完整回执8项交叉检查通过；另6项核对确认首轮失败仍保留、版本与问题均未改变。依赖是通过上述部署命令恢复，不宣称系统自动启动了已停止容器。

## 显式人工审批

为了实际检验批准前零调用，使用正常草稿API加入HUMAN_APPROVAL节点，再通过页面校验发布v6：Start → confirm-read → MCP → End。v5草稿及v4发布记录保留。

从编辑器“测试”打开冻结v6对话，发送：

> 请按工作流先确认，再执行本地只读工具核对，并保留回执。

Run：`chat-chat-session-206336fb-ab52-4da3-928c-06b01c5daca4-bad0f390`。

1. 正常进入WAITING_APPROVAL；API审批ID与节点一致，MySQL工具表及远端tools/call均为0。
2. 从对话进入工作台，点击“批准并继续”及“确定”。
3. 原审批记录保持同一ID、记录实际处理人，工作流自动继续至SUCCEEDED；没有点击“重新调度继续执行”。
4. 实际远端调用恰好一次，接收时间晚于批准时间，冻结v6/hash保持一致。8项协议交叉检查及7项审批时序核对通过。

页面输出为正常工具结果卡，原始JSON收在折叠详情中。当前手动入口：<http://127.0.0.1:3302/workflows/config?projectId=ops-acceptance-a&agentId=workflow-11-4>。

## 验收范围

以上是合成协议夹具上的真实HTTP MCP、MySQL、SQLite和人工审批链路，没有模型调用、业务资源变更、任务验收或新增Skill源；界面模型选择值不代表DIRECT节点调用了模型。多轮重试不会被算成20个独立任务。

本批没有重新跑5,637项后台全量回归；后台制品未变。此前真实三阶段与Skill方法使用证据保留，但完整Skill CREATE/PATCH/SPLIT/MERGE现实任务矩阵、容量压测与正式大集仍未全部验收。本批不宣称这些已完成，也未修改秋招文档或执行Git提交/推送。

本批日志、SQL、草稿请求、截图及完整报告保存在工作目录 `work/acceptance-20261104-0800/`。目录名仅是批次标签，实际执行时间看各记录。
