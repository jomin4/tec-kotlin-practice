package com.travel.demo

import com.travel.model.SearchRequest
import com.travel.provider.ScriptedFlightProvider
import com.travel.service.LowestPriceTracker
import com.travel.service.PriceWatcher
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

/** 6단계: 여러 가격 흐름을 합쳐 현재 최저가 유지: combine, StateFlow */
fun step6() {
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
