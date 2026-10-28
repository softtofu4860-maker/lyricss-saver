<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# 스마트 음악 화면보호기 (Smart Music Screensaver)

음악 앱이 제공한 재생 정보와 가사를 테마 애니메이션과 함께 표시하는 안드로이드 화면보호기입니다. 실제 오디오 분석은 하지 않습니다.

## 🎨 화면보호기 테마 종류 (Dual Themes)

화면보호기 화면을 한 번 탭하면 나타나는 컨트롤 바의 **팔레트(🎨) 아이콘**을 눌러 언제든지 테마를 전환할 수 있습니다.

1. **클래식 네온 테마 (Classic Neon Theme)**
   - 선택한 효과 속도로 움직이는 네온 스펙트럼 애니메이션을 제공합니다. 실제 오디오 주파수나 BPM을 측정하지 않습니다.
   - 앨범 아트를 미세하게 다운스케일 후 부드러운 가우시안 블러 처리를 하여 연출한 심플하고 감각적인 앰비언트 배경 테마입니다.

2. **풀 아트 미니멀 테마 (Full Art Minimal Theme) [NEW]**
   - 불투명한 앨범 커버 이미지가 화면 전체를 가득 채우는 강렬하고 압도적인 아트워크 비주얼을 제공합니다.
   - **우측 정렬 가사 스크롤**: 화면 오른쪽에 가사가 감각적으로 부유하듯 스크롤됩니다.
   - **세로형 반투명 자막 오버레이**: 화면 우측 가장자리에 현재 구절의 글자가 일본어/한국어 세로쓰기(縦書き, Tategaki) 스타일의 오버레이로 아련하게 흐르는 듯한 감성적 디자인입니다.
   - **미니멀 플레어 카드**: 재생/일시정지, 이전곡/다음곡 조작과 진행 상태바를 한눈에 볼 수 있는 불투명 검정 그라데이션 카드가 배치됩니다.

---

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Build the debug variant for development. In the app dashboard, configure your personal Gemini key for translation. Keys are not built into the APK.
5. Run the app on an emulator or physical device. Grant notification access to read authorized music MediaSessions.
6. For a signed release, supply `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` through your local environment or CI secret store. Missing signing credentials produce an unsigned release artifact; do not distribute it as an installable release.

GitHub Actions builds and tests each update and retains a development APK for seven days. Its debug signing key is temporary: it is not a production signing key and it cannot be assumed to update an APK signed by another key. A production release requires the owner's existing release certificate and securely configured signing credentials.

## 싱크·번역·연동 수정

- 타임스탬프가 있는 LRC/YRC만 싱크 강조·가사 터치 탐색에 사용합니다. 일반 가사는 기본적으로 자동 강조하지 않으며, 화면의 **추정 스크롤 켜기**로만 추정 이동을 선택할 수 있습니다. 이전 캐시 중 출처가 없는 시간은 `기존 가사 · 시간 출처 미확인`으로 표시하고 원래 데이터를 유지합니다.
- 번역은 입력한 개인 키를 Android Keystore의 AES-GCM 키로 암호화해 저장하고 `x-goog-api-key` 헤더로 보냅니다. 키 미설정·인증·한도·네트워크·서버·빈 결과를 구분하며 원문과 원래 타이밍은 유지합니다. 기존 평문 키는 암호화 저장·검증 성공 후 제거합니다. 실패 시 기존 키를 파괴하지 않고 설정 오류를 표시합니다.
- 음악 상태는 OS가 허용한 MediaSession만 사용합니다. 검증할 수 없는 Spotify 방송의 수신·중복 등록은 제거했습니다. 재생 위치는 세션 갱신 시각과 단조 시계로 계산합니다.
- 제공처 ID가 있으면 제공처+ID를, 없으면 앨범·재생 길이를 추가해 저장·보정값을 구분합니다. 기존 보호 데이터는 한 버전에만 최초 연결하고 원본을 보존합니다. ID가 없고 앨범·길이까지 동일한 녹음은 구별할 수 없습니다.
- 자세한 수정 범위와 자동 테스트/실기기 검증 구분은 [유지보수 검증 보고서](docs/maintenance-verification.md)를 참고하세요.


## 가사 저장 및 캐시 동작

### 참고 프로젝트를 반영한 업데이트

Enhanced LRC·YRC 단어 타이밍, 여러 타임스탬프·오프셋 처리, NetEase 곡 링크/ID 조회, 곡별 싱크 보정값 저장을 지원합니다. 고사양 Apple Music 가사 화면은 단어별로 강조하고 저사양은 줄 단위로 표시합니다. [참고한 다섯 프로젝트·사용법·지원 범위](docs/lyrics-references.md)를 확인하세요.

- 곡을 감지하면 자동으로 검색합니다. 공식 영상·가사 표시 문구를 정리한 제목으로 정확 조회한 뒤, 제목+아티스트·아티스트 별칭·제목만 검색을 최대 3개 검색어로 시도합니다. 수동 검색과 같은 LRCLIB·NetEase 검색 API를 사용합니다.
- 한글·영문 아티스트 병기, 참여 가수 목록, `- Topic` 표기를 처리합니다. 제목·아티스트·곡 길이를 검증하고 라이브/리믹스 등 다른 버전은 구분합니다. 일반적으로 길이 차이는 12초 이내로 제한하며, 제목과 아티스트가 정확히 일치하는 경우 영상 인트로를 고려해 최대 30초까지 허용합니다. 아티스트 정보가 없어도 검색하되 동일 제목의 여러 가수가 매칭되면 임의 선택하지 않습니다.
- 검색 실패나 일시적인 연결 오류는 2초·8초 간격으로 최대 두 번 자동 재시도합니다. 성공·곡 변경·수동 교정 시 재시도를 중단합니다. LRCLIB 조회는 한 시도당 최대 10초, NetEase는 최대 8초로 제한합니다. 메타데이터를 검증할 수 없는 Spotify 프록시는 자동 선택에 사용하지 않습니다.
- 가사 교정의 DB 검색은 현재 곡과 가까운 LRCLIB 후보를 먼저 표시하며, 곡 길이·일치 여부·싱크 가사 여부를 확인하고 직접 적용할 수 있습니다. 일반 가사에는 기본적으로 시간이 없으며, 사용자가 추정 스크롤을 켠 경우에만 추정 시간을 사용합니다.
- NetEase 수동 선택은 선택한 곡 ID로 가사를 가져옵니다. 검색을 다시 실행하거나 창을 닫으면 이전 요청을 취소합니다. 가사가 없는 항목의 안내문은 적용할 수 없습니다.

- 수동 재검색은 기존 캐시와 메타데이터를 건너뛰고 입력한 검색어로 검색합니다. 실패한 검색 안내문은 저장하지 않으며, 기존 편집본도 삭제하지 않습니다.
- 원문, 타임스탬프, 발음은 유지하고 번역은 각 줄의 `translation` 필드에 저장합니다.
- 캐시 비우기는 자동 검색 결과만 삭제합니다. 수동 편집·재검색으로 선택한 결과·번역본은 보존합니다.
- DB v2 → v3 업데이트는 저장된 제목·아티스트로 유니코드를 보존하는 새 ID를 생성합니다. 기존 데이터에는 편집 여부 정보가 없어 모든 기존 항목을 보호된 저장본으로 이전합니다. 이전 버전의 ID 충돌로 이미 덮어써진 가사는 복구할 수 없습니다.
- 같은 곡의 동시 조회는 성공한 캐시를 재사용하고, 곡 변경 시 이전 검색·번역과 HTTP 요청을 취소합니다. 캐시 삭제 전에 시작한 자동 요청은 삭제 후 캐시를 다시 채우지 않습니다.

### 개발 검증

검색 단계별 진단은 별도 파일에 자동 기록됩니다. 관리자 진입 버튼은 일반 화면에 표시하지 않습니다. 대시보드 상단의 작은 제목 **스마트 화면보호기**를 **5초 안에 7번 누르고 3초 안에 길게 누른 뒤**, Android 화면잠금으로 인증해야 열람·비울 수 있습니다. 앱을 벗어나거나 로그 창을 닫으면 다시 잠깁니다. [관리자 진단 로그 사용법](docs/admin-diagnostics.md)을 참고하세요.

JDK 21, Gradle 9.3.1, Android SDK 35 및 Build Tools 36.0.0 환경에서 다음 명령으로 APK와 단위 테스트를 검증합니다.

```sh
gradle --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest
```

테스트에는 실제 v2 DB 마이그레이션, 캐시·편집본 보존, 곡 전환 경합, 번역 원문 보존, HTTP 취소 검증이 포함됩니다. 기기에서 음악 알림 접근·화면보호기 실행과 실제 Gemini 연동은 별도 확인이 필요합니다.

## 저사양·고사양 성능 설정

대시보드의 **기기 성능 설정** 또는 Apple Music 화면보호기의 **팔레트 → 기기 성능 설정**에서 선택합니다. 선택은 즉시 저장되며 앱과 시스템 화면보호기에 함께 적용됩니다. 최초에는 저메모리 기기 또는 RAM 3GB 이하이면 저사양 모드를 기본 선택합니다.

| 항목 | 저사양 | 고사양 |
| --- | --- | --- |
| 지속 화면 효과 갱신 목표 | 최대 30fps | 최대 60fps |
| 궤도 입자 / 스펙트럼 막대 | 8 / 18개 | 18 / 36개 |
| 파도 레이어 | 1개 | 3개 |
| 앨범 이미지 최대 변 길이 | 512px | 1024px |
| 배경 효과 | 작은 이미지·단순 그라데이션 | 앰비언트 오브·앨범 블러 |
| 장식 애니메이션 | 회전·호흡 효과 생략 | 회전·호흡 효과 유지 |

두 모드 모두 가사·번역·싱크·재생 조작은 유지합니다. VSync에 맞춰 효과를 갱신하고, 시간에 따라 진행량을 계산해 모드별 재생 속도가 달라지지 않도록 합니다. 이미지 축소는 UI 스레드 밖에서 수행하고, 그리기 경로·그라데이션은 재사용합니다. 화면이 꺼지거나 앱이 보이지 않으면 반복 효과와 진행 타이머를 중단합니다. 샘플 재생도 250ms마다 전역 상태를 갱신하지 않고 종료 시점만 예약합니다.

프레임 수치는 지속 효과의 갱신 예산이며 실제 기기의 프레임·발열·배터리 성능 보장은 아닙니다. 실제 저사양 기기에서의 장시간 사용 검증은 별도로 필요합니다.
