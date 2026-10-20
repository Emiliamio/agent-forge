import json, sys

def normalize(path: str) -> None:
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    doc.pop("serialNumber", None)
    meta = doc.get("metadata") or {}
    meta.pop("timestamp", None)
    # 生成工具版本随执行环境漂移（如 npm 版本随 CI 镜像变化），不纳入证据范围：
    # cyclonedx-maven-plugin 版本由 pom 固定、npm 版本由 CI 镜像固定，均可在构建脚本中追溯。
    meta.pop("tools", None)
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
