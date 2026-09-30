"""Regression test for the source collector's AArch64 .SRCINFO selection."""

import importlib.util
import pathlib
import subprocess
import tempfile
import unittest


REPO = pathlib.Path(__file__).resolve().parents[2]
COLLECTOR = REPO / "garden-arch/rootfs/collect-sources.py"


class SourceSelectionTest(unittest.TestCase):
    def test_common_and_aarch64_sources_only(self):
        spec = importlib.util.spec_from_file_location("collect_sources", COLLECTOR)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        script = module.COLLECT

        self.assertIn("export CARCH=aarch64", script)
        self.assertIn("makepkg --verifysource --ignorearch", script)
        selector = next(line.split(" | sort -u", 1)[0]
                        for line in script.splitlines() if line.startswith("sed -n "))
        srcinfo = ("pkgbase = example\n"
                   "\tsource = common.tar.gz\n"
                   "\tsource_aarch64 = arm.tar.gz\n"
                   "\tsource_x86_64 = x86.tar.gz\n"
                   "\tsource_armv7h = armv7.tar.gz\n")
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "srcinfo"
            path.write_text(srcinfo)
            result = subprocess.run(selector.replace("/tmp/srcinfo", str(path)),
                                    shell=True, check=True, text=True,
                                    capture_output=True)
        self.assertEqual(["common.tar.gz", "arm.tar.gz"],
                         result.stdout.splitlines())


if __name__ == "__main__":
    unittest.main()
