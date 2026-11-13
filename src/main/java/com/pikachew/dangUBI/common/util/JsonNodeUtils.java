package com.pikachew.dangUBI.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

/** JsonNode에서 값을 느슨하게 꺼내는 함수 모음. 형식이 맞지 않으면 예외 대신 null을 돌려준다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonNodeUtils {

	/** JSON 정수 또는 정수 문자열("72")이면 값을, 없거나 다른 형식이면 null. */
	public static Long toLong(JsonNode node) {
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

	/** {@link #toLong}이 int 범위 안이면 값을, 아니면 null. */
	public static Integer toInteger(JsonNode node) {
		Long value = toLong(node);
		if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			return null;
		}
		return value.intValue();
	}

	/** 비어 있지 않은 JSON 문자열이면 값을, 아니면 null. */
	public static String toNonBlankText(JsonNode node) {
		if (node == null || !node.isString() || node.stringValue().isBlank()) {
			return null;
		}
		return node.stringValue();
	}

	/** 필드가 없거나 JSON null인지. */
	public static boolean isAbsent(JsonNode node) {
		return node == null || node.isNull();
	}

}
