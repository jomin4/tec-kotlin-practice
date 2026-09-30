package com.travel.service

import com.travel.model.SearchRequest
import com.travel.provider.ScriptedFlightProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class FlowTest {

    private val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")

    @Test
    @DisplayName("감시 흐름은 간격마다 새 가격을 내보낸다")
    fun watcherEmitsPricePerInterval() = runTest {
        val watcher = PriceWatcher(
            ScriptedFlightProvider("제주항공", latencyMs = 100, prices = listOf(289_000, 289_000, 279_000, 265_000)),
            interval = 300.milliseconds,
        )

        val prices = watcher.watch(request)
            .map { it.price }
            .distinctUntilChanged()
            .take(3)
            .toList()

        assertEquals(listOf(289_000, 279_000, 265_000), prices)
        assertEquals(100 + 400 + 400 + 400, currentTime.toInt())
    }

    @Test
    @DisplayName("최저가 추적기는 시간이 흐르면 최신 최저가로 바뀐다")
    fun trackerFollowsLatestLowest() = runTest {
        val feeds = listOf(
            PriceWatcher(ScriptedFlightProvider("대한항공", 50, listOf(420_000, 395_000, 380_000, 260_000)), 400.milliseconds),
            PriceWatcher(ScriptedFlightProvider("제주항공", 50, listOf(289_000, 289_000, 299_000, 275_000)), 300.milliseconds),
            PriceWatcher(ScriptedFlightProvider("티웨이", 50, listOf(301_000, 270_000, 285_000)), 500.milliseconds),
        ).map { it.watch(request) }
        val tracker = LowestPriceTracker(feeds, backgroundScope)

        assertNull(tracker.lowest.value)

        advanceTimeBy(51); runCurrent()
        assertEquals("제주항공", tracker.lowest.value?.provider)

        advanceTimeBy(550); runCurrent()
        assertEquals("티웨이", tracker.lowest.value?.provider)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(260_000, tracker.lowest.value?.price)
    }
}
