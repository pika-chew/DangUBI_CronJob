# DangUBI CronJob

UBI 배치 애플리케이션 (Spring Boot 4.1 / Spring Batch 6 / Java 17 / MySQL).
이미지 하나에 모든 Job을 넣고, 실행할 Job은 `--spring.batch.job.name` 인자로 고른다.

## 로컬 실행

```sh
docker compose up -d          # 로컬 MySQL (localhost:3306, db/user/password: ubi)
./gradlew bootJar

java -jar build/libs/dangUBI-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --spring.batch.job.name=ingestJob \
  targetDate=2026-11-10
```

- `local` 프로필은 테이블을 자동으로 만든다.
  - Spring Batch 메타 테이블(`BATCH_*`): `spring.batch.jdbc.initialize-schema=always`
  - 업무 테이블(`driver`, `trip`, `trip_microtrip`): `src/main/resources/db/trip-schema-mysql.sql`
- 운영은 둘 다 자동으로 만들지 않는다. 배포 전에 `spring-batch-core` jar 안의 `org/springframework/batch/core/schema-mysql.sql`과 `db/trip-schema-mysql.sql`을 실행해 둔다.
- Job 파라미터는 `key=value` 형식으로 넘긴다. 같은 파라미터로 이미 COMPLETED된 Job은 다시 돌지 않는다.
- 종료 코드: 성공 0, 실패 1.

## Job 목록

| Job | 파라미터 | 설명 |
| --- | --- | --- |
| `ingestJob` | `targetDate` (필수, yyyy-MM-dd, UTC), `rerun` (선택, 정수) | Swift에 저장된 그날 수집 파일(gz JSONL)을 읽어 운전자·trip·microtrip을 MySQL에 적재한다. 실패한 실행은 같은 파라미터로 다시 돌리면 멈춘 파일 다음부터 이어서 처리한다. 완료된 날짜를 다시 적재하려면 `rerun=2`처럼 이전에 쓰지 않은 값을 붙인다. 다시 적재해도 중복으로 쌓이지 않는다 |

저장 경로, 파일 형식, 적재 규칙은 [docs/ingest-spec.md](docs/ingest-spec.md)에 있다.

Swift 클라이언트(`infra/swift/SwiftClient`)는 아직 껍데기라 실제로 실행하면 `UnsupportedOperationException`으로 실패한다. 동작은 테스트(H2 + Mock)로 확인한다.

## 설정

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `ubi.batch.ingest.chunk-size` | 10 | ingestJob chunk 하나에 담는 수집 파일 수. chunk마다 커밋된다 |

## 운영 환경 변수

| 이름 | 설명 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | 예: `jdbc:mysql://<host>:3306/ubi` |
| `SPRING_DATASOURCE_USERNAME` | Vault → Secret |
| `SPRING_DATASOURCE_PASSWORD` | Vault → Secret |
