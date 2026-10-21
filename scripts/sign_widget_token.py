#!/usr/bin/env python3
"""
AgentForge 安全挂件令牌签名与验证工具
生成与校验 v1.<exp>.<nonce>.<hmacBase64Url> 签名令牌
"""

import argparse
import base64
import hmac
import hashlib
import secrets
import sys
import time


def sign_token(secret: str, ttl_seconds: int = 3600, nonce: str = None) -> str:
    if not nonce:
        nonce = secrets.token_hex(8)
    exp = int(time.time()) + ttl_seconds
    payload = f"v1.{exp}.{nonce}".encode("utf-8")
    mac = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).digest()
    sig = base64.urlsafe_b64encode(mac).decode("ascii").rstrip("=")
    return f"v1.{exp}.{nonce}.{sig}"


def verify_token(token: str, secret: str) -> bool:
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

    payload = f"v1.{exp_str}.{nonce}".encode("utf-8")
    mac = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).digest()
    expected_sig = base64.urlsafe_b64encode(mac).decode("ascii").rstrip("=")

    if not hmac.compare_digest(sig, expected_sig):
        sys.stderr.write("[FAIL] Invalid token signature\n")
        return False

    print(f"[OK] Token valid (exp={exp_str}, nonce={nonce})")
    return True


def main():
    parser = argparse.ArgumentParser(description="AgentForge Widget Token Signer & Verifier")
    parser.add_argument("--secret", required=True, help="Agent public token secret key")
    parser.add_argument("--ttl-seconds", type=int, default=3600, help="TTL in seconds (default: 3600)")
    parser.add_argument("--nonce", help="Custom nonce string (optional)")
    parser.add_argument("--verify", help="Verify given token against secret")

    args = parser.parse_args()

    if args.verify:
        if verify_token(args.verify, args.secret):
            sys.exit(0)
        else:
            sys.exit(1)
    else:
        token = sign_token(args.secret, args.ttl_seconds, args.nonce)
        print(token)


if __name__ == "__main__":
    main()
