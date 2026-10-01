package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RateLimitedFlightProvider(
    private val delegate: FlightProvider,
    permits: Int,
) : FlightProvider {

    override val name: String get() = delegate.name

    private val semaphore = Semaphore(permits)

    override suspend fun search(request: SearchRequest): FlightQuote =
        semaphore.withPermit {
            delegate.search(request)
        }
}
