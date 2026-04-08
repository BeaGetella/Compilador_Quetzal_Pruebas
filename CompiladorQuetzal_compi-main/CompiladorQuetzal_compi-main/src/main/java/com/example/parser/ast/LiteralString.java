package com.example.parser.ast;

// Representa un string literal como "Hola mundo"
public class LiteralString extends Expresion {
    private String valor;

    public LiteralString(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    @Override
    public String toString() {
        return "LiteralString(\"" + valor + "\")";
    }
}