#!/usr/bin/env python3
"""Rebuild the independently signed STAYON+ 5.4.9 using only public APK ZIP records.

No keystore, passwords or private signing material is contained in this repository.
The reconstructed APK must be byte-for-byte identical to the verified offline build.
"""
import base64
import hashlib
import lzma
import struct
import sys
import zipfile
from pathlib import Path

UNSIGNED_SHA256 = "e1081082d5ceb61aaba54723a6b3c4d5fb51b4710ee5e01f3c34dd61bbba3f70"
SIGNED_SHA256 = "580bb9aca689daab16f44d67ab616b831d17249190c867501e2a14fd962f5faf"

def payload_start(source, info):
    source.seek(info.header_offset + 26)
    name_length, extra_length = struct.unpack("<HH", source.read(4))
    return info.header_offset + 30 + name_length + extra_length

def central_entries(data):
    eocd = data.rfind(bytes((80, 75, 5, 6)))
    if eocd < 0:
        raise ValueError("Missing ZIP central directory")
    central = struct.unpack_from("<I", data, eocd + 16)[0]
    entries = []
    pos = central
    while pos < eocd:
        if data[pos:pos+4] != bytes((80, 75, 1, 2)):
            raise ValueError("Unexpected central directory entry")
        name, extra, comment = struct.unpack_from("<HHH", data, pos + 28)
        end = pos + 46 + name + extra + comment
        entries.append(data[pos:end])
        pos = end
    if pos != eocd:
        raise ValueError("Corrupt central directory")
    return entries, eocd

def apply_patch(original, patch, position):
    prefix, suffix, middle_length = struct.unpack_from("<HHI", patch, position)
    position += 8
    if prefix + suffix > len(original) or middle_length > len(patch) - position:
        raise ValueError("Malformed public ZIP patch")
    middle = patch[position:position+middle_length]
    position += middle_length
    result = original[:prefix] + middle
    if suffix:
        result += original[len(original)-suffix:]
    return result, position

def main():
    unsigned_path, recipe_path, output_path = map(Path, sys.argv[1:4])
    unsigned = unsigned_path.read_bytes()
    if hashlib.sha256(unsigned).hexdigest() != UNSIGNED_SHA256:
        raise ValueError("Unsigned build does not match the verified CI artifact")
    patch = lzma.decompress(base64.b64decode(recipe_path.read_text().strip(), validate=True))
    if patch[:8] != b"ST49mini":
        raise ValueError("Unexpected public reconstruction format")
    unsigned_sha = patch[8:40].hex()
    signed_sha = patch[40:72].hex()
    count, expected_size = struct.unpack_from("<II", patch, 72)
    if unsigned_sha != UNSIGNED_SHA256 or signed_sha != SIGNED_SHA256:
        raise ValueError("Unexpected reconstruction checksums")
    central, eocd = central_entries(unsigned)
    with zipfile.ZipFile(unsigned_path) as archive:
        items = sorted(archive.infolist(), key=lambda item: item.header_offset)
    if len(items) != count or len(central) != count:
        raise ValueError("Unexpected APK entry count")
    position = 80
    sha = hashlib.sha256()
    size = 0
    with unsigned_path.open("rb") as source, output_path.open("wb") as target:
        def write(data):
            nonlocal size
            target.write(data)
            sha.update(data)
            size += len(data)
        for item in items:
            start = payload_start(source, item)
            source.seek(item.header_offset)
            original_header = source.read(start - item.header_offset)
            header, position = apply_patch(original_header, patch, position)
            write(header)
            source.seek(start)
            remaining = item.compress_size
            while remaining:
                buf = source.read(min(1024*1024, remaining))
                if not buf:
                    raise ValueError("Unsigned APK payload truncated")
                write(buf)
                remaining -= len(buf)
        (signing_length,) = struct.unpack_from("<I", patch, position)
        position += 4
        write(patch[position:position+signing_length])
        position += signing_length
        for entry in central:
            updated, position = apply_patch(entry, patch, position)
            write(updated)
        tail, position = apply_patch(unsigned[eocd:], patch, position)
        write(tail)
    if position != len(patch) or size != expected_size or sha.hexdigest() != SIGNED_SHA256:
        output_path.unlink(missing_ok=True)
        raise ValueError("Signed APK byte-for-byte verification failed")
    print("Verified signed STAYON+ 5.4.9:", size, "bytes, SHA-256", sha.hexdigest())

if __name__ == "__main__":
    main()
