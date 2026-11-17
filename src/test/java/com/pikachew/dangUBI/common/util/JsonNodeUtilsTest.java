package com.pikachew.dangUBI.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 단위 테스트: JsonNode 값 꺼내기. */
class JsonNodeUtilsTest {

	private final JsonNode node = JsonMapper.shared().readTree("""
			{"int":72,"long":1793575800000,"numText":" 72 ","word":"fast","blank":"  ","nil":null,"dec":1.5}""");

	@Test
	void 정수와_정수_문자열을_숫자로_꺼낸다() {
		assertThat(JsonNodeUtils.toLong(node.get("int"))).isEqualTo(72L);
		assertThat(JsonNodeUtils.toLong(node.get("long"))).isEqualTo(1793575800000L);
		assertThat(JsonNodeUtils.toLong(node.get("numText"))).isEqualTo(72L);
	}

	@Test
	void 숫자가_아니면_null이다() {
		assertThat(JsonNodeUtils.toLong(node.get("word"))).isNull();
		assertThat(JsonNodeUtils.toLong(node.get("dec"))).isNull();
		assertThat(JsonNodeUtils.toLong(node.get("nil"))).isNull();
		assertThat(JsonNodeUtils.toLong(node.get("missing"))).isNull();
	}

	@Test
	void int_범위를_넘으면_toInteger는_null이다() {
		assertThat(JsonNodeUtils.toInteger(node.get("int"))).isEqualTo(72);
		assertThat(JsonNodeUtils.toInteger(node.get("long"))).isNull();
	}

	@Test
	void 비어_있지_않은_문자열만_꺼낸다() {
		assertThat(JsonNodeUtils.toNonBlankText(node.get("word"))).isEqualTo("fast");
		assertThat(JsonNodeUtils.toNonBlankText(node.get("blank"))).isNull();
		assertThat(JsonNodeUtils.toNonBlankText(node.get("int"))).isNull();
		assertThat(JsonNodeUtils.toNonBlankText(node.get("missing"))).isNull();
	}

	@Test
	void 없거나_null이면_absent다() {
		assertThat(JsonNodeUtils.isAbsent(node.get("missing"))).isTrue();
		assertThat(JsonNodeUtils.isAbsent(node.get("nil"))).isTrue();
		assertThat(JsonNodeUtils.isAbsent(node.get("int"))).isFalse();
	}

}
