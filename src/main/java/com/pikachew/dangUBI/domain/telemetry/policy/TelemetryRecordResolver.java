package com.pikachew.dangUBI.domain.telemetry.policy;

import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.EVENT_MASK_HEX_PATTERN;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.SUPPORTED_SCHEMA_VERSION;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TY_MICROTRIP;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TY_TRIP;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.XYZ_DELIMITER;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.XYZ_SIZE;

import java.time.Instant;

import com.pikachew.dangUBI.common.util.JsonNodeUtils;
import com.pikachew.dangUBI.domain.telemetry.exception.TelemetryHandler;
import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TelemetryRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 수집 메시지(JSONL 한 줄)를 {@link TelemetryRecord}로 해석하는 규칙 (docs/ingest-spec.md 6장).
 *
 * - 키 필드(vehicleId, idempotencyKey, ty, schemaVersion, pld.tid, microtrip의 pld.clt)가 없거나 형식이 틀리면
 *   {@link TelemetryHandler}를 던진다. 호출한 쪽은 그 줄을 버린다.
 * - 지원하지 않는 ty·schemaVersion도 {@link TelemetryHandler}를 던진다.
 * - 나머지 필드는 형식이 틀리면 그 필드만 null로 두고 경고 로그를 남긴다. 원본은 Swift에 남아 있다.
 * - 숫자 필드는 JSON 숫자와 숫자 문자열을 모두 받는다.
 */
@Slf4j
@Component
public class TelemetryRecordResolver {

	public TelemetryRecord resolve(String line) {
		JsonNode root;
		try {
			root = JsonMapper.shared().readTree(line);
		} catch (JacksonException e) {
			throw TelemetryHandler.INVALID_JSON;
		}
		if (root == null || !root.isObject()) {
			throw TelemetryHandler.INVALID_JSON;
		}

		String vehicleId = requiredText(root, "vehicleId");
		String idempotencyKey = requiredText(root, "idempotencyKey");
		int ty = requiredInt(root, "ty");
		int schemaVersion = requiredInt(root, "schemaVersion");
		if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
			throw TelemetryHandler.unsupportedSchemaVersion(schemaVersion);
		}
		JsonNode pld = root.get("pld");
		if (pld == null || !pld.isObject()) {
			throw TelemetryHandler.missingField("pld");
		}

		Fields fields = new Fields(idempotencyKey);
		Instant receivedAt = fields.epochMillis(root, "ts");
		int tid = requiredInt(pld, "tid");

		return switch (ty) {
			case TY_TRIP -> new TripRecord(vehicleId, idempotencyKey, receivedAt, tid,
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
			case TY_MICROTRIP -> {
				Instant collectedAt = Instant.ofEpochMilli(requiredLong(pld, "clt"));
				Integer[] xyz = fields.xyz(pld);
				yield new MicrotripRecord(vehicleId, idempotencyKey, receivedAt, tid, collectedAt,
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
			default -> throw TelemetryHandler.unsupportedType(ty);
		};
	}

	private static String requiredText(JsonNode parent, String name) {
		String value = JsonNodeUtils.toNonBlankText(parent.get(name));
		if (value == null) {
			throw TelemetryHandler.missingField(name);
		}
		return value;
	}

	private static int requiredInt(JsonNode parent, String name) {
		Integer value = JsonNodeUtils.toInteger(parent.get(name));
		if (value == null) {
			throw TelemetryHandler.missingField(name);
		}
		return value;
	}

	private static long requiredLong(JsonNode parent, String name) {
		Long value = JsonNodeUtils.toLong(parent.get(name));
		if (value == null) {
			throw TelemetryHandler.missingField(name);
		}
		return value;
	}

	/** 버려도 되는 필드를 읽는다. 형식이 틀리면 null로 두고 경고만 남긴다. */
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

		/** em: "28"은 0x28 = bit3(급유턴) + bit5(급가속)이다. 숫자 28로 와도 16진으로 본다. */
		Integer eventMask(JsonNode parent) {
			JsonNode node = parent.get("em");
			if (JsonNodeUtils.isAbsent(node)) {
				return null;
			}
			String text = node.isString() ? node.stringValue().trim()
					: node.isIntegralNumber() ? node.asString() : null;
			if (text == null || !EVENT_MASK_HEX_PATTERN.matcher(text).matches()) {
				return invalid("em", node);
			}
			return Integer.parseInt(text, 16);
		}

		/** xyz: 세 값이 모두 정수가 아니면 셋 다 null이다. */
		Integer[] xyz(JsonNode parent) {
			Integer[] empty = new Integer[XYZ_SIZE];
			JsonNode node = parent.get("xyz");
			if (JsonNodeUtils.isAbsent(node)) {
				return empty;
			}
			if (!node.isString()) {
				invalid("xyz", node);
				return empty;
			}
			String[] parts = node.stringValue().split(XYZ_DELIMITER, -1);
			if (parts.length != XYZ_SIZE) {
				invalid("xyz", node);
				return empty;
			}
			Integer[] values = new Integer[XYZ_SIZE];
			for (int i = 0; i < XYZ_SIZE; i++) {
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
