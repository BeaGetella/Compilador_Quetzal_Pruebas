package com.example.parser;

import com.example.parser.ast.*;
import java.util.List;

public class VisualizadorAST {

    private static final String RAMA = "├── ";
    private static final String ULTIMA_RAMA = "└── ";
    private static final String VERTICAL = "│   ";
    private static final String ESPACIO = "    ";

    public static void imprimir(Programa programa) {
        System.out.println("\nARBOL SINTACTICO (AST):");
        System.out.println("─────────────────────────────");

        List<Nodo> instrucciones = programa.getInstrucciones();
        for (int i = 0; i < instrucciones.size(); i++) {
            boolean esUltimo = (i == instrucciones.size() - 1);
            imprimirNodo(instrucciones.get(i), "", esUltimo);
        }
    }

    private static void imprimirNodo(Nodo nodo, String prefijo, boolean esUltimo) {
        String marcador = esUltimo ? ULTIMA_RAMA : RAMA;
        String nuevoPrefijo = esUltimo ? ESPACIO : VERTICAL;

        if (nodo instanceof DeclaracionVariable) {
            imprimirDeclaracionVariable((DeclaracionVariable) nodo, prefijo, marcador, nuevoPrefijo);
        } else if (nodo instanceof LlamadaFuncion) {
            imprimirLlamadaFuncion((LlamadaFuncion) nodo, prefijo, marcador, nuevoPrefijo);
        }
    }

    private static void imprimirDeclaracionVariable(DeclaracionVariable decl, String prefijo, String marcador, String nuevoPrefijo) {
        System.out.println(prefijo + marcador + "DeclaracionVariable (" + decl.getTipo() + " " + decl.getNombre() + ")");
        imprimirExpresion(decl.getValor(), prefijo + nuevoPrefijo, true);
    }

    private static void imprimirLlamadaFuncion(LlamadaFuncion llamada, String prefijo, String marcador, String nuevoPrefijo) {
        System.out.println(prefijo + marcador + "LlamadaFuncion (consola." + llamada.getMetodo() + ")");

        List<Expresion> argumentos = llamada.getArgumentos();
        for (int i = 0; i < argumentos.size(); i++) {
            boolean esUltimo = (i == argumentos.size() - 1);
            imprimirExpresion(argumentos.get(i), prefijo + nuevoPrefijo, esUltimo);
        }
    }

    private static void imprimirExpresion(Expresion expr, String prefijo, boolean esUltimo) {
        String marcador = esUltimo ? ULTIMA_RAMA : RAMA;
        String nuevoPrefijo = esUltimo ? ESPACIO : VERTICAL;

        if (expr instanceof LiteralNumero) {
            LiteralNumero literal = (LiteralNumero) expr;
            System.out.println(prefijo + marcador + "LiteralNumero(" + literal.getValor() + ")");

        } else if (expr instanceof LiteralString) {
            LiteralString literal = (LiteralString) expr;
            System.out.println(prefijo + marcador + "LiteralString(\"" + literal.getValor() + "\")");

        } else if (expr instanceof Variable) {
            Variable variable = (Variable) expr;
            System.out.println(prefijo + marcador + "Variable(" + variable.getNombre() + ")");

        } else if (expr instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) expr;
            System.out.println(prefijo + marcador + "OperacionBinaria (" + op.getOperador() + ")");
            imprimirExpresion(op.getIzquierda(), prefijo + nuevoPrefijo, false);
            imprimirExpresion(op.getDerecha(), prefijo + nuevoPrefijo, true);

        } else if (expr instanceof Concatenacion) {
            Concatenacion concat = (Concatenacion) expr;
            System.out.println(prefijo + marcador + "Concatenacion (+)");
            imprimirExpresion(concat.getIzquierda(), prefijo + nuevoPrefijo, false);
            imprimirExpresion(concat.getDerecha(), prefijo + nuevoPrefijo, true);

        } else if (expr instanceof ConversionTexto) {
            ConversionTexto conv = (ConversionTexto) expr;
            System.out.println(prefijo + marcador + "ConversionTexto (.texto())");
            imprimirExpresion(conv.getExpresion(), prefijo + nuevoPrefijo, true);

        } else if (expr instanceof ConversionNumero) {
            ConversionNumero conv = (ConversionNumero) expr;
            System.out.println(prefijo + marcador + "ConversionNumero (.numero())");
            imprimirExpresion(conv.getExpresion(), prefijo + nuevoPrefijo, true);

        } else if (expr instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            System.out.println(prefijo + marcador + "LlamadaFuncion (consola." + llamada.getMetodo() + ")");
            List<Expresion> argumentos = llamada.getArgumentos();
            for (int i = 0; i < argumentos.size(); i++) {
                boolean esUltimoArg = (i == argumentos.size() - 1);
                imprimirExpresion(argumentos.get(i), prefijo + nuevoPrefijo, esUltimoArg);
            }
        }
    }
}