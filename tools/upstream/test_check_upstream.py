"""Tests for check_upstream.py: python -m unittest discover tools/upstream"""
import unittest

from check_upstream import based_on_version, parse_version, reset_fork_release


class VersionTest(unittest.TestCase):

    def test_stable_versions_compare_numerically(self):
        self.assertEqual(parse_version("v5.4.0"), (5, 4, 0))
        self.assertEqual(parse_version("5.10.0"), (5, 10, 0))
        self.assertGreater(parse_version("v5.10.0"), parse_version("5.9.3"))
        self.assertEqual(parse_version("v5.4.0"), parse_version("5.4.0"))

    def test_betas_and_other_tags_are_not_stable(self):
        self.assertIsNone(parse_version("v5.4.0-beta2"))
        self.assertIsNone(parse_version("5.4.0-q1"))
        self.assertIsNone(parse_version("nightly"))

    def test_based_on_reads_upstream_version_line(self):
        gradle = 'def baseVersionCode = 1109\ndef appVersionName = "5.4.0"\n'
        self.assertEqual(based_on_version(gradle), "5.4.0")

    def test_reset_fork_release(self):
        self.assertEqual(reset_fork_release("x\ndef qpipepipeRelease = 7\ny"),
                         "x\ndef qpipepipeRelease = 1\ny")
        self.assertEqual(reset_fork_release("def qpipepipeRelease = 1\n"),
                         "def qpipepipeRelease = 1\n")


if __name__ == "__main__":
    unittest.main()
