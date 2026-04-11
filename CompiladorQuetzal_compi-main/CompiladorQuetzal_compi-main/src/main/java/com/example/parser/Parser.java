package com.example.parser;

import com.example.lexer.Lexer;
import com.example.lexer.Token;
import com.example.lexer.TipoToken;
import com.example.parser.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Parser {
    private List<Token> tokens;
    private int posicion;
    private Token tokenActual;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.posicion = 0;
        this.tokenActual = tokens.size() > 0 ? tokens.get(0) : null;
    }

    // Avanzar al siguiente token
    private void avanzar() {
        posicion++;
        if (posicion < tokens.size()) {
            tokenActual = tokens.get(posicion);
        }
    }

    // Verificar si el token actual es del tipo esperado
    private boolean verificar(TipoToken tipo) {
        if (tokenActual == null) return false;
        return tokenActual.getTipo() == tipo;
    }

    // Consumir un token esperado (si no coincide, lanza error)
    private Token consumir(TipoToken tipo, String mensajeError) {
        if (verificar(tipo)) {
            Token token = tokenActual;
            avanzar();
            return token;
        }
        throw new RuntimeException(mensajeError + " en línea " + tokenActual.getLinea());
    }

    // Saltar tokens de nueva línea
    private void saltarNuevasLineas() {
        while (verificar(TipoToken.NUEVA_LINEA)) {
            avanzar();
        }


    }

    // Parsear el programa completo
    public Programa parsear() {
        List<Nodo> instrucciones = new ArrayList<>();

        saltarNuevasLineas();

        while (!verificar(TipoToken.EOF)) {
            instrucciones.add(parsearInstruccion());
            saltarNuevasLineas();
        }

        return new Programa(instrucciones);
    }

    private Nodo parsearInstruccion() {

        // 1. Declaración de función: vacio/entero/numero/texto + IDENTIFICADOR + (
        if ((verificar(TipoToken.TIPO_VACIO)  ||
                verificar(TipoToken.TIPO_ENTERO) ||
                verificar(TipoToken.TIPO_NUMERO) ||
                verificar(TipoToken.TIPO_TEXTO)  ||
                verificar(TipoToken.TIPO_LOG)) && esFuncion()) {
            String tipoRetorno = tokenActual.getValor();
            avanzar();
            return parsearFuncion(tipoRetorno);
        }

        // 2. Declaración de variable con tipo primitivo
        if (verificar(TipoToken.TIPO_ENTERO) ||
                verificar(TipoToken.TIPO_NUMERO) ||
                verificar(TipoToken.TIPO_TEXTO)  ||
                verificar(TipoToken.TIPO_LOG)    ||
                verificar(TipoToken.TIPO_LISTA)  ||
                verificar(TipoToken.TIPO_JSN)    ||
                verificar(TipoToken.TIPO_VACIO)) {
            return parsearDeclaracionVariable();
        }

        // 3. Declaración de variable de tipo objeto: Usuario persona = nuevo Usuario(...)
        if (verificar(TipoToken.IDENTIFICADOR) && esDeclaracionObjeto()) {
            return parsearDeclaracionVariableObjeto();
        }

        // 4. Expresión suelta: llamada a función, asignación, incremento, etc.
        if (verificar(TipoToken.IDENTIFICADOR)) {
            Expresion expr = parsearExpresion();
            return new InstruccionExpresion(expr);
        }

        // 5. Definición de objeto
        if (verificar(TipoToken.OBJETO)) {
            return parsearObjeto();
        }

        // 6. retornar
        if (verificar(TipoToken.RETORNAR)) {
            return parsearRetornar();
        }

        // 7. Llamada a consola
        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
        }

        // 8. Bucles y control de flujo
        if (verificar(TipoToken.PARA)) {
            return parsearPara();
        }

        if (verificar(TipoToken.SI)) {
            return parsearSi();
        }

        if (verificar(TipoToken.MIENTRAS)) {
            return parsearMientras();
        }

        if (verificar(TipoToken.ROMPER)) {
            return parsearRomper();
        }

        if (verificar(TipoToken.CONTINUAR)) {
            return parsearContinuar();
        }

        if (verificar(TipoToken.HACER)) {
            return parsearHacerMientras();
        }

        // 9. ambiente.campo = valor
        if (verificar(TipoToken.AMBIENTE)) {
            Expresion expr = parsearExpresion();
            return new InstruccionExpresion(expr);
        }

        // 10. Palabras reservadas aún sin implementación
        if (verificar(TipoToken.NUEVO)     ||
                verificar(TipoToken.INTENTAR)  ||
                verificar(TipoToken.IMPORTAR)  ||
                verificar(TipoToken.EXPORTAR)  ||
                verificar(TipoToken.PUBLICO)   ||
                verificar(TipoToken.PRIVADO)   ||
                verificar(TipoToken.LIBRE)     ||
                verificar(TipoToken.ASINCRONO)) {
            throw new RuntimeException(
                    "'" + tokenActual.getValor() + "' aún no está implementado " +
                            "(línea " + tokenActual.getLinea() + ")"
            );
        }

        throw new RuntimeException("Instrucción no reconocida: '" +
                tokenActual.getValor() + "' en línea " + tokenActual.getLinea());
    }

    private boolean esDeclaracionObjeto() {
        int pos = posicion + 1;
        while (pos < tokens.size() && tokens.get(pos).getTipo() == TipoToken.NUEVA_LINEA) {
            pos++;
        }
        if (pos < tokens.size() && tokens.get(pos).getTipo() == TipoToken.IDENTIFICADOR) {
            int pos2 = pos + 1;
            while (pos2 < tokens.size() && tokens.get(pos2).getTipo() == TipoToken.NUEVA_LINEA) {
                pos2++;
            }
            if (pos2 < tokens.size() && tokens.get(pos2).getTipo() == TipoToken.IGUAL) {
                return true;
            }
        }
        return false;
    }

    private DeclaracionVariable parsearDeclaracionVariableObjeto() {
        String tipo = tokenActual.getValor();
        avanzar();
        String nombre = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de variable").getValor();
        consumir(TipoToken.IGUAL, "Se esperaba '='");
        Expresion valor = parsearExpresion();
        return new DeclaracionVariable(tipo, nombre, valor);
    }

    private boolean esFuncion() {
        // Saltar posibles NUEVA_LINEA entre el tipo y el nombre
        int pos = posicion + 1;
        while (pos < tokens.size() && tokens.get(pos).getTipo() == TipoToken.NUEVA_LINEA) {
            pos++;
        }
        if (pos < tokens.size() && tokens.get(pos).getTipo() == TipoToken.IDENTIFICADOR) {
            int pos2 = pos + 1;
            // También saltar NUEVA_LINEA entre nombre y (
            while (pos2 < tokens.size() && tokens.get(pos2).getTipo() == TipoToken.NUEVA_LINEA) {
                pos2++;
            }
            if (pos2 < tokens.size() && tokens.get(pos2).getTipo() == TipoToken.PARENTESIS_IZQ) {
                return true;
            }
        }
        return false;
    }



    // ════════════════════════════════════════════════════════════════
    //  BUCLE PARA
    //  Sintaxis: para (entero var i = 0; i < 5; i++) { ... }
    // ════════════════════════════════════════════════════════════════

    private Nodo parsearPara() {
        consumir(TipoToken.PARA, "Se esperaba 'para'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        // Detectar si es para-en: tipo [var] nombre EN|CADA iterable
        if (esBucleParaEn()) {
            return parsearParaEnCuerpo();
        }

        // Si no, es para clásico — delegar al método existente sin consumir PARA y (
        return parsearParaClasicoCuerpo();
    }

    private boolean esBucleParaEn() {
        int pos = posicion;
        // saltar tipo
        if (pos >= tokens.size()) return false;
        pos++;
        // saltar var opcional
        if (pos < tokens.size() && tokens.get(pos).getTipo() == TipoToken.VAR) pos++;
        // saltar nombre
        if (pos >= tokens.size()) return false;
        pos++;
        // verificar EN o CADA
        if (pos < tokens.size()) {
            TipoToken tipo = tokens.get(pos).getTipo();
            return tipo == TipoToken.EN || tipo == TipoToken.CADA;
        }
        return false;
    }

    private NodoParaEn parsearParaEnCuerpo() {
        // tipo
        String tipo = tokenActual.getValor();
        avanzar();
        // var opcional
        if (verificar(TipoToken.VAR)) avanzar();
        // nombre
        String nombre = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de variable").getValor();
        // en o cada
        if (verificar(TipoToken.EN) || verificar(TipoToken.CADA)) avanzar();
        // iterable
        Expresion iterable = parsearExpresion();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();
        List<Nodo> cuerpo = parsearBloque();
        return new NodoParaEn(tipo, nombre, iterable, cuerpo);
    }

    private BuclePara parsearParaClasicoCuerpo() {
        // ya consumimos PARA y ( en parsearPara()
        // aquí va directo a la inicialización
        DeclaracionVariable inicializacion = parsearDeclaracionVariableInterna();
        saltarNuevasLineas();
        consumir(TipoToken.PUNTO_COMA, "Se esperaba ';'");
        saltarNuevasLineas();
        Expresion condicion = parsearExpresion();
        saltarNuevasLineas();
        consumir(TipoToken.PUNTO_COMA, "Se esperaba ';'");
        saltarNuevasLineas();
        Expresion incremento = parsearExpresion();
        saltarNuevasLineas();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        List<Nodo> cuerpo = parsearBloque();
        return new BuclePara(inicializacion, condicion, incremento, cuerpo);
    }

    private BuclePara parsearBuclePara() {
        consumir(TipoToken.PARA, "Se esperaba 'para'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '(' después de 'para'");

        // ── 1. Inicialización: entero var i = 0 ─────────────────────────────
        DeclaracionVariable inicializacion = parsearDeclaracionVariableInterna();

        saltarNuevasLineas();
        consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' después de la inicialización del para");

        // ── 2. Condición: i < 5 ─────────────────────────────────────────────
        saltarNuevasLineas();
        Expresion condicion = parsearExpresion();

        saltarNuevasLineas();
        consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' después de la condición del para");

        // ── 3. Incremento: i++ o i = i + 1 ──────────────────────────────────
        saltarNuevasLineas();
        Expresion incremento = parsearExpresion();

        saltarNuevasLineas();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')' después del incremento del para");

        // ── 4. Cuerpo: { instrucciones } ────────────────────────────────────
        List<Nodo> cuerpo = parsearBloque();

        return new BuclePara(inicializacion, condicion, incremento, cuerpo);
    }

    /**
     * Versión interna de parsearDeclaracionVariable que:
     * - Acepta el modificador opcional "var" entre el tipo y el nombre
     * - NO consume punto y coma al final (lo hace el bucle para)
     */
    private DeclaracionVariable parsearDeclaracionVariableInterna() {
        Token tipoToken;

        if (verificar(TipoToken.TIPO_ENTERO)) {
            tipoToken = consumir(TipoToken.TIPO_ENTERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_NUMERO)) {
            tipoToken = consumir(TipoToken.TIPO_NUMERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_TEXTO)) {
            tipoToken = consumir(TipoToken.TIPO_TEXTO, "Se esperaba tipo de dato");
        } else {
            throw new RuntimeException("Se esperaba un tipo de dato en la inicialización del 'para', línea " + tokenActual.getLinea());
        }

        String tipo = tipoToken.getValor();

        // Consumir el modificador "var" si está presente (entero var i = 0)
        if (verificar(TipoToken.VAR)) {
            avanzar();
        }

        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de variable en 'para'");
        String nombre = nombreToken.getValor();

        consumir(TipoToken.IGUAL, "Se esperaba '=' en inicialización del 'para'");

        Expresion valor = parsearExpresion();

        return new DeclaracionVariable(tipo, nombre, valor);
    }

    /**
     * Parsea un bloque de instrucciones delimitado por { }
     */
    private List<Nodo> parsearBloque() {
        saltarNuevasLineas();
        consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{'");
        saltarNuevasLineas();

        List<Nodo> instrucciones = new ArrayList<>();

        while (!verificar(TipoToken.LLAVE_DER) && !verificar(TipoToken.EOF)) {
            instrucciones.add(parsearInstruccion());
            saltarNuevasLineas();
        }

        consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
        return instrucciones;
    }

    // ════════════════════════════════════════════════════════════════
    //  Resto del parser
    // ════════════════════════════════════════════════════════════════

    private DeclaracionVariable parsearDeclaracionVariable() {
        Token tipoToken;

        if (verificar(TipoToken.TIPO_ENTERO)) {
            tipoToken = consumir(TipoToken.TIPO_ENTERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_NUMERO)) {
            tipoToken = consumir(TipoToken.TIPO_NUMERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_TEXTO)) {
            tipoToken = consumir(TipoToken.TIPO_TEXTO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_LOG)) {
            tipoToken = consumir(TipoToken.TIPO_LOG, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_LISTA)) {
            tipoToken = consumir(TipoToken.TIPO_LISTA, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_JSN)) {
            tipoToken = consumir(TipoToken.TIPO_JSN, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_VACIO)) {
            tipoToken = consumir(TipoToken.TIPO_VACIO, "Se esperaba tipo de dato");
        } else {
            throw new RuntimeException("Se esperaba un tipo de dato en línea " + tokenActual.getLinea());
        }

        String tipo = tipoToken.getValor();

        // Manejar tipo genérico: lista<entero>, lista<texto>, etc.  ← AQUÍ, antes del var y nombre
        if (tipo.equals("lista") && verificar(TipoToken.MENOR)) {
            avanzar(); // consumir
            String tipoInterno = tokenActual.getValor();
            avanzar(); // consumir el tipo interno
            consumir(TipoToken.MAYOR, "Se esperaba '>'");
            tipo = "lista<" + tipoInterno + ">";
        }

        // Consumir 'var' opcional
        boolean esMutable = false;
        if (verificar(TipoToken.VAR)) {
            esMutable = true;
            avanzar();
        }

        // Consumir nombre
        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba un nombre de variable");
        String nombre = nombreToken.getValor();

        // Consumir '='
        consumir(TipoToken.IGUAL, "Se esperaba '='");

        // Parsear valor
        Expresion valor = parsearExpresion();

        return new DeclaracionVariable(tipo, nombre, valor);
    }
    private LlamadaFuncion parsearLlamadaConsola() {
        consumir(TipoToken.CONSOLA, "Se esperaba 'consola'");
        consumir(TipoToken.PUNTO, "Se esperaba '.'");

        Token metodoToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre del método");
        String metodo = metodoToken.getValor();

        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        List<Expresion> argumentos = new ArrayList<>();

        if (!verificar(TipoToken.PARENTESIS_DER)) {
            argumentos.add(parsearExpresionString());
        }

        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");

        return new LlamadaFuncion("consola", metodo, argumentos);
    }

    private Expresion parsearExpresionString() {
        Expresion izquierda = parsearElementoString();

        while (verificar(TipoToken.MAS)) {
            avanzar();
            Expresion derecha = parsearElementoString();
            izquierda = new Concatenacion(izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearElementoString() {
        if (verificar(TipoToken.LITERAL_STRING)) {
            String valor = tokenActual.getValor();
            avanzar();
            return new LiteralString(valor);
        }

        if (verificar(TipoToken.STRING_INTERPOLADO)) {
            String template = tokenActual.getValor();
            avanzar();
            return parsearInterpolacion(template);
        }

        if (verificar(TipoToken.IDENTIFICADOR)) {
            String nombre = tokenActual.getValor();
            avanzar();

            if (verificar(TipoToken.PUNTO)) {
                avanzar();

                String nombreMetodo = null;
                if (verificar(TipoToken.IDENTIFICADOR)) {
                    nombreMetodo = tokenActual.getValor();
                    avanzar();
                } else if (verificar(TipoToken.TIPO_TEXTO)) {
                    nombreMetodo = "texto";
                    avanzar();
                } else if (verificar(TipoToken.TIPO_NUMERO)) {
                    nombreMetodo = "numero";
                    avanzar();
                } else {
                    throw new RuntimeException("Se esperaba nombre de método en línea " + tokenActual.getLinea());
                }

                if (nombreMetodo.equals("texto")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionTexto(new Variable(nombre));
                } else if (nombreMetodo.equals("numero")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionNumero(new Variable(nombre));
                } else if (verificar(TipoToken.PARENTESIS_IZQ)) {
                    List<Expresion> args = new ArrayList<>();
                    avanzar();
                    while (!verificar(TipoToken.PARENTESIS_DER)) {
                        args.add(parsearExpresion());
                        if (verificar(TipoToken.COMA)) avanzar();
                    }
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new LlamadaFuncion(nombre, nombreMetodo, args);
                } else {
                    // Acceso JSN con posible encadenado: formulario.metadatos.version
                    Expresion resultado = new AccesoJsn(new Variable(nombre), nombreMetodo);
                    while (verificar(TipoToken.PUNTO)) {
                        avanzar();
                        String siguiente;
                        if (verificar(TipoToken.IDENTIFICADOR)) {
                            siguiente = tokenActual.getValor();
                            avanzar();
                        } else {
                            throw new RuntimeException("Se esperaba propiedad en línea " + tokenActual.getLinea());
                        }
                        if (verificar(TipoToken.PARENTESIS_IZQ)) {
                            List<Expresion> args = new ArrayList<>();
                            avanzar();
                            while (!verificar(TipoToken.PARENTESIS_DER)) {
                                args.add(parsearExpresion());
                                if (verificar(TipoToken.COMA)) avanzar();
                            }
                            consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                            resultado = new LlamadaFuncion(nombre, siguiente, args);
                        } else {
                            resultado = new AccesoJsn(resultado, siguiente);
                        }
                    }
                    return resultado;
                }
            }

            if (verificar(TipoToken.PARENTESIS_IZQ)) {
                avanzar();
                List<Expresion> args = new ArrayList<>();
                while (!verificar(TipoToken.PARENTESIS_DER)) {
                    args.add(parsearExpresion());
                    if (verificar(TipoToken.COMA)) avanzar();
                }
                consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                return new LlamadaFuncion("", nombre, args);
            }

            return new Variable(nombre);
        }

        if (verificar(TipoToken.LITERAL_NUMERO)) {
            int valor = Integer.parseInt(tokenActual.getValor());
            avanzar();
            return new LiteralNumero(valor);
        }

        throw new RuntimeException("Expresión de string no válida en línea " + tokenActual.getLinea());
    }

    private Expresion parsearExpresion() {
        return parsearAsignacion();
    }

    private Expresion parsearAsignacion() {
        Expresion izquierda = parsearTernario();

        if (verificar(TipoToken.IGUAL)       ||
                verificar(TipoToken.MAS_IGUAL)   ||
                verificar(TipoToken.MENOS_IGUAL) ||
                verificar(TipoToken.MULT_IGUAL)  ||
                verificar(TipoToken.DIV_IGUAL)   ||
                verificar(TipoToken.MOD_IGUAL)) {

            if (!(izquierda instanceof Variable)) {
                throw new RuntimeException("Solo puedes asignar a una variable, línea " + tokenActual.getLinea());
            }

            String operador = tokenActual.getValor();
            avanzar();
            Expresion valor = parsearTernario();
            return new Asignacion(((Variable) izquierda).getNombre(), operador, valor);
        }

        return izquierda;
    }

    private Expresion parsearTernario() {
        Expresion condicion = parsearOr();

        if (verificar(TipoToken.INTERROGACION)) {
            avanzar();
            Expresion siVerdadero = parsearExpresion();
            consumir(TipoToken.DOS_PUNTOS, "Se esperaba ':' en operador ternario");
            Expresion siFalso = parsearExpresion();
            return new OperacionTernaria(condicion, siVerdadero, siFalso);
        }

        return condicion;
    }

    private Expresion parsearOr() {
        Expresion izquierda = parsearAnd();

        while (verificar(TipoToken.O)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearAnd();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }
        return izquierda;
    }

    private Expresion parsearAnd() {
        Expresion izquierda = parsearIgualdad();

        while (verificar(TipoToken.Y)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearIgualdad();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearIgualdad() {
        Expresion izquierda = parsearRelacional();

        while (verificar(TipoToken.IGUAL_IGUAL) || verificar(TipoToken.DIFERENTE)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearRelacional();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearRelacional() {
        Expresion izquierda = parsearExpresionAditiva();

        while (verificar(TipoToken.MAYOR)       ||
                verificar(TipoToken.MENOR)       ||
                verificar(TipoToken.MAYOR_IGUAL) ||
                verificar(TipoToken.MENOR_IGUAL)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearExpresionAditiva();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearExpresionAditiva() {
        Expresion izquierda = parsearExpresionMultiplicativa();

        while (verificar(TipoToken.MAS) || verificar(TipoToken.MENOS)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearExpresionMultiplicativa();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearExpresionMultiplicativa() {
        Expresion izquierda = parsearUnaria();

        while (verificar(TipoToken.MULTIPLICACION) ||
                verificar(TipoToken.DIVISION)       ||
                verificar(TipoToken.MODULO)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearUnaria();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearUnaria() {
        if (verificar(TipoToken.NO)) {
            String operador = tokenActual.getValor();
            avanzar();
            return new OperacionUnaria(operador, parsearUnaria(), false);
        }

        if (verificar(TipoToken.MENOS)) {
            avanzar();
            return new OperacionUnaria("-", parsearUnaria(), false);
        }

        if (verificar(TipoToken.INCREMENTO)) {
            avanzar();
            return new OperacionUnaria("++", parsearUnaria(), false);
        }

        if (verificar(TipoToken.DECREMENTO)) {
            avanzar();
            return new OperacionUnaria("--", parsearUnaria(), false);
        }

        Expresion expr = parsearExpresionPrimaria();

        if (verificar(TipoToken.INCREMENTO)) {
            avanzar();
            return new OperacionUnaria("++", expr, true);
        }

        if (verificar(TipoToken.DECREMENTO)) {
            avanzar();
            return new OperacionUnaria("--", expr, true);
        }

        return expr;
    }

    private Expresion parsearExpresionPrimaria() {

        if (verificar(TipoToken.NUEVO)) {
            avanzar();
            String tipoObjeto = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre del objeto").getValor();
            consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
            List<Expresion> argumentos = new ArrayList<>();
            while (!verificar(TipoToken.PARENTESIS_DER)) {
                argumentos.add(parsearExpresion());
                if (verificar(TipoToken.COMA)) avanzar();
            }
            consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
            return new ExpresionNuevo(tipoObjeto, argumentos);
        }

        // Lista literal: [1, 2, 3]
        if (verificar(TipoToken.CORCHETE_IZQ)) {
            avanzar();
            List<Expresion> elementos = new ArrayList<>();
            saltarNuevasLineas();
            while (!verificar(TipoToken.CORCHETE_DER) && !verificar(TipoToken.EOF)) {
                elementos.add(parsearExpresion());
                saltarNuevasLineas();
                if (verificar(TipoToken.COMA)) {
                    avanzar();
                    saltarNuevasLineas();
                }
            }
            consumir(TipoToken.CORCHETE_DER, "Se esperaba ']'");
            return new LiteralLista(elementos, null);
        }

        // JSN literal: { clave: valor, ... }
        if (verificar(TipoToken.LLAVE_IZQ)) {
            avanzar();
            Map<String, Expresion> propiedades = new java.util.LinkedHashMap<>();
            saltarNuevasLineas();
            while (!verificar(TipoToken.LLAVE_DER) && !verificar(TipoToken.EOF)) {
                String clave;
                if (verificar(TipoToken.LITERAL_STRING)) {
                    clave = tokenActual.getValor();
                    avanzar();
                } else {
                    clave = consumir(TipoToken.IDENTIFICADOR, "Se esperaba clave del JSN").getValor();
                }
                consumir(TipoToken.DOS_PUNTOS, "Se esperaba ':'");
                saltarNuevasLineas();
                Expresion valor = parsearExpresion();
                propiedades.put(clave, valor);
                saltarNuevasLineas();
                if (verificar(TipoToken.COMA)) {
                    avanzar();
                    saltarNuevasLineas();
                }
            }
            consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
            return new LiteralJsn(propiedades);
        }

        if (verificar(TipoToken.AMBIENTE)) {
            avanzar();
            consumir(TipoToken.PUNTO, "Se esperaba '.'");
            String campo = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre del campo").getValor();
            if (verificar(TipoToken.IGUAL)) {
                avanzar();
                Expresion valor = parsearExpresion();
                return new Asignacion("ambiente." + campo, "=", valor);
            }
            return new ExpresionAmbiente(campo);
        }

        if (verificar(TipoToken.LITERAL_STRING)) {
            String valor = tokenActual.getValor();
            avanzar();
            return new LiteralString(valor);
        }

        if (verificar(TipoToken.STRING_INTERPOLADO)) {
            String template = tokenActual.getValor();
            avanzar();
            return parsearInterpolacion(template);
        }

        if (verificar(TipoToken.LITERAL_NUMERO)) {
            int valor = Integer.parseInt(tokenActual.getValor());
            avanzar();
            return new LiteralNumero(valor);
        }

        if (verificar(TipoToken.IDENTIFICADOR)) {
            String nombre = tokenActual.getValor();
            avanzar();

            if (verificar(TipoToken.PARENTESIS_IZQ)) {
                avanzar();
                List<Expresion> argumentos = new ArrayList<>();
                while (!verificar(TipoToken.PARENTESIS_DER)) {
                    argumentos.add(parsearExpresion());
                    if (verificar(TipoToken.COMA)) avanzar();
                }
                consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                return new LlamadaFuncion("", nombre, argumentos);
            }

            if (verificar(TipoToken.CORCHETE_IZQ)) {
                avanzar();
                Expresion indice = parsearExpresion();
                consumir(TipoToken.CORCHETE_DER, "Se esperaba ']'");
                return new AccesoLista(new Variable(nombre), indice);
            }

            if (verificar(TipoToken.PUNTO)) {
                avanzar();
                String nombreMetodo;
                if (verificar(TipoToken.IDENTIFICADOR)) {
                    nombreMetodo = tokenActual.getValor();
                    avanzar();
                } else if (verificar(TipoToken.TIPO_TEXTO)) {
                    nombreMetodo = "texto"; avanzar();
                } else if (verificar(TipoToken.TIPO_NUMERO)) {
                    nombreMetodo = "numero"; avanzar();
                } else {
                    throw new RuntimeException("Se esperaba nombre de método en línea " + tokenActual.getLinea());
                }

                if (nombreMetodo.equals("texto")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionTexto(new Variable(nombre));
                } else if (nombreMetodo.equals("numero")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionNumero(new Variable(nombre));
                } else if (verificar(TipoToken.PARENTESIS_IZQ)) {
                    List<Expresion> args = new ArrayList<>();
                    avanzar();
                    while (!verificar(TipoToken.PARENTESIS_DER)) {
                        args.add(parsearExpresion());
                        if (verificar(TipoToken.COMA)) avanzar();
                    }
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new LlamadaFuncion(nombre, nombreMetodo, args);
                } else {
                    return new AccesoJsn(new Variable(nombre), nombreMetodo);
                }
            }

            return new Variable(nombre);
        }

        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
        }

        if (verificar(TipoToken.PARENTESIS_IZQ)) {
            avanzar();
            Expresion expresion = parsearExpresion();
            consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
            return expresion;
        }

        if (verificar(TipoToken.VERDADERO)) {
            avanzar();
            return new LiteralNumero(1);
        }

        if (verificar(TipoToken.FALSO)) {
            avanzar();
            return new LiteralNumero(0);
        }

        throw new RuntimeException("Expresión no válida en línea " + tokenActual.getLinea());
    }

    private Expresion parsearInterpolacion(String template) {
        List<Expresion> partes = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        int i = 0;

        while (i < template.length()) {
            if (template.charAt(i) == '{') {
                if (texto.length() > 0) {
                    partes.add(new LiteralString(texto.toString()));
                    texto = new StringBuilder();
                }

                int fin = template.indexOf('}', i);
                if (fin == -1) {
                    throw new RuntimeException("Falta '}' en string interpolado");
                }

                String codigoExpresion = template.substring(i + 1, fin).trim();
                Expresion expresion = parsearExpresionInterpolada(codigoExpresion);
                partes.add(new ConversionTexto(expresion));

                i = fin + 1;
            } else {
                texto.append(template.charAt(i));
                i++;
            }
        }

        if (texto.length() > 0) {
            partes.add(new LiteralString(texto.toString()));
        }

        if (partes.isEmpty()) {
            return new LiteralString("");
        }

        Expresion resultado = partes.get(0);
        for (int j = 1; j < partes.size(); j++) {
            resultado = new Concatenacion(resultado, partes.get(j));
        }

        return resultado;
    }

    private Expresion parsearExpresionInterpolada(String codigo) {
        Lexer lexerTemp = new Lexer(codigo);
        List<Token> tokensTemp = lexerTemp.tokenizar();
        Parser parserTemp = new Parser(tokensTemp);
        return parserTemp.parsearExpresion();
    }

    private NodoSi parsearSi() {
        consumir(TipoToken.SI, "Se esperaba 'si'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
        Expresion condicion = parsearExpresion();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();

        List<Nodo> cuerpoSi = parsearBloque();

        List<Expresion> condicionesSinoSi = new ArrayList<>();
        List<List<Nodo>> cuerposSinoSi = new ArrayList<>();
        List<Nodo> cuerpoSino = null;

        saltarNuevasLineas();

        while (verificar(TipoToken.SINO)) {
            consumir(TipoToken.SINO, "Se esperaba 'sino'");
            saltarNuevasLineas();

            if (verificar(TipoToken.SI)) {
                consumir(TipoToken.SI, "Se esperaba 'si'");
                consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                Expresion condSinoSi = parsearExpresion();
                consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                saltarNuevasLineas();
                condicionesSinoSi.add(condSinoSi);
                cuerposSinoSi.add(parsearBloque());
                saltarNuevasLineas();
            } else {
                cuerpoSino = parsearBloque();
                break;
            }
        }

        return new NodoSi(condicion, cuerpoSi, condicionesSinoSi, cuerposSinoSi, cuerpoSino);
    }

    private NodoMientras parsearMientras() {
        consumir(TipoToken.MIENTRAS, "Se esperaba 'mientras'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
        Expresion condicion = parsearExpresion();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();

        List<Nodo> cuerpo = parsearBloque();

        return new NodoMientras(condicion, cuerpo);
    }

    private NodoRomper parsearRomper() {
        consumir(TipoToken.ROMPER, "Se esperaba 'romper'");
        return new NodoRomper();
    }

    private NodoContinuar parsearContinuar() {
        consumir(TipoToken.CONTINUAR, "Se esperaba 'continuar'");
        return new NodoContinuar();
    }

    private NodoHacerMientras parsearHacerMientras() {
        consumir(TipoToken.HACER, "Se esperaba 'hacer'");
        saltarNuevasLineas();

        List<Nodo> cuerpo = parsearBloque();

        saltarNuevasLineas();
        consumir(TipoToken.MIENTRAS, "Se esperaba 'mientras'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
        Expresion condicion = parsearExpresion();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");

        return new NodoHacerMientras(cuerpo, condicion);
    }

    private NodoFuncion parsearFuncion(String tipoRetorno) {
        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de función");
        String nombre = nombreToken.getValor();

        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        List<String[]> parametros = new ArrayList<>();
        while (!verificar(TipoToken.PARENTESIS_DER)) {
            // Tipo del parámetro
            String tipoParm = tokenActual.getValor();
            avanzar();

            // Manejar tipo genérico: lista<entero>, lista<texto>
            if (tipoParm.equals("lista") && verificar(TipoToken.MENOR)) {
                avanzar(); // consumir
                String tipoInterno = tokenActual.getValor();
                avanzar(); // consumir tipo interno
                consumir(TipoToken.MAYOR, "Se esperaba '>'");
                tipoParm = "lista<" + tipoInterno + ">";
            }

            // var opcional
            if (verificar(TipoToken.VAR)) avanzar();

            // Nombre del parámetro
            Token nombreParm = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de parámetro");
            parametros.add(new String[]{tipoParm, nombreParm.getValor()});

            if (verificar(TipoToken.COMA)) avanzar();
        }

        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();

        List<Nodo> cuerpo = parsearBloque();

        return new NodoFuncion(tipoRetorno, nombre, parametros, cuerpo);
    }

    private NodoRetornar parsearRetornar() {
        consumir(TipoToken.RETORNAR, "Se esperaba 'retornar'");

        // Si la siguiente línea es nueva línea o } no hay valor
        if (verificar(TipoToken.NUEVA_LINEA) || verificar(TipoToken.LLAVE_DER)
                || verificar(TipoToken.EOF)) {
            return new NodoRetornar(null);
        }

        Expresion valor = parsearExpresion();
        return new NodoRetornar(valor);
    }

    private NodoObjeto parsearObjeto() {
        consumir(TipoToken.OBJETO, "Se esperaba 'objeto'");
        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre del objeto");
        String nombre = nombreToken.getValor();

        consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{'");
        saltarNuevasLineas();

        List<NodoAtributo> atributos = new ArrayList<>();
        NodoConstructor constructor = null;
        List<NodoMetodo> metodos = new ArrayList<>();

        boolean esPublico = true; // por defecto publico

        while (!verificar(TipoToken.LLAVE_DER) && !verificar(TipoToken.EOF)) {

            // Bloque privado:
            if (verificar(TipoToken.PRIVADO)) {
                avanzar();
                consumir(TipoToken.DOS_PUNTOS, "Se esperaba ':'");
                saltarNuevasLineas();
                esPublico = false;
                continue;
            }

            // Bloque publico:
            if (verificar(TipoToken.PUBLICO)) {
                avanzar();
                consumir(TipoToken.DOS_PUNTOS, "Se esperaba ':'");
                saltarNuevasLineas();
                esPublico = true;
                continue;
            }

            // Constructor: mismo nombre que el objeto
            if (verificar(TipoToken.IDENTIFICADOR) &&
                    tokenActual.getValor().equals(nombre) &&
                    tokens.get(posicion + 1).getTipo() == TipoToken.PARENTESIS_IZQ) {
                constructor = parsearConstructor();
                saltarNuevasLineas();
                continue;
            }

            // Atributo o método
            if (verificar(TipoToken.TIPO_ENTERO) || verificar(TipoToken.TIPO_TEXTO) ||
                    verificar(TipoToken.TIPO_NUMERO) || verificar(TipoToken.TIPO_LOG) ||
                    verificar(TipoToken.TIPO_VACIO)) {

                String tipo = tokenActual.getValor();
                avanzar();

                // var opcional
                boolean mutable = false;
                if (verificar(TipoToken.VAR)) {
                    mutable = true;
                    avanzar();
                }

                Token miembroNombre = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre");

                // Si sigue ( es método
                if (verificar(TipoToken.PARENTESIS_IZQ)) {
                    NodoMetodo metodo = parsearMetodo(tipo, miembroNombre.getValor(), esPublico);
                    metodos.add(metodo);
                } else {
                    // Es atributo
                    atributos.add(new NodoAtributo(tipo, miembroNombre.getValor(), esPublico, mutable));
                }
            }

            saltarNuevasLineas();
        }

        consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
        return new NodoObjeto(nombre, atributos, constructor, metodos);
    }


    private NodoConstructor parsearConstructor() {
        avanzar(); // consumir nombre del constructor
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        List<String[]> parametros = new ArrayList<>();
        while (!verificar(TipoToken.PARENTESIS_DER)) {
            // Tipo del parámetro
            String tipoParm = tokenActual.getValor();
            avanzar();

            // Manejar tipo genérico: lista<entero>, lista<texto>
            if (tipoParm.equals("lista") && verificar(TipoToken.MENOR)) {
                avanzar(); // consumir
                String tipoInterno = tokenActual.getValor();
                avanzar(); // consumir tipo interno
                consumir(TipoToken.MAYOR, "Se esperaba '>'");
                tipoParm = "lista<" + tipoInterno + ">";
            }

            // var opcional
            if (verificar(TipoToken.VAR)) avanzar();

            // Nombre del parámetro
            Token nombreParm = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de parámetro");
            parametros.add(new String[]{tipoParm, nombreParm.getValor()});

            if (verificar(TipoToken.COMA)) avanzar();
        }

        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();
        List<Nodo> cuerpo = parsearBloque();
        return new NodoConstructor(parametros, cuerpo);
    }


    private NodoMetodo parsearMetodo(String tipo, String nombre, boolean esPublico) {
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        List<String[]> parametros = new ArrayList<>();
        while (!verificar(TipoToken.PARENTESIS_DER)) {
            // Tipo del parámetro
            String tipoParm = tokenActual.getValor();
            avanzar();

            // Manejar tipo genérico: lista<entero>, lista<texto>
            if (tipoParm.equals("lista") && verificar(TipoToken.MENOR)) {
                avanzar(); // consumir
                String tipoInterno = tokenActual.getValor();
                avanzar(); // consumir tipo interno
                consumir(TipoToken.MAYOR, "Se esperaba '>'");
                tipoParm = "lista<" + tipoInterno + ">";
            }

            // var opcional
            if (verificar(TipoToken.VAR)) avanzar();

            // Nombre del parámetro
            Token nombreParm = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre de parámetro");
            parametros.add(new String[]{tipoParm, nombreParm.getValor()});

            if (verificar(TipoToken.COMA)) avanzar();
        }

        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();
        List<Nodo> cuerpo = parsearBloque();
        return new NodoMetodo(tipo, nombre, parametros, cuerpo, esPublico);
    }



}