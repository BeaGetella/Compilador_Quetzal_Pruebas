package com.example.parser.ast;

// Representa la conversión .texto() de un número a string
public class ConversionTexto extends Expresion {
    private Expresion expresion;

    public ConversionTexto(Expresion expresion) {
        this.expresion = expresion;
    }

    public Expresion getExpresion() {
        return expresion;
    }

    @Override
    public String toString() {
        return "ConversionTexto(" + expresion + ")";
    }
}