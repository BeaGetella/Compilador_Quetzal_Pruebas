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

    // ════════════════════════════════════════════════════════════════
    //  DISPATCHER DE INSTRUCCIONES
    // ════════════════════════════════════════════════════════════════
    private void generarInstruccion(Nodo instruccion) {
        if (instruccion instanceof DeclaracionVariable) {
            generarDeclaracionVariable((DeclaracionVariable) instruccion);
        } else if (instruccion instanceof LlamadaFuncion) {
            generarLlamadaConsola((LlamadaFuncion) instruccion);
        } else if (instruccion instanceof BuclePara) {
            // ── NUEVO ──────────────────────────────────────────────
            generarBuclePara((BuclePara) instruccion);
        } else if (instruccion instanceof Expresion) {
            // Asignaciones sueltas, i++, etc.
            generarExpresionComoInstruccion((Expresion) instruccion);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GENERACIÓN DEL BUCLE PARA
    //
    //  Bytecode equivalente a:
    //    para (entero var i = 0; i < 5; i++) { cuerpo }
    //
    //  Se traduce a:
    //    inicializacion          → entero var i = 0
    //    label_inicio:
    //      condicion             → i < 5
    //      si FALSO → saltar a label_fin
    //      cuerpo
    //      incremento            → i++
    //      GOTO label_inicio
    //    label_fin:
    // ════════════════════════════════════════════════════════════════
    private void generarBuclePara(BuclePara bucle) {
        // 1. Inicialización (ejecuta una sola vez antes del bucle)
        generarDeclaracionVariable(bucle.getInicializacion());

        // 2. Labels de control
        Label labelInicio = new Label();
        Label labelFin    = new Label();

        // 3. Marcar inicio del bucle
        methodVisitor.visitLabel(labelInicio);

        // 4. Evaluar condición → si es FALSA, saltar al fin
        generarCondicionSalto(bucle.getCondicion(), labelFin);

        // 5. Generar cuerpo del bucle
        for (Nodo instruccion : bucle.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        // 6. Generar incremento (i++, i--, i += 1, etc.)
        generarExpresionComoInstruccion(bucle.getIncremento());

        // 7. Volver al inicio
        methodVisitor.visitJumpInsn(GOTO, labelInicio);

        // 8. Label de fin (aquí se aterriza cuando la condición es falsa)
        methodVisitor.visitLabel(labelFin);
    }

    /**
     * Evalúa una expresión booleana/relacional y emite un salto condicional
     * hacia labelFalso cuando la condición NO se cumple.
     *
     * Ejemplo: condicion "i < 5"
     *   → genera ILOAD i, ICONST 5, IF_ICMPGE labelFalso
     *   (si i >= 5 → salta al fin, es decir, sale del bucle)
     */
    private void generarCondicionSalto(Expresion condicion, Label labelFalso) {
        if (condicion instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) condicion;
            String operador = op.getOperador();

            // Operadores relacionales: generar ambos operandos y saltar si la condición es FALSA
            switch (operador) {
                case "<":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPGE, labelFalso); // sale si izq >= der
                    return;
                case "<=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPGT, labelFalso); // sale si izq > der
                    return;
                case ">":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPLE, labelFalso); // sale si izq <= der
                    return;
                case ">=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPLT, labelFalso); // sale si izq < der
                    return;
                case "==":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPNE, labelFalso); // sale si son distintos
                    return;
                case "!=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPEQ, labelFalso); // sale si son iguales
                    return;
            }
        }

        // Fallback: evaluar como expresión booleana (0 = falso)
        generarExpresion(condicion);
        methodVisitor.visitJumpInsn(IFEQ, labelFalso);
    }

    /**
     * Genera una expresión que se usa como INSTRUCCIÓN (su resultado se descarta).
     * Útil para: i++, i--, i = i + 1, i += 1
     */
    private void generarExpresionComoInstruccion(Expresion expr) {
        if (expr instanceof OperacionUnaria) {
            generarIncrementoDecremento((OperacionUnaria) expr);
        } else if (expr instanceof Asignacion) {
            generarAsignacion((Asignacion) expr);
        }
        // Si hubiera otros casos, se pueden agregar aquí
    }

    /**
     * Genera bytecode para i++ o i-- usando la instrucción IINC de JVM,
     * que es la más eficiente para incrementar/decrementar variables locales enteras.
     *
     * IINC índice, delta  →  variable[índice] += delta
     */
    private void generarIncrementoDecremento(OperacionUnaria unaria) {
        String operador = unaria.getOperador();
        Expresion operando = unaria.getOperando();

        if (operando instanceof Variable) {
            String nombre = ((Variable) operando).getNombre();
            int indice = tabla.obtenerIndice(nombre);
            TipoDato tipo = tabla.obtenerTipo(nombre);

            if (tipo == TipoDato.ENTERO || tipo == TipoDato.NUMERO) {
                if (operador.equals("++")) {
                    methodVisitor.visitIincInsn(indice, 1);   // i++  →  IINC i, 1
                } else if (operador.equals("--")) {
                    methodVisitor.visitIincInsn(indice, -1);  // i--  →  IINC i, -1
                }
            }
        }
    }

    /**
     * Genera bytecode para asignaciones: i = expr, i += expr, i -= expr, etc.
     */
    private void generarAsignacion(Asignacion asignacion) {
        String nombre    = asignacion.getNombre();
        String operador  = asignacion.getOperador();
        int indice       = tabla.obtenerIndice(nombre);
        TipoDato tipo    = tabla.obtenerTipo(nombre);

        if (operador.equals("=")) {
            // Asignación simple
            generarExpresion(asignacion.getValor());
        } else {
            // Asignación compuesta: cargar valor actual, calcular, guardar
            if (tipo == TipoDato.TEXTO) {
                methodVisitor.visitVarInsn(ALOAD, indice);
            } else {
                methodVisitor.visitVarInsn(ILOAD, indice);
            }
            generarExpresion(asignacion.getValor());

            switch (operador) {
                case "+=": methodVisitor.visitInsn(IADD); break;
                case "-=": methodVisitor.visitInsn(ISUB); break;
                case "*=": methodVisitor.visitInsn(IMUL); break;
                case "/=": methodVisitor.visitInsn(IDIV); break;
                case "%=": methodVisitor.visitInsn(IREM); break;
            }
        }

        // Guardar resultado
        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ASTORE, indice);
        } else {
            methodVisitor.visitVarInsn(ISTORE, indice);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Código existente sin cambios
    // ════════════════════════════════════════════════════════════════

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
        } else if (expresion instanceof OperacionUnaria) {
            // Para cuando i++ se usa como VALOR (no como instrucción)
            generarIncrementoDecremento((OperacionUnaria) expresion);
            // Después del IINC, cargamos el valor actualizado
            Expresion operando = ((OperacionUnaria) expresion).getOperando();
            if (operando instanceof Variable) {
                generarVariable((Variable) operando);
            }
        } else if (expresion instanceof Asignacion) {
            generarAsignacion((Asignacion) expresion);
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
        int indice    = tabla.obtenerIndice(nombre);
        TipoDato tipo = tabla.obtenerTipo(nombre);

        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ALOAD, indice);
        } else {
            methodVisitor.visitVarInsn(ILOAD, indice);
        }
    }

    private void generarOperacionBinaria(OperacionBinaria operacion) {
        String operador = operacion.getOperador();

        if (operador.equals("y") || operador.equals("&&")) {
            Label falso = new Label();
            Label fin   = new Label();
            generarExpresion(operacion.getIzquierda());
            methodVisitor.visitJumpInsn(IFEQ, falso);
            generarExpresion(operacion.getDerecha());
            methodVisitor.visitJumpInsn(IFEQ, falso);
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
            methodVisitor.visitJumpInsn(IFNE, verdadero);
            generarExpresion(operacion.getDerecha());
            methodVisitor.visitJumpInsn(IFNE, verdadero);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitJumpInsn(GOTO, fin);
            methodVisitor.visitLabel(verdadero);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitLabel(fin);
            return;
        }

        generarExpresion(operacion.getIzquierda());
        generarExpresion(operacion.getDerecha());

        Label verdadero = new Label();
        Label fin       = new Label();

        switch (operador) {
            case "+":  methodVisitor.visitInsn(IADD); return;
            case "-":  methodVisitor.visitInsn(ISUB); return;
            case "*":  methodVisitor.visitInsn(IMUL); return;
            case "/":  methodVisitor.visitInsn(IDIV); return;
            case "%":  methodVisitor.visitInsn(IREM); return;

            case ">":  methodVisitor.visitJumpInsn(IF_ICMPGT, verdadero); break;
            case "<":  methodVisitor.visitJumpInsn(IF_ICMPLT, verdadero); break;
            case ">=": methodVisitor.visitJumpInsn(IF_ICMPGE, verdadero); break;
            case "<=": methodVisitor.visitJumpInsn(IF_ICMPLE, verdadero); break;
            case "==": methodVisitor.visitJumpInsn(IF_ICMPEQ, verdadero); break;
            case "!=": methodVisitor.visitJumpInsn(IF_ICMPNE, verdadero); break;

            default:
                throw new RuntimeException("Operador no soportado: " + operador);
        }

        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitJumpInsn(GOTO, fin);
        methodVisitor.visitLabel(verdadero);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitLabel(fin);
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

        classWriter.visit(V11, ACC_PUBLIC, nombreClase, null, "java/lang/Object", null);

        methodVisitor = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null
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
            if (tipo != TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
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

            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO) {
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
}