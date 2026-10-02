package com.example.data

import android.content.Context
import com.example.api.GeminiLyricsService
import com.example.service.MediaStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SampleDataProvider {

    private var playbackTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    const val DITTO_LRC = """
[00:00.00]Stay in the middle
[00:00.00]어중간한 자리에 머물며
[00:03.50]Like you a little
[00:03.50]널 조금 좋아하는 것 같아
[00:06.20]Don't want no riddle
[00:06.20]수수께끼 같은 건 싫어
[00:09.10]말해줘 say it back, oh, say it ditto
[00:09.10]내게도 같다고 답해줘
[00:13.80]아침은 너무 멀어 so say it ditto
[00:13.80]너의 마음을 지금 들려줘
[00:18.50]훌쩍 커버렸어 함께한 기억처럼
[00:23.50]널 보는 내 마음은 어느새 여름 지나 가을
[00:29.80]기다렸지 all this time
[00:32.40]Do you want somebody like I want somebody?
[00:32.40]내가 널 원하는 만큼 너도 누군가를 원하니?
[00:37.40]날 보고 웃었지만 do you think about me now?
[00:37.40]지금도 내 생각을 하고 있을까?
[00:43.90]All the time, yeah, all the time
[00:43.90]언제나, 항상
[00:48.20]I got no time to lose
[00:48.20]더 이상 망설일 시간이 없어
[00:51.80]내 길었던 하루, 난 보고 싶어
[00:56.50]Ra-ta-ta-ta 울린 심장
[01:00.80]I got nothing to lose
[01:00.80]두려울 건 아무것도 없어
[01:04.20]널 좋아한다고 ooh-woah
[01:09.10]Ra-ta-ta-ta 울린 심장
[01:13.50]But I don't want to stay in the middle
[01:13.50]하지만 애매한 채로 머물고 싶진 않아
[01:19.50]Like you a little
[01:22.20]Don't want no riddle
[01:25.10]말해줘 say it back, oh, say it ditto
[01:31.00]Say it back, oh, say it ditto
    """

    const val LOVE_WINS_ALL_LRC = """
[00:00.00]Dearest, darling, my universe
[00:00.00]가장 소중한 나의 그대, 나의 우주
[00:06.50]날 데려가 줄래?
[00:12.80]나의 이 가난한 상상력으론
[00:19.20]떠올릴 수 없는 곳으로
[00:26.50]저기 멀리 from Earth to Mars
[00:26.50]지구에서 저 먼 화성까지
[00:33.20]꼭 같이 가줄래?
[00:39.80]그곳이 어디든, 오랜 외로움
[00:46.50]그 반대말을 찾아서
[00:53.00]어떤 결말이 우리를 기다린대도
[01:02.50]Love wins all, love wins all
[01:02.50]결국 사랑이 모든 것을 이겨낼 거야
[01:12.00]Love wins all
    """

    const val CRUEL_SUMMER_LRC = """
[00:00.00]Fever dream high in the quiet of the night
[00:00.00]조용한 밤에 찾아온 열병 같은 꿈
[00:04.50]You know that I caught it
[00:04.50]너도 내가 빠져버린 걸 알잖아
[00:08.50]Bad, bad boy, shiny toy with a price
[00:08.50]나쁜 남자, 대가가 따르는 반짝이는 장난감
[00:12.80]You know that I bought it
[00:12.80]내가 그 값을 치른 걸 알잖아
[00:16.80]Killing me slow, out the window
[00:16.80]창밖을 보며 천천히 타들어가
[00:20.50]I'm always waiting for you to be waiting below
[00:20.50]아래에서 날 기다려주길 언제나 기다려
[00:25.20]Devils roll the dice, angels roll their eyes
[00:25.20]악마는 주사위를 굴리고 천사는 눈을 굴려
[00:29.80]What doesn't kill me makes me want you more
[00:29.80]날 죽이지 못하는 건 너를 더 원하게 만들어
[00:34.50]And it's new, the shape of your body
[00:34.50]새로워, 너의 그 실루엣
[00:37.20]It's blue, the feeling I've got
[00:37.20]푸르스름해, 내가 느끼는 이 감정
[00:40.80]And it's ooh, whoa, oh
[00:43.50]It's a cruel summer
[00:43.50]이건 너무나 잔인한 여름이야
[00:48.20]It's cool, that's what I tell 'em
[00:48.20]괜찮아, 사람들에겐 그렇게 말해
[00:52.00]No rules in unbreakable heaven
[00:52.00]깨어질 수 없는 천국엔 규칙 따윈 없어
[00:56.50]It's a cruel summer with you
[00:56.50]너와 함께하는 잔인한 여름
    """

    const val BIRDS_OF_A_FEATHER_LRC = """
[00:00.00]I want you to stay
[00:00.00]네가 곁에 머물렀으면 좋겠어
[00:04.50]'Til I'm in the grave
[00:04.50]내가 무덤에 묻힐 때까지
[00:09.20]'Til I rot away, dead and buried
[00:09.20]내가 썩어 흙이 될 때까지
[00:14.20]'Til I'm in the casket you carry
[00:14.20]네가 운구하는 관 속에 들어갈 때까지
[00:18.50]If you go, I'm going too, uh
[00:18.50]네가 떠난다면 나도 함께 갈 거야
[00:23.00]'Cause it was always you, alright
[00:23.00]언제나 너뿐이었으니까
[00:27.50]And if I'm turnin' blue, please don't save me
[00:27.50]내가 차갑게 식어가도 날 구하지 말아줘
[00:32.00]Nothing in this world could ever break we
[00:32.00]이 세상 어떤 것도 우릴 갈라놓을 수 없어
[00:37.00]Birds of a feather, we should stick together, I know
[00:37.00]같은 깃털을 가진 새들처럼, 우린 함께여야 해
[00:44.20]I said I'd never think I wasn't better alone
[00:44.20]혼자인 게 더 낫다고 생각했던 적은 결코 없었어
[00:51.50]Can't change the weather, might not be forever
[00:51.50]날씨는 바꿀 수 없고 영원하지 않을지도 모르지만
[00:58.20]But if it's forever, it's even better
[00:58.20]만약 영원할 수 있다면 더할 나위 없을 거야
    """

    fun getSampleSongs(): List<CachedLyrics> {
        val dittoLines = GeminiLyricsService.parseLrcLyrics(DITTO_LRC, 186000L)
        val dittoJson = GeminiLyricsService.lyricAdapter.toJson(dittoLines)

        val loveWinsLines = GeminiLyricsService.parseLrcLyrics(LOVE_WINS_ALL_LRC, 210000L)
        val loveWinsJson = GeminiLyricsService.lyricAdapter.toJson(loveWinsLines)

        val cruelSummerLines = GeminiLyricsService.parseLrcLyrics(CRUEL_SUMMER_LRC, 178000L)
        val cruelSummerJson = GeminiLyricsService.lyricAdapter.toJson(cruelSummerLines)

        val birdsLines = GeminiLyricsService.parseLrcLyrics(BIRDS_OF_A_FEATHER_LRC, 196000L)
        val birdsJson = GeminiLyricsService.lyricAdapter.toJson(birdsLines)

        return listOf(
            CachedLyrics(
                id = GeminiLyricsService.generateSongId("Ditto", "NewJeans"),
                title = "Ditto",
                artist = "NewJeans",
                lyricsJson = dittoJson,
                bpm = 134,
                hexColorsJson = "[\"#FF3B82F6\", \"#FF1E1B4B\", \"#FF06B6D4\", \"#FF4C1D95\"]"
            ),
            CachedLyrics(
                id = GeminiLyricsService.generateSongId("Love wins all", "IU"),
                title = "Love wins all",
                artist = "IU",
                lyricsJson = loveWinsJson,
                bpm = 85,
                hexColorsJson = "[\"#FFBE185D\", \"#FF4C0519\", \"#FF1E1B4B\", \"#FF831843\"]"
            ),
            CachedLyrics(
                id = GeminiLyricsService.generateSongId("Cruel Summer", "Taylor Swift"),
                title = "Cruel Summer",
                artist = "Taylor Swift",
                lyricsJson = cruelSummerJson,
                bpm = 170,
                hexColorsJson = "[\"#FFF59E0B\", \"#FFDC2626\", \"#FF7C3AED\", \"#FF0284C7\"]"
            ),
            CachedLyrics(
                id = GeminiLyricsService.generateSongId("BIRDS OF A FEATHER", "Billie Eilish"),
                title = "BIRDS OF A FEATHER",
                artist = "Billie Eilish",
                lyricsJson = birdsJson,
                bpm = 105,
                hexColorsJson = "[\"#FF059669\", \"#FF047857\", \"#FF065F46\", \"#FF111827\"]"
            )
        )
    }

    fun playSampleSong(song: CachedLyrics) {
        playbackTickerJob?.cancel()

        val parsedLines = GeminiLyricsService.parseJsonLyrics(song.lyricsJson)
        val durationMs = if (parsedLines.isNotEmpty()) {
            (parsedLines.last().timeSec * 1000).toLong() + 10000L
        } else {
            180000L
        }

        MediaStateHolder.updateState(
            title = song.title,
            artist = song.artist,
            album = "스마트 가사 체험 앨범",
            isPlaying = true,
            positionMs = 0L,
            durationMs = durationMs,
            packageName = "com.example.sample",
            speed = 1.0f,
            lyrics = null
        )

        // Live playback ticker simulating realistic track playback
        playbackTickerJob = scope.launch {
            var currentPos = 0L
            while (currentPos < durationMs) {
                delay(250)
                currentPos += 250L
                MediaStateHolder.setPlaybackPosition(currentPos)
            }
            MediaStateHolder.setPlayingState(false)
        }
    }

    fun stopSamplePlayback() {
        playbackTickerJob?.cancel()
        MediaStateHolder.clear()
    }
}
