package com.example.parser.ast;

public class OperacionTernaria extends Expresion {
    private Expresion condicion;   // lo que va antes del ?
    private Expresion siVerdadero; // lo que va entre ? y :
    private Expresion siFalso;     // lo que va después del :

    public OperacionTernaria(Expresion condicion, Expresion siVerdadero, Expresion siFalso) {
        this.condicion = condicion;
        this.siVerdadero = siVerdadero;
        this.siFalso = siFalso;
    }

    public Expresion getCondicion() { return condicion; }
    public Expresion getSiVerdadero() { return siVerdadero; }
    public Expresion getSiFalso() { return siFalso; }

    @Override
    public String toString() {
        return "Ternario(" + condicion + " ? " + siVerdadero + " : " + siFalso + ")";
    }
}