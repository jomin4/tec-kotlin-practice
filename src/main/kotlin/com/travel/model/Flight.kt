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
