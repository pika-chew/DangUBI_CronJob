---
name: package-structure
description: DangUBI CronJob 서버의 패키지·폴더 구조 컨벤션(batch / domain / infra / common 레이어, Job별 하위 패키지, 클래스 이름 규칙, 의존 방향). 새 Job·Step·클래스·패키지를 만들거나, 파일을 옮기거나, 클래스를 어디에 둘지 정할 때 반드시 먼저 읽는다.
---

# 패키지 구조 컨벤션

참고 프로젝트 두 개를 Spring Batch 크론잡에 맞게 바꾼 것이다. 이 프로젝트의 베이스 패키지는 `com.pikachew.dangUBI`이고, 아래 경로는 모두 `src/main/java/com/pikachew/dangUBI/` 기준이다.

- 폴더 구조·도메인 구성·코드 스타일: DangCharge Producer (`/Users/hann/Project/picka-chew/DangCharge-BE/dangcharge_Producer`)
- 예외 처리: SAFORI-Server (`/Users/hann/Project/SAFORI/SAFORI-Server`)

이 문서와 참고 프로젝트 코드가 다르면 참고 프로젝트 코드가 기준이다. 두 프로젝트는 레포 밖이라 읽기 전에 사용자에게 확인받는다.

HTTP 요청을 받지 않는 배치 전용 애플리케이션이므로 컨트롤러, 요청·응답 DTO, `security` 레이어는 두지 않는다. API 서버의 `api` 레이어 자리를 `batch` 레이어가 맡는다.

## 최상위 레이어

| 패키지 | 역할 |
| --- | --- |
| `batch` | 배치의 진입점과 처리 흐름. Job·Step 설정, Reader·Processor·Writer, Tasklet, 리스너, 스케줄러 |
| `domain` | 비즈니스 규칙과 영속성. 엔티티, 리포지토리, 도메인 서비스 |
| `infra` | 외부 시스템 연동 구현체. 외부 API, 메시지 브로커, 푸시 등 |
| `common` | 어느 기능에도 속하지 않는 공용 코드 |

베이스 패키지 바로 아래에는 `DangUbiApplication` 하나만 둔다.

## 의존 방향

```
batch ──▶ domain ──▶ common
  ▲         ▲
  │         │
infra ──────┘
```

- `domain`은 `batch`, `infra`를 import하지 않는다. `common`만 쓴다.
- `batch`는 `domain`과 `common`을 쓴다. 쓰기 로직은 Writer·Tasklet에서 직접 구현하지 않고 `DomainService`를 호출한다.
- `infra`는 `domain`이나 `batch`에 정의된 인터페이스(포트)를 구현하는 방향으로만 의존한다. `batch`와 `domain`이 `infra`의 구체 클래스에 의존하게 만들지 않는다.
- `common`은 특정 기능 패키지에 의존하지 않는다.

## Job·기능별 하위 패키지

`batch` 아래는 Job 이름으로, `domain` 아래는 저장하는 업무 대상 명사(`trip`, `driver` 등)로 한 번 더 나눈다. 엔티티가 없는 개념(파일 형식, 메시지 해석 등)으로 도메인을 만들지 않는다. 패키지 이름은 소문자 단수형이다. 아래 하위 패키지 중 필요한 것만 만들고, 빈 패키지를 미리 만들어두지 않는다.

```
batch/
├── common/
│   ├── listener/        여러 Job이 같이 쓰는 JobExecutionListener 등
│   └── incrementer/     공용 JobParametersIncrementer   (필요 시)
└── {job}/
    ├── config/          {Job}JobConfig            Job·Step 빈 정의
    ├── reader/          {Name}Reader
    ├── processor/       {Name}Processor
    ├── writer/          {Name}Writer
    ├── tasklet/         {Name}Tasklet             (필요 시)
    ├── listener/        {Name}Listener            (필요 시)
    ├── dto/             Step 사이를 오가는 중간 데이터 (필요 시)
    ├── scheduler/       {Job}JobScheduler         (필요 시)
    └── port/            외부 연동 인터페이스        (필요 시)

domain/
├── common/
│   └── entity/          BaseTimeEntity
└── {feature}/
    ├── entity/          엔티티, 엔티티가 쓰는 enum
    ├── repository/      {Entity}Repository
    ├── adaptor/         {Entity}Adaptor, {Entity}AdaptorImpl
    ├── service/         {Feature}DomainService, {Feature}DomainServiceImpl
    ├── exception/       {Feature}Handler               (필요 시)
    ├── model/           {Name}Snapshot (외부에서 읽은 아직 저장 전 값), {Name}Result (처리 결과) (필요 시)
    ├── policy/          {Name}Policy                   (필요 시)
    └── validator/       {Feature}Validator, {Feature}ValidatorImpl (필요 시)

infra/
└── {system}/            연동 대상 이름. 예: swift, sqs, kafka
    ├── config/          {System}Config, {System}Properties
    ├── {System}Client.java              외부 시스템 접근
    ├── {System}{Name}Reader.java 등     batch 포트 구현체
    ├── {Name}Parser.java                외부 파일·메시지 형식 해석
    └── {Name}Exception.java             인프라 예외 (RuntimeException)

common/
├── annotation/          @Adaptor, @DomainService, @Validator
├── config/              {Name}Config  (필요 시)
├── consts/              {Name}StaticValues
├── exception/           BaseCode, BaseErrorCode, Reason, ErrorStatus, GeneralException
└── util/                {Name}Utils
```

`batch`의 Job 이름과 `domain`의 기능 이름이 1:1일 필요는 없다. `batch`는 "무엇을 언제 처리하는가" 관점으로, `domain`은 데이터·규칙 관점으로 나눈다. Job 하나가 여러 도메인을 쓸 수 있다.

## 클래스별 규칙

| 종류 | 위치 | 이름 | 규칙 |
| --- | --- | --- | --- |
| Job 설정 | `batch/{job}/config` | `{Job}JobConfig` | `@Configuration`. Job 이름을 `public static final String JOB_NAME`으로 두고, Job 빈은 `{job}Job`, Step 빈은 `{job}{Name}Step`으로 짓는다 |
| Reader | `batch/{job}/reader` | `{Name}Reader` | 조회 전용. 대량 조회는 페이징·커서 Reader를 쓰고, 단건 조회는 `Adaptor`를 쓴다 |
| Processor | `batch/{job}/processor` | `{Name}Processor` | 변환·필터링만 한다. 저장하지 않는다. 걸러낼 항목은 `null`을 반환한다. 포트에서 올라온 인프라 예외는 여기서 `ErrorStatus`로 바꿔 던진다 |
| 포트 | `batch/{job}/port` | 역할 이름 (예: `TelemetryFileReader`) | 외부 데이터를 도메인 모델(`Snapshot`)이나 Step 중간 데이터로 돌려준다. 저장 경로·파일 형식을 드러내지 않는다 |
| Writer | `batch/{job}/writer` | `{Name}Writer` | 저장·변경은 `DomainService`에 위임한다 |
| Tasklet | `batch/{job}/tasklet` | `{Name}Tasklet` | chunk로 나눌 필요 없는 단일 작업(정리, 집계, 알림 등)에만 쓴다 |
| 스케줄러 | `batch/{job}/scheduler` | `{Job}JobScheduler` | 앱 안에서 cron으로 실행할 때만 만든다. Job 실행만 하고 로직은 두지 않는다. cron 표현식은 `application.yaml`에서 주입받는다 |
| 엔티티 | `domain/{feature}/entity` | 명사 | `BaseTimeEntity` 상속(생성·수정 시각은 Hibernate `@CreationTimestamp`·`@UpdateTimestamp`). 생성은 private 생성자에 `@Builder`를 붙여 builder로만 한다. 정적 생성 메서드(`of`, `start` 등)나 public 생성자를 두지 않고, 기본 생성자는 `@NoArgsConstructor(access = PROTECTED)`. 외부 값(Snapshot)을 엔티티로 옮기는 일은 DomainService에서 builder로 한다. 상태 변경은 setter 대신 의도가 드러나는 메서드로 한다 |
| 모델 | `domain/{feature}/model` | `{Name}Snapshot`, `{Name}Result` | record. 외부에서 읽은 저장 전 값은 `Snapshot`, DomainService 처리 결과는 `Result` |
| 리포지토리 | `domain/{feature}/repository` | `{Entity}Repository` | `JpaRepository` 상속 인터페이스 |
| Adaptor | `domain/{feature}/adaptor` | `{Entity}Adaptor` + `Impl` | `@Adaptor`. 조회 전용이고 메서드는 `query...`로 시작한다. 없으면 `{Feature}Handler`의 예외를 던진다 |
| DomainService | `domain/{feature}/service` | `{Feature}DomainService` + `Impl` | `@Transactional @DomainService @RequiredArgsConstructor`. 생성·변경 같은 쓰기 로직을 맡는다. 호출한 쪽(chunk) 트랜잭션에 합류한다. 다른 도메인의 쓰기는 그 도메인의 DomainService를 부른다 |
| Policy | `domain/{feature}/policy` | `{Name}Policy` | 빈이 아닌 `final` 클래스 + static 메서드, private 생성자 |
| Validator | `domain/{feature}/validator` | `{Feature}Validator` + `Impl` | `@Validator`. 비즈니스 규칙 검증 |
| 도메인 예외 | `domain/{feature}/exception` | `{Feature}Handler` | `GeneralException` 상속, `public` 생성자 `(BaseErrorCode code)`. 예외는 `public static final GeneralException X = new {Feature}Handler(ErrorStatus.{FEATURE}_X)` 상수로만 정의하고 상세 메시지는 받지 않는다. 처음 필요할 때 만든다 |
| 인프라 예외 | `infra/{system}` | `{Name}Exception` | `RuntimeException` 상속, 메시지(·원인)만 받는다. 호출한 쪽(Processor·Tasklet)이 `ErrorStatus`로 바꿔 던진다 |
| 에러 코드 | `common/exception` | `ErrorStatus` | `BaseErrorCode` 구현 enum 하나에 `(Integer code, String message)`로 모은다. 기능별로 코드 번호대를 나눠 주석으로 구분한다 (예: `// 수집 적재 오류 (6000번대)`). HTTP 응답이 없어 HTTP 상태는 두지 않는다 |

- 인터페이스와 구현체는 같은 패키지에 두고, 구현체 이름은 인터페이스 이름에 `Impl`을 붙인다.
- `batch`가 외부 시스템을 호출해야 하면 `batch/{job}/port`에 인터페이스를 두고 구현체는 `infra/{system}`에 둔다. 외부 파일·메시지의 형식(gz, JSONL, CSV, 필드 이름)과 저장 경로 규칙은 infra가 안다.
- import 순서: static → `java` → `org` → `com.pikachew` → `jakarta`·`lombok`·기타, 그룹 사이 빈 줄.
- `@Service` 대신 `common/annotation`의 `@Adaptor`, `@DomainService`, `@Validator`를 쓴다. 이 어노테이션들이 아직 없으면 처음 필요한 시점에 `common/annotation`에 만든다.
- Job·Step 이름 문자열은 `JobConfig`의 상수로만 참조하고, 다른 곳에 하드코딩하지 않는다.
- 고정값(경로 prefix, 날짜 포맷, 정규식, 메시지 타입 코드 등)은 클래스 안에 `private static final`로 두지 않고 `common/consts/{Name}StaticValues`에 `public static final`로 모은다. 쓰는 쪽은 static import한다.
- 특정 기능에 묶이지 않는 함수(JSON 값 꺼내기 등)는 `common/util/{Name}Utils`에 static 메서드로 둔다. 상수 클래스와 Utils 클래스는 `final`이고 생성자는 private이다. Utils에도 단위 테스트를 쓴다.
- 기능 예외를 던지는 검증처럼 특정 도메인 규칙이 들어간 함수는 Utils로 빼지 않고 그 도메인 클래스에 둔다.

## Job 한 번의 흐름

```
Scheduler(또는 외부 cron) ─▶ Job ─▶ Step
                                     ├─ chunk:   Reader ─▶ Processor ─▶ Writer ─▶ DomainService ─▶ Repository
                                     └─ tasklet: Tasklet ─▶ Adaptor / DomainService
```

Job·Step은 흐름만 조합하고, 규칙 자체는 `domain`에 둔다. Job은 같은 파라미터로 다시 돌려도 결과가 같도록(멱등) 만든다.

## 테스트

코드를 만들 때 테스트도 같이 만든다. 클래스 단위로 단위 테스트를 쓰고, 상위 레이어로 갈수록 그 레이어에 필요한 통합 테스트를 더한다.

| 대상 | 종류 | 방법 |
| --- | --- | --- |
| Tasklet, Processor, Policy, Validator, 엔티티, infra 파서·포트 구현체 등 | 단위 | 스프링 컨텍스트 없이 직접 생성. 의존 대상은 Mockito Mock. Step 실행 정보가 필요하면 `MetaDataInstanceFactory` |
| Reader, Writer, Adaptor, DomainService | DB 통합 | `@SpringBootTest` 또는 `@DataJpaTest` + H2 |
| Job·Step | Job 통합 | `@SpringBatchTest` + `@SpringBootTest`, `JobOperatorTestUtils`로 Job·Step 실행. 끝나면 `JobRepositoryTestUtils.removeJobExecutions()` |

- 데이터베이스는 외부 MySQL 대신 H2 메모리 DB(MySQL 호환 모드)를 쓴다. 설정은 `src/test/resources/application-test.yaml`에 있고, 스프링 테스트에는 `@ActiveProfiles("test")`를 붙인다.
- H2가 지원하지 않는 MySQL 전용 기능(`GET_LOCK` 등)은 인터페이스 뒤로 숨기고 테스트에서 대체 구현을 쓴다.
- `src/test/java` 아래에 대상 클래스와 같은 패키지 경로로 두고, 이름은 `{대상클래스}Test`로 한다. Job 통합 테스트는 `batch/{job}` 바로 아래에 `{Job}JobTest`로 둔다.
- 테스트 메서드 이름은 한글로 동작을 설명한다. 예: `같은_targetDate로_이미_완료됐으면_다시_실행되지_않는다`

## 리소스

`src/main/resources`에는 `application.yaml`과 프로필별 `application-{profile}.yaml`을 둔다. cron 표현식, chunk 크기 같은 배치 설정값은 여기에 두고 코드에 하드코딩하지 않는다.
