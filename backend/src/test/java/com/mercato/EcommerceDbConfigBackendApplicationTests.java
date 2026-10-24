package com.mercato;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class EcommerceDbConfigBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
