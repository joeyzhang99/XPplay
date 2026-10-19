# Third-party notices

XPplay is distributed under the **GNU General Public License v3.0** (see [`LICENSE`](../LICENSE)).
It is not affiliated with, authorised by, or certified by Apple Inc.

## Upstream projects

| Project | Role in XPplay | Licence |
| --- | --- | --- |
| [xcertplay](https://github.com/shilapi/xcertplay) | iAP2 / AirPlay / MFi protocol stack (`:shared`) | GPL-3.0 |
| [DiPlay](https://github.com/shihabal3amri/DiPlay) | Android head-unit app shell and settings UI (`:common`), and the fork point of this repository | GPL-3.0 |
| DiAuto | visual language the DiPlay UI was adapted from | AGPL-3.0 |

XPplay's own changes are released under the same GPL-3.0 terms. When distributing a binary, the
corresponding source must be made available as GPL-3.0 requires.

## Accessory identity bundled in release APKs

Release APKs built by the maintainer bundle an experimental MFi accessory identity
(`assets/offline-mfi/identity.pk8` and `certificate.p7b`). It was recovered from publicly available
Carlinkit firmware and is **not** an Apple-issued identity for this project:

- Subject `C=US, O=Apple Inc., OU=Apple Accessories, CN=IPA_24ACBB48646F8077F7463523085B60D2`,
  issued by Apple Accessories Certification Authority; EC P-256, valid 2018-06-13 → 2049-12-31.
- Anyone who obtains an APK can extract the certificate and the matching private key. Bundling it is a
  deliberate decision by the maintainer for personal use, not a claim of certification.
- Apple may revoke or stop accepting this identity at any time. If that happens, the fallback is a
  certified CarPlay accessory rather than software authentication.

Source builds never import the identity automatically: `DIPLAY_AUTH_ASSETS_DIR` must point at a
directory the builder owns, and `mobile/build.gradle.kts` pins the two expected files by SHA-256.

## Trademarks

Apple, iPhone, CarPlay, AirPlay and Siri are trademarks of Apple Inc. Android is a trademark of
Google LLC. XPeng and G3 are trademarks of their respective owners. All other trademarks belong to
their owners.

## No warranty

This project is provided "as is", without warranty of any kind. Use at your own risk.
