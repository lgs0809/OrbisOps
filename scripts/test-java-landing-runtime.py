#!/usr/bin/env python3
"""Exercise the actual Java process deployment/rollback adapter with isolated tiny Java artifacts.

This is a process/filesystem component test, not a business release or approval test.
Uses loopback 8092 only when free; never starts or changes mall dependencies/data.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = '''
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
public class LandingSmokeService {
    public static void main(String[] args) throws Exception {
        String release = new String(LandingSmokeService.class.getResourceAsStream("/release-id.txt").readAllBytes(), StandardCharsets.UTF_8).trim();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8092), 0);
        server.createContext("/actuator/health", exchange -> {
            byte[] body = ("{\\"status\\":\\"UP\\",\\"release\\":\\"" + release + "\\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }
}
'''


def verify_ci_coverage():
    expected = {
        'GovernedMemoryTransactionMySqlTest': (6, None),
        'ConversationMemoryMySqlTest': (7, None),
        'WorkflowMySqlTest': (28, None),
        'WorkflowToolBudgetMySqlTest': (3, None),
        'SkillEvolutionMySqlTest': (1, {'realFiveMinuteLeaseExpiryRecoversExactlyOnceAndFencesLateWorker'}),
        'CodeWorkspaceMcpServerIntegrationTest': (3, {
            'realJdtlsSupportsReadOnlySemanticsAndRefreshesDiagnosticsAfterRepairEdit',
            'realManagedPyrightSupportsReadOnlySemanticsAndRefreshesDiagnosticsAfterRepairEdit',
            'realManagedJavaAndPythonProvidersCanCoexistInOneCodeMcp',
        }),
        'WorkflowChangeVerificationRealEvidenceTest': (1, None),
        'OpsProdLikeJavaLandingAcceptanceTest': (1, None),
    }
    report_root = ROOT / 'server/orbisops-app/target/surefire-reports'
    counts = {}
    for suite, (count, names) in expected.items():
        reports = list(report_root.glob(f'TEST-*.{suite}.xml'))
        if len(reports) != 1:
            raise RuntimeError(f'Missing or ambiguous Surefire report for {suite}')
        cases = ET.parse(reports[0]).getroot().findall('testcase')
        if names:
            cases = [case for case in cases if case.attrib['name'].split('(')[0] in names]
            if {case.attrib['name'].split('(')[0] for case in cases} != names:
                raise RuntimeError(f'Missing required cases in {suite}')
        if len(cases) < count or any(case.find(status) is not None
                                     for case in cases for status in ('skipped', 'failure', 'error')):
            raise RuntimeError(f'Expected at least {count} successful integration executions in {suite}')
        counts[suite] = len(cases)
    skipped = [(case.get('classname'), case.get('name'))
               for report in (ROOT / 'server').glob('*/target/surefire-reports/TEST-*.xml')
               for case in ET.parse(report).getroot().findall('testcase')
               if case.find('skipped') is not None]
    if ('cn.lgs.orbisops.trigger.ops.runtime.OpsOpenAiCompatibilityRealSmokeTest',
        'localRelayUsesTheSameSpringAiUrlCompositionWithoutHiddenRetry') not in skipped:
        raise RuntimeError('The real model probe must remain opt-in in Server CI')
    return {'integrationExecutions': sum(counts.values()), 'suites': counts, 'skipped': skipped}


def main(output, full_suite):
    if output.exists() or output.with_suffix('.log').exists():
        raise ValueError('Use a new evidence filename')
    with socket.socket() as check:
        check.bind(('127.0.0.1', 8092))
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    result = {'status': 'RUNNING', 'scope': 'REAL_JAVA_PROCESSES_SYNTHETIC_JARS',
              'businessApprovalAndMcpLanding': 'NOT_TESTED_BY_THIS_COMPONENT_TEST'}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='orbisops-java-landing-') as directory:
        root = Path(directory)
        source = root / 'LandingSmokeService.java'
        source.write_text(SOURCE)
        classes = root / 'classes'
        classes.mkdir()
        subprocess.run([str(java / 'javac'), '-d', str(classes), str(source)], check=True)
        artifacts = root / '.ops-repair/artifacts'
        artifacts.mkdir(parents=True)
        hashes = {}
        for release in ('baseline', 'candidate'):
            (classes / 'release-id.txt').write_text(release)
            artifact = artifacts / (release + '.jar')
            subprocess.run([str(java / 'jar'), '--create', '--file', str(artifact),
                '--main-class', 'LandingSmokeService', '-C', str(classes), '.'], check=True)
            hashes[release] = hashlib.sha256(artifact.read_bytes()).hexdigest()
        result['artifactHashes'] = hashes
        command = ['mvn', '-B', '-f', 'server/pom.xml', '-Dorbisops.prodlike.landing.acceptance=true',
            '-Dorbisops.prodlike.repository.root=' + str(root),
            '-Dorbisops.prodlike.candidate.artifact=' + str(artifacts / 'candidate.jar'),
            '-Dorbisops.prodlike.baseline.artifact=' + str(artifacts / 'baseline.jar'),
            '-Dorbisops.prodlike.candidate.sha256=' + hashes['candidate']]
        if full_suite:
            evidence = root / 'recorded-workflow-change.json'
            shutil.copyfile(ROOT / 'server/orbisops-app/src/test/resources/workflow/change-verification-recorded.json',
                            evidence)
            command.extend(['-Dorbisops.acceptance.realLeaseWait=true', f'-Dops04.change.evidence={evidence}'])
        else:
            command.extend(['-Dtest=OpsProdLikeJavaLandingAcceptanceTest', '-Dsurefire.failIfNoSpecifiedTests=false'])
        command.append('test')
        result['status'] = 'FAIL'
        try:
            if full_suite:
                process = subprocess.run(command, cwd=ROOT, timeout=2400)
            else:
                with output.with_suffix('.log').open('x') as log:
                    process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=600)
            result['exitCode'] = process.returncode
            if process.returncode == 0:
                if full_suite:
                    result['coverage'] = verify_ci_coverage()
                result['status'] = 'PASS'
        finally:
            output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--full-suite', action='store_true', help='Run the server suite with the CI integration fixtures')
    args = parser.parse_args()
    main(args.output, args.full_suite)
