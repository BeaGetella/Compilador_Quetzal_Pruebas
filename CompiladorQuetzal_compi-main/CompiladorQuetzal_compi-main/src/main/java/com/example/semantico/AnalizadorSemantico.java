package com.example.semantico;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalizadorSemantico {
    private TablaSimbolos tabla;
    private List<String> errores;
    private Map<String, NodoObjeto> objetos = new java.util.HashMap<>();

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
        } else if (instruccion instanceof NodoFuncion) {
            analizarFuncion((NodoFuncion) instruccion);
        } else if (instruccion instanceof NodoRetornar) {
            analizarRetornar((NodoRetornar) instruccion);
        } else if (instruccion instanceof NodoObjeto) {
            analizarObjeto((NodoObjeto) instruccion);

    } else if (instruccion instanceof NodoParaEn) {
        analizarParaEn((NodoParaEn) instruccion);
    }

    }



    private void analizarParaEn(NodoParaEn nodo) {
        // Validar el iterable
        validarExpresion(nodo.getIterable());

        tabla.entrarScope();

        // Registrar la variable del bucle
        try {
            tabla.agregarVariable(nodo.getNombreVariable(), convertirTipo(nodo.getTipoVariable()), 0);
        } catch (RuntimeException e) {
            errores.add(e.getMessage());
        }

        // Validar cuerpo
        for (Nodo instruccion : nodo.getCuerpo()) {
            analizarInstruccion(instruccion);
        }

        tabla.salirScope();
    }

    private void analizarObjeto(NodoObjeto nodo) {
        // Registrar el objeto como tipo en la tabla
        try {
            tabla.agregarVariable(nodo.getNombre(), TipoDato.DESCONOCIDO, 0);
        } catch (RuntimeException e) {
            errores.add(e.getMessage());
            return;
        }

        // Guardar el objeto para referencia futura
        objetos.put(nodo.getNombre(), nodo);

        // Entrar al scope del objeto
        tabla.entrarScope();

        // Registrar atributos
        for (NodoAtributo attr : nodo.getAtributos()) {
            try {
                tabla.agregarVariable(attr.getNombre(), convertirTipo(attr.getTipo()), 0);
            } catch (RuntimeException e) {
                errores.add(e.getMessage());
            }
        }

        // Validar constructor
        if (nodo.getConstructor() != null) {
            tabla.entrarScope();
            for (String[] param : nodo.getConstructor().getParametros()) {
                try {
                    tabla.agregarVariable(param[1], convertirTipo(param[0]), 0);
                } catch (RuntimeException e) {
                    errores.add(e.getMessage());
                }
            }
            for (Nodo instruccion : nodo.getConstructor().getCuerpo()) {
                analizarInstruccion(instruccion);
            }
            tabla.salirScope();
        }

        // Validar métodos
        for (NodoMetodo metodo : nodo.getMetodos()) {
            tabla.entrarScope();
            for (String[] param : metodo.getParametros()) {
                try {
                    tabla.agregarVariable(param[1], convertirTipo(param[0]), 0);
                } catch (RuntimeException e) {
                    errores.add(e.getMessage());
                }
            }
            for (Nodo instruccion : metodo.getCuerpo()) {
                analizarInstruccion(instruccion);
            }
            tabla.salirScope();
        }

        tabla.salirScope();
    }

    private void analizarFuncion(NodoFuncion nodo) {
        // Registrar la función en la tabla
        TipoDato tipoRetorno = convertirTipo(nodo.getTipoRetorno());
        try {
            tabla.agregarVariable(nodo.getNombre(), tipoRetorno, 0);
        } catch (RuntimeException e) {
            errores.add(e.getMessage());
            return;
        }

        // Entrar al scope de la función
        tabla.entrarScope();

        // Registrar parámetros
        for (String[] param : nodo.getParametros()) {
            TipoDato tipoParam = convertirTipo(param[0]);
            try {
                tabla.agregarVariable(param[1], tipoParam, 0);
            } catch (RuntimeException e) {
                errores.add(e.getMessage());
            }
        }

        // Validar cuerpo
        for (Nodo instruccion : nodo.getCuerpo()) {
            analizarInstruccion(instruccion);
        }

        tabla.salirScope();
    }

    private void analizarRetornar(NodoRetornar nodo) {
        if (nodo.tieneValor()) {
            validarExpresion(nodo.getValor());
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
            tabla.agregarVariableConTipo(decl.getNombre(), tipo, decl.getTipo(), 0);
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
            // ambiente.campo = valor → válido dentro de objeto, no buscar en tabla
            if (!a.getNombre().startsWith("ambiente.") && !tabla.existe(a.getNombre())) {
                errores.add("Error: Variable '" + a.getNombre() + "' no declarada");
            }
            validarExpresion(a.getValor());

        } else if (expr instanceof ExpresionNuevo) {
            ExpresionNuevo nuevo = (ExpresionNuevo) expr;
            for (Expresion arg : nuevo.getArgumentos()) {
                validarExpresion(arg);
            }
        } else if (expr instanceof ExpresionAmbiente) {
            // ambiente.campo — válido dentro de objeto
        } else if (expr instanceof LiteralLista) {
            LiteralLista lista = (LiteralLista) expr;
            for (Expresion elemento : lista.getElementos()) {
                validarExpresion(elemento);
            }
        } else if (expr instanceof AccesoLista) {
            AccesoLista acceso = (AccesoLista) expr;
            validarExpresion(acceso.getLista());
            validarExpresion(acceso.getIndice());
        }

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
        String tipoBase = tipo.contains("<") ? tipo.substring(0, tipo.indexOf("<")) : tipo;
        switch (tipoBase.toLowerCase()) {
            case "entero":  return TipoDato.ENTERO;
            case "numero":  return TipoDato.NUMERO;
            case "texto":   return TipoDato.TEXTO;
            case "log":     return TipoDato.LOG;
            case "lista":   return TipoDato.LISTA;
            default:        return TipoDato.OBJETO;
        }
    }

    public TablaSimbolos getTabla() {
        return tabla;
    }

    public List<String> getErrores() {
        return errores;
    }
}