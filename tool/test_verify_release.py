"""Regression coverage for old and current Android apksigner certificate output."""
import unittest
from verify_release import RELEASE_CERTIFICATE, verify_certificate


class CertificateOutputTest(unittest.TestCase):
    def output(self, label, certificate=RELEASE_CERTIFICATE, count=1):
        separator = ' ' if label.startswith('Signer #') else ': '
        return f"Number of signers: {count}\n{label}{separator}certificate SHA-256 digest: {certificate}\n"

    def test_numbered_signer(self):
        self.assertEqual(RELEASE_CERTIFICATE, verify_certificate(self.output('Signer #1')))

    def test_scheme_signer_used_by_current_sdk(self):
        self.assertEqual(RELEASE_CERTIFICATE, verify_certificate(self.output('V2 Signer')))

    def test_same_certificate_across_schemes(self):
        text = self.output('V2 Signer') + f'V3.1 Signer: certificate SHA-256 digest: {RELEASE_CERTIFICATE}\n'
        self.assertEqual(RELEASE_CERTIFICATE, verify_certificate(text))

    def test_wrong_certificate_is_rejected(self):
        with self.assertRaises(ValueError):
            verify_certificate(self.output('V2 Signer', '0' * 64))

    def test_multiple_signers_are_rejected_even_if_certificate_matches(self):
        with self.assertRaises(ValueError):
            verify_certificate(self.output('Signer #1', count=2))

    def test_unrecognized_output_is_rejected(self):
        with self.assertRaises(ValueError):
            verify_certificate('Number of signers: 1\nSource Stamp certificate SHA-256 digest: ' + RELEASE_CERTIFICATE)


if __name__ == '__main__':
    unittest.main()
