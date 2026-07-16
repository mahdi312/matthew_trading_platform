package com.mst.matt.tradingservice.exception;

import com.mst.matt.tradingservice.dto.TradeResponse;
import com.mst.matt.tradingservice.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TradeService tradeService;

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<TradeResponse> handleDuplicateIdempotencyKey(
            DataIntegrityViolationException e,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null) {
            return tradeService.findByIdempotencyKey(idempotencyKey)
                    .map(t -> ResponseEntity.ok(TradeResponse.from(t)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}