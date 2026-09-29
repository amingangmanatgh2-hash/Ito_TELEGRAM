#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
آینه‌ی پایتونیِ CatalogTest.kt

هدف: قبل از این‌که CI پنج دقیقه وقت بگذارد و روی تستِ واحد بیفتد، همان
شرط‌ها را همین‌جا (بدون JVM) بررسی کنیم. هر تغییری در CatalogTest.kt باید
اینجا هم بازتاب پیدا کند.
"""

import json
import os
import re
import sys
from collections import Counter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CATALOG = os.path.join(ROOT, "app", "src", "main", "assets", "features.json")
TEXTLAB = os.path.join(ROOT, "app", "src", "main", "java", "ito", "telegram", "core", "text", "TextLab.kt")

BANNED = ["رنگ صورتی", "رنگ آبی", "رنگ قرمز", "رنگ سبز", "رنگ زرد", "تم صورتی", "تم آبی"]

problems = []


def bad(msg):
    problems.append(msg)


def main():
    doc = json.load(open(CATALOG, encoding="utf-8"))
    items = doc["features"]

    if len(items) < 2000:
        bad(f"تعداد قابلیت‌ها کم است: {len(items)}")
    if len(items) != doc["total"]:
        bad(f"فیلد total ({doc['total']}) با تعداد واقعی ({len(items)}) نمی‌خواند")

    ids = Counter(i["id"] for i in items)
    for k, v in ids.items():
        if v > 1:
            bad(f"شناسه‌ی تکراری: {k}")
        if not re.fullmatch(r"[a-z0-9_.]+", k):
            bad(f"شناسه‌ی نامعتبر: {k}")

    titles = Counter(i["title"] for i in items)
    for k, v in titles.items():
        if v > 1:
            bad(f"عنوان تکراری: {k}")
        if not k.strip():
            bad("عنوان خالی")

    for i in items:
        for b in BANNED:
            if b in i["title"]:
                bad(f"آیتم پرکننده: {i['title']}")

    declared = {c["id"] for c in doc["categories"]}
    used = {i["cat"] for i in items}
    if used - declared:
        bad(f"دسته‌ی اعلام‌نشده: {sorted(used - declared)}")
    if declared - used:
        bad(f"دسته‌ی بدون آیتم: {sorted(declared - used)}")

    adult = [i for i in items if i.get("adult")]
    if len(adult) <= 20:
        bad(f"بخش بزرگسال خیلی کوچک است: {len(adult)}")
    for i in adult:
        if i["cat"] not in ("adult", "lang"):
            bad(f"آیتم بزرگسال در دسته‌ی نامربوط: {i['id']} -> {i['cat']}")

    for i in items:
        if i["impl"] not in ("core", "data", "combo"):
            bad(f"نوع پیاده‌سازی ناشناس: {i['impl']}")

    for i in items:
        if i["impl"] == "data" and i["cat"] != "wordlist":
            p = i.get("payload") or {}
            if not (p.get("text") or "").strip():
                bad(f"آیتم داده‌ای بدون محتوا: {i['id']}")

    src = open(TEXTLAB, encoding="utf-8").read()
    body = src[src.index("val all: List<Transform>"): src.index("val byId")]
    tids = re.findall(r"\n\s*t\(\s*\"([a-z0-9_.]+)\"", body)
    dup = [k for k, v in Counter(tids).items() if v > 1]
    if dup:
        bad(f"ابزار متنِ تکراری در TextLab.kt: {dup}")

    combos = [i for i in items if (i.get("payload") or {}).get("kind") == "textlab"]
    if len(combos) != len(tids) * 4:
        bad(f"هر ابزار باید چهار مسیر داشته باشد: {len(combos)} در برابر {len(tids) * 4}")
    ctids = {i["payload"]["transform"] for i in combos}
    if ctids - set(tids):
        bad(f"ابزارِ ناموجود در TextLab: {sorted(ctids - set(tids))[:10]}")
    if set(tids) - ctids:
        bad(f"ابزارِ بدون آیتم در کاتالوگ: {sorted(set(tids) - ctids)[:10]}")

    if problems:
        print("کاتالوگ مشکل دارد:")
        for p in problems:
            print("  -", p)
        return 1

    stats = doc["stats"]
    print(
        f"کاتالوگ سالم است: {len(items)} قابلیت "
        f"(core {stats['core']} / data {stats['data']} / combo {stats['combo']})، "
        f"{len(tids)} ابزار متن، {len(adult)} آیتم بزرگسال."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
