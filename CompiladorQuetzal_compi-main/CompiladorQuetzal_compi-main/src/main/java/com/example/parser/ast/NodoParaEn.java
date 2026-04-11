package com.example.parser.ast;

import java.util.List;

public class NodoParaEn extends Nodo {
    private String tipoVariable;
    private String nombreVariable;
    private Expresion iterable;
    private List<Nodo> cuerpo;

    public NodoParaEn(String tipoVariable, String nombreVariable,
                      Expresion iterable, List<Nodo> cuerpo) {
        this.tipoVariable = tipoVariable;
        this.nombreVariable = nombreVariable;
        this.iterable = iterable;
        this.cuerpo = cuerpo;
    }

    public String getTipoVariable() { return tipoVariable; }
    public String getNombreVariable() { return nombreVariable; }
    public Expresion getIterable() { return iterable; }
    public List<Nodo> getCuerpo() { return cuerpo; }

    @Override
    public String toString() { return "NodoParaEn(" + tipoVariable + " " + nombreVariable + ")"; }
}