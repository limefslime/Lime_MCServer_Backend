import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('overlay', ROOT / 'scripts/build-s2-overlay.py')
overlay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(overlay)


class OverlayTests(unittest.TestCase):
    def fixture(self, root):
        files = {
            'manifest.json': json.dumps({'minecraft': {'version': '1.21.1'}, 'files': [
                {'projectID': 1321557, 'fileID': 1}, {'projectID': 1, 'fileID': 2}]}),
            'overrides/config/economycraft/data/balances.json': '{}',
            'overrides/kubejs/server_scripts/ecoGive.js': 'old economy command',
            'overrides/kubejs/server_scripts/ranks.js': "Commands.literal('rankGive').then(x)",
            'overrides/config/ftbquests/quests/chapters/minecolonies.snbt':
                '{command: "/eco addmoney @p 100", id: "1A368A09AED26F1D", type: "command"}',
            'overrides/config/ftbquests/quests/reward_tables/68B11D6C597ECFA2.snbt':
                '{command: "/balGive @s 100", type: "command", weight: 10}',
        }
        for name, text in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        return files

    def test_removes_economy_adds_pinned_mods_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / 'source'
            originals = self.fixture(root)
            output = Path(temporary) / 'pack.zip'
            overlay.build(root, output)
            for name, original in originals.items():
                self.assertEqual((root / name).read_text(), original)
            with zipfile.ZipFile(output) as archive:
                manifest = json.loads(archive.read('manifest.json'))
                ids = {f['projectID'] for f in manifest['files']}
                self.assertNotIn(1321557, ids)
                self.assertTrue({398521, 60028}.issubset(ids))
                self.assertNotIn(328085, ids)
                self.assertFalse(any('config/economycraft/' in name for name in archive.namelist()))
                custom = archive.read('overrides/kubejs/server_scripts/CustomRewards.js').decode()
                self.assertIn('nfsreward', custom)
                self.assertNotIn('eco addmoney', custom)
                self.assertIn('hasPermission(2)', archive.read('overrides/kubejs/server_scripts/ranks.js').decode())
                chapter = archive.read('overrides/config/ftbquests/quests/chapters/minecolonies.snbt').decode()
                self.assertIn('type: "custom"', chapter)

    def test_unknown_economy_dependency_stops_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / 'source'
            self.fixture(root)
            (root / 'overrides/kubejs/server_scripts/new.js').write_text('server.runCommandSilent(`eco addmoney name 999`);')
            with self.assertRaisesRegex(ValueError, 'Unconverted'):
                overlay.build(root, Path(temporary) / 'pack.zip')


if __name__ == '__main__':
    unittest.main()
