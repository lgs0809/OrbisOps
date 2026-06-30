"""Configure the shared CPU model process before importing/loading its models.

This changes CPU scheduling only. Model IDs, weights, tokenizer/context, output
dimensions and the hard resource deadline retain their existing contracts.
"""
import os


def _positive_setting(env, name, default):
    value = int(env.get(name, default))
    if not 1 <= value <= 256:
        raise ValueError(name + ' must be between 1 and 256')
    return value


def configure(env=None, torch_module=None):
    env = os.environ if env is None else env
    threads = _positive_setting(env, 'INFERENCE_CPU_THREADS', '2')
    interop = _positive_setting(env, 'INFERENCE_INTEROP_THREADS', '1')
    # Set these before torch and tokenizer libraries initialize their native pools.
    env.setdefault('OMP_NUM_THREADS', str(threads))
    env.setdefault('MKL_NUM_THREADS', str(threads))
    env.setdefault('OPENBLAS_NUM_THREADS', str(threads))
    env.setdefault('TOKENIZERS_PARALLELISM', 'false')
    if torch_module is None:
        import torch as torch_module
    torch_module.set_num_threads(threads)
    torch_module.set_num_interop_threads(interop)
    return {'intraOpThreads': torch_module.get_num_threads(),
            'interOpThreads': torch_module.get_num_interop_threads(),
            'tokenizersParallelism': env['TOKENIZERS_PARALLELISM'],
            'resourcePolicy': 'bounded-cpu-pools-v1'}
