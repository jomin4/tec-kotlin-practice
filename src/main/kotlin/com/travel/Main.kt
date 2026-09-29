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
