# 4단계: 블로킹 라이브러리(DB 저장) 섞기: Dispatchers.IO, withContext, launch, CoroutineExceptionHandler

- 문제: 조회 결과를 블로킹 DB 라이브러리(800ms)로 저장하면 main 스레드가 멈춰 같은 스레드의 코루틴(화면 갱신)이 모두 멈춘다. 저장 실패가 다른 작업을 망가뜨리면 안 된다.
- 목표: 블로킹 호출은 `withContext(Dispatchers.IO)`로 IO 스레드에서, 결과를 기다릴 필요 없는 저장은 `launch`로 맡기고, 실패는 `SupervisorJob` + `CoroutineExceptionHandler`로 격리한다.
- 핵심 개념: `launch`/`Job`, `Dispatchers.IO`, `withContext`, `CoroutineScope(...)`, `SupervisorJob`, `CoroutineExceptionHandler`

## 강의 목차와 진행 상태

| 강의 | 파일 | 답할 질문 | 상태 |
|---|---|---|---|
| 1 | `Main.kt` [수정] | 세 가지 저장 방식을 어떻게 비교하나? 화면 갱신 코루틴은 무엇을 보여주나? | 완료 |
| 2 | `repository/SearchHistoryRepository.kt` [신규] | 블로킹 함수는 왜 main 스레드를 멈추나? | 완료 |
| 3 | `service/SearchHistoryRecorder.kt` [신규] | 블로킹을 어떻게 IO 스레드로 옮기고, 실패를 어떻게 격리하나? | 완료 |
| 마무리 | 실행 결과, 실험 | | 완료, `src/` 반영 (마무리 글은 생략하고 구현 우선 진행) |

순서를 고른 이유: 무대(Main) → 문제의 원인(블로킹 저장소) → 해결 도구(Recorder).

## 글별 그림 배치

| 글 | 위치 | 그림 파일 (`docs/diagrams/`) | 답하는 질문 |
|---|---|---|---|
| 도입 | 문제 설명 뒤 | `step04-0-1-blocking-freezes-main.json` | 블로킹 저장을 main에서 부르면 무엇이 멈추나, 목표는? |
| 도입 | 목차 앞 | `step04-0-2-file-map.json` | 이번 단계 파일과 강의 번호는? |
| 강의 1 | 세 저장 방식 서술 뒤 | `step04-1-1-three-ways-timeline.json` | 세 방식에서 main 스레드와 화면 갱신은 각각 어떻게 되나? |
| 강의 2 | `Thread.sleep` 서술 뒤 | `step04-2-1-who-gets-blocked.json` | 같은 `save()`인데 왜 어떤 때는 화면이 멈추고 어떤 때는 안 멈추나? |
| 강의 3 | `save()`(withContext) 서술 뒤 | `step04-3-1-withcontext-hop.json` | `withContext`는 코루틴을 어떻게 다른 스레드로 옮기나? |
| 강의 3 | `saveInBackground()` 서술 뒤 | `step04-3-2-recorder-scope.json` | 기록기 스코프는 runBlocking과 어떻게 다르고, 실패는 어디로 가나? |

## 검증 결과 (Claude 실행)

- ① 직접 저장: 화면 갱신 2(1879ms) → 3(2786ms), 약 900ms 멈춤. 저장 로그 스레드 `main @coroutine#1`.
- ② `withContext(Dispatchers.IO)`: 저장 로그 스레드 `DefaultDispatcher-worker-1 @coroutine#1`(같은 코루틴 #1, 다른 스레드). 화면 갱신은 200ms마다 계속.
- ③ `launch`: `바로 다음 작업` 로그가 1ms 뒤 찍힘. C는 실패 → 핸들러가 `기록 저장 실패 처리`, D는 정상 완료.
- 실험: 핸들러 없음 → `Exception in thread "DefaultDispatcher-worker-2 @coroutine#7"` 스택 트레이스 출력, 프로그램은 계속.
- 실험: `SupervisorJob()` 대신 `Job()` → C 실패 후 스코프가 취소되어 나중에 요청한 저장 E가 실행되지 않고 `isCancelled=true`. (D는 블로킹 `Thread.sleep` 중이라 취소가 먹히지 않아 완료됨)
- SupervisorJob에서는 저장 E가 정상 실행(`isCancelled=false`).
- 실험: `joinPending()`을 빼면 `백그라운드 저장 모두 끝`이 바로 찍히고 프로그램이 종료되어 저장 C·D 모두 `DB 저장 완료`/실패 처리 로그 없이 사라진다.

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlakyFlightProvider
import com.travel.repository.SearchHistoryRepository
import com.travel.service.PriceComparisonService
import com.travel.service.SearchHistoryRecorder
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

private val TIMEOUT = 1_500.milliseconds

fun main() {
    initLogging()
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")
    val service = PriceComparisonService(
        providers = listOf(
            FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
            FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
            FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
            FlakyFlightProvider("진에어", failAfterMs = 300),
        ),
    )
    val repository = SearchHistoryRepository(brokenLabels = setOf("저장 C"))
    val recorder = SearchHistoryRecorder(repository)

    runBlocking {
        val result = service.searchResilient(request, TIMEOUT)
        log("===== 조회 끝: 최저가 ${result.quotes.firstOrNull()?.provider ?: "없음"} =====")

        val screen = launch {
            var frame = 0
            while (true) {
                log("화면 갱신 ${++frame}")
                delay(200)
            }
        }
        delay(300)

        log("===== 1) main 스레드에서 직접 저장 =====")
        repository.save("저장 A", result)

        log("===== 2) withContext(Dispatchers.IO)로 저장 =====")
        recorder.save("저장 B", result)

        log("===== 3) launch로 맡기고 바로 다음 일 =====")
        recorder.saveInBackground("저장 C", result)
        recorder.saveInBackground("저장 D", result)
        log("저장을 맡겼으니 바로 다음 작업 진행")

        recorder.joinPending()
        log("===== 백그라운드 저장 모두 끝 =====")
        screen.cancel()
    }
}
```

### `src/main/kotlin/com/travel/repository/SearchHistoryRepository.kt`

```kotlin
package com.travel.repository

import com.travel.model.SearchResult
import com.travel.util.log

class SearchHistoryRepository(
    private val brokenLabels: Set<String> = emptySet(),
) {

    fun save(label: String, result: SearchResult) {
        log("DB 저장 시작: $label")
        Thread.sleep(800)
        if (label in brokenLabels) {
            throw IllegalStateException("DB 연결 끊김 ($label)")
        }
        log("DB 저장 완료: $label (견적 ${result.quotes.size}건)")
    }
}
```

### `src/main/kotlin/com/travel/service/SearchHistoryRecorder.kt`

```kotlin
package com.travel.service

import com.travel.model.SearchResult
import com.travel.repository.SearchHistoryRepository
import com.travel.util.log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchHistoryRecorder(
    private val repository: SearchHistoryRepository,
) {

    private val handler = CoroutineExceptionHandler { _, e ->
        log("기록 저장 실패 처리: ${e.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    suspend fun save(label: String, result: SearchResult) {
        withContext(Dispatchers.IO) {
            repository.save(label, result)
        }
    }

    fun saveInBackground(label: String, result: SearchResult): Job =
        scope.launch {
            repository.save(label, result)
        }

    suspend fun joinPending() {
        scope.coroutineContext.job.children.forEach { it.join() }
    }
}
```
