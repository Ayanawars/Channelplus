"""Execute the editor's actual SQL against SQLite and check resources without Android tooling."""
import json
import re
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DAO = ROOT / 'owntv_core_tdt/core/src/main/java/tv/own/owntv/core/database/dao/ChannelDao.kt'
source = DAO.read_text()
queries = {}
for match in re.finditer(r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)\s*(?:suspend\s+)?fun\s+(\w+)', source):
    queries[match[2]] = ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', match[1]))
assert {'snapshotAllManual', 'pagingAllManual'} <= queries.keys()
con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE channels(id INTEGER PRIMARY KEY, sourceId INTEGER, sortOrder INTEGER, name TEXT);
CREATE TABLE content_order(itemId INTEGER, profileId INTEGER, mediaType TEXT, contextKey TEXT, position INTEGER);
INSERT INTO channels VALUES(1,1,0,'A'),(2,1,1,'B'),(3,2,0,'C'),(4,2,1,'D'),(5,3,0,'Other profile');
INSERT INTO content_order VALUES(3,7,'LIVE','__all_channels__',0),(1,7,'LIVE','__all_channels__',1),
 (2,7,'MOVIE','__all_channels__',0),(4,8,'LIVE','__all_channels__',0),(4,7,'LIVE','other-folder',0);
''')
for name in ['snapshotAllManual', 'pagingAllManual']:
    sql = queries[name].replace(':sourceIds', ':s1, :s2')
    def ids(profile=7, s1=1, s2=2, limit=100):
        return [r[0] for r in con.execute(sql, dict(profileId=profile, contextKey='__all_channels__', s1=s1, s2=s2, limit=limit))]
    assert ids() == [3, 1, 2, 4], (name, ids())
    assert ids(profile=8) == [4, 1, 2, 3]
    assert ids(s1=1,s2=1) == [1, 2]
    if name == 'snapshotAllManual':
        assert ids(limit=2) == [3, 1]
        assert len(ids(limit=4)) == 4
    assert 5 not in ids()
print('Actual manual-order SQL: order, source/profile/media/context isolation and limit checks passed.')
resources = ROOT / 'owntv_tdt/app/src/main/res'
values = {}
for locale in ['values','values-es']:
    nodes = ET.parse(resources / locale / 'strings_channel_editor.xml').getroot()
    values[locale] = {n.attrib['name']: n.text or '' for n in nodes}
assert values['values'].keys() == values['values-es'].keys()
for key, english in values['values'].items():
    assert sorted(re.findall(r'%\d+\$[ds]', english)) == sorted(re.findall(r'%\d+\$[ds]', values['values-es'][key])), key
all_keys = {n.attrib.get('name') for folder in [ROOT/'owntv_tdt/app/src/main/res/values', ROOT/'owntv_core_tdt/core/src/main/res/values'] for file in folder.glob('*.xml') for n in ET.parse(file).getroot()}
for path in (ROOT/'owntv_tdt/app/src/main/java/tv/own/owntv/features/customize').glob('ChannelEditor*.kt'):
    missing = set(re.findall(r'R\.string\.(\w+)',path.read_text())) - all_keys
    assert not missing, (path, missing)
print('Editor ES/EN XML, format placeholders and string references passed.')
