package com.example.parser.ast;

import java.util.List;

public class NodoHacerMientras extends Nodo {
    private List<Nodo> cuerpo;
    private Expresion condicion;

    public NodoHacerMientras(List<Nodo> cuerpo, Expresion condicion) {
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    public List<Nodo> getCuerpo() { return cuerpo; }
    public Expresion getCondicion() { return condicion; }

    @Override
    public String toString() {
        return "NodoHacerMientras(condicion=" + condicion + ")";
    }
}