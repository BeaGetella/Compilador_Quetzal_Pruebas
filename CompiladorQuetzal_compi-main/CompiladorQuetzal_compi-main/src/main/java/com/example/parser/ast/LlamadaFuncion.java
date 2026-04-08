package com.example.parser.ast;

import java.util.List;

// Representa una llamada a función como consola.mostrar(...)
public class LlamadaFuncion extends Expresion {
    private String objeto;         // "consola"
    private String metodo;         // "mostrar"
    private List<Expresion> argumentos;

    public LlamadaFuncion(String objeto, String metodo, List<Expresion> argumentos) {
        this.objeto = objeto;
        this.metodo = metodo;
        this.argumentos = argumentos;
    }

    public String getObjeto() {
        return objeto;
    }

    public String getMetodo() {
        return metodo;
    }

    public List<Expresion> getArgumentos() {
        return argumentos;
    }

    @Override
    public String toString() {
        return "LlamadaFuncion{" + objeto + "." + metodo + "(" + argumentos + ")}";
    }
}