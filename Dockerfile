# DangUBI 배치 이미지
#
# jar는 이 Dockerfile이 아니라 Gradle이 먼저 만든다 (Jenkins Build 단계와 같은 jar를 그대로 넣는다).
#   ./gradlew bootJar
#   docker build -t dangubi-cronjob .
#
# 실행할 Job과 Job 파라미터는 컨테이너 인자(K8s CronJob의 args)로 넘긴다.
#   docker run --rm --env-file .env dangubi-cronjob --spring.batch.job.name=sampleJob targetDate=2026-11-08
FROM eclipse-temurin:17-jre-noble

# targetDate 계산·로그 시각이 UTC로 하루 밀리지 않도록 한국 시간으로 둔다
ENV TZ=Asia/Seoul

# 고정 UID로 root가 아닌 사용자로 실행한다 (K8s runAsNonRoot는 숫자 UID여야 확인할 수 있다)
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

# build/libs의 bootJar 하나만 들어온다 (-plain.jar는 .dockerignore에서 뺀다).
# jar가 둘 이상이면 COPY가 실패하므로 잘못된 jar가 조용히 들어가지 않는다.
# 소유자는 root로 두어 실행 사용자가 jar를 바꿀 수 없게 한다.
COPY build/libs/*.jar app.jar

USER 10001:10001

# exec 형식: java가 PID 1이 되어 종료 코드(성공 0, 실패 1)와 SIGTERM이 그대로 K8s에 전달되고,
# 컨테이너 인자(--spring.batch.job.name=..., key=value)가 뒤에 붙는다.
# - MaxRAMPercentage: 힙을 컨테이너 메모리 limit 기준으로 잡는다
# - ExitOnOutOfMemoryError: OOM이면 멈춰 있지 말고 바로 실패로 끝낸다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
