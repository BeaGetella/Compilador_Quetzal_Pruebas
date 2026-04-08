package com.example.parser.ast;

import java.util.List;

public class Condicional extends Nodo {

    public static class Rama {
        public final Expresion condicion;
        public final List<Nodo> cuerpo;

        public Rama(Expresion condicion, List<Nodo> cuerpo) {
            this.condicion = condicion;
            this.cuerpo = cuerpo;
        }
    }

    public final List<Rama> ramas;

    public Condicional(List<Rama> ramas) {
        this.ramas = ramas;
    }

    @Override
    public String toString() {
        return "Condicional{ramas=" + ramas.size() + "}";
    }
}