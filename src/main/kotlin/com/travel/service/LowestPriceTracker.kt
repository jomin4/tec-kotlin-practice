package com.travel.service

import com.travel.model.FlightQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LowestPriceTracker(
    feeds: List<Flow<FlightQuote>>,
    scope: CoroutineScope,
) {

    val lowest: StateFlow<FlightQuote?> =
        combine(feeds) { latestQuotes -> latestQuotes.minBy { it.price } }
            .stateIn(scope, SharingStarted.Eagerly, initialValue = null)
}
