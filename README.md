# DangUBI CronJob

UBI 배치 애플리케이션 (Spring Boot 4.1 / Spring Batch 6 / Java 17 / MySQL).
이미지 하나에 모든 Job을 넣고, 실행할 Job은 `--spring.batch.job.name` 인자로 고른다.

## 로컬 실행

```sh
docker compose up -d          # 로컬 MySQL (localhost:3306, db/user/password: ubi)
./gradlew bootJar

java -jar build/libs/dangUBI-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --spring.batch.job.name=reconcileJob \
  targetDate=2026-11-08
```

- `local` 프로필은 Spring Batch 메타 테이블(`BATCH_*`)을 자동으로 만든다(`spring.batch.jdbc.initialize-schema=always`).
  운영은 `never`이므로 `spring-batch-core` jar 안의 `org/springframework/batch/core/schema-mysql.sql`로 미리 만든다.
- Job 파라미터는 `key=value` 형식으로 넘긴다. 같은 파라미터로 이미 COMPLETED된 Job은 다시 돌지 않는다.
- 종료 코드: 성공 0, 실패 1.

## 운영 환경 변수

| 이름 | 설명 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | 예: `jdbc:mysql://<host>:3306/ubi` |
| `SPRING_DATASOURCE_USERNAME` | Vault → Secret |
| `SPRING_DATASOURCE_PASSWORD` | Vault → Secret |

## Job 목록

| Job | 파라미터 | 설명 |
| --- | --- | --- |
| `reconcileJob` | `targetDate` (필수, yyyy-MM-dd) | S3 원본 목록과 Swift 목록을 키로 비교해 Swift에 없는 객체를 SQS로 재큐잉한다. 직접 복사하지 않는다. |

S3·Swift·SQS 구현체(`infra/`)는 아직 껍데기라 실제 실행하면 `UnsupportedOperationException`으로 실패한다. 동작은 테스트(H2 + Mock)로 확인한다.
