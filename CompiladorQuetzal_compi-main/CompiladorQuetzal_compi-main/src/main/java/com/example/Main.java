package com.example;

import com.example.lexer.Lexer;
import com.example.lexer.Token;
import com.example.lexer.TipoToken;
import com.example.parser.Parser;
import com.example.parser.VisualizadorAST;
import com.example.parser.ast.Programa;
import com.example.generador.GeneradorBytecode;
import com.example.semantico.AnalizadorSemantico;
import com.example.semantico.gestores.TablaSimbolos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso:");
            System.out.println("  Compilar:     java -jar compilador-quetzal.jar archivo.qz");
            System.out.println("  Solo tokens:  java -jar compilador-quetzal.jar --tokens archivo.qz");
            System.out.println("  Solo AST:     java -jar compilador-quetzal.jar --ast archivo.qz");
            System.exit(1);
        }

        // Detectar flags
        boolean mostrarTokens = false;
        boolean mostrarAST = false;
        int archivoIndex = 0;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--tokens") || args[i].equals("--lexer")) {
                mostrarTokens = true;
            } else if (args[i].equals("--ast")) {
                mostrarAST = true;
            } else if (args[i].endsWith(".qz")) {
                archivoIndex = i;
            }
        }

        try {
            String codigo = Files.readString(Paths.get(args[archivoIndex]));

            // ========== FASE 1: ANALISIS LEXICO ==========
            Lexer lexer = new Lexer(codigo);
            List<Token> tokens = lexer.tokenizar();

            // Si solo quieren tokens
            if (mostrarTokens) {
                System.out.println("=== COMPILADOR QUETZAL → JVM ===");
                System.out.println("Archivo: " + args[archivoIndex]);
                System.out.println("\n--- FASE 1: ANALISIS LEXICO ---");
                System.out.println("Tokens generados: " + tokens.size());
                mostrarTokensJSON(tokens);
                return;
            }

            // ========== FASE 2: ANALISIS SINTACTICO ==========
            Parser parser = new Parser(tokens);
            Programa ast = parser.parsear();

            // Si solo quieren AST
            if (mostrarAST) {
                VisualizadorAST.imprimir(ast);
                return;
            }

            // ========== COMPILACION COMPLETA ==========
            System.out.println("=== COMPILADOR QUETZAL → JVM ===");
            System.out.println("Archivo: " + args[archivoIndex]);
            System.out.println("Codigo Quetzal:");
            System.out.println("─────────────────────");
            System.out.println(codigo);
            System.out.println("─────────────────────");

            System.out.println("\n--- FASE 1: ANALISIS LEXICO ---");
            System.out.println("Tokens generados: " + tokens.size());

            System.out.println("\n--- FASE 2: ANALISIS SINTACTICO ---");
            System.out.println("AST generado correctamente");

            // ========== FASE 3: ANALISIS SEMANTICO ==========
            System.out.println("\n--- FASE 3: ANALISIS SEMANTICO ---");
            AnalizadorSemantico analizador = new AnalizadorSemantico();
            TablaSimbolos tabla = analizador.analizar(ast);
            System.out.println("Analisis semantico completado");

            // ========== FASE 4: GENERACION DE BYTECODE ==========
            System.out.println("\n--- FASE 4: GENERACION DE BYTECODE ---");

            String nombreArchivo = Paths.get(args[archivoIndex]).getFileName().toString();
            String nombreClase = nombreArchivo.replace(".qz", "");
            nombreClase = nombreClase.substring(0, 1).toUpperCase() + nombreClase.substring(1);

            GeneradorBytecode generador = new GeneradorBytecode(nombreClase, tabla);

            Files.createDirectories(Paths.get("output"));
            String rutaSalida = "output/" + nombreClase + ".class";

            generador.generarConImpresion(ast, rutaSalida);

            System.out.println("Bytecode generado: " + rutaSalida);
            System.out.println("\n=== COMPILACION EXITOSA ===");
            System.out.println("Para ejecutar:");
            System.out.println("  cd output");
            System.out.println("  java " + nombreClase);

        } catch (IOException e) {
            System.err.println("ERROR al leer el archivo: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("ERROR de compilacion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Mostrar tokens en formato JSON
    private static void mostrarTokensJSON(List<Token> tokens) {
        System.out.println("TOKENS EN FORMATO JSON:");
        System.out.println("─────────────────────────────");
        System.out.println("[");

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // Saltar tokens de nueva linea y EOF para JSON mas limpio
            if (token.getTipo() == TipoToken.NUEVA_LINEA || token.getTipo() == TipoToken.EOF) {
                continue;
            }

            System.out.println("    {");
            System.out.println("        \"tipo\": \"" + token.getTipo() + "\",");
            System.out.println("        \"valor\": \"" + escaparJSON(token.getValor()) + "\",");
            System.out.println("        \"linea\": " + token.getLinea());
            System.out.print("    }");

            // Coma si no es el ultimo
            if (i < tokens.size() - 1) {
                System.out.println(",");
            } else {
                System.out.println();
            }
        }

        System.out.println("]");
        System.out.println("─────────────────────────────");
        System.out.println("Total de tokens: " + tokens.size());
    }

    // Escapar caracteres especiales para JSON
    private static String escaparJSON(String texto) {
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}