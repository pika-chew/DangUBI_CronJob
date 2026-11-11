package com.pikachew.dangUBI.infra.swift;

import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_EVENT_MASK_HEX_PATTERN;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_SUPPORTED_SCHEMA_VERSION;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_TY_MICROTRIP;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_TY_TRIP;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_XYZ_DELIMITER;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_XYZ_SIZE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.common.util.JsonNodeUtils;
import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TelemetrySnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * gz로 압축된 JSONL 수집 파일을 trip·microtrip으로 해석한다 (docs/ingest-spec.md 5·6장).
 *
 * 파일
 * - 빈 줄은 건너뛰고 세지 않는다. 해석하지 못한 줄은 경고를 남기고 skippedLineCount로 센다.
 * - gz 자체를 읽을 수 없으면 {@link TelemetryFileFormatException}을 던진다.
 *
 * 줄
 * - 키 필드(vehicleId, idempotencyKey, ty, schemaVersion, pld.tid, microtrip의 pld.clt)가 없거나 형식이 틀리면 그 줄을 버린다.
 * - 지원하지 않는 ty·schemaVersion인 줄도 버린다.
 * - 나머지 필드는 형식이 틀리면 그 필드만 null로 두고 경고 로그를 남긴다. 원본은 Swift에 남아 있다.
 * - 숫자 필드는 JSON 숫자와 숫자 문자열을 모두 받는다.
 *
 * ============================================================================================
 * [MEMORY RISK] 파일 하나를 통째로 메모리에 올린다 — 장애 테스트 필수 확인 지점
 * ============================================================================================
 * - gz를 풀면서 파일의 모든 줄을 Snapshot 객체로 만들어 리스트에 담는다. 파일을 끝까지 읽기 전에는 넘기지 않는다.
 * - 메모리 사용량 ≈ 파일 하나의 압축 해제 크기 × 객체 오버헤드(수 배). Firehose 버퍼 설정에 따라 파일 하나가 수십~100MB대일 수 있다.
 * - 이 결과(ParsedTelemetryFile)는 chunk가 커밋될 때까지 chunk 크기만큼 함께 메모리에 머문다 (IngestJobConfig 참고).
 * - 장애 테스트: 가장 큰 운영 파일 크기 × chunk-size 기준으로 힙 한도(-Xmx)와 OOM 여부를 확인한다.
 * - 대응: chunk-size 축소 → 그래도 부족하면 줄 단위 스트리밍으로 바꾼다.
 * TODO: 파일이 커서 메모리에 다 올리기 어려우면 레코드를 스트리밍으로 넘기는 방식으로 바꾼다.
 */
@Slf4j
public final class TelemetryJsonlParser {

	private TelemetryJsonlParser() {
	}

	public static ParsedTelemetryFile parse(String objectKey, InputStream gzipped) {
		List<TripSnapshot> trips = new ArrayList<>();
		List<MicrotripSnapshot> microtrips = new ArrayList<>();
		int skipped = 0;

		try {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(new GZIPInputStream(gzipped), StandardCharsets.UTF_8));
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				try {
					TelemetrySnapshot snapshot = parseLine(line);
					if (snapshot instanceof TripSnapshot trip) {
						trips.add(trip);
					} else if (snapshot instanceof MicrotripSnapshot microtrip) {
						microtrips.add(microtrip);
					}
				} catch (InvalidTelemetryLineException e) {
					skipped++;
					log.warn("줄을 건너뜁니다: {}:{} ({})", objectKey, lineNumber, e.getMessage());
				}
			}
		} catch (IOException e) {
			throw new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + objectKey, e);
		}

		return new ParsedTelemetryFile(objectKey, trips, microtrips, skipped);
	}

	/**
	 * JSONL 한 줄을 해석한다.
	 *
	 * @throws InvalidTelemetryLineException 줄을 버려야 할 때. 메시지에 이유가 들어간다
	 */
	public static TelemetrySnapshot parseLine(String line) {
		JsonNode root;
		try {
			root = JsonMapper.shared().readTree(line);
		} catch (JacksonException e) {
			throw new InvalidTelemetryLineException("JSON 형식이 아닙니다");
		}
		if (root == null || !root.isObject()) {
			throw new InvalidTelemetryLineException("JSON 객체가 아닙니다");
		}

		String vehicleId = requiredText(root, "vehicleId");
		String idempotencyKey = requiredText(root, "idempotencyKey");
		int ty = requiredInt(root, "ty");
		int schemaVersion = requiredInt(root, "schemaVersion");
		if (schemaVersion != TELEMETRY_SUPPORTED_SCHEMA_VERSION) {
			throw new InvalidTelemetryLineException("지원하지 않는 schemaVersion: " + schemaVersion);
		}
		JsonNode pld = root.get("pld");
		if (pld == null || !pld.isObject()) {
			throw new InvalidTelemetryLineException("pld가 없습니다");
		}

		Fields fields = new Fields(idempotencyKey);
		Instant receivedAt = fields.epochMillis(root, "ts");
		int tid = requiredInt(pld, "tid");

		return switch (ty) {
			case TELEMETRY_TY_TRIP -> new TripSnapshot(vehicleId, idempotencyKey, receivedAt, tid,
					fields.epochMillis(pld, "stt"),
					fields.epochMillis(pld, "edt"),
					fields.integer(pld, "dis"),
					fields.integer(pld, "tdis"),
					fields.integer(pld, "fc"),
					fields.integer(pld, "stlat"),
					fields.integer(pld, "stlon"),
					fields.integer(pld, "edlat"),
					fields.integer(pld, "edlon"),
					fields.integer(pld, "ctp"),
					fields.integer(pld, "coe"),
					fields.integer(pld, "fct"),
					fields.integer(pld, "hsts"),
					fields.integer(pld, "mesp"),
					fields.integer(pld, "idt"),
					fields.integer(pld, "btv"),
					fields.integer(pld, "gnv"),
					fields.integer(pld, "wut"),
					fields.integer(pld, "est"),
					fields.integer(pld, "fwv"),
					fields.integer(pld, "dtvt"));
			case TELEMETRY_TY_MICROTRIP -> {
				Instant collectedAt = Instant.ofEpochMilli(requiredLong(pld, "clt"));
				Integer[] xyz = fields.xyz(pld);
				yield new MicrotripSnapshot(vehicleId, idempotencyKey, receivedAt, tid, collectedAt,
						fields.integer(pld, "lat"),
						fields.integer(pld, "lon"),
						fields.integer(pld, "lc"),
						fields.integer(pld, "fc"),
						fields.integer(pld, "cdit"),
						fields.integer(pld, "rpm"),
						fields.integer(pld, "sp"),
						fields.eventMask(pld),
						fields.integer(pld, "el"),
						xyz[0], xyz[1], xyz[2],
						fields.integer(pld, "vv"),
						fields.integer(pld, "tpos"));
			}
			default -> throw new InvalidTelemetryLineException("지원하지 않는 ty: " + ty);
		};
	}

	private static String requiredText(JsonNode parent, String name) {
		String value = JsonNodeUtils.toNonBlankText(parent.get(name));
		if (value == null) {
			throw new InvalidTelemetryLineException(name + "가 없거나 문자열이 아닙니다");
		}
		return value;
	}

	private static int requiredInt(JsonNode parent, String name) {
		Integer value = JsonNodeUtils.toInteger(parent.get(name));
		if (value == null) {
			throw new InvalidTelemetryLineException(name + "가 없거나 정수가 아닙니다");
		}
		return value;
	}

	private static long requiredLong(JsonNode parent, String name) {
		Long value = JsonNodeUtils.toLong(parent.get(name));
		if (value == null) {
			throw new InvalidTelemetryLineException(name + "가 없거나 정수가 아닙니다");
		}
		return value;
	}

	/** 버려도 되는 필드를 읽는다. 형식이 틀리면 null로 두고 경고만 남긴다 */
	private record Fields(String idempotencyKey) {

		Integer integer(JsonNode parent, String name) {
			JsonNode node = parent.get(name);
			if (JsonNodeUtils.isAbsent(node)) {
				return null;
			}
			Integer value = JsonNodeUtils.toInteger(node);
			return value == null ? invalid(name, node) : value;
		}

		Instant epochMillis(JsonNode parent, String name) {
			JsonNode node = parent.get(name);
			if (JsonNodeUtils.isAbsent(node)) {
				return null;
			}
			Long value = JsonNodeUtils.toLong(node);
			return value == null ? invalid(name, node) : Instant.ofEpochMilli(value);
		}

		/** em: "28"은 0x28 = bit3(급유턴) + bit5(급가속)이다. 숫자 28로 와도 16진으로 본다 */
		Integer eventMask(JsonNode parent) {
			JsonNode node = parent.get("em");
			if (JsonNodeUtils.isAbsent(node)) {
				return null;
			}
			String text = node.isString() ? node.stringValue().trim()
					: node.isIntegralNumber() ? node.asString() : null;
			if (text == null || !TELEMETRY_EVENT_MASK_HEX_PATTERN.matcher(text).matches()) {
				return invalid("em", node);
			}
			return Integer.parseInt(text, 16);
		}

		/** xyz: 세 값이 모두 정수가 아니면 셋 다 null이다 */
		Integer[] xyz(JsonNode parent) {
			Integer[] empty = new Integer[TELEMETRY_XYZ_SIZE];
			JsonNode node = parent.get("xyz");
			if (JsonNodeUtils.isAbsent(node)) {
				return empty;
			}
			if (!node.isString()) {
				invalid("xyz", node);
				return empty;
			}
			String[] parts = node.stringValue().split(TELEMETRY_XYZ_DELIMITER, -1);
			if (parts.length != TELEMETRY_XYZ_SIZE) {
				invalid("xyz", node);
				return empty;
			}
			Integer[] values = new Integer[TELEMETRY_XYZ_SIZE];
			for (int i = 0; i < TELEMETRY_XYZ_SIZE; i++) {
				try {
					values[i] = Integer.parseInt(parts[i].trim());
				} catch (NumberFormatException e) {
					invalid("xyz", node);
					return empty;
				}
			}
			return values;
		}

		private <T> T invalid(String name, JsonNode node) {
			log.warn("필드 형식이 달라 null로 둡니다: idempotencyKey={}, {}={}", idempotencyKey, name, node);
			return null;
		}

	}

}
