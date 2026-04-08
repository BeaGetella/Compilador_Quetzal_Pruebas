package com.example.parser;

import com.example.lexer.Lexer;
import com.example.lexer.Token;
import com.example.lexer.TipoToken;
import com.example.parser.ast.*;

import java.util.ArrayList;
import java.util.List;

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

    // Parsear una instrucción
    private Nodo parsearInstruccion() {
        // Declaración de variable (soporta: entero, numero, texto, log, lista, jsn, vacio)
        if (verificar(TipoToken.TIPO_ENTERO) ||
                verificar(TipoToken.TIPO_NUMERO) ||
                verificar(TipoToken.TIPO_TEXTO)  ||
                verificar(TipoToken.TIPO_LOG)    ||
                verificar(TipoToken.TIPO_LISTA)  ||
                verificar(TipoToken.TIPO_JSN)    ||
                verificar(TipoToken.TIPO_VACIO)) {
            return parsearDeclaracionVariable();
        }

        // Llamada a consola
        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
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

        // Palabras reservadas reconocidas pero aún sin implementación
        if (
//                verificar(TipoToken.SINO)        ||
                verificar(TipoToken.PARA)        ||
                verificar(TipoToken.HACER)       ||
                verificar(TipoToken.RETORNAR)    ||
                verificar(TipoToken.OBJETO)      ||
                verificar(TipoToken.NUEVO)       ||
                verificar(TipoToken.INTENTAR)    ||
                verificar(TipoToken.IMPORTAR)    ||
                verificar(TipoToken.EXPORTAR)    ||
                verificar(TipoToken.PUBLICO)     ||
                verificar(TipoToken.PRIVADO)     ||
                verificar(TipoToken.LIBRE)       ||
                verificar(TipoToken.ASINCRONO)) {
            throw new RuntimeException(
                    "'" + tokenActual.getValor() + "' aún no está implementado " +
                            "(línea " + tokenActual.getLinea() + ")"
            );
        }

        if (verificar(TipoToken.IDENTIFICADOR)) {
            return parsearExpresion();
        }



        throw new RuntimeException("Instrucción no reconocida: '" +
                tokenActual.getValor() + "' en línea " + tokenActual.getLinea());
    }

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

        // Consumir 'var' opcional (indica variable mutable)
        boolean esMutable = false;
        if (verificar(TipoToken.VAR)) {
            esMutable = true;
            avanzar();
        }

        // Consumir el nombre de la variable
        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba un nombre de variable");
        String nombre = nombreToken.getValor();

        // Consumir '='
        consumir(TipoToken.IGUAL, "Se esperaba '='");

        // Parsear la expresión del lado derecho
        Expresion valor = parsearExpresion();

        return new DeclaracionVariable(tipo, nombre, valor);
    }

    // Parsear consola.mostrar(...) o consola.pedir(...)
    private LlamadaFuncion parsearLlamadaConsola() {
        // Consumir 'consola'
        consumir(TipoToken.CONSOLA, "Se esperaba 'consola'");

        // Consumir '.'
        consumir(TipoToken.PUNTO, "Se esperaba '.'");

        // Consumir el nombre del método
        Token metodoToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba nombre del método");
        String metodo = metodoToken.getValor();

        // Consumir '('
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");

        // Parsear argumentos (con concatenación)
        List<Expresion> argumentos = new ArrayList<>();

        if (!verificar(TipoToken.PARENTESIS_DER)) {
            argumentos.add(parsearExpresionString());
        }

        // Consumir ')'
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");

        return new LlamadaFuncion("consola", metodo, argumentos);
    }

    // Parsear expresiones de string (con concatenación)
    private Expresion parsearExpresionString() {
        Expresion izquierda = parsearElementoString();

        // Concatenaciones con +
        while (verificar(TipoToken.MAS)) {
            avanzar(); // consumir '+'
            Expresion derecha = parsearElementoString();
            izquierda = new Concatenacion(izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearElementoString() {
        // String literal normal
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

        // Variable con posible .texto() o .numero()
        if (verificar(TipoToken.IDENTIFICADOR)) {
            String nombre = tokenActual.getValor();
            avanzar();

            // Verificar si tiene un método (.texto(), .numero())
            if (verificar(TipoToken.PUNTO)) {
                avanzar(); // consumir '.'

                // Obtener nombre del método (puede ser palabra reservada)
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

                // Procesar según el método
                if (nombreMetodo.equals("texto")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionTexto(new Variable(nombre));
                } else if (nombreMetodo.equals("numero")) {
                    consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                    consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                    return new ConversionNumero(new Variable(nombre));
                } else {
                    throw new RuntimeException("Método desconocido: " + nombreMetodo + " en línea " + tokenActual.getLinea());
                }
            }

            return new Variable(nombre);
        }

        // Número literal
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

        // Asignación compuesta: a = 5, a += 3, etc.
        if (verificar(TipoToken.IGUAL)       ||
                verificar(TipoToken.MAS_IGUAL)   ||
                verificar(TipoToken.MENOS_IGUAL) ||
                verificar(TipoToken.MULT_IGUAL)  ||
                verificar(TipoToken.DIV_IGUAL)   ||
                verificar(TipoToken.MOD_IGUAL)) {

            // El lado izquierdo debe ser una variable
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
            avanzar(); // consumir '?'
            Expresion siVerdadero = parsearExpresion();
            consumir(TipoToken.DOS_PUNTOS, "Se esperaba ':' en operador ternario");
            Expresion siFalso = parsearExpresion();
            return new OperacionTernaria(condicion, siVerdadero, siFalso);
        }

        return condicion;
    }

    private Expresion parsearOr() {
        Expresion izquierda = parsearAnd();
//        while (verificar(TipoToken.O) || verificar(TipoToken.OR)) {  // O = "o", OR = "||"
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

    // Parsear expresiones aditivas: a + b, a - b
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

    // Parsear expresiones multiplicativas: a * b, a / b
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
        // Prefijos: no, !, - (negación), ++ (pre-incremento), -- (pre-decremento)
        if (verificar(TipoToken.NO) || verificar(TipoToken.NO)) {
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

        // Postfijos: variable++ o variable--
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
        // Número literal
        if (verificar(TipoToken.LITERAL_NUMERO)) {
            int valor = Integer.parseInt(tokenActual.getValor());
            avanzar();
            return new LiteralNumero(valor);
        }

        // Variable
        if (verificar(TipoToken.IDENTIFICADOR)) {
            String nombre = tokenActual.getValor();
            avanzar();

            // Verificar si tiene un método (.texto(), .numero())
            if (verificar(TipoToken.PUNTO)) {
                avanzar(); // consumir '.'

                // Aceptar tanto IDENTIFICADOR como palabras reservadas como nombres de método
                String nombreMetodo;
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
                }
            }

            return new Variable(nombre);
        }

        // Llamada a consola.pedir() o consola.mostrar()
        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
        }

        // Paréntesis: (expresión)
        if (verificar(TipoToken.PARENTESIS_IZQ)) {
            avanzar();
            Expresion expresion = parsearExpresion();
            consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
            return expresion;
        }

        throw new RuntimeException("Expresión no válida en línea " + tokenActual.getLinea());
    }


    private Expresion parsearInterpolacion(String template) {
        List<Expresion> partes = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        int i = 0;

        while (i < template.length()) {
            if (template.charAt(i) == '{') {
                // Agregar el texto acumulado
                if (texto.length() > 0) {
                    partes.add(new LiteralString(texto.toString()));
                    texto = new StringBuilder();
                }

                // Encontrar el cierre }
                int fin = template.indexOf('}', i);
                if (fin == -1) {
                    throw new RuntimeException("Falta '}' en string interpolado");
                }

                // Extraer el código de la expresión
                String codigoExpresion = template.substring(i + 1, fin).trim();

                // Parsear la expresión (soporta operaciones)
                Expresion expresion = parsearExpresionInterpolada(codigoExpresion);
                partes.add(new ConversionTexto(expresion));

                i = fin + 1;
            } else {
                texto.append(template.charAt(i));
                i++;
            }
        }

        // Agregar último texto
        if (texto.length() > 0) {
            partes.add(new LiteralString(texto.toString()));
        }

        // Combinar todas las partes con Concatenacion
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
        // Crear un Lexer temporal para el código dentro de {}
        Lexer lexerTemp = new Lexer(codigo);
        List<Token> tokensTemp = lexerTemp.tokenizar();

        // Crear un Parser temporal
        Parser parserTemp = new Parser(tokensTemp);

        // Parsear como expresión (soporta a + b, a * 2, etc.)
        return parserTemp.parsearExpresion();
    }

    private List<Nodo> parsearBloque() {
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

        // Mientras haya sino si o sino
        while (verificar(TipoToken.SINO)) {
            consumir(TipoToken.SINO, "Se esperaba 'sino'");
            saltarNuevasLineas();

            if (verificar(TipoToken.SI)) {
                // sino si (condicion) { }
                consumir(TipoToken.SI, "Se esperaba 'si'");
                consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                Expresion condSinoSi = parsearExpresion();
                consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                saltarNuevasLineas();
                condicionesSinoSi.add(condSinoSi);
                cuerposSinoSi.add(parsearBloque());
                saltarNuevasLineas();
            } else {
                // sino { } — bloque final
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
}