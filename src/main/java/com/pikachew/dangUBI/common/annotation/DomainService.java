package com.pikachew.dangUBI.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

/**
 * 생성·변경 같은 쓰기 로직을 맡는 도메인 서비스.
 *
 * chunk 트랜잭션 안에서 호출되므로 별도 트랜잭션을 열지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {

	@AliasFor(annotation = Component.class)
	String value() default "";

}
