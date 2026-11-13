package com.pikachew.dangUBI.infra.sqs;

import java.util.List;

import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;

/**
 * SQS 재큐잉 구현체 (껍데기).
 *
 * TODO: AWS SDK v2(software.amazon.awssdk:sqs)를 추가하고 SendMessageBatch(최대 10건씩)로 보낸다.
 * TODO: 메시지 형식은 수집 워커가 받는 S3 이벤트 알림 형식과 같게 맞춘다(수집 워커 담당과 합의).
 */
@Component
public class SqsRequeuePublisher implements RequeuePublisher {

	@Override
	public int publish(List<ObjectRef> missingObjects) {
		throw new UnsupportedOperationException("TODO: SQS 재큐잉 미구현");
	}

}
