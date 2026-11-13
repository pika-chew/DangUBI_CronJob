package com.pikachew.dangUBI.common.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Reason {

	private final boolean isSuccess;
	private final Integer code;
	private final String message;

}
