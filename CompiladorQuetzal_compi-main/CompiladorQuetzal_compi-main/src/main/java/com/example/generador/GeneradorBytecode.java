package com.example.generador;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;

import static org.objectweb.asm.Opcodes.*;

public class GeneradorBytecode {
    private ClassWriter classWriter;
    private MethodVisitor methodVisitor;
    private TablaSimbolos tabla;
    private String nombreClase;

    public GeneradorBytecode(String nombreClase, TablaSimbolos tabla) {
        this.nombreClase = nombreClase;
        this.tabla = tabla;
    }

    public void generar(Programa programa, String archivoSalida) throws IOException {
        // Crear el ClassWriter
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Definir la clase
        classWriter.visit(
                V11,                          // Versión de Java
                ACC_PUBLIC,                   // Modificadores
                nombreClase,                  // Nombre de la clase
                null,                         // Signature (generics)
                "java/lang/Object",           // Superclase
                null                          // Interfaces
        );

        // Crear el método main
        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,      // public static
                "main",                       // Nombre del método
                "([Ljava/lang/String;)V",    // Descriptor (args[], retorna void)
                null,                         // Signature
                null                          // Excepciones
        );

        methodVisitor.visitCode();

        // Generar código para cada instrucción del programa
        for (Nodo instruccion : programa.getInstrucciones()) {
            if (instruccion instanceof DeclaracionVariable) {
                generarDeclaracionVariable((DeclaracionVariable) instruccion);
            } else if (instruccion instanceof LlamadaFuncion) {
                generarLlamadaConsola((LlamadaFuncion) instruccion);
            }
        }

        // Retornar
        methodVisitor.visitInsn(RETURN);

        // Finalizar método
        methodVisitor.visitMaxs(0, 0); // Se calculan automáticamente
        methodVisitor.visitEnd();

        // Finalizar clase
        classWriter.visitEnd();

        // Escribir el archivo .class
        byte[] bytecode = classWriter.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            fos.write(bytecode);
        }

        System.out.println("Archivo generado: " + archivoSalida);
    }


    private void generarDeclaracionVariable(DeclaracionVariable decl) {
        // Obtener índice desde la tabla de símbolos
        int indiceVariable = tabla.obtenerIndice(decl.getNombre());

        // Generar código para la expresión del lado derecho
        generarExpresion(decl.getValor());

        // Guardar según el tipo (consultando la tabla)
        TipoDato tipo = tabla.obtenerTipo(decl.getNombre());
        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ASTORE, indiceVariable);
        } else {
            methodVisitor.visitVarInsn(ISTORE, indiceVariable);
        }
    }

    // ⭐ MODIFICADO - Ahora maneja LlamadaFuncion también
    private void generarExpresion(Expresion expresion) {
        if (expresion instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expresion);
        } else if (expresion instanceof Variable) {
            generarVariable((Variable) expresion);
        } else if (expresion instanceof OperacionBinaria) {
            generarOperacionBinaria((OperacionBinaria) expresion);
        } else if (expresion instanceof ConversionNumero) {
            generarConversionNumero((ConversionNumero) expresion);
        } else if (expresion instanceof LlamadaFuncion) { // ⭐ NUEVO
            generarLlamadaConsola((LlamadaFuncion) expresion);
        }
    }

    private void generarLiteralNumero(LiteralNumero literal) {
        int valor = literal.getValor();

        // Optimización: usar instrucciones específicas para valores pequeños
        if (valor >= -1 && valor <= 5) {
            methodVisitor.visitInsn(ICONST_0 + valor);
        } else if (valor >= -128 && valor <= 127) {
            methodVisitor.visitIntInsn(BIPUSH, valor);
        } else if (valor >= -32768 && valor <= 32767) {
            methodVisitor.visitIntInsn(SIPUSH, valor);
        } else {
            methodVisitor.visitLdcInsn(valor);
        }
    }

    // ⭐ MODIFICADO - Ahora maneja tipos (texto y numero)
    private void generarVariable(Variable variable) {
        String nombre = variable.getNombre();

        // Obtener información de la tabla
        int indice = tabla.obtenerIndice(nombre);
        TipoDato tipo = tabla.obtenerTipo(nombre);

        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ALOAD, indice);
        } else {
            methodVisitor.visitVarInsn(ILOAD, indice);
        }
    }

    private void generarOperacionBinaria(OperacionBinaria operacion) {
        String operador = operacion.getOperador();

        // Operadores lógicos cortocircuito: y, &&, o, ||
        // Se manejan antes de generar los operandos
        if (operador.equals("y") || operador.equals("&&")) {
            Label falso = new Label();
            Label fin   = new Label();
            generarExpresion(operacion.getIzquierda());
            methodVisitor.visitJumpInsn(IFEQ, falso);      // si izq == 0 → falso
            generarExpresion(operacion.getDerecha());
            methodVisitor.visitJumpInsn(IFEQ, falso);      // si der == 0 → falso
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitJumpInsn(GOTO, fin);
            methodVisitor.visitLabel(falso);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitLabel(fin);
            return;
        }

        if (operador.equals("o") || operador.equals("||")) {
            Label verdadero = new Label();
            Label fin       = new Label();
            generarExpresion(operacion.getIzquierda());
            methodVisitor.visitJumpInsn(IFNE, verdadero);  // si izq != 0 → verdadero
            generarExpresion(operacion.getDerecha());
            methodVisitor.visitJumpInsn(IFNE, verdadero);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitJumpInsn(GOTO, fin);
            methodVisitor.visitLabel(verdadero);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitLabel(fin);
            return;
        }

        // Para el resto: generar ambos operandos primero
        generarExpresion(operacion.getIzquierda());
        generarExpresion(operacion.getDerecha());

        Label verdadero = new Label();
        Label fin       = new Label();

        switch (operador) {
            // Aritméticos
            case "+":  methodVisitor.visitInsn(IADD); return;
            case "-":  methodVisitor.visitInsn(ISUB); return;
            case "*":  methodVisitor.visitInsn(IMUL); return;
            case "/":  methodVisitor.visitInsn(IDIV); return;
            case "%":  methodVisitor.visitInsn(IREM); return;

            // Relacionales → producen 0 o 1
            case ">":  methodVisitor.visitJumpInsn(IF_ICMPGT, verdadero); break;
            case "<":  methodVisitor.visitJumpInsn(IF_ICMPLT, verdadero); break;
            case ">=": methodVisitor.visitJumpInsn(IF_ICMPGE, verdadero); break;
            case "<=": methodVisitor.visitJumpInsn(IF_ICMPLE, verdadero); break;
            case "==": methodVisitor.visitJumpInsn(IF_ICMPEQ, verdadero); break;
            case "!=": methodVisitor.visitJumpInsn(IF_ICMPNE, verdadero); break;

            default:
                throw new RuntimeException("Operador no soportado: " + operador);
        }

        // Rama falso (no saltó)
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitJumpInsn(GOTO, fin);
        // Rama verdadero
        methodVisitor.visitLabel(verdadero);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitLabel(fin);
    }

    private void generarConversionNumero(ConversionNumero conversion) {
        // Generar la expresión (debe ser un String)
        generarExpresion(conversion.getExpresion());

        // Convertir String a double usando Double.parseDouble()
        methodVisitor.visitMethodInsn(
                INVOKESTATIC,
                "java/lang/Double",
                "parseDouble",
                "(Ljava/lang/String;)D",
                false
        );

        // Convertir double a int
        methodVisitor.visitInsn(D2I);
    }

    public void generarConImpresion(Programa programa, String archivoSalida) throws IOException {
        // Crear el ClassWriter
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Definir la clase
        classWriter.visit(
                V11,
                ACC_PUBLIC,
                nombreClase,
                null,
                "java/lang/Object",
                null
        );

        // Crear el método main
        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null
        );

        methodVisitor.visitCode();

        // Generar código para cada instrucción del programa
        for (Nodo instruccion : programa.getInstrucciones()) {
            if (instruccion instanceof DeclaracionVariable) {
                DeclaracionVariable decl = (DeclaracionVariable) instruccion;
                generarDeclaracionVariable(decl);
            } else if (instruccion instanceof LlamadaFuncion) {
                generarLlamadaConsola((LlamadaFuncion) instruccion);
            }
        }

        // Retornar
        methodVisitor.visitInsn(RETURN);

        // Finalizar método
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        // Finalizar clase
        classWriter.visitEnd();

        // Escribir el archivo .class
        byte[] bytecode = classWriter.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            fos.write(bytecode);
        }

        System.out.println("Archivo generado: " + archivoSalida);
    }

    // ⭐ MODIFICADO - Ahora soporta mostrar() y pedir()
    private void generarLlamadaConsola(LlamadaFuncion llamada) {
        String metodo = llamada.getMetodo();

        if (metodo.equals("mostrar")) {
            // System.out.println(...)
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

            if (!llamada.getArgumentos().isEmpty()) {
                generarExpresionString(llamada.getArgumentos().get(0));
            }

            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

        } else if (metodo.equals("pedir")) {
            // Mostrar el mensaje (si hay argumento)
            if (!llamada.getArgumentos().isEmpty()) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generarExpresionString(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            }

            // Leer input con Scanner
            // new Scanner(System.in)
            methodVisitor.visitTypeInsn(NEW, "java/util/Scanner");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);

            // scanner.nextLine()
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);

            // El String queda en el stack
        }
    }

    // Generar expresión de string
    private void generarExpresionString(Expresion expr) {
        if (expr instanceof LiteralString) {
            // String literal directo
            methodVisitor.visitLdcInsn(((LiteralString) expr).getValor());

        } else if (expr instanceof Concatenacion) {
            // Concatenación: usar StringBuilder
            generarConcatenacion((Concatenacion) expr);

        } else if (expr instanceof ConversionTexto) {
            // Convertir número a string
            generarExpresion(((ConversionTexto) expr).getExpresion());
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);

        } else if (expr instanceof Variable) {
            // Agregar variable
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);

            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            // Número literal
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
        }
    }

    // Generar concatenación de strings
    private void generarConcatenacion(Concatenacion concat) {
        // Crear StringBuilder
        methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

        // Agregar elementos
        agregarAStringBuilder(concat.getIzquierda());
        agregarAStringBuilder(concat.getDerecha());

        // Convertir a String
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    // Agregar expresión al StringBuilder
    private void agregarAStringBuilder(Expresion expr) {
        if (expr instanceof LiteralString) {
            // Agregar string
            methodVisitor.visitLdcInsn(((LiteralString) expr).getValor());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof ConversionTexto) {
            // Agregar número convertido
            generarExpresion(((ConversionTexto) expr).getExpresion());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof Variable) {
            // Agregar variable
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);

            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            // Agregar número literal
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof Concatenacion) {
            // Concatenación anidada
            agregarAStringBuilder(((Concatenacion) expr).getIzquierda());
            agregarAStringBuilder(((Concatenacion) expr).getDerecha());
        }
    }
}