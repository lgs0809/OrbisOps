#!/usr/bin/env python3
"""Create/validate/publish the separate explicit-binding fixture through normal APIs; preserve existing versions."""
from pathlib import Path
import json, runpy
ROOT = Path(__file__).resolve().parents[1]
if __name__ == '__main__':
    prepare = runpy.run_path(str(ROOT / 'scripts/seed-skill-revocation-acceptance.py'))['prepare']
    print(json.dumps(prepare('workflow-skill-binding.json', 'skill-binding.json',
                            'OPS-07 手动验收 · Workflow 显式绑定'), ensure_ascii=False, indent=2))
