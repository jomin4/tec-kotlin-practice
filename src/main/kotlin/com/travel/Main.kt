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
