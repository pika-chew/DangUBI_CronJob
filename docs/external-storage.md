# 외부 저장소 연동 (운영)

reconcileJob과 ingestJob이 쓰는 외부 시스템과 접속 설정. local 프로필은 [local-run.md](local-run.md)를 본다.

| 대상 | 쓰는 Job | 구현체 | 접근 방식 |
| --- | --- | --- | --- |
| AWS S3 원본 버킷 | reconcile | `S3SourceObjectStore` | AWS SDK v2 S3 |
| Swift 컨테이너 | reconcile, ingest | `SwiftClientImpl` | AWS SDK v2 S3 → Swift proxy의 s3api |
| AWS SQS 수집 큐 | reconcile | `SqsRequeuePublisher` | AWS SDK v2 SQS |

S3와 Swift는 같은 키를 쓰고(ingest-spec.md 2장), 목록 조회와 내용 열기는 `S3CompatibleBucket` 하나로 한다.

## 환경 변수

비어 있으면 앱이 뜰 때 실패한다.

| 환경 변수 | 설정 키 | 설명 |
| --- | --- | --- |
| `S3_SOURCE_BUCKET` | `ubi.s3.bucket` | Firehose가 쓰는 원본 버킷 |
| `AWS_REGION` | `ubi.s3.region`, `ubi.sqs.region` | 원본 버킷·큐 리전. 예: `ap-northeast-2` |
| `SQS_REQUEUE_QUEUE_URL` | `ubi.sqs.queue-url` | 수집 워커가 읽는 큐 URL |
| `SWIFT_ENDPOINT` | `ubi.swift.endpoint` | Swift proxy의 s3api 주소 |
| `SWIFT_CONTAINER` | `ubi.swift.container` | 수집 파일 컨테이너 |
| `SWIFT_ACCESS_KEY` | `ubi.swift.access-key` | Keystone EC2 credentials access |
| `SWIFT_SECRET_KEY` | `ubi.swift.secret-key` | Keystone EC2 credentials secret |
| `SWIFT_REGION` | `ubi.swift.region` | 서명용 리전. s3api `location` 설정과 맞춘다. 기본 `us-east-1` |

## 인증

- **AWS (S3, SQS)**: 키를 설정에 두지 않는다. AWS SDK 기본 체인이 `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY` 환경 변수나 K8s IRSA에서 찾는다.
  필요한 권한: 원본 버킷 `s3:ListBucket`, 큐 `sqs:SendMessage`.
- **Swift**: s3api는 S3 서명(SigV4)을 Keystone EC2 credentials로 검증한다. 배치용 사용자·프로젝트로 한 번 만든다.

  ```bash
  openstack ec2 credentials create --project <프로젝트> --user <사용자>
  ```

  배치는 읽기만 하므로 해당 컨테이너 읽기 권한만 준다.

## 재큐잉 메시지

수집 워커가 받는 S3 `ObjectCreated` 이벤트 알림 형식으로 보낸다(ingest-spec.md 4장). 키는 S3 이벤트처럼 URL 인코딩한다.

```json
{"Records":[{"eventVersion":"2.1","eventSource":"aws:s3","awsRegion":"ap-northeast-2","eventName":"ObjectCreated:Put",
  "s3":{"bucket":{"name":"<원본 버킷>"},"object":{"key":"firehose/processed/2026/11/08/03/a.jsonl.gz","size":120}}}]}
```

TODO: 수집 워커가 실제로 읽는 필드를 수집 워커 담당과 확인한다.
