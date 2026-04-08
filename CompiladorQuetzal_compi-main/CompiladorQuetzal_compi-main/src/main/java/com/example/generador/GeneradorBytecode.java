package com.example.generador;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class GeneradorBytecode {
    private ClassWriter classWriter;
    private MethodVisitor methodVisitor;
    private Map<String, Integer> variables;
    private Map<String, String> tiposVariables;
    private int contadorVariables;
    private TablaSimbolos tabla;
    private String nombreClase;

    public GeneradorBytecode(String nombreClase, TablaSimbolos tabla) {
        this.nombreClase = nombreClase;
        this.tabla = tabla;
    }

    public void generar(Programa programa, String archivoSalida) throws IOException {
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        classWriter.visit(
                V11,
                ACC_PUBLIC,
                nombreClase,
                null,
                "java/lang/Object",
                null
        );

        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null
        );

        methodVisitor.visitCode();

        for (Nodo instruccion : programa.getInstrucciones()) {
            generarInstruccion(instruccion);
        }

        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        classWriter.visitEnd();

        byte[] bytecode = classWriter.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            fos.write(bytecode);
        }

        System.out.println("Archivo generado: " + archivoSalida);
    }

    private void generarDeclaracionVariable(DeclaracionVariable decl) {
        int indiceVariable = tabla.obtenerIndice(decl.getNombre());
        generarExpresion(decl.getValor());
        TipoDato tipo = tabla.obtenerTipo(decl.getNombre());
        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ASTORE, indiceVariable);
        } else {
            methodVisitor.visitVarInsn(ISTORE, indiceVariable);
        }
    }

    private void generarExpresion(Expresion expresion) {
        if (expresion instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expresion);
        } else if (expresion instanceof Variable) {
            generarVariable((Variable) expresion);
        } else if (expresion instanceof OperacionBinaria) {
            generarOperacionBinaria((OperacionBinaria) expresion);
        } else if (expresion instanceof ConversionNumero) {
            generarConversionNumero((ConversionNumero) expresion);
        } else if (expresion instanceof LlamadaFuncion) {
            generarLlamadaConsola((LlamadaFuncion) expresion);
        }
    }

    private void generarLiteralNumero(LiteralNumero literal) {
        int valor = literal.getValor();
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

    private void generarVariable(Variable variable) {
        String nombre = variable.getNombre();
        int indice = tabla.obtenerIndice(nombre);
        TipoDato tipo = tabla.obtenerTipo(nombre);
        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ALOAD, indice);
        } else {
            methodVisitor.visitVarInsn(ILOAD, indice);
        }
    }

    private void generarOperacionBinaria(OperacionBinaria operacion) {
        generarExpresion(operacion.getIzquierda());
        generarExpresion(operacion.getDerecha());

        String operador = operacion.getOperador();
        switch (operador) {
            case "+": methodVisitor.visitInsn(IADD); break;
            case "-": methodVisitor.visitInsn(ISUB); break;
            case "*": methodVisitor.visitInsn(IMUL); break;
            case "/": methodVisitor.visitInsn(IDIV); break;
            default:
                throw new RuntimeException("Operador no soportado: " + operador);
        }
    }

    private void generarConversionNumero(ConversionNumero conversion) {
        generarExpresion(conversion.getExpresion());
        methodVisitor.visitMethodInsn(
                INVOKESTATIC,
                "java/lang/Double",
                "parseDouble",
                "(Ljava/lang/String;)D",
                false
        );
        methodVisitor.visitInsn(D2I);
    }

    public void generarConImpresion(Programa programa, String archivoSalida) throws IOException {
        classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        classWriter.visit(
                V11,
                ACC_PUBLIC,
                nombreClase,
                null,
                "java/lang/Object",
                null
        );

        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null
        );

        methodVisitor.visitCode();

        for (Nodo instruccion : programa.getInstrucciones()) {
            generarInstruccion(instruccion);
        }

        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        classWriter.visitEnd();

        byte[] bytecode = classWriter.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            fos.write(bytecode);
        }

        System.out.println("Archivo generado: " + archivoSalida);
    }

    private void generarLlamadaConsola(LlamadaFuncion llamada) {
        String metodo = llamada.getMetodo();

        if (metodo.equals("mostrar")) {
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            if (!llamada.getArgumentos().isEmpty()) {
                generarExpresionString(llamada.getArgumentos().get(0));
            }
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

        } else if (metodo.equals("pedir")) {
            if (!llamada.getArgumentos().isEmpty()) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generarExpresionString(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            }
            methodVisitor.visitTypeInsn(NEW, "java/util/Scanner");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
        }
    }

    private void generarExpresionString(Expresion expr) {
        if (expr instanceof LiteralString) {
            methodVisitor.visitLdcInsn(((LiteralString) expr).getValor());

        } else if (expr instanceof Concatenacion) {
            generarConcatenacion((Concatenacion) expr);

        } else if (expr instanceof ConversionTexto) {
            generarExpresion(((ConversionTexto) expr).getExpresion());
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);

        } else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
        }
    }

    private void generarConcatenacion(Concatenacion concat) {
        methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        agregarAStringBuilder(concat.getIzquierda());
        agregarAStringBuilder(concat.getDerecha());
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void agregarAStringBuilder(Expresion expr) {
        if (expr instanceof LiteralString) {
            methodVisitor.visitLdcInsn(((LiteralString) expr).getValor());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof ConversionTexto) {
            generarExpresion(((ConversionTexto) expr).getExpresion());
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            String tipo = tiposVariables.get(nombre);
            if (tipo != null && tipo.equals("texto")) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof Concatenacion) {
            agregarAStringBuilder(((Concatenacion) expr).getIzquierda());
            agregarAStringBuilder(((Concatenacion) expr).getDerecha());
        }
    }

    // NUEVO - Generar condicional si/sino si/sino
    private void generarCondicional(Condicional condicional) {
        Label labelFin = new Label();

        for (int i = 0; i < condicional.ramas.size(); i++) {
            Condicional.Rama rama = condicional.ramas.get(i);

            if (rama.condicion == null) {
                // Bloque "sino" final — sin condición, solo ejecuta
                for (Nodo instruccion : rama.cuerpo) {
                    generarInstruccion(instruccion);
                }
            } else {
                Label labelSiguiente = new Label();

                // Evaluar condición, si es falsa salta al siguiente bloque
                generarCondicion(rama.condicion, labelSiguiente);

                // Cuerpo del si / sino si
                for (Nodo instruccion : rama.cuerpo) {
                    generarInstruccion(instruccion);
                }

                // Saltar al final después de ejecutar este bloque
                methodVisitor.visitJumpInsn(GOTO, labelFin);

                // Etiqueta para cuando la condición es falsa
                methodVisitor.visitLabel(labelSiguiente);
            }
        }

        methodVisitor.visitLabel(labelFin);
    }

    //  NUEVO - Evaluar condición y saltar si es falsa
    private void generarCondicion(Expresion condicion, Label labelFalso) {
        if (condicion instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) condicion;
            generarExpresion(op.getIzquierda());
            generarExpresion(op.getDerecha());

            switch (op.getOperador()) {
                case ">":  methodVisitor.visitJumpInsn(IF_ICMPLE, labelFalso); break;
                case "<":  methodVisitor.visitJumpInsn(IF_ICMPGE, labelFalso); break;
                case ">=": methodVisitor.visitJumpInsn(IF_ICMPLT, labelFalso); break;
                case "<=": methodVisitor.visitJumpInsn(IF_ICMPGT, labelFalso); break;
                case "==": methodVisitor.visitJumpInsn(IF_ICMPNE, labelFalso); break;
                case "!=": methodVisitor.visitJumpInsn(IF_ICMPEQ, labelFalso); break;
                default:
                    throw new RuntimeException("Operador de comparación no soportado: " + op.getOperador());
            }
        }
    }

    // NUEVO - Despachar cualquier tipo de instrucción
    private void generarInstruccion(Nodo instruccion) {
        if (instruccion instanceof DeclaracionVariable) {
            generarDeclaracionVariable((DeclaracionVariable) instruccion);
        } else if (instruccion instanceof LlamadaFuncion) {
            generarLlamadaConsola((LlamadaFuncion) instruccion);
        } else if (instruccion instanceof Condicional) {
            generarCondicional((Condicional) instruccion);
        }
    }
}