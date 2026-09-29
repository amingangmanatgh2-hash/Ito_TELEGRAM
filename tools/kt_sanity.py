#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
بررسی سبکِ استاتیک روی سورس کاتلین، بدون نیاز به کامپایلر.

چه چیزی را می‌گیرد؟
  ۱) پرانتز/آکولاد/براکت نامتوازن (با نادیده‌گرفتن رشته‌ها و کامنت‌ها)
  ۲) شناسه‌ی بزرگ‌حرفی که نه ایمپورت شده، نه در همین ماژول تعریف شده،
     و نه جزو فهرست شناخته‌شده‌ی کاتلین/جاواست  ⇐ معمولاً یعنی ایمپورت جا افتاده
  ۳) ایمپورت تکراری
"""

import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC_DIRS = [
    os.path.join(ROOT, "app", "src", "main", "java"),
    os.path.join(ROOT, "app", "src", "test", "java"),
]

BUILTIN = set("""
Any Array ArrayList Boolean BooleanArray Byte ByteArray Char CharArray CharSequence Collection
Comparable Double DoubleArray Enum Exception Float FloatArray Function HashMap HashSet IllegalArgumentException
IllegalStateException Int IntArray Iterable Iterator List Long LongArray Map MutableList MutableMap MutableSet
Nothing Number NullPointerException Number Pair Regex RuntimeException Sequence Set Short String StringBuilder
Throwable Triple Unit UnsupportedOperationException Volatile Synchronized JvmStatic JvmField Deprecated
Suppress OptIn Override Throws SuppressLint Math System Thread Runnable Comparator Void Object Class
AssertionError Error NoSuchElementException Result Lazy Byte Character Integer Boolean Long Float Double
Charsets StringBuilder Appendable Cloneable AutoCloseable Closeable UInt ULong UByte UShort
""".split())

# چیزهایی که با نامِ کامل (fully qualified) صدا زده می‌شوند، نیازی به ایمپورت ندارند
FQ_PREFIXES = ("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "com.", "org.", "ito.")

decl_re = re.compile(
    r"^\s*(?:@\w+\s+)*(?:public |private |internal |protected |abstract |open |sealed |data |enum |annotation |value |inner |inline )*"
    r"(class|interface|object|enum class|annotation class)\s+([A-Z]\w*)",
    re.M,
)
typealias_re = re.compile(r"^\s*typealias\s+([A-Z]\w*)", re.M)
import_re = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", re.M)
package_re = re.compile(r"^package\s+([\w.]+)", re.M)
# هر چیزی که در فایل تعریف می‌شود: تابع، مقدار، شیء، ورودی enum
def_re = re.compile(r"\b(?:fun|val|var|object|class|interface|typealias)\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)")
enum_entry_re = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:,|;|\(|$)", re.M)
enum_inline_re = re.compile(r"enum\s+class\s+\w+[^{]*\{([^}]*)\}")


def enum_names(src):
    out = set()
    for m in enum_entry_re.finditer(src):
        out.add(m.group(1))
    for m in enum_inline_re.finditer(src):
        for part in m.group(1).split(","):
            part = part.strip().split("(")[0].strip()
            if re.fullmatch(r"[A-Z][A-Z0-9_]*", part):
                out.add(part)
    return out


def strip_noise(text):
    """حذف رشته‌ها و کامنت‌ها برای شمارش امنِ پرانتزها."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append(' ')
            continue
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                if text[i] == '\\':
                    i += 1
                i += 1
            i += 1
            out.append(' ')
            continue
        if c == "'":
            i += 1
            while i < n and text[i] != "'":
                if text[i] == '\\':
                    i += 1
                i += 1
            i += 1
            out.append(' ')
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            out.append(' ')
            continue
        out.append(c)
        i += 1
    return "".join(out)



# --- توابع/الحاقی‌های کوچک‌حرفی که حتماً باید ایمپورت شوند -------------------
LOWER_REQUIRED = {
    "padding": "androidx.compose.foundation.layout.padding",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "width": "androidx.compose.foundation.layout.width",
    "height": "androidx.compose.foundation.layout.height",
    "size": "androidx.compose.foundation.layout.size",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "sizeIn": "androidx.compose.foundation.layout.sizeIn",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "offset": "androidx.compose.foundation.layout.offset",
    "imePadding": "androidx.compose.foundation.layout.imePadding",
    "navigationBarsPadding": "androidx.compose.foundation.layout.navigationBarsPadding",
    "statusBarsPadding": "androidx.compose.foundation.layout.statusBarsPadding",
    "systemBarsPadding": "androidx.compose.foundation.layout.systemBarsPadding",
    "safeDrawingPadding": "androidx.compose.foundation.layout.safeDrawingPadding",
    "clickable": "androidx.compose.foundation.clickable",
    "combinedClickable": "androidx.compose.foundation.combinedClickable",
    "background": "androidx.compose.foundation.background",
    "border": "androidx.compose.foundation.border",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "rememberScrollState": "androidx.compose.foundation.rememberScrollState",
    "selectable": "androidx.compose.foundation.selection.selectable",
    "toggleable": "androidx.compose.foundation.selection.toggleable",
    "clip": "androidx.compose.ui.draw.clip",
    "alpha": "androidx.compose.ui.draw.alpha",
    "rotate": "androidx.compose.ui.draw.rotate",
    "scale": "androidx.compose.ui.draw.scale",
    "shadow": "androidx.compose.ui.draw.shadow",
    "blur": "androidx.compose.ui.draw.blur",
    "remember": "androidx.compose.runtime.remember",
    "rememberSaveable": "androidx.compose.runtime.saveable.rememberSaveable",
    "mutableStateOf": "androidx.compose.runtime.mutableStateOf",
    "mutableIntStateOf": "androidx.compose.runtime.mutableIntStateOf",
    "mutableStateListOf": "androidx.compose.runtime.mutableStateListOf",
    "derivedStateOf": "androidx.compose.runtime.derivedStateOf",
    "rememberCoroutineScope": "androidx.compose.runtime.rememberCoroutineScope",
    "collectAsState": "androidx.compose.runtime.collectAsState",
    "rememberLauncherForActivityResult": "androidx.activity.compose.rememberLauncherForActivityResult",
    "items": "androidx.compose.foundation.lazy.items",
    "itemsIndexed": "androidx.compose.foundation.lazy.itemsIndexed",
    "stickyHeader": "androidx.compose.foundation.lazy.stickyHeader",
    "sp": "androidx.compose.ui.unit.sp",
    "dp": "androidx.compose.ui.unit.dp",
    "launch": "kotlinx.coroutines.launch",
    "delay": "kotlinx.coroutines.delay",
    "withContext": "kotlinx.coroutines.withContext",
}

# نماینده‌های by remember: بدون این دو ایمپورت، کامپایل نمی‌شود
DELEGATE_GET = "androidx.compose.runtime.getValue"
DELEGATE_SET = "androidx.compose.runtime.setValue"


def check_lowercase(rel, src, body, imports_full, problems, local_names=()):
    for name, need in LOWER_REQUIRED.items():
        if name in local_names:
            continue
        if name in ("dp", "sp"):
            used = re.search(r"\d\s*\.\s*" + name + r"\b", body) is not None
        else:
            used = re.search(r"(?<![\w.])" + name + r"\s*[({]", body) is not None
        if not used:
            continue
        if need in imports_full:
            continue
        pkg_star = need.rsplit(".", 1)[0] + ".*"
        if pkg_star in imports_full:
            continue
        problems.append(f"{rel}: «{name}» استفاده شده ولی import {need} نیست")

    # by remember { mutableStateOf(...) }
    if re.search(r"\bby\s+remember", body) or re.search(r"\bby\s+\w*[Ss]tate", body):
        if DELEGATE_GET not in imports_full and "androidx.compose.runtime.*" not in imports_full:
            problems.append(f"{rel}: از «by remember» استفاده شده ولی import {DELEGATE_GET} نیست")
        if re.search(r"\bvar\s+\w+\s+by\s+remember", body):
            if DELEGATE_SET not in imports_full and "androidx.compose.runtime.*" not in imports_full:
                problems.append(f"{rel}: «var ... by remember» بدون import {DELEGATE_SET}")


def check_icons(rel, body, imports_full, problems):
    """آیکن‌های متریال: Icons.Filled.X بدون ایمپورتِ خودِ X کامپایل نمی‌شود."""
    for m in re.finditer(r"Icons\.(AutoMirrored\.)?(Filled|Outlined|Rounded|Sharp|TwoTone|Default)\.(\w+)", body):
        auto, style, name = m.group(1), m.group(2), m.group(3)
        style_pkg = "filled" if style == "Default" else style.lower()
        need = "androidx.compose.material.icons." + ("automirrored." if auto else "") + style_pkg + "." + name
        if need not in imports_full:
            problems.append(f"{rel}: آیکن «{name}» بدون import {need}")


def collect_files():
    files = []
    for d in SRC_DIRS:
        for dirpath, _, names in os.walk(d):
            for nm in names:
                if nm.endswith(".kt"):
                    files.append(os.path.join(dirpath, nm))
    return sorted(files)


def main():
    files = collect_files()
    if not files:
        print("هیچ فایل کاتلینی پیدا نشد")
        return 1

    # نقشه‌ی «نامِ نوع -> پکیج» برای همه‌ی چیزهایی که خودمان تعریف کرده‌ایم
    declared = {}
    file_text = {}
    for f in files:
        src = open(f, encoding="utf-8").read()
        file_text[f] = src
        pkg = package_re.search(src)
        pkg = pkg.group(1) if pkg else ""
        for m in decl_re.finditer(src):
            declared[m.group(2)] = pkg
        for m in typealias_re.finditer(src):
            declared[m.group(1)] = pkg
        for m in def_re.finditer(src):
            declared.setdefault(m.group(1), pkg)
        for nm in enum_names(src):
            declared.setdefault(nm, pkg)

    problems = []

    for f in files:
        src = file_text[f]
        rel = os.path.relpath(f, ROOT)
        clean = strip_noise(src)

        for open_c, close_c in [("(", ")"), ("{", "}"), ("[", "]")]:
            if clean.count(open_c) != clean.count(close_c):
                problems.append(
                    f"{rel}: نامتوازن {open_c}{close_c} -> {clean.count(open_c)} در برابر {clean.count(close_c)}"
                )

        pkg = package_re.search(src)
        pkg = pkg.group(1) if pkg else ""
        imported = {}
        seen_imports = set()
        for m in import_re.finditer(src):
            full, alias = m.group(1), m.group(2)
            name = alias or full.rsplit(".", 1)[-1]
            if full in seen_imports:
                problems.append(f"{rel}: ایمپورت تکراری -> {full}")
            seen_imports.add(full)
            imported[name] = full

        local_names = set()
        for m in decl_re.finditer(src):
            local_names.add(m.group(2))
        for m in typealias_re.finditer(src):
            local_names.add(m.group(1))
        for m in def_re.finditer(src):
            local_names.add(m.group(1))
        local_names |= enum_names(src)

        # هم‌پکیجی‌ها بدون ایمپورت در دسترس‌اند
        same_pkg = {n for n, p in declared.items() if p == pkg}

        body = strip_noise(re.sub(r"^\s*(package|import)\s+.*$", "", src, flags=re.M))
        used = set()
        for m in re.finditer(r"(?<![\w.])([A-Z]\w*)(?=[\s.(<{,)\]:;=?!*]|$)", body):
            used.add(m.group(1))
        # چیزهایی که با نام کامل صدا زده شده‌اند
        for m in re.finditer(r"(?:[a-z][\w]*\.){2,}([A-Z]\w*)", body):
            used.discard(m.group(1))

        for name in sorted(used):
            if name in BUILTIN or name in imported or name in local_names or name in same_pkg:
                continue
            if len(name) <= 2:  # پارامترهای جنریک مثل T و R
                continue
            if name in declared:
                problems.append(f"{rel}: «{name}» از پکیج {declared[name]} است ولی ایمپورت نشده")
            else:
                problems.append(f"{rel}: «{name}» ناشناس است (ایمپورت جا افتاده؟)")

        check_lowercase(rel, src, body, set(imported.values()), problems, local_names | {m.group(1) for m in def_re.finditer(src)})
        check_icons(rel, body, set(imported.values()), problems)

    if problems:
        print("مشکلات احتمالی:")
        for p in problems:
            print("  -", p)
        return 1
    print(f"سالم است: {len(files)} فایل کاتلین بررسی شد.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
