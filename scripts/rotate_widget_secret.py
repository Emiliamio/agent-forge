#!/usr/bin/env python3
"""
AgentForge 挂件密钥双阶段平滑轮换工具 (rotate_widget_secret.py)
生成密码学安全新密钥，打印平滑轮换 SQL、窗口结束收敛 SQL 与紧急回滚 SQL
"""

import argparse
import secrets
import string
import sys


def generate_secret(length: int = 32) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def main():
    parser = argparse.ArgumentParser(
        description="AgentForge Widget Secret Rotation Tool (Two-Phase Smooth Migration)"
    )
    parser.add_argument("--agent-id", type=int, required=True, help="Target Agent App ID")
    parser.add_argument("--tenant-id", type=int, required=True, help="Target Tenant ID")
    parser.add_argument(
        "--length", type=int, default=32, help="Length of the new secret (default: 32)"
    )

    args = parser.parse_args()

    new_secret = generate_secret(args.length)

    print("=" * 70)
    print(" AgentForge 挂件密钥双阶段平滑轮换指南 (Widget Secret Rotation)")
    print(f" 目标智能体 ID: {args.agent_id} | 租户 ID: {args.tenant_id}")
    print(f" 新生成的安全密钥: {new_secret}")
    print("=" * 70)
    print()
    print("【第一阶段：开启轮换过渡窗口 (推荐窗口时间 ≤ 24 小时)】")
    print("说明：将当前密钥归档至 public_token_prev，并设定新密钥为 public_token。")
    print("效果：轮换过渡期内，新旧客户端签发的令牌均可正常通过校验 (HTTP 200)。")
    print()
    print("--- 请在数据库中执行以下 SQL 启动轮换 ---")
    print(
        f"UPDATE agent_app "
        f"SET public_token_prev = public_token, "
        f"public_token = '{new_secret}', "
        f"updated_at = CURRENT_TIMESTAMP "
        f"WHERE id = {args.agent_id} AND tenant_id = {args.tenant_id};"
    )
    print()
    print("=" * 70)
    print("【第二阶段：收敛并关闭轮换窗口】")
    print("说明：通知全部客户端迁移至新密钥后，清空 public_token_prev。")
    print("效果：旧密钥彻底失效，任何残留旧令牌将被坚决拦截 (HTTP 401)。")
    print()
    print("--- 迁移完毕后在数据库中执行以下 SQL 完成收敛 ---")
    print(
        f"UPDATE agent_app "
        f"SET public_token_prev = NULL, "
        f"updated_at = CURRENT_TIMESTAMP "
        f"WHERE id = {args.agent_id} AND tenant_id = {args.tenant_id};"
    )
    print()
    print("=" * 70)
    print("【应急回滚方案 (Rollback Plan)】")
    print("说明：若轮换过程中客户端出现大面积异常，可一键撤销轮换还原至原密钥。")
    print()
    print("--- 应急回滚执行以下 SQL ---")
    print(
        f"UPDATE agent_app "
        f"SET public_token = public_token_prev, "
        f"public_token_prev = NULL, "
        f"updated_at = CURRENT_TIMESTAMP "
        f"WHERE id = {args.agent_id} AND tenant_id = {args.tenant_id} AND public_token_prev IS NOT NULL;"
    )
    print("=" * 70)


if __name__ == "__main__":
    main()
