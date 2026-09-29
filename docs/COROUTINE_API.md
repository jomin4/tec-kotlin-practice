# 코루틴 문법 사전

강의에서 처음 등장한 코루틴 API와, 코루틴과 맞물리는 Kotlin 문법을 카드로 모은다.
강의 본문에는 요약 카드가 들어가고, 이 문서에는 전체 카드가 쌓인다. 기준 버전: kotlinx-coroutines 1.11.0.

## 목차

| API / 문법 | 종류 | 처음 등장 |
|---|---|---|
| [`suspend fun`](#suspend-fun) | 키워드 | 1단계 |
| [suspend 람다 타입 `suspend () -> T`](#suspend-람다-타입) | Kotlin 문법 | 1단계 |
| [수신 객체 지정 람다 `CoroutineScope.() -> T`](#수신-객체-지정-람다-coroutinescope---t) | Kotlin 문법 | 1단계 |
| [`runBlocking`](#runblocking) | 코루틴 빌더 | 1단계 |
| [`coroutineScope`](#coroutinescope) | 스코프 함수 (suspend) | 1단계 |
| [`async`](#async) | 코루틴 빌더 (`CoroutineScope` 확장) | 1단계 |
| [`Deferred<T>`](#deferredt) | 타입 (`Job`의 하위 타입) | 1단계 |
| [`awaitAll`](#awaitall) | suspend 확장 함수 | 1단계 |
| [`delay`](#delay) | suspend 함수 | 1단계 |
| [`withTimeoutOrNull`](#withtimeoutornull) / [`withTimeout`](#withtimeout) | 스코프 함수 (suspend) | 2단계 강의 2 |
| [`Pair`, `to`, 구조 분해](#pair-to-구조-분해) | Kotlin 문법 | 2단계 강의 2 |

용어
- **코루틴 빌더**: 새 코루틴을 만들어 시작시키는 함수(`runBlocking`, `launch`, `async`). 새 디버그 번호(`@coroutine#N`)가 붙는다.
- **스코프 함수**: 새 코루틴 번호 없이, 호출한 코루틴 안에 **자식 스코프(Job 한 층)**를 만들고 블록이 끝날 때까지 suspend하는 함수(`coroutineScope`, `withTimeoutOrNull`, `withContext` 등).
- **suspend 지점**: 코루틴이 멈췄다가 나중에 이어질 수 있는 곳. suspend 함수를 호출하는 줄이다.

---

## `suspend fun`

```kotlin
suspend fun search(request: SearchRequest): FlightQuote
```

- **뜻**: 이 함수는 실행 도중 **멈췄다가(suspend) 나중에 이어서 실행될 수 있다**는 표시다.
- **호출할 수 있는 곳**: 다른 `suspend` 함수 안, 또는 코루틴 빌더/스코프 함수의 블록 안(그 블록 자체가 suspend 람다라서).
  일반 함수에서 부르면 컴파일 에러가 난다.
- **스레드**: `suspend`가 붙었다고 새 스레드나 새 코루틴이 생기지 않는다. 호출한 코루틴이 그대로 실행한다.
  멈추는 동안에는 스레드를 놓아준다(안에서 `delay` 같은 진짜 suspend 지점을 만났을 때).
- **흔한 오해**: `suspend`를 붙이면 비동기로 도는 게 아니다. 동시 실행은 `async`/`launch`가 만든다.
- **처음 등장**: 1단계 `FlightProvider.search`

## suspend 람다 타입

```kotlin
private suspend fun compare(label: String, search: suspend () -> List<FlightQuote>)
```

- **뜻**: "suspend 함수를 호출할 수 있는 람다"의 타입이다. 일반 `() -> T` 람다 안에서는 suspend 함수를 부를 수 없다.
- **호출**: `search()`처럼 부르며, 부르는 쪽도 suspend 문맥이어야 한다.
- **inline 함수 예외**: `map`, `measureTimedValue`처럼 `inline` 함수의 람다는 호출한 곳에 코드가 펼쳐지므로,
  바깥이 suspend 문맥이면 람다 타입이 일반이어도 안에서 suspend 함수를 부를 수 있다.
- **처음 등장**: 1단계 `Main.compare`

## 수신 객체 지정 람다 `CoroutineScope.() -> T`

```kotlin
suspend fun <R> coroutineScope(block: suspend CoroutineScope.() -> R): R
```

- **뜻**: 블록 안에서 `this`가 `CoroutineScope`다. 그래서 블록 안에서는 `this.async { }`를 `async { }`로 바로 쓸 수 있다.
- **왜 중요한가**: `async`/`launch`는 `CoroutineScope`의 확장 함수라서 스코프(`this`)가 있어야 부를 수 있다.
  `runBlocking { }`, `coroutineScope { }`, `withTimeoutOrNull { }`, `async { }`의 블록이 모두 이 형태라서,
  그 안에서는 새 자식 코루틴을 만들 수 있다.
- **처음 등장**: 1단계 `searchConcurrently`의 `coroutineScope { ... async { } }`

## `runBlocking`

```kotlin
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T
```

- **종류**: 코루틴 빌더. **suspend 함수가 아니다**(일반 함수에서 부를 수 있다).
- **하는 일**: 새 코루틴을 만들어 블록을 실행하고, 그 코루틴(과 자식 전부)이 끝날 때까지 **호출한 스레드를 막고(blocking)** 기다린다.
- **스레드**: 호출한 스레드(여기서는 `main`)를 이벤트 루프로 써서 블록과 자식 코루틴을 실행한다. 따로 스레드를 만들지 않는다.
- **반환**: 블록의 마지막 값(`T`).
- **쓰는 곳**: 일반 세계와 코루틴 세계를 잇는 `main` 함수나 테스트. 코루틴 안에서 다시 부르면 스레드를 막으므로 쓰지 않는다.
- **처음 등장**: 1단계 `Main.main`

## `coroutineScope`

```kotlin
suspend fun <R> coroutineScope(block: suspend CoroutineScope.() -> R): R
```

- **종류**: 스코프 함수(suspend). 새 코루틴 번호는 생기지 않고, 호출한 코루틴 아래에 자식 스코프 한 층이 생긴다.
- **하는 일**: 블록을 실행하고, **블록 안에서 시작한 자식 코루틴이 모두 끝날 때까지** 반환하지 않는다.
- **반환**: 블록의 마지막 값(`R`).
- **예외**: 자식 하나가 실패하면 나머지 자식을 취소하고 그 예외를 다시 던진다(3단계에서 자세히).
- **왜 쓰나**: suspend 함수 안에서 `async`를 쓰려면 스코프가 필요하다. `coroutineScope`는 그 스코프를 호출자의 자식으로 만들어서,
  함수가 반환될 때 뒤에서 몰래 도는 코루틴이 남지 않게 한다(구조화된 동시성).
- **처음 등장**: 1단계 `searchConcurrently`

## `async`

```kotlin
fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T>
```

- **종류**: 코루틴 빌더, `CoroutineScope`의 확장 함수. **suspend 함수가 아니다**(그래서 기다리지 않고 바로 반환한다).
- **하는 일**: 새 자식 코루틴을 만들어 실행 대기열에 넣고, 결과를 나중에 받을 `Deferred<T>`를 즉시 돌려준다.
- **파라미터**
  - `context`: 이 코루틴에만 더할 설정(Dispatcher, 이름 등). 기본값은 부모 것을 그대로 쓴다.
  - `start`: 시작 방식. 기본 `DEFAULT`는 "곧바로 실행 예약". `LAZY`면 `await()`/`start()`를 부를 때까지 시작하지 않는다.
  - `block`: 코루틴이 실행할 코드. 마지막 값이 결과 `T`가 된다.
- **부모-자식**: 수신 객체 스코프(`this`)의 Job이 부모가 된다. 부모 스코프는 이 자식이 끝나기를 기다린다.
- **예외**: 블록에서 난 예외는 `await()` 때 다시 던져지고, 동시에 부모에게도 전파된다(3단계).
- **처음 등장**: 1단계 `searchConcurrently`

## `Deferred<T>`

```kotlin
interface Deferred<out T> : Job {
    suspend fun await(): T
}
```

- **뜻**: "나중에 `T`를 받을 수 있는 교환권". `Job`이기도 해서 취소(`cancel()`), 상태 확인(`isActive`, `isCompleted`)이 된다.
- **`await()`**: 결과가 나올 때까지 호출한 코루틴을 suspend한다(스레드는 막지 않는다). 이미 끝났으면 바로 반환한다.
- **처음 등장**: 1단계 `searchConcurrently`의 `map { async { } }` 결과 `List<Deferred<FlightQuote>>`

## `awaitAll`

```kotlin
suspend fun <T> Collection<Deferred<T>>.awaitAll(): List<T>
suspend fun <T> awaitAll(vararg deferreds: Deferred<T>): List<T>
```

- **종류**: suspend 확장 함수.
- **하는 일**: 모든 `Deferred`가 끝날 때까지 suspend하고, 결과를 **리스트에 넣은 순서대로** 돌려준다(끝난 순서가 아니다).
- **예외**: 하나라도 실패하면 나머지를 기다리지 않고 **즉시** 그 예외를 던진다.
  그래서 `map { it.await() }`와 다르다(그쪽은 앞에서부터 차례로 기다리다가 실패한 것에 도달해야 던진다).
- **처음 등장**: 1단계 `searchConcurrently`

## `delay`

```kotlin
suspend fun delay(timeMillis: Long)
suspend fun delay(duration: Duration)
```

- **종류**: suspend 함수.
- **하는 일**: 지정한 시간만큼 **이 코루틴만** 멈춘다. 스레드는 돌려주므로 그동안 다른 코루틴이 같은 스레드에서 실행된다.
  시간이 지나면 다음 줄부터 이어서 실행한다.
- **`Thread.sleep`과의 차이**: `Thread.sleep`은 스레드를 붙잡고 잔다. 같은 스레드의 다른 코루틴은 그동안 실행되지 못한다.
- **취소**: 기다리는 도중 코루틴이 취소되면 `CancellationException`을 던지며 즉시 깨어난다(2단계 강의 3).
- **처음 등장**: 1단계 `FakeFlightProvider.search`

## `withTimeoutOrNull`

```kotlin
suspend fun <T> withTimeoutOrNull(timeout: Duration, block: suspend CoroutineScope.() -> T): T?
suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend CoroutineScope.() -> T): T?
```

- **종류**: 스코프 함수(suspend). 새 코루틴 번호는 없지만, 호출한 코루틴 아래에 **제한 시간이 달린 자식 스코프**가 한 층 생긴다.
- **하는 일**: 블록을 실행하면서 타이머를 건다. 타이머는 **이 함수를 호출한 순간** 시작한다.
  - 제한 안에 블록이 끝나면: 블록의 값 `T`를 반환한다.
  - 제한을 넘기면: 블록 안의 코드를 **취소**하고(`TimeoutCancellationException`), 그 예외를 스스로 잡아 **`null`을 반환**한다.
- **반환 타입**: `T?`. 블록이 `FlightQuote`를 돌려주면 결과는 `FlightQuote?`가 된다. `null`은 "시간 초과"라는 뜻이다.
- **범위**: 취소는 이 스코프 안에만 미친다. 바깥 코루틴과 형제 코루틴은 영향을 받지 않고 계속 실행된다.
- **주의**
  - 타임아웃은 취소 신호일 뿐이다. 블록 안 코드가 suspend 지점에서 취소를 확인해야 실제로 멈춘다(2단계 강의 3, 4).
  - 제한 시간이 0 이하면 블록을 실행하지 않고 바로 `null`을 반환한다.
  - 블록이 원래 `null`을 돌려줄 수 있는 타입이면, 결과 `null`이 "시간 초과"인지 "원래 null"인지 구별할 수 없다.
- **처음 등장**: 2단계 강의 2 `searchWithTimeout`

## `withTimeout`

```kotlin
suspend fun <T> withTimeout(timeout: Duration, block: suspend CoroutineScope.() -> T): T
```

- **`withTimeoutOrNull`과의 차이**: 제한을 넘기면 `null` 대신 `TimeoutCancellationException`을 **밖으로 던진다**. 반환 타입도 `T?`가 아니라 `T`다.
- **이 프로젝트에서 안 쓴 이유**: `searchWithTimeout`에서 `withTimeout`을 쓰면 예외가 `async` → `awaitAll` → `coroutineScope`를 타고 올라가
  **전체 조회가 예외로 끝난다**. 제한 안에 도착한 대한항공·제주항공 결과까지 잃는다(Claude 실험으로 확인, 2단계 강의 2).
- **언제 쓰나**: 시간 초과를 "실패"로 다뤄 위로 알려야 할 때(예: 전체 요청 마감 시간).
- **처음 등장**: 2단계 강의 2 (비교용)

## `Pair`, `to`, 구조 분해

```kotlin
infix fun <A, B> A.to(that: B): Pair<A, B>
data class Pair<out A, out B>(val first: A, val second: B)
```

- **`to`**: `a to b`는 `Pair(a, b)`를 만든다. `infix` 함수라서 점과 괄호 없이 쓴다.
- **구조 분해**: `val (name, quote) = pair`는 `pair.first`, `pair.second`를 한 번에 꺼낸다(`component1()`, `component2()`).
  람다 파라미터에도 쓸 수 있다: `answers.map { (name, _) -> name }`. 안 쓰는 칸은 `_`로 둔다.
- **처음 등장**: 2단계 강의 2 `searchWithTimeout`
