# 진행 기록

세션이 바뀌어도 이 문서만 보면 현재 위치를 알 수 있게 유지한다. 단계가 끝날 때마다 Claude가 갱신한다.

## 현재 상태

- **단계**: 3 (하나가 실패하면 전체가 죽는 문제: 예외 전파와 supervisorScope)
- **강의**: 3단계 마무리 글 설명 완료 → 반영 확인 대기 (목차: `docs/steps/step03.md`)
- **다음 할 일**: 사용자가 확인하면 3단계 코드를 `src/`에 커밋하고 4단계 도입 글 + 강의 1 시작

## 프로젝트 개요

- 주제: **여행 상품 가격 비교 및 가격 알림 서비스**
  - 여러 항공사 API(가짜 구현: `delay` + 고정/랜덤 응답)에 가격을 동시에 묻고 최저가를 모은다.
  - 이후 단계에서 타임아웃, 부분 실패, 블로킹 I/O, 주기적 가격 감시(Flow), 알림, 요청 제한, 테스트로 확장한다.
- 패키지: `com.travel` (`model`, `util`, `provider`, `service`)

## 단계 로드맵

| 단계 | 내용 | 주요 코루틴 개념 | 상태 |
|---|---|---|---|
| 0 | 진행 방식 합의, 저장소 준비 | - | 완료 |
| 1 | API 5개 순차 호출 vs 병렬 호출 | `suspend`, `runBlocking`, `coroutineScope`, `async`/`awaitAll` | 완료 |
| 2 | 응답이 안 오는 API | `withTimeoutOrNull`, `CancellationException`, 협력적 취소(`yield`) | 완료 |
| 3 | 하나가 실패하면 전체가 죽는 문제 | 예외 전파, `supervisorScope`, `await` 예외 처리 | 완료 |
| 4 | 블로킹 라이브러리(DB 저장) 섞기 | `Dispatchers.IO`, `withContext`, `launch`, `CoroutineExceptionHandler` | 예정 |
| 5 | 가격을 주기적으로 감시 | `Flow`, `map`/`filter`/`distinctUntilChanged` | 예정 |
| 6 | 여러 가격 흐름을 합쳐 현재 최저가 유지 | `combine`, `StateFlow` | 예정 |
| 7 | 요청이 몰리면 API가 차단 | `Semaphore`, `Channel` | 예정 |
| 8 | 시간이 걸리는 코드를 빠르게 테스트 | `runTest`, 가상 시간 | 예정 |

로드맵은 진행하면서 조정할 수 있다.

## 단계별 기록

### 0단계: 준비
- `CLAUDE.md`에 진행 방식을 정리했다.
- `docs/PROGRESS.md`(이 문서)로 진행 상태를 기록하기 시작했다.
- SessionStart 훅(`.claude/hooks/session-start.sh`)을 추가해 세션마다 현재 상태를 자동으로 보여주게 했다.

### 1단계: 순차 호출 vs async 병렬 호출
- 그림 6장: `docs/diagrams/step01-1-file-map.json` ~ `step01-6-delay-vs-sleep.json` (배치 순서는 `docs/steps/step01.md`)
- 제공 코드 원본: `docs/steps/step01.md`
- 1단계 설명을 블로그 글 형식으로 다시 제공했고, 사용자가 이 형식을 확정했다.
- 설명 대상 파일: `Main.kt`, `service/PriceComparisonService.kt`, `provider/FlightProvider.kt`, `provider/FakeFlightProvider.kt` (반영 완료)
- 환경 코드(반영 완료): `model/Flight.kt`, `util/Log.kt`
- 검증 결과: 순차 ≈4238ms, 병렬 ≈1212ms, 최저가 진에어 275,000원
- 사용자 이해 확인 후 `src/`에 반영 완료

### 2단계: 응답이 안 오는 API: 타임아웃과 협력적 취소
- 강의 목차, 그림 배치, 코드 스냅샷: `docs/steps/step02.md`
- 검증 결과: search 1(양보 없음) ≈3105ms 최저가 에어부산, 시간 초과 [대한항공, 제주항공, 티웨이] / search 2(yield) ≈1513ms 최저가 제주항공, 시간 초과 [티웨이, 에어부산]
- 마무리에서 `Main`의 빈 결과 처리(`firstOrNull`) 수정
- 사용자 확인 후 `src/`에 반영 완료

### 3단계: 하나가 실패하면 전체가 죽는 문제
- 강의 목차, 그림 배치, 코드 스냅샷, 실험 결과: `docs/steps/step03.md`
- 검증 결과: coroutineScope 조회 → 약 300ms에 전체 실패 / supervisorScope 조회 → 1515ms, 최저가 제주항공, 시간 초과 [티웨이], 실패 [진에어]
- 사용자 확인 후 `src/`에 반영 완료

## 결정 기록

- 사용자는 코드 이해에 집중하고, 환경 설정과 Git은 Claude가 전담한다.
- 개발 환경은 클라우드 세션에만 둔다. 사용자는 코드와 그림을 읽고 이해하고, 코드 반영·실행·Git은 Claude가 한다 (2026-09-29 변경).
- 진행 방식이 바뀌면 `CLAUDE.md`를 즉시 갱신한다 (`CLAUDE.md` 9번).
- 주제: 1번 "여행 상품 가격 비교 및 가격 알림 서비스" (2026-09-29)
- 빌드: Kotlin 2.4.20, kotlinx-coroutines 1.11.0, JDK 21 toolchain, Gradle 8.14.3 wrapper
- 로그에 코루틴 이름을 보이려고 `util/Log.kt`의 `initLogging()`에서 `kotlinx.coroutines.debug`를 켠다.
- `gradlew run` 출력 한글 깨짐 방지로 `-Dstdout.encoding=UTF-8`을 준다.
- 설명 형식: 블로그 글처럼 설명 자리마다 작은 그림을 배치한다 (`CLAUDE.md` 5번).
- 진행 단위: 단계를 파일 단위 강의로 나눠 한 번에 파일 하나씩 설명한다 (`CLAUDE.md` 3번).
- 설명 깊이: 코루틴 API와 관련 Kotlin 문법은 문법 카드로 철저히 설명하고 `docs/COROUTINE_API.md`에 누적한다 (`CLAUDE.md` 4-1).
- 설명 방식: 값 추적 흐름 서술(실제 값·타입·코루틴 상태·시간을 따라 한 동작씩)과 값 추적 표 (`CLAUDE.md` 4-0).
- 속도 조절: 핵심 코드만 깊게 설명하고 연결부·보일러플레이트는 짧게 넘긴다 (`CLAUDE.md` 4-0-3, 2026-09-30).
- 로드맵 조정: `CoroutineExceptionHandler`는 `launch`와 함께 4단계에서 다룬다(`async` 예외는 `await`로 받으므로 3단계에는 맞지 않음) (2026-09-30).
- 강의 글 형식: 파일 전체 코드 → 덩어리별 `코드 박스 → 설명`, 수정 파일은 바뀐 부분만 (`CLAUDE.md` 5-1, 2026-09-30).
- 설명 순서: 코드 읽는 순서(선언부 → 매개변수 실제 값 → 스코프 진입 → 본문, 람다는 풀어서)로 확정 (`CLAUDE.md` 4-0-1, 2026-09-30).
