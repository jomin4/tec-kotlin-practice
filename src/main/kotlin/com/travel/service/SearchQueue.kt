package com.travel.service

import com.travel.model.FlightQuote
import com.travel.model.SearchRequest
import com.travel.provider.FlightProvider
import com.travel.util.log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class SearchQueue(
    private val provider: FlightProvider,
    private val workerCount: Int,
) {

    suspend fun processAll(requests: List<SearchRequest>): List<Pair<String, FlightQuote>> = coroutineScope {
        val inbox = Channel<SearchRequest>(capacity = 2)
        val outbox = Channel<Pair<String, FlightQuote>>(capacity = Channel.UNLIMITED)

        val workers = List(workerCount) { index ->
            launch {
                for (request in inbox) {
                    log("작업자 ${index + 1}: ${request.to} 조회 시작")
                    outbox.send(request.to to provider.search(request))
                }
            }
        }

        for (request in requests) {
            inbox.send(request)
            log("접수: ${request.to}")
        }
        inbox.close()

        workers.joinAll()
        outbox.close()
        outbox.toList()
    }
}
