package com.example.lexer;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private String codigo;
    private int posicion;
    private int linea;
    private char caracterActual;

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

        while (Character.isDigit(caracterActual)) {
            numero.append(caracterActual);
            avanzar();
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

        TipoToken tipo;
        switch (valor) {
            case "entero":
                tipo = TipoToken.TIPO_ENTERO;
                break;
            case "numero":
                tipo = TipoToken.TIPO_NUMERO;
                break;
            case "texto":
                tipo = TipoToken.TIPO_TEXTO;
                break;
            case "consola":
                tipo = TipoToken.CONSOLA;
                break;
            case "si":
                tipo = TipoToken.SI;
                break;
            case "sino":
                tipo = TipoToken.SINO;
                break;
            default:
                tipo = TipoToken.IDENTIFICADOR;
                break;
        }

        return new Token(tipo, valor, lineaInicio);
    }

    private Token leerString() {
        StringBuilder string = new StringBuilder();
        int lineaInicio = linea;

        avanzar();

        while (caracterActual != '"' && caracterActual != '\0') {
            string.append(caracterActual);
            avanzar();
        }

        if (caracterActual == '"') {
            avanzar();
        }

        return new Token(TipoToken.LITERAL_STRING, string.toString(), lineaInicio);
    }

    private Token leerStringInterpolado() {
        StringBuilder string = new StringBuilder();
        int lineaInicio = linea;

        avanzar();

        while (caracterActual != '"' && caracterActual != '\0') {
            string.append(caracterActual);
            avanzar();
        }

        if (caracterActual == '"') {
            avanzar();
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

            if (Character.isDigit(caracterActual)) {
                return leerNumero();
            }

            if (Character.isLetter(caracterActual) || caracterActual == '_') {
                if (caracterActual == 't' && verSiguiente() == '"') {
                    avanzar();
                    return leerStringInterpolado();
                }
                return leerIdentificador();
            }

            if (caracterActual == '"') {
                return leerString();
            }

            int lineaActual = linea;
            switch (caracterActual) {
                case '=':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.IGUAL_IGUAL, "==", lineaActual);
                    }
                    return new Token(TipoToken.IGUAL, "=", lineaActual);
                case '+':
                    avanzar();
                    return new Token(TipoToken.MAS, "+", lineaActual);
                case '-':
                    avanzar();
                    return new Token(TipoToken.MENOS, "-", lineaActual);
                case '*':
                    avanzar();
                    return new Token(TipoToken.MULTIPLICACION, "*", lineaActual);
                case '/':
                    avanzar();
                    return new Token(TipoToken.DIVISION, "/", lineaActual);
                case '(':
                    avanzar();
                    return new Token(TipoToken.PARENTESIS_IZQ, "(", lineaActual);
                case ')':
                    avanzar();
                    return new Token(TipoToken.PARENTESIS_DER, ")", lineaActual);
                case '.':
                    avanzar();
                    return new Token(TipoToken.PUNTO, ".", lineaActual);
                case ':':
                    avanzar();
                    return new Token(TipoToken.DOS_PUNTOS, ":", lineaActual);
                case '{':
                    avanzar();
                    return new Token(TipoToken.LLAVE_IZQ, "{", lineaActual);
                case '}':
                    avanzar();
                    return new Token(TipoToken.LLAVE_DER, "}", lineaActual);
                case '?':
                    avanzar();
                    return new Token(TipoToken.INTERROGACION, "?", lineaActual);
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
                case '!':
                    avanzar();
                    if (caracterActual == '=') {
                        avanzar();
                        return new Token(TipoToken.DIFERENTE, "!=", lineaActual);
                    }
                    throw new RuntimeException("Caracter no reconocido: '!' en línea " + linea);
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
        tokens.add(token);

        return tokens;
    }
}