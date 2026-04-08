package com.example.parser.ast;

public class Asignacion extends Expresion {
    private String nombre;      // nombre de la variable: "contador"
    private String operador;    // "=", "+=", "-=", "*=", "/=", "%="
    private Expresion valor;    // expresión del lado derecho

    public Asignacion(String nombre, String operador, Expresion valor) {
        this.nombre = nombre;
        this.operador = operador;
        this.valor = valor;
    }

    public String getNombre() { return nombre; }
    public String getOperador() { return operador; }
    public Expresion getValor() { return valor; }

    @Override
    public String toString() {
        return "Asignacion(" + nombre + " " + operador + " " + valor + ")";
    }
}