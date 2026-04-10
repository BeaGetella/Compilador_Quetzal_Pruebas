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
     else if (nodo instanceof NodoSi) {
        imprimirNodoSi((NodoSi) nodo, prefijo, marcador, nuevoPrefijo);
        } else if (nodo instanceof Asignacion) {
            Asignacion a = (Asignacion) nodo;
            System.out.println(prefijo + marcador + "Asignacion (" + a.getNombre() + " " + a.getOperador() + ")");
            if (a.getValor() != null) {
                imprimirExpresion(a.getValor(), prefijo + nuevoPrefijo, true);
            }
        } else if (nodo instanceof OperacionUnaria) {
            OperacionUnaria op = (OperacionUnaria) nodo;
            String pos = op.esPostfijo() ? "(postfijo)" : "(prefijo)";
            System.out.println(prefijo + marcador + "OperacionUnaria " + pos + " (" + op.getOperador() + ")");
            imprimirExpresion(op.getOperando(), prefijo + nuevoPrefijo, true);
        } else if (nodo instanceof NodoMientras) {
            imprimirNodoMientras((NodoMientras) nodo, prefijo, marcador, nuevoPrefijo);
        } else if (nodo instanceof NodoRomper) {
            System.out.println(prefijo + marcador + "NodoRomper");
        } else if (nodo instanceof NodoContinuar) {
            System.out.println(prefijo + marcador + "NodoContinuar");
        } else if (nodo instanceof NodoHacerMientras) {
            imprimirNodoHacerMientras((NodoHacerMientras) nodo, prefijo, marcador, nuevoPrefijo);
        }

    }

    private static void imprimirNodoHacerMientras(NodoHacerMientras nodo, String prefijo, String marcador, String nuevoPrefijo) {
        System.out.println(prefijo + marcador + "NodoHacerMientras");

        // Cuerpo primero
        System.out.println(prefijo + nuevoPrefijo + RAMA + "Cuerpo:");
        List<Nodo> cuerpo = nodo.getCuerpo();
        for (int i = 0; i < cuerpo.size(); i++) {
            boolean esUltimo = (i == cuerpo.size() - 1);
            imprimirNodo(cuerpo.get(i), prefijo + nuevoPrefijo + VERTICAL, esUltimo);
        }

        // Condición después
        System.out.println(prefijo + nuevoPrefijo + ULTIMA_RAMA + "Condicion:");
        imprimirExpresion(nodo.getCondicion(), prefijo + nuevoPrefijo + ESPACIO, true);
    }

    private static void imprimirNodoMientras(NodoMientras nodo, String prefijo, String marcador, String nuevoPrefijo) {
        System.out.println(prefijo + marcador + "NodoMientras");

        // Condición
        System.out.println(prefijo + nuevoPrefijo + RAMA + "Condicion:");
        imprimirExpresion(nodo.getCondicion(), prefijo + nuevoPrefijo + VERTICAL, true);

        // Cuerpo
        System.out.println(prefijo + nuevoPrefijo + ULTIMA_RAMA + "Cuerpo:");
        List<Nodo> cuerpo = nodo.getCuerpo();
        for (int i = 0; i < cuerpo.size(); i++) {
            boolean esUltimo = (i == cuerpo.size() - 1);
            imprimirNodo(cuerpo.get(i), prefijo + nuevoPrefijo + ESPACIO, esUltimo);
        }
    }

    private static void imprimirNodoSi(NodoSi nodo, String prefijo, String marcador, String nuevoPrefijo) {
        System.out.println(prefijo + marcador + "NodoSi");

        // Condición
        System.out.println(prefijo + nuevoPrefijo + RAMA + "Condicion:");
        imprimirExpresion(nodo.getCondicion(), prefijo + nuevoPrefijo + VERTICAL, true);

        // Cuerpo si
        System.out.println(prefijo + nuevoPrefijo + RAMA + "CuerpoSi:");
        List<Nodo> cuerpoSi = nodo.getCuerpoSi();
        for (int i = 0; i < cuerpoSi.size(); i++) {
            boolean esUltimo = (i == cuerpoSi.size() - 1);
            imprimirNodo(cuerpoSi.get(i), prefijo + nuevoPrefijo + VERTICAL, esUltimo);
        }

        // Sino si encadenados
        for (int i = 0; i < nodo.getCondicionesSinoSi().size(); i++) {
            System.out.println(prefijo + nuevoPrefijo + RAMA + "SinoSi[" + i + "] Condicion:");
            imprimirExpresion(nodo.getCondicionesSinoSi().get(i), prefijo + nuevoPrefijo + VERTICAL, true);
            System.out.println(prefijo + nuevoPrefijo + RAMA + "SinoSi[" + i + "] Cuerpo:");
            List<Nodo> cuerpo = nodo.getCuerposSinoSi().get(i);
            for (int j = 0; j < cuerpo.size(); j++) {
                imprimirNodo(cuerpo.get(j), prefijo + nuevoPrefijo + VERTICAL, j == cuerpo.size() - 1);
            }
        }

        // Sino final
        if (nodo.tieneSino()) {
            System.out.println(prefijo + nuevoPrefijo + ULTIMA_RAMA + "Sino:");
            List<Nodo> cuerpoSino = nodo.getCuerpoSino();
            for (int i = 0; i < cuerpoSino.size(); i++) {
                imprimirNodo(cuerpoSino.get(i), prefijo + nuevoPrefijo + ESPACIO, i == cuerpoSino.size() - 1);
            }
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

        } else if (expr instanceof OperacionUnaria) {
            OperacionUnaria op = (OperacionUnaria) expr;
            String pos = op.esPostfijo() ? "(postfijo)" : "(prefijo)";
            System.out.println(prefijo + marcador + "OperacionUnaria " + pos + " (" + op.getOperador() + ")");
            imprimirExpresion(op.getOperando(), prefijo + nuevoPrefijo, true);

        } else if (expr instanceof OperacionTernaria) {
            OperacionTernaria op = (OperacionTernaria) expr;
            System.out.println(prefijo + marcador + "OperacionTernaria (? :)");
            imprimirExpresion(op.getCondicion(),    prefijo + nuevoPrefijo, false);
            imprimirExpresion(op.getSiVerdadero(),  prefijo + nuevoPrefijo, false);
            imprimirExpresion(op.getSiFalso(),      prefijo + nuevoPrefijo, true);

        } else if (expr instanceof Asignacion) {
            Asignacion a = (Asignacion) expr;
            System.out.println(prefijo + marcador + "Asignacion (" + a.getNombre() + " " + a.getOperador() + ")");
            if (a.getValor() != null) {
                imprimirExpresion(a.getValor(), prefijo + nuevoPrefijo, true);
            }
        }

    }
}