package com.redhat.service.bridge.manager.api.user;

import java.util.Collection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.redhat.service.bridge.infra.api.APIConstants;
import com.redhat.service.bridge.manager.TestConstants;
import com.redhat.service.bridge.manager.api.models.responses.ErrorListResponse;
import com.redhat.service.bridge.manager.api.models.responses.ErrorResponse;
import com.redhat.service.bridge.manager.utils.ExceptionHelper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ErrorsAPITest {

    private static Collection<Class<?>> exceptionClasses;

    @BeforeAll
    private static void init() {
        exceptionClasses = ExceptionHelper.getExceptions();
    }

    @Test
    @TestSecurity(user = TestConstants.DEFAULT_CUSTOMER_ID)
    void testGetList() {
        ErrorListResponse response = given().contentType(ContentType.JSON).when().get(APIConstants.ERROR_API_BASE_PATH).as(ErrorListResponse.class);
        assertThat(exceptionClasses).hasSize((int) response.getTotal());
        assertThat(response.getItems().isEmpty()).isFalse();
        for (ErrorResponse item : response.getItems()) {
            assertThat(item).isEqualTo(given().contentType(ContentType.JSON).when().get(item.getHref()).as(ErrorResponse.class));
        }
    }

}
