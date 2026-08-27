"""Compile an isolated read-only probe against the exact tested/deployed application modules."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA_HOME = Path('/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home')


def run_probe(name, payload, timeout=90):
    jar = ROOT/'server/orbisops-app/target/orbisops-app.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
                                      'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0]
    if deployed != digest:
        raise RuntimeError('The local tested application does not match the running container')
    prefixes = ('orbisops-domain-', 'orbisops-infrastructure-', 'orbisops-types-', 'orbisops-application-',
                'spring-jdbc-', 'spring-tx-', 'spring-core-', 'spring-beans-', 'spring-jcl-',
                'mysql-connector-j-', 'slf4j-api-', 'fastjson-', 'fastjson2-')
    with tempfile.TemporaryDirectory(prefix='ops-repository-read-') as temporary:
        dependencies = []
        with zipfile.ZipFile(jar) as packed:
            for entry in packed.namelist():
                if entry.startswith('BOOT-INF/lib/') and Path(entry).name.startswith(prefixes):
                    target = Path(temporary)/Path(entry).name
                    target.write_bytes(packed.read(entry))
                    dependencies.append(str(target))
        classpath = os.pathsep.join(dependencies)
        source = ROOT/'scripts'/f'{name}.java'
        compiled = subprocess.run([str(JAVA_HOME/'bin/javac'), '--class-path', classpath,
                                   '-d', temporary, str(source)], text=True, capture_output=True, timeout=30)
        if compiled.returncode:
            raise RuntimeError('Read-only probe compilation failed: '+compiled.stderr[-2000:])
        password = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-mysql-1',
                                           'sh', '-c', 'printf "%s" "$MYSQL_ROOT_PASSWORD"'], text=True)
        env = dict(os.environ, OPS_ACCEPTANCE_READ_PASSWORD=password)
        command = [str(JAVA_HOME/'bin/java'), '--class-path', os.pathsep.join([temporary, classpath]),
                   f'cn.lgs.orbisops.infrastructure.adapter.repository.{name}']
        result = subprocess.run(command, input=payload, text=True, capture_output=True, env=env, timeout=timeout)
        if result.returncode:
            raise RuntimeError('Read-only repository check failed: '+result.stderr[-2000:])
        return dict(json.loads(result.stdout), jarSha256=digest, probeSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest())
