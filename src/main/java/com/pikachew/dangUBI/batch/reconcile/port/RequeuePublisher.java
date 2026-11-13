package com.pikachew.dangUBI.batch.reconcile.port;

import java.util.List;

/** 누락 객체를 수집 워커의 큐(AWS SQS)에 다시 넣는다. 배치가 직접 복사하지 않고 수집 워커가 다시 옮기게 한다. 구현체는 infra.sqs. */
public interface RequeuePublisher {

	/**
	 * @return 실제로 큐에 넣은 건수
	 */
	int publish(List<ObjectRef> missingObjects);

}
