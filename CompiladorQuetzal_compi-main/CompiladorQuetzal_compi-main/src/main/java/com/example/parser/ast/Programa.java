package com.example.parser.ast;

import java.util.List;

// Representa el programa completo (raíz del AST)
public class Programa extends Nodo {
    private List<Nodo> instrucciones;

    public Programa(List<Nodo> instrucciones) {
        this.instrucciones = instrucciones;
    }

    public List<Nodo> getInstrucciones() {
        return instrucciones;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Programa{\n");
        for (Nodo instruccion : instrucciones) {
            sb.append("  ").append(instruccion).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}