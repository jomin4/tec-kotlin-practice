package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.util.log
import kotlinx.coroutines.delay

class FlakyFlightProvider(
    override val name: String,
    private val failAfterMs: Long,
) : FlightProvider {

    override suspend fun search(request: SearchRequest): FlightQuote {
        log("$name 조회 시작")
        delay(failAfterMs)
        log("$name 서버 오류 발생")
        throw ProviderException("$name 서버 오류 (500)")
    }
}
