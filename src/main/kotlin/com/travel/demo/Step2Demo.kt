package com.travel.demo

import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlightProvider
import com.travel.provider.HeavyParsingFlightProvider
import com.travel.service.PriceComparisonService
import com.travel.util.log
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

private val TIMEOUT = 1_500.milliseconds

/** 2단계: 응답이 안 오는 API: 타임아웃과 협력적 취소 */
fun step2() {
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
