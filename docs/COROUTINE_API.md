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
| [`mapNotNull`](#mapnotnull) | Kotlin 표준 라이브러리 | 2단계 강의 2 |
| [`CancellationException`](#cancellationexception) / [`TimeoutCancellationException`](#timeoutcancellationexception) | 예외 타입 | 2단계 강의 3 |
| [취소 가능한 suspend 함수](#취소-가능한-suspend-함수) | 개념 | 2단계 강의 3 |
| [suspend 지점이 없는 코드](#suspend-지점이-없는-코드) | 개념 | 2단계 강의 4 |
| [`yield`](#yield) | suspend 함수 | 2단계 강의 4 |
| [`ensureActive` / `isActive`](#ensureactive--isactive) | 취소 확인 함수 / 프로퍼티 | 2단계 강의 4 (비교) |
| [취소 vs 실패, 예외 전파 규칙](#취소-vs-실패-예외-전파-규칙) | 개념 | 3단계 강의 2~3 |
| [`supervisorScope`](#supervisorscope) | 스코프 함수 (suspend) | 3단계 강의 3 |
| [`await()`의 예외 규칙](#await의-예외-규칙) | 동작 규칙 | 3단계 강의 3 |
| [`sealed interface`, `filterIsInstance`](#sealed-interface-filterisinstance) | Kotlin 문법 | 3단계 강의 3 |
| [`launch`](#launch) / [`Job`, `cancel()`](#job-cancel) | 코루틴 빌더 / 타입 | 4단계 강의 1 |
| [블로킹 함수](#블로킹-함수) | 개념 | 4단계 강의 2 |
| [`withContext`](#withcontext) / [`Dispatchers.IO`](#dispatchersio) | 스코프 함수 / Dispatcher | 4단계 강의 3 |
| [`CoroutineContext`와 `+`](#coroutinecontext와-) / [`CoroutineScope(...)`](#coroutinescope-만들기) | 타입 / 함수 | 4단계 강의 3 |
| [`SupervisorJob`](#supervisorjob) / [`CoroutineExceptionHandler`](#coroutineexceptionhandler) | Job / 컨텍스트 요소 | 4단계 강의 3 |
| [`Flow`, `flow { }`, `emit`, `collect`](#flow-flow--emit-collect) | 타입 / 빌더 / 종단 연산자 | 5단계 |
| [Flow 중간 연산자](#flow-중간-연산자) | `onEach`·`map`·`filter`·`distinctUntilChanged`·`take`·`onCompletion` | 5단계 |
| [`combine`](#combine) / [`StateFlow`, `stateIn`](#stateflow-statein) | Flow 연산자 / 뜨거운 흐름 | 6단계 |

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
- **취소**: 취소 가능한 suspend 함수다. 기다리는 도중 코루틴이 취소되면 원래 깨어날 시간을 기다리지 않고 즉시 깨어나
  `CancellationException`을 던진다(2단계 강의 3: 30000ms를 기다리던 티웨이가 1500ms에 예외로 깨어남).
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
- **타이머를 누가 재나**: `runBlocking` 안에서는 제한 시간 타이머를 main이 아닌 `kotlinx.coroutines.DefaultExecutor` 스레드가 처리한다.
  그래서 main이 CPU 작업으로 바빠도 1.5초에 **취소 표시**는 정확히 된다(2단계 강의 4 실험으로 확인). 표시된 취소를 코드가 확인하는지는 별개다.
- **주의**
  - 블록이 **한 번도 suspend하지 않고** 값을 반환하면, 제한을 넘겼어도 `withTimeoutOrNull`은 그 값을 돌려준다(실험: 3초 파싱한 에어부산 견적이 채택됨).
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

## `mapNotNull`

```kotlin
inline fun <T, R : Any> Iterable<T>.mapNotNull(transform: (T) -> R?): List<R>
```

- **하는 일**: 각 원소를 변환하면서 결과가 `null`인 것은 버린다.
- **타입 변화**: 변환 결과가 `FlightQuote?`여도 반환은 `List<FlightQuote>`(non-null)다(`R : Any` 제약).
- **처음 등장**: 2단계 강의 2 `searchWithTimeout`

## `CancellationException`

```kotlin
// kotlinx.coroutines (JVM)
typealias CancellationException = java.util.concurrent.CancellationException
// 상속: CancellationException → IllegalStateException → RuntimeException → Exception
```

- **종류**: 예외 타입. 코루틴에서는 **"이 코루틴은 취소되었다"는 신호**로 쓰인다. 실패가 아니다.
- **어디서 던져지나**: 코루틴이 취소된 상태에서 **취소 가능한 suspend 함수**(`delay`, `await`, `yield`, `withContext` 등)를 부르거나,
  그 함수 안에서 기다리는 중에 취소되면 그 자리에서 던져진다.
- **실패와의 차이**: 취소된 자식은 `CancellationException`으로 끝나도 부모를 실패시키지 않는다(3단계에서 일반 예외와 비교).
- **잡았다면 다시 던진다**: 로그나 정리 작업을 위해 `catch`해도 되지만 **반드시 `throw e`로 다시 던진다**. 삼키면:
  - 취소된 코루틴이 멈추지 않고 다음 줄을 계속 실행한다(실험: 티웨이가 1.7초에 "조회 완료 (30000ms)"라는 거짓 로그를 찍었다).
  - 그 코루틴에서 다음 suspend 함수를 부르면 곧바로 같은 예외가 다시 던져진다(실험: 삼킨 뒤 `delay(10)`이 즉시 `TimeoutCancellationException`).
  - 블록이 반환한 값은 버려지고 `withTimeoutOrNull`은 여전히 `null`을 돌려준다(실험으로 확인).
- **흔한 실수**: `catch (e: Exception)`이나 `runCatching { }`은 `CancellationException`도 함께 잡는다(`Exception`의 하위 타입이므로).
  이렇게 넓게 잡을 때는 `CancellationException`을 먼저 다시 던지도록 따로 처리해야 한다(3단계).
- **정리 작업 위치**: 취소돼도 꼭 해야 하는 정리는 보통 `finally`에 둔다. 이 프로젝트는 취소 시점을 로그로 보려고 `catch`를 썼다.
- **처음 등장**: 2단계 강의 3 `FakeFlightProvider.search`

## `TimeoutCancellationException`

```kotlin
class TimeoutCancellationException : CancellationException
```

- **뜻**: `withTimeout`/`withTimeoutOrNull`의 제한 시간이 지나 취소될 때 쓰이는 원인 예외. `CancellationException`의 하위 타입이다.
- **흐름**: 제한 시간이 지나면 블록 안 코루틴이 이 예외로 취소된다 → 블록 안의 suspend 지점(`delay`)에서 이 예외가 던져진다 →
  블록 밖으로 올라온 예외를 `withTimeoutOrNull`은 잡아서 `null`로 바꾸고, `withTimeout`은 그대로 밖으로 던진다.
- **확인한 사실**: `FakeFlightProvider`의 `catch (e: CancellationException)`에서 `e::class.simpleName`을 찍으면 `TimeoutCancellationException`이 나온다.
- **처음 등장**: 2단계 강의 3

## 취소 가능한 suspend 함수

- **뜻**: 기다리는 도중에 코루틴이 취소되면 기다림을 멈추고 `CancellationException`을 던지는 suspend 함수.
  `kotlinx.coroutines`의 suspend 함수(`delay`, `await`, `awaitAll`, `yield`, `withContext`, `withTimeout` 등)는 모두 이렇다.
- **협력적 취소의 핵심**: 취소는 강제 종료가 아니다. 코드가 이런 suspend 지점에 도달해야 취소가 효과를 낸다.
  suspend 지점 없이 CPU만 쓰는 코드는 취소 신호를 받아도 계속 돈다(2단계 강의 4).
- **처음 등장**: 2단계 강의 3 (`delay`가 취소되는 모습)

## suspend 지점이 없는 코드

- **뜻**: 반복문이나 계산처럼 suspend 함수를 한 번도 부르지 않고 CPU만 쓰는 코드. 일반 함수(`fun`)는 suspend 함수를 부를 수 없으므로 전부 여기에 해당한다.
- **스레드**: 끝날 때까지 스레드를 놓지 않는다. 같은 스레드를 쓰는 다른 코루틴은 그동안 실행되지 못한다(깨어날 시간이 돼도 차례를 못 받음).
- **취소**: 취소 표시가 돼도 확인하는 곳이 없어 끝까지 실행된다. 취소는 "확인하는 코드"가 있어야 효과가 난다(협력적 취소).
- **해결**: 반복 중간중간 `yield()`(확인 + 양보) 또는 `ensureActive()`/`isActive`(확인만)를 넣는다. 오래 걸리는 CPU 작업을 다른 스레드로 보내는 방법은 4단계.
- **처음 등장**: 2단계 강의 4 `HeavyParsingFlightProvider.parseChunk`

## `yield`

```kotlin
suspend fun yield(): Unit
```

- **종류**: suspend 함수(취소 가능).
- **하는 일**: 두 가지를 한 번에 한다.
  1. **취소 확인**: 이 코루틴이 취소된 상태면 `CancellationException`을 던진다.
  2. **양보**: 이 코루틴을 잠깐 suspend하고 같은 Dispatcher(여기서는 main 스레드) 대기열의 다른 코루틴에게 차례를 준 뒤, 다시 차례가 오면 이어서 실행한다.
     대기 중인 다른 코루틴이 없으면 거의 곧바로 이어진다.
- **스레드**: 막지 않는다. 오히려 스레드를 잠깐 내놓는 함수다.
- **이 코드에서**: 10ms 파싱할 때마다 `yield()`를 불러, 그 사이 제주항공·대한항공이 제때 깨어나 결과를 내고, 1.5초 취소도 10ms 안에 확인된다.
- **처음 등장**: 2단계 강의 4

## `ensureActive` / `isActive`

```kotlin
fun CoroutineContext.ensureActive()        // suspend 함수 안에서는 currentCoroutineContext().ensureActive()
fun CoroutineScope.ensureActive()
val CoroutineScope.isActive: Boolean
```

- **`ensureActive()`**: 취소된 상태면 `CancellationException`을 던진다. **suspend하지 않고 스레드도 내놓지 않는다**(확인만).
- **`isActive`**: 취소되지 않았으면 `true`. 예외 없이 `while (isActive) { }`처럼 조건으로 쓴다.
- **`yield()`와의 차이 (실험으로 확인)**: 에어부산 루프에 `yield()` 대신 `ensureActive()`를 넣으면, 에어부산은 1.5초에 정확히 멈춘다.
  하지만 그때까지 main 스레드를 계속 쥐고 있어서 500ms·1000ms에 깨어났어야 할 제주항공·대한항공이 차례를 못 받고 함께 시간 초과로 잘렸다.
  스레드가 하나뿐인 곳에서는 **확인뿐 아니라 양보도 필요**하므로 `yield()`를 쓴다.
- **처음 등장**: 2단계 강의 4 (비교)

## 취소 vs 실패, 예외 전파 규칙

- **취소**: 코루틴이 `CancellationException` 계열로 끝남. 부모에게 실패로 알리지 않는다(2단계 티웨이).
- **실패**: 그 밖의 예외로 끝남(3단계 진에어의 `ProviderException`). 부모에게 알린다.
- **`coroutineScope`(일반 Job)의 규칙**: 자식 하나가 실패하면 ① 부모 스코프가 자기 자신을 취소하고 ② 다른 자식을 모두 취소한 뒤
  ③ 모든 자식이 끝나면 그 **원래 예외**를 스코프 밖으로 던진다. 실험: 진에어 실패 300ms에 대한·제주·티웨이가 즉시 취소되고
  `Main`에는 `ProviderException`이 도착했다.
- **`try/catch`로 막을 수 없다**: `coroutineScope` 안에서 `await()`마다 `try/catch`를 둬도, 자식 실패가 부모를 먼저 취소하므로
  형제는 이미 취소되고 catch는 실행되지 않은 채 전체가 실패한다(3단계 실험 B).
- `withTimeoutOrNull`은 자기 타이머의 `TimeoutCancellationException`만 `null`로 바꾸고, 다른 예외는 그대로 통과시킨다.
- **처음 등장**: 3단계 강의 2(실패 분류), 강의 3(전파 규칙)

## `supervisorScope`

```kotlin
suspend fun <R> supervisorScope(block: suspend CoroutineScope.() -> R): R
```

- **종류**: 스코프 함수(suspend). `coroutineScope`와 모양이 같다. 새 코루틴 번호는 없고, 자식 스코프 한 층이 생긴다.
- **다른 점**: 자식이 실패해도 **부모 스코프와 형제를 취소하지 않는다.** 실패는 그 자식에게만 남는다.
  - `async` 자식의 예외는 그 `Deferred` 안에 보관되었다가 `await()` 때 다시 던져진다.
  - `launch` 자식의 예외는 `CoroutineExceptionHandler`로 간다(4단계).
- **블록 자신이 던지면**: 블록 코드(예: `awaitAll()`, `await()`)에서 예외가 밖으로 나가면 스코프는 자식을 모두 취소하고 그 예외를 던진다.
  실험 A: `supervisorScope` 안에서 `awaitAll()`을 쓰면 첫 실패 예외가 블록 밖으로 나가 결국 전체 실패.
  그래서 `await()`를 하나씩 부르고 각각 `try/catch`한다.
- **반환**: 블록의 마지막 값. 자식이 모두 끝나야 반환한다(구조화된 동시성은 그대로).
- **처음 등장**: 3단계 강의 3 `searchResilient`

## `await()`의 예외 규칙

```kotlin
suspend fun await(): T   // Deferred<T>
```

- 자식이 값으로 끝났으면 그 값을, **예외로 끝났으면 그 예외를 호출한 자리에서 다시 던진다.**
- 이미 끝난 `Deferred`면 기다리지 않고 즉시 돌려주거나 던진다. 진에어는 300ms에 실패했지만, 4번째로 `await()`한 순간(≈1500ms)에야 `catch`에 도착했다.
- **처음 등장**: 3단계 강의 3

## `sealed interface`, `filterIsInstance`

```kotlin
sealed interface ProviderAnswer { val provider: String
    data class Success(...) : ProviderAnswer
    data class TimedOut(...) : ProviderAnswer
    data class Failed(...) : ProviderAnswer
}
inline fun <reified R> Iterable<*>.filterIsInstance(): List<R>
```

- **`sealed interface`**: 구현 타입이 이 파일 안의 것들로 **닫혀 있는** 인터페이스. "결과는 딱 이 세 가지 중 하나"를 타입으로 표현한다.
- **`filterIsInstance<T>()`**: 리스트에서 `T` 타입인 원소만 골라 `List<T>`로 돌려준다. 그래서 `Success`만 골라 `.quote`에 바로 접근할 수 있다.
- **처음 등장**: 3단계 강의 3 `searchResilient`

## `launch`

```kotlin
fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job
```

- **종류**: 코루틴 빌더, `CoroutineScope`의 확장 함수. suspend 함수가 아니다(기다리지 않고 바로 반환).
- **하는 일**: 새 자식 코루틴을 만들어 실행 대기열에 넣고, **결과값 없이** `Job`을 즉시 돌려준다. "맡겨 두고 잊는" 작업용.
- **`async`와의 차이**: `async`는 결과 `T`를 담은 `Deferred<T>`를 주고 `await()`로 받는다. `launch`는 결과가 없고(`Unit`), 예외는
  `await()`로 받을 곳이 없으므로 부모로 전파되거나(일반 Job) `CoroutineExceptionHandler`로 간다(SupervisorJob 아래 루트 코루틴).
- **Dispatcher**: `context`를 주지 않으면 부모의 것을 물려받는다. `runBlocking` 안의 `launch`는 main 스레드에서 돈다.
- **처음 등장**: 4단계 강의 1 (화면 갱신 코루틴), 강의 3 (백그라운드 저장)

## `Job`, `cancel()`

```kotlin
interface Job : CoroutineContext.Element {
    fun cancel(cause: CancellationException? = null)
    suspend fun join()
    val isActive: Boolean; val isCancelled: Boolean; val isCompleted: Boolean
    val children: Sequence<Job>
}
```

- **뜻**: 코루틴 하나의 "손잡이". 상태 확인, 취소(`cancel()`), 끝날 때까지 기다리기(`join()`)를 한다.
- **`cancel()`**: 취소 표시만 한다. 코루틴은 다음 suspend 지점(예: `delay`)에서 `CancellationException`을 받아 끝난다(2단계 협력적 취소).
- **`join()`**: 그 코루틴이 끝날 때까지 suspend한다. 결과값은 없다.
- **부모는 자식을 기다린다**: `runBlocking`은 블록이 끝나도 자식이 모두 끝나야 반환한다. 무한 반복하는 화면 갱신 코루틴을
  `cancel()`하지 않으면 프로그램이 끝나지 않는다(4단계 강의 1).
- **처음 등장**: 4단계 강의 1

## 블로킹 함수

- **뜻**: 일이 끝날 때까지 **호출한 스레드를 붙잡고 놓지 않는** 함수. JDBC 같은 DB 드라이버, 파일 입출력, 오래된 HTTP 클라이언트 등
  코루틴을 모르는 라이브러리가 대부분 이렇다. 이 프로젝트에서는 `Thread.sleep(800)`으로 흉내 낸다(`SearchHistoryRepository.save`).
- **스레드는 호출한 쪽이 정한다**: 블로킹 함수는 자기가 어느 스레드에서 도는지 모른다. main이 부르면 main이, IO 스레드가 부르면 IO 스레드가 멈춘다.
- **`suspend`를 붙여도 안 바뀐다**: `suspend fun` 안에서 `Thread.sleep`을 불러도 스레드는 그대로 붙잡힌다. `suspend`는 "멈출 수 있다"는
  표시일 뿐 블로킹을 없애 주지 않는다(1단계 `delay` vs `Thread.sleep`). 해결은 **블로킹 호출을 다른 스레드로 옮기는 것**(`withContext(Dispatchers.IO)`).
- **취소가 안 먹힌다**: 블로킹 중에는 suspend 지점이 없어 코루틴 취소를 확인하지 못한다(2단계 협력적 취소, 4단계 `Job()` 실험에서 저장 D가 취소되지 않고 끝까지 실행됨).
- **처음 등장**: 4단계 강의 2

## `withContext`

```kotlin
suspend fun <T> withContext(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T
```

- **종류**: 스코프 함수(suspend). 새 코루틴 번호는 생기지 않는다. 호출한 코루틴이 `context`(주로 다른 Dispatcher)로 **건너가서** 블록을 실행하고,
  끝나면 원래 스레드로 **돌아와** 블록의 값을 반환한다.
- **기다림**: 블록이 끝날 때까지 호출한 코루틴은 suspend한다(결과를 기다림). 하지만 원래 스레드는 붙잡지 않는다.
- **확인한 사실**: `recorder.save("저장 B")`의 저장 로그 스레드가 `DefaultDispatcher-worker-1 @coroutine#1`. 같은 #1이 IO 스레드에서 실행됐고,
  그동안 main에서 화면 갱신이 200ms마다 계속 찍혔다.
- **`launch`와의 차이**: `withContext`는 결과를 기다리고 반환값이 있다. `launch`는 기다리지 않고 `Job`만 준다.
- **처음 등장**: 4단계 강의 3 `SearchHistoryRecorder.save`

## `Dispatchers.IO`

- **뜻**: 블로킹 I/O(DB, 파일, 네트워크)를 위한 스레드 풀. 스레드 이름은 `DefaultDispatcher-worker-N`(`Dispatchers.Default`와 스레드를 공유).
  동시에 최대 64개(또는 CPU 코어 수 중 큰 값)까지 스레드를 늘려 쓴다.
- **다른 Dispatcher**: `Dispatchers.Default`(CPU 계산용, 코어 수만큼), `Dispatchers.Main`(안드로이드 등 UI 스레드), `Dispatchers.Unconfined`(특수).
  `runBlocking`은 자기 스레드(여기서는 main)를 쓴다.
- **처음 등장**: 4단계 강의 3

## `CoroutineContext`와 `+`

- **뜻**: 코루틴이 들고 다니는 설정 묶음. 요소로 `Job`, `CoroutineDispatcher`, `CoroutineExceptionHandler`, `CoroutineName` 등이 있다.
- **`+`**: `SupervisorJob() + Dispatchers.IO + handler`처럼 요소를 합쳐 하나의 컨텍스트를 만든다. 같은 종류가 겹치면 오른쪽이 이긴다.
- **`coroutineContext.job`**: 컨텍스트에서 `Job` 요소를 꺼낸다(`val CoroutineContext.job: Job`).
- **처음 등장**: 4단계 강의 3

## `CoroutineScope(...)` 만들기

```kotlin
fun CoroutineScope(context: CoroutineContext): CoroutineScope
```

- **하는 일**: 주어진 컨텍스트로 새 스코프를 만든다. 컨텍스트에 `Job`이 없으면 `Job()`을 넣어 준다.
- **구조화된 동시성 밖**: 이렇게 만든 스코프는 `runBlocking`이나 호출한 코루틴의 **자식이 아니다.** 그래서 부모가 끝나기를 기다려 주지 않는다.
  실험: `joinPending()`을 빼면 프로그램이 먼저 끝나 저장 C·D가 로그 없이 사라졌다. 수명은 만든 쪽이 직접 관리한다(기다리기 `join`, 끄기 `cancel`).
- **쓰는 곳**: 화면·요청보다 오래 사는 "백그라운드 작업 담당" 객체(예: 앱 전체 수명의 기록기).
- **처음 등장**: 4단계 강의 3

## `SupervisorJob`

```kotlin
fun SupervisorJob(parent: Job? = null): CompletableJob
```

- **뜻**: 자식이 실패해도 자신과 다른 자식을 취소하지 않는 `Job`. `supervisorScope`(3단계)의 Job 버전으로, 스코프를 직접 만들 때 쓴다.
- **실험**: `Job()`으로 바꾸면 저장 C 실패 순간 스코프 전체가 취소되어, 나중에 요청한 저장 E가 실행되지 않고 `isCancelled=true`.
  `SupervisorJob()`이면 저장 E가 정상 실행된다.
- **처음 등장**: 4단계 강의 3

## `CoroutineExceptionHandler`

```kotlin
inline fun CoroutineExceptionHandler(crossinline handler: (CoroutineContext, Throwable) -> Unit): CoroutineExceptionHandler
```

- **뜻**: 아무도 받아 주지 않은 예외(uncaught)를 마지막으로 받는 컨텍스트 요소.
- **언제 불리나**: **루트 코루틴**을 `launch`로 띄웠을 때 그 안에서 난 예외. `SupervisorJob` 바로 아래 자식은 루트처럼 취급된다.
  `async`의 예외는 `Deferred`에 보관되어 `await()`로 받으므로 핸들러로 가지 않는다(그래서 3단계에서는 쓰지 않음).
- **실행 스레드**: 예외가 난 스레드에서 불린다(로그: `DefaultDispatcher-worker-1 @coroutine#7`).
- **한계**: 예외를 "기록"할 뿐 코루틴을 되살리지 않는다. 그 코루틴은 이미 실패로 끝났다.
- **없으면**: 스레드의 기본 예외 처리기로 가서 `Exception in thread "DefaultDispatcher-worker-2 @coroutine#7" ...` 스택 트레이스가 출력된다(프로그램은 계속).
- **처음 등장**: 4단계 강의 3

## `Flow`, `flow { }`, `emit`, `collect`

```kotlin
interface Flow<out T> { suspend fun collect(collector: FlowCollector<T>) }
fun <T> flow(block: suspend FlowCollector<T>.() -> Unit): Flow<T>
suspend fun emit(value: T)            // FlowCollector<T>
suspend fun Flow<T>.collect(action: suspend (T) -> Unit)
```

- **뜻**: 시간에 따라 값을 **여러 번** 내보내는 비동기 흐름. `suspend fun`이 값 하나를 돌려준다면 `Flow`는 값 여러 개를 차례로 내보낸다.
- **차가운 흐름(cold)**: `flow { }`는 흐름을 **정의만** 한다. `collect`를 부를 때마다 블록이 처음부터 실행된다. 5단계 실행에서 흐름을 만든 뒤에도
  `collect` 전까지 조회가 한 번도 일어나지 않았다.
- **`emit`**: 값을 하나 내보낸다. 내보낸 값이 아래 연산자와 `collect` 블록까지 처리될 때까지 suspend한다(한 번에 한 값씩 흐른다).
- **`collect`**: 흐름을 실제로 돌리는 **종단 연산자**. suspend 함수라서 흐름이 끝날 때까지 기다린다. 새 코루틴을 만들지 않고, 부른 코루틴 안에서 블록이 실행된다
  (5단계 로그가 모두 `main @coroutine#1`).
- **처음 등장**: 5단계 `PriceWatcher.watch`, `Main`

## Flow 중간 연산자

| 연산자 | 하는 일 | 5단계에서 |
|---|---|---|
| `onEach { }` | 값을 그대로 흘려보내면서 곁가지 작업(로그) | `가격 확인` 로그 |
| `map { }` | 값을 바꿈 | `FlightQuote` → 가격 `Int` |
| `distinctUntilChanged()` | 직전 값과 같으면 버림 | 289,000 중복 제거 |
| `filter { }` | 조건에 맞는 값만 통과 | 280,000원 이하만 |
| `take(n)` | n개를 받으면 흐름을 끝냄. 위쪽 흐름은 취소된다 | 알림 2번 뒤 `while (true)` 루프까지 멈춤 |
| `onCompletion { }` | 흐름이 끝날 때(정상·취소·예외) 한 번 실행 | `감시 종료` 로그 |

- 중간 연산자는 새 `Flow`를 돌려줄 뿐 아무것도 실행하지 않는다. 실행은 `collect` 때 한꺼번에 일어난다.
- **처음 등장**: 5단계

## `combine`

```kotlin
inline fun <reified T, R> combine(vararg flows: Flow<T>, crossinline transform: suspend (Array<T>) -> R): Flow<R>
inline fun <reified T, R> combine(flows: Iterable<Flow<T>>, crossinline transform: suspend (Array<T>) -> R): Flow<R>
```

- **하는 일**: 여러 흐름의 **각자 가장 최근 값**을 모아 `transform`으로 하나의 값을 만든다. 어느 흐름이든 새 값을 내면 다시 계산해 내보낸다.
- **첫 값**: 모든 흐름이 **최소 한 번씩** 값을 내야 첫 결과를 낸다.
- **6단계에서**: 세 항공사 흐름의 최신 견적 배열 → `minBy { it.price }` → 현재 최저가 견적.
- **처음 등장**: 6단계 `LowestPriceTracker`

## `StateFlow`, `stateIn`

```kotlin
interface StateFlow<out T> : SharedFlow<T> { val value: T }
fun <T> Flow<T>.stateIn(scope: CoroutineScope, started: SharingStarted, initialValue: T): StateFlow<T>
```

- **`StateFlow`**: **항상 값을 하나 들고 있는** 뜨거운 흐름. `value`로 아무 때나 최신 값을 바로 읽고, `collect`하면 현재 값부터 바뀔 때마다 받는다.
  **같은 값(equals)이면 다시 내보내지 않는다.** 6단계에서 최저가가 그대로인 갱신은 화면에 가지 않았다.
- **차가운 흐름과의 차이**: `flow { }`는 `collect`할 때마다 처음부터 실행된다. `StateFlow`는 모으는 사람이 없어도 이미 돌고 있고, 여러 곳에서 모아도 원천은 하나다.
- **`stateIn`**: 차가운 흐름을 `StateFlow`로 바꾼다. `scope`에 공유용 코루틴을 띄워 원천 흐름을 모은다. 이 코루틴은 스코프가 끝날 때까지 계속 돈다
  (6단계에서 `trackerScope.cancel()`로 종료).
- **`SharingStarted`**: `Eagerly`(바로 시작), `Lazily`(첫 구독자가 생기면 시작), `WhileSubscribed()`(구독자가 있을 때만).
- **주의**: `take(n)`으로 받을 때 `StateFlow`는 같은 값을 건너뛰므로 n개가 영영 안 올 수 있다(6단계 `take(5)`로 멈춘 경험).
- **처음 등장**: 6단계
