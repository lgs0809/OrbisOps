# 复用本机已有 Qwen 检索模型

现有 `embedding-model-cache` 卷已包含约定的 Qwen3-VL Embedding/Reranker 2B。无需重新下载权重或创建 Python 虚拟环境。`embedding-model:local` 原镜像保留为基础；兼容层统一管理原服务的模型加载、运行许可和长度校验，保留既有 `/v1/*` 路由。

固定身份：
- Embedding：Qwen/Qwen3-VL-Embedding-2B，`9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda`。
- Reranker：Qwen/Qwen3-VL-Reranker-2B，`4bd860ac4f15ad1897a214615cccc700f8f71818`。
- 新接口：`http://127.0.0.1:8110/orbisops/embed`、`/orbisops/rerank`。后端容器通过 `host.docker.internal:8110` 访问；主机端口仅绑定 127.0.0.1。
- 新接口要求 Bearer 凭据；本机保存在 `deploy/.acceptance-private/retrieval-contract.json`，不可提交或放入截图。原服务其他接口的鉴权行为不在本次改动范围。

在仓库根目录执行：

```sh
docker run --rm --network none -v "$PWD/deploy/acceptance:/contract:ro" -w /contract embedding-model:local python -m unittest test_retrieval_contract
python3 scripts/deploy-existing-retrieval-contract.py
python3 scripts/connect-existing-retrieval.py
python3 scripts/test-existing-retrieval-contract.py --output /tmp/orbisops-model-contract.json
python3 scripts/test-existing-retrieval-cache.py --output /tmp/orbisops-model-cache.json
python3 scripts/test-existing-retrieval-java.py --output /tmp/orbisops-java-fallback.log
python3 scripts/inspect-acceptance-environment.py --output /tmp/orbisops-environment.json
```

输出文件已存在时测试拒绝覆盖，请换路径。模型测试会实际占用 CPU；与后台索引构建同时运行可能返回 503，需保留失败记录，空闲时复测，不能把忙碌拒绝当成超时降级通过。Java 测试使用已打包 JAR 中的生产实现和依赖，编译一个合成授权候选目录的真实模型超时探针。它不证明完整用户权限链或模型质量。

后端定向打包：

```sh
mvn -f server/pom.xml -Dtest=OpsSkillLlmRerankAdapterTest,SkillHybridRetrievalTest -Dsurefire.failIfNoSpecifiedTests=false package
```

之后用 `scripts/deploy-tested-acceptance.py --test-log <已保存的成功打包日志> --output <新证据路径>` 部署。模型连接脚本会拒绝在存在 RUNNING 任务时重启验收后端，保留原环境配置备份；不会改数据库成功状态。模型升级脚本按源码摘要判断是否需要更新；启动失败恢复旧容器。推理验收后可删除本次明确记录的停止备份容器，不能带 `-v`，也不能清理共享模型卷。

接口验证实际加载模型的 commit hash；文本 NFC，查询固定指令，输出为 1024 维 L2 单位向量，重排返回原始分数和输入候选 ID。Java 显式使用 HTTP/1.1，避免 Uvicorn 对 h2c 升级请求丢失请求体。5 秒请求预算保留，超时或忙碌时上层保留授权的词法／RRF 结果。

新接口最多同时执行一次推理，不无限排队。文档向量成功结果仅在进程内缓存：最多 128 项、15 分钟有效，键含完整模型／版本／预处理／维度／规范化文本；查询和重排不缓存。较长文档第一次可能超时，但后台推理完成后，下轮投影修复可复用精确相同的结果。缓存不写磁盘，不改变 MySQL 发布检查和 PG READY 条件。重启会丢失缓存，后台扫描重算；旧 READY 索引保留。

实际结果和未验收项以 `docs/acceptance/2026-09-10-landing/重跑步骤.md` 最新批次为准。CPU 模型功能可用不等于 5 秒性能通过，也不等于 1 万条 ANN／240 题质量验收通过。

## 共享 CPU 推理线程预算（2026-10-02）

`contract_app.py` 在导入模型服务前调用 `qwen_cpu_runtime.configure`。默认
`INFERENCE_CPU_THREADS=2`、`INFERENCE_INTEROP_THREADS=1`，原模型、权重缓存、维度和
180 秒资源期限不变。`/ready` 的 `cpuRuntime` 返回实际 PyTorch 配置。线程数是本机
资源配置，不是已经测得的最优值；完整语义模型推理仍须实际完成后才算解除阻塞。
按 [PyTorch 官方要求](https://docs.pytorch.org/docs/stable/generated/torch.set_num_threads.html)，
线程设置必须早于 eager/model 工作；[CPU 线程竞争说明](https://docs.pytorch.org/docs/stable/notes/multiprocessing.html)
解释了过量线程可能造成的资源竞争，本次配置的效果以实测为准。

兼容回归可在原镜像内运行，不下载权重、不挂载业务数据：

```sh
docker run --rm --network none --cpus 1 --memory 256m \
  -v "$PWD/deploy/acceptance:/contract:ro" -w /contract embedding-model:local \
  python -m unittest test_qwen_cpu_runtime test_qwen_embedding_length_guard \
  test_qwen_inference_admission test_embedding_dimensions_patch test_retrieval_contract \
  test_qwen_model_residency
```

部署脚本重新检查共享推理准入状态；有 kernel 正在运行或状态未知时拒绝替换。
原容器保留为报告中的 `rollbackContainer`，模型卷完全保留。恢复时先确保没有正在
执行的推理，再停止新容器并改为其他备份名，将报告中的旧容器改回 `embedding-model`
并启动；不删除任何容器、镜像或数据卷。2026-10-02 本批的配置与 23 项兼容回归
证据保存在工作目录的 `qwen-cpu-runtime-*` 文件中，真实完整输入复测另行记录。

## 同一镜像的本地 CPU 配置对照（2026-10-03）

部署入口支持 `--cpu-threads`、`--interop-threads` 和新路径 `--output`。仅调整线程且
contract source 摘要未变时，复用当前容器的精确 image ID，不构建或拉取镜像，不下载
权重。默认调用保留当前显式线程配置；未配置时仍为 2/1。线程调整必须提供新的证据
文件，已有文件会在部署前拒绝覆盖。

```sh
python3 scripts/deploy-existing-retrieval-contract.py --cpu-threads 4 --interop-threads 1 \
  --output /tmp/qwen-cpu4-deployment-new.json
```

先协调所有调用方，等待实际 `/ready` 中 `inference.busy=false`；部署脚本在替换前
再次检查。报告保存调整前后实际 readiness、image/container identity 和精确恢复命令，
不含环境凭据。旧容器保留停止；若新进程未就绪，也保留失败容器并恢复旧容器，不
删除卷或模型。恢复前同样必须等无在途推理。4/1 是本机性能对照配置，不能仅凭
readiness 宣称长输入、重排或性能通过；输入、模型、1024 维接口和 180 秒期限均按
原合同实际验证。

## 加载与单模型驻留（2026-10-03）

`qwen_model_residency.py` 复用原构造器及离线权重缓存，通过锁保证同一首次加载不被
并发重复执行。加载、完整预处理、推理及显式 preload 均处于同一运行许可内，CPU
操作在线程池执行；忙碌时仍返回 503，不排无限队列。切换模型前释放前一对象并由
Python 回收，在本机限制一次只驻留一个模型，不改变权重、数值精度、指令或维度。
这是本地资源策略，模型切换会增加加载时间，不能声称它已经满足 5 秒性能要求。

原 `lru_cache` 可在首次结果缓存前调用构造器多次，见
[Python 官方说明](https://docs.python.org/3/library/functools.html)。原兼容重排调用还会
在线程池/许可之前求值 loader。回归现覆盖这两个边界及错误后释放、实际子进程超限
退出、完整输入限制与原接口尺寸，共 28 项通过；轻量对象测试不算 Qwen 推理质量。

`/ready` 的 `residency` 分别报告容量、驻留/加载中的种类、实际成功加载验证过的种类
及加载次数；非驻留对象显示 `evicted`。`ready=true` 表示本进程已成功加载验证两个
本地模型且当前有可用对象，并不声称两个模型同时驻留。忙碌状态仍独立报告。
启动失败继续回滚旧容器，旧卷及停止容器保留。

```sh
python3 scripts/deploy-existing-retrieval-contract.py --output /tmp/qwen-residency-deploy-new.json
python3 scripts/test-existing-retrieval-residency.py --output /tmp/qwen-residency-real-new.json
```

本轮真实合成文本切换测试三次均 HTTP 200：编码 44.229 秒，重排 38.481 秒，再编码
20.844 秒；1024 维 L2 向量重载前后逐项一致、相关文档排第一，每次原生读回仅一个
驻留对象，容器身份及 restart=0 始终相同。证据在工作目录
`work/resume-20261003/qwen-residency-real-1.json`；实际部署及原生旧故障记录另存。
重排仍超出部分调用方期限，完整问答和后台索引必须各自验证，不能用此三次测试替代。

## CPU Attention 框架兼容与完整输入诊断（2026-10-03）

`qwen_cpu_attention.py` 使用已安装 Transformers 的 `AttentionInterface.register`
扩展既有 `sdpa` 后端，保留其原 mask formatter，并继续调用官方
`sdpa_attention_forward`。默认 `native` 不改数值计算；显式 `expanded-kv` 只在 CPU
及原函数将启用 GQA 时，使用官方 `repeat_kv` 展开 K/V，然后交回同一 SDPA 函数。
padding、因果关系、位置 bias、scale、dropout、dtype、完整输入及非 CPU 调度均保留。
不复制整段 attention 算法，不安装新包、下载权重或改变模型精度。

参见 [Transformers AttentionInterface](https://huggingface.co/docs/transformers/attention_interface)
和 [PyTorch 2.8 SDPA](https://docs.pytorch.org/docs/2.8/generated/torch.nn.functional.scaled_dot_product_attention.html)。
不同 SDPA kernel 可能带来浮点舍入差异，不能声称输出逐项相同。框架原生测试验证
float32/bfloat16 的数值近似、padding 排除、因果规则、位置 bias、参数转交和 mask
注册不变，8 项通过；这些小张量测试不替代完整模型输入或正式质量验收。

部署 source 摘要现在包含全部 9 个运行文件。`--cpu-attention native|expanded-kv`
必须同时提供新 `--output`；只改策略时复用精确 image ID，不构建、拉取或改模型卷。
报告及 `/ready.cpuAttention` 明确保存实际策略，策略未知时不冒称原生一致。

```sh
docker run --rm --network none --memory 768m --cpus 2 \
  -v "$PWD/deploy/acceptance:/contract:ro" -w /contract \
  --entrypoint python embedding-model:local -m unittest test_qwen_cpu_attention
# 先停止新调用、等待现有 kernel 自然退出，确认 inference.busy=false。
python3 scripts/deploy-existing-retrieval-contract.py --cpu-attention expanded-kv \
  --output /tmp/qwen-cpu-attention-deploy-new.json
python3 scripts/test-existing-retrieval-input.py --body /完整合成输入.json \
  --previous-vector /已有实际向量.json --output /tmp/qwen-full-input-replay-new.json
```

输入诊断日志只记请求 ID、完整输入 SHA/长度、实际原生 token 数及 tensor shape/dtype，
不记录原文或凭据。2026-10-03 的实际 `native` 诊断记录了完整 10041 字、2754 token，
实际 Qwen CPU/bfloat16、Q shape `[1,16,2754,128]`、K/V `[1,8,2754,128]`、无 mask，
原函数会启用 GQA。完整复放结果与策略调整必须分别保存；日志显示进入模型不等于
已得到向量，旧策略失败不删除。策略改进的性能效果仍以真实完整输入结果为准。
