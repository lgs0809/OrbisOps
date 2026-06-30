ARG BASE_IMAGE=embedding-model:local
FROM ${BASE_IMAGE}
COPY orbisops_retrieval_contract.py /app/orbisops_retrieval_contract.py
COPY qwen_inference_admission.py /app/qwen_inference_admission.py
COPY qwen_cpu_runtime.py /app/qwen_cpu_runtime.py
COPY qwen_model_residency.py /app/qwen_model_residency.py
COPY qwen_cpu_attention.py /app/qwen_cpu_attention.py
COPY qwen_embedding_length_guard.py /app/qwen_embedding_length_guard.py
COPY qwen_encoding_identity.py /app/qwen_encoding_identity.py
COPY contract_app.py /app/contract_app.py
COPY patch_embedding_dimensions.py /app/patch_embedding_dimensions.py
RUN python /app/patch_embedding_dimensions.py
CMD ["python", "-m", "uvicorn", "contract_app:app", "--host", "0.0.0.0", "--port", "8110"]
