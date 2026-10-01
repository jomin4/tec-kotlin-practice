package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FlightProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

class PriceWatcher(
    private val provider: FlightProvider,
    private val interval: Duration,
) {

    fun watch(request: SearchRequest): Flow<FlightQuote> = flow {
        while (true) {
            emit(provider.search(request))
            delay(interval)
        }
    }
}
