"""Run imported-channel checks in fresh, disposable Forge 1.12 installations."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
for name in ('java', 'runtime', 'candidate', 'baseline', 'probe', 'output'):
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args()
assert not args.output.exists(), 'Keep earlier evidence; choose a new output directory'
args.output.mkdir(parents=True)
forge = 'forge-1.12.2-14.23.5.2859-universal.jar'
profile_path = Path('world/serverconfig/orespawn-worldgen.json')
ore_id = 'orespawn:legacy/iron_ore'
deposit_id = 'oresourcequalification:deposit'
group_id = 'orespawn:iron|minecraft:overworld'
flags = subprocess.CREATE_NO_WINDOW if hasattr(subprocess, 'CREATE_NO_WINDOW') else 0


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def prepare(name, jar, source=None):
    directory = args.output / name
    assert not directory.exists(), directory
    directory.mkdir()
    for item in (forge, 'minecraft_server.1.12.2.jar', 'libraries', 'eula.txt'):
        old, new = args.runtime / item, directory / item
        shutil.copytree(old, new) if old.is_dir() else shutil.copy2(old, new)
    (directory / 'mods').mkdir()
    shutil.copy2(jar, directory / 'mods' / jar.name)
    shutil.copy2(args.probe, directory / 'mods' / args.probe.name)
    if source:
        for item in ('world', 'config'):
            shutil.copytree(source / item, directory / item)
    (directory / 'server.properties').write_text(
        'level-name=world\nlevel-seed=zsjpxah\nlevel-type=FLAT\n'
        'generator-settings=3;minecraft:bedrock,223*minecraft:stone;1;decoration\n'
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
        'generate-structures=false\nspawn-animals=false\nspawn-monsters=false\n'
        'view-distance=2\nmax-tick-time=-1\n', encoding='utf-8')
    for item in ('banned-players.json', 'banned-ips.json', 'ops.json', 'whitelist.json'):
        (directory / item).write_text('[]\n', encoding='utf-8')
    return directory


def run(directory, phase, base=64, old_base=64, reload=False, zero=False, managed=True,
        bootstrap=False, expect_failure=False):
    properties = {'phase': phase, 'base': base, 'oldBase': old_base, 'reload': str(reload).lower(),
                  'manage': str(managed).lower(), 'expect': 'zero' if zero else 'positive',
                  'bootstrap': str(bootstrap).lower()}
    command = [str(args.java), '-Xms256m', '-Xmx2g']
    command += ['-Doresourceprobe.' + key + '=' + str(value) for key, value in properties.items()]
    command += ['-jar', forge, 'nogui']
    write(directory / (phase + '-command.json'), command)
    with (directory / (phase + '-console.log')).open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=directory, stdout=log, stderr=subprocess.STDOUT,
                                creationflags=flags, timeout=240, check=False)
    shutil.copytree(directory / 'logs', directory / (phase + '-logs'))
    assert result.returncode == 0, (directory, phase, result.returncode)
    report = json.loads((directory / (phase + '-result.json')).read_text(encoding='utf-8'))
    assert not list(directory.glob('crash-reports/*')), directory
    if expect_failure:
        assert not report['passed'] and 'Replacement left ordinary iron behind' in report['failure'], report
    else:
        assert report['passed'], (directory, phase, report.get('failure'), report.get('terrain'))
    print(('EXPECTED_RED' if expect_failure else 'PASS'), directory.name, phase, flush=True)
    return report


bootstrap = prepare('bootstrap', args.candidate)
run(bootstrap, 'bootstrap', bootstrap=True)
factory = json.loads((bootstrap / profile_path).read_text(encoding='utf-8'))


def profile(scale, shape='string', custom=None, saved=False, managed=True):
    root = deepcopy(factory)
    root['manage_vanilla_ores'] = managed
    root['place_fluid_deposits'] = False
    root['ore_source_policies'] = {}
    for rule in root.get('ores', {}).values():
        rule['enabled'] = False
    placement = {'enabled': True, 'frequency': 8.0, 'quantity': 8, 'min_y': 12, 'max_y': 18,
                 'host_blocks': ['minecraft:stone'], 'pattern': 'orespawn:default', 'retrogen': False}
    if shape == 'object':
        placement['pattern'] = {'type': 'orespawn:default', 'settings': {}}
    if custom:
        placement['placement_channel'] = custom
    ordinary = {'block': 'minecraft:iron_ore', 'material': 'orespawn:iron', 'enabled': True,
                'source_mod': 'minecraft', 'retrogen': False, 'dimensions': {'minecraft:overworld': placement},
                'dimension_selectors': {}}
    if shape == 'selector':
        ordinary['dimensions'] = {}
        ordinary['dimension_selectors'] = {'orespawn:all_except_nether_end': placement}
    root['ores'][ore_id] = ordinary
    root['ores'][deposit_id] = {'block': 'minecraft:iron_ore', 'material': 'orespawn:iron',
        'enabled': True, 'source_mod': 'oresourcequalification', 'retrogen': False,
        'dimension_selectors': {},
        'dimensions': {'minecraft:overworld': {'enabled': True, 'frequency': 1.0, 'quantity': 16,
            'min_y': 196, 'max_y': 210,
            # Neighbouring vanilla decoration can turn these high stone cells into gravel or dirt.
            'host_blocks': ['minecraft:stone', 'minecraft:gravel', 'minecraft:dirt'],
            'pattern': {'type': deposit_id, 'settings': {}}, 'background_generation_scale': scale}}}
    if saved:
        root['ore_source_policies'][group_id] = {'material': 'orespawn:iron', 'domain': 'minecraft:overworld',
            'mode': 'consolidated', 'output_mode': 'balanced', 'review_required': False,
            'outputs': {ore_id: 1.0, deposit_id: 1.0},
            'placement_sources': {'orespawn:default': ore_id, deposit_id: deposit_id}}
    return root


cases = [('additive', 1.0, 'string', None, False, True),
         ('hybrid', 0.25, 'string', None, False, True),
         ('replacement', 0.0, 'string', None, False, True),
         ('custom-channel', 0.0, 'string', 'example:custom', False, True),
         ('explicit-old-channel', 0.0, 'string', 'orespawn:default', False, True),
         ('object-replacement', 0.0, 'object', None, False, True),
         ('selector-replacement', 0.0, 'selector', None, False, True),
         ('saved-additive', 1.0, 'string', None, True, True),
         ('saved-replacement', 0.0, 'string', None, True, True),
         ('management-off', 1.0, 'string', None, False, False)]


def check_rules(expected, actual):
    for key in (ore_id, deposit_id):
        assert actual['ores'][key] == expected['ores'][key], (key, 'saved rule changed')
    selected = expected['ore_source_policies'].get(group_id, {}).get('placement_sources')
    if selected:
        assert actual['ore_source_policies'][group_id]['placement_sources'] == selected


def check_case(case):
    name, scale, shape, custom, saved, managed = case
    root = profile(scale, shape, custom, saved, managed)
    directory = prepare(name, args.candidate)
    write(directory / profile_path, root)
    base = -32 if shape == 'selector' else 64
    zero = scale == 0.0 and custom is None
    first = run(directory, 'fresh', base, base, zero=zero, managed=managed)
    check_rules(root, first['profile'])
    reloaded = run(directory, 'reload', base, base, True, zero, managed)
    assert first['terrain'] == reloaded['terrain'], name
    later = run(directory, 'later', base + 16, base, zero=zero, managed=managed)
    assert first['terrain'] == later['old_terrain'], name
    stable = run(directory, 'stable', base + 16, base, True, zero, managed)
    assert stable['terrain'] == later['terrain'] and stable['old_terrain'] == first['terrain'], name
    check_rules(root, stable['profile'])
    return name, {'first': first, 'later': later, 'passed': True}


with ThreadPoolExecutor(max_workers=2) as pool:
    results = dict(pool.map(check_case, cases))
assert 0 < results['hybrid']['first']['terrain']['ordinary'] < results['additive']['first']['terrain']['ordinary']
for name in ('hybrid', 'replacement', 'custom-channel', 'explicit-old-channel', 'object-replacement',
             'saved-additive', 'saved-replacement', 'management-off'):
    assert results[name]['first']['terrain']['deposit_sha256'] == results['additive']['first']['terrain']['deposit_sha256']

# Keep a failing published-release control and a copied-world upgrade beside the green runs.
red = prepare('published-replacement-red', args.baseline)
write(red / profile_path, profile(0.0))
run(red, 'red', zero=True, expect_failure=True)
old = prepare('published-additive', args.baseline)
old_profile = profile(1.0)
write(old / profile_path, old_profile)
old_result = run(old, 'fresh')
assert old_result['terrain'] == results['additive']['first']['terrain'], 'Untouched additive generation changed'
upgrade = prepare('published-upgrade', args.candidate, old)
upgraded = run(upgrade, 'upgrade', 80, 64)
assert upgraded['old_terrain'] == old_result['terrain']
assert upgraded['terrain'] == results['additive']['later']['terrain']
check_rules(old_profile, upgraded['profile'])
reloaded = run(upgrade, 'reload', 80, 64, True)
assert reloaded['old_terrain'] == old_result['terrain'] and reloaded['terrain'] == upgraded['terrain']
write(args.output / 'results.json', {'passed': True, 'cases': results, 'upgrade': upgraded,
    'candidate_sha256': hashlib.sha256(args.candidate.read_bytes()).hexdigest()})
print('ORE_SOURCE_MATRIX_PASS cases=10 boots=45 upgrade=true', flush=True)
