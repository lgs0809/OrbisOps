#!/usr/bin/env python3
"""Read-only size and round-trip audit against frozen Skill sources. No model calls or state writes."""
import argparse
import json
import os
from pathlib import Path
import runpy
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def inspect(output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Use a new evidence path')
    helper = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    query = ("SELECT JSON_OBJECT('sourceId',source_id,'sourceHash',input_hash,'body',input_json) "
             "FROM ai_ops_skill_evolution_source WHERE project_id='ops-acceptance-a' ORDER BY create_time DESC LIMIT 20")
    rows = helper['rows'](query)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    classpath = os.pathsep.join(str(ROOT / 'server' / name / 'target/classes')
                               for name in ('orbisops-domain', 'orbisops-trigger'))
    with zipfile.ZipFile(ROOT / 'server/orbisops-app/target/orbisops-app.jar') as packaged:
        logging_jars = [Path(name).name for name in packaged.namelist() if name.startswith('BOOT-INF/lib/slf4j-api-')]
    if len(logging_jars) != 1:
        raise ValueError('Build the tested application JAR first')
    installed = list((Path.home() / '.m2/repository/org/slf4j/slf4j-api').glob('*/' + logging_jars[0]))
    if len(installed) != 1:
        raise ValueError('The packaged logging API must be available in the local Maven repository')
    classpath += os.pathsep + str(installed[0])
    reports = []
    for row in rows:
        completed = subprocess.run([java, '--class-path', classpath, str(ROOT / 'scripts/InspectSkillModelInput.java')],
                                   input=row['body'], text=True, capture_output=True, timeout=30, check=True)
        report = json.loads(completed.stdout)
        assert report['sourceHash'] == row['sourceHash'], 'Frozen source integrity mismatch'
        report['sourceId'] = row['sourceId']
        reports.append(report)
    result = {'scope': 'READ_ONLY_REAL_SOURCE_ENCODING_NO_MODEL_CALLS', 'sources': reports}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    output.with_suffix('.sql').write_text(query + ';\n')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    inspect(parser.parse_args().output)
