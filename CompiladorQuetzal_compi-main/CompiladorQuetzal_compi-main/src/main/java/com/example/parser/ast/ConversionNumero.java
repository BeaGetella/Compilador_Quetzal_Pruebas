package com.example.parser.ast;

public class ConversionNumero extends Expresion {
    private Expresion expresion;

    public ConversionNumero(Expresion expresion) {
        this.expresion = expresion;
    }

    public Expresion getExpresion() {
        return expresion;
    }

    @Override
    public String toString() {
        return "ConversionNumero{" + expresion + ".numero()}";
    }
}