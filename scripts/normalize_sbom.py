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
    # cyclonedx 在启用可重复构建（outputTimestamp）时会追加 cdx:reproducible 属性，
    # 是否出现取决于 Maven 版本/构建环境，不属于依赖证据，统一剔除。
    props = meta.get("properties")
    if isinstance(props, list):
        meta["properties"] = [p for p in props if p.get("name") != "cdx:reproducible"]
    doc["metadata"] = meta
    if isinstance(doc.get("components"), list):
        doc["components"] = sorted(
            doc["components"],
            key=lambda c: (c.get("group") or "", c.get("name") or "", c.get("version") or ""),
        )
    if isinstance(doc.get("dependencies"), list):
        doc["dependencies"] = sorted(doc["dependencies"], key=lambda d: d.get("ref") or "")
    # newline="\n" 必须显式指定：Windows 下默认文本模式会把 \n 翻成 \r\n，
    # 导致 CI（Linux，LF）重新生成后与仓库里的 CRLF 版本全行 diff —— SBOM drift 假红。
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")

if __name__ == "__main__":
    for p in sys.argv[1:]:
        normalize(p)
        print(f"[OK] normalized {p}")
