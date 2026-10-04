# ThothTerm Resolute — trademark and naming decision

**Decision (2026-10-04): the product formerly shown as "ThothTerm Ubuntu" is
named ThothTerm Resolute.** It is described factually as running "a Ubuntu
26.04 LTS userland", with a non-affiliation notice in the About dialog and the
store description. The name *ThothTerm Ubuntu* is no longer used anywhere a
user sees it (launcher label, title, welcome, notification, About, LAN page,
`thothfetch`, store title).

"Resolute" is the code name of Ubuntu 26.04 LTS (suite `resolute`, "Resolute
Raccoon"), the release this app runs — the same pattern as ThothTerm Trixie
(Debian 13 "trixie") and ThothTerm Rolling.

Internal identifiers keep `ubuntu`, because they describe the guest to the
project and changing them would break existing installs: package
`com.thothterm.ubuntu` (Android refuses an update to a different package, and
the F-Droid recipe is keyed by it), Gradle module `term-ubuntu`, source tags
`ubuntu-v*`. None of them is a software title.

## Evidence

Fetched 2026-10-04 from Canonical's live site; `ubuntu.com/legal/intellectual-property-policy`
redirects (302) to the page below.

| Source | Version / SHA-256 of the HTML |
|---|---|
| Canonical Intellectual property rights policy, <https://canonical.com/legal/intellectual-property-policy> | dated "15 July 2015"; `2e90334f2ae8c712f7a7b7031b76b77dad9171fba9d29c5532f7d698a551f71f` |

### What the policy says (section 4, "Your use of our trademarks")

- The Trademarks, "registered in word and logo form", include UBUNTU,
  KUBUNTU, EDUBUNTU, XUBUNTU, JUJU and LANDSCAPE. Release code names are not
  listed.
- **"You cannot use the Trademarks in software titles. If you are producing
  software for use with or on Ubuntu you may reference Ubuntu, but must avoid:
  (i) any implication of endorsement, or (ii) any attempt to unfairly or
  confusingly capitalise on the goodwill of Canonical or Ubuntu."**
- "You can use the Trademarks, in accordance with Canonical's brand
  guidelines, with Canonical's permission in writing."
- Permission is required for "any mark ending with the letters UBUNTU or
  BUNTU which is sufficiently similar to the Trademarks" and for "any
  Trademark in a domain name or URL or for merchandising purposes".

### Conclusion

"ThothTerm Ubuntu" puts the UBUNTU mark in a software title, which the policy
does not allow without written permission. The project has no such
permission and does not claim any. Referencing Ubuntu to say what the app
runs ("runs a genuine Ubuntu 26.04 LTS userland") is the reference the policy
allows for software used with Ubuntu, and is kept, with the notice
"ThothTerm Resolute is an independent application and is not affiliated with
or endorsed by Canonical. Ubuntu is a registered trademark of Canonical Ltd."

## Artwork

The launcher cup and the eight-petal emblem (`source/`) are the project's own
artwork. No Canonical logo (the "Circle of Friends") or Ubuntu font is used.
The orange palette is a colour choice, not a Canonical mark.
