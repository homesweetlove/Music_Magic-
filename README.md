# 🎵 Music Magic

<p align="center"><b>내 음악을, 내 마음대로.</b><br/>Android-native audio metadata editor with an iOS-inspired interface.</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Offline" src="https://img.shields.io/badge/Processing-100%25%20Local-0A84FF" />
</p>

[한국어](#한국어) · [English](#english)

---

## 한국어

Music Magic은 앱 안에서만 제목을 바꿔 보이는 플레이어가 아닙니다. 선택한 **실제 오디오 파일에 메타데이터를 기록**하고, 기본적으로 그 원본 파일을 덮어씁니다.

### ✨ 기능

- 실제 파일 메타데이터 편집: 제목, 아티스트(만든이), 앨범, 앨범 아티스트, 장르, 연도, 트랙 번호
- 동일 확장자 내 파일명 변경 · 아티스트/제목 기반 파일명 정리
- Android Storage Access Framework 기반 파일 선택/쓰기
- MP3 · FLAC · M4A/MP4 · OGG · Opus · WAV · AIFF · WMA · DSF 메타데이터 처리
- 라이트/다크 모드 대응 iOS 스타일 Jetpack Compose UI
- Liquid Music 캐러셀: 앨범별/모든 곡 탐색, 앨범·곡 카드 전환, 실제 표지 기반 색상과 글로우
- 스와이프에 반응하는 spring 스냅·카드 stretch/scale·표지 패럴랙스 및 제한된 모션 블러
- WAV/M4A 내보내기 성공 후 실제 원본/결과 URI에 연결된 Before/After 비교 재생
- 오프라인 편집 · 앨범아트 삽입 · 가사 편집 · 음악 미리듣기
- 폴더 내 다중 파일 일괄 아티스트/앨범 편집 · SHA-256 완전 동일 파일 탐지
- 변경 전후 미리보기 · 마지막 저장 백업 복원
- Android 디코더를 이용한 PCM WAV 변환 · AAC M4A 인코딩
- 선택적 MusicBrainz 곡 정보 검색 (인터넷 연결 필요)
- 로그인 · 자체 서버 · 광고 없음

> **확장자만 바꾸는 저장은 차단합니다.** 실제 변환은 별도의 WAV/M4A 내보내기에서 실행됩니다. MP3 인코딩과 FLAC 출력은 아직 제공하지 않습니다. 일부 코덱·기기에서는 Android 하드웨어/소프트웨어 인코더 지원에 따라 변환이 실패할 수 있습니다.

### 🛡️ 원본 덮어쓰기 안전장치

저장 시 바로 원본을 건드리지 않습니다. 캐시의 작업본에 먼저 메타데이터를 기록하고 다시 읽어 검증한 뒤 원본을 덮어씁니다. 덮어쓰기 후 실제 URI를 다시 읽어 핵심 태그를 재검증하며, 실패하면 보관해 둔 원본 복사본으로 복구를 시도합니다. 1GB를 초과하는 파일은 안전을 위해 차단합니다.

Android 문서 제공자(SAF)의 동작은 제조사·클라우드 서비스마다 다를 수 있으므로 **중요한 파일은 처음 사용하기 전에 별도 백업을 권장합니다.**

### Liquid Music UI

폴더 선택 후 메인 캐러셀에서 앨범을 고르고 **곡 보기 → 정보 편집**으로 이동합니다. **모든 곡**에는 검색된 파일이 전부 표시됩니다(하위 폴더 포함 최대 500곡). 개별 파일을 열었을 때도 앨범/곡 카드를 넘겨 표지를 바꾸거나 미리들을 수 있습니다. 스와이프만으로 파일을 편집하거나 재생하지 않으며, 각 카드의 버튼으로 실행합니다.

Compose `HorizontalPager`와 spring으로 직접 구현한 독자적인 모션입니다. Inspora의 fluid carousel은 움직임의 참고로만 사용했으며 웹 코드·디자인 에셋을 가져오지 않았습니다. 표지 기반 그라데이션은 카드 안에서만 그리며, 배경색은 글자 대비에 영향을 주지 않도록 제한했습니다. Android 12(API 31)+에서만 표지에 작은 방향성 블러를 적용하고 Android 10–11 및 저메모리 기기는 scale/stretch와 그라데이션으로 동작합니다. RuntimeShader/AGSL은 사용하지 않아 Android 13 전용 경로가 필요 없습니다.

폴더/메타데이터/썸네일 처리는 UI 스레드 밖에서 수행합니다. 표지는 최대 512px로 디코딩하고 8MiB LRU에 보관하며, 현재 카드와 이웃만 요청합니다. 무한 반복 애니메이션은 없습니다. 시스템 애니메이션을 끄면 장식 모션·블러와 자동 스냅 애니메이션을 끕니다. TalkBack 페이지 안내, 48dp 이전/다음 버튼, 확장 가능한 글자 영역과 라이트/다크 모드를 지원합니다.

**Before/After의 현재 연결 대상은 실제 WAV/AAC M4A 변환 결과입니다.** 기존 `main`에는 DSP/AI 업스케일 엔진이 없으며 이 UI 변경에도 추가하지 않았습니다. WAV 변환이 손실 음원의 디테일을 복원하거나 AI로 음질을 향상한다고 표시하지 않습니다. 비교 재생은 버튼으로 각각 시작하며 정지 버튼과 앱 백그라운드 전환 시 재생을 해제합니다.

### 📦 설치 / 빌드

`main` 브랜치 푸시마다 GitHub Actions에서 `:app:assembleDebug`를 수행하고 설치 가능한 debug APK를 artifact로 생성합니다.

로컬 빌드 환경은 JDK 17, Android SDK 35, Gradle 8.9 기준입니다. Android Studio에서 저장소를 열고 Gradle Sync 후 실행할 수 있습니다.

### ✅ 구현 상태

- CI Android debug APK 빌드 성공
- 메타데이터 엔진: `jaudiotagger-android`
- 원본 덮어쓰기 전/후 검증 및 best-effort 복구 구현
- 동일 확장자 파일명 변경 및 PCM WAV/AAC M4A 내보내기 구현
- 온라인 MusicBrainz 검색은 명시적으로 실행할 때만 사용
- 마지막 원본 백업은 앱 내부에 한 건 보존; 일괄 편집은 파일별 마지막 저장만 실행 취소 가능
- SHA-256 중복 탐지는 오디오 유사도가 아닌 파일 바이트 완전 동일성 비교
- 실기기별 SAF 및 각 플레이어 호환성은 실제 기기 테스트가 추가로 필요
- 캐러셀 회귀 테스트: API 29/35 에뮬레이터에서 이전/다음·스와이프·비활성 버튼·목록 축소/빈 상태·실제 비교 URI 연결·200% 글자·애니메이션 끄기 검사. UI 스크린샷과 결과는 Actions artifact에 저장
- 실기기 GPU 프레임 시간과 메모리 프로파일은 추가 측정 필요

---

## English

Music Magic edits **metadata embedded in the actual audio file** rather than storing cosmetic changes inside the app. The selected source document is overwritten by default.

### Features

- Edit title, artist, album, album artist, genre, year and track number
- Rename files while preserving their real audio extension
- Metadata handling for MP3, FLAC, M4A/MP4, OGG, Opus, WAV, AIFF, WMA and DSF
- Android Storage Access Framework support
- iOS-inspired Jetpack Compose UI with light/dark mode
- Liquid Music album/all-track carousel and album/song faces with artwork-derived color and bounded glow
- Spring snapping, subtle stretch/scale, artwork parallax and bounded directional motion blur
- Before/After playback connected to the actual source and successfully exported WAV/M4A document
- Cover art, embedded lyrics, preview playback, batch artist/album editing, exact-file SHA-256 duplicate scan
- Undo last save and preview metadata changes
- Export decoded PCM WAV or AAC M4A using Android platform codecs
- Optional MusicBrainz metadata lookup (internet required)
- Local metadata editing; no login, backend or ads

> Extension-only changes are rejected. Actual conversion is available for WAV and AAC M4A export. MP3 encoding and FLAC export are not implemented. Codec support varies by Android device.

### Safe overwrite strategy

Music Magic edits and verifies a temporary working copy first, keeps a backup copy of the selected source, overwrites the original only after verification, re-reads the actual document after writing, and attempts recovery if the provider write fails. Files larger than 1 GiB are rejected.

Provider behavior varies across Android devices and cloud document providers. Back up valuable files before first use.

### Liquid Music UI

Choose a folder, browse albums, then use **곡 보기** (view tracks) and **정보 편집** (edit metadata). **모든 곡** (all tracks) exposes every discovered file, including nested folders, up to 500 tracks. Single-file users can swipe between album and song faces to change artwork or preview audio. Swiping never writes metadata or starts playback; explicit buttons perform actions.

This is an original Compose `HorizontalPager`/spring implementation. Inspora was a motion reference only; no website code or design assets were copied. Artwork colors tint bounded card gradients while text stays on a controlled surface. Android 12/API 31+ adds subtle artwork-only blur; Android 10–11 and low-RAM devices retain scale/stretch and gradients. No RuntimeShader/AGSL or Android 13-only path is needed.

Folder/metadata/thumbnail work runs off the UI thread. Thumbnails are at most 512px and use an 8MiB LRU; only the settled card and its neighbors request art. There are no perpetual animations. Disabling system animations removes decorative transforms/blur and animated snapping. TalkBack page announcements, 48dp previous/next buttons, growing text areas and light/dark themes are supported.

**The comparison currently uses real WAV/AAC M4A exports.** The existing main branch has no DSP/AI upscaling engine, and this UI change does not add one. Decoding to WAV does not reconstruct lost audio detail. Comparison buttons start each document separately; Stop or backgrounding the app releases playback.

The API 29/35 emulator smoke suite covers buttons, swipes, disabled actions, shrinking/empty lists, comparison URI binding, 200% text and disabled system animations. Screenshots and reports are uploaded as Actions artifacts. Physical-device GPU frame time, memory profiling and SAF compatibility still need device testing.

### Build

The `main` branch is built by GitHub Actions with JDK 17 / Android SDK 35 / Gradle 8.9. A debug-signed APK is uploaded as the `music-magic-debug` workflow artifact.

---

## Third-party

Music Magic uses the Android-compatible jaudiotagger fork for audio metadata handling. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for attribution and license information.
