package com.blindtest.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only endpoints used to exercise exception handling paths that production controllers cannot force. */
@RestController
@RequestMapping("/test/errors")
public class ErrorContractTestController {

    @PostMapping("/validation")
    void validation(@Valid @RequestBody TestRequest request) {
        // Validation happens before method invocation.
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @GetMapping("/internal")
    void internal() {
        throw new IllegalStateException("provider-secret-value");
    }

    record TestRequest(@NotBlank String name) {
    }
}
