package com.example.parser.ast;


public class OperacionUnaria extends Expresion {
    private String operador;    // "no", "!", "-", "++", "--"
    private Expresion operando;
    private boolean esPostfijo; // true = contador++, false = -x

    public OperacionUnaria(String operador, Expresion operando, boolean esPostfijo) {
        this.operador = operador;
        this.operando = operando;
        this.esPostfijo = esPostfijo;
    }

    public String getOperador() { return operador; }
    public Expresion getOperando() { return operando; }
    public boolean esPostfijo() { return esPostfijo; }

    @Override
    public String toString() {
        return esPostfijo
                ? "OperacionUnaria(" + operando + operador + ")"
                : "OperacionUnaria(" + operador + operando + ")";
    }
}