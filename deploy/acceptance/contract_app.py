from qwen_cpu_runtime import configure

# PyTorch thread budgets must be set before eager/model work starts.
CPU_RUNTIME = configure()

from qwen_cpu_attention import configure as configure_attention
CPU_ATTENTION = configure_attention()

import app as source_app
from qwen_model_residency import install as install_residency

RESIDENCY = install_residency(source_app)
app = source_app.app
from orbisops_retrieval_contract import install

install(app, source_app.embedding_model, source_app.rerank_model)

from qwen_embedding_length_guard import install as install_length_guard
from qwen_encoding_identity import install as install_encoding_identity
# The length guard owns admission around both certification and inference.
ENCODING_IDENTITY = install_encoding_identity(source_app)
install_length_guard(source_app)

from qwen_inference_admission import GATE

# Readiness reports model loading and resource admission separately; busy is not a
# failed model load, and a kernel overrun is recovered by the container supervisor.
original_readiness = source_app.readiness_payload


def readiness_with_inference():
    payload = original_readiness()
    residency = RESIDENCY.snapshot()
    payload['residency'] = residency
    payload['ready'] = (set(residency['verifiedKinds']) == {'embedding', 'rerank'}
                        and residency['residentKind'] is not None
                        and residency['loadingKind'] is None)
    payload['status'] = 'READY' if payload['ready'] else 'NOT_READY'
    payload['inference'] = GATE.snapshot()
    payload['cpuRuntime'] = CPU_RUNTIME
    payload['cpuAttention'] = CPU_ATTENTION
    return payload


source_app.readiness_payload = readiness_with_inference
