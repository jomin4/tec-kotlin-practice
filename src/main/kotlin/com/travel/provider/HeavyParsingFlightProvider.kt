package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

class HeavyParsingFlightProvider(
    override val name: String,
    private val parsingMs: Long,
    private val price: Int,
    private val cooperative: Boolean,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 응답 파싱 시작 (${parsingMs}ms 걸림)")
        val endAt = System.currentTimeMillis() + parsingMs
        try {
            while (System.currentTimeMillis() < endAt) {
                parseChunk()
                if (cooperative) yield()
            }
        } catch (e: CancellationException) {
            log("$name 파싱 중단됨")
            throw e
        }
        log("$name 응답 파싱 완료")
        return FlightQuote(provider = name, price = price)
    }

    private fun parseChunk() {
        val until = System.nanoTime() + 10_000_000
        while (System.nanoTime() < until) {
            // CPU만 쓰는 작업 흉내 (10ms)
        }
    }
}
