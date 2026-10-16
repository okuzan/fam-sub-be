package com.almonium.famsubbe.config

import com.almonium.famsubbe.dto.ApiErrorResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        IllegalStateException::class,
        BindException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
        ConstraintViolationException::class,
        MethodArgumentNotValidException::class,
        ResponseStatusException::class
    )
    fun handleBadRequest(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        return when (exception) {
            is MethodArgumentNotValidException -> {
                val message = exception.bindingResult.fieldErrors
                    .mapNotNull { error ->
                        error.defaultMessage?.let { "${error.field}: $it" }
                    }
                    .plus(exception.bindingResult.globalErrors.mapNotNull { it.defaultMessage })
                    .joinToString("; ")
                    .ifBlank { "Validation failed" }
                buildError(HttpStatus.BAD_REQUEST, message, request.requestURI)
            }
            is ConstraintViolationException -> {
                val message = exception.constraintViolations
                    .map { it.message }
                    .joinToString("; ")
                    .ifBlank { "Validation failed" }
                buildError(HttpStatus.BAD_REQUEST, message, request.requestURI)
            }
            is ResponseStatusException -> {
                val status = HttpStatus.valueOf(exception.statusCode.value())
                buildError(status, exception.reason ?: status.reasonPhrase, request.requestURI)
            }
            else -> {
                buildError(
                    HttpStatus.BAD_REQUEST,
                    exception.message ?: "Bad request",
                    request.requestURI
                )
            }
        }
    }

    @ExceptionHandler(
        EntityNotFoundException::class,
        NoSuchElementException::class
    )
    fun handleNotFound(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        return buildError(
            HttpStatus.NOT_FOUND,
            exception.message ?: "Not found",
            request.requestURI
        )
    }

    /**
     * Deleting a subscriber, service, or account that still has history behind it
     * trips a foreign key. That is a refusal, not a server fault.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        logger.warn("Integrity violation for {}: {}", request.requestURI, exception.mostSpecificCause.message)
        return buildError(
            HttpStatus.CONFLICT,
            "This record is still referenced by other data and cannot be changed or removed",
            request.requestURI
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandled(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        logger.error("Unhandled exception for {}", request.requestURI, exception)
        return buildError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            request.requestURI
        )
    }

    private fun buildError(
        status: HttpStatus,
        message: String,
        path: String?
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                message = message,
                status = status.value(),
                path = path
            )
        )
    }
}
