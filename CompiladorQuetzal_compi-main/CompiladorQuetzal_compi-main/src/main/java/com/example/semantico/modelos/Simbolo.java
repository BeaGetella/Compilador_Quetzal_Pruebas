package com.example.semantico.modelos;

import com.example.semantico.enums.TipoDato;
import com.example.semantico.enums.TipoSimbolo;

public class Simbolo {
    private String nombre;
    private TipoDato tipoDato;
    private TipoSimbolo tipoSimbolo;
    private int lineaDeclaracion;
    private int indice;  // Índice para variables locales en JVM

    public Simbolo(String nombre, TipoDato tipoDato, TipoSimbolo tipoSimbolo, int lineaDeclaracion, int indice) {
        this.nombre = nombre;
        this.tipoDato = tipoDato;
        this.tipoSimbolo = tipoSimbolo;
        this.lineaDeclaracion = lineaDeclaracion;
        this.indice = indice;
    }

    // Getters
    public String getNombre() {
        return nombre;
    }

    public TipoDato getTipoDato() {
        return tipoDato;
    }

    public TipoSimbolo getTipoSimbolo() {
        return tipoSimbolo;
    }

    public int getLineaDeclaracion() {
        return lineaDeclaracion;
    }

    public int getIndice() {
        return indice;
    }

    public void setIndice(int indice) {
        this.indice = indice;
    }

    @Override
    public String toString() {
        return "Simbolo{" +
                "nombre='" + nombre + '\'' +
                ", tipoDato=" + tipoDato +
                ", tipoSimbolo=" + tipoSimbolo +
                ", línea=" + lineaDeclaracion +
                ", índice=" + indice +
                '}';
    }
}