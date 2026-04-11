package com.example.parser.ast;

public class NodoAtributo extends Nodo {
    private String tipo;
    private String nombre;
    private boolean esPublico;
    private boolean esMutable;

    public NodoAtributo(String tipo, String nombre, boolean esPublico, boolean esMutable) {
        this.tipo = tipo;
        this.nombre = nombre;
        this.esPublico = esPublico;
        this.esMutable = esMutable;
    }

    public String getTipo() { return tipo; }
    public String getNombre() { return nombre; }
    public boolean esPublico() { return esPublico; }
    public boolean esMutable() { return esMutable; }

    @Override
    public String toString() { return "NodoAtributo(" + tipo + " " + nombre + ")"; }
}