package com.example.lexer;

public class Token {
    private TipoToken tipo;  //tipotoken.entero
    private String valor;  //
    private int linea;

    public Token(TipoToken tipo, String valor, int linea) {
        this.tipo = tipo;
        this.valor = valor;
        this.linea = linea;
    }

    public TipoToken getTipo() {
        return tipo;
    }

    public String getValor() {
        return valor;
    }

    public int getLinea() {
        return linea;
    }

    @Override
    public String toString() {
        return "Token{" +
                "tipo=" + tipo +
                ", valor='" + valor + '\'' +
                ", linea=" + linea +
                '}';
    }
}