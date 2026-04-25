package com.example.lexer;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private String codigo;
    private int posicion;
    private int linea;
    private char caracterActual;

    private static final java.util.Map<String, TipoToken> PALABRAS_RESERVADAS = new java.util.HashMap<>();
    static {
        // Tipos de datos
        PALABRAS_RESERVADAS.put("vacio",       TipoToken.TIPO_VACIO);
        PALABRAS_RESERVADAS.put("entero",      TipoToken.TIPO_ENTERO);
        PALABRAS_RESERVADAS.put("número",      TipoToken.TIPO_NUMERO);
        PALABRAS_RESERVADAS.put("numero",      TipoToken.TIPO_NUMERO);
        PALABRAS_RESERVADAS.put("texto",       TipoToken.TIPO_TEXTO);
        PALABRAS_RESERVADAS.put("log",         TipoToken.TIPO_LOG);
        PALABRAS_RESERVADAS.put("lista",       TipoToken.TIPO_LISTA);
        PALABRAS_RESERVADAS.put("jsn",         TipoToken.TIPO_JSN);
        // Modificadores
        PALABRAS_RESERVADAS.put("var",         TipoToken.VAR);
        PALABRAS_RESERVADAS.put("publico",     TipoToken.PUBLICO);
        PALABRAS_RESERVADAS.put("privado",     TipoToken.PRIVADO);
        PALABRAS_RESERVADAS.put("libre",       TipoToken.LIBRE);
        PALABRAS_RESERVADAS.put("ambiente",    TipoToken.AMBIENTE);
        // Control de flujo
        PALABRAS_RESERVADAS.put("si",          TipoToken.SI);
        PALABRAS_RESERVADAS.put("sino",        TipoToken.SINO);
        PALABRAS_RESERVADAS.put("para",        TipoToken.PARA);
        PALABRAS_RESERVADAS.put("mientras",    TipoToken.MIENTRAS);
        PALABRAS_RESERVADAS.put("hacer",       TipoToken.HACER);
        PALABRAS_RESERVADAS.put("romper",      TipoToken.ROMPER);
        PALABRAS_RESERVADAS.put("continuar",   TipoToken.CONTINUAR);
        PALABRAS_RESERVADAS.put("en",          TipoToken.EN);
        PALABRAS_RESERVADAS.put("cada",        TipoToken.CADA);
        PALABRAS_RESERVADAS.put("retornar",    TipoToken.RETORNAR);
        // Objetos
        PALABRAS_RESERVADAS.put("objeto",      TipoToken.OBJETO);
        PALABRAS_RESERVADAS.put("nuevo",       TipoToken.NUEVO);
        PALABRAS_RESERVADAS.put("constructor", TipoToken.CONSTRUCTOR);
        // Asincronía
        PALABRAS_RESERVADAS.put("asincrono",   TipoToken.ASINCRONO);
        PALABRAS_RESERVADAS.put("esperar",     TipoToken.ESPERAR);
        // Excepciones
        PALABRAS_RESERVADAS.put("intentar",    TipoToken.INTENTAR);
        PALABRAS_RESERVADAS.put("capturar",    TipoToken.CAPTURAR);
        PALABRAS_RESERVADAS.put("finalmente",  TipoToken.FINALMENTE);
        PALABRAS_RESERVADAS.put("lanzar",      TipoToken.LANZAR);
        PALABRAS_RESERVADAS.put("excepcion",   TipoToken.EXCEPCION);
        // Módulos
        PALABRAS_RESERVADAS.put("importar",    TipoToken.IMPORTAR);
        PALABRAS_RESERVADAS.put("exportar",    TipoToken.EXPORTAR);
        PALABRAS_RESERVADAS.put("desde",       TipoToken.DESDE);
        PALABRAS_RESERVADAS.put("como",        TipoToken.COMO);
        // Literales y lógicos
        PALABRAS_RESERVADAS.put("verdadero",   TipoToken.VERDADERO);
        PALABRAS_RESERVADAS.put("falso",       TipoToken.FALSO);
        PALABRAS_RESERVADAS.put("nulo",        TipoToken.NULO);
        PALABRAS_RESERVADAS.put("y",           TipoToken.Y);
        PALABRAS_RESERVADAS.put("o",           TipoToken.O);
        PALABRAS_RESERVADAS.put("no",          TipoToken.NO);
        // Otros protegidos
        PALABRAS_RESERVADAS.put("mut",         TipoToken.MUT);
        PALABRAS_RESERVADAS.put("de",          TipoToken.DE);
        PALABRAS_RESERVADAS.put("es",          TipoToken.ES);
        // Consola
        PALABRAS_RESERVADAS.put("consola",     TipoToken.CONSOLA);
    }

    public Lexer(String codigo) {
        this.codigo = codigo;
        this.posicion = 0;
        this.linea = 1;
        this.caracterActual = codigo.length() > 0 ? codigo.charAt(0) : '\0';
    }

    private void avanzar() {
        posicion++;
        if (posicion < codigo.length()) {
            caracterActual = codigo.charAt(posicion);
        } else {
            caracterActual = '\0';
        }
    }

    private char verSiguiente() {
        if (posicion + 1 < codigo.length()) {
            return codigo.charAt(posicion + 1);
        }
        return '\0';
    }

    private void saltarEspacios() {
        while (caracterActual == ' ' || caracterActual == '\t' || caracterActual == '\r') {
            avanzar();
        }
    }

    private Token leerNumero() {
        StringBuilder numero = new StringBuilder();
        int lineaInicio = linea;
        boolean esDecimal = false;

        // Parte entera
        while (Character.isDigit(caracterActual)) {
            numero.append(caracterActual);
            avanzar();
        }


        if (caracterActual == '.' && Character.isDigit(verSiguiente())) {
            esDecimal = true;
            numero.append(caracterActual); // el punto
            avanzar();
            while (Character.isDigit(caracterActual)) {
                numero.append(caracterActual);
                avanzar();
            }
        }

        if (esDecimal) {
            return new Token(TipoToken.LITERAL_DECIMAL, numero.toString(), lineaInicio);
        }

        return new Token(TipoToken.LITERAL_NUMERO, numero.toString(), lineaInicio);
    }

    private Token leerIdentificador() {
        StringBuilder identificador = new StringBuilder();
        int lineaInicio = linea;

        while (Character.isLetterOrDigit(caracterActual) || caracterActual == '_') {
            identificador.append(caracterActual);
            avanzar();
        }

        String valor = identificador.toString();
        TipoToken tipo = PALABRAS_RESERVADAS.getOrDefault(valor, TipoToken.IDENTIFICADOR);

        return new Token(tipo, valor, lineaInicio);
    }

    private Token leerString() {
        StringBuilder string = new StringBuilder();
        int lineaInicio = linea;

        avanzar(); // Saltar la comilla inicial "

        while (caracterActual != '"' && caracterActual != '\0') {
            string.append(caracterActual);
            avanzar();
        }

        if (caracterActual == '"') {
            avanzar(); // Saltar la comilla final "
        }

        if (caracterActual == '\'') {
            return leerStringSimple();
        }



        return new Token(TipoToken.LITERAL_STRING, string.toString(), lineaInicio);
    }


    private Token leerStringSimple() {
        StringBuilder string = new StringBuilder();
        int lineaInicio = linea;

        avanzar(); // Saltar la comilla simple inicial '

        while (caracterActual != '\'' && caracterActual != '\0') {
            string.append(caracterActual);
            avanzar();
        }

        if (caracterActual == '\'') {
            avanzar(); // Saltar la comilla simple final '
        }

        return new Token(TipoToken.LITERAL_STRING, string.toString(), lineaInicio);
    }

    private Token leerStringInterpolado() {
        StringBuilder string = new StringBuilder();
        int lineaInicio = linea;

        avanzar(); // Saltar la comilla inicial "

        while (caracterActual != '"' && caracterActual != '\0') {
            string.append(caracterActual);
            avanzar();
        }

        if (caracterActual == '"') {
            avanzar(); // Saltar la comilla final "
        }

        return new Token(TipoToken.STRING_INTERPOLADO, string.toString(), lineaInicio);
    }

    private Token siguienteToken() {
        while (caracterActual != '\0') {

            if (caracterActual == ' ' || caracterActual == '\t' || caracterActual == '\r') {
                saltarEspacios();
                continue;
            }

            if (caracterActual == '\n') {
                Token token = new Token(TipoToken.NUEVA_LINEA, "\\n", linea);
                linea++;
                avanzar();
                return token;
            }

            // Comentarios de línea: //
            if (caracterActual == '/' && verSiguiente() == '/') {
                while (caracterActual != '\n' && caracterActual != '\0') {
                    avanzar();
                }
                continue;
            }

            if (Character.isDigit(caracterActual)) {
                return leerNumero();
            }

            if (Character.isLetter(caracterActual) || caracterActual == '_') {
                if (caracterActual == 't' && verSiguiente() == '"') {
                    avanzar(); // saltar la 't'
                    return leerStringInterpolado();
                }
                return leerIdentificador();
            }

            if (caracterActual == '"') {
                return leerString();
            }

            if (caracterActual == '\'') {
                return leerStringSimple();
            }

            int lineaActual = linea;
            switch (caracterActual) {
                case '(':
                    avanzar();
                    return new Token(TipoToken.PARENTESIS_IZQ, "(", lineaActual);
                case ')':
                    avanzar();
                    return new Token(TipoToken.PARENTESIS_DER, ")", lineaActual);
                case '{':
                    avanzar();
                    return new Token(TipoToken.LLAVE_IZQ, "{", lineaActual);
                case '}':
                    avanzar();
                    return new Token(TipoToken.LLAVE_DER, "}", lineaActual);
                case '.':
                    avanzar();
                    return new Token(TipoToken.PUNTO, ".", lineaActual);
                case ':':
                    avanzar();
                    return new Token(TipoToken.DOS_PUNTOS, ":", lineaActual);
                case ';':                                                      // ← NUEVO
                    avanzar();
                    return new Token(TipoToken.PUNTO_COMA, ";", lineaActual); // ← NUEVO
                case '%':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MOD_IGUAL, "%=", lineaActual);
                    }
                    return new Token(TipoToken.MODULO, "%", lineaActual);

                case '!':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.DIFERENTE, "!=", lineaActual);
                    }
                    return new Token(TipoToken.NO, "!", lineaActual);

                case '=':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.IGUAL_IGUAL, "==", lineaActual);
                    }
                    return new Token(TipoToken.IGUAL, "=", lineaActual);

                case '>':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MAYOR_IGUAL, ">=", lineaActual);
                    }
                    return new Token(TipoToken.MAYOR, ">", lineaActual);

                case '<':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MENOR_IGUAL, "<=", lineaActual);
                    }
                    return new Token(TipoToken.MENOR, "<", lineaActual);

                case '&':
                    avanzar();
                    if (caracterActual == '&') {
                        avanzar();
                        return new Token(TipoToken.Y, "&&", lineaActual);
                    }
                    throw new RuntimeException("Se esperaba '&&' en línea " + lineaActual);

                case '|':
                    avanzar();
                    if (caracterActual == '|') {
                        avanzar();
                        return new Token(TipoToken.O, "||", lineaActual);
                    }
                    throw new RuntimeException("Se esperaba '||' en línea " + lineaActual);

                case '+':
                    avanzar();
                    if (caracterActual == '+') {
                        avanzar();
                        return new Token(TipoToken.INCREMENTO, "++", lineaActual);
                    }
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MAS_IGUAL, "+=", lineaActual);
                    }
                    return new Token(TipoToken.MAS, "+", lineaActual);

                case '-':
                    avanzar();
                    if (caracterActual == '-') {
                        avanzar();
                        return new Token(TipoToken.DECREMENTO, "--", lineaActual);
                    }
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MENOS_IGUAL, "-=", lineaActual);
                    }
                    return new Token(TipoToken.MENOS, "-", lineaActual);

                case '*':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.MULT_IGUAL, "*=", lineaActual);
                    }
                    return new Token(TipoToken.MULTIPLICACION, "*", lineaActual);

                case '/':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.DIV_IGUAL, "/=", lineaActual);
                    }
                    // Comentario de línea: // ...
                    if (caracterActual == '/') {
                        while (caracterActual != '\n' && caracterActual != '\0') {
                            avanzar();
                        }
                        continue; // saltar el comentario y seguir tokenizando
                    }
                    return new Token(TipoToken.DIVISION, "/", lineaActual);

                case '?':
                    avanzar();
                    return new Token(TipoToken.INTERROGACION, "?", lineaActual);
                case ',':
                    avanzar();
                    return new Token(TipoToken.COMA, ",", lineaActual);
                case '[':
                    avanzar();
                    return new Token(TipoToken.CORCHETE_IZQ, "[", lineaActual);
                case ']':
                    avanzar();
                    return new Token(TipoToken.CORCHETE_DER, "]", lineaActual);

                default:
                    throw new RuntimeException("Caracter no reconocido: '" + caracterActual + "' en línea " + linea);
            }
        }

        return new Token(TipoToken.EOF, "", linea);
    }

    public List<Token> tokenizar() {
        List<Token> tokens = new ArrayList<>();

        Token token = siguienteToken();
        while (token.getTipo() != TipoToken.EOF) {
            tokens.add(token);
            token = siguienteToken();
        }
        tokens.add(token); // Agregar EOF

        return tokens;
    }
}