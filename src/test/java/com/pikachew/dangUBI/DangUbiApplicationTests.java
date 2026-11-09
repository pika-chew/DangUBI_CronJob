package com.pikachew.dangUBI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// MySQL이 있어야 컨텍스트가 뜬다. SPRING_DATASOURCE_URL이 있을 때만 실행한다.
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class DangUbiApplicationTests {

	@Test
	void contextLoads() {
	}

}
