package com.example.semantico.modelos;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    private Scope padre;  // Scope padre (para jerarquía)
    private Map<String, Simbolo> simbolos;

    public Scope(Scope padre) {
        this.padre = padre;
        this.simbolos = new HashMap<>();
    }

    // Agregar símbolo al scope actual
    public void agregar(String nombre, Simbolo simbolo) {
        simbolos.put(nombre, simbolo);
    }

    // Verificar si existe en el scope actual
    public boolean existeLocal(String nombre) {
        return simbolos.containsKey(nombre);
    }

    // Verificar si existe en el scope actual o en padres
    public boolean existe(String nombre) {
        if (simbolos.containsKey(nombre)) {
            return true;
        }
        if (padre != null) {
            return padre.existe(nombre);
        }
        return false;
    }

    // Obtener símbolo del scope actual o de padres
    public Simbolo obtener(String nombre) {
        if (simbolos.containsKey(nombre)) {
            return simbolos.get(nombre);
        }
        if (padre != null) {
            return padre.obtener(nombre);
        }
        return null;
    }

    // Obtener scope padre
    public Scope getPadre() {
        return padre;
    }

    // Obtener todos los símbolos del scope actual
    public Map<String, Simbolo> getSimbolos() {
        return simbolos;
    }
}