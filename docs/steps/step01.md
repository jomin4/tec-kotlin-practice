# 1단계: 순차 호출 vs async 병렬 호출

- 목표: 같은 5개 조회를 순차/병렬로 실행해 시간 차이(≈4200ms vs ≈1200ms)를 확인한다.
- 핵심 개념: `suspend`, `runBlocking`, `coroutineScope`, `async`/`awaitAll`, `delay`가 스레드를 놓아준다는 점
- Claude가 작성한 환경 코드: `model/Flight.kt`, `util/Log.kt`, Gradle 설정

## 글 구성 (그림 배치)

| 순서 | 위치 | 그림 파일 (`docs/diagrams/`) | 그림이 답하는 질문 |
|---|---|---|---|
| 1 | 도입, 단계 목표 뒤 | `step01-1-file-map.json` | 어떤 파일이 있고 누가 누구를 부르나? |
| 2 | ① Main.kt, `runBlocking` 설명 직전 | `step01-2-run-blocking.json` | 일반 함수 `main`과 코루틴은 어떻게 이어지나? |
| 3 | ② Service, 순차 조회 설명 뒤 | `step01-3-sequential.json` | 순차 조회는 왜 시간이 합이 되나? |
| 4 | ② Service, 병렬 조회 3단계 설명 뒤 | `step01-4-coroutine-tree.json` | `coroutineScope`/`async`/`Deferred`/`awaitAll`의 관계는? |
| 5 | ④ FakeFlightProvider, `delay` 설명 뒤 | `step01-5-thread-interleave.json` | 스레드 1개로 어떻게 5개가 동시에 기다리나? |
| 6 | 실험(선택) 섹션 | `step01-6-delay-vs-sleep.json` | `delay` 대신 `Thread.sleep`이면 왜 느려지나? |

## 사용자가 입력한 파일 (제공 코드 원본, 리뷰 기준)

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.service.PriceComparisonService
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.runBlocking
import kotlin.time.measureTimedValue

fun main() {
    initLogging()

    val service = PriceComparisonService(
        providers = listOf(
            FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
            FakeFlightProvider("아시아나", latencyMs = 800, price = 398_000),
            FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
            FakeFlightProvider("진에어", latencyMs = 1_200, price = 275_000),
            FakeFlightProvider("티웨이", latencyMs = 700, price = 301_000),
        ),
    )
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    runBlocking {
        compare("순차 조회") { service.searchSequentially(request) }
        compare("병렬 조회") { service.searchConcurrently(request) }
    }
}

private suspend fun compare(label: String, search: suspend () -> List<FlightQuote>) {
    log("===== $label 시작 =====")
    val (quotes, elapsed) = measureTimedValue { search() }
    val cheapest = quotes.first()
    log("===== $label 끝: ${elapsed.inWholeMilliseconds}ms / 최저가 ${cheapest.provider} ${"%,d".format(cheapest.price)}원 =====")
}
```

### `src/main/kotlin/com/travel/service/PriceComparisonService.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FlightProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PriceComparisonService(
    private val providers: List<FlightProvider>,
) {

    suspend fun searchSequentially(request: SearchRequest): List<FlightQuote> =
        providers
            .map { provider -> provider.search(request) }
            .sortedBy { it.price }

    suspend fun searchConcurrently(request: SearchRequest): List<FlightQuote> = coroutineScope {
        providers
            .map { provider -> async { provider.search(request) } }
            .awaitAll()
            .sortedBy { it.price }
    }
}
```

### `src/main/kotlin/com/travel/provider/FlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest

interface FlightProvider {
    val name: String

    suspend fun search(request: SearchRequest): FlightQuote
}
```

### `src/main/kotlin/com/travel/provider/FakeFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.delay

class FakeFlightProvider(
    override val name: String,
    private val latencyMs: Long,
    private val price: Int,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 조회 시작")
        delay(latencyMs)
        log("$name 조회 완료 (${latencyMs}ms)")
        return FlightQuote(provider = name, price = price)
    }
}
```
