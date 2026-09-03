#!/usr/bin/env python3
"""Build the RuneLite money-maker plugin dataset from the OSRS Wiki Bucket API.

Emits mmg-data.json: one entry per money making guide, with machine-readable
skill requirements, quest requirements, and item ids resolved against the
real-time prices mapping. No prices are baked in -- the plugin fetches those
live at runtime.
"""
import json, re, sys, urllib.parse, urllib.request

UA = "osrs-mmg-dataset-builder/1.0 (simon.craig90@gmail.com)"
WIKI = "https://oldschool.runescape.wiki/api.php"
MAPPING = "https://prices.runescape.wiki/api/v1/osrs/mapping"
QUERY = ("bucket('money_making_guide')"
         ".select('page_name','json','value','recurring')"
         ".limit(5000).run()")
TAX_EXEMPT_CAT = ("?action=query&list=categorymembers&format=json&formatversion=2"
                  "&cmlimit=500&cmtitle=Category:Items%20exempt%20from"
                  "%20Grand%20Exchange%20tax")

SCP = re.compile(r'<span class="scp"[^>]*?data-skill="([^"]*)"'
                 r'(?:[^>]*?data-level="([^"]*)")?[^>]*>')
# Pseudo-skills the wiki puts in scp spans that are not RuneLite Skill values.
NON_SKILL = {"Combat level", "Quest points", "Skills"}


def get(url):
    return json.load(urllib.request.urlopen(
        urllib.request.Request(url, headers={"User-Agent": UA})))


def strip_wikitext(s):
    """Flatten wiki markup to the plain text a tooltip can show."""
    if not s:
        return s
    s = re.sub(r"\[\[(?:File|Image):[^\]]*\]\]", "", s)
    s = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"'''?", "", s)
    s = re.sub(r"[ \t]+", " ", s)
    return "\n".join(l.strip(" *,") for l in s.split("\n") if l.strip(" *,")).strip()


def classify(prose):
    """Decide whether a skill line is a hard gate, and pull any higher
    'recommended' level out of it.

    The wiki puts the real requirement in the scp span's data-level. A trailing
    "(91 recommended)" names a *better* level, not a looser one -- so a numeric
    recommendation leaves the span level required. Only "optional", or a bare
    "recommended" with no number, softens the span itself.
    """
    rec = None
    m = re.search(r"(\d{1,2})\s*\+?\s*(?:or higher\s*)?"
                  r"(?:is\s+)?(?:strongly\s+|highly\s+)?"
                  r"(?:recommend|suggest|preferab|ideal)", prose, re.I)
    if m:
        rec = int(m.group(1))
    if re.search(r"\boptional\b", prose, re.I):
        return False, rec
    if rec is not None:
        return True, rec
    if re.search(r"\b(recommend|suggest|preferab|ideal)", prose, re.I):
        return False, None
    if re.search(r"^\s*\(?\s*(if|for|when|unless)\b", prose.strip(), re.I):
        return False, None
    return True, None


def parse_skills(raw):
    """Turn the wiki's skill wikitext into [{skill, level, required, recommended}]."""
    out = []
    if not raw or raw.strip() in ("None", ""):
        return out
    for line in raw.split("\n"):
        spans = SCP.findall(line)
        if not spans:
            continue
        # Test the prose with the spans removed, so the span's own level text
        # can't be mistaken for a recommendation.
        required, rec = classify(SCP.sub("", line))
        for skill, level in spans:
            lvl = re.match(r"(\d+)", level or "")
            if not lvl:
                continue  # e.g. a bare Agility icon with no level
            e = {"skill": skill, "level": int(lvl.group(1)), "required": required}
            if skill in NON_SKILL:
                e["pseudo"] = True
            if rec and rec > e["level"]:
                e["recommended"] = rec
            out.append(e)
    return out


def humanise_skills(raw):
    """Render the skill wikitext as "76 Fishing" style prose for tooltips.

    The skill name only lives in the span's attributes -- the visible part is a
    File: link -- so substitute the whole span before the markup is stripped.
    """
    if not raw or raw.strip() == "None":
        return ""
    def sub(m):
        skill, level = m.group(1), (m.group(2) or "").strip()
        return f"{level} {skill}".strip() + " \x00"
    s = SCP.sub(sub, raw)
    s = re.sub(r"\[\[(?:File|Image):[^\]]*\]\]", "", s)
    s = re.sub(r"\x00[^\x00<]*?</span>", "", s)
    return strip_wikitext(s.replace("\x00", ""))


def parse_quests(raw):
    if not raw or raw.strip() == "None":
        return []
    seen, out = set(), []
    for link in re.findall(r"\[\[([^\]|#]+)", raw):
        name = link.strip()
        if name and name not in seen:
            seen.add(name)
            out.append(name)
    return out


def main():
    ids, limits = {}, {}
    for it in get(MAPPING):
        ids[it["name"].lower()] = it["id"]
        if it.get("limit"):
            limits[it["id"]] = it["limit"]

    exempt = sorted({
        ids[m["title"].lower()]
        for m in get(WIKI + TAX_EXEMPT_CAT)["query"]["categorymembers"]
        if m["title"].lower() in ids
    })

    rows = get(WIKI + "?action=bucket&format=json&formatversion=2&query="
               + urllib.parse.quote(QUERY))["bucket"]

    methods, unresolved = [], set()
    for r in rows:
        d = json.loads(r["json"])

        def items(key):
            out = []
            for it in d.get(key) or []:
                e = {"name": it["name"], "qty": float(it["qty"]),
                     "perHour": bool(it.get("isph"))}
                if it.get("pricetype") == "gemw":
                    iid = ids.get(it["name"].lower())
                    if iid is None:
                        unresolved.add(it["name"])
                        e["flat"] = float(it.get("value") or 0)
                    else:
                        e["id"] = iid
                        if iid in limits:
                            e["buyLimit"] = limits[iid]
                else:
                    e["flat"] = float(it.get("value") or 0)
                out.append(e)
            return out

        p = d.get("prices") or {}
        methods.append({
            "page": r["page_name"],
            "name": r["page_name"].split("/", 1)[1],
            "activity": strip_wikitext(d.get("activity")),
            "members": bool(d.get("members")),
            "category": d.get("category"),
            "skillCategory": d.get("skillcategory"),
            "intensity": d.get("intensity"),
            "recurring": bool(r["recurring"]),
            "perKill": bool(d.get("isperkill")),
            "defaultKph": p.get("default_kph"),
            "kphLabel": p.get("kph_text"),
            "skills": parse_skills(d.get("skill")),
            "skillsText": humanise_skills(d.get("skill")),
            "quests": parse_quests(d.get("quest")),
            "questsText": strip_wikitext(d.get("quest")),
            "inputs": items("inputs"),
            "outputs": items("outputs"),
            "wikiValue": float(r["value"]) if r["value"] else 0.0,
        })

    methods.sort(key=lambda m: -m["wikiValue"])
    out = sys.argv[1] if len(sys.argv) > 1 else "mmg-data.json"
    with open(out, "w") as f:
        json.dump({"schema": 1, "taxExempt": exempt, "methods": methods}, f,
                  indent=1)
    print(f"{len(methods)} methods, {len(exempt)} tax-exempt items -> {out}; "
          f"unresolved item names: {sorted(unresolved) or 'none'}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
