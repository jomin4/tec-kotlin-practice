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
