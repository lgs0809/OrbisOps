#!/usr/bin/env python3
"""Run the focused Java/MySQL regressions in an isolated schema on the existing local fixture.

Keeps all test records for readback. Does not start a database, change passwords,
touch showcase/business tables, publish a Skill or run a model.
"""
from pathlib import Path
import argparse
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DATABASE = 'orbisops_mcp_counting_20261102'


def main(output, java_home, database=DATABASE, tests=None, migrate=False):
    if not re.fullmatch(r"orbisops_mcp_counting_[a-zA-Z0-9_]+", database):
        raise ValueError("Only explicitly isolated counting/regression schemas are allowed")
    tests = tests or "OpsMcpDiscoveryFrameworkTest,McpDiscoveryCountingBasisMySqlIT,SkillEffectOutcomeMySqlIT"
    if not re.fullmatch(r"[a-zA-Z0-9_,#]+", tests):
        raise ValueError("Use an explicit safe list of test classes/methods")
    if output.exists():
        raise ValueError('Previous evidence is retained; choose a fresh log path')
    values = dict(line.split('=', 1) for line in (ROOT / 'deploy/.env.acceptance').read_text().splitlines()
                  if line and not line.startswith('#') and '=' in line)
    if values.get('ORBISOPS_COMPOSE_PROJECT_NAME', '').strip('"\'') != 'orbisops-acceptance':
        raise ValueError('Only the existing local acceptance instance is allowed')
    command = ['docker', 'exec', '-i', 'orbisops-acceptance-mysql-1', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B']
    subprocess.run(command, input=f'CREATE DATABASE IF NOT EXISTS {database} CHARACTER SET utf8mb4;\n',
                   text=True, check=True, stdout=subprocess.DEVNULL)
    env = dict(os.environ, JAVA_HOME=str(java_home), PATH=str(java_home / 'bin') + ':' + os.environ['PATH'],
               MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',
               ORBISOPS_DISCOVERY_TEST_JDBC_URL=f'jdbc:mysql://127.0.0.1:13362/{database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
               ORBISOPS_DISCOVERY_TEST_MYSQL_USER='root',
               ORBISOPS_DISCOVERY_TEST_MYSQL_PASSWORD=values['ORBISOPS_MYSQL_ROOT_PASSWORD'].strip('"\''),
               ORBISOPS_MEMORY_TEST_PG_URL='jdbc:postgresql://127.0.0.1:'+values['ORBISOPS_PGVECTOR_PORT'].strip('"\'')+'/'+values['ORBISOPS_PGVECTOR_DATABASE'].strip('"\''),
               ORBISOPS_MEMORY_TEST_PG_USER=values['ORBISOPS_PGVECTOR_USERNAME'].strip('"\''),
               ORBISOPS_MEMORY_TEST_PG_PASSWORD=values['ORBISOPS_PGVECTOR_PASSWORD'].strip('"\''))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w') as stream:
        if migrate:
            # Same checksummed migration entrypoint as normal deployment. No backend or model starts.
            migration_env=dict(env, MYSQL_PWD=env['ORBISOPS_DISCOVERY_TEST_MYSQL_PASSWORD'])
            subprocess.run(['docker','compose','--project-name','orbisops-acceptance','--env-file',str(ROOT/'deploy/.env.acceptance'),
                '-f','compose.yml','-f','deploy/compose.acceptance.yml','run','--rm','--no-deps',
                '-e','MYSQL_DATABASE='+database,'-e','MYSQL_USERNAME=root','-e','MYSQL_PWD',
                '-v',str(ROOT/'server/db/migrations')+':/opt/orbisops/db/migrations:ro',
                '-v',str(ROOT/'server/scripts/db-migrate.sh')+':/opt/orbisops/scripts/db-migrate.sh:ro','migrate'],
                cwd=ROOT,env=migration_env,check=True,stdout=stream,stderr=subprocess.STDOUT)
        result = subprocess.run(['mvn', '-pl', 'orbisops-app', '-am', 'test', '-Dtest=' + tests,
                                 '-Dsurefire.failIfNoSpecifiedTests=false', '-DargLine=-Xmx512m -XX:ActiveProcessorCount=2'],
                                cwd=ROOT / 'server', env=env, stdout=stream, stderr=subprocess.STDOUT)
    print(json.dumps({'exitCode': result.returncode, 'database': database, 'log': str(output),
                      'credentialsPrinted': False, 'businessWritesOrModelCalls': False}))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--database',default=DATABASE)
    parser.add_argument('--tests',help='Explicit comma-separated Java test classes, with optional #method')
    parser.add_argument('--migrate',action='store_true',help='Apply normal checksummed migrations to the isolated schema first')
    args = parser.parse_args()
    main(args.output, args.java_home,args.database,args.tests,args.migrate)
