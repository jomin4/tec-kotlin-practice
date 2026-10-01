# 6단계: 여러 가격 흐름을 합쳐 현재 최저가 유지: combine, StateFlow

- 문제: 항공사마다 가격 흐름이 따로 있다. 화면은 "지금 이 순간 가장 싼 곳" 하나만 알고 싶고, 아무 때나 최신 값을 바로 읽을 수 있어야 한다.
- 목표: `combine`으로 세 흐름의 최신 값을 합쳐 최저가를 계산하고, `stateIn`으로 항상 최신 값을 들고 있는 `StateFlow`로 만든다.
- 핵심 개념: `combine`, `StateFlow`(`value`, 같은 값 건너뜀), `stateIn`, `SharingStarted.Eagerly`, 뜨거운 흐름 vs 차가운 흐름
- 상태: 실습 완료

## 강의 목차

| 강의 | 파일 | 답할 질문 |
|---|---|---|
| 1 | `Main.kt [수정]` | 세 흐름을 어떻게 추적기에 넘기고, 화면은 최저가를 어떻게 받나? `value`로 읽는 것과 `collect`의 차이는? |
| 2 | `service/LowestPriceTracker.kt [신규]` | `combine`은 언제 새 값을 내보내고, `stateIn`은 무엇을 만드나? |

순서를 고른 이유: 무대에서 결과를 먼저 보고, 합치는 원리(Tracker)로 들어간다.

## 검증 결과 (Claude 실행)

```
[  111ms] [main @coroutine#1   ] ===== 시작 직후 value: null =====
[  215ms] [main @coroutine#3   ] 화면: 현재 최저가 제주항공 289,000원
[  761ms] [main @coroutine#3   ] 화면: 현재 최저가 티웨이 270,000원
[ 1128ms] [main @coroutine#1   ] ===== 1초 뒤 아무 때나 value로 읽기: 티웨이 =====
[ 1312ms] [main @coroutine#3   ] 화면: 현재 최저가 제주항공 275,000원
[ 1563ms] [main @coroutine#3   ] 화면: 현재 최저가 대한항공 260,000원
[ 1566ms] [main @coroutine#1   ] ===== 감시 종료 =====
```

- 시작 직후 `value`는 초기값 `null`: `combine`은 세 흐름이 모두 한 번씩 값을 내야 첫 값을 만든다(약 50ms 뒤 제주항공 289,000).
- 최저가가 바뀔 때만 화면이 갱신된다(4번): 제주 289,000 → 티웨이 270,000 → 제주 275,000 → 대한 260,000. 대한항공 395,000·380,000처럼 최저가를 바꾸지 않는 갱신은 `StateFlow`가 같은 값으로 보고 건너뛴다.
- `value`로 아무 때나 최신 최저가를 바로 읽을 수 있다(1초 시점 티웨이).
- 처음 `take(5)`로 작성했다가 프로그램이 끝나지 않았다. `StateFlow`는 같은 값을 다시 내보내지 않아 서로 다른 최저가가 4개뿐이었기 때문이다. `take(4)`로 고쳤다.
- `stateIn(trackerScope, SharingStarted.Eagerly, null)`은 `trackerScope`에 공유 코루틴(#2)을 띄워 세 흐름을 계속 모은다. 이 코루틴은 스스로 끝나지 않으므로 마지막에 `trackerScope.cancel()`로 끈다.
- 화면 코루틴(#3)은 `launch`로 띄워 `collect`한다. 그동안 #1은 `delay` 후 `value`를 직접 읽는다.

## 코드 스냅샷

### `src/main/kotlin/com/travel/Main.kt`

```kotlin
package com.travel

import com.travel.model.SearchRequest
import com.travel.provider.ScriptedFlightProvider
import com.travel.service.LowestPriceTracker
import com.travel.service.PriceWatcher
import com.travel.util.initLogging
import com.travel.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

fun main() {
    initLogging()
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")
    val feeds = listOf(
        PriceWatcher(ScriptedFlightProvider("대한항공", 50, listOf(420_000, 395_000, 380_000, 260_000)), 400.milliseconds),
        PriceWatcher(ScriptedFlightProvider("제주항공", 50, listOf(289_000, 289_000, 299_000, 275_000)), 300.milliseconds),
        PriceWatcher(ScriptedFlightProvider("티웨이", 50, listOf(301_000, 270_000, 285_000)), 500.milliseconds),
    ).map { watcher -> watcher.watch(request) }

    runBlocking {
        val trackerScope = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        val tracker = LowestPriceTracker(feeds, trackerScope)
        log("===== 시작 직후 value: ${tracker.lowest.value} =====")

        val screen = launch {
            tracker.lowest
                .filterNotNull()
                .take(4)
                .collect { log("화면: 현재 최저가 ${it.provider} ${"%,d".format(it.price)}원") }
        }

        delay(1_000)
        log("===== 1초 뒤 아무 때나 value로 읽기: ${tracker.lowest.value?.provider} =====")

        screen.join()
        trackerScope.cancel()
        log("===== 감시 종료 =====")
    }
}
```

### `src/main/kotlin/com/travel/service/LowestPriceTracker.kt`

```kotlin
package com.travel.service

import com.travel.model.FlightQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LowestPriceTracker(
    feeds: List<Flow<FlightQuote>>,
    scope: CoroutineScope,
) {

    val lowest: StateFlow<FlightQuote?> =
        combine(feeds) { latestQuotes -> latestQuotes.minBy { it.price } }
            .stateIn(scope, SharingStarted.Eagerly, initialValue = null)
}
```
