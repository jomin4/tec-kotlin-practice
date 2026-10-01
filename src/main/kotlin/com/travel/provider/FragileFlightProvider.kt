package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class FragileFlightProvider(
    override val name: String,
    private val maxConcurrent: Int,
    private val latencyMs: Long,
    private val price: Int,
) : FlightProvider {

    private val inFlight = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    val peakConcurrent: Int get() = peak.get()

    override suspend fun search(request: SearchRequest): FlightQuote {
        val now = inFlight.incrementAndGet()
        peak.accumulateAndGet(now, ::maxOf)
        try {
            if (now > maxConcurrent) {
                throw ProviderException("$name 요청 과다 (동시 ${now}건, 허용 ${maxConcurrent}건)")
            }
            delay(latencyMs)
            return FlightQuote(provider = name, price = price)
        } finally {
            inFlight.decrementAndGet()
        }
    }
}
