package com.example;

import com.example.lexer.Lexer;
import com.example.lexer.Token;
import com.example.lexer.TipoToken;
import com.example.parser.Parser;
import com.example.parser.ast.Programa;
import com.example.generador.GeneradorBytecode;
import com.example.semantico.AnalizadorSemantico;
import com.example.semantico.gestores.TablaSimbolos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    //
    private static void mostrarTokensJSON(List<Token> tokens) {
        System.out.println("\nTOKENS EN FORMATO JSON:");
        System.out.println("─────────────────────────────");
        System.out.println("[");

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            // Saltar NUEVA_LINEA y EOF en la visualización (opcional)
            if (token.getTipo() == TipoToken.NUEVA_LINEA || token.getTipo() == TipoToken.EOF) {
                continue;
            }

            System.out.println("    {");
            System.out.println("        \"tipo\": \"" + token.getTipo() + "\",");
            System.out.println("        \"valor\": \"" + escaparJSON(token.getValor()) + "\",");
            System.out.println("        \"linea\": " + token.getLinea());
            System.out.print("    }");

            // Coma entre elementos (excepto el último)
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

    // Helper: Escapar caracteres especiales para JSON
    private static String escaparJSON(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static void main(String[] args) {
        // VERIFICAR ARGUMENTOS
        if (args.length == 0) {
            System.out.println("Error: No se especificó un archivo");
            System.out.println("\nUso:");
            System.out.println("  Compilar:     java -jar compilador-quetzal.jar <archivo.qz>");
            System.out.println("  Solo tokens:  java -jar compilador-quetzal.jar --tokens <archivo.qz>");
            System.out.println("  Solo tokens:  java -jar compilador-quetzal.jar --lexer <archivo.qz>");
            System.out.println("\nEjemplos:");
            System.out.println("  java -jar target\\compilador-quetzal.jar ejemplos\\suma.qz");
            System.out.println("  java -jar target\\compilador-quetzal.jar --tokens ejemplos\\suma.qz");
            System.exit(1);
        }

        // VERIFICAR SI ES MODO SOLO TOKENS
        boolean soloTokens = false;
        String archivoPath = args[0];

        if (args[0].equals("--tokens") || args[0].equals("--lexer")) {
            if (args.length < 2) {
                System.out.println("Error: Falta especificar el archivo");
                System.out.println("Uso: java -jar compilador-quetzal.jar --tokens <archivo.qz>");
                System.exit(1);
            }
            soloTokens = true;
            archivoPath = args[1];
        }

        // Banner
        System.out.println("=== COMPILADOR QUETZAL → JVM ===\n");
        System.out.println("Archivo: " + archivoPath + "\n");

        try {
            // Leer el archivo
            String codigo = Files.readString(Paths.get(archivoPath));

            System.out.println("Código Quetzal:");
            System.out.println("─────────────────────");
            System.out.println(codigo);
            System.out.println("─────────────────────\n");

            // ========== FASE 1: ANÁLISIS LÉXICO ==========
            System.out.println("--- FASE 1: ANÁLISIS LÉXICO ---");
            Lexer lexer = new Lexer(codigo);
            List<Token> tokens = lexer.tokenizar();
            System.out.println("Tokens generados: " + tokens.size());


            if (soloTokens) {
                mostrarTokensJSON(tokens);
                return;  // Terminar aquí, no compilar
            }

            // ========== FASE 2: ANÁLISIS SINTÁCTICO ==========
            System.out.println("\n--- FASE 2: ANÁLISIS SINTÁCTICO ---");
            Parser parser = new Parser(tokens);
            Programa ast = parser.parsear();
            System.out.println("AST generado correctamente");

            System.out.println("\n--- FASE 3: ANÁLISIS SEMÁNTICO ---");
            AnalizadorSemantico analizador = new AnalizadorSemantico();
            TablaSimbolos tabla = analizador.analizar(ast);
            System.out.println("Análisis semántico completado");

            // ========== FASE 3: GENERACIÓN DE BYTECODE ==========
            System.out.println("\n--- FASE 4: GENERACIÓN DE BYTECODE ---");

            // Extraer el nombre del archivo (sin extensión ni ruta)
            String nombreArchivo = Paths.get(archivoPath).getFileName().toString();
            String nombreClase = nombreArchivo.substring(0, nombreArchivo.lastIndexOf('.'));

            // Capitalizar primera letra
            nombreClase = nombreClase.substring(0, 1).toUpperCase() + nombreClase.substring(1);

            GeneradorBytecode generador = new GeneradorBytecode(nombreClase, tabla); // <- PASAR TABLA

// Crear directorio y generar
            Files.createDirectories(Paths.get("output"));
            String rutaSalida = "output/" + nombreClase + ".class";

            generador.generarConImpresion(ast, rutaSalida);




            System.out.println("Bytecode generado: " + rutaSalida);

            System.out.println("\n=== COMPILACIÓN EXITOSA ===");
            System.out.println("\nPara ejecutar:");
            System.out.println("  cd output");
            System.out.println("  java " + nombreClase);

        } catch (IOException e) {
            System.err.println("ERROR al leer el archivo: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println("ERROR de compilación: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}