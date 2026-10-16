#!/usr/bin/env python3
"""Checks a release APK after R8: ABIs, native libraries and JNI bindings.

usage: verify-release-apk.py <apk> <abis|all> [R8 mapping.txt]

- With a comma-separated ABI list (e.g. arm64-v8a), the APK must contain native libraries for
  exactly those ABIs (a universal APK built with "all" is not restricted).
- Every 64-bit library must have ELF LOAD segments aligned to at least 16 KB (Android 15+ devices
  with 16 KB pages).
- The playback libraries (mpvRx/libmpv, FFmpeg, libass, Media3 FFmpeg decoder) must be present.
- Every JNI entry point exported by the native libraries (Java_<class>_<method>) must still exist
  in the dex files with the same class and method name. With the R8 mapping, a missing one is only
  an error when R8 renamed it (a call would fail with UnsatisfiedLinkError); one that R8 removed
  because nothing calls it is listed as information.
"""
import struct
import sys
import zipfile

REQUIRED_LIBS = [
    "libmpv.so", "libplayer.so", "libavcodec.so", "libavformat.so", "libavutil.so",
    "libavfilter.so", "libswscale.so", "libswresample.so", "libc++_shared.so",
    "libass.so", "libasskt.so", "libffmpegJNI.so",
]
PAGE_16K = 0x4000


def uleb128(data, pos):
    result = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, pos
        shift += 7


def dex_methods(data):
    """Returns (defined class descriptors, {(class descriptor, method name)}) of one dex file."""
    (string_ids_size, string_ids_off, type_ids_size, type_ids_off) = struct.unpack_from("<IIII", data, 0x38)
    (method_ids_size, method_ids_off, class_defs_size, class_defs_off) = struct.unpack_from("<IIII", data, 0x58)

    def string(index):
        offset = struct.unpack_from("<I", data, string_ids_off + 4 * index)[0]
        _, start = uleb128(data, offset)
        end = data.index(b"\0", start)
        return data[start:end].decode("utf-8", "replace")

    types = [string(struct.unpack_from("<I", data, type_ids_off + 4 * i)[0]) for i in range(type_ids_size)]
    methods = set()
    for i in range(method_ids_size):
        class_idx, _, name_idx = struct.unpack_from("<HHI", data, method_ids_off + 8 * i)
        methods.add((types[class_idx], string(name_idx)))
    defined = {types[struct.unpack_from("<I", data, class_defs_off + 32 * i)[0]] for i in range(class_defs_size)}
    return defined, methods


def elf_info(blob):
    """(is 64-bit, minimum LOAD alignment, exported Java_ symbol names)."""
    if blob[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = blob[4] == 2
    if is64:
        e_phoff, = struct.unpack_from("<Q", blob, 0x20)
        e_shoff, = struct.unpack_from("<Q", blob, 0x28)
        e_phentsize, e_phnum, e_shentsize, e_shnum = struct.unpack_from("<HHHH", blob, 0x36)
    else:
        e_phoff, e_shoff = struct.unpack_from("<II", blob, 0x1C)
        e_phentsize, e_phnum, e_shentsize, e_shnum = struct.unpack_from("<HHHH", blob, 0x2A)
    aligns = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type, = struct.unpack_from("<I", blob, base)
        if p_type != 1:  # PT_LOAD
            continue
        aligns.append(struct.unpack_from("<Q", blob, base + 0x30)[0] if is64 else struct.unpack_from("<I", blob, base + 0x1C)[0])
    # Dynamic symbols (.dynsym + its .dynstr): JNI entry points must be exported to be found.
    symbols = []
    sections = []
    for i in range(e_shnum):
        base = e_shoff + i * e_shentsize
        if is64:
            _, sh_type, _, _, sh_offset, sh_size, sh_link, _, _, sh_entsize = struct.unpack_from("<IIQQQQIIQQ", blob, base)
        else:
            _, sh_type, _, _, sh_offset, sh_size, sh_link, _, _, sh_entsize = struct.unpack_from("<IIIIIIIIII", blob, base)
        sections.append((sh_type, sh_offset, sh_size, sh_link, sh_entsize))
    for sh_type, sh_offset, sh_size, sh_link, sh_entsize in sections:
        if sh_type != 11 or not sh_entsize:  # SHT_DYNSYM
            continue
        strtab_off = sections[sh_link][1]
        for j in range(sh_size // sh_entsize):
            name_off, = struct.unpack_from("<I", blob, sh_offset + j * sh_entsize)
            end = blob.index(b"\0", strtab_off + name_off)
            name = blob[strtab_off + name_off:end].decode("ascii", "replace")
            if name.startswith("Java_"):
                symbols.append(name)
    return is64, (min(aligns) if aligns else 0), symbols


def demangle_jni(symbol):
    """Java_is_xyz_mpv_MPVLib_create -> ('Lis/xyz/mpv/MPVLib;', 'create')."""
    body = symbol[len("Java_"):]
    parts, current, i = [], [], 0
    while i < len(body):
        ch = body[i]
        if ch == "_" and i + 1 < len(body) and body[i + 1] in "0123":
            code = body[i + 1]
            if code == "1":
                current.append("_"); i += 2
            elif code == "2":
                current.append(";"); i += 2
            elif code == "3":
                current.append("["); i += 2
            else:
                current.append(chr(int(body[i + 2:i + 6], 16))); i += 6
        elif ch == "_":
            parts.append("".join(current)); current = []; i += 1
        else:
            current.append(ch); i += 1
    parts.append("".join(current))
    if "" in parts:  # overloaded: Java_pkg_Class_method__<signature>
        parts = parts[:parts.index("")]
    return "L" + "/".join(parts[:-1]) + ";", parts[-1]


def renamed_in_mapping(mapping_path, wanted):
    """{(class descriptor, method): obfuscated name} for wanted members that R8 kept under another name."""
    by_class = {}
    for cls, method in wanted:
        by_class.setdefault(cls[1:-1].replace("/", "."), set()).add(method)
    renamed, current = {}, None
    with open(mapping_path, encoding="utf-8", errors="replace") as mapping:
        for line in mapping:
            if not line.startswith(" "):
                current = line.split(" -> ")[0] if " -> " in line and line.rstrip().endswith(":") else None
                continue
            if current not in by_class or "(" not in line or " -> " not in line:
                continue
            left, new_name = line.strip().rsplit(" -> ", 1)
            original = left.split("(")[0].split()[-1].split(":")[-1].split(".")[-1]
            if original in by_class[current] and new_name != original:
                renamed[("L" + current.replace(".", "/") + ";", original)] = new_name
    return renamed


def main():
    apk_path, abis = sys.argv[1], sys.argv[2]
    mapping_path = sys.argv[3] if len(sys.argv) > 3 else None
    errors = []
    missing_jni = []
    with zipfile.ZipFile(apk_path) as apk:
        names = apk.namelist()
        defined, methods = set(), set()
        for name in names:
            if name.startswith("classes") and name.endswith(".dex"):
                d, m = dex_methods(apk.read(name))
                defined |= d
                methods |= m
        libs = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
        present_abis = sorted({n.split("/")[1] for n in libs})
        print(f"ABIs in APK: {', '.join(present_abis)}")
        if abis != "all":
            expected = sorted(a.strip() for a in abis.split(",") if a.strip())
            if present_abis != expected:
                errors.append(f"APK contains ABIs {present_abis}, expected exactly {expected}")
        jni_checked = 0
        for abi in present_abis:
            abi_libs = {n.split("/")[-1]: n for n in libs if n.split("/")[1] == abi}
            for required in REQUIRED_LIBS:
                if required not in abi_libs:
                    errors.append(f"{abi}: missing {required}")
            for lib_name, entry in sorted(abi_libs.items()):
                is64, align, symbols = elf_info(apk.read(entry))
                if is64 and align < PAGE_16K:
                    errors.append(f"{entry}: LOAD alignment {hex(align)} < 16 KB")
                for symbol in symbols:
                    cls, method = demangle_jni(symbol)
                    if cls not in defined or (cls, method) not in methods:
                        missing_jni.append((entry, symbol, cls, method))
                    jni_checked += 1
            print(f"{abi}: {len(abi_libs)} libraries, required playback libraries present, 64-bit LOAD >= 16 KB")
        print(f"JNI entry points checked against dex: {jni_checked}")
        if missing_jni and mapping_path:
            renamed = renamed_in_mapping(mapping_path, {(c, m) for _, _, c, m in missing_jni})
            removed = 0
            for entry, symbol, cls, method in missing_jni:
                if (cls, method) in renamed:
                    errors.append(f"{entry}: {symbol}: R8 renamed {cls}.{method} to {renamed[(cls, method)]}")
                else:
                    removed += 1
                    print(f"info: {symbol} not in dex: removed by R8 as unused (not renamed)")
            print(f"JNI entry points removed as unused: {removed}; renamed: {len(renamed)}")
        else:
            for entry, symbol, cls, method in missing_jni:
                errors.append(f"{entry}: {symbol} -> {cls}.{method} missing after R8 (no mapping to tell removal from renaming)")
    if errors:
        for error in errors:
            print(f"::error::{error}")
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
