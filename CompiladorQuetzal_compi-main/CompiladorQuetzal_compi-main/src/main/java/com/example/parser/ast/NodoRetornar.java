package com.example.parser.ast;

public class NodoRetornar extends Nodo {
    private Expresion valor; // puede ser null si es vacio

    public NodoRetornar(Expresion valor) {
        this.valor = valor;
    }

    public Expresion getValor() { return valor; }
    public boolean tieneValor() { return valor != null; }

    @Override
    public String toString() {
        return "NodoRetornar(" + valor + ")";
    }
}