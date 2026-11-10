package com.pikachew.dangUBI.domain.telemetry.policy;

import java.time.Instant;
import java.util.regex.Pattern;

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
 * 수집 메시지(JSONL 한 줄)를 {@link TelemetryRecord}로 해석하는 규칙.
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

	public static final int TY_TRIP = 3;
	public static final int TY_MICROTRIP = 4;
	public static final int SUPPORTED_SCHEMA_VERSION = 1;

	private static final Pattern EVENT_MASK_HEX = Pattern.compile("[0-9A-Fa-f]{1,2}");

	private final JsonMapper jsonMapper = JsonMapper.shared();

	public TelemetryRecord resolve(String line) {
		JsonNode root;
		try {
			root = jsonMapper.readTree(line);
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
		JsonNode node = parent.get(name);
		if (node == null || !node.isString() || node.stringValue().isBlank()) {
			throw TelemetryHandler.missingField(name);
		}
		return node.stringValue();
	}

	private static int requiredInt(JsonNode parent, String name) {
		Long value = toLong(parent.get(name));
		if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw TelemetryHandler.missingField(name);
		}
		return value.intValue();
	}

	private static long requiredLong(JsonNode parent, String name) {
		Long value = toLong(parent.get(name));
		if (value == null) {
			throw TelemetryHandler.missingField(name);
		}
		return value;
	}

	/** JSON 정수 또는 정수 문자열이면 값을, 아니면 null을 돌려준다. */
	private static Long toLong(JsonNode node) {
		if (node == null) {
			return null;
		}
		if (node.isIntegralNumber() && node.canConvertToLong()) {
			return node.longValue();
		}
		if (node.isString()) {
			try {
				return Long.parseLong(node.stringValue().trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** 버려도 되는 필드를 읽는다. 형식이 틀리면 null로 두고 경고만 남긴다. */
	private record Fields(String idempotencyKey) {

		Integer integer(JsonNode parent, String name) {
			JsonNode node = parent.get(name);
			if (node == null || node.isNull()) {
				return null;
			}
			Long value = toLong(node);
			if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
				return invalid(name, node);
			}
			return value.intValue();
		}

		Instant epochMillis(JsonNode parent, String name) {
			JsonNode node = parent.get(name);
			if (node == null || node.isNull()) {
				return null;
			}
			Long value = toLong(node);
			return value == null ? invalid(name, node) : Instant.ofEpochMilli(value);
		}

		/** em: 한 주기의 이벤트를 16진으로 적은 값. "28"은 0x28 = bit3(급유턴) + bit5(급가속)이다. 숫자 28로 와도 16진으로 본다. */
		Integer eventMask(JsonNode parent) {
			JsonNode node = parent.get("em");
			if (node == null || node.isNull()) {
				return null;
			}
			String text = node.isString() ? node.stringValue().trim()
					: node.isIntegralNumber() ? node.asString() : null;
			if (text == null || !EVENT_MASK_HEX.matcher(text).matches()) {
				return invalid("em", node);
			}
			return Integer.parseInt(text, 16);
		}

		/** xyz: "가속도X,가속도Y,각속도Y". 세 값이 모두 정수가 아니면 셋 다 null이다. */
		Integer[] xyz(JsonNode parent) {
			Integer[] empty = {null, null, null};
			JsonNode node = parent.get("xyz");
			if (node == null || node.isNull()) {
				return empty;
			}
			if (!node.isString()) {
				invalid("xyz", node);
				return empty;
			}
			String[] parts = node.stringValue().split(",", -1);
			if (parts.length != 3) {
				invalid("xyz", node);
				return empty;
			}
			Integer[] values = new Integer[3];
			for (int i = 0; i < 3; i++) {
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
