package com.travel.util

private var startedAt = System.currentTimeMillis()

/**
 * 로그 시계를 0으로 맞추고, 스레드 이름 뒤에 코루틴 이름(@coroutine#N)이 붙도록 디버그 모드를 켠다.
 * 코루틴이 하나라도 만들어지기 전에(= runBlocking 호출 전에) 불러야 디버그 모드가 적용된다.
 */
fun initLogging() {
    System.setProperty("kotlinx.coroutines.debug", "on")
    startedAt = System.currentTimeMillis()
}

fun log(message: String) {
    val elapsed = System.currentTimeMillis() - startedAt
    println("[%5dms] [%-20s] %s".format(elapsed, Thread.currentThread().name, message))
}
