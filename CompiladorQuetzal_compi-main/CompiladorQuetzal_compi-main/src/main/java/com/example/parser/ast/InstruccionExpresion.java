package com.example.parser.ast;

public class InstruccionExpresion extends Nodo {
    private Expresion expresion;

    public InstruccionExpresion(Expresion expresion) {
        this.expresion = expresion;
    }

    public Expresion getExpresion() {
        return expresion;
    }

    @Override
    public String toString() {
        return "InstruccionExpresion(" + expresion + ")";
    }
}