#!/usr/bin/env python3
"""
AgentForge 安全挂件令牌签名与验证工具
生成与校验 v1.<exp>.<nonce>.<hmacBase64Url> 签名令牌
签名载荷：agent_id|tenant_id|exp|nonce
"""

import argparse
import base64
import hashlib
import hmac
import secrets
import sys
import time


def sign_token(agent_id: int, tenant_id: int, secret: str, ttl_seconds: int = 3600, nonce: str = None) -> str:
    if not nonce:
        nonce = secrets.token_hex(8)
    exp = int(time.time()) + ttl_seconds
    payload = f"{agent_id}|{tenant_id}|{exp}|{nonce}".encode("utf-8")
    mac = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).digest()
    sig = base64.urlsafe_b64encode(mac).decode("ascii").rstrip("=")
    return f"v1.{exp}.{nonce}.{sig}"


def verify_token(token: str, agent_id: int, tenant_id: int, secret: str) -> bool:
    if not token or not token.startswith("v1."):
        sys.stderr.write("[FAIL] Invalid token prefix (expected v1.)\n")
        return False
    parts = token.split(".")
    if len(parts) != 4:
        sys.stderr.write(f"[FAIL] Invalid token structure: expected 4 segments, got {len(parts)}\n")
        return False
    prefix, exp_str, nonce, sig = parts
    try:
        exp = int(exp_str)
    except ValueError:
        sys.stderr.write(f"[FAIL] Invalid exp integer: {exp_str}\n")
        return False

    now = int(time.time())
    if now > exp + 30:
        sys.stderr.write(f"[FAIL] Token expired: now={now}, exp={exp} (grace 30s exceeded)\n")
        return False

    payload = f"{agent_id}|{tenant_id}|{exp_str}|{nonce}".encode("utf-8")
    mac = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).digest()
    expected_sig = base64.urlsafe_b64encode(mac).decode("ascii").rstrip("=")

    if not hmac.compare_digest(sig, expected_sig):
        sys.stderr.write("[FAIL] Invalid token signature\n")
        return False

    print(f"[OK] Token valid (agent_id={agent_id}, tenant_id={tenant_id}, exp={exp_str}, nonce={nonce})")
    return True


def self_test():
    secret = "self-test-secret-key"
    agent_id = 1
    tenant_id = 1
    token = sign_token(agent_id, tenant_id, secret, ttl_seconds=60, nonce="testnonce01")
    if not verify_token(token, agent_id, tenant_id, secret):
        sys.stderr.write("[FAIL] Self-test verification failed\n")
        sys.exit(1)
    tampered = token + "X"
    if verify_token(tampered, agent_id, tenant_id, secret):
        sys.stderr.write("[FAIL] Tampered token incorrectly passed\n")
        sys.exit(1)
    if verify_token(token, agent_id + 1, tenant_id, secret):
        sys.stderr.write("[FAIL] Cross-agent token incorrectly passed\n")
        sys.exit(1)
    print("[OK] Self-test passed")
    sys.exit(0)


def main():
    parser = argparse.ArgumentParser(description="AgentForge Widget Token Signer & Verifier")
    parser.add_argument("--agent-id", type=int, help="Agent ID")
    parser.add_argument("--tenant-id", type=int, help="Tenant ID")
    parser.add_argument("--secret", help="Agent public token secret key")
    parser.add_argument("--ttl-seconds", type=int, default=3600, help="TTL in seconds (default: 3600)")
    parser.add_argument("--nonce", help="Custom nonce string (optional)")
    parser.add_argument("--verify", help="Verify given token against agent-id, tenant-id, and secret")
    parser.add_argument("--self-test", action="store_true", help="Run internal self test")

    args = parser.parse_args()

    if args.self_test:
        self_test()

    if not args.secret:
        parser.error("--secret is required")
    if args.agent_id is None:
        parser.error("--agent-id is required")
    if args.tenant_id is None:
        parser.error("--tenant-id is required")

    if args.verify:
        if verify_token(args.verify, args.agent_id, args.tenant_id, args.secret):
            sys.exit(0)
        else:
            sys.exit(1)
    else:
        token = sign_token(args.agent_id, args.tenant_id, args.secret, args.ttl_seconds, args.nonce)
        print(token)


if __name__ == "__main__":
    main()
