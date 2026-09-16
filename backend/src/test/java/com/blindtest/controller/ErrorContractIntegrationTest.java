package com.blindtest.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.config.CorrelationIdFilter;
import com.blindtest.error.GlobalExceptionHandler;
import com.blindtest.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ErrorContractTestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class),
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
        })
@Import({CorrelationIdFilter.class, GlobalExceptionHandler.class})
class ErrorContractIntegrationTest {

    private static final String REQUEST_ID = "2a4e1208-72f3-4a14-a8d5-f2e36c8f24f8";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorUsesTheCommonContract() throws Exception {
        mockMvc.perform(post("/test/errors/validation").
                        header(CorrelationIdFilter.HEADER_NAME, REQUEST_ID).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"name\":\"\"}")).
                andExpect(status().isBadRequest()).
                andExpect(header().string(CorrelationIdFilter.HEADER_NAME, REQUEST_ID)).
                andExpect(jsonPath("$.timestamp").isNotEmpty()).
                andExpect(jsonPath("$.status").value(400)).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.message").value("Request validation failed")).
                andExpect(jsonPath("$.path").value("/test/errors/validation")).
                andExpect(jsonPath("$.requestId").value(REQUEST_ID)).
                andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"));
    }

    @Test
    void notFoundAndConflictErrorsUseStableCodes() throws Exception {
        mockMvc.perform(get("/test/errors/not-found")).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND")).
                andExpect(jsonPath("$.requestId").isNotEmpty()).
                andExpect(jsonPath("$.fieldErrors").doesNotExist());

        mockMvc.perform(get("/test/errors/conflict")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("CONFLICT")).
                andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void internalErrorHidesExceptionDetailsAndProviderSecrets() throws Exception {
        mockMvc.perform(get("/test/errors/internal")).
                andExpect(status().isInternalServerError()).
                andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).
                andExpect(jsonPath("$.message").value("An unexpected error occurred")).
                andExpect(jsonPath("$.requestId").isNotEmpty()).
                andExpect(jsonPath("$.trace").doesNotExist()).
                andExpect(jsonPath("$.exception").doesNotExist()).
                andExpect(content().string(not(containsString("provider-secret-value"))));
    }

}
