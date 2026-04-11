package com.example.parser.ast;

import java.util.List;

public class ExpresionNuevo extends Expresion {
    private String tipoObjeto;
    private List<Expresion> argumentos;

    public ExpresionNuevo(String tipoObjeto, List<Expresion> argumentos) {
        this.tipoObjeto = tipoObjeto;
        this.argumentos = argumentos;
    }

    public String getTipoObjeto() { return tipoObjeto; }
    public List<Expresion> getArgumentos() { return argumentos; }

    @Override
    public String toString() { return "Nuevo(" + tipoObjeto + ")"; }
}