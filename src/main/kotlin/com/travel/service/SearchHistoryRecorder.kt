package com.travel.service

import com.travel.model.SearchResult
import com.travel.repository.SearchHistoryRepository
import com.travel.util.log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchHistoryRecorder(
    private val repository: SearchHistoryRepository,
) {

    private val handler = CoroutineExceptionHandler { _, e ->
        log("기록 저장 실패 처리: ${e.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    suspend fun save(label: String, result: SearchResult) {
        withContext(Dispatchers.IO) {
            repository.save(label, result)
        }
    }

    fun saveInBackground(label: String, result: SearchResult): Job =
        scope.launch {
            repository.save(label, result)
        }

    suspend fun joinPending() {
        scope.coroutineContext.job.children.forEach { it.join() }
    }
}
