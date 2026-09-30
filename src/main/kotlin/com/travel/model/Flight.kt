package com.travel.model

data class SearchRequest(
    val from: String,
    val to: String,
    val date: String,
)

data class FlightQuote(
    val provider: String,
    val price: Int,
)

data class SearchResult(
    val quotes: List<FlightQuote>,
    val timedOut: List<String>,
    val failed: List<String> = emptyList(),
)

sealed interface ProviderAnswer {
    val provider: String

    data class Success(override val provider: String, val quote: FlightQuote) : ProviderAnswer
    data class TimedOut(override val provider: String) : ProviderAnswer
    data class Failed(override val provider: String, val reason: String) : ProviderAnswer
}

class ProviderException(message: String) : Exception(message)
