package com.example.semantico.gestores;

import com.example.semantico.enums.TipoDato;
import com.example.semantico.enums.TipoSimbolo;
import com.example.semantico.modelos.Scope;
import com.example.semantico.modelos.Simbolo;

public class TablaSimbolos {
    private Scope scopeActual;
    private int contadorIndices;

    public TablaSimbolos() {
        this.scopeActual = new Scope(null);  // Scope global
        this.contadorIndices = 1;  // 0 reservado para args en main
    }

    // Agregar variable
    public void agregarVariable(String nombre, TipoDato tipo, int linea) {
        // Verificar si ya existe en el scope actual
        if (scopeActual.existeLocal(nombre)) {
            Simbolo existente = scopeActual.obtener(nombre);
            throw new RuntimeException(
                    "Error en línea " + linea + ": " +
                            "Variable '" + nombre + "' ya declarada en línea " + existente.getLineaDeclaracion()
            );
        }

        // Crear nuevo símbolo
        int indice = contadorIndices++;
        Simbolo simbolo = new Simbolo(nombre, tipo, TipoSimbolo.VARIABLE, linea, indice);
        scopeActual.agregar(nombre, simbolo);
    }

    // Verificar si existe una variable
    public boolean existe(String nombre) {
        return scopeActual.existe(nombre);
    }

    // Obtener símbolo
    public Simbolo obtener(String nombre) {
        return scopeActual.obtener(nombre);
    }

    // Entrar a un nuevo scope (para funciones, if, while, etc.)
    public void entrarScope() {
        scopeActual = new Scope(scopeActual);
    }

    // Salir del scope actual
    public void salirScope() {
        if (scopeActual.getPadre() != null) {
            scopeActual = scopeActual.getPadre();
        }
    }

    // Obtener tipo de una variable
    public TipoDato obtenerTipo(String nombre) {
        Simbolo simbolo = obtener(nombre);
        return simbolo != null ? simbolo.getTipoDato() : TipoDato.DESCONOCIDO;
    }

    // Obtener índice de una variable
    public int obtenerIndice(String nombre) {
        Simbolo simbolo = obtener(nombre);
        if (simbolo == null) {
            throw new RuntimeException("Variable '" + nombre + "' no declarada");
        }
        return simbolo.getIndice();
    }

    // Obtener scope actual
    public Scope getScopeActual() {
        return scopeActual;
    }
}