#!/usr/bin/env python3
"""Reconstruct the already locally signed StayON+ 5.4.1 APK from its CI output.

The patch contains only public signed-APK ZIP headers, signing records, central
directory and the three standard META-INF signature files. It does not contain
the signing keystore, private key, passwords or any reusable signing capability.
The output is SHA-256 checked against the independently validated signed APK.
"""
import base64
import hashlib
import json
import struct
import sys
import zipfile
import zlib
from pathlib import Path

EXPECTED_UNSIGNED_SHA256 = "ae28f3578c6dfd29a2ab146bdcd307c2e9b822c6164e425396f8721bc3b992b1"
EXPECTED_SIGNED_SHA256 = "022c407b01bcc061d2edc5917511873c1ec5e12312b36d284b2fbcdc6b1b2e79"

def decode(text):
    return base64.b64decode(text, validate=True)

def payload_start(input_file, info):
    input_file.seek(info.header_offset)
    if input_file.read(4) != b"PK\\x03\\x04":
        raise ValueError("Unexpected ZIP local header")
    input_file.seek(info.header_offset + 26)
    namelen, extralen = struct.unpack("<HH", input_file.read(4))
    return info.header_offset + 30 + namelen + extralen

def main():
    unsigned_path, recipe_path, output_path = map(Path, sys.argv[1:4])
    actual_unsigned_sha = hashlib.sha256(unsigned_path.read_bytes()).hexdigest()
    if actual_unsigned_sha != EXPECTED_UNSIGNED_SHA256:
        raise ValueError(f"Wrong unsigned build: {actual_unsigned_sha}")
    recipe_bytes = decode(recipe_path.read_text().strip())
    recipe = json.loads(zlib.decompress(recipe_bytes))
    if recipe.get("format") != "stayon-release-zip-reconstruction-v1":
        raise ValueError("Unexpected recipe version")
    with zipfile.ZipFile(unsigned_path) as archive:
        entries = {entry.filename: entry for entry in archive.infolist()}
        with unsigned_path.open("rb") as src, output_path.open("wb") as dst:
            for item in recipe["entries"]:
                dst.write(decode(item["h"]))
                if item.get("ref"):
                    old = entries[item["n"]]
                    src.seek(payload_start(src, old))
                    remaining = old.compress_size
                    while remaining:
                        chunk = src.read(min(remaining, 1024 * 1024))
                        if not chunk:
                            raise ValueError("Truncated unsigned input")
                        dst.write(chunk)
                        remaining -= len(chunk)
                else:
                    dst.write(decode(item["d"]))
                dst.write(decode(item["t"]))
            dst.write(decode(recipe["tail"]))
    size = output_path.stat().st_size
    sha = hashlib.sha256(output_path.read_bytes()).hexdigest()
    if sha != EXPECTED_SIGNED_SHA256 or sha != recipe["sha256"] or size != recipe["size"]:
        output_path.unlink(missing_ok=True)
        raise ValueError(f"Signed APK does not match verified target (sha={sha}, size={size})")
    print(f"Verified signed APK: SHA256 {sha}, {size} bytes")

if __name__ == "__main__":
    main()
