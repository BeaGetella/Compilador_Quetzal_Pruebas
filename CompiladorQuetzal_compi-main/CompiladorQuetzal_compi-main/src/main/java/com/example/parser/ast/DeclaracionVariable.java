package com.example.parser.ast;

// Representa una declaración de variable: entero a = 5
public class DeclaracionVariable extends Nodo {
    private String tipo;          // "entero"
    private String nombre;        // "a", "suma", etc.
    private Expresion valor;      // La expresión que se asigna

    public DeclaracionVariable(String tipo, String nombre, Expresion valor) {
        this.tipo = tipo;
        this.nombre = nombre;
        this.valor = valor;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public Expresion getValor() {
        return valor;
    }

    @Override
    public String toString() {
        return "DeclaracionVariable{tipo='" + tipo + "', nombre='" + nombre + "', valor=" + valor + "}";
    }
}