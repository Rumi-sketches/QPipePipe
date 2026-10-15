"""Tests for release_notes.py: python -m unittest discover tools/release"""
import unittest

from release_notes import fork_release, update_log, upstream_version


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


if __name__ == "__main__":
    unittest.main()
