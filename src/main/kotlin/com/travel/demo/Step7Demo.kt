package com.travel.demo

import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlightProvider
import com.travel.provider.FragileFlightProvider
import com.travel.provider.RateLimitedFlightProvider
import com.travel.service.SearchQueue
import com.travel.util.log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.time.measureTimedValue

/** 7단계: 요청이 몰리면 API가 차단: Semaphore, Channel */
fun step7() {
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
