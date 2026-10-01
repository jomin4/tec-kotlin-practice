package com.travel

import com.travel.demo.step1
import com.travel.demo.step2
import com.travel.demo.step3
import com.travel.demo.step4
import com.travel.demo.step5
import com.travel.demo.step6
import com.travel.demo.step7
import com.travel.util.initLogging

private val demos: Map<Int, Pair<String, () -> Unit>> = linkedMapOf(
    1 to ("순차 호출 vs async 병렬 호출" to ::step1),
    2 to ("응답이 안 오는 API: 타임아웃과 협력적 취소" to ::step2),
    3 to ("하나가 실패하면 전체가 죽는 문제: supervisorScope" to ::step3),
    4 to ("블로킹 DB 저장 섞기: Dispatchers.IO, launch" to ::step4),
    5 to ("가격을 주기적으로 감시: Flow" to ::step5),
    6 to ("여러 가격 흐름을 합쳐 현재 최저가 유지: combine, StateFlow" to ::step6),
    7 to ("요청이 몰리면 API가 차단: Semaphore, Channel" to ::step7),
)

fun main(args: Array<String>) {
    val selected = args.firstOrNull()?.toIntOrNull()
    val targets = if (selected == null) demos.keys else listOf(selected)

    for (step in targets) {
        val (title, demo) = demos[step] ?: run {
            println("알 수 없는 단계: $step (1~7 중에서 고르세요. 8단계는 ./gradlew test)")
            return
        }
        println()
        println("################ $step 단계: $title ################")
        initLogging()
        demo()
    }
}
