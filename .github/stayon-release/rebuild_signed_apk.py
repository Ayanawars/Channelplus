#!/usr/bin/env python3
"""Reconstruct one verified offline-signed STAYON+ APK from CI output.

Only public ZIP local headers, central directory and APK v2/v3 signatures
are serialized. The signing keystore, password and private key never enter GitHub.
"""
import base64
import hashlib
import lzma
import struct
import sys
import zipfile
from pathlib import Path

EXPECTED_UNSIGNED = "e1081082d5ceb61aaba54723a6b3c4d5fb51b4710ee5e01f3c34dd61bbba3f70"
EXPECTED_SIGNED = "580bb9aca689daab16f44d67ab616b831d17249190c867501e2a14fd962f5faf"

def payload_start(src, info):
    src.seek(info.header_offset)
    if src.read(4) != bytes((80, 75, 3, 4)):
        raise ValueError("Bad ZIP local entry")
    src.seek(info.header_offset + 26)
    namelen, extralen = struct.unpack("<HH", src.read(4))
    return info.header_offset + 30 + namelen + extralen

def read_chunk(data, pos, size):
    out = data[pos:pos + size]
    if len(out) != size:
        raise ValueError("Truncated patch")
    return out, pos + size

def main():
    unsigned_path, recipe_path, output_path = map(Path, sys.argv[1:4])
    patch = lzma.decompress(base64.b64decode(recipe_path.read_text().strip(), validate=True))
    if patch[:8] != b"STAYON49":
        raise ValueError("Wrong reconstruction format")
    unsigned_sha, signed_sha = patch[8:40], patch[40:72]
    expected_size, count = struct.unpack_from("<QI", patch, 72)
    if unsigned_sha.hex() != EXPECTED_UNSIGNED or signed_sha.hex() != EXPECTED_SIGNED:
        raise ValueError("Unexpected release digests")
    if hashlib.sha256(unsigned_path.read_bytes()).digest() != unsigned_sha:
        raise ValueError("Unsigned build differs from reviewed artifact")
    with zipfile.ZipFile(unsigned_path) as archive:
        entries = sorted(archive.infolist(), key=lambda info: info.header_offset)
        if len(entries) != count:
            raise ValueError("ZIP entry count mismatch")
        with unsigned_path.open("rb") as src, output_path.open("wb") as dst:
            pos = 84
            for entry in entries:
                (header_size,) = struct.unpack_from("<H", patch, pos)
                pos += 2
                header, pos = read_chunk(patch, pos, header_size)
                (trailer_size,) = struct.unpack_from("<H", patch, pos)
                pos += 2
                trailer, pos = read_chunk(patch, pos, trailer_size)
                dst.write(header)
                src.seek(payload_start(src, entry))
                remaining = entry.compress_size
                while remaining:
                    part = src.read(min(1024 * 1024, remaining))
                    if not part:
                        raise ValueError("Truncated unsigned ZIP entry")
                    dst.write(part)
                    remaining -= len(part)
                dst.write(trailer)
            (tail_size,) = struct.unpack_from("<I", patch, pos)
            pos += 4
            tail, pos = read_chunk(patch, pos, tail_size)
            if pos != len(patch):
                raise ValueError("Trailing patch bytes")
            dst.write(tail)
    size = output_path.stat().st_size
    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    if size != expected_size or digest != EXPECTED_SIGNED:
        output_path.unlink(missing_ok=True)
        raise ValueError("Offline signed APK reconstruction SHA mismatch")
    print(f"Validated signed APK: size={size} sha256={digest}")

if __name__ == "__main__":
    main()
