package com.example.parser.ast;

// Representa un número decimal literal como 3.14, 0.5, 22.5
public class LiteralDecimal extends Expresion {
    private double valor;

    public LiteralDecimal(double valor) {
        this.valor = valor;
    }

    public double getValor() {
        return valor;
    }

    @Override
    public String toString() {
        return "LiteralDecimal(" + valor + ")";
    }
}