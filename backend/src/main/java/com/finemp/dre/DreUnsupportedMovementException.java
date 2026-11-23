package com.finemp.dre;

/**
 * Movimento que a regra de sinal atual não sabe interpretar (valor negativo, #449).
 * Estende IllegalArgumentException para manter o contrato da calculadora.
 */
public class DreUnsupportedMovementException extends IllegalArgumentException {
    public DreUnsupportedMovementException(String message) {
        super(message);
    }
}
