package pe.deligo.shared;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("message", "No se encontró el recurso.")); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("message", "Revisa los datos ingresados y los límites de cada campo.")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message", "El registro ya existe o cambió. Actualiza y vuelve a intentar.")); }
}
