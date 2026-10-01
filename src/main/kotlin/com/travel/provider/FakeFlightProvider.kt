package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class FakeFlightProvider(
    override val name: String,
    private val latencyMs: Long,
    private val price: Int,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 조회 시작")
        try {
            delay(latencyMs)
        } catch (e: CancellationException) {
            log("$name 조회 취소됨")
            throw e
        }
        log("$name 조회 완료 (${latencyMs}ms)")
        return FlightQuote(provider = name, price = price)
    }
}
