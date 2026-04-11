package com.example.parser.ast;

import java.util.List;

public class NodoFuncion extends Nodo {
    private String tipoRetorno;        // "vacio", "entero", "texto", etc.
    private String nombre;             // nombre de la función
    private List<String[]> parametros; // cada String[] = {tipo, nombre}
    private List<Nodo> cuerpo;

    public NodoFuncion(String tipoRetorno, String nombre,
                       List<String[]> parametros, List<Nodo> cuerpo) {
        this.tipoRetorno = tipoRetorno;
        this.nombre = nombre;
        this.parametros = parametros;
        this.cuerpo = cuerpo;
    }

    public String getTipoRetorno() { return tipoRetorno; }
    public String getNombre() { return nombre; }
    public List<String[]> getParametros() { return parametros; }
    public List<Nodo> getCuerpo() { return cuerpo; }

    @Override
    public String toString() {
        return "NodoFuncion(" + tipoRetorno + " " + nombre + ")";
    }
}