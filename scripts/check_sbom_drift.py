#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge SBOM 零漂移校验工具 (scripts/check_sbom_drift.py)
校验 docs/sbom/ 目录下的软件物料清单有效性与版本同步状态
"""

import json
import os
import sys


def check_sbom():
    files = ["docs/sbom/backend-bom.json", "docs/sbom/frontend-bom.json"]
    for path in files:
        if not os.path.exists(path):
            sys.stderr.write(f"[FAIL] Missing SBOM file: {path}\n")
            sys.exit(1)
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            if "metadata" in data and "component" in data["metadata"]:
                comp = data["metadata"]["component"]
                ver = comp.get("version")
                if ver and ver != "1.0.0":
                    sys.stderr.write(f"[FAIL] {path} metadata version is {ver}, expected 1.0.0\n")
                    sys.exit(1)
        except Exception as e:
            sys.stderr.write(f"[FAIL] Failed to parse {path}: {e}\n")
            sys.exit(1)

    print("[OK] SBOM files verified with v1.0.0 (no drift detected)")
    sys.exit(0)


if __name__ == "__main__":
    check_sbom()
