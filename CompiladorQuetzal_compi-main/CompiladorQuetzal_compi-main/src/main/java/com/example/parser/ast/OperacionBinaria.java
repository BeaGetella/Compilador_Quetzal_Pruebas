package com.example.parser.ast;

// Representa operaciones binarias: a + b, a - b, a * b, a / b
public class OperacionBinaria extends Expresion {
    private String operador;      // "+", "-", "*", "/"
    private Expresion izquierda;  // Lado izquierdo (ej: 'a')
    private Expresion derecha;    // Lado derecho (ej: 'b')

    public OperacionBinaria(String operador, Expresion izquierda, Expresion derecha) {
        this.operador = operador;
        this.izquierda = izquierda;
        this.derecha = derecha;
    }

    public String getOperador() {
        return operador;
    }

    public Expresion getIzquierda() {
        return izquierda;
    }

    public Expresion getDerecha() {
        return derecha;
    }

    @Override
    public String toString() {
        return "OperacionBinaria(" + izquierda + " " + operador + " " + derecha + ")";
    }
}