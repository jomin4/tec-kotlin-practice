package com.travel.service

import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlakyFlightProvider
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PriceComparisonServiceTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    private val fiveAirlines = listOf(
        FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
        FakeFlightProvider("아시아나", latencyMs = 800, price = 398_000),
        FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
        FakeFlightProvider("진에어", latencyMs = 1_200, price = 275_000),
        FakeFlightProvider("티웨이", latencyMs = 700, price = 301_000),
    )

    @Test
    @DisplayName("순차 조회는 지연 시간의 합만큼 걸린다")
    fun sequentialTakesSumOfLatencies() = runTest {
        val quotes = PriceComparisonService(fiveAirlines).searchSequentially(request)

        assertEquals(4_200, currentTime)
        assertEquals("진에어", quotes.first().provider)
    }

    @Test
    @DisplayName("병렬 조회는 가장 느린 항공사만큼 걸린다")
    fun concurrentTakesSlowestLatency() = runTest {
        val quotes = PriceComparisonService(fiveAirlines).searchConcurrently(request)

        assertEquals(1_200, currentTime)
        assertEquals(listOf(275_000, 289_000, 301_000, 398_000, 420_000), quotes.map { it.price })
    }

    @Test
    @DisplayName("제한 시간을 넘긴 항공사는 시간 초과 목록에 들어간다")
    fun slowProviderGoesToTimedOut() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
            ),
        )

        val result = service.searchWithTimeout(request, 1_500.milliseconds)

        assertEquals(1_500, currentTime)
        assertEquals(listOf("제주항공"), result.quotes.map { it.provider })
        assertEquals(listOf("티웨이"), result.timedOut)
    }

    @Test
    @DisplayName("coroutineScope 조회는 한 곳이 실패하면 전체가 실패한다")
    fun coroutineScopeFailsWholeSearch() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FlakyFlightProvider("진에어", failAfterMs = 300),
            ),
        )

        assertFailsWith<ProviderException> { service.searchWithTimeout(request, 1_500.milliseconds) }
        assertEquals(300, currentTime)
    }

    @Test
    @DisplayName("supervisorScope 조회는 실패한 곳만 실패 목록에 넣는다")
    fun supervisorScopeIsolatesFailure() = runTest {
        val service = PriceComparisonService(
            listOf(
                FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
                FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
                FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
                FlakyFlightProvider("진에어", failAfterMs = 300),
            ),
        )

        val result = service.searchResilient(request, 1_500.milliseconds)

        assertEquals(1_500, currentTime)
        assertEquals(listOf("제주항공", "대한항공"), result.quotes.map { it.provider })
        assertEquals(listOf("티웨이"), result.timedOut)
        assertEquals(listOf("진에어"), result.failed)
    }

    @Test
    @DisplayName("가상 시간 덕분에 30초 기다림도 실제로는 금방 끝난다")
    fun virtualTimeSkipsLongWaits() {
        val startedAt = System.nanoTime()
        runTest {
            val service = PriceComparisonService(listOf(FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000)))

            service.searchWithTimeout(request, 60.seconds)

            assertEquals(30_000, currentTime)
        }
        val realMs = (System.nanoTime() - startedAt) / 1_000_000
        println("가상 시간 30,000ms / 실제 걸린 시간 ${realMs}ms")
        assertTrue(realMs < 10_000)
    }
}
