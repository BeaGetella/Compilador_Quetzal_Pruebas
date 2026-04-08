package com.example.lexer;

public enum TipoToken {
    // Palabras reservadas
    TIPO_NUMERO,
    TIPO_TEXTO,
    TIPO_ENTERO,
    CONSOLA,

    // Identificadores y literales
    IDENTIFICADOR,
    LITERAL_NUMERO,
    LITERAL_STRING,
    STRING_INTERPOLADO,

    // Operadores aritméticos
    IGUAL,
    MAS,
    MENOS,
    MULTIPLICACION,
    DIVISION,

    // Símbolos
    PARENTESIS_IZQ,
    PARENTESIS_DER,
    PUNTO,
    DOS_PUNTOS,

    // Palabras reservadas para condicionales
    SI,
    SINO,

    // Operadores de comparación
    MAYOR,
    MENOR,
    MAYOR_IGUAL,
    MENOR_IGUAL,
    IGUAL_IGUAL,
    DIFERENTE,

    // Llaves
    LLAVE_IZQ,
    LLAVE_DER,

    // Operador ternario
    INTERROGACION,

    // Especiales
    NUEVA_LINEA,
    EOF
}