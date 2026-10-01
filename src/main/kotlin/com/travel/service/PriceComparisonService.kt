package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.ProviderAnswer
import com.travel.model.ProviderException
import com.travel.model.SearchRequest
import com.travel.model.SearchResult
import com.travel.provider.FlightProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class PriceComparisonService(
    private val providers: List<FlightProvider>,
) {

    suspend fun searchSequentially(request: SearchRequest): List<FlightQuote> =
        providers
            .map { provider -> provider.search(request) }
            .sortedBy { it.price }

    suspend fun searchConcurrently(request: SearchRequest): List<FlightQuote> = coroutineScope {
        providers
            .map { provider -> async { provider.search(request) } }
            .awaitAll()
            .sortedBy { it.price }
    }

    suspend fun searchWithTimeout(request: SearchRequest, timeout: Duration): SearchResult = coroutineScope {
        val answers = providers
            .map { provider ->
                async { provider.name to withTimeoutOrNull(timeout) { provider.search(request) } }
            }
            .awaitAll()

        SearchResult(
            quotes = answers.mapNotNull { (_, quote) -> quote }.sortedBy { it.price },
            timedOut = answers.filter { (_, quote) -> quote == null }.map { (name, _) -> name },
        )
    }

    suspend fun searchResilient(request: SearchRequest, timeout: Duration): SearchResult = supervisorScope {
        val calls = providers.map { provider ->
            provider.name to async { withTimeoutOrNull(timeout) { provider.search(request) } }
        }

        val answers = calls.map { (name, call) ->
            try {
                call.await()
                    ?.let { quote -> ProviderAnswer.Success(name, quote) }
                    ?: ProviderAnswer.TimedOut(name)
            } catch (e: ProviderException) {
                ProviderAnswer.Failed(name, e.message.orEmpty())
            }
        }

        SearchResult(
            quotes = answers.filterIsInstance<ProviderAnswer.Success>().map { it.quote }.sortedBy { it.price },
            timedOut = answers.filterIsInstance<ProviderAnswer.TimedOut>().map { it.provider },
            failed = answers.filterIsInstance<ProviderAnswer.Failed>().map { it.provider },
        )
    }
}
