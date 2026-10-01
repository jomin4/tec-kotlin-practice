package com.travel.repository

import com.travel.model.SearchResult
import com.travel.util.log

class SearchHistoryRepository(
    private val brokenLabels: Set<String> = emptySet(),
) {

    fun save(label: String, result: SearchResult) {
        log("DB 저장 시작: $label")
        Thread.sleep(800)
        if (label in brokenLabels) {
            throw IllegalStateException("DB 연결 끊김 ($label)")
        }
        log("DB 저장 완료: $label (견적 ${result.quotes.size}건)")
    }
}
