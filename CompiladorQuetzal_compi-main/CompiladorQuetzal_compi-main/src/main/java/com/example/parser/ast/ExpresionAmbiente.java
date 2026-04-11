package com.example.parser.ast;

public class ExpresionAmbiente extends Expresion {
    private String campo;

    public ExpresionAmbiente(String campo) {
        this.campo = campo;
    }

    public String getCampo() { return campo; }

    @Override
    public String toString() { return "Ambiente(" + campo + ")"; }
}