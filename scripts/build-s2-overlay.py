"""Build a separate CurseForge pack from the extracted S2 1.4; never edit input."""
import argparse
import json
import re
import shutil
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def build(source, output):
    manifest = json.loads((source / 'manifest.json').read_text())
    if manifest['minecraft']['version'] != '1.21.1':
        raise ValueError('Only S2 Minecraft 1.21.1 is supported')
    if not any(f['projectID'] == 1321557 for f in manifest['files']):
        raise ValueError('Expected EconomyCraft in original S2 source')
    additions = json.loads((ROOT / 'modpack/s2/additions.lock.json').read_text())
    if {m['projectID'] for m in additions} != {398521, 60028}:
        raise ValueError('Farmer\'s Delight and Aquaculture must be pinned')
    with tempfile.TemporaryDirectory() as temporary:
        stage = Path(temporary)
        shutil.copytree(source / 'overrides', stage / 'overrides')
        overrides = stage / 'overrides'
        scripts = overrides / 'kubejs/server_scripts'
        shutil.rmtree(overrides / 'config/economycraft', ignore_errors=True)
        (scripts / 'ecoGive.js').unlink(missing_ok=True)
        policies = json.loads((ROOT / 'config/s2-rewards.json').read_text())
        custom = (
            '// S2 rewards: amount and cooldown are controlled by the backend.\n'
            '// nfsreward persists a request before this handler returns.\n'
            'const nfsRewards = ' + json.dumps(list(policies)) + ';\n'
            'nfsRewards.forEach(id => {\n'
            '  FTBQuestsEvents.customReward(id, event => {\n'
            '    const name = event.player.name.string;\n'
            '    const accepted = event.server.runCommandSilent(`nfsreward ${name} ${id}`);\n'
            '    if (accepted !== 1) throw new Error("Quest reward was not queued");\n'
            '  });\n'
            '});\n'
        )
        (scripts / 'CustomRewards.js').write_text(custom)
        # Existing quest commands still use rankGive; restrict it rather than breaking them.
        ranks = scripts / 'ranks.js'
        text = ranks.read_text()
        old = "Commands.literal('rankGive').then("
        if old not in text:
            raise ValueError('Unexpected ranks.js; review before patching')
        ranks.write_text(text.replace(old, "Commands.literal('rankGive').requires(source => source.hasPermission(2)).then("))
        chapter = overrides / 'config/ftbquests/quests/chapters/minecolonies.snbt'
        text = chapter.read_text()
        # The command reward is converted to the custom event with the same FTB ID.
        pattern = r'command: "/eco addmoney @p 100"(.*?id: "1A368A09AED26F1D".*?)type: "command"'
        text, count = re.subn(pattern, lambda m: m.group(1) + 'type: "custom"', text, flags=re.S)
        if count != 1:
            raise ValueError('Unexpected starting quest reward')
        chapter.write_text(text)
        # Repeatable random loot cannot use a one-time quest key. Preserve the loot
        # entry/weight, replace its EconomyCraft money with one vanilla emerald.
        loot = overrides / 'config/ftbquests/quests/reward_tables/68B11D6C597ECFA2.snbt'
        text = loot.read_text().replace('command: "/balGive @s 100"', 'item: { id: "minecraft:emerald", count: 1 }')
        text = text.replace('type: "command"', 'type: "item"')
        loot.write_text(text)
        for file in overrides.rglob('*'):
            if file.is_file() and file.suffix in {'.js', '.snbt'}:
                if re.search(r'(?:command:\s*"/|runCommandSilent\([^\n]*)(?:eco\s|balGive\s)', file.read_text(errors='replace')):
                    raise ValueError('Unconverted economy command: ' + str(file.relative_to(overrides)))
        ids = {m['projectID'] for m in additions}
        manifest['files'] = [f for f in manifest['files'] if f['projectID'] not in ids | {1321557}]
        manifest['files'] += [{k: m[k] for k in ('projectID', 'fileID', 'required')} for m in additions]
        manifest['name'] = 'Lime Colony Economy'
        manifest['version'] = 's2-1.4-integration-0.1.0'
        # Preserve the original pack's Java/loader requirements; our mod targets Java 21.
        (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(stage.rglob('*')):
                if file.is_file():
                    archive.write(file, file.relative_to(stage))
    return {'removed': 'EconomyCraft', 'added': len(additions), 'rewardPolicies': len(policies)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path, help='Extracted original S2 1.4 folder')
    parser.add_argument('output', type=Path, help='New CurseForge import ZIP')
    args = parser.parse_args()
    print(json.dumps(build(args.source, args.output)))
