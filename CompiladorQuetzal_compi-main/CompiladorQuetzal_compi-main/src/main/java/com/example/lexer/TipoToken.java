package com.example.lexer;

public enum TipoToken {
    // ===== TIPOS DE DATOS =====
    TIPO_VACIO,      // vacio
    TIPO_ENTERO,     // entero
    TIPO_NUMERO,     // numero
    TIPO_TEXTO,      // texto
    TIPO_LOG,        // log
    TIPO_LISTA,      // lista
    TIPO_JSN,        // jsn

    // ===== MODIFICADORES =====
    VAR,             // var
    PUBLICO,         // publico
    PRIVADO,         // privado
    LIBRE,           // libre
    AMBIENTE,        // ambiente

    // ===== CONTROL DE FLUJO =====
    SI,              // si
    SINO,            // sino
    PARA,            // para
    MIENTRAS,        // mientras
    HACER,           // hacer
    ROMPER,          // romper
    CONTINUAR,       // continuar
    EN,              // en
    CADA,            // cada
    RETORNAR,        // retornar

    // ===== OBJETOS =====
    OBJETO,          // objeto
    NUEVO,           // nuevo
    CONSTRUCTOR,     // constructor

    // ===== ASINCRONIA (RESERVADO) =====
    ASINCRONO,       // asincrono
    ESPERAR,         // esperar

    // ===== EXCEPCIONES =====
    INTENTAR,        // intentar
    CAPTURAR,        // capturar
    FINALMENTE,      // finalmente
    LANZAR,          // lanzar
    EXCEPCION,       // excepcion

    // ===== MODULOS =====
    IMPORTAR,        // importar
    EXPORTAR,        // exportar
    DESDE,           // desde
    COMO,            // como

    // ===== LITERALES LOGICOS =====
    VERDADERO,       // verdadero
    FALSO,           // falso
    NULO,            // nulo
    Y,               // y
    O,               // o
    NO,              // no

    // ===== OTROS PROTEGIDOS =====
    MUT,             // mut
    DE,              // de
    ES,              // es

    // ===== YA EXISTENTES =====
    CONSOLA,
    IDENTIFICADOR,
    LITERAL_NUMERO,
    LITERAL_STRING,
    STRING_INTERPOLADO,
    IGUAL,
    MAS,
    MENOS,
    MULTIPLICACION,
    DIVISION,
    PARENTESIS_IZQ,
    PARENTESIS_DER,
    CORCHETE_IZQ,    // [
    CORCHETE_DER,    // ]
    PUNTO,
    COMA,   // ,
    DOS_PUNTOS,
    PUNTO_COMA,      // ;
    LLAVE_IZQ,       // {
    LLAVE_DER,       // }
    NUEVA_LINEA,
    EOF,

    // ===== OPERADORES ARITMÉTICOS =====
    MODULO,          // %

    // ===== OPERADORES RELACIONALES =====
    IGUAL_IGUAL,     // ==
    DIFERENTE,       // !=
    MAYOR,           // >
    MENOR,           // <
    MAYOR_IGUAL,     // >=
    MENOR_IGUAL,     // <=

    // ===== ASIGNACIÓN COMPUESTA =====
    MAS_IGUAL,       // +=
    MENOS_IGUAL,     // -=
    MULT_IGUAL,      // *=
    DIV_IGUAL,       // /=
    MOD_IGUAL,       // %=

    // ===== INCREMENTO Y DECREMENTO =====
    INCREMENTO,      // ++
    DECREMENTO,      // --

    // ===== TERNARIO =====
    INTERROGACION,   // ?
}