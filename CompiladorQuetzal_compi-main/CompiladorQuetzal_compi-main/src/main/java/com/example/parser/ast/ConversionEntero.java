package com.example.parser.ast;

public class ConversionEntero extends Expresion {
    private Expresion expresion;

    public ConversionEntero(Expresion expresion) {
        this.expresion = expresion;
    }

    public Expresion getExpresion() {
        return expresion;
    }

    @Override
    public String toString() {
        return "ConversionEntero{" + expresion + ".entero()}";
    }
}