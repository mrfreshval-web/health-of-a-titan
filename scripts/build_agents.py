"""Собирает промпты агентов из docs/TEAM.md в .claude/agents/.

Запуск из корня репозитория: python3 scripts/build_agents.py
Каждый файл = YAML-шапка + общий базовый промпт + промпт роли.
"""
import os
import re
import sys

src_path = sys.argv[1] if len(sys.argv) > 1 else "docs/TEAM.md"
out_dir = sys.argv[2] if len(sys.argv) > 2 else ".claude/agents"
src = open(src_path, encoding="utf-8").read()
base = re.search(r"<!-- BASE:START -->\n(.*?)<!-- BASE:END -->", src, re.S).group(1).strip()
os.makedirs(out_dir, exist_ok=True)
for old in os.listdir(out_dir):
    if old.endswith(".md"):
        os.remove(os.path.join(out_dir, old))
pattern = r'<!-- AGENT id="([^"]+)" desc="([^"]+)" -->\n(.*?)<!-- AGENT:END -->'
for aid, desc, body in re.findall(pattern, src, re.S):
    name = aid.split("-", 1)[1]
    text = f"---\nname: {name}\ndescription: {desc}\n---\n\n{base}\n\n---\n\n{body.strip()}\n"
    with open(os.path.join(out_dir, aid + ".md"), "w", encoding="utf-8") as f:
        f.write(text)
    print(f"{aid}: {len(text)} символов")
