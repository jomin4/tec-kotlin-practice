# 8단계: 시간이 걸리는 코드를 빠르게 테스트: runTest, 가상 시간

- 문제: 지금까지 코드는 1.5초 제한, 30초 응답 없음, 주기적 감시처럼 실제 시간을 기다린다. 테스트가 그 시간을 그대로 기다리면 느리고, 시간을 검증할 수도 없다.
- 목표: `runTest`의 가상 시간으로 `delay`를 즉시 건너뛰면서, `currentTime`으로 "몇 ms가 걸렸어야 하는지"까지 검증한다.
- 핵심 개념: `runTest`, 가상 시간과 `currentTime`, `advanceTimeBy`/`runCurrent`, `backgroundScope`, `assertFailsWith`
- 상태: 실습 완료

## 강의 목차

| 강의 | 파일 | 답할 질문 |
|---|---|---|
| 1 | `src/test/.../service/PriceComparisonServiceTest.kt [신규]` | 1~3단계 조회 방식을 걸린 가상 시간까지 어떻게 검증하나? |
| 2 | `src/test/.../service/FlowTest.kt [신규]` | 끝나지 않는 흐름과 StateFlow는 시간을 조금씩 흘려 가며 어떻게 검증하나? |
| 3 | `src/test/.../provider/ConcurrencyTest.kt [신규]` | Semaphore와 Channel 작업자의 동시성은 어떻게 검증하나? |

순서를 고른 이유: 단계 순서(1~3단계 조회 → 5·6단계 흐름 → 7단계 동시성)대로 테스트를 읽는다.

## 검증 결과 (Claude 실행)

```
PASSED  ConcurrencyTest > Semaphore는 동시 요청을 허가 수만큼으로 묶는다 (206ms)
PASSED  ConcurrencyTest > Channel 작업자 3명이 요청 7건을 나눠 처리한다 (29ms)
PASSED  FlowTest > 최저가 추적기는 시간이 흐르면 최신 최저가로 바뀐다 (31ms)
PASSED  FlowTest > 감시 흐름은 간격마다 새 가격을 내보낸다 (8ms)
PASSED  PriceComparisonServiceTest > 병렬 조회는 가장 느린 항공사만큼 걸린다 (6ms)
PASSED  PriceComparisonServiceTest > coroutineScope 조회는 한 곳이 실패하면 전체가 실패한다 (12ms)
PASSED  PriceComparisonServiceTest > supervisorScope 조회는 실패한 곳만 실패 목록에 넣는다 (11ms)
PASSED  PriceComparisonServiceTest > 가상 시간 덕분에 30초 기다림도 실제로는 금방 끝난다 (6ms)
PASSED  PriceComparisonServiceTest > 순차 조회는 지연 시간의 합만큼 걸린다 (4ms)
PASSED  PriceComparisonServiceTest > 제한 시간을 넘긴 항공사는 시간 초과 목록에 들어간다 (5ms)
총 10개 테스트 통과, 실패 0 / 가상 시간 30,000ms 테스트의 실제 걸린 시간 2ms
```

- `runTest` 안의 `delay`, `withTimeoutOrNull`은 가상 시간으로 동작한다. 순차 4,200ms / 병렬 1,200ms / 제한 1,500ms를 `currentTime`으로 정확히 확인.
- `coroutineScope` 조회의 전체 실패는 `assertFailsWith<ProviderException>`으로, 실패 시각 300ms까지 검증.
- 6단계 추적기는 `stateIn`이 끝나지 않는 코루틴을 띄우므로 `backgroundScope`에 두었다. 테스트가 끝나면 자동으로 취소된다. `advanceTimeBy` + `runCurrent`로 시간을 조금씩 흘려 가며 `value`를 확인.
- 4단계 `SearchHistoryRecorder`(`Dispatchers.IO` + `Thread.sleep`)와 2단계 `HeavyParsingFlightProvider`(실제 CPU 시간)는 가상 시간으로 건너뛸 수 없어 테스트에서 뺐다. 테스트하려면 Dispatcher를 생성자로 주입해 테스트 Dispatcher로 바꾸는 설계가 필요하다.
- 환경 메모: 이 컨테이너의 파일 시스템 인코딩이 한글 클래스 파일 이름을 못 써서, 백틱 한글 함수 이름 대신 영문 함수 이름 + `@DisplayName("한글 설명")`을 쓴다.

## 코드 스냅샷

### `src/test/kotlin/com/travel/service/PriceComparisonServiceTest.kt`

```kotlin
package com.travel.service

import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlakyFlightProvider
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PriceComparisonServiceTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    private val fiveAirlines = listOf(
        FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
        FakeFlightProvider("아시아나", latencyMs = 800, price = 398_000),
        FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
        FakeFlightProvider("진에어", latencyMs = 1_200, price = 275_000),
        FakeFlightProvider("티웨이", latencyMs = 700, price = 301_000),
    )

    @Test
    @DisplayName("순차 조회는 지연 시간의 합만큼 걸린다")
    fun sequentialTakesSumOfLatencies() = runTest {
        val quotes = PriceComparisonService(fiveAirlines).searchSequentially(request)

        assertEquals(4_200, currentTime)
        assertEquals("진에어", quotes.first().provider)
    }

    @Test
    @DisplayName("병렬 조회는 가장 느린 항공사만큼 걸린다")
    fun concurrentTakesSlowestLatency() = runTest {
        val quotes = PriceComparisonService(fiveAirlines).searchConcurrently(request)

        assertEquals(1_200, currentTime)
        assertEquals(listOf(275_000, 289_000, 301_000, 398_000, 420_000), quotes.map { it.price })
    }

    @Test
    @DisplayName("제한 시간을 넘긴 항공사는 시간 초과 목록에 들어간다")
    fun slowProviderGoesToTimedOut() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
            ),
        )

        val result = service.searchWithTimeout(request, 1_500.milliseconds)

        assertEquals(1_500, currentTime)
        assertEquals(listOf("제주항공"), result.quotes.map { it.provider })
        assertEquals(listOf("티웨이"), result.timedOut)
    }

    @Test
    @DisplayName("coroutineScope 조회는 한 곳이 실패하면 전체가 실패한다")
    fun coroutineScopeFailsWholeSearch() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FlakyFlightProvider("진에어", failAfterMs = 300),
            ),
        )

        assertFailsWith<ProviderException> { service.searchWithTimeout(request, 1_500.milliseconds) }
        assertEquals(300, currentTime)
    }

    @Test
    @DisplayName("supervisorScope 조회는 실패한 곳만 실패 목록에 넣는다")
    fun supervisorScopeIsolatesFailure() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
                FlakyFlightProvider("진에어", failAfterMs = 300),
            ),
        )

        val result = service.searchResilient(request, 1_500.milliseconds)

        assertEquals(1_500, currentTime)
        assertEquals(listOf("제주항공", "대한항공"), result.quotes.map { it.provider })
        assertEquals(listOf("티웨이"), result.timedOut)
        assertEquals(listOf("진에어"), result.failed)
    }

    @Test
    @DisplayName("가상 시간 덕분에 30초 기다림도 실제로는 금방 끝난다")
    fun virtualTimeSkipsLongWaits() {
        val startedAt = System.nanoTime()
        runTest {
            val service = PriceComparisonService(listOf(FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000)))

            service.searchWithTimeout(request, 60.seconds)

            assertEquals(30_000, currentTime)
        }
        val realMs = (System.nanoTime() - startedAt) / 1_000_000
        println("가상 시간 30,000ms / 실제 걸린 시간 ${realMs}ms")
        assertTrue(realMs < 10_000)
    }
}
```

### `src/test/kotlin/com/travel/service/FlowTest.kt`

```kotlin
package com.travel.service

import com.travel.model.SearchRequest
import com.travel.provider.ScriptedFlightProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class FlowTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    @Test
    @DisplayName("감시 흐름은 간격마다 새 가격을 내보낸다")
    fun watcherEmitsPricePerInterval() = runTest {
        val watcher = PriceWatcher(
            ScriptedFlightProvider("제주항공", latencyMs = 100, prices = listOf(289_000, 289_000, 279_000, 265_000)),
            interval = 300.milliseconds,
        )

        val prices = watcher.watch(request)
            .map { it.price }
            .distinctUntilChanged()
            .take(3)
            .toList()

        assertEquals(listOf(289_000, 279_000, 265_000), prices)
        assertEquals(100 + 400 + 400 + 400, currentTime.toInt())
    }

    @Test
    @DisplayName("최저가 추적기는 시간이 흐르면 최신 최저가로 바뀐다")
    fun trackerFollowsLatestLowest() = runTest {
        val feeds = listOf(
            PriceWatcher(ScriptedFlightProvider("대한항공", 50, listOf(420_000, 395_000, 380_000, 260_000)), 400.milliseconds),
            PriceWatcher(ScriptedFlightProvider("제주항공", 50, listOf(289_000, 289_000, 299_000, 275_000)), 300.milliseconds),
            PriceWatcher(ScriptedFlightProvider("티웨이", 50, listOf(301_000, 270_000, 285_000)), 500.milliseconds),
        ).map { it.watch(request) }
        val tracker = LowestPriceTracker(feeds, backgroundScope)

        assertNull(tracker.lowest.value)

        advanceTimeBy(51); runCurrent()
        assertEquals("제주항공", tracker.lowest.value?.provider)

        advanceTimeBy(550); runCurrent()
        assertEquals("티웨이", tracker.lowest.value?.provider)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(260_000, tracker.lowest.value?.price)
    }
}
```

### `src/test/kotlin/com/travel/provider/ConcurrencyTest.kt`

```kotlin
package com.travel.provider

import com.travel.model.SearchRequest
import com.travel.service.SearchQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals

class ConcurrencyTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    @Test
    @DisplayName("Semaphore는 동시 요청을 허가 수만큼으로 묶는다")
    fun semaphoreLimitsConcurrency() = runTest {
        val fragile = FragileFlightProvider("제주항공", maxConcurrent = 2, latencyMs = 300, price = 289_000)
        val limited = RateLimitedFlightProvider(fragile, permits = 2)

        val quotes = (1..6).map { async { limited.search(request) } }.awaitAll()

        assertEquals(6, quotes.size)
        assertEquals(2, fragile.peakConcurrent)
        assertEquals(900, currentTime)
    }

    @Test
    @DisplayName("Channel 작업자 3명이 요청 7건을 나눠 처리한다")
    fun channelWorkersShareRequests() = runTest {
        val queue = SearchQueue(FakeFlightProvider("대한항공", latencyMs = 300, price = 420_000), workerCount = 3)
        val destinations = listOf("NRT", "KIX", "FUK", "CTS", "OKA", "TPE", "BKK")

        val results = queue.processAll(destinations.map { request.copy(to = it) })

        assertEquals(destinations.toSet(), results.map { it.first }.toSet())
        assertEquals(900, currentTime)
    }
}
```
