# 가사·번역·음악 연동 유지보수 검증 보고서

## 1. 원본에서 확인한 문제와 분석 정정

검토 대상은 이 저장소의 원본 Kotlin/Compose 코드와 이 환경에서 빌드한 APK입니다. 이번 요청에 별도로 업로드된 제3의 APK 파일은 없어 다른 빌드와의 동일성 비교는 하지 않았습니다. 디컴파일 결과만으로 결함을 확정하지 않았습니다.

확인한 결함:

- 설정의 개인 키와 Gemini 요청 키가 분리되어 있었고, 빌드 키가 없으면 실패를 알리지 않고 원문을 반환했습니다.
- 일반 가사에도 줄 수로 나눈 임의 타임스탬프가 부여되어 실제 싱크와 구별할 수 없었습니다.
- MediaSession 위치 갱신 시각을 버리고 벽시계로 경과 시간을 계산했습니다.
- Spotify 방송이 검증 없이 상태를 교체하고 manifest·동적 수신으로 중복될 수 있었습니다.
- 모든 알림에서 세션을 다시 조회·재등록했습니다. 연결 해제 시 활성 컨트롤러와 상태 정리가 불완전했습니다.
- 개인 키 평문 저장과 백업 제외 누락, 실제 측정이 아닌 BPM·주파수 표현, 버전별 저장 항목 충돌 가능성을 확인했습니다.
- 알송은 현재 실행 경로에서 사용하지 않는데 HTTP 예외만 남아 있었습니다.

정정·유지한 부분:

- `RECEIVER_EXPORTED` 값 2를 비공개 수신으로 해석하지 않았습니다. 이번에는 필요성이 없는 Spotify 방송 경로 자체를 제거했습니다.
- 이전 곡 작업 취소와 generation 검사, 원문/번역 필드 분리는 이미 있었습니다. 이를 유지하면서 명확한 번역 상태를 추가했습니다.
- 제목·가수 SHA-256 식별자는 유니코드와 필드 경계를 이미 보존했습니다. 해시 자체의 문제가 아니라 같은 이름의 녹음 버전 구분을 보완했습니다.
- 알림 접근 서비스와 DreamService의 `exported=true` 및 시스템 바인딩 권한은 시스템 연결에 필요하므로 유지했습니다.
- 기존 타임스탬프가 실제인지 추정치인지 출처 없이 완벽히 복원할 수는 없습니다. 이전 JSON은 시간을 파괴하지 않고 `UNKNOWN`으로 읽어 자동 싱크·탐색을 중단합니다.

## 2. 변경 파일과 수정 이유

| 파일/영역 | 변경 이유 |
| --- | --- |
| `api/GeminiTranslationClient.kt`, `api/CancellableHttp.kt`, `api/GeminiLyricsService.kt` | 개인 키를 헤더에 사용, HTTP 상태 보존, 오류 분류, 취소 가능한 요청, 원문으로 위장한 성공 제거 |
| `data/PersonalApiKeyStore.kt`, `LyricsApplication.kt`, `ui/ApiKeySettings.kt` | Keystore AES-GCM 저장, 시작 시 기존 평문 이전, 삭제, 마스킹 입력·저장 실패 안내 |
| `ui/MainViewModel.kt`, `data/LyricsTranslation.kt` | 번역 중에도 원문 유지, 오류 안내, 곡 변경 방어, 원문 타이밍 보존, 일반 가사의 줄별 번역 대응 |
| `data/LyricLine.kt`, `data/LyricsParser.kt`, `ui/LyricsTimingControls.kt` | 싱크/일반/추정/기존 출처 불명 구분과 JSON 저장, 추정 스크롤 명시적 선택 |
| `ui/MainScreen.kt`, `AppleMusicScreensaverView.kt`, `AppleMusicLyricsView.kt`, `LyricsView.kt`, `TimedLyricText.kt` | 상태 표시·강조·탐색 제한, 추정 절/후렴 북마크 제거, 잘못된 기능 설명 수정, 자동 데모 시작 제거 |
| `service/MediaStateHolder.kt` | 단조 시계·기준 위치/시각·배속·위치 불명·범위 제한 |
| `service/MusicNotificationListener.kt`, `SessionSelection.kt` | 세션 토큰별 안정적인 콜백, 활성 세션 선택, 늦은 이벤트 방어, 관련 없는 알림 처리 제거, 연결 해제 정리 |
| `SpotifyBroadcastReceiver.kt` 제거, `MainActivity.kt`, `LyricsScreensaverDreamService.kt`, manifest | 외부 방송을 신뢰하는 상태 경로와 manifest/동적 중복 등록 제거 |
| `data/SongDescriptor.kt`, `CachedLyrics.kt`, `LyricsDao.kt`, `LyricsDatabase.kt`, `LyricsRepository.kt` | 제공처 ID/앨범/길이 기반 버전 구분, DB v4, 보호된 기존 데이터를 원본 보존 후 한 버전에만 연결 |
| `data/LyricsSyncSettings.kt`, `ui/LyricsSyncState.kt` | 곡 버전별 보정값, 기존 값은 첫 버전에만 이전하고 원본 유지 |
| `app/build.gradle.kts`, `res/xml/backup_rules.xml`, `data_extraction_rules.xml`, `network_security_config.xml` | 빌드 비밀 키 제거, release 비디버그·외부 서명 설정, 키 파일 백업/이전 제외, HTTP 예외 제거 |
| 관련 `app/src/test` 및 README | 동작 회귀·보안·데이터 보존 테스트와 사용자 설명 갱신 |

## 3. 항목별 완료 여부

| 요청 | 코드 반영 | 남은 확인 |
| --- | --- | --- |
| 1. 개인 키 번역 연결 | 완료: 실제 요청 헤더, 미설정/인증/한도/네트워크/서버/빈 결과 분류, 실패 시 원문 유지 | 실제 개인 계정으로 Gemini 호출 미실행 |
| 2. 싱크·일반·추정 구분 | 완료: 기본 일반 표시, 추정은 사용자 선택, 출처·타이밍 JSON 유지, 추정 탐색 금지 | 실기기 디자인·스크롤 확인 |
| 3. 재생 위치 | 완료: 세션 `lastPositionUpdateTime`과 `elapsedRealtime`, 상태별 정지·배속·경계 처리 | 실제 음악 앱에서 위치 정확도 측정 |
| 4. Spotify 상태 통합 | 완료: MediaSession 단일 외부 공급원, 방송 제거, 활성 앱 선택·늦은 이벤트 방어 | 실제 Spotify/다른 앱 교대 재생 |
| 5. 개인 키 보호 | 완료: Keystore AES-GCM, 평문 이전 후 삭제, 백업 제외, 키 삭제, URL·로그 제외 | 실제 AndroidKeyStore/하드웨어·기기 이전 확인 |
| 6. 알림/세션 처리 | 완료: 세션 구성 변경 중심, 유지 중인 콜백 재사용, 연결 해제·종료 시 정리 | 실제 권한 회수/복구 흐름 |
| 7. 기능 설명 | 완료: 효과 속도/장식 애니메이션으로 표시, 번역 키 저장과 유효성 분리 | 실기기 전체 UI 확인 |
| 8. 버전별 식별 | 완료: 제공처 ID 우선, 앨범·길이 보완, 보호 데이터·보정값 최초 연결 및 원본 보존 | 제공처가 구분 데이터를 주지 않는 동일 이름·앨범·길이 녹음은 구별 불가 |
| 9. 배포 준비 | 설정 완료: release 디버깅 금지, 서명 비밀은 환경 변수, HTTP 제거 | 정식 인증서·비밀번호가 없어 정식 서명/설치 배포 미완료 |

## 4. 빌드·테스트와 필수 검증

자동 검증은 JDK 21 / Gradle 9.3.1 / Android SDK 35 및 Robolectric API 36 환경에서 수행합니다. 테스트 대상 Android API는 Robolectric 모의 환경이며 실제 음악 앱의 실행 결과와 구분합니다. 네트워크 테스트는 MockWebServer와 실패 주입을 사용합니다. AES-GCM 저장 테스트는 실제 JCE 암호화를 주입하며 실제 AndroidKeyStore 하드웨어를 시험한 것이 아닙니다.

실행 명령:

```sh
gradle --no-daemon --max-workers=2 :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
```

검증 결과: 자동 테스트 **95개 통과, 실패/오류 0개**. debug APK 빌드·서명 검증과 unsigned release APK 빌드·release vital lint가 통과했습니다. 패키징된 APK 검사에서 debug는 `debuggable=true`, release는 `debuggable=false`이며, release에는 서명이 없습니다. 두 APK의 DEX에서 Google API 키 형태의 하드코딩 값은 발견되지 않았습니다. 백업 규칙의 두 키 관련 설정 파일 제외와 cleartext 비허용도 확인했습니다.

| 필수 검증 | 자동 검증 결과 | 실기기 결과 |
| --- | --- | --- |
| 곡이 이미 재생 중일 때 앱 실행 | 통과: MediaSession 초기 스냅샷·지난 시간 위치 반영 | 미실행 |
| 일시정지 후 재개 | 통과: 상태 콜백·새 기준 위치/시각·배속 반영 | 미실행 |
| 앞뒤 구간 탐색과 다음 곡 전환 | 통과: 위치 기준점·VM 곡 변경/늦은 결과 방어 | 미실행 |
| Spotify와 다른 음악 앱 전환 | 통과: 서로 다른 모의 세션 토큰으로 선택·이전 이벤트 방어 | 미실행 |
| 일반 가사 표시 | 통과: PLAIN은 강조·탐색 안 함, 추정 opt-in, 캐시 왕복 | 미실행 |
| 정상·잘못된·미설정 키 번역 | 통과: 저장한 키가 요청 헤더에 실제 전달됨, 400/401/403·429·미설정 분리 | 미실행: 실제 Gemini 계정·키 미제공 |
| 번역 중 곡 변경 | 통과: 취소·세대 검사로 다음 곡/캐시 오염 방지 | 미실행 |
| 인터넷 끊김·서버 오류 | 통과: IOException·503·빈 응답, 원문 보존·상태 안내 | 미실행: 실제 기기 망 전환 |
| 알림 접근 권한 회수 후 재허용 | 통과: 서비스 disconnect/connect 모의 호출로 콜백/참조 해제·복구 | 미실행: 시스템 설정에서 권한 변경 |
| 화면보호기 진입·종료 반복 | 미실행: 실제 Dream 윈도 부착/분리; 기존 효과 lifecycle 중단 테스트는 통과 | 미실행 |
| 기존 가사·수정 데이터 보존 | 통과: 실제 SQLite v2→v4, 번역/보호 항목/최초 버전 연결/보정값 보존 | 미실행: 사용자 실데이터 업그레이드 |
| 개인 키 평문 저장·로그 노출 | 통과: AES-GCM 암호문·평문 제거·삭제·이전 실패 보존·헤더/URL/로그 검사 | 미실행: 실제 Keystore/백업·이전 |

모의 MediaSession은 Robolectric의 빈 Binder 토큰 대신 서로 다른 테스트 Binder를 부여하고 메인 Looper를 진행해 Android 콜백을 처리했습니다. 이 테스트의 성공을 실제 Spotify와의 호환 인증으로 해석하지 않습니다.

## 5. 미해결·추가 확인과 이유

- 연결된 기기가 없으며 `adb devices -l` 결과가 비어 있습니다. 실기기 음악 재생, 화면보호기 윈도, 권한 설정, 시스템 인증, Keystore 하드웨어 동작을 정상이라고 보고하지 않습니다.
- 실제 개인 Gemini 키를 제공받지 않았습니다. 유효한 계정·프로젝트·지역·과금/한도에서의 실서비스 성공은 미실행입니다.
- 배포용 인증서와 비밀번호가 없습니다. unsigned release는 설치 배포본이 아닙니다. 제공하는 APK는 기존과 같은 개발용 debug 서명입니다. 실제 서명 비밀값을 저장소에 넣거나 새 정식 키를 임의 생성하지 않았습니다.
- 이전 캐시에 시간 출처가 없으면 정확한 싱크였는지 복원할 수 없습니다. 출처 미확인으로 보수적으로 처리하고 원본 시간·본문·번역을 보존했습니다.
- 같은 제목·가수·앨범·길이이며 제공처 ID까지 없는 녹음은 완벽히 구분할 수 없습니다. 오래된 보호 항목을 모든 새 버전에 복사하지 않고 첫 버전에만 연결합니다.
- 제공처가 가사나 일관된 곡 메타데이터를 제공하지 않는 경우 자동 검색·싱크의 성공을 보장할 수 없습니다. 진단 로그로 실패 상태를 확인할 수 있습니다.
