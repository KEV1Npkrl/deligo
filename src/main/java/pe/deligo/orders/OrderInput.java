package pe.deligo.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record OrderInput(@NotBlank String merchantId,@NotBlank String zoneId,
    @NotBlank @Size(min=8,max=250) String address,
    @DecimalMin("-90") @DecimalMax("90") double latitude,
    @DecimalMin("-180") @DecimalMax("180") double longitude,
    @NotBlank @Pattern(regexp="CASH|YAPE") String paymentMethod,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal tip,
    @NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal expectedTotal,
    @NotBlank @Size(min=8,max=80) String idempotencyKey,
    @NotEmpty @Size(max=50) List<@Valid Item> items) {
    public record Item(@NotBlank String productId,@Min(1) @Max(50) int quantity) {}
}
