package com.example.parser.ast;

import java.util.List;

public class NodoConstructor extends Nodo {
    private List<String[]> parametros; // {tipo, nombre}
    private List<Nodo> cuerpo;

    public NodoConstructor(List<String[]> parametros, List<Nodo> cuerpo) {
        this.parametros = parametros;
        this.cuerpo = cuerpo;
    }

    public List<String[]> getParametros() { return parametros; }
    public List<Nodo> getCuerpo() { return cuerpo; }

    @Override
    public String toString() { return "NodoConstructor()"; }
}