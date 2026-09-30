# 3단계: 하나가 실패하면 전체가 죽는 문제: 예외 전파와 supervisorScope

- 문제: `coroutineScope` 안에서 자식 하나가 예외로 실패하면 형제가 모두 취소되고 예외가 밖으로 나가 조회 전체가 실패한다.
- 목표: `supervisorScope`와 `await`별 예외 처리로 실패한 항공사만 "실패 목록"에 넣고 나머지 결과는 살린다.
- 핵심 개념: 예외 전파(자식 → 부모 → 형제 취소), `supervisorScope`, `await()`가 예외를 다시 던지는 규칙
- 환경 코드: `model/Flight.kt`에 `SearchResult.failed`, `ProviderAnswer`(sealed interface), `ProviderException` 추가
- 로드맵 조정: `CoroutineExceptionHandler`는 `launch`와 함께 4단계로 옮김(`async`에서는 쓰이지 않으므로)

## 강의 목차와 진행 상태

| 강의 | 파일 | 답할 질문 | 상태 |
|---|---|---|---|
| 1 | `Main.kt` [수정] | 무엇을 비교하고, 전체 실패는 어디서 잡나? | 완료 |
| 2 | `provider/FlakyFlightProvider.kt` [신규] | 예외는 어디서 태어나 어디로 가나? | 설명 완료, 이해 확인 대기 |
| 3 | `service/PriceComparisonService.kt` [수정] | 왜 전체가 죽고, `supervisorScope`는 어떻게 살리나? | 예정 |
| 마무리 | 실행 결과, 실험 | | 예정 |

순서를 고른 이유: 무대(Main) → 예외가 생기는 곳(Flaky) → 예외가 퍼지고 막히는 곳(Service).

## 글별 그림 배치

| 글 | 위치 | 그림 파일 (`docs/diagrams/`) | 답하는 질문 |
|---|---|---|---|
| 도입 | 문제 설명 뒤 | `step03-0-1-one-failure-kills-all.json` | 진에어 하나가 실패하면 어떻게 되나, 목표는? |
| 도입 | 목차 앞 | `step03-0-2-file-map.json` | 이번 단계 파일과 강의 번호는? |
| 강의 2 | `throw` 흐름 서술 뒤 | `step03-2-1-exception-path.json` | 진에어의 예외는 어디서 태어나 어디까지 올라가나? |

## 검증 결과 (Claude 실행)

- coroutineScope 조회: 약 300ms에 진에어 오류 → 대한·제주·티웨이 즉시 취소 → `조회 전체 실패 (진에어 서버 오류 (500))`
- supervisorScope 조회: 1513ms / 최저가 제주항공 / 시간 초과 [티웨이] / 실패 [진에어]
- 실험 A: `supervisorScope` 안에서도 `awaitAll()`을 쓰면 첫 실패에서 예외가 블록 밖으로 나가 전체 실패(형제 취소).
- 실험 B: `coroutineScope` 안에서 `await()`마다 `try/catch`를 해도 못 막는다. 자식이 실패하는 순간 부모 스코프가 취소되어 형제가 먼저 취소되고, catch는 실행되지 않은 채 전체 실패.

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.model.SearchResult
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlakyFlightProvider
import com.travel.service.PriceComparisonService
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

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

    runBlocking {
        search("coroutineScope 조회") { service.searchWithTimeout(request, TIMEOUT) }
        search("supervisorScope 조회") { service.searchResilient(request, TIMEOUT) }
    }
}

private suspend fun search(label: String, block: suspend () -> SearchResult) {
    log("===== $label: 시작 =====")
    try {
        val (result, elapsed) = measureTimedValue { block() }
        val cheapest = result.quotes.firstOrNull()
            ?.let { "${it.provider} ${"%,d".format(it.price)}원" }
            ?: "없음"
        log("===== $label: ${elapsed.inWholeMilliseconds}ms / 최저가 $cheapest / 시간 초과 ${result.timedOut} / 실패 ${result.failed} =====")
    } catch (e: ProviderException) {
        log("===== $label: 조회 전체 실패 (${e.message}) =====")
    }
}
```

### `src/main/kotlin/com/travel/provider/FlakyFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.delay

class FlakyFlightProvider(
    override val name: String,
    private val failAfterMs: Long,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 조회 시작")
        delay(failAfterMs)
        log("$name 서버 오류 발생")
        throw ProviderException("$name 서버 오류 (500)")
    }
}
```

### `src/main/kotlin/com/travel/service/PriceComparisonService.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.ProviderAnswer
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.model.SearchResult
import com.travel.provider.FlightProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

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

    suspend fun searchWithTimeout(request: SearchRequest, timeout: Duration): SearchResult = coroutineScope {
        val answers = providers
            .map { provider ->
                async { provider.name to withTimeoutOrNull(timeout) { provider.search(request) } }
            }
            .awaitAll()

        SearchResult(
            quotes = answers.mapNotNull { (_, quote) -> quote }.sortedBy { it.price },
            timedOut = answers.filter { (_, quote) -> quote == null }.map { (name, _) -> name },
        )
    }

    suspend fun searchResilient(request: SearchRequest, timeout: Duration): SearchResult = supervisorScope {
        val calls = providers.map { provider ->
            provider.name to async { withTimeoutOrNull(timeout) { provider.search(request) } }
        }

        val answers = calls.map { (name, call) ->
            try {
                call.await()
                    ?.let { quote -> ProviderAnswer.Success(name, quote) }
                    ?: ProviderAnswer.TimedOut(name)
            } catch (e: ProviderException) {
                ProviderAnswer.Failed(name, e.message.orEmpty())
            }
        }

        SearchResult(
            quotes = answers.filterIsInstance<ProviderAnswer.Success>().map { it.quote }.sortedBy { it.price },
            timedOut = answers.filterIsInstance<ProviderAnswer.TimedOut>().map { it.provider },
            failed = answers.filterIsInstance<ProviderAnswer.Failed>().map { it.provider },
        )
    }
}
```

### `src/main/kotlin/com/travel/model/Flight.kt`

```kotlin
package com.travel.model

data class SearchRequest(
    val from: String,
    val to: String,
    val date: String,
)

data class FlightQuote(
    val provider: String,
    val price: Int,
)

data class SearchResult(
    val quotes: List<FlightQuote>,
    val timedOut: List<String>,
    val failed: List<String> = emptyList(),
)

sealed interface ProviderAnswer {
    val provider: String

    data class Success(override val provider: String, val quote: FlightQuote) : ProviderAnswer
    data class TimedOut(override val provider: String) : ProviderAnswer
    data class Failed(override val provider: String, val reason: String) : ProviderAnswer
}

class ProviderException(message: String) : Exception(message)
```
