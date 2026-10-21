#!/usr/bin/env python3
"""
AgentForge 高并发限流与 Retry-After 探针
并发打入指定挂件端点，验证原子 Lua 限流与动态 Retry-After 响应头
"""

import argparse
import concurrent.futures
import json
import sys
import urllib.error
import urllib.request


def send_request(url: str, token: str, timeout: float = 10.0):
    payload = json.dumps({"message": "ping"}).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Public-Token"] = token
        headers["X-AgentForge-Public-Token"] = token

    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            headers_dict = dict(response.headers)
            return status, headers_dict, None
    except urllib.error.HTTPError as e:
        headers_dict = dict(e.headers)
        return e.code, headers_dict, None
    except Exception as e:
        return 0, {}, str(e)


def main():
    parser = argparse.ArgumentParser(description="AgentForge Rate Limit Concurrency Probe")
    parser.add_argument("--url", required=True, help="Target widget chat endpoint")
    parser.add_argument("--token", default="", help="Public widget token (optional)")
    parser.add_argument("--concurrency", type=int, default=40, help="Number of concurrent requests (default: 40)")
    parser.add_argument("--expected-limit", type=int, default=30, help="Expected RPM limit (default: 30)")

    args = parser.parse_args()

    print(f"Starting concurrency probe: {args.concurrency} concurrent requests -> {args.url}")

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(send_request, args.url, args.token) for _ in range(args.concurrency)]
        results = [f.result() for f in futures]

    count_200 = sum(1 for status, _, _ in results if status == 200)
    count_429 = sum(1 for status, _, _ in results if status == 429)
    count_other = len(results) - count_200 - count_429

    print(f"Results summary:")
    print(f"  - HTTP 200 OK: {count_200}")
    print(f"  - HTTP 429 Too Many Requests: {count_429}")
    print(f"  - Other responses / errors: {count_other}")

    retry_after_values = []
    for status, headers, err in results:
        if status == 429:
            ra = headers.get("Retry-After") or headers.get("retry-after")
            if ra:
                try:
                    retry_after_values.append(int(ra))
                except ValueError:
                    sys.stderr.write(f"[FAIL] Invalid Retry-After header: {ra}\n")
                    sys.exit(1)
            else:
                sys.stderr.write("[FAIL] Missing Retry-After header in 429 response\n")
                sys.exit(1)

    if count_429 > 0:
        print(f"  - Retry-After range: min={min(retry_after_values)}s, max={max(retry_after_values)}s")

    # 断言校验
    errors = []
    if count_200 > args.expected_limit:
        errors.append(f"HTTP 200 count ({count_200}) exceeded expected limit ({args.expected_limit})")

    expected_min_429 = max(0, args.concurrency - args.expected_limit)
    if count_429 < expected_min_429:
        errors.append(f"HTTP 429 count ({count_429}) is less than expected minimum ({expected_min_429})")

    for ra in retry_after_values:
        if not (1 <= ra <= 60):
            errors.append(f"Retry-After value {ra} is out of expected [1, 60] range")
            break

    if errors:
        sys.stderr.write("\n[FAIL] Rate limit probe failed:\n")
        for err in errors:
            sys.stderr.write(f"  - {err}\n")
        sys.exit(1)

    print("\n[OK] Rate limit concurrency probe PASSED")
    sys.exit(0)


if __name__ == "__main__":
    main()
