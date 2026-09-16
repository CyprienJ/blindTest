package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void validRequestIdIsAvailableToTheResponseAndLoggingMdc() throws Exception {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(req.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).isEqualTo(requestId);
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(requestId);
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(requestId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void malformedRequestIdIsReplacedWithAGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            String generatedId = (String) req.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
            assertThatCode(() -> UUID.fromString(generatedId)).doesNotThrowAnyException();
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(generatedId);
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).
                isEqualTo(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
