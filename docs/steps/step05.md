# 5단계: 가격을 주기적으로 감시: Flow

- 문제: 한 번 조회로 끝나지 않고, 가격이 바뀌는지 계속 확인해 목표가 이하로 떨어지면 알려야 한다.
- 목표: `flow { }`로 주기적 조회 흐름을 만들고, 연산자(`map`·`distinctUntilChanged`·`filter`·`take`)로 "바뀐 가격 중 목표가 이하만 2번 알림"을 조립한다.
- 핵심 개념: `Flow`(차가운 흐름), `flow { }`/`emit`, `collect`, 중간 연산자 `onEach`·`map`·`distinctUntilChanged`·`filter`·`take`, `onCompletion`
- 상태: 실습 완료

## 강의 목차

| 강의 | 파일 | 답할 질문 |
|---|---|---|
| 1 | `Main.kt [수정]` | 흐름을 어떻게 조립하고, 언제 실제로 돌기 시작하나? |
| 2 | `service/PriceWatcher.kt [신규]` | `flow { while(true) { emit; delay } }`는 무한 반복인데 어떻게 멈추나? |
| 3 | `provider/ScriptedFlightProvider.kt [신규]` | 부를 때마다 가격이 바뀌는 가짜 항공사는 어떻게 만드나? (짧게) |

순서를 고른 이유: 무대(흐름 조립) → 흐름의 원천(PriceWatcher) → 값을 만드는 가짜 항공사.

## 검증 결과 (Claude 실행)

```
[  159ms] [main @coroutine#1   ] ===== 흐름을 만들었지만 아직 아무 일도 일어나지 않음 =====
[  311ms] [main @coroutine#1   ] 가격 확인: 289,000원
[  312ms] [main @coroutine#1   ] 가격 변동 감지: 289,000원
[  714ms] [main @coroutine#1   ] 가격 확인: 289,000원
[ 1115ms] [main @coroutine#1   ] 가격 확인: 279,000원
[ 1115ms] [main @coroutine#1   ] 가격 변동 감지: 279,000원
[ 1123ms] [main @coroutine#1   ] ===== 알림: 목표가 280,000원 이하 → 279,000원 =====
[ 1524ms] [main @coroutine#1   ] 가격 확인: 279,000원
[ 1925ms] [main @coroutine#1   ] 가격 확인: 285,000원
[ 1926ms] [main @coroutine#1   ] 가격 변동 감지: 285,000원
[ 2327ms] [main @coroutine#1   ] 가격 확인: 265,000원
[ 2327ms] [main @coroutine#1   ] 가격 변동 감지: 265,000원
[ 2328ms] [main @coroutine#1   ] ===== 알림: 목표가 280,000원 이하 → 265,000원 =====
[ 2328ms] [main @coroutine#1   ] 감시 종료
```

- `collect` 전에는 조회가 한 번도 일어나지 않는다(차가운 흐름): 첫 로그 이후 첫 `가격 확인`은 `collect` 뒤에 찍힌다.
- `distinctUntilChanged`: 289,000 → 289,000(중복)은 `가격 변동 감지`가 찍히지 않는다.
- `filter { it <= 280_000 }` + `take(2)`: 279,000과 265,000에서 알림 2번 후 `take`가 흐름을 끝내고, 무한 루프(`while (true)`)도 취소되어 멈춘다(`감시 종료`).
- 모든 로그가 `main @coroutine#1`: `flow { }` 블록은 `collect`를 부른 코루틴 안에서 실행된다(새 코루틴이 생기지 않음).

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.SearchRequest
import com.travel.provider.ScriptedFlightProvider
import com.travel.service.PriceWatcher
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

private const val TARGET_PRICE = 280_000

fun main() {
    initLogging()
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")
    val watcher = PriceWatcher(
        provider = ScriptedFlightProvider(
            name = "제주항공",
            latencyMs = 100,
            prices = listOf(289_000, 289_000, 279_000, 279_000, 285_000, 265_000, 265_000),
        ),
        interval = 300.milliseconds,
    )

    runBlocking {
        val alerts = watcher.watch(request)
            .onEach { log("가격 확인: ${"%,d".format(it.price)}원") }
            .map { it.price }
            .distinctUntilChanged()
            .onEach { log("가격 변동 감지: ${"%,d".format(it)}원") }
            .filter { it <= TARGET_PRICE }
            .take(2)
            .onCompletion { log("감시 종료") }

        log("===== 흐름을 만들었지만 아직 아무 일도 일어나지 않음 =====")
        alerts.collect { price ->
            log("===== 알림: 목표가 ${"%,d".format(TARGET_PRICE)}원 이하 → ${"%,d".format(price)}원 =====")
        }
    }
}
```

### `src/main/kotlin/com/travel/service/PriceWatcher.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FlightProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

class PriceWatcher(
    private val provider: FlightProvider,
    private val interval: Duration,
) {

    fun watch(request: SearchRequest): Flow<FlightQuote> = flow {
        while (true) {
            emit(provider.search(request))
            delay(interval)
        }
    }
}
```

### `src/main/kotlin/com/travel/provider/ScriptedFlightProvider.kt`

```kotlin
package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import kotlinx.coroutines.delay

class ScriptedFlightProvider(
    override val name: String,
    private val latencyMs: Long,
    private val prices: List<Int>,
) : FlightProvider {

    private var calls = 0

    override suspend fun search(request: SearchRequest): FlightQuote {
        delay(latencyMs)
        val price = prices[minOf(calls, prices.lastIndex)]
        calls++
        return FlightQuote(provider = name, price = price)
    }
}
```
