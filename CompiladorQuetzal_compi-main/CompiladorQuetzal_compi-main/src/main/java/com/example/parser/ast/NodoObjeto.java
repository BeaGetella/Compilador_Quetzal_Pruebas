package com.example.parser.ast;

import java.util.List;

public class NodoObjeto extends Nodo {
    private String nombre;
    private List<NodoAtributo> atributos;
    private NodoConstructor constructor;
    private List<NodoMetodo> metodos;

    public NodoObjeto(String nombre,
                      List<NodoAtributo> atributos,
                      NodoConstructor constructor,
                      List<NodoMetodo> metodos) {
        this.nombre = nombre;
        this.atributos = atributos;
        this.constructor = constructor;
        this.metodos = metodos;
    }

    public String getNombre() { return nombre; }
    public List<NodoAtributo> getAtributos() { return atributos; }
    public NodoConstructor getConstructor() { return constructor; }
    public List<NodoMetodo> getMetodos() { return metodos; }

    @Override
    public String toString() { return "NodoObjeto(" + nombre + ")"; }
}