package com.travel.provider

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest

interface FlightProvider {
    val name: String

    suspend fun search(request: SearchRequest): FlightQuote
}
