package com.example.parser.ast;

import java.util.List;

public class NodoMientras extends Nodo {
    private Expresion condicion;
    private List<Nodo> cuerpo;

    public NodoMientras(Expresion condicion, List<Nodo> cuerpo) {
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    public Expresion getCondicion() { return condicion; }
    public List<Nodo> getCuerpo() { return cuerpo; }

    @Override
    public String toString() {
        return "NodoMientras(condicion=" + condicion + ")";
    }
}