package com.example.parser.ast;

import java.util.List;

// Representa el programa completo (raíz del AST)
public class Programa extends Nodo {
    private List<Nodo> instrucciones; //Almacena la secuencia ordenada de instrucciones
                                        //    Cada elemento es un Nodo
/**
    digamos que es
    numero b = 3
    consola.mostrar("Hola")
    La lista contendria esto
    instrucciones = [
    DeclaracionVariable("numero", "b", LiteralNumero(3)),
    LlamadaFuncion("consola", "mostrar", [LiteralString("Hola")])
            ]
**/
    public Programa(List<Nodo> instrucciones) {  //Es un constructor que inicializa con una lista de instrucciones
        this.instrucciones = instrucciones;
    }


    public List<Nodo> getInstrucciones() { // solo lee las instucciones o solo las obtiene
        return instrucciones;
    }

    @Override //Generar representación textual para depuración
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Programa{\n");
        for (Nodo instruccion : instrucciones) {
            sb.append("  ").append(instruccion).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}