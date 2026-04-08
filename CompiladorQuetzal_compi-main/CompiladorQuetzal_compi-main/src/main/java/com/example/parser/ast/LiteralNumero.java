package com.example.parser.ast;

// Representa un número literal como 5, 10, 3
public class LiteralNumero extends Expresion {
    private int valor;

    public LiteralNumero(int valor) {
        this.valor = valor;
    }

    public int getValor() {
        return valor;
    }

    @Override
    public String toString() {
        return "LiteralNumero(" + valor + ")";
    }
}