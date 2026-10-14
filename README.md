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
- 파일명 및 확장자 문자열 변경
- Android Storage Access Framework 기반 파일 선택/쓰기
- MP3 · FLAC · M4A/MP4 · OGG · Opus · WAV · AIFF · WMA · DSF 메타데이터 처리
- 라이트/다크 모드 대응 iOS 스타일 Jetpack Compose UI
- 로그인 · 서버 · 광고 없이 100% 로컬 처리

> **확장자 변경은 파일명 변경입니다.** `song.mp3`를 `song.flac`으로 바꿔도 코덱을 FLAC으로 재인코딩하지 않습니다. 메타데이터 수정 자체도 오디오 스트림을 재인코딩하지 않습니다.

### 🛡️ 원본 덮어쓰기 안전장치

저장 시 바로 원본을 건드리지 않습니다. 캐시의 작업본에 먼저 메타데이터를 기록하고 다시 읽어 검증한 뒤 원본을 덮어씁니다. 덮어쓰기 후 실제 URI를 다시 읽어 핵심 태그를 재검증하며, 실패하면 보관해 둔 원본 복사본으로 복구를 시도합니다. 1GB를 초과하는 파일은 안전을 위해 차단합니다.

Android 문서 제공자(SAF)의 동작은 제조사·클라우드 서비스마다 다를 수 있으므로 **중요한 파일은 처음 사용하기 전에 별도 백업을 권장합니다.**

### 📦 설치 / 빌드

`main` 브랜치 푸시마다 GitHub Actions에서 `:app:assembleDebug`를 수행하고 설치 가능한 debug APK를 artifact로 생성합니다.

로컬 빌드 환경은 JDK 17, Android SDK 35, Gradle 8.9 기준입니다. Android Studio에서 저장소를 열고 Gradle Sync 후 실행할 수 있습니다.

### ✅ v1.0.0 상태

- CI Android debug APK 빌드 성공
- 메타데이터 엔진: `jaudiotagger-android`
- 원본 덮어쓰기 전/후 검증 및 best-effort 복구 구현
- 파일명/확장자 rename 구현
- 실기기별 SAF 및 각 플레이어 호환성은 실제 기기 테스트가 추가로 필요

---

## English

Music Magic edits **metadata embedded in the actual audio file** rather than storing cosmetic changes inside the app. The selected source document is overwritten by default.

### Features

- Edit title, artist, album, album artist, genre, year and track number
- Rename file names and filename extensions
- Metadata handling for MP3, FLAC, M4A/MP4, OGG, Opus, WAV, AIFF, WMA and DSF
- Android Storage Access Framework support
- iOS-inspired Jetpack Compose UI with light/dark mode
- Fully local: no login, backend or ads

> Changing a filename extension does **not** transcode the audio codec. Metadata editing also does not re-encode the audio stream.

### Safe overwrite strategy

Music Magic edits and verifies a temporary working copy first, keeps a backup copy of the selected source, overwrites the original only after verification, re-reads the actual document after writing, and attempts recovery if the provider write fails. Files larger than 1 GiB are rejected.

Provider behavior varies across Android devices and cloud document providers. Back up valuable files before first use.

### Build

The `main` branch is built by GitHub Actions with JDK 17 / Android SDK 35 / Gradle 8.9. A signed debug APK is uploaded as the `music-magic-debug` workflow artifact.

---

## Third-party

Music Magic uses the Android-compatible jaudiotagger fork for audio metadata handling. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for attribution and license information.
