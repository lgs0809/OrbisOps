#!/usr/bin/env python3
"""Connect the isolated stack to the owner's existing, authorized Luna/Terra gateway.

Copies only model configuration into the private acceptance env. Never creates a
provider account, rotates credentials, changes vector models or starts background
model jobs. Redeploy the acceptance backend after running this command.
"""
from pathlib import Path
import argparse
import json
import os
import tempfile
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]


def read_env(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def decoded(value):
    return value.strip().strip('"').strip("'")


def configure(output):
    source = read_env(ROOT / 'deploy/orbisops.env')
    target = ROOT / 'deploy/.env.acceptance'
    existing = read_env(target)
    if decoded(existing.get('ORBISOPS_COMPOSE_PROJECT_NAME', '')) != 'orbisops-acceptance':
        raise ValueError('Expected the isolated acceptance environment')
    fields = ['ORBISOPS_MODEL_BASE_URL', 'ORBISOPS_MODEL_API_KEY']
    if not all(decoded(source.get(key, '')) for key in fields):
        raise ValueError('Existing authorized model configuration is missing')
    endpoint = urlparse(decoded(source[fields[0]]))
    if endpoint.scheme != 'https' or not endpoint.hostname or endpoint.username or endpoint.password:
        raise ValueError('Expected an HTTPS model endpoint without URL credentials')
    updates = {key: source[key] for key in fields}
    updates.update(ORBISOPS_CHAT_MODEL='gpt-5.6-luna', ORBISOPS_AI_MODEL_CALLS_ENABLED='true')
    for key in fields:
        previous = decoded(existing.get(key, ''))
        if previous not in ('', 'placeholder', 'https://model-provider.invalid', decoded(source[key])):
            raise ValueError('An existing acceptance model binding differs; it was retained')
    lines, written = [], set()
    for line in target.read_text().splitlines():
        key = line.split('=', 1)[0] if '=' in line and not line.lstrip().startswith('#') else None
        if key in updates:
            if key not in written:
                lines.append(key + '=' + updates[key])
                written.add(key)
        else:
            lines.append(line)
    lines.extend(key + '=' + value for key, value in updates.items() if key not in written)
    new_text = '\n'.join(lines) + '\n'
    changed = new_text != target.read_text()
    if changed:
        fd, temporary = tempfile.mkstemp(prefix='.acceptance-model-', dir=target.parent)
        try:
            with os.fdopen(fd, 'w') as stream:
                stream.write(new_text)
            os.chmod(temporary, 0o600)
            os.replace(temporary, target)
        finally:
            if os.path.exists(temporary): os.unlink(temporary)
    os.chmod(target, 0o600)
    result = {'status': 'PASS', 'changed': changed, 'providerHost': endpoint.hostname,
              'defaultModel': 'gpt-5.6-luna', 'authoringModel': 'gpt-5.6-terra',
              'credentialsPrinted': False, 'requiresBackendRedeploy': True}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    configure(parser.parse_args().output)
