"""Repository structure and resolver regression tests; no external services required."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from project_layout import ROOT, check, module_path, modules, service_path


def pom(path, artifact, children=(), dependencies=(), boot=False, parent=None):
    path.mkdir(parents=True, exist_ok=True)
    body = '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
    if parent:
        body += '<parent><groupId>com.example</groupId><artifactId>' + parent + '</artifactId><version>1</version><relativePath>../pom.xml</relativePath></parent>'
    body += '<artifactId>' + artifact + '</artifactId>'
    body += '<modules>' + ''.join('<module>' + c + '</module>' for c in children) + '</modules>'
    body += '<dependencies>' + ''.join('<dependency><groupId>com.example</groupId><artifactId>' + d + '</artifactId></dependency>' for d in dependencies) + '</dependencies>'
    if boot:
        body += '<build><plugins><plugin><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>'
        (path / 'src/main/resources').mkdir(parents=True)
        (path / 'src/main/resources/application.yml').write_text('spring: {}', encoding='utf-8')
    (path / 'pom.xml').write_text(body + '</project>', encoding='utf-8')


class LayoutTest(unittest.TestCase):
    def test_repository_has_seven_deployments_not_one_per_maven_module(self):
        entries, runtime = check()
        self.assertEqual({'gateway', 'auth-service', 'note-service', 'upload-service', 'leaf-service', 'search-service', 'search-sync-service'}, runtime)
        self.assertIn('community-api', entries)
        self.assertFalse({'common', 'note-api', 'comment-api', 'interaction-api'} & entries.keys())

    def test_services_resolve_by_artifact_not_old_folder_name(self):
        self.assertEqual(ROOT / 'services/community/note-service', service_path('note-service'))
        self.assertEqual(ROOT / 'platform/id/leaf-service', service_path('leaf-service'))
        self.assertEqual(ROOT / 'services/search/search-sync-service', service_path('search-sync-service'))

    def test_library_is_not_a_deployable_service(self):
        with self.assertRaisesRegex(ValueError, 'Not a deployable service'):
            service_path('community-api')
        self.assertTrue((module_path('community-api') / 'pom.xml').is_file())

    def test_module_cannot_escape_repository(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t) / 'repo'
            pom(root, 'root', ['../outside'])
            with self.assertRaisesRegex(ValueError, 'outside repository'):
                modules(root)

    def test_duplicate_artifacts_are_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            pom(root, 'root', ['a', 'b'])
            pom(root / 'a', 'duplicate')
            pom(root / 'b', 'duplicate')
            with self.assertRaisesRegex(ValueError, 'Duplicate artifactId'):
                check(root)

    def test_service_must_depend_on_contract_not_another_service_jar(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            pom(root, 'root', ['a', 'b'])
            pom(root / 'a', 'a-service', dependencies=['b-service'], boot=True)
            pom(root / 'b', 'b-service', boot=True)
            with self.assertRaisesRegex(ValueError, 'depends on deployable service'):
                check(root)

    def test_parent_reference_must_match_local_parent(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            pom(root, 'root', ['a'])
            pom(root / 'a', 'a-api', parent='wrong-parent')
            with self.assertRaisesRegex(ValueError, 'parent artifact mismatch'):
                check(root)

    def test_gateway_cannot_pull_rpc_security_into_http_entrypoint(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            pom(root, 'root', ['gateway', 'rpc-security'])
            pom(root / 'gateway', 'gateway', dependencies=['rpc-security'], boot=True)
            pom(root / 'rpc-security', 'rpc-security')
            with self.assertRaisesRegex(ValueError, 'gateway: RPC'):
                check(root)

    def test_platform_cannot_depend_on_business_contracts(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            pom(root, 'root', ['platform/shared', 'services/community'])
            pom(root / 'platform/shared', 'shared-library', dependencies=['community-api'])
            pom(root / 'services/community', 'community-api')
            with self.assertRaisesRegex(ValueError, 'platform depends on business domain'):
                check(root)


if __name__ == '__main__':
    unittest.main()
