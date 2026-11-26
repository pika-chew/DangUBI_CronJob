# UBI 수집 데이터 저장·적재 스펙

S3 → Swift → MySQL로 이어지는 수집 데이터의 저장 경로, 파일 형식, 적재 규칙을 정한다.
이 문서가 기준이고, Firehose 설정·수집 워커·배치(reconcileJob, ingestJob)는 이 문서를 따른다.

## 1. 원칙

- **저장은 날짜로, trip은 MySQL에서 나눈다.** S3·Swift 경로는 날짜(UTC)로만 나눈다. 레코드마다 `vehicleId`와 `tid`가 들어 있으므로 trip 분리는 적재할 때 `(vehicleId, tid)`로 한다.
- **하루의 기준은 UTC다.** 경로 날짜, Job 파라미터 `targetDate`, 실행 시각 모두 UTC로 맞춘다.
- **S3 키와 Swift 키는 같다.** 키 변환 규칙을 두지 않는다.
- **원본은 Swift에 남는다.** MySQL에는 해석한 값만 넣는다. 해석 규칙이 바뀌면 Swift에서 다시 적재한다.

## 2. 저장 경로

```
s3://{bucket}/firehose/processed/year={yyyy}/month={MM}/day={dd}/hour={HH}/{파일 이름}.jsonl.gz
swift://{container}/firehose/processed/year={yyyy}/month={MM}/day={dd}/hour={HH}/{파일 이름}.jsonl.gz   ← S3 키 그대로
```

- `year={yyyy}/month={MM}/day={dd}/hour={HH}`는 Firehose가 레코드를 받은 시각(UTC)이다. 단말기 시각(`clt`, `stt` 등)과 다를 수 있다.
- 한 파일에 ty=3(trip)과 ty=4(microtrip)가 섞여 있을 수 있다. 차량·trip별로 파일을 나누지 않는다.
- 버킷·컨테이너 이름은 환경 설정으로 주입하고 코드에 넣지 않는다.

### 이전 구조에서 바꾼 이유

이전 구조는 `vehicleId=…/tid=…/ty=…/` 동적 파티셔닝이었다. 이 구조에는 세 가지 문제가 있었다.

- 경로가 차량으로 시작해서 날짜로 목록을 뽑을 수 없었다. 그래서 reconcileJob과 ingestJob이 매번 전체 목록을 읽어야 했다.
- 운행 중인 차량마다 파티션이 열려서 Firehose의 동시 파티션 한도에 닿을 수 있었다.
- 작은 파일이 많이 생겼다.

trip 분리는 MySQL에서 하므로 경로로 나눌 이득이 없다.

## 3. Firehose 설정

| 항목 | 값 |
| --- | --- |
| Dynamic partitioning | 끔 |
| S3 bucket prefix | `firehose/processed/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/` |
| S3 bucket error output prefix | `firehose/errors/!{firehose:error-output-type}/!{timestamp:yyyy/MM/dd/HH}/` |
| Compression | GZIP |
| File extension format | `.jsonl.gz` |
| New line delimiter | 켬 (레코드 한 건 = 한 줄) |

- 에러 출력은 `firehose/processed/` 밖에 두어 배치가 읽지 않게 한다.

## 4. 수집 워커 (S3 → Swift)

- S3 `ObjectCreated` 이벤트(SQS)를 받아 같은 키로 Swift에 복사한다. 메시지 형식은 S3 이벤트 알림 그대로다.
- 이벤트의 키는 URL 인코딩되어 있으므로 디코딩해서 쓴다.
- 같은 키를 여러 번 복사해도 결과가 같아야 한다(덮어쓰기). reconcileJob이 재큐잉하면 같은 키가 다시 온다.

## 5. 레코드 형식 (JSONL 한 줄)

```json
{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"…","pld":{…}}
```

| 필드 | 설명 |
| --- | --- |
| `vehicleId` | 차량 ID. 지금은 운전자 = 차량으로 본다 |
| `ts` | Lambda가 받은 시각 (epoch ms, UTC) |
| `ty` | 3 = trip(운행 종료 후 1회), 4 = microtrip(운행 중 주기 전송) |
| `schemaVersion` | 지금은 1만 받는다 |
| `idempotencyKey` | Lambda가 만드는 레코드 식별자. 같은 키는 한 번만 적재한다 |
| `pld` | ty별 본문. 필드 목록은 단말기 메시지 규격(Trip / OBD Microtrip)을 따른다 |

## 6. 적재 규칙 (ingestJob)

### 실행

- 파라미터: `targetDate` (필수, yyyy-MM-dd, UTC), `rerun` (선택, 정수)
- `firehose/processed/{targetDate}/` 아래 `.gz` 파일을 키 순서로 읽는다.
- 실패한 실행은 같은 파라미터로 다시 실행하면 멈춘 곳부터 이어서 처리한다.
- 이미 COMPLETED된 날짜를 다시 적재하려면 `rerun=2`처럼 이전에 쓰지 않은 값을 붙인다. Spring Batch는 같은 파라미터로 완료된 Job을 다시 실행하지 않기 때문이다.
- 다시 적재해도 결과가 같다. 이미 넣은 레코드는 `idempotencyKey`로 걸러진다.

### 줄 해석

- 키 필드(`vehicleId`, `idempotencyKey`, `ty`, `schemaVersion`, `pld.tid`, microtrip의 `pld.clt`)가 없거나 형식이 틀리면 그 줄을 버린다.
- 지원하지 않는 `ty`·`schemaVersion`인 줄도 버린다.
- 그 밖의 필드는 형식이 틀리면 그 필드만 null로 둔다.
- 문서상 필수(M)인 필드도 실제로 빠질 수 있어 null을 허용한다.
- 숫자 필드는 JSON 숫자와 숫자 문자열을 모두 받는다. 값은 원본 Int 그대로 두고 단위·스케일은 바꾸지 않는다.
- 예외로 측정값(microtrip의 `sp`, trip의 `dis`·`hsts`·`mesp`·`dtvt`)은 Lambda가 소수(`42.392873068822745`)로 보낸다. 반올림해 정수로 저장한다. 점수 계산은 임계값 비교라 소수점 아래를 쓰지 않는다.
- `tid`는 int 범위를 넘는 값(`30836460003626`)이 와서 BIGINT로 저장한다.
- `em`은 이벤트 비트마스크다. Lambda는 JSON 숫자(10진)로 보낸다(`256` = 과속). 문자열로 오면 16진으로 읽는다(`"28"` = 0x28 = 급유턴 + 급가속). 비트 정의(bit8 = 과속 포함)는 [score-spec.md](score-spec.md) 2장을 따른다.
- trip(ty=3)은 Lambda가 보내는 요약 필드 `stt`, `edt`, `dis`(운행 누적 주행거리, m), `hsts`, `mesp`, `dtvt`만 읽어 저장한다.
- microtrip은 점수 계산에 쓰는 `clt`, `sp`, `em`만 읽어 저장한다. 나머지 필드(`lat`, `lon`, `xyz`, `vv` 등)는 Swift 원본에만 남는다. 다시 필요해지면 컬럼을 추가하고 Swift에서 다시 적재한다.
- `usm`(휴대폰 번호)은 개인정보라 저장하지 않는다.
- gz가 깨진 파일은 Job을 실패시킨다. 원인을 확인한 뒤 다시 실행한다.

### trip 정제

- trip 키는 `(vehicleId, tid)`다.
- microtrip이 먼저 오면 trip을 `IN_PROGRESS`로 만든다. 그리고 `clt`의 최소값·최대값을 `first_collected_at`, `last_collected_at`에 반영한다.
- ty=3이 오면 trip을 `COMPLETED`로 바꾸고 요약값을 채운다.
- 새 microtrip이나 ty=3을 받은 trip에는 그 chunk의 적재 시각을 `last_ingested_at`에 남긴다. scoreJob은 이 시각부터 settle-delay를 기다린다(score-spec.md 8장). 이미 저장된 microtrip만 다시 오면 바꾸지 않는다.
- 두 메시지는 서로의 컬럼을 건드리지 않는다. status는 `IN_PROGRESS` → `COMPLETED`로만 바뀐다. 그래서 순서와 상관없이 결과가 같다.
- 자정을 넘긴 trip은 이틀치 Job에 나뉘어 들어와도 같은 행으로 합쳐진다.
- TODO: ty=3이 오지 않는 trip을 시간 초과로 닫는 정책.

## 7. 테이블

컬럼 상세는 엔티티(`domain/driver/entity`, `domain/trip/entity`)와 `src/main/resources/db/trip-schema-mysql.sql`을 기준으로 한다. 시각은 UTC `DATETIME(3)`이다.

| 표 | 키 | 내용 |
| --- | --- | --- |
| `driver` | `vehicle_id` 유니크 | 지금은 차량 1대 = 운전자 1명 |
| `trip` | `(vehicle_id, tid)` 유니크 | 상태, ty=3 요약값(시작·종료 시각, 주행거리, 최고·평균 속도, 주행 시간), microtrip 수집 시각 범위, 마지막 적재 시각 |
| `trip_microtrip` | `idempotency_key` 유니크 | microtrip 한 건의 수집 시각(`clt`), 속도(`sp`), 이벤트 비트(`em`). 인덱스 `(trip_id, collected_at)` |
| `outbox` | `(aggregate_type, aggregate_id, event_type)` 유니크 | 적재와 같은 트랜잭션에서 남기는 발행 대기 이벤트. 인덱스 `(status, id)` |

### outbox 이벤트

ingestJob Writer가 trip을 저장하는 chunk 트랜잭션 안에서 함께 기록한다. 적재가 롤백되면 이벤트도 남지 않는다.

| event_type | 기록 시점 |
| --- | --- |
| `TRIP_STARTED` | trip을 처음 만들 때. ty=3이 먼저 와도 생성 이벤트가 먼저 남는다 |
| `TRIP_COMPLETED` | trip이 `IN_PROGRESS`에서 `COMPLETED`로 바뀔 때. 이미 종료된 trip의 ty=3을 다시 받으면 남기지 않는다 |

- `payload`는 식별자만 담은 JSON이다: `{"tripId":1,"driverId":1,"vehicleId":"vehicle-001","tid":354}`. 상세 값은 소비자가 `trip` 테이블에서 읽는다.
- 새 행은 `status=PENDING`이다. PENDING 행을 발행하고 `PUBLISHED`로 바꾸는 relay는 아직 없다.

"어디까지 읽었나"는 Spring Batch 실행 기록(`targetDate`별 JobInstance)으로 관리한다. 파일별 처리 기록 표는 두지 않는다.

## 8. 하루 실행 순서 (UTC)

매일 **UTC 02:00(KST 11:00)**에 `dailyJob` 하나를 실행한다. 파라미터 없이 띄우면 `targetDate` = 어제(UTC) = D다.

| 순서 | Step | 내용 |
| --- | --- | --- |
| 1 | `reconcileStep` | S3와 Swift의 D일 목록을 비교해 Swift에 없는 파일을 재큐잉한다 |
| 2 | `dailyRequeueWaitStep` | S3와 Swift를 다시 비교해, Swift에 없는 파일이 없어질 때까지 기다린다. `requeue-wait-timeout`(30분)이 지나면 있는 파일로 진행한다. 기다릴 키를 Job 실행 기록(MySQL TEXT, 64KB)에 넘기지 않아 누락이 많아도 멈추지 않는다 |
| 3 | `ingestFileStep` | D일 Swift 파일을 MySQL에 적재한다 |
| 4 | `scoreMetricStep` → `scoreReportStep` | 적재 후 24시간이 지난 trip의 점수 지표와 차량 리포트·outbox 이벤트를 남긴다 (score-spec.md 8장) |

- 3–4가 실패하면 Job을 멈춘다. 같은 날짜로 다시 띄우면(같은 날 다시 실행) 실패한 Step부터 이어서 처리한다.
- 1–2가 실패하면(재큐잉 일부 실패, S3 조회 실패 등) 뒤 Step은 그대로 진행해 Swift에 있는 파일을 적재하고, Job은 FAILED로 끝난다. 종료 코드가 0이 아니라 K8s CronJob 실패 알림으로 알 수 있다. 이때 실패한 Step은 ABANDONED가 되어 같은 날짜로 다시 띄워도 다시 돌지 않으므로, 재큐잉은 `reconcileJob targetDate=D`로 따로 돌린다.
- 대기 시간 안에 복사되지 않은 파일은 경고 로그에 건수와 앞부분 10개 키가 남는다. 복사된 뒤 `ingestJob targetDate=D rerun=2`로 다시 적재한다. 자동 재검토 Job은 아직 없다.
- 특정 날짜를 돌릴 때는 `--ubi.batch.daily.target-date=2026-11-20`을 주거나 개별 Job(`reconcileJob`, `ingestJob`, `scoreJob`)을 쓴다. 개별 Job은 수동 재실행용으로 남겨 둔다.
- dailyJob과 개별 Job을 같은 시각에 함께 돌리지 않는다. 같은 trip을 동시에 처음 만들면 유니크 충돌로 실패한다(IngestFileWriter TODO).

### K8s CronJob (GitOps 레포)

CronJob 매니페스트는 DangUBI_GitOps 레포에 있다. 기존 `reconcileJob`·`ingestJob` CronJob을 내리고 아래 하나로 바꾼다. env·Secret·imagePullSecret은 기존 CronJob과 같게 둔다.

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: dangubi-daily
spec:
  schedule: "0 2 * * *"
  timeZone: "Etc/UTC"
  concurrencyPolicy: Forbid        # 전날 실행이 길어져도 겹치지 않게
  startingDeadlineSeconds: 3600
  jobTemplate:
    spec:
      backoffLimit: 0              # 재시도는 같은 날짜로 수동 실행 (실패한 Step부터 이어서 처리)
      template:
        spec:
          restartPolicy: Never
          containers:
            - name: dangubi-cronjob
              image: dangubi-cronjob   # kustomization.yaml images 항목의 name
              args: ["--spring.batch.job.name=dailyJob"]
```
