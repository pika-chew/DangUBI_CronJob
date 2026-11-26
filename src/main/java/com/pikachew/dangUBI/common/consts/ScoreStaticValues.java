package com.pikachew.dangUBI.common.consts;

import java.time.ZoneId;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** scoreJob·안전운전 점수 고정값. 근거는 docs/score-spec.md. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScoreStaticValues {

	// Job·Step 이름
	public static final String SCORE_JOB_NAME = "scoreJob";
	public static final String SCORE_METRIC_STEP_NAME = "scoreMetricStep";
	public static final String SCORE_REPORT_STEP_NAME = "scoreReportStep";

	/** chunk 크기 설정 (application.yaml) */
	public static final String SCORE_CHUNK_SIZE_PROPERTY = "${ubi.batch.score.chunk-size}";

	// 이벤트 묶음 비트 (3장). 묶음 안의 비트가 하나라도 켜지면 그 묶음의 이벤트다
	/** 과속: bit7 */
	public static final int SCORE_OVERSPEED_MASK = 0x80;
	/** 급가속: bit0 급출발 | bit5 급가속 */
	public static final int SCORE_ACCEL_MASK = 0x21;
	/** 급감속: bit4 급감속 | bit6 급정지 */
	public static final int SCORE_BRAKE_MASK = 0x50;
	/** 급회전: bit1 급좌회전 | bit2 급우회전 | bit3 급유턴 */
	public static final int SCORE_TURN_MASK = 0x0E;

	// 집계 (4장)
	/** microtrip 전송 주기. 과속 주기 거리 = sp(km/h) × 이 시간 */
	public static final int SCORE_MICROTRIP_INTERVAL_SECONDS = 2;
	public static final double SCORE_SECONDS_PER_HOUR = 3600.0;
	public static final double SCORE_METERS_PER_KM = 1000.0;
	/** 야간 시간대와 하루 리포트 날짜의 기준 */
	public static final ZoneId SCORE_ZONE = ZoneId.of("Asia/Seoul");

	// 점수 (6장)
	public static final double SCORE_MAX = 100.0;
	public static final double SCORE_MIN = 0.0;
	/** 리포트 이벤트(outbox payload)에 싣는 숫자의 소수 자릿수 */
	public static final int SCORE_EVENT_SCALE = 2;

	// 설정 (application.yaml)
	public static final String SCORE_PROPERTY_PREFIX = "ubi.batch.score";
	/** ubi.batch.score.basis가 없을 때 쓰는 운영 기준 이름 */
	public static final String SCORE_DEFAULT_BASIS = "standard";

}
