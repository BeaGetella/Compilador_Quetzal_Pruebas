package com.example.parser.ast;

import java.util.List;

public class NodoSi extends Nodo {
    private Expresion condicion;
    private List<Nodo> cuerpoSi;           // instrucciones dentro del si
    private List<List<Nodo>> cuerposSinoSi; // cuerpos de cada sino si
    private List<Expresion> condicionesSinoSi; // condiciones de cada sino si
    private List<Nodo> cuerpoSino;         // instrucciones del sino final (puede ser null)

    public NodoSi(Expresion condicion,
                  List<Nodo> cuerpoSi,
                  List<Expresion> condicionesSinoSi,
                  List<List<Nodo>> cuerposSinoSi,
                  List<Nodo> cuerpoSino) {
        this.condicion = condicion;
        this.cuerpoSi = cuerpoSi;
        this.condicionesSinoSi = condicionesSinoSi;
        this.cuerposSinoSi = cuerposSinoSi;
        this.cuerpoSino = cuerpoSino;
    }

    public Expresion getCondicion() { return condicion; }
    public List<Nodo> getCuerpoSi() { return cuerpoSi; }
    public List<Expresion> getCondicionesSinoSi() { return condicionesSinoSi; }
    public List<List<Nodo>> getCuerposSinoSi() { return cuerposSinoSi; }
    public List<Nodo> getCuerpoSino() { return cuerpoSino; }
    public boolean tieneSino() { return cuerpoSino != null; }

    @Override
    public String toString() {
        return "NodoSi(condicion=" + condicion + ")";
    }
}