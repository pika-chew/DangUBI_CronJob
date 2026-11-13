# 로컬 실행 (local 프로필)

S3·Swift·SQS 없이 reconcileJob과 ingestJob을 로컬에서 돌린다. local 프로필에서는 외부 연동 구현체 대신 로컬 디렉터리를 읽는 구현체가 주입된다.

| 포트 | 운영 구현체 | local 프로필 구현체 | 하는 일 |
| --- | --- | --- | --- |
| `SwiftClient` | `SwiftClientImpl` | `LocalSwiftClient` | `{root}/swift` 아래 파일을 Swift 객체로 읽는다 |
| `SourceObjectStore` | `S3SourceObjectStore` | `LocalSourceObjectStore` | `{root}/s3` 아래 파일을 S3 원본 객체로 읽는다 |
| `RequeuePublisher` | `SqsRequeuePublisher` | `LocalRequeuePublisher` | 누락 객체를 로그로만 남긴다. 수집 워커가 없으므로 파일을 옮기지 않는다 |

키 규칙(`TelemetryObjectKeys`)과 gz·JSONL 해석(`TelemetryJsonlParser`)은 운영과 같은 코드를 쓴다.

## 로컬 저장소 구조

`{root}` 아래 상대 경로가 객체 키다. S3와 Swift는 같은 키를 쓴다 (ingest-spec.md 2장).

```
{root}/
├── s3/firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.jsonl.gz
└── swift/firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.jsonl.gz
```

`{root}`는 `application-local.yaml`의 `ubi.local-storage.root`다. 기본값은 레포 옆 `../local-storage`이고, 환경 변수 `UBI_LOCAL_STORAGE_ROOT`로 바꿀 수 있다.

## 실행 순서

레포 루트에서 실행한다.

1. MySQL을 띄운다.

   ```bash
   docker compose up -d
   ```

2. 샘플 수집 파일을 만든다. 2026-11-08 하루치가 `s3/`와 `swift/`에 만들어진다.

   ```bash
   scripts/make-local-sample.sh
   ```

3. reconcileJob을 실행한다.

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=reconcileJob targetDate=2026-11-08'
   ```

   기대 로그: `S3 3건, Swift 2건, 누락 1건`, `[local] 재큐잉 대상: firehose/processed/2026/11/08/14/sample-c.jsonl.gz`

4. ingestJob을 실행한다.

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=ingestJob targetDate=2026-11-08'
   ```

   기대 로그: `파일 2개, microtrip 저장 3건·중복 0건, trip 종료 1건, 새 trip 1건, 건너뛴 줄 1줄`
   기대 데이터: `trip`에 vehicle-001 tid 1이 `COMPLETED`, `trip_microtrip` 3행

같은 `targetDate`로 이미 완료된 Job은 다시 실행되지 않는다. 다시 돌리려면 `rerun=2`처럼 이전에 쓰지 않은 파라미터를 붙이거나, DB를 비운다(`docker compose down -v`).

## 3306 포트를 이미 쓰고 있을 때

다른 MySQL이 3306을 쓰고 있으면 compose의 MySQL이 뜨지 않는다. 다른 포트로 띄우고 접속 주소만 환경 변수로 바꾼다.

```bash
docker run -d --name ubi-mysql -e MYSQL_DATABASE=ubi -e MYSQL_USER=ubi -e MYSQL_PASSWORD=ubi \
  -e MYSQL_ROOT_PASSWORD=root -p 3307:3306 mysql:8.4
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/ubi ./gradlew bootRun --args='...'
```
