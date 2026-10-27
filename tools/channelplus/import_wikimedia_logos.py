#!/usr/bin/env python3
"""Bundle selected Commons SVGs unchanged, with per-file provenance and attribution.

Run from a checkout: python tools/channelplus/import_wikimedia_logos.py
Only the importer uses the network. The installed app reads bundled assets offline.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SELECTION = Path(__file__).with_name('wikimedia_logos.json')
ASSETS = ROOT / 'owntv_core_tdt/core/src/main/assets/channel-logos/wikimedia'
API = 'https://commons.wikimedia.org/w/api.php'
AGENT = 'ChannelplusLogoImporter/1.0 (https://github.com/Ayanawars/Channelplus)'


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                data = response.read(2 * 1024 * 1024 + 1)
            if len(data) > 2 * 1024 * 1024:
                raise ValueError('Oversized response')
            return data
        except urllib.error.HTTPError as error:
            if error.code not in (429, 502, 503, 504) or attempt == 3:
                raise
            time.sleep(min(15, int(error.headers.get('Retry-After', 2 ** attempt))))


def plain(value):
    return html.unescape(re.sub('<[^>]+>', '', value or '')).strip()


def image_info(titles):
    found = {}
    for offset in range(0, len(titles), 20):
        params = {'action': 'query', 'format': 'json', 'formatversion': 2, 'redirects': 1,
                  'titles': '|'.join(titles[offset:offset + 20]), 'prop': 'imageinfo',
                  'iiprop': 'url|sha1|timestamp|size|extmetadata', 'iiextmetadatalanguage': 'en'}
        result = json.loads(get(API + '?' + urllib.parse.urlencode(params)))
        redirects = {v['from']: v['to'] for v in result.get('query', {}).get('redirects', [])}
        pages = {p['title']: p for p in result.get('query', {}).get('pages', [])}
        for title in titles[offset:offset + 20]:
            target = title
            while target in redirects:
                target = redirects[target]
            page = pages.get(target, {})
            if 'imageinfo' not in page:
                raise ValueError('Missing Commons file: ' + title)
            found[title] = (page['title'], page['imageinfo'][0])
    return found


def validate_svg(data):
    root = ET.fromstring(data)
    if root.tag != '{http://www.w3.org/2000/svg}svg':
        raise ValueError('Not an SVG')
    for element in root.iter():
        if element.tag.rsplit('}', 1)[-1] in ('script', 'foreignObject'):
            raise ValueError('Active content in SVG')
        for key, value in element.attrib.items():
            if key.rsplit('}', 1)[-1] == 'href' and value and not value.startswith('#'):
                raise ValueError('SVG contains an external or embedded image resource: ' + value[:100])
    return root


def generate(offline=False, metadata_cache=None):
    selection = json.loads(SELECTION.read_text())
    titles = ['File:' + e['file'] for e in selection['entries']]
    ASSETS.mkdir(parents=True, exist_ok=True)
    cache = ASSETS / 'SOURCES.json'
    if offline:
        sources = json.loads(cache.read_text())
        metadata = {e['requested_file']: (e['file'], e['imageinfo']) for e in sources['entries']}
    else:
        if metadata_cache and metadata_cache.exists():
            metadata = json.loads(metadata_cache.read_text())
            if not set(titles).issubset(metadata):
                raise ValueError('Metadata cache belongs to a different selection; omit the cache to refresh it')
        else:
            metadata = image_info(titles)
            if metadata_cache:
                metadata_cache.write_text(json.dumps(metadata, ensure_ascii=False))
    available = {hashlib.sha1(p.read_bytes()).hexdigest(): p for p in ASSETS.glob('*.svg')}

    def acquire(chosen):
        requested = 'File:' + chosen['file']
        title, info = metadata[requested]
        parsed = urllib.parse.urlparse(info['url'])
        if parsed.scheme != 'https' or parsed.hostname != 'upload.wikimedia.org' or not parsed.path.lower().endswith('.svg'):
            raise ValueError('Unexpected Commons asset URL: ' + title)
        asset = available.get(info['sha1'])
        if asset is None and offline:
            raise ValueError('Missing offline asset: ' + title)
        data = asset.read_bytes() if asset else get(info['url'])
        if hashlib.sha1(data).hexdigest() != info['sha1']:
            raise ValueError('Commons file changed during import: ' + title)
        try:
            validate_svg(data)
        except ValueError as error:
            raise ValueError(title + ': ' + str(error)) from error
        filename = hashlib.sha256(data).hexdigest()[:24] + '.svg'
        (ASSETS / filename).write_bytes(data)
        return chosen, requested, title, info, data, filename

    rows, provenance, credits = [], [], []
    with ThreadPoolExecutor(max_workers=4) as pool:
        acquired = list(pool.map(acquire, selection['entries']))
    for chosen, requested, title, info, data, filename in acquired:
        for channel in chosen['channels']:
            if '\t' in channel or '\n' in channel:
                raise ValueError('Invalid channel name')
            rows.append(channel + '\t' + filename)
        ext = info.get('extmetadata', {})
        field = lambda name: plain(ext.get(name, {}).get('value', ''))
        license_name = field('LicenseShortName')
        if not license_name:
            raise ValueError('Missing license metadata: ' + title)
        attribution = {'artist': field('Artist'), 'credit': field('Credit'), 'license': license_name,
                       'license_url': field('LicenseUrl'), 'attribution': field('Attribution'),
                       'restrictions': field('Restrictions')}
        record = {'requested_file': requested, 'file': title, 'channels': chosen['channels'],
                  'asset': filename, 'sha256': hashlib.sha256(data).hexdigest(),
                  'description_url': info['descriptionurl'], 'imageinfo': info, **attribution}
        provenance.append(record)
        credits += [title, 'Channels: ' + ', '.join(chosen['channels']), info['descriptionurl'],
                    'Author: ' + attribution['artist'], 'Credit: ' + attribution['credit'],
                    'License: ' + license_name + (' — ' + attribution['license_url'] if attribution['license_url'] else ''),
                    'Changes: none; original SVG bytes bundled unchanged.', '']
    sources = {'source_category': selection['category'], 'source_url': 'https://commons.wikimedia.org/wiki/' + selection['category'],
               'changes': 'SVG files bundled unchanged; channel-name matching index added.', 'entries': provenance}
    (ASSETS / 'catalog.tsv').write_text('\n'.join(rows) + '\n')
    cache.write_text(json.dumps(sources, ensure_ascii=False, indent=2) + '\n')
    (ASSETS / 'ATTRIBUTION.txt').write_text('Wikimedia Commons channel logos\nPer-file attribution and licenses\n\n' + '\n'.join(credits))
    print(f'Bundled {len(provenance)} SVGs with {len(rows)} channel names/aliases.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline', action='store_true', help='Validate and rebuild the index from bundled source records')
    parser.add_argument('--metadata-cache', type=Path, help='Optional explicit metadata cache for retrying an import')
    args = parser.parse_args()
    generate(args.offline, args.metadata_cache)
