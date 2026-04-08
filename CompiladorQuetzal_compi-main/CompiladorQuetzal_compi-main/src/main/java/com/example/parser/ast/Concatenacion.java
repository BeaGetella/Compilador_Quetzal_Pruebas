package com.example.parser.ast;

// Representa concatenación de strings: "texto" + variable
public class Concatenacion extends Expresion {
    private Expresion izquierda;
    private Expresion derecha;

    public Concatenacion(Expresion izquierda, Expresion derecha) {
        this.izquierda = izquierda;
        this.derecha = derecha;
    }

    public Expresion getIzquierda() {
        return izquierda;
    }

    public Expresion getDerecha() {
        return derecha;
    }

    @Override
    public String toString() {
        return "Concatenacion(" + izquierda + " + " + derecha + ")";
    }
}