#!/usr/bin/env python3
"""Extract API endpoints and response samples from docs/report.html."""
import html
import re
from pathlib import Path
from urllib.parse import unquote, urlparse

REPORT = Path(__file__).resolve().parents[1] / "docs" / "report.html"
text = REPORT.read_text(encoding="utf-8", errors="replace")

# Split on request cards
blocks = re.split(r'<div class="card-header\s+bg-success iteration-0">', text)
print(f"Success request blocks: {len(blocks) - 1}")

samples = []
for block in blocks[1:]:
    title_m = re.search(r"Iteration:\s*1\s*-\s*([^<]+)", block)
    url_m = re.search(r'Request URL:</strong>\s*<a href="([^"]+)"', block)
    code_m = re.search(r"Response Code:</strong>.*?>\s*([^<]+)</span>", block, re.DOTALL)
    body_m = re.search(
        r"Response Body</h5>.*?<pre><code[^>]*>(.*?)</code></pre>",
        block,
        re.DOTALL,
    )
    if not title_m or not url_m:
        continue
    title = html.unescape(title_m.group(1).strip())
    url = html.unescape(unquote(url_m.group(1)))
    code = code_m.group(1).strip() if code_m else "?"
    body = ""
    if body_m:
        body = html.unescape(body_m.group(1))
        body = body.replace("&quot;", '"').strip()
        if len(body) > 400:
            body = body[:400] + "..."
    host = urlparse(url).netloc
    # Skip rate-limit / empty bodies
    meaningful = (
        '"Global Quote"' in body
        or '"c":' in body
        or '"results"' in body
        or '"ticker"' in body
        or "Time Series" in body
        or '"rates"' in body
        or '"bitcoin"' in body
        or '"data"' in body
        or '"close"' in body
        or len(body) > 80 and "Information" not in body[:120]
    )
    if "Thank you for using Alpha Vantage" in body or '"Note"' in body[:200]:
        meaningful = False
    samples.append(
        {
            "host": host,
            "title": title,
            "code": code,
            "url": url.split("?")[0] + "?" + (url.split("?")[1][:80] if "?" in url else ""),
            "meaningful": meaningful,
            "body_preview": body[:200] if body else "(empty)",
        }
    )

by_host = {}
for s in samples:
    by_host.setdefault(s["host"], []).append(s)

print("\n--- Meaningful responses by provider ---")
for host in sorted(by_host):
    items = [x for x in by_host[host] if x["meaningful"]]
    if not items:
        continue
    print(f"\n{host} ({len(items)} meaningful / {len(by_host[host])} total)")
    for x in items[:6]:
        print(f"  - {x['title']}")
        print(f"    {x['body_preview'][:120].replace(chr(10), ' ')}")

print("\n--- Non-meaningful (rate limit / empty) ---")
for host in sorted(by_host):
    bad = [x for x in by_host[host] if not x["meaningful"]]
    if bad:
        print(f"{host}: {len(bad)} requests with rate-limit or tiny body")
