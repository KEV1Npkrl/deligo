package pe.deligo.orders;

import java.math.*;

/** Immutable quote. Product revenue and tips are never commissionable. */
public record Pricing(BigDecimal subtotal, BigDecimal delivery, BigDecimal rain, BigDecimal tip,
                      BigDecimal platformShare, BigDecimal courierShare, BigDecimal total) {
    public static Pricing calculate(BigDecimal subtotal,BigDecimal delivery,BigDecimal rain,BigDecimal tip) {
        for (var value:new BigDecimal[]{subtotal,delivery,rain,tip})
            if(value==null||value.signum()<0||value.scale()>2) throw new IllegalArgumentException("Importe inválido");
        var base=delivery.add(rain);
        var commission=base.multiply(new BigDecimal("0.15")).setScale(2,RoundingMode.HALF_UP);
        return new Pricing(subtotal,delivery,rain,tip,commission,base.subtract(commission).add(tip),subtotal.add(base).add(tip));
    }
}
