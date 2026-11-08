# LaunchOS TV

A player for an Xtream Codes account (live channels, films, series), for the owner's Mi Box. It is a separate
app from the launcher (`com.gathin.launchos.tv`): it needs the network and a video engine, which the launcher must
not carry. Plain Java, Media3 (ExoPlayer) for playback, AndroidX RecyclerView for the lists. No Play Store, no
Google services, no analytics.

## The account

- The owner types the server, user name and password in the app (Compte). They are kept on the box only, encrypted
  with a key held by the Android Keystore; backups are off. They are never in this repo, in an APK or on the Mac.
- Nothing in the app logs, and R8 strips `android.util.Log` from the release build: every Xtream address carries
  the user name and password.
- The Xtream protocol itself sends them in every request, often over plain http. That is the provider's design.

## Build

```bash
cd mibox/tv
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The release build is signed with the LaunchOS release key (`~/.mibox-launcher/keystore.properties`, or the
`MIBOX_KEYSTORE_*` variables in CI), so a later version installs over it. Version in `VERSION`.

## Test without a real account

```bash
python3 -I mibox/tv/mock/xtream_mock.py --port 8080
```

A fake Xtream server on 127.0.0.1 (account demo / demo), with test-pattern streams made by ffmpeg. The emulator
reaches it at `http://10.0.2.2:8080`. `--redirect`, `--episodes-as-list` and `--plain-epg` mimic panels that answer that way.

Updates: the same updater as LaunchOS, reading this repository's `tv-vX.Y.Z` releases (published by
`.github/workflows/release-tv.yml`; notes from `CHANGELOG.md`). Bump `VERSION` and the changelog, then push the tag.

Study and decisions: [mibox/docs/09-appli-tv-xtream.md](../docs/09-appli-tv-xtream.md). Tests: sections C decies,
C duodecies, C quaterdecies and C quindecies of [mibox/docs/02-plan-de-tests.md](../docs/02-plan-de-tests.md).
