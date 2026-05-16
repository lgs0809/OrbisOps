#!/usr/bin/env python3
"""Compatibility entrypoint for the repository-wide release hygiene gate."""

from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).resolve().parents[2] / "scripts" / "release-hygiene.py"), run_name="__main__")
