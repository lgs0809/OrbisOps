# 独立日常展示空间

展示空间包含交易中台、支付清结算、仓配履约三个项目。项目、成员、只读资源、MCP、手工 Skill、工作流发布及暂停的巡检规则通过正常接口创建。过往会话、任务、告警、审批及候选记录为合成历史，来源在记录和导入清单中标明；它们用于页面展示，不能作为真实模型、资源执行或验收的证据。

页面地址为 `http://127.0.0.1:3304`，接口为 `http://127.0.0.1:18099`。依赖只监听本机：MySQL 13319、pgvector 15449、Redis 16399。Compose 项目固定为 `orbisops-showcase-v2`，拥有五个独立数据卷。旧展示项目及原验收库的数据保留；新环境不复用它们的数据卷。

## 初始化与账号

在仓库根目录使用本地已验证的完整镜像 ID：

```sh
python3 scripts/showcase-workspace.py plan
python3 scripts/showcase-workspace.py init --server-image sha256:服务镜像完整ID --web-image sha256:页面镜像完整ID
python3 scripts/showcase-workspace.py up
python3 scripts/showcase-workspace.py accounts
python3 scripts/showcase-workspace.py catalog
python3 scripts/showcase-workspace.py history
python3 scripts/showcase-workspace.py verify
python3 scripts/verify-showcase-runtime.py --output /私有证据目录/runtime-check.json
python3 scripts/showcase-workspace.py seal
```

只有三个账号：一个管理员、一个值班维护者、一个项目复核者。默认显示名为通用角色，可以在初始化时用 `--display-names '["管理员姓名","值班成员姓名","复核成员姓名"]'` 指定本机显示名；个人姓名不必写入公共仓库。三个手机号 `19900000001`、`19900000002`、`19900000003` 是虚构的展示身份。

初始密码随机生成，保存于 `deploy/.showcase-private-v2/accounts.json`。目录权限0700，秘密文件权限0600，已被 Git 忽略。创建账号时只存加密密码；首次登录必须自行改密码，管理员只能重置为创建时的初始密码。修改或重置后，旧登录令牌立即失效。普通用户可以修改自己的密码、正常退出，不能添加账号或重置其他人的密码。

初始化脚本通过正常个人接口暂时为管理员设置维护密码，以完成建项目和配置发布；不会直接改数据库绕过首次修改。完成后执行 `seal`，通过正常管理员重置接口恢复三人的初始密码，并使维护令牌失效。交付时三人均须在首次使用中选择自己的密码。没有公开自助注册入口，新成员由管理员在「用户与权限」添加，再在项目成员页分配权限。

值班维护者在三个项目中为 MAINTAINER，复核成员为 APPROVER，管理员保留平台管理权限。成员与任务使用内部用户 ID；历史兼容的项目 owner 名称保留原有解析方式。项目复核权限不授予生产写权限。

## 时间范围与关联内容

初始化把历史截止日期固定为当天，起始日期为2026-06-30，包含周末。每个项目每天一条排查和一条巡检历史，每条排查关联自然语言对话、工作流节点、任务及 Episode。以2026-10-10为截止日，共103天、309次排查、1,236条消息、309条巡检记录。

告警每四天安排一组不同症状的关联示例，保留原始事件；变更包每五天安排一条草稿、复核、拒绝或归档示例；沉淀候选每周一条。上述窗口分别产生156个事件、78个关联组、63个变更包和45条候选。合成审批示例使用两个不同的复核身份，仍不构成实际审批或执行证明。

每个项目每天有90条业务接收记录，对应72个请求标识，包含78条正常、6条拒绝和6条超时。103天每库9,270条观察；三个业务库的观察、服务目录、依赖、配置与维护窗口共27,858行，包含三个来源标记。只读账号仅获得自己的业务库 SELECT 权限，资源连接必须真实扫描后才能成为 SCANNED。

合成 Run 没有执行租约或供应商回执。Episode 来源已分配，自动复盘输入标记为 SOURCE_BLOCKED；候选为 REJECTED、结果为 SKIPPED，知识文档为 NOT_INDEXED。定时触发、自动进化、模型调用及 Landing 执行默认关闭，资源执行目标 DISABLED。手工 Skill 和工作流的正常发布只证明定义有效。要测试实际审批、灰度或资源执行，应新建真实任务并完整执行流程。

## 重跑与保留数据

在封存前再次运行 `accounts`、`catalog`、`history`，只复用或插入缺失记录。历史逐列核对，发现用户修改或身份冲突即停止，不覆盖数据。完整初始化 SQL、清单和失败记录保存在私有目录；历史只使用 INSERT，口令只出现在私有建库文件。日期截止值固定，重新运行不会因当天变化重写历史。

封存后，管理员首次登录设置个人密码即可手动维护。再次通过脚本配置时，需要在私有的 `maintenance-login.json` 中提供管理员当前密码；脚本不会猜测、自动重置或覆盖用户后来设置的密码。只读历史验证无需更改密码。

```sh
python3 scripts/showcase-workspace.py stop
python3 scripts/showcase-workspace.py up
python3 scripts/showcase-workspace.py verify
python3 scripts/showcase-workspace.py restart
```

停止和重启都保留五个数据卷。更新经过验证的镜像可运行 `refresh-images --server-image sha256:完整ID --web-image sha256:完整ID`，脚本先核对项目、数据库和活动任务，再替换页面及后端，保留秘密与数据。

## 实际使用与证据边界

用手机号和初始密码登录，先设置自己的密码，再查看项目会话、告警关联、变更包、工作流、Skill 和巡检配置。普通用户仅能看到获授权的项目；密码修改入口在用户菜单。管理员可以添加新成员和恢复初始密码。

展示库未连接真实模型供应商。执行新任务前需要配置已授权的 `gpt-6-luna` 和 `gpt-6.1-terra`，并核对只读资源、审批及执行目标。页面展示、历史导入、模型闭环和实际资源执行分别验收，不把接口200或合成状态算作后两项通过。

本地交付记录保留实际浏览器截图、数据库核对、只读权限拒绝、重跑及重启结果，区分通过、失败、未测和受阻。原验收库的账号、业务数据、模型配置及 Git 历史保持原状。
