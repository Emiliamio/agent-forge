import json, sys

def normalize(path: str) -> None:
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    doc.pop("serialNumber", None)
    meta = doc.get("metadata") or {}
    meta.pop("timestamp", None)
    doc["metadata"] = meta
    if isinstance(doc.get("components"), list):
        doc["components"] = sorted(
            doc["components"],
            key=lambda c: (c.get("group") or "", c.get("name") or "", c.get("version") or ""),
        )
    if isinstance(doc.get("dependencies"), list):
        doc["dependencies"] = sorted(doc["dependencies"], key=lambda d: d.get("ref") or "")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")

if __name__ == "__main__":
    for p in sys.argv[1:]:
        normalize(p)
        print(f"[OK] normalized {p}")
