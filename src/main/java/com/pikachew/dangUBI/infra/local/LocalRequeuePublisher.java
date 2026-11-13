package com.pikachew.dangUBI.infra.local;

import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.LOCAL_PROFILE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * local 프로필에서 SQS 대신 쓰는 {@link RequeuePublisher} 구현체. 누락 객체를 로그로만 남긴다.
 * 로컬에는 수집 워커가 없으므로 파일을 swift 디렉터리로 옮기지 않는다. reconcileJob이 누락을 제대로 찾는지만 확인한다.
 */
@Slf4j
@Component
@Profile(LOCAL_PROFILE)
public class LocalRequeuePublisher implements RequeuePublisher {

	@Override
	public int publish(List<ObjectRef> missingObjects) {
		missingObjects.forEach(object -> log.info("[local] 재큐잉 대상: {} ({} bytes)", object.key(), object.size()));
		return missingObjects.size();
	}

}
