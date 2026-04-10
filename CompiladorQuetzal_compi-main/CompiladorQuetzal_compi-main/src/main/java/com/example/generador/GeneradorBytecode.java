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

    private java.util.Deque<Label> pilaEtiquetasFin = new java.util.ArrayDeque<>();
    private java.util.Deque<Label> pilaEtiquetasInicio = new java.util.ArrayDeque<>();

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
            generarBuclePara((BuclePara) instruccion);
        } else if (instruccion instanceof Asignacion) {
            generarAsignacion((Asignacion) instruccion);
        } else if (instruccion instanceof OperacionUnaria) {
            generarOperacionUnaria((OperacionUnaria) instruccion);
        } else if (instruccion instanceof NodoSi) {
            generarSi((NodoSi) instruccion);
        } else if (instruccion instanceof NodoMientras) {
            generarMientras((NodoMientras) instruccion);
        } else if (instruccion instanceof NodoRomper) {
            generarRomper();
        } else if (instruccion instanceof NodoContinuar) {
            generarContinuar();
        } else if (instruccion instanceof NodoHacerMientras) {
            generarHacerMientras((NodoHacerMientras) instruccion);
        } else if (instruccion instanceof Expresion) {
            generarExpresionComoInstruccion((Expresion) instruccion);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GENERACIÓN DEL BUCLE PARA
    // ════════════════════════════════════════════════════════════════
    private void generarBuclePara(BuclePara bucle) {
        generarDeclaracionVariable(bucle.getInicializacion());

        Label labelInicio = new Label();
        Label labelFin    = new Label();

        pilaEtiquetasInicio.push(labelInicio);
        pilaEtiquetasFin.push(labelFin);

        methodVisitor.visitLabel(labelInicio);
        generarCondicionSalto(bucle.getCondicion(), labelFin);

        for (Nodo instruccion : bucle.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        generarExpresionComoInstruccion(bucle.getIncremento());
        methodVisitor.visitJumpInsn(GOTO, labelInicio);
        methodVisitor.visitLabel(labelFin);

        pilaEtiquetasInicio.pop();
        pilaEtiquetasFin.pop();
    }

    private void generarCondicionSalto(Expresion condicion, Label labelFalso) {
        if (condicion instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) condicion;
            String operador = op.getOperador();

            switch (operador) {
                case "<":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPGE, labelFalso);
                    return;
                case "<=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPGT, labelFalso);
                    return;
                case ">":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPLE, labelFalso);
                    return;
                case ">=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPLT, labelFalso);
                    return;
                case "==":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPNE, labelFalso);
                    return;
                case "!=":
                    generarExpresion(op.getIzquierda());
                    generarExpresion(op.getDerecha());
                    methodVisitor.visitJumpInsn(IF_ICMPEQ, labelFalso);
                    return;
            }
        }

        generarExpresion(condicion);
        methodVisitor.visitJumpInsn(IFEQ, labelFalso);
    }

    private void generarExpresionComoInstruccion(Expresion expr) {
        if (expr instanceof OperacionUnaria) {
            generarIncrementoDecremento((OperacionUnaria) expr);
        } else if (expr instanceof Asignacion) {
            generarAsignacion((Asignacion) expr);
        }
    }

    private void generarIncrementoDecremento(OperacionUnaria unaria) {
        String operador = unaria.getOperador();
        Expresion operando = unaria.getOperando();

        if (operando instanceof Variable) {
            String nombre = ((Variable) operando).getNombre();
            int indice = tabla.obtenerIndice(nombre);
            TipoDato tipo = tabla.obtenerTipo(nombre);

            if (tipo == TipoDato.ENTERO || tipo == TipoDato.NUMERO) {
                if (operador.equals("++")) {
                    methodVisitor.visitIincInsn(indice, 1);
                } else if (operador.equals("--")) {
                    methodVisitor.visitIincInsn(indice, -1);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  SI / SINO SI / SINO
    // ════════════════════════════════════════════════════════════════
    private void generarSi(NodoSi nodo) {
        Label fin = new Label();
        Label siguienteBloque = new Label();

        generarExpresion(nodo.getCondicion());
        methodVisitor.visitJumpInsn(IFEQ, siguienteBloque);

        for (Nodo instruccion : nodo.getCuerpoSi()) {
            generarInstruccion(instruccion);
        }
        methodVisitor.visitJumpInsn(GOTO, fin);

        for (int i = 0; i < nodo.getCondicionesSinoSi().size(); i++) {
            methodVisitor.visitLabel(siguienteBloque);
            siguienteBloque = new Label();

            generarExpresion(nodo.getCondicionesSinoSi().get(i));
            methodVisitor.visitJumpInsn(IFEQ, siguienteBloque);

            for (Nodo instruccion : nodo.getCuerposSinoSi().get(i)) {
                generarInstruccion(instruccion);
            }
            methodVisitor.visitJumpInsn(GOTO, fin);
        }

        methodVisitor.visitLabel(siguienteBloque);
        if (nodo.tieneSino()) {
            for (Nodo instruccion : nodo.getCuerpoSino()) {
                generarInstruccion(instruccion);
            }
        }

        methodVisitor.visitLabel(fin);
    }

    // ════════════════════════════════════════════════════════════════
    //  MIENTRAS
    // ════════════════════════════════════════════════════════════════
    private void generarMientras(NodoMientras nodo) {
        Label inicio = new Label();
        Label fin    = new Label();

        pilaEtiquetasInicio.push(inicio);
        pilaEtiquetasFin.push(fin);

        methodVisitor.visitLabel(inicio);
        generarExpresion(nodo.getCondicion());
        methodVisitor.visitJumpInsn(IFEQ, fin);

        for (Nodo instruccion : nodo.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        methodVisitor.visitJumpInsn(GOTO, inicio);
        methodVisitor.visitLabel(fin);

        pilaEtiquetasInicio.pop();
        pilaEtiquetasFin.pop();
    }

    // ════════════════════════════════════════════════════════════════
    //  HACER MIENTRAS
    // ════════════════════════════════════════════════════════════════
    private void generarHacerMientras(NodoHacerMientras nodo) {
        Label inicio = new Label();
        Label fin    = new Label();

        pilaEtiquetasInicio.push(inicio);
        pilaEtiquetasFin.push(fin);

        methodVisitor.visitLabel(inicio);

        for (Nodo instruccion : nodo.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        generarExpresion(nodo.getCondicion());
        methodVisitor.visitJumpInsn(IFNE, inicio);
        methodVisitor.visitLabel(fin);

        pilaEtiquetasInicio.pop();
        pilaEtiquetasFin.pop();
    }

    private void generarRomper() {
        if (pilaEtiquetasFin.isEmpty()) {
            throw new RuntimeException("'romper' usado fuera de un bucle");
        }
        methodVisitor.visitJumpInsn(GOTO, pilaEtiquetasFin.peek());
    }

    private void generarContinuar() {
        if (pilaEtiquetasInicio.isEmpty()) {
            throw new RuntimeException("'continuar' usado fuera de un bucle");
        }
        methodVisitor.visitJumpInsn(GOTO, pilaEtiquetasInicio.peek());
    }

    // ════════════════════════════════════════════════════════════════
    //  DECLARACIÓN DE VARIABLE
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
            generarOperacionUnaria((OperacionUnaria) expresion);
        } else if (expresion instanceof Asignacion) {
            generarAsignacion((Asignacion) expresion);
        } else if (expresion instanceof OperacionTernaria) {
            generarTernario((OperacionTernaria) expresion);
        }
    }

    private void generarAsignacion(Asignacion asignacion) {
        String nombre   = asignacion.getNombre();
        String operador = asignacion.getOperador();
        int indice      = tabla.obtenerIndice(nombre);
        TipoDato tipo   = tabla.obtenerTipo(nombre);

        if (operador.equals("=")) {
            generarExpresion(asignacion.getValor());
        } else {
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

        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ASTORE, indice);
        } else {
            methodVisitor.visitVarInsn(ISTORE, indice);
        }
    }

    private void generarTernario(OperacionTernaria ternario) {
        Label siFalso = new Label();
        Label fin     = new Label();

        generarExpresion(ternario.getCondicion());
        methodVisitor.visitJumpInsn(IFEQ, siFalso);
        generarExpresion(ternario.getSiVerdadero());
        methodVisitor.visitJumpInsn(GOTO, fin);
        methodVisitor.visitLabel(siFalso);
        generarExpresion(ternario.getSiFalso());
        methodVisitor.visitLabel(fin);
    }

    private void generarOperacionUnaria(OperacionUnaria unaria) {
        String operador = unaria.getOperador();
        Expresion operando = unaria.getOperando();

        if (operador.equals("no") || operador.equals("!")) {
            generarExpresion(operando);
            Label verdadero = new Label();
            Label fin = new Label();
            methodVisitor.visitJumpInsn(IFEQ, verdadero);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitJumpInsn(GOTO, fin);
            methodVisitor.visitLabel(verdadero);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitLabel(fin);
            return;
        }

        if (operador.equals("-")) {
            generarExpresion(operando);
            methodVisitor.visitInsn(INEG);
            return;
        }

        if (!(operando instanceof Variable)) {
            throw new RuntimeException("++ y -- solo aplican a variables");
        }

        String nombre = ((Variable) operando).getNombre();
        int indice = tabla.obtenerIndice(nombre);

        if (operador.equals("++")) {
            methodVisitor.visitIincInsn(indice, 1);
        } else if (operador.equals("--")) {
            methodVisitor.visitIincInsn(indice, -1);
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
            Expresion interna = ((ConversionTexto) expr).getExpresion();
            generarExpresion(interna);
            TipoDato tipo = (interna instanceof Variable)
                    ? tabla.obtenerTipo(((Variable) interna).getNombre())
                    : TipoDato.ENTERO;
            if (tipo == TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            }

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

        } else if (expr instanceof OperacionBinaria) {
            generarExpresion(expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof OperacionUnaria) {
            generarExpresion(expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        }
    }
}