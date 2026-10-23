---
name: release
description: Publish a new Shelfi version as a GitHub release with the installable APK attached. Bumps the app version, runs tests, builds the release APK, checks it is signed by the same key as earlier releases, tags, pushes and runs `gh release create`. Use when the user asks to release, ship, publish or cut a version of Shelfi, or to put an APK on GitHub.
argument-hint: "[patch|minor|major|X.Y.Z]"
---

# Release Shelfi

Requested version: `$ARGUMENTS` (empty means you choose it in step 2).

Releases go to the public repo `mjarkk/shelfi-android-tv-launcher`. Tags are `vX.Y.Z`, and the app's `versionName` is `X.Y.Z`. One universal APK covers arm64, armv7, x86 and x86_64 TVs, so don't add ABI splits.

## 1. Preflight

Stop and tell the user if any of these fail:

- `git status --porcelain` is empty and the branch is `main`.
- After `git fetch origin`, `main` is not behind `origin/main`. Being ahead is fine because those commits ship with the release.
- `gh auth status` succeeds.

## 2. Pick the version

- Previous release: `gh release list --limit 1 --json tagName --jq '.[0].tagName'`. Empty means this is the first release.
- Current version: `versionCode` and `versionName` in `androidApp/build.gradle.kts`.
- Changes: `git log --oneline <prevTag>..HEAD`, or the full log for the first release.

If the version in `build.gradle.kts` has never been released (no previous release, or the previous tag doesn't match it), release it as it is. Otherwise bump it:

- `$ARGUMENTS` is `patch`, `minor` or `major`: bump that part.
- `$ARGUMENTS` is `X.Y.Z`: use it. It must be higher than the previous release.
- Empty: ask once with AskUserQuestion. Offer patch, minor and major, and recommend one based on the changes. Before 1.0, new features are minor and fixes-only releases are patch.

`versionCode` goes up by exactly 1 on every bump. Android won't install an update whose `versionCode` is lower than the installed one, even when the `versionName` is newer.

## 3. Build and verify

1. Edit `versionCode` and `versionName` in `androidApp/build.gradle.kts` (skip this if you're not bumping).
2. Run `mise run test` and `mise run apk:release`. If either fails, revert with `git checkout -- androidApp/build.gradle.kts`, show the failure and stop.
3. Run `mise run apk:signer` to get the new APK's signing certificate digest.
4. If there is a previous release, download its APK into the scratchpad with `gh release download <prevTag> --pattern '*.apk' --dir <scratchpad>/prev-apk`, then run `mise run apk:signer <that apk>`. If the two digests differ, revert and stop. Users who installed the earlier release could not update to this one. The release build is signed with `~/.android/debug.keystore`, so a mismatch usually means you are on another machine or the keystore was regenerated.
5. Copy the APK to `build/release/shelfi-vX.Y.Z.apk`. That filename is what users download.

## 4. Release notes

Write the notes to `<scratchpad>/release-notes.md`. Base the changes on the commit log, written for someone using the launcher: what looks or behaves differently. Leave out refactors, build changes and docs-only commits. For the first release, describe what Shelfi is (see `README.md`) instead of listing changes.

```markdown
## What's new
- …

## Install
Download `shelfi-vX.Y.Z.apk` below. It runs on ARM and x86 Android TV devices with Android 8.0 or newer.

    adb install -r shelfi-vX.Y.Z.apk
    adb shell cmd package set-home-activity com.mkopenga.tvhomescreen/.MainActivity

The second command makes Shelfi the home screen. If Home still opens the stock launcher (common on Google TV), see [Disable the stock launcher](https://github.com/mjarkk/shelfi-android-tv-launcher#disable-the-stock-launcher).

Signing certificate SHA-256: `<digest>`
```

## 5. Publish

```sh
git commit -am "Release vX.Y.Z"   # skip when the version wasn't bumped
git tag -a vX.Y.Z -m "Shelfi vX.Y.Z"
git push origin main vX.Y.Z
gh release create vX.Y.Z build/release/shelfi-vX.Y.Z.apk \
  --verify-tag --title "Shelfi vX.Y.Z" --notes-file <scratchpad>/release-notes.md
```

If the push is rejected, don't force it. Delete the local tag (`git tag -d vX.Y.Z`), report the error and stop. The release commit stays local so the user can decide what to do.

Finish by giving the user the release URL that `gh` printed, the version and `versionCode`, and the signer digest.
