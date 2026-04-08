package com.example.parser.ast;

// Representa una referencia a una variable como 'a', 'b', 'suma'
public class Variable extends Expresion {
    private String nombre;

    public Variable(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public String toString() {
        return "Variable(" + nombre + ")";
    }
}