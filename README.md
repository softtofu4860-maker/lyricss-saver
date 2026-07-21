<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# 스마트 음악 화면보호기 (Smart Music Screensaver)

재생 중인 시스템 음악을 정밀하게 분석하여 가사 싱크와 비주얼 연동을 띄워주는 미학적인 안드로이드 화면보호기입니다.

## 🎨 화면보호기 테마 종류 (Dual Themes)

화면보호기 화면을 한 번 탭하면 나타나는 컨트롤 바의 **팔레트(🎨) 아이콘**을 눌러 언제든지 테마를 전환할 수 있습니다.

1. **클래식 네온 테마 (Classic Neon Theme)**
   - 음악의 주파수와 비트에 맞춰 반응하는 네온 스펙트럼 비주얼라이저가 제공됩니다.
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
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

