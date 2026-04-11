package com.example.parser.ast;

import java.util.List;

public class LiteralLista extends Expresion {
    private List<Expresion> elementos;
    private String tipoElemento; // "entero", "texto", etc. puede ser null si no tipada

    public LiteralLista(List<Expresion> elementos, String tipoElemento) {
        this.elementos = elementos;
        this.tipoElemento = tipoElemento;
    }

    public List<Expresion> getElementos() { return elementos; }
    public String getTipoElemento() { return tipoElemento; }

    @Override
    public String toString() { return "LiteralLista(" + elementos + ")"; }
}