# 2단계: 응답이 안 오는 API: 타임아웃과 협력적 취소

- 문제: 병렬 조회는 가장 느린 항공사를 기다린다. 한 곳이 응답하지 않으면 전체가 멈춘다.
- 목표: 항공사별 제한 시간(`withTimeoutOrNull`)으로 늦는 곳을 잘라내고, 취소가 실제로 어떻게 전달되는지(협력적 취소) 확인한다.
- 핵심 개념: `withTimeoutOrNull`, `CancellationException`, 취소는 suspend 지점에서 확인된다, `yield()`
- 환경 코드: `model/Flight.kt`에 `SearchResult` 추가

## 강의 목차와 진행 상태

| 강의 | 파일 | 답할 질문 | 상태 |
|---|---|---|---|
| 1 | `Main.kt` [수정] | 무엇을 비교하는 실험인가? | 완료 |
| 2 | `service/PriceComparisonService.kt` [수정] | 항공사별 제한 시간을 어떻게 거나? | 완료 |
| 3 | `provider/FakeFlightProvider.kt` [수정] | 취소는 코루틴 안에 어떤 모습으로 도착하나? | 완료 |
| 4 | `provider/HeavyParsingFlightProvider.kt` [신규] | 취소를 확인하지 않는 코드는 어떻게 되나? | 완료 |
| 마무리 | 실행 결과 두 개 비교, 실험 | 협력하지 않는 코루틴 하나가 전체에 끼치는 영향 | 완료, `src/` 반영 |

순서를 고른 이유: 실행 흐름 순서(무대 → 제한을 거는 곳 → 취소를 받는 곳 → 취소를 무시하는 곳)대로 가면 강의 4의 문제가 앞 강의들 위에서 드러난다.

## 글별 그림 배치

| 글 | 위치 | 그림 파일 (`docs/diagrams/`) | 답하는 질문 |
|---|---|---|---|
| 도입 | 문제 설명 뒤 | `step02-0-1-slowest-blocks-all.json` | 한 곳이 응답하지 않으면 왜 전체가 멈추나, 제한 시간을 걸면? |
| 도입 | 목차 앞 | `step02-0-2-file-map.json` | 이번 단계 파일과 강의 번호는? |
| 강의 1 | 항공사 구성 설명 뒤 | `step02-1-1-provider-roles.json` | 각 항공사는 1.5초 제한선 기준으로 어떤 역할인가? |
| 강의 2 | `withTimeoutOrNull` 문법 카드 앞 | `step02-2-1-timeout-two-paths.json` | 제한 안에 끝날 때와 넘길 때 각각 무엇이 반환되나? |
| 강의 2 | `async` + `withTimeoutOrNull` 조합 설명 뒤 | `step02-2-2-search-with-timeout-flow.json` | 네 항공사의 결과가 어떻게 모여 `SearchResult`가 되나? |
| 강의 3 | 흐름 서술 뒤, 값 추적 표 앞 | `step02-3-1-search-two-paths.json` | 같은 `search`가 제주항공과 티웨이에서 각각 어느 길로 가나? |
| 강의 3 | "왜 다시 던지나" 설명 뒤 | `step02-3-2-swallow-vs-rethrow.json` | `throw e`를 빼서 예외를 삼키면 무엇이 달라지나? |
| 강의 4 | cooperative = false 흐름 서술 뒤 | `step02-4-1-no-yield-timeline.json` | 양보하지 않는 코루틴 하나가 있으면 스레드와 다른 코루틴은 어떻게 되나? |
| 강의 4 | cooperative = true 흐름 서술 뒤 | `step02-4-2-yield-timeline.json` | `yield()`를 넣으면 무엇이 달라지나? |

## 검증 결과 (Claude 실행)

- search 1 (협력 안 함): ≈3100ms, 최저가 에어부산 260,000원, 시간 초과 [대한항공, 제주항공, 티웨이]
- search 2 (협력함): ≈1510ms, 최저가 제주항공 289,000원, 시간 초과 [티웨이, 에어부산]
- 확인한 사실: `runBlocking` 안의 제한 시간 타이머는 main이 아닌 별도 스레드가 처리한다. 그래서 main이 CPU 작업에 붙잡혀 있어도 1.5초에 취소 신호는 간다.
  블록이 한 번도 suspend하지 않고 값을 반환하면 `withTimeoutOrNull`은 제한을 넘겼어도 그 값을 돌려준다.
- `withTimeout`으로 바꾸면 `TimeoutCancellationException`이 `searchWithTimeout` 밖으로 던져져 전체 조회가 실패한다(대한·제주 결과도 잃음). 강의 2에서 비교로 사용.
- 강의 2부터 문법 카드 적용(`CLAUDE.md` 4-1). 카드 전체는 `docs/COROUTINE_API.md`.
- 강의 3부터 값 추적 흐름 서술 적용(`CLAUDE.md` 4-0).
- 강의 4 실험: 취소 표시는 `kotlinx.coroutines.DefaultExecutor` 스레드가 1500ms에 한다(main이 바빠도 정확).
  `yield()` 대신 `ensureActive()`를 쓰면 에어부산은 1.5초에 멈추지만 대한·제주도 차례를 못 받아 4곳 모두 시간 초과.
- 마무리에서 고침: 4곳 모두 시간 초과면 `result.quotes.first()`가 `NoSuchElementException`으로 죽던 문제를 `firstOrNull()?.let { } ?: "없음"`으로 수정. 제한 100ms로 확인하니 `최저가 없음` 출력.
- 삼키기 실험(강의 3): `throw e`를 빼면 티웨이가 1.7초에 "조회 완료 (30000ms)" 거짓 로그를 찍고 견적을 반환하지만, `withTimeoutOrNull` 결과는 여전히 `null`.
  삼킨 뒤 `delay(10)`을 부르면 즉시 `TimeoutCancellationException`. catch한 `e`의 실제 타입은 `TimeoutCancellationException`.

## 마무리 설명 (모범 예시)

사용자가 "이 방식"이라고 확정한 설명. 이후 강의도 이 형식을 따른다(`CLAUDE.md` 4-0-2).

```kotlin
val cheapest = result.quotes.firstOrNull()
    ?.let { "${it.provider} ${"%,d".format(it.price)}원" }
    ?: "없음"
```

**정상 경우 (search 2)**
① `searchWithTimeout`이 약 1513ms 만에 돌려준 `SearchResult`가 `result`에 담긴다. `result.quotes`는 가격순 `[제주항공 289,000원, 대한항공 420,000원]`.
② `firstOrNull()`이 맨 앞의 최저가 제주항공 견적을 꺼낸다. 고치기 전 `first()`와 같은 값이지만 비었을 때 예외 대신 `null`을 주므로 타입이 `FlightQuote?`가 되고, 다음 줄은 `null` 가능성을 전제로 이어진다.
③ 값이 있으므로 `?.let { }`가 실행된다(`?.`는 왼쪽이 `null`이 아닐 때만 실행). `it`은 제주항공 견적, 마지막 식 `"제주항공 289,000원"`이 결과.
④ `?: "없음"`의 왼쪽이 `null`이 아니므로 그 문자열이 `cheapest`에 담긴다(`?:`는 왼쪽이 `null`일 때만 오른쪽 사용).

**경계 경우 (전부 시간 초과)**
① 서비스는 `quotes = []`를 정상 결과로 돌려준다. 보여주는 방식은 `Main`의 몫이라 수정도 `Main`에서 한다.
② `firstOrNull()` → `null`. ③ `?.let` 블록은 건너뛰고 `null`이 넘어간다. ④ `?: "없음"` → `"없음"`.
고치기 전에는 ②의 `first()`가 `NoSuchElementException`을 던져 #1 → `runBlocking` → `main`까지 올라가 프로그램이 종료됐다.

| 단계 | 정상 (search 2) | 경계 (전부 시간 초과) | 고치기 전 경계 |
|---|---|---|---|
| `result.quotes` | `[제주항공, 대한항공]` | `[]` | `[]` |
| `firstOrNull()` / `first()` | 제주항공 견적 | `null` | `NoSuchElementException` → 종료 |
| `?.let { }` | 실행 → `"제주항공 289,000원"` | 건너뜀 → `null` | - |
| `?: "없음"` | 그대로 | `"없음"` | - |

즉, "견적이 없다"는 정상 결과를 예외로 터뜨리지 않고 `"없음"`이라는 표시로 바꿔 끝까지 흘려보낸다.

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlightProvider
import com.travel.provider.HeavyParsingFlightProvider
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

    runBlocking {
        search("취소를 확인하지 않는 파싱", request, heavyParser(cooperative = false))
        search("취소를 확인하는 파싱", request, heavyParser(cooperative = true))
    }
}

private fun heavyParser(cooperative: Boolean) =
    HeavyParsingFlightProvider("에어부산", parsingMs = 3_000, price = 260_000, cooperative = cooperative)

private suspend fun search(label: String, request: SearchRequest, parser: FlightProvider) {
    val service = PriceComparisonService(
        providers = listOf(
            FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
            FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
            FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
            parser,
        ),
    )

    log("===== $label: 시작 (항공사별 제한 ${TIMEOUT.inWholeMilliseconds}ms) =====")
    val (result, elapsed) = measureTimedValue { service.searchWithTimeout(request, TIMEOUT) }
    val cheapest = result.quotes.firstOrNull()
        ?.let { "${it.provider} ${"%,d".format(it.price)}원" }
        ?: "없음"
    log("===== $label: ${elapsed.inWholeMilliseconds}ms / 최저가 $cheapest / 시간 초과 ${result.timedOut} =====")
}
```

### `src/main/kotlin/com/travel/service/PriceComparisonService.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.model.SearchResult
import com.travel.provider.FlightProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
}
```

### `src/main/kotlin/com/travel/provider/FakeFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class FakeFlightProvider(
    override val name: String,
    private val latencyMs: Long,
    private val price: Int,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 조회 시작")
        try {
            delay(latencyMs)
        } catch (e: CancellationException) {
            log("$name 조회 취소됨")
            throw e
        }
        log("$name 조회 완료 (${latencyMs}ms)")
        return FlightQuote(provider = name, price = price)
    }
}
```

### `src/main/kotlin/com/travel/provider/HeavyParsingFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

class HeavyParsingFlightProvider(
    override val name: String,
    private val parsingMs: Long,
    private val price: Int,
    private val cooperative: Boolean,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 응답 파싱 시작 (${parsingMs}ms 걸림)")
        val endAt = System.currentTimeMillis() + parsingMs
        try {
            while (System.currentTimeMillis() < endAt) {
                parseChunk()
                if (cooperative) yield()
            }
        } catch (e: CancellationException) {
            log("$name 파싱 중단됨")
            throw e
        }
        log("$name 응답 파싱 완료")
        return FlightQuote(provider = name, price = price)
    }

    private fun parseChunk() {
        val until = System.nanoTime() + 10_000_000
        while (System.nanoTime() < until) {
            // CPU만 쓰는 작업 흉내 (10ms)
        }
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
)
```
