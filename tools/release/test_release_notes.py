"""Tests for release_notes.py: python -m unittest discover tools/release"""
import unittest

from release_notes import ROOT, fork_release, update_log, upstream_version

RES = ROOT / "app" / "src" / "main" / "res"


def bullets(strings_xml):
    lines = update_log(strings_xml.read_text(encoding="utf-8")).split("\n")
    assert all(line.startswith("- ") for line in lines), lines
    return len(lines)


class ReleaseNotesTest(unittest.TestCase):

    def test_versions(self):
        self.assertEqual(upstream_version('def appVersionName = "5.4.0"\n'), "5.4.0")
        self.assertEqual(fork_release("x\ndef qpipepipeRelease = 2\n"), 2)

    def test_update_log_unescapes_android_strings(self):
        xml = ('<resources><string name="other">x</string>'
               '<string name="qpipepipe_update_log">- One (Settings &gt; QPipePipe)\\n'
               '- Don\\\'t \\"lose\\" it</string></resources>')
        self.assertEqual(update_log(xml),
                         "- One (Settings > QPipePipe)\n- Don't \"lose\" it")

    def test_every_locale_has_the_current_notes(self):
        # A locale left behind would show the previous release's notes (FORK.md, Release notes).
        expected = bullets(RES / "values" / "strings_qpipepipe.xml")
        for strings_xml in RES.glob("values-*/strings_qpipepipe.xml"):
            with self.subTest(strings_xml.parent.name):
                self.assertEqual(bullets(strings_xml), expected)


if __name__ == "__main__":
    unittest.main()
