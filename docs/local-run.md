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
├── s3/firehose/processed/year={yyyy}/month={MM}/day={dd}/hour={HH}/{파일 이름}.jsonl.gz
└── swift/firehose/processed/year={yyyy}/month={MM}/day={dd}/hour={HH}/{파일 이름}.jsonl.gz
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

   기대 로그: `S3 3건, Swift 2건, 누락 1건`, `[local] 재큐잉 대상: firehose/processed/year=2026/month=11/day=08/hour=14/sample-c.jsonl.gz`

4. ingestJob을 실행한다.

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=ingestJob targetDate=2026-11-08'
   ```

   기대 로그: `파일 2개, microtrip 저장 3건·중복 0건, trip 종료 1건, 새 trip 1건, 건너뛴 줄 1줄`
   기대 데이터: `trip`에 vehicle-001 tid 1이 `COMPLETED`, `trip_microtrip` 3행

5. scoreJob을 실행한다. 샘플은 15km뿐이라 테스트 기준(구간 50km)으로도 구간이 닫히지 않는다.

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=scoreJob --ubi.batch.score.basis=test'
   ```

   기대 로그: `score metric chunk: trip 지표 1건 저장`, `score report chunk: 차량 1대, 닫은 구간 0개, 리포트 1건`
   기대 데이터: `trip_score_metric` 1행(급가속 1회, 급회전 2회), `vehicle_score_report` 1행(`score` NULL), `outbox`에 `VEHICLE_SCORE_REPORTED` 1행

   scoreJob은 파라미터 없이 매번 새 실행을 만든다. 다시 실행해도 새로 종료된 trip이 없으면 아무것도 만들지 않는다.

같은 `targetDate`로 이미 완료된 ingestJob·reconcileJob은 다시 실행되지 않는다. 다시 돌리려면 `rerun=2`처럼 이전에 쓰지 않은 파라미터를 붙이거나, DB를 비운다(`docker compose down -v`).

## 하루 파이프라인 (dailyJob)

운영에서는 매일 UTC 02:00에 `dailyJob`이 어제(UTC) 하루치를 reconcile → 복사 대기 → ingest → score 순서로 처리한다 (ingest-spec.md 8장). 로컬에서는 날짜를 지정해 돌린다.

```bash
scripts/make-local-sample.sh
./gradlew bootRun --args='--spring.profiles.active=local --spring.batch.job.name=dailyJob --ubi.batch.daily.target-date=2026-11-08 --ubi.batch.score.basis=test --ubi.batch.score.settle-delay=0s'
```

- 로컬 프로필은 복사 대기를 10초로 줄여 둔다. 수집 워커가 없어 재큐잉한 sample-c는 복사되지 않으므로, 10초 뒤 경고를 남기고 있는 파일로 적재한다.
- `settle-delay=0s`를 주지 않으면 방금 적재한 trip은 24시간 뒤 실행까지 점수를 매기지 않는다.
- 기대 로그: `S3 3건, Swift 2건, 누락 1건` → `1건이 복사되지 않아 있는 파일로 적재를 진행함` → `ingest chunk: …` → `score report chunk: 차량 1대, …`
- 같은 날짜로 다시 띄우면 이미 끝난 날이라 실행되지 않는다. 다시 보려면 DB를 비운다.

## 점수 시연 (더미 데이터)

차량 3대(안전·보통·난폭)의 3일치 더미 수집 파일로 ingestJob → scoreJob을 돌리고 점수를 확인한다.

```bash
docker compose down -v && docker compose up -d   # 깨끗한 DB로 시작 (이전 스키마·데이터가 섞이지 않게)
scripts/run-score-demo.sh
```

스크립트가 하는 일:

1. `scripts/make-score-sample.py`가 `../local-storage`에 수집 파일(2026-11-20–22, microtrip 2초 주기, ty=3 포함)을 만들고, 같은 규칙으로 계산한 **예상 점수**를 출력한다.
2. 날짜마다 ingestJob을 실행한다.
3. scoreJob을 test 기준(구간 50km, 반영 범위 600km), 대기 시간 0초(`--ubi.batch.score.settle-delay=0s`)로 실행한다.
4. `scripts/score-report.sql`로 차량별 최신 리포트, 구간, outbox 이벤트를 조회한다. 1번의 예상 점수와 같아야 한다.

기본 시드의 예상 결과:

| 차량 | 성향 | 주행 | 구간 | 점수 |
| --- | --- | --- | --- | --- |
| `DUMMY0000000SAFE1` | 안전 | 152.9km | 2 | 95.40 |
| `DUMMY000000NORML1` | 보통 | 181.6km | 3 | 88.34 |
| `DUMMY0000000AGGR1` | 난폭 (심야 운행 포함) | 263.2km | 4 | 70.89 |

- 다른 데이터를 만들려면 `python3 scripts/make-score-sample.py --seed 11 --days 5`처럼 직접 실행한 뒤 날짜마다 ingestJob과 scoreJob을 돌린다.
- 스크립트를 다시 돌리면 같은 레코드는 걸러지고, scoreJob은 새로 종료된 trip이 없어 리포트를 만들지 않는다. 처음부터 보려면 DB를 비운다.
### 3306을 다른 MySQL이 쓰고 있을 때

compose의 MySQL이 뜨지 않으므로 3307에 컨테이너를 따로 띄우고, 접속 주소와 조회 명령을 함께 바꾼다.

```bash
# 1. 3307에 MySQL 컨테이너 띄우기 (처음 한 번)
docker run -d --name ubi-mysql \
  -e MYSQL_DATABASE=ubi -e MYSQL_USER=ubi -e MYSQL_PASSWORD=ubi -e MYSQL_ROOT_PASSWORD=root \
  -e TZ=Asia/Seoul -p 3307:3306 mysql:8.4

# 2. 뜰 때까지 기다리기
until docker exec ubi-mysql mysqladmin ping -uubi -pubi --silent; do sleep 2; done

# 3. 시연 실행
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/ubi \
MYSQL_CMD="docker exec -i ubi-mysql mysql -t -uubi -pubi ubi" \
scripts/run-score-demo.sh
```

처음부터 다시 하려면 `docker rm -f ubi-mysql` 후 1번부터 한다.

## 3306 포트를 이미 쓰고 있을 때

다른 MySQL이 3306을 쓰고 있으면 compose의 MySQL이 뜨지 않는다. 다른 포트로 띄우고 접속 주소만 환경 변수로 바꾼다.

```bash
docker run -d --name ubi-mysql -e MYSQL_DATABASE=ubi -e MYSQL_USER=ubi -e MYSQL_PASSWORD=ubi \
  -e MYSQL_ROOT_PASSWORD=root -p 3307:3306 mysql:8.4
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/ubi ./gradlew bootRun --args='...'
```
