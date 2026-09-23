# 🎶🧩 YT Music Queue API

Patches that let other apps, such as Tasker, control the YouTube Music queue.

## ❓ About

Adds a broadcast intent API to YouTube Music: song requests with fair ordering, add, play, move, remove,
jump, clear and read the queue, plus now playing events. Use these patches together with the
regular [Morphe Patches](https://github.com/MorpheApp/morphe-patches).

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=leobenzol/yt-music-queue-api

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.1](https://github.com/leobenzol/yt-music-queue-api/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 YouTube Music&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.37.54 | 🧪&nbsp;9.36.50 | 🧪&nbsp;9.35.54 | 9.15.51 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Queue API](#queue-api) | Lets other apps, such as Tasker, control the queue with broadcast intents. | • Token |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Run the unit tests of the patches and the extension with `./gradlew test`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

YT Music Queue API patches are licensed under the [GNU General Public License v3.0](LICENSE)
