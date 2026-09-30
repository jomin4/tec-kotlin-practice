# Kotlin 코루틴 실습: 여행 항공권 가격 비교 서비스

**여러 항공사에 가격을 묻고 최저가를 찾는 서비스**를 8단계로 키워 가며 Kotlin 코루틴을 실습한 저장소입니다.
단계마다 "문제 하나 → 코루틴 해결책 하나"를 코드로 구현하고, 실제로 실행해 로그와 시간으로 동작을 확인했습니다.

- 언어/빌드: Kotlin 2.4.20 (JVM 21), Gradle 8.14.3 Wrapper
- 라이브러리: `kotlinx-coroutines-core` 1.11.0, 테스트 `kotlinx-coroutines-test` + JUnit 5
- 외부 서버 없이 동작합니다. 항공사 API와 DB는 `delay`·`Thread.sleep`으로 지연과 실패를 흉내 낸 가짜 구현입니다.

## 실습 단계

| 단계 | 풀어 본 문제 | 코루틴 해결책 | 확인한 결과 |
|---|---|---|---|
| 1 | 항공사 5곳을 하나씩 물으면 느리다 | `coroutineScope`, `async` / `awaitAll` | 순차 약 4,200ms → 병렬 약 1,200ms(가장 느린 한 곳의 시간) |
| 2 | 응답이 안 오는 항공사 하나가 전체를 붙잡는다 | `withTimeoutOrNull`, `CancellationException`, 협력적 취소(`yield`) | 항공사별 1.5초 제한. 취소를 확인하지 않는 CPU 작업은 제한을 무시하고 다른 코루틴까지 멈춘다 |
| 3 | 한 곳이 예외로 실패하면 조회 전체가 죽는다 | 예외 전파 규칙, `supervisorScope`, `await()`별 예외 처리 | `coroutineScope`는 300ms에 전체 실패, `supervisorScope`는 실패한 한 곳만 "실패 목록"에 |
| 4 | 블로킹 DB 저장이 main 스레드(화면)를 멈춘다 | `withContext(Dispatchers.IO)`, `launch`, `SupervisorJob`, `CoroutineExceptionHandler` | 직접 저장 시 화면 갱신 약 900ms 멈춤 → IO로 옮기면 계속 갱신, 저장 실패는 핸들러가 격리 |
| 5 | 가격을 계속 감시하다 목표가 이하일 때만 알리고 싶다 | `Flow`, `flow { }`, `collect`, `map`·`distinctUntilChanged`·`filter`·`take` | 가격이 바뀔 때만 감지, 목표가 이하 2번 알림 후 감시 종료 |
| 6 | 여러 항공사 가격 흐름에서 "지금 최저가"를 항상 알고 싶다 | `combine`, `StateFlow`, `stateIn` | 최저가가 바뀔 때만 화면 갱신, `value`로 아무 때나 최신 최저가 조회 |
| 7 | 동시 요청이 몰리면 항공사 API가 거절한다 | `Semaphore`, `Channel`, 작업자 여러 명(fan-out) | 제한 없음 6건 중 4건 실패 → `Semaphore(2)`로 6건 모두 성공, 작업자 3명이 7건 분배 처리 |
| 8 | 시간이 걸리는 코드를 빠르게, 시간까지 검증하고 싶다 | `runTest`, 가상 시간, `advanceTimeBy`, `backgroundScope` | 테스트 10개 통과, 30초 대기 시나리오가 실제 2ms |

단계별 자세한 기록(목차, 실행 로그, 실험, 코드 스냅샷)은 [`docs/steps/`](docs/steps)에 있습니다.

## 실행 방법

```bash
# 1~7단계 데모를 차례로 모두 실행
./gradlew run

# 원하는 단계만 실행 (예: 3단계)
./gradlew run --args="3"

# 8단계: 테스트 실행
./gradlew test
```

로그는 `[경과 시간] [스레드 @코루틴 번호] 메시지` 형식으로 찍힙니다. 어느 코루틴이 어느 스레드에서 언제 실행되는지를 그대로 볼 수 있습니다.

```
[ 1209ms] [main @coroutine#1   ] ===== 병렬 조회 끝: 1209ms / 최저가 진에어 275,000원 =====
[ 3587ms] [DefaultDispatcher-worker-1 @coroutine#1] DB 저장 시작: 저장 B
```

## 프로젝트 구조

```
src/main/kotlin/com/travel
├── Main.kt                         단계 번호로 데모 선택 실행
├── demo/Step1Demo.kt ~ Step7Demo.kt   단계별 실행 시나리오
├── model/Flight.kt                 SearchRequest, FlightQuote, SearchResult, ProviderAnswer, ProviderException
├── provider/                       항공사 API (가짜 구현)
│   ├── FlightProvider.kt           suspend fun search()
│   ├── FakeFlightProvider.kt       delay 후 가격 응답 (1단계~)
│   ├── HeavyParsingFlightProvider.kt  CPU 파싱, yield 협력 여부 (2단계)
│   ├── FlakyFlightProvider.kt      일정 시간 뒤 예외 (3단계)
│   ├── ScriptedFlightProvider.kt   부를 때마다 가격이 바뀜 (5단계~)
│   ├── FragileFlightProvider.kt    허용 동시 요청 수 초과 시 거절 (7단계)
│   └── RateLimitedFlightProvider.kt  Semaphore로 동시 요청 제한 (7단계)
├── repository/SearchHistoryRepository.kt  블로킹 DB 저장 흉내 (4단계)
├── service/
│   ├── PriceComparisonService.kt   순차/병렬/타임아웃/supervisorScope 조회 (1~3단계)
│   ├── SearchHistoryRecorder.kt    withContext(IO), 전용 스코프 + launch (4단계)
│   ├── PriceWatcher.kt             주기적 가격 Flow (5단계)
│   ├── LowestPriceTracker.kt       combine + StateFlow (6단계)
│   └── SearchQueue.kt              Channel 대기열 + 작업자 (7단계)
└── util/Log.kt                     경과 시간·스레드·코루틴 번호 로그

src/test/kotlin/com/travel         runTest 가상 시간 테스트 (8단계)
```

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/COROUTINE_API.md`](docs/COROUTINE_API.md) | 실습에서 쓴 코루틴 API와 관련 Kotlin 문법 카드 모음 (시그니처, 동작, 주의점, 실험으로 확인한 사실) |
| [`docs/steps/stepNN.md`](docs/steps) | 단계별 강의 목차, 실행 결과, 실험, 코드 스냅샷 |
| [`docs/diagrams/`](docs/diagrams) | 1~4단계 설명에 쓴 Excalidraw 그림(elements JSON) |
| [`docs/PROGRESS.md`](docs/PROGRESS.md) | 진행 기록과 결정 사항 |
| [`CLAUDE.md`](CLAUDE.md) | 실습 진행 방식 (Claude Code와 함께 진행한 강의 형식 규칙) |

## 실습에서 얻은 핵심

1. **`async`는 기다리지 않고, `await`가 기다린다.** 동시성은 `async`/`launch`가 만들고, `suspend`는 "멈출 수 있다"는 표시일 뿐이다.
2. **`delay`는 코루틴만 멈추고, `Thread.sleep`은 스레드를 멈춘다.** 스레드를 붙잡는 코드 하나가 같은 스레드의 모든 코루틴을 멈춘다.
3. **취소는 협력적이다.** suspend 지점(`delay`, `yield` 등)에서만 확인된다. `CancellationException`은 잡았다면 반드시 다시 던진다.
4. **실패는 부모를 타고 번진다.** `coroutineScope`는 자식 하나의 실패로 전체를 취소하고, `supervisorScope`·`SupervisorJob`은 실패를 그 자식에 가둔다.
5. **블로킹은 `withContext(Dispatchers.IO)`로 옮긴다.** 직접 만든 `CoroutineScope`는 구조화된 동시성 밖이라 수명(기다리기·취소)을 직접 챙긴다.
6. **`Flow`는 차갑고, `StateFlow`는 뜨겁다.** `flow { }`는 `collect`할 때 돌고, `StateFlow`는 항상 최신 값을 들고 있으며 같은 값은 다시 내보내지 않는다.
7. **동시성은 `Semaphore`로 제한하고 `Channel`로 줄 세운다.** 가득 찬 채널의 `send`는 suspend해서 보내는 쪽 속도를 자연스럽게 맞춘다.
8. **`runTest`의 가상 시간으로 "얼마나 걸려야 하는지"까지 테스트한다.** 단, `Thread.sleep`과 `Dispatchers.IO`는 가상 시간 밖이다.
