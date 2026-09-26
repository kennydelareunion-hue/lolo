#!/usr/bin/env python3
"""Régénère app/src/main/assets/free_models.json depuis le catalogue officiel d'OmniRoute
(modèles gratuits + fournisseurs « sans clé » que OmniRoute considère toujours actifs).

Usage : python3 scripts/update_free_models.py
"""
import json
import re
import urllib.request
from pathlib import Path

SOURCE = ("https://raw.githubusercontent.com/diegosouzapw/OmniRoute/main/"
          "open-sse/config/freeModelCatalog.data.ts")
NOAUTH_SOURCE = ("https://raw.githubusercontent.com/diegosouzapw/OmniRoute/main/"
                 "src/shared/constants/providers/noauth.ts")
TARGET = Path(__file__).resolve().parent.parent / "TermuxDevCenter/app/src/main/assets/free_models.json"

text = urllib.request.urlopen(SOURCE, timeout=30).read().decode()
curated = re.search(r'FREE_CATALOG_CURATED_AT = "([^"]+)"', text)
entry = re.compile(r'\{ provider: "([^"]+)", modelId: "([^"]+)", displayName: "([^"]*)".*?freeType: "([^"]+)"')

providers: dict[str, dict[str, str]] = {}
for provider, model_id, _name, free_type in entry.findall(text):
    if free_type != "discontinued":
        providers.setdefault(provider, {})[model_id] = free_type

noauth_text = urllib.request.urlopen(NOAUTH_SOURCE, timeout=30).read().decode()
no_auth = sorted(set(re.findall(r'^\s+(?:id|alias): "([^"]+)"', noauth_text, re.M)))

TARGET.parent.mkdir(parents=True, exist_ok=True)
TARGET.write_text(json.dumps({
    "source": SOURCE,
    "curatedAt": curated.group(1) if curated else None,
    "noAuthProviders": no_auth,
    "providers": {p: dict(sorted(m.items())) for p, m in sorted(providers.items())},
}, ensure_ascii=False, indent=1) + "\n")
print(f"{sum(map(len, providers.values()))} modèles gratuits, {len(providers)} fournisseurs, "
      f"{len(no_auth)} identifiants sans clé -> {TARGET}")
