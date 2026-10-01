package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import kotlinx.coroutines.delay

class ScriptedFlightProvider(
    override val name: String,
    private val latencyMs: Long,
    private val prices: List<Int>,
) : FlightProvider {

    private var calls = 0

    override suspend fun search(request: SearchRequest): FlightQuote {
        delay(latencyMs)
        val price = prices[minOf(calls, prices.lastIndex)]
        calls++
        return FlightQuote(provider = name, price = price)
    }
}
