package com.example.parser;

import com.example.lexer.Lexer;
import com.example.lexer.Token;
import com.example.lexer.TipoToken;
import com.example.parser.ast.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    //recorre la lista de tokens y va construyendo el AST, tiene métodos para cada tipo de instrucción y expresión
    private List<Token> tokens;
    private int posicion;
    private Token tokenActual;

    //constructor que recibe la lista de tokens generada por el lexer y se inicializa con el primer token
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        //inicializar la posición y el token actual
        this.posicion = 0;
        this.tokenActual = tokens.size() > 0 ? tokens.get(0) : null;
    }
    //avanzar al siguiente token y actualizar el token actual
    private void avanzar() {
        posicion++;
        if (posicion < tokens.size()) {
            tokenActual = tokens.get(posicion);
        }
    }
    //verificar si el token actual es del tipo esperado, si no es así lanzar una excepción con un mensaje de error
    private boolean verificar(TipoToken tipo) {
        if (tokenActual == null) return false;
        return tokenActual.getTipo() == tipo;
    }
        //consumir un token del tipo esperado, si es correcto avanzar al siguiente token y devolver el token consumido, si no es así lanzar una excepción con un mensaje de error
    //si esperaba un = y aparece otra cosa da error
    private Token consumir(TipoToken tipo, String mensajeError) {
        if (verificar(tipo)) {
            Token token = tokenActual;
            avanzar();
            return token;
        }
        throw new RuntimeException(mensajeError + " en línea " + tokenActual.getLinea());
    }
    //saltar cualquier cantidad de tokens de nueva línea para ignorarlos en el análisis sintáctico
    private void saltarNuevasLineas() {
        while (verificar(TipoToken.NUEVA_LINEA)) {
            avanzar();
        }
    }
    //método principal para iniciar el proceso de análisis sintáctico, devuelve un nodo raíz del tipo Programa que contiene la lista de instrucciones del programa
    public Programa parsear() {
        List<Nodo> instrucciones = new ArrayList<>();
    //ingnora las nuevas líneas al inicio del programa para evitar problemas con el formato del código fuente
        saltarNuevasLineas();
    //mientras no se alcance el final del archivo (EOF) se van parseando las instrucciones
        //y agregándolas a la lista de instrucciones del programa, también se saltan las nuevas líneas entre instrucciones para permitir un formato más flexible
        while (!verificar(TipoToken.EOF)) {
            instrucciones.add(parsearInstruccion());
            saltarNuevasLineas();
        }

        return new Programa(instrucciones);
    }
    //método para parsear una instrucción, dependiendo del token actual se decide qué tipo de instrucción se va a parsear (declaración de variable, llamada a consola, condicional, etc.) y se llama al método correspondiente para cada caso, si el token no coincide con ningún tipo de instrucción conocida se lanza una excepción con un mensaje de error
    private Nodo parsearInstruccion() {
        if (verificar(TipoToken.TIPO_ENTERO) ||
                verificar(TipoToken.TIPO_NUMERO) ||
                verificar(TipoToken.TIPO_TEXTO)) {
            return parsearDeclaracionVariable();
        }

        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
        }

        if (verificar(TipoToken.SI)) {
            return parsearCondicional();
        }

        throw new RuntimeException("Instrucción no reconocida en línea " + tokenActual.getLinea());
    }

    //método para parsear una declaración de variable,
    // primero se verifica el tipo de dato (entero, numero o texto) y se consume el token
    // correspondiente, luego se consume el token del nombre de la variable y el
    // símbolo de igual, finalmente se parsea la expresión que representa el valor
    // inicial de la variable y se devuelve un nodo del tipo DeclaracionVariable con
    // toda la información recopilada
    private DeclaracionVariable parsearDeclaracionVariable() {
        Token tipoToken;

        if (verificar(TipoToken.TIPO_ENTERO)) {
            tipoToken = consumir(TipoToken.TIPO_ENTERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_NUMERO)) {
            tipoToken = consumir(TipoToken.TIPO_NUMERO, "Se esperaba tipo de dato");
        } else if (verificar(TipoToken.TIPO_TEXTO)) {
            tipoToken = consumir(TipoToken.TIPO_TEXTO, "Se esperaba tipo de dato");
        } else {
            throw new RuntimeException("Se esperaba un tipo de dato (entero, numero, texto) en línea " + tokenActual.getLinea());
        }

        String tipo = tipoToken.getValor();

        Token nombreToken = consumir(TipoToken.IDENTIFICADOR, "Se esperaba un nombre de variable");
        String nombre = nombreToken.getValor();

        consumir(TipoToken.IGUAL, "Se esperaba '='");

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
                } else {
                    throw new RuntimeException("Método desconocido: " + nombreMetodo + " en línea " + tokenActual.getLinea());
                }
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
-----
    private Expresion parsearExpresion() {
        return parsearExpresionAditiva();
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
        Expresion izquierda = parsearExpresionPrimaria();

        while (verificar(TipoToken.MULTIPLICACION) || verificar(TipoToken.DIVISION)) {
            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearExpresionPrimaria();
            izquierda = new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }

    private Expresion parsearExpresionPrimaria() {
        if (verificar(TipoToken.LITERAL_NUMERO)) {
            int valor = Integer.parseInt(tokenActual.getValor());
            avanzar();
            return new LiteralNumero(valor);
        }

        if (verificar(TipoToken.IDENTIFICADOR)) {
            String nombre = tokenActual.getValor();
            avanzar();

            if (verificar(TipoToken.PUNTO)) {
                avanzar();

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

        if (verificar(TipoToken.CONSOLA)) {
            return parsearLlamadaConsola();
        }

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

    private List<Nodo> parsearBloque() {
        List<Nodo> instrucciones = new ArrayList<>();
        saltarNuevasLineas();
        while (!verificar(TipoToken.LLAVE_DER) && !verificar(TipoToken.EOF)) {
            instrucciones.add(parsearInstruccion());
            saltarNuevasLineas();
        }
        return instrucciones;
    }

    private Condicional parsearCondicional() {
        List<Condicional.Rama> ramas = new ArrayList<>();

        consumir(TipoToken.SI, "Se esperaba 'si'");
        consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
        Expresion condicion = parsearExpresionComparacion();
        consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
        saltarNuevasLineas();
        consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{'");
        saltarNuevasLineas();
        List<Nodo> cuerpo = parsearBloque();
        consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
        ramas.add(new Condicional.Rama(condicion, cuerpo));

        saltarNuevasLineas();
        while (verificar(TipoToken.SINO)) {
            consumir(TipoToken.SINO, "Se esperaba 'sino'");
            saltarNuevasLineas();

            if (verificar(TipoToken.SI)) {
                consumir(TipoToken.SI, "Se esperaba 'si'");
                consumir(TipoToken.PARENTESIS_IZQ, "Se esperaba '('");
                Expresion cond2 = parsearExpresionComparacion();
                consumir(TipoToken.PARENTESIS_DER, "Se esperaba ')'");
                saltarNuevasLineas();
                consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{'");
                saltarNuevasLineas();
                List<Nodo> cuerpo2 = parsearBloque();
                consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
                ramas.add(new Condicional.Rama(cond2, cuerpo2));
            } else {
                consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{'");
                saltarNuevasLineas();
                List<Nodo> cuerpoSino = parsearBloque();
                consumir(TipoToken.LLAVE_DER, "Se esperaba '}'");
                ramas.add(new Condicional.Rama(null, cuerpoSino));
                break;
            }

            saltarNuevasLineas();
        }

        return new Condicional(ramas);
    }

    private Expresion parsearExpresionComparacion() {
        Expresion izquierda = parsearExpresion();

        if (verificar(TipoToken.MAYOR) || verificar(TipoToken.MENOR) ||
                verificar(TipoToken.MAYOR_IGUAL) || verificar(TipoToken.MENOR_IGUAL) ||
                verificar(TipoToken.IGUAL_IGUAL) || verificar(TipoToken.DIFERENTE)) {

            String operador = tokenActual.getValor();
            avanzar();
            Expresion derecha = parsearExpresion();
            return new OperacionBinaria(operador, izquierda, derecha);
        }

        return izquierda;
    }
}