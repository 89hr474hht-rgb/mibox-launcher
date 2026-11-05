# Fork rules (mibox-launcher)

This repository is a **fork of Gathin23/cleanyourtv** for one device: a Xiaomi Mi Box S 2nd gen
(`MiTV-AFKR0`, codename `jaws`, Google TV 11, build `RTT0.211222.001.773`). These rules take precedence
over the upstream [AGENTS.md](../AGENTS.md) where they differ; everything else in the upstream file
(safety rules for cleaning a TV, Home guard, setupwraith/Wi-Fi check, no uninstalling of system
packages) still applies.

## The owner's rules

- Talk to the owner in **French**. Code comments and commit messages stay in English (upstream rule).
- **No change on the box without explicit approval for that specific step.** One sensitive change at a
  time, then the tests in [mibox/docs/02-plan-de-tests.md](docs/02-plan-de-tests.md), then approval.
- **The box is used by the family.** Read-only ADB, builds and preparation are fine at any time.
  Anything visible on screen (install, Home change, disabling, reboot, `am start`, key injection,
  screenshots) needs an agreed time slot, even when the step itself is approved.
- Never mark a manual test as passed without the owner's report. Never claim Google TV is *removed*:
  it is disabled for user 0 only.
- **Red list, never disabled:** Bluetooth stack and `com.mi.bluetooth` (the Xiaomi remote is
  Bluetooth), network stack, `com.droidlogic*`, `mitv.service`, HDMI-CEC, Play Services, Play Store,
  WebView, the keyboard, `com.google.android.tv.remote.service`, **SFR TV**
  (`com.sfr.androidtv.launcher`, `com.sfr.androidtv.selfcare`), Netflix, YouTube, YouTube Music,
  Prime Video.
- No personal data in the repo (IP addresses, Wi-Fi name, MAC addresses, serial, Netflix ESN, names,
  e-mail). Device dumps go to `audits/` (git-ignored).

## Where things go

- Fork-specific files live under `mibox/` (docs, step lists, config, scripts) and `.github/README.md`.
  Keep upstream files unchanged unless the change is one of the fork features below, so that
  `git merge upstream/main` stays easy.
- Fork features (one commit each, behind a `config.xml` key when possible): release signing with the
  owner's key, the GitHub Releases updater, the HOME-role check, `release.yml`. Upstream forbids
  self-updaters, release signing and published APKs; **this fork allows them** — they are the point
  of the fork. Never commit a keystore or a GitHub token.
- Debloat steps are lists in `mibox/lists/`, applied one at a time with `tools/disable.sh`.

## ADB on this Mac

macOS blocks local-network access for an `adb` server started by Claude ("No route to host"). The
owner starts the server from Terminal.app (`adb connect <box>:5555`); reuse it, and **never run
`adb kill-server`**. For `tools/`, set `TV_ADB_BINARY=/opt/homebrew/bin/adb` so they go through that
server.
