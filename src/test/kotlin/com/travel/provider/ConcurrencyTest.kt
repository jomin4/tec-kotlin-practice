package com.travel.provider

import com.travel.model.SearchRequest
import com.travel.service.SearchQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals

class ConcurrencyTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    @Test
    @DisplayName("Semaphore는 동시 요청을 허가 수만큼으로 묶는다")
    fun semaphoreLimitsConcurrency() = runTest {
        val fragile = FragileFlightProvider("제주항공", maxConcurrent = 2, latencyMs = 300, price = 289_000)
        val limited = RateLimitedFlightProvider(fragile, permits = 2)

        val quotes = (1..6).map { async { limited.search(request) } }.awaitAll()

        assertEquals(6, quotes.size)
        assertEquals(2, fragile.peakConcurrent)
        assertEquals(900, currentTime)
    }

    @Test
    @DisplayName("Channel 작업자 3명이 요청 7건을 나눠 처리한다")
    fun channelWorkersShareRequests() = runTest {
        val queue = SearchQueue(FakeFlightProvider("대한항공", latencyMs = 300, price = 420_000), workerCount = 3)
        val destinations = listOf("NRT", "KIX", "FUK", "CTS", "OKA", "TPE", "BKK")

        val results = queue.processAll(destinations.map { request.copy(to = it) })

        assertEquals(destinations.toSet(), results.map { it.first }.toSet())
        assertEquals(900, currentTime)
    }
}
