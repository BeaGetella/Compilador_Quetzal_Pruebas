package com.example.parser.ast;

import java.util.LinkedHashMap;
import java.util.Map;

public class LiteralJsn extends Expresion {
    private Map<String, Expresion> propiedades;

    public LiteralJsn(Map<String, Expresion> propiedades) {
        this.propiedades = propiedades;
    }

    public Map<String, Expresion> getPropiedades() { return propiedades; }

    @Override
    public String toString() { return "LiteralJsn(" + propiedades.keySet() + ")"; }
}