# 가사 엔진 업데이트 참고 자료

2026-10-14에 아래 다섯 저장소의 README, 포맷 설명 및 라이선스를 확인했습니다. 기능과 공개 포맷을 참고하여 Android/Kotlin으로 독립 구현했습니다. 외부 소스 코드, 이미지, 음악·가사 데이터, 웹 렌더러를 앱에 복사하거나 의존성으로 추가하지 않았습니다.

| 프로젝트 · 확인한 커밋 | 확인한 내용 | 이번 앱에 적용한 내용 | 라이선스 확인 |
| --- | --- | --- | --- |
| [Better Lyrics](https://github.com/better-lyrics/better-lyrics/tree/0df422e9d1e689baaa8f38a0823914f42f4652a0) | 다중 가사 소스, 번역·발음, 곡별 오프셋, 경량 표시 옵션 | 기존 소스 선택을 유지하고 곡별 보정값을 영구 저장 | GPL-3.0 |
| [LyricProvider](https://github.com/tomakino/LyricProvider/tree/be4c131f9303966e2bb146c3e7c0665cf69585cb) | Android 제공자별 데이터 수집, 원문·번역·단어 타이밍 분리 | 가사 모델에 단어 타이밍을 추가하고 원문·번역·발음을 함께 보존 | Apache-2.0 |
| [163MusicLyrics](https://github.com/jitwxs/163MusicLyrics/tree/26d8a73471ea88ab6d4c97406853391b2fadad19) | 링크/ID 정확 조회, 검색 후보 직접 선택 | NetEase 곡 ID와 공식 곡 링크로 정확 조회; 선택한 ID로 가사 로드 | Apache-2.0 |
| [Apple Music-like Lyrics](https://github.com/amll-dev/applemusic-like-lyrics/tree/86200dead453bb067e554e989110cbadca8d4756) | 단어 타이밍 모델, LRC/YRC 등 다양한 포맷, 동기화 표시 | Enhanced LRC/YRC 파싱과 활성 줄 단어 강조, 저사양 줄 단위 표시 | AGPL-3.0 |
| [Lyricify App](https://github.com/WXRIW/Lyricify-App/tree/e26aae5401493d90294fb272688e07f3c3ce865f) | `docs/Lyricify 4/Lyrics.md`의 YRC 포맷, 단어별 표시·싱크 보정 UX | YRC 절대 타이밍 처리, 보정값 적용 후 가사 클릭 시 이동 위치 보정 | 앱 저장소 전체에 대한 LICENSE 파일 없음. README는 일부 별도 라이브러리의 Apache-2.0과 특정 독창적 기능의 CC BY-SA-4.0을 구분해서 설명 |

LyricProvider의 Xposed/LSPosed 모듈이나 타 앱 후킹 기능은 포함하지 않습니다. Lyricify의 Dynamic Lyrics Island, Magic Strip 등 독창적 기능도 포함하지 않습니다.

## 사용 방법

- 가사 교정 → DB 가사 검색 → NetEase에서 키워드, 양의 정수 곡 ID 또는 `https://music.163.com/#/song?id=12345` 같은 공식 곡 링크를 입력합니다. 단축 URL과 앨범·플레이리스트 URL은 지원하지 않습니다.
- 가사 교정 → 직접 편집에서 LRC, Enhanced LRC, YRC를 입력할 수 있습니다. 예: `[00:01]<00:01>Hi <00:02>there<00:03>` 또는 `[1000,2000](1000,500,0)Hi (1500,1500,0)there`.
- 고사양 모드의 Apple Music 가사 화면은 단어 타이밍이 있는 활성 줄에서 현재 단어를 청록색으로 표시합니다. 저사양은 줄 단위 강조를 사용합니다. 일반 LRC에 단어 타이밍을 임의 생성하지 않습니다.
- 싱크 보정은 제목+아티스트별로 ±10초 안에서 저장됩니다. 앱·화면보호기에 공유되며 곡 변경 시 해당 곡의 값으로 전환합니다. 0으로 초기화할 수 있습니다.
- `[offset:250]`은 가사를 250ms 일찍 표시합니다. 여러 타임스탬프가 있는 줄은 각 시간에 표시하고, 가까운 시간의 서로 다른 원문 줄을 번역으로 합치지 않습니다.

## 검증과 범위

포맷 파싱, 기존 JSON 캐시 호환, 단어 타이밍 저장, URL/ID 식별, 곡별 보정 저장, 고사양/저사양 표시 분기를 자동 테스트로 검증합니다. 단어 표시용 갱신은 활성 줄에만 적용하고 기존 화면 비활성화·일시정지 시 갱신 중단 규칙을 따릅니다.

현재 클라우드 환경의 프록시는 `music.163.com` 요청에 403을 반환하므로 실제 NetEase 조회 성공 여부는 검증하지 못했습니다. 서버가 YRC를 반환하는 경우 단어 타이밍을 사용하며, 없으면 기존 LRC로 표시합니다. TTML/QRC/LYS, 듀엣·배경 보컬 렌더링, 웹 확장 테마와 외부 계정 연동은 이번 구현 범위에 포함하지 않습니다.
