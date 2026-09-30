# 7단계: 요청이 몰리면 API가 차단: Semaphore, Channel

- 문제: 여러 사용자가 동시에 조회하면 항공사 API가 허용 동시 요청 수(2건)를 넘겨 요청을 거절한다. 요청을 무작정 동시에 보내지 말고 줄을 세워야 한다.
- 목표: `Semaphore`로 동시에 나가는 요청 수를 2건으로 묶고, `Channel` 대기열과 작업자 3명으로 요청을 순서대로 나눠 처리한다.
- 핵심 개념: `Semaphore`, `withPermit`, `Channel`(`send`/`for` 수신/`close`, 용량과 역압), 작업자 패턴(fan-out), `joinAll`
- 상태: 실습 완료

## 강의 목차

| 강의 | 파일 | 답할 질문 |
|---|---|---|
| 1 | `Main.kt [수정]` | 제한 없음 / Semaphore / Channel 세 경우를 어떻게 비교하나? |
| 2 | `provider/FragileFlightProvider.kt [신규]` | 허용 동시 요청 수를 넘으면 거절하는 API는 어떻게 흉내 내나? |
| 3 | `provider/RateLimitedFlightProvider.kt [신규]` | `Semaphore.withPermit`은 어떻게 동시 요청 수를 묶나? |
| 4 | `service/SearchQueue.kt [신규]` | `Channel` 대기열과 작업자 여러 명은 어떻게 일을 나눠 갖나? 대기열이 차면 보내는 쪽은 어떻게 되나? |

순서를 고른 이유: 무대 → 문제를 만드는 API → 동시 수 제한(Semaphore) → 줄 세우기(Channel).

## 검증 결과 (Claude 실행)

```
[   71ms] [main @coroutine#1   ] ===== 1) 사용자 6명이 동시에 조회 (제한 없음) =====
[  430ms] [main @coroutine#1   ] 결과: {성공=2, 실패=4} / 329ms
[  433ms] [main @coroutine#1   ] 최대 동시 요청: 3건
[  433ms] [main @coroutine#1   ] ===== 2) Semaphore(2)로 동시 요청 수 제한 =====
[ 1343ms] [main @coroutine#1   ] 결과: {성공=6} / 904ms
[ 1343ms] [main @coroutine#1   ] 최대 동시 요청: 2건
[ 1343ms] [main @coroutine#1   ] ===== 3) Channel 대기열 + 작업자 3명 =====
[ 1374ms] [main @coroutine#1   ] 접수: NRT
[ 1374ms] [main @coroutine#1   ] 접수: KIX
[ 1382ms] [main @coroutine#14  ] 작업자 1: NRT 조회 시작
[ 1383ms] [main @coroutine#14  ] 대한항공 조회 시작
[ 1383ms] [main @coroutine#15  ] 작업자 2: KIX 조회 시작
[ 1383ms] [main @coroutine#15  ] 대한항공 조회 시작
[ 1383ms] [main @coroutine#16  ] 작업자 3: FUK 조회 시작
[ 1383ms] [main @coroutine#16  ] 대한항공 조회 시작
[ 1384ms] [main @coroutine#1   ] 접수: FUK
[ 1384ms] [main @coroutine#1   ] 접수: CTS
[ 1384ms] [main @coroutine#1   ] 접수: OKA
[ 1684ms] [main @coroutine#14  ] 대한항공 조회 완료 (300ms)
[ 1686ms] [main @coroutine#14  ] 작업자 1: CTS 조회 시작
[ 1686ms] [main @coroutine#14  ] 대한항공 조회 시작
[ 1686ms] [main @coroutine#15  ] 대한항공 조회 완료 (300ms)
[ 1686ms] [main @coroutine#15  ] 작업자 2: OKA 조회 시작
[ 1686ms] [main @coroutine#15  ] 대한항공 조회 시작
[ 1687ms] [main @coroutine#16  ] 대한항공 조회 완료 (300ms)
[ 1687ms] [main @coroutine#16  ] 작업자 3: TPE 조회 시작
[ 1687ms] [main @coroutine#16  ] 대한항공 조회 시작
[ 1687ms] [main @coroutine#1   ] 접수: TPE
[ 1687ms] [main @coroutine#1   ] 접수: BKK
[ 1986ms] [main @coroutine#14  ] 대한항공 조회 완료 (300ms)
[ 1987ms] [main @coroutine#14  ] 작업자 1: BKK 조회 시작
[ 1988ms] [main @coroutine#14  ] 대한항공 조회 시작
[ 1988ms] [main @coroutine#15  ] 대한항공 조회 완료 (300ms)
[ 1988ms] [main @coroutine#16  ] 대한항공 조회 완료 (300ms)
[ 2288ms] [main @coroutine#14  ] 대한항공 조회 완료 (300ms)
[ 2304ms] [main @coroutine#1   ] ===== 처리 완료 7건 / 930ms / 순서 [NRT, KIX, FUK, CTS, OKA, TPE, BKK] =====
```

- 1) 제한 없음: 6명 동시 조회 중 2건 성공, 4건이 `요청 과다`로 실패(최대 동시 3건에서 거절 시작).
- 2) `Semaphore(2)`: 6건 모두 성공. 2건씩 세 차례(약 905ms), 최대 동시 요청 2건. 나머지는 `withPermit`에서 suspend하며 차례를 기다린다(스레드를 막지 않음).
- 3) `Channel(capacity = 2)` + 작업자 3명(#14~#16): 7건을 약 933ms(300ms × 3회)에 처리. 대기열이 차면 `send`가 suspend해서 접수가 잠시 멈춘다(`접수: TPE`는 작업자 1이 CTS를 꺼내 간 뒤에 찍힘).
- `inbox.close()` 후 작업자의 `for (request in inbox)`가 남은 요청을 다 꺼내면 루프가 끝나고, `joinAll()`로 세 작업자가 끝나기를 기다린다.
- `FragileFlightProvider`의 카운터는 `AtomicInteger`를 쓴다. 이 예제는 main 스레드 하나에서 돌지만, 여러 스레드에서 불려도 안전하게 세려는 실무 습관이다.

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlightProvider
import com.travel.provider.FragileFlightProvider
import com.travel.provider.RateLimitedFlightProvider
import com.travel.service.SearchQueue
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.time.measureTimedValue

fun main() {
    initLogging()
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    runBlocking {
        log("===== 1) 사용자 6명이 동시에 조회 (제한 없음) =====")
        val raw = FragileFlightProvider("제주항공", maxConcurrent = 2, latencyMs = 300, price = 289_000)
        burst(raw, request, users = 6)
        log("최대 동시 요청: ${raw.peakConcurrent}건")

        log("===== 2) Semaphore(2)로 동시 요청 수 제한 =====")
        val guarded = FragileFlightProvider("제주항공", maxConcurrent = 2, latencyMs = 300, price = 289_000)
        burst(RateLimitedFlightProvider(guarded, permits = 2), request, users = 6)
        log("최대 동시 요청: ${guarded.peakConcurrent}건")

        log("===== 3) Channel 대기열 + 작업자 3명 =====")
        val destinations = listOf("NRT", "KIX", "FUK", "CTS", "OKA", "TPE", "BKK")
        val queue = SearchQueue(FakeFlightProvider("대한항공", latencyMs = 300, price = 420_000), workerCount = 3)
        val (results, elapsed) = measureTimedValue {
            queue.processAll(destinations.map { request.copy(to = it) })
        }
        log("===== 처리 완료 ${results.size}건 / ${elapsed.inWholeMilliseconds}ms / 순서 ${results.map { it.first }} =====")
    }
}

private suspend fun burst(provider: FlightProvider, request: SearchRequest, users: Int) {
    val (outcomes, elapsed) = measureTimedValue {
        coroutineScope {
            (1..users).map { user ->
                async {
                    try {
                        provider.search(request)
                        "성공"
                    } catch (e: ProviderException) {
                        "실패"
                    }
                }
            }.awaitAll()
        }
    }
    log("결과: ${outcomes.groupingBy { it }.eachCount()} / ${elapsed.inWholeMilliseconds}ms")
}
```

### `src/main/kotlin/com/travel/provider/FragileFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class FragileFlightProvider(
    override val name: String,
    private val maxConcurrent: Int,
    private val latencyMs: Long,
    private val price: Int,
) : FlightProvider {

    private val inFlight = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    val peakConcurrent: Int get() = peak.get()

    override suspend fun search(request: SearchRequest): FlightQuote {
        val now = inFlight.incrementAndGet()
        peak.accumulateAndGet(now, ::maxOf)
        try {
            if (now > maxConcurrent) {
                throw ProviderException("$name 요청 과다 (동시 ${now}건, 허용 ${maxConcurrent}건)")
            }
            delay(latencyMs)
            return FlightQuote(provider = name, price = price)
        } finally {
            inFlight.decrementAndGet()
        }
    }
}
```

### `src/main/kotlin/com/travel/provider/RateLimitedFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RateLimitedFlightProvider(
    private val delegate: FlightProvider,
    permits: Int,
) : FlightProvider {

    override val name: String get() = delegate.name

    private val semaphore = Semaphore(permits)

    override suspend fun search(request: SearchRequest): FlightQuote =
        semaphore.withPermit {
            delegate.search(request)
        }
}
```

### `src/main/kotlin/com/travel/service/SearchQueue.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FlightProvider
import com.travel.util.log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class SearchQueue(
    private val provider: FlightProvider,
    private val workerCount: Int,
) {

    suspend fun processAll(requests: List<SearchRequest>): List<Pair<String, FlightQuote>> = coroutineScope {
        val inbox = Channel<SearchRequest>(capacity = 2)
        val outbox = Channel<Pair<String, FlightQuote>>(capacity = Channel.UNLIMITED)

        val workers = List(workerCount) { index ->
            launch {
                for (request in inbox) {
                    log("작업자 ${index + 1}: ${request.to} 조회 시작")
                    outbox.send(request.to to provider.search(request))
                }
            }
        }

        for (request in requests) {
            inbox.send(request)
            log("접수: ${request.to}")
        }
        inbox.close()

        workers.joinAll()
        outbox.close()
        outbox.toList()
    }
}
```
