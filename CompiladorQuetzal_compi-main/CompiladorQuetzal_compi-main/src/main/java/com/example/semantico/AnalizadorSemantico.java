package com.example.semantico;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;

import java.util.ArrayList;
import java.util.List;

public class AnalizadorSemantico {
    private TablaSimbolos tabla;
    private List<String> errores;

    public AnalizadorSemantico() {
        this.tabla = new TablaSimbolos();
        this.errores = new ArrayList<>();
    }

    public TablaSimbolos analizar(Programa programa) {
        for (Nodo instruccion : programa.getInstrucciones()) {
            analizarInstruccion(instruccion);
        }

        if (!errores.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String error : errores) {
                sb.append(error).append("\n");
            }
            throw new RuntimeException("Errores semánticos:\n" + sb.toString());
        }

        return tabla;
    }

    private void analizarInstruccion(Nodo instruccion) {
        if (instruccion instanceof DeclaracionVariable) {
            analizarDeclaracionVariable((DeclaracionVariable) instruccion);
        } else if (instruccion instanceof LlamadaFuncion) {
            analizarLlamadaFuncion((LlamadaFuncion) instruccion);
        } else if (instruccion instanceof BuclePara) {
            analizarBuclePara((BuclePara) instruccion);
        } else if (instruccion instanceof NodoSi) {
            analizarSi((NodoSi) instruccion);
        } else if (instruccion instanceof NodoMientras) {
            analizarMientras((NodoMientras) instruccion);
        } else if (instruccion instanceof NodoRomper) {
            // por ahora solo lo reconocemos
        } else if (instruccion instanceof NodoContinuar) {
            // por ahora solo lo reconocemos
        } else if (instruccion instanceof NodoHacerMientras) {
            analizarHacerMientras((NodoHacerMientras) instruccion);
        } else if (instruccion instanceof Expresion) {
            // Asignaciones sueltas (i++, i = i + 1, etc.)
            validarExpresion((Expresion) instruccion);
        }
    }

    private void analizarHacerMientras(NodoHacerMientras nodo) {
        for (Nodo instruccion : nodo.getCuerpo()) {
            analizarInstruccion(instruccion);
        }
        validarExpresion(nodo.getCondicion());
    }

    private void analizarMientras(NodoMientras nodo) {
        validarExpresion(nodo.getCondicion());

        for (Nodo instruccion : nodo.getCuerpo()) {
            analizarInstruccion(instruccion);
        }
    }

    private void analizarSi(NodoSi nodo) {
        // Validar la condición
        validarExpresion(nodo.getCondicion());

        // Validar cuerpo del si
        for (Nodo instruccion : nodo.getCuerpoSi()) {
            analizarInstruccion(instruccion);
        }

        // Validar cada sino si
        for (int i = 0; i < nodo.getCondicionesSinoSi().size(); i++) {
            validarExpresion(nodo.getCondicionesSinoSi().get(i));
            for (Nodo instruccion : nodo.getCuerposSinoSi().get(i)) {
                analizarInstruccion(instruccion);
            }
        }

        // Validar cuerpo del sino si existe
        if (nodo.tieneSino()) {
            for (Nodo instruccion : nodo.getCuerpoSino()) {
                analizarInstruccion(instruccion);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ANÁLISIS DEL BUCLE PARA
    // ════════════════════════════════════════════════════════════════
    private void analizarBuclePara(BuclePara bucle) {
        tabla.entrarScope();

        // 1. Inicialización: declara la variable del bucle
        analizarDeclaracionVariable(bucle.getInicializacion());

        // 2. Condición: debe poder evaluarse
        validarExpresion(bucle.getCondicion());

        // 3. Incremento: i++, i--, i = i + 1, i += 1 ...
        validarExpresion(bucle.getIncremento());

        // 4. Cuerpo: cada instrucción dentro del bloque
        for (Nodo instruccion : bucle.getCuerpo()) {
            analizarInstruccion(instruccion);
        }

        tabla.salirScope();
    }

    private void analizarDeclaracionVariable(DeclaracionVariable decl) {
        TipoDato tipo = convertirTipo(decl.getTipo());

        try {
            tabla.agregarVariable(decl.getNombre(), tipo, 0);
        } catch (RuntimeException e) {
            errores.add(e.getMessage());
            return;
        }

        validarExpresion(decl.getValor());
    }

    private void analizarLlamadaFuncion(LlamadaFuncion llamada) {
        for (Expresion arg : llamada.getArgumentos()) {
            validarExpresion(arg);
        }
    }

    private void validarExpresion(Expresion expr) {
        if (expr instanceof Variable) {
            validarVariable((Variable) expr);
        } else if (expr instanceof OperacionBinaria) {
            validarOperacionBinaria((OperacionBinaria) expr);
        } else if (expr instanceof Concatenacion) {
            validarConcatenacion((Concatenacion) expr);
        } else if (expr instanceof ConversionTexto) {
            validarExpresion(((ConversionTexto) expr).getExpresion());
        } else if (expr instanceof ConversionNumero) {
            validarExpresion(((ConversionNumero) expr).getExpresion());
        } else if (expr instanceof LlamadaFuncion) {
            analizarLlamadaFuncion((LlamadaFuncion) expr);
        } else if (expr instanceof OperacionUnaria) {
            OperacionUnaria u = (OperacionUnaria) expr;
            validarExpresion(u.getOperando());
        } else if (expr instanceof OperacionTernaria) {
            OperacionTernaria t = (OperacionTernaria) expr;
            validarExpresion(t.getCondicion());
            validarExpresion(t.getSiVerdadero());
            validarExpresion(t.getSiFalso());
        } else if (expr instanceof Asignacion) {
            Asignacion a = (Asignacion) expr;
            if (!tabla.existe(a.getNombre())) {
                errores.add("Error: Variable '" + a.getNombre() + "' no declarada");
            }
            validarExpresion(a.getValor());
        }
        // LiteralNumero y LiteralString no necesitan validación
    }

    private void validarVariable(Variable variable) {
        String nombre = variable.getNombre();
        if (!tabla.existe(nombre)) {
            errores.add("Error: Variable '" + nombre + "' no declarada");
        }
    }

    private void validarOperacionBinaria(OperacionBinaria op) {
        validarExpresion(op.getIzquierda());
        validarExpresion(op.getDerecha());
    }

    private void validarConcatenacion(Concatenacion concat) {
        validarExpresion(concat.getIzquierda());
        validarExpresion(concat.getDerecha());
    }

    private TipoDato convertirTipo(String tipo) {
        switch (tipo.toLowerCase()) {
            case "entero":  return TipoDato.ENTERO;
            case "numero":  return TipoDato.NUMERO;
            case "texto":   return TipoDato.TEXTO;
            default:        return TipoDato.DESCONOCIDO;
        }
    }

    public TablaSimbolos getTabla() {
        return tabla;
    }

    public List<String> getErrores() {
        return errores;
    }
}