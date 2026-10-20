<div align="center">

<img src="docs/music-magic-mark.svg" alt="Music Magic app icon" width="112" />

# Music Magic

**내 음악을, 내 마음대로.**<br />
Edit your music. Keep it yours.

실제 음악 파일의 태그·표지·가사를 편집하는 Android 앱<br />
An Android audio metadata editor with an iOS-inspired interface.

<p>
  <img alt="Android 10 or later" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Debug-signed preview" src="https://img.shields.io/badge/Release-Debug%20preview-64748B" />
</p>

<a href="https://github.com/homesweetlove/Music_Magic-/releases/download/v1.0.0-liquid-ui/Music-Magic-debug.apk"><img alt="Android APK 다운로드 / Download Android APK" src="https://img.shields.io/badge/Android%20APK-Download%20%2F%20다운로드-0A84FF?style=for-the-badge&logo=android&logoColor=white" /></a>

[직접 다운로드 / Direct APK download](https://github.com/homesweetlove/Music_Magic-/releases/download/v1.0.0-liquid-ui/Music-Magic-debug.apk) · [릴리스 / Release notes](https://github.com/homesweetlove/Music_Magic-/releases/tag/v1.0.0-liquid-ui)

> **플레이어 개발 버전 안내:** 위 v1.0.0 릴리스 APK는 기존 편집기 버전입니다. 새 플레이어는 [PR #1](https://github.com/homesweetlove/Music_Magic-/pull/1)의 `feat/fluid-music-player` 브랜치에 있으며, 빌드 검증 후 새 APK로 배포해야 합니다.

**비공개 저장소: 접근 권한이 있는 GitHub 계정으로 로그인해야 다운로드할 수 있습니다.**<br />
**Private repository: sign in to GitHub with an account that has repository access to download.**

Android 10+ · API 29+ · Debug-signed preview · Google Play 배포 없음 / No Play Store release

[설치 / Install](#install) · [기능 / Features](#features) · [시작 / Quick start](#quick-start) · [안전 / Safety](#safety) · [개발 / Development](#development)

</div>

---

<a id="install"></a>
## APK 설치 · Install

### 한국어

1. Android 10 이상 기기에서 위의 **Android APK 다운로드**를 누릅니다.
2. 다운로드한 **Music-Magic-debug.apk** 파일을 엽니다.
3. Android가 요청하면 해당 브라우저의 **이 출처 허용 / 알 수 없는 앱 설치** 권한을 켠 뒤 **설치**를 누릅니다.

### English

1. On an Android 10 or later device, select **Download Android APK** above.
2. Open the downloaded **Music-Magic-debug.apk** file.
3. If Android asks, allow **Install unknown apps / Allow from this source** for your browser, then select **Install**.

> **미리보기 빌드 / Preview build** — 개발용 서명(debug) APK이며 Google Play 배포판이 아닙니다. / This APK uses a debug signing key and is distributed as a preview outside Google Play.

<a id="features"></a>
## 할 수 있는 일 · Features

앱 안의 표시만 바꾸는 대신 **실제 오디오 파일에 메타데이터를 기록**합니다. 기본 저장은 선택한 원본 파일을 덮어씁니다.<br />
Music Magic writes **metadata into the actual audio file**. Saving overwrites the selected source document by default.

| | 한국어 | English |
| :--- | :--- | :--- |
| **태그 · Tags** | 제목, 아티스트, 앨범, 앨범 아티스트, 장르, 연도, 트랙 번호 편집 | Edit title, artist, album, album artist, genre, year and track number |
| **표지·가사 · Artwork & lyrics** | 앨범아트 삽입, 내장 가사 편집, 음악 미리듣기 | Embed cover art, edit embedded lyrics and preview audio |
| **파일 · Files** | SAF로 파일·폴더 선택, 확장자를 유지한 이름 변경 및 아티스트/제목 기반 정리 | Select files/folders through Android SAF; rename using artist/title while preserving the extension |
| **정리 · Library** | 폴더 내 아티스트/앨범 일괄 편집, SHA-256 완전 동일 파일 탐지 | Batch-edit artist/album in a folder; find byte-identical files with SHA-256 |
| **탐색 · Browse** | Liquid Music 앨범/모든 곡 캐러셀, 표지 기반 색상, 라이트·다크 모드 | Liquid Music album/all-track carousel, artwork-derived colors and light/dark themes |
| **플레이어 · Player** | 햄버거 메뉴로 편집기↔음악 재생 전환, 표지 색상 반응형 배경, 큐·탐색·셔플·반복·백그라운드 재생 | Drawer navigation, artwork-reactive background, queue/seek/shuffle/repeat and background playback |
| **저장 · Save** | 변경 미리보기, 원본 덮어쓰기 검증, 마지막 저장 백업 복원 | Preview changes, verify source overwrites and restore the last-save backup |
| **내보내기 · Export** | PCM WAV / AAC M4A 변환, 성공한 결과와 원본의 Before/After 비교 재생 | Export PCM WAV / AAC M4A; compare the actual source with a successful export |
| **곡 정보 · Lookup** | 선택적으로 MusicBrainz 검색 실행 — 인터넷 필요 | Optional, explicitly requested MusicBrainz lookup — internet required |
| **로컬 편집 · Local editing** | 메타데이터 편집은 오프라인으로 사용. 로그인·자체 서버·광고 없음 | Edit metadata offline. No app login, app backend or ads |

**재생 지원:** 편집 가능한 모든 확장자가 모든 Android 기기에서 재생되는 것은 아닙니다. 실제 재생 가능 여부는 기기의 Media3/시스템 코덱에 따라 달라집니다.

**메타데이터 형식 / Metadata formats**<br />
MP3 · FLAC · M4A/M4B/MP4 · OGG/OGA · Opus · WAV · AIF/AIFF · WMA · DSF

> **변환 ≠ 업스케일 / Conversion ≠ upscaling**<br />
> DSP/AI 업스케일 엔진은 구현되어 있지 않습니다. Before/After는 실제 WAV/AAC M4A 변환 결과를 비교하며, WAV 변환이 손실된 음원 정보를 복원하지는 않습니다.<br />
> No DSP/AI upscaling engine is implemented. Before/After compares real WAV/AAC M4A exports; converting to WAV does not restore lost audio detail.

<a id="quick-start"></a>
## 바로 시작 · Quick start

| 단계 · Step | 한국어 | English |
| :---: | :--- | :--- |
| **1** | 파일이나 폴더를 선택하고 접근 권한을 허용합니다. | Choose a file or folder and grant access. |
| **2** | 앨범 → **곡 보기** → **정보 편집**에서 태그·표지·가사를 수정합니다. | Browse an album → **곡 보기** (view tracks) → **정보 편집** (edit metadata). |
| **3** | 변경 내용을 미리 보고 저장합니다. 변환은 별도의 WAV/M4A 내보내기를 사용합니다. | Preview changes and save. Use the separate WAV/M4A export action for conversion. |

**왼쪽 상단 메뉴 → 뮤직 플레이어**를 누르면 선택한 폴더의 곡을 연속 재생할 수 있습니다. 앨범 카드를 넘기거나 **이 곡 재생**을 누르세요. Android 미디어 세션을 통해 백그라운드·잠금 화면에서도 제어할 수 있습니다. 플레이어는 로컬 파일만 재생하며 스트리밍 서비스와 연동하지 않습니다.

**모든 곡**은 하위 폴더를 포함해 최대 500곡을 표시합니다. 스와이프는 탐색만 수행하며, 저장·재생은 버튼으로 실행합니다.<br />
**모든 곡** (all tracks) shows up to 500 discovered tracks, including nested folders. Swipes browse cards; buttons perform writes and start playback.

<a id="safety"></a>
## 저장 전 확인 · Safety & limits

**중요한 파일은 첫 사용 전에 별도로 백업하세요. / Back up valuable files before first use.**

| | 한국어 | English |
| :--- | :--- | :--- |
| **원본 보호 · Overwrite** | 작업본을 기록·검증한 뒤 원본을 덮어쓰고 실제 URI의 텍스트 태그를 재검증합니다. 실패 시 원본 복사본으로 복구를 시도하지만 보장하지 않습니다. 표지·오디오 무결성 전체를 검증하지는 않습니다. | A working copy is written and verified before overwriting the source; text tags are re-read from the actual URI afterward. Recovery is best effort. These checks do not verify artwork or full audio integrity. |
| **백업 · Backup** | 앱 전체에 마지막 원본 백업 **한 건만** 보관합니다. 일괄 편집은 일부만 성공할 수 있으며, 마지막으로 저장에 성공한 파일만 되돌릴 수 있습니다. | The app retains **one** last-source backup in total. A batch may partially succeed; only the last successfully saved file remains undoable. |
| **파일 크기 · Size** | 메타데이터 편집 입력은 **최대 1 GiB**입니다. 문서 제공자가 보고한 크기를 기준으로 초과 파일을 차단합니다. | Metadata editing accepts inputs **up to 1 GiB**; larger files are rejected based on the document provider's reported size. |
| **변환 · Codecs** | 확장자만 바꾸는 저장은 차단합니다. MP3 인코딩·FLAC 출력은 미지원이며, WAV/M4A 변환도 기기의 코덱 지원에 따라 실패할 수 있습니다. | Extension-only changes are rejected. MP3 encoding and FLAC export are unavailable; WAV/M4A conversion can fail depending on device codec support. |
| **중복 · Duplicates** | SHA-256은 파일 바이트의 완전 동일성을 비교하며 오디오 유사도를 비교하지 않습니다. | SHA-256 checks exact file bytes, not audio similarity. |
| **접근 · Access** | 기기·클라우드 SAF 제공자와 플레이어마다 호환성이 다릅니다. 클라우드 파일 접근에는 인터넷이 필요할 수 있습니다. | Compatibility varies by device, SAF provider and player. Accessing cloud files may require internet. |

<a id="development"></a>
## 더 알아보기 · Details

<details>
<summary><strong>Liquid Music UI · 모션과 접근성 / Motion & accessibility</strong></summary>

### 한국어

- Compose `HorizontalPager`와 spring으로 앨범/곡 카드를 구현했습니다. 표지 패럴랙스와 제한된 stretch/scale·글로우·그라데이션을 사용하며 텍스트 대비를 유지합니다.
- Android 12 **API 31+**에서만 표지에 작은 방향성 블러를 적용합니다. Android 10–11과 저메모리 기기는 scale/stretch·그라데이션으로 동작합니다. **RuntimeShader/AGSL은 사용하지 않습니다.**
- 시스템 애니메이션을 끄면 장식 모션·블러·자동 스냅 애니메이션도 꺼집니다. 무한 반복 애니메이션은 없습니다.
- TalkBack 페이지 안내, **48dp** 이전/다음 버튼, 확장 가능한 글자 영역, 라이트·다크 모드를 지원합니다.
- 폴더·메타데이터·썸네일 처리는 UI 스레드 밖에서 실행합니다. 표지는 최대 **512px**, 캐시는 **8 MiB LRU**이며 현재 카드와 이웃만 표지를 요청합니다.
- Before/After는 원본과 성공한 내보내기 결과를 각각 재생합니다. 정지 버튼 또는 앱의 백그라운드 전환 시 재생을 해제합니다.
- Inspora의 fluid carousel은 모션 참고로만 사용했으며 웹 코드·디자인 에셋을 가져오지 않았습니다.

### English

- Album/song cards use Compose `HorizontalPager` and springs, with artwork parallax and bounded stretch/scale, glow and gradients that preserve text contrast.
- Subtle artwork-only blur runs on Android 12 **API 31+**. Android 10–11 and low-RAM devices use scale/stretch and gradients. **No RuntimeShader/AGSL is used.**
- Disabling system animations disables decorative motion, blur and animated snapping. There are no perpetual animations.
- Accessibility includes TalkBack page announcements, **48dp** previous/next buttons, growing text areas and light/dark themes.
- Folder, metadata and thumbnail work runs off the UI thread. Artwork is at most **512px**, cached in an **8 MiB LRU**, and requested only for the current card and its neighbors.
- Before/After buttons play the source and successful export separately. Stop or backgrounding the app releases playback.
- Inspora's fluid carousel was a motion reference only; no website code or design assets were copied.

</details>

<details>
<summary><strong>개발과 검증 / Development & verification</strong></summary>

| 항목 · Item | 내용 · Details |
| :--- | :--- |
| **환경 · Toolchain** | JDK 17 · Android SDK 35 · Gradle 8.9 · min SDK 29 |
| **로컬 실행 · Run locally** | Android Studio에서 저장소를 열고 Gradle Sync 후 실행합니다. / Open the repository in Android Studio, sync Gradle and run. |
| **자동 빌드 · CI** | `main` 푸시 시 `:app:assembleDebug` 실행, `music-magic-debug` APK artifact 생성. / Pushes to `main` build a debug APK as the `music-magic-debug` artifact. |
| **워크플로 · Workflow** | [Android GitHub Actions](https://github.com/homesweetlove/Music_Magic-/actions/workflows/android.yml) |
| **메타데이터 엔진 · Metadata engine** | `jaudiotagger-android` |

**검증 기록 / Verification record** — `45e70ff`: API 29·35 에뮬레이터에서 **각 7개 테스트 통과**. `a1f3409`: debug APK 빌드 성공. / At `45e70ff`, **7 tests passed on each** of the API 29 and API 35 emulators. The debug APK build succeeded at `a1f3409`.

컴포넌트 테스트는 버튼·스와이프·비활성 상태·목록 축소/빈 상태·비교 URI 연결·200% 글자·시스템 애니메이션 끄기를 검사합니다. URI 연결 검사는 실제 변환·재생의 종단 간 검증은 아닙니다. 실기기 GPU 프레임 시간·메모리·SAF 호환성은 추가 검증이 필요합니다.<br />
Component tests cover buttons, swipes, disabled actions, shrinking/empty lists, comparison URI binding, 200% text and disabled system animations. URI binding checks do not establish end-to-end conversion/playback. Physical-device GPU frame times, memory and SAF compatibility still need verification.

</details>

---

### Third-party notices

Music Magic은 Android 호환 jaudiotagger 포크를 사용합니다. / Music Magic uses the Android-compatible jaudiotagger fork for audio metadata handling.

저작권·라이선스 정보 / Attribution and license information: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
