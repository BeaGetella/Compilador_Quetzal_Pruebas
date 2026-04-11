package com.example.parser.ast;

public class AccesoJsn extends Expresion {
    private Expresion objeto;   // la variable jsn
    private String propiedad;  // el nombre de la propiedad

    public AccesoJsn(Expresion objeto, String propiedad) {
        this.objeto = objeto;
        this.propiedad = propiedad;
    }

    public Expresion getObjeto() { return objeto; }
    public String getPropiedad() { return propiedad; }

    @Override
    public String toString() { return "AccesoJsn(" + objeto + "." + propiedad + ")"; }
}