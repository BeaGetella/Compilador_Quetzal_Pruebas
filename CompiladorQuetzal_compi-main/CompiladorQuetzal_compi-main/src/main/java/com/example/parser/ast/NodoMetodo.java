package com.example.parser.ast;

import java.util.List;

public class NodoMetodo extends Nodo {
    private String tipoRetorno;
    private String nombre;
    private List<String[]> parametros;
    private List<Nodo> cuerpo;
    private boolean esPublico;

    public NodoMetodo(String tipoRetorno, String nombre,
                      List<String[]> parametros, List<Nodo> cuerpo,
                      boolean esPublico) {
        this.tipoRetorno = tipoRetorno;
        this.nombre = nombre;
        this.parametros = parametros;
        this.cuerpo = cuerpo;
        this.esPublico = esPublico;
    }

    public String getTipoRetorno() { return tipoRetorno; }
    public String getNombre() { return nombre; }
    public List<String[]> getParametros() { return parametros; }
    public List<Nodo> getCuerpo() { return cuerpo; }
    public boolean esPublico() { return esPublico; }

    @Override
    public String toString() { return "NodoMetodo(" + tipoRetorno + " " + nombre + ")"; }
}