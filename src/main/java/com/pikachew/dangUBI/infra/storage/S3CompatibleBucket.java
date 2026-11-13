package com.pikachew.dangUBI.infra.storage;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.OBJECT_STORAGE_KEY_DELIMITER;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * S3 API로 버킷 하나를 읽는다. AWS S3 원본 버킷과 Swift(s3api) 컨테이너가 같이 쓴다.
 * 엔드포인트·인증은 넘겨받은 {@link S3Client}가 정하고, 이 클래스는 목록 조회와 내용 열기만 한다.
 */
public class S3CompatibleBucket {

	private final S3Client s3Client;
	private final String bucket;

	public S3CompatibleBucket(S3Client s3Client, String bucket) {
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	/**
	 * prefix 아래 객체 전부. ListObjectsV2 페이지(최대 1000건)를 끝까지 넘기며 모은다.
	 * 폴더 표시 객체('/'로 끝나는 키)는 뺀다. 순서는 저장소가 돌려준 순서(키 순서)다.
	 *
	 * [MEMORY RISK] 하루치 목록 전체를 메모리에 올린다. 키와 크기만이라 보통은 작다.
	 */
	public List<StorageObject> list(String prefix) {
		List<StorageObject> objects = new ArrayList<>();
		ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
		try {
			ListObjectsV2Response response;
			do {
				response = s3Client.listObjectsV2(request.build());
				response.contents().stream()
						.filter(object -> !object.key().endsWith(OBJECT_STORAGE_KEY_DELIMITER))
						.map(object -> new StorageObject(object.key(), object.size()))
						.forEach(objects::add);
				request.continuationToken(response.nextContinuationToken());
			} while (Boolean.TRUE.equals(response.isTruncated()));
		} catch (SdkException e) {
			throw new ObjectStorageException("객체 목록을 읽지 못했습니다: " + bucket + "/" + prefix, e);
		}
		return objects;
	}

	/** 객체 내용을 연다. 호출한 쪽이 닫는다 */
	public InputStream open(String key) {
		try {
			return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			throw new ObjectStorageException("객체를 열지 못했습니다: " + bucket + "/" + key, e);
		}
	}

}
