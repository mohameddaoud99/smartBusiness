package com.sales.smartBusiness.exception;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database-level guards must reach the user as a readable 409, never as a 500 —
 * the service checks cannot see a concurrent request that slips in between.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private DataIntegrityViolationException violation(String sqlState) {
        SQLException driverError = new SQLException("constraint violated", sqlState);
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("wrapped by Hibernate", driverError));
    }

    @Test
    @DisplayName("a unique-constraint violation becomes a 409")
    void uniqueViolationIsAConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23505"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("already used");
    }

    @Test
    @DisplayName("a foreign-key violation becomes a 409")
    void foreignKeyViolationIsAConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23503"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("still used");
    }

    @Test
    @DisplayName("any other integrity error is a bug and stays a neutral 500")
    void otherIntegrityErrorIsAServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23502"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("a concurrent modification becomes a 409 asking to reload")
    void optimisticLockIsAConflict() {
        var stale = new ObjectOptimisticLockingFailureException("Customer", 1L,
                new StaleObjectStateException("Customer", 1L));

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(stale);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Reload");
    }

    @Test
    @DisplayName("a required query parameter left out is a 400, not a 500")
    void missingParameterIsABadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(
                new org.springframework.web.bind.MissingServletRequestParameterException("type", "SalesDocumentType"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("'type' is required");
    }

    @Test
    @DisplayName("a verb the endpoint does not offer is a 405, not a 500")
    void unsupportedMethodIsMethodNotAllowed() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getStatus()).isEqualTo(405);
    }

    @Test
    @DisplayName("an address nothing answers to is a 404, not a 500")
    void unknownAddressIsNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.PUT, "api/payments/30", "api/payments/30"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
