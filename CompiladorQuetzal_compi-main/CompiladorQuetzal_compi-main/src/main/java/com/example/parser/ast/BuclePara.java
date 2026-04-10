package com.example.parser.ast;

import java.util.List;

/**
 * Representa el bucle para clásico:
 *   para (entero var i = 0; i < 5; i++) { ... }
 */
public class BuclePara extends Nodo {

    private final DeclaracionVariable inicializacion; // entero var i = 0
    private final Expresion condicion;                // i < 5
    private final Expresion incremento;              // i++ (OperacionUnaria postfija) o i = i + 1 (Asignacion)
    private final List<Nodo> cuerpo;                 // instrucciones dentro del bloque

    public BuclePara(DeclaracionVariable inicializacion,
                     Expresion condicion,
                     Expresion incremento,
                     List<Nodo> cuerpo) {
        this.inicializacion = inicializacion;
        this.condicion = condicion;
        this.incremento = incremento;
        this.cuerpo = cuerpo;
    }

    public DeclaracionVariable getInicializacion() { return inicializacion; }
    public Expresion getCondicion()                { return condicion; }
    public Expresion getIncremento()               { return incremento; }
    public List<Nodo> getCuerpo()                  { return cuerpo; }

    @Override
    public String toString() {
        return "BuclePara{init=" + inicializacion +
                ", condicion=" + condicion +
                ", incremento=" + incremento +
                ", cuerpo=" + cuerpo.size() + " instrucciones}";
    }
}