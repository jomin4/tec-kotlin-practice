package com.travel.demo

import com.travel.model.SearchRequest
import com.travel.provider.FakeFlightProvider
import com.travel.provider.FlakyFlightProvider
import com.travel.repository.SearchHistoryRepository
import com.travel.service.PriceComparisonService
import com.travel.service.SearchHistoryRecorder
import com.travel.util.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

private val TIMEOUT = 1_500.milliseconds

/** 4단계: 블로킹 DB 저장 섞기: Dispatchers.IO, launch */
fun step4() {
    val request = SearchRequest(from = "ICN", to = "NRT", date = "2026-10-15")
    val service = PriceComparisonService(
        providers = listOf(
            FakeFlightProvider("대한항공", latencyMs = 1_000, price = 420_000),
            FakeFlightProvider("제주항공", latencyMs = 500, price = 289_000),
            FakeFlightProvider("티웨이", latencyMs = 30_000, price = 301_000),
            FlakyFlightProvider("진에어", failAfterMs = 300),
        ),
    )
    val repository = SearchHistoryRepository(brokenLabels = setOf("저장 C"))
    val recorder = SearchHistoryRecorder(repository)

    runBlocking {
        val result = service.searchResilient(request, TIMEOUT)
        log("===== 조회 끝: 최저가 ${result.quotes.firstOrNull()?.provider ?: "없음"} =====")

        val screen = launch {
            var frame = 0
            while (true) {
                log("화면 갱신 ${++frame}")
                delay(200)
            }
        }
        delay(300)

        log("===== 1) main 스레드에서 직접 저장 =====")
        repository.save("저장 A", result)

        log("===== 2) withContext(Dispatchers.IO)로 저장 =====")
        recorder.save("저장 B", result)

        log("===== 3) launch로 맡기고 바로 다음 일 =====")
        recorder.saveInBackground("저장 C", result)
        recorder.saveInBackground("저장 D", result)
        log("저장을 맡겼으니 바로 다음 작업 진행")

        recorder.joinPending()
        log("===== 백그라운드 저장 모두 끝 =====")
        screen.cancel()
    }
}
