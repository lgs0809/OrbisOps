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
import socket
import subprocess
import tempfile

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


def main(output):
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
        command = ['mvn', '-f', 'server/pom.xml', '-Dtest=OpsProdLikeJavaLandingAcceptanceTest',
            '-Dsurefire.failIfNoSpecifiedTests=false', '-Dorbisops.prodlike.landing.acceptance=true',
            '-Dorbisops.prodlike.repository.root=' + str(root),
            '-Dorbisops.prodlike.candidate.artifact=' + str(artifacts / 'candidate.jar'),
            '-Dorbisops.prodlike.baseline.artifact=' + str(artifacts / 'baseline.jar'),
            '-Dorbisops.prodlike.candidate.sha256=' + hashes['candidate'], 'test']
        try:
            with output.with_suffix('.log').open('x') as log:
                process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=600)
            result['exitCode'] = process.returncode
            result['status'] = 'PASS' if process.returncode == 0 else 'FAIL'
        finally:
            output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    main(parser.parse_args().output)
