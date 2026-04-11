package com.example.parser.ast;

public class AccesoLista extends Expresion {
    private Expresion lista;
    private Expresion indice;

    public AccesoLista(Expresion lista, Expresion indice) {
        this.lista = lista;
        this.indice = indice;
    }

    public Expresion getLista() { return lista; }
    public Expresion getIndice() { return indice; }

    @Override
    public String toString() { return "AccesoLista(" + lista + "[" + indice + "])"; }
}