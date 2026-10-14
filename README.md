# 🎵 Music Magic

**내 음악을, 내 마음대로.** / *Your music, your details.*

Android-native, offline-first audio metadata editor with an iOS-inspired interface.

[한국어](#한국어) · [English](#english)

## 한국어

### 핵심 원칙
- 앱 내부 데이터가 아닌 **실제 음악 파일의 태그**를 수정합니다.
- 기본 저장 방식은 **원본 덮어쓰기**입니다.
- 서버, 회원가입, 광고 없이 기기에서 처리합니다.

### 현재 구현 범위 (v0.1 초기 코드)
- Kotlin + Jetpack Compose 홈/편집 화면
- Android 파일 선택기(SAF)
- MP3 ID3v2 제목(TIT2), 아티스트(TPE1), 앨범(TALB) 읽기/쓰기
- MP3 파일명 변경
- 저장 후 바이트 단위 검증 및 실패 시 복원 시도

### 주의사항 및 로드맵
- **현재 MP3만 수정 가능합니다.** FLAC, M4A, WAV, OGG, OPUS 및 실제 포맷 변환은 아직 미구현입니다.
- 현재 MP3 저장은 기존 ID3 태그 전체를 새로 생성하므로 **앨범아트 및 기타 태그가 유실될 수 있습니다. 실제 중요한 파일에 사용하지 마세요.**
- SAF 제공자에 따라 덮어쓰기가 원자적으로 보장되지 않습니다. 복구도 항상 보장되지 않습니다.
- 다음 단계: 태그 보존형 편집 엔진, 앨범아트, 다중 선택/일괄 편집, 변환 엔진, 안전한 저장 트랜잭션, 테스트 및 APK 릴리스.
- 이 저장소에는 아직 CI 빌드 결과나 검증된 APK가 없습니다.

### 빌드
Android Studio에서 열고 JDK 17 및 Android SDK 35를 설치한 뒤 Gradle Sync → Run.

## English

Music Magic edits **embedded file metadata**, not just the app's display. Default behavior is overwriting the selected source document. No backend or login.

**Initial source only:** MP3 ID3v2 title/artist/album read/write, MP3 rename, SAF picker, Compose UI, write verification and best-effort recovery. Other audio formats, album artwork preservation, batch editing and conversion are **not implemented yet**. Current MP3 rewriting discards unrelated existing tags and artwork; do not use on valuable files. SAF writes are not guaranteed atomic. Build with Android Studio, JDK 17 and SDK 35.
