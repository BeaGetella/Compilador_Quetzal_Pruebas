package com.example.generador;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;


public class GeneradorBytecode {
    private ClassWriter classWriter;
    private MethodVisitor methodVisitor;
    private TablaSimbolos tabla;
    private String nombreClase;
    private String objetoActual = null;
    private Map<String, NodoFuncion> funciones = new java.util.HashMap<>();
    private int contadorSlots = 10; // Slots 0-9 reservados para variables locales y argumentos en main



    private java.util.Deque<Label> pilaEtiquetasFin = new java.util.ArrayDeque<>();
    private java.util.Deque<Label> pilaEtiquetasInicio = new java.util.ArrayDeque<>();
    private Map<String, NodoObjeto> objetos = new java.util.HashMap<>();



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
            Asignacion a = (Asignacion) instruccion;
            if (a.getNombre().startsWith("ambiente.")) {
                generarAsignacionAmbiente(a);
            } else {
                generarAsignacion(a);
            }

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
        } else if (instruccion instanceof InstruccionExpresion) {
            Expresion expr = ((InstruccionExpresion) instruccion).getExpresion();
            if (expr instanceof LlamadaFuncion) {
                LlamadaFuncion llamada = (LlamadaFuncion) expr;
                if (llamada.getObjeto().equals("consola")) {
                    generarLlamadaConsola(llamada);
                } else if (!llamada.getObjeto().isEmpty()
                        && !llamada.getObjeto().equals(llamada.getMetodo())) {
                    // ← AGREGAR: método de objeto como instrucción suelta: p1.saludar("Hola")
                    generarLlamadaMetodoObjeto(llamada);
                    // Si retorna valor y no se usa, sacar del stack
                    String claseObjeto = inferirClaseObjeto(llamada.getObjeto());
                    NodoObjeto nodoObjeto = objetos.get(claseObjeto);
                    if (nodoObjeto != null) {
                        for (NodoMetodo m : nodoObjeto.getMetodos()) {
                            if (m.getNombre().equals(llamada.getMetodo())
                                    && !m.getTipoRetorno().equals("vacio")) {
                                methodVisitor.visitInsn(POP);
                                break;
                            }
                        }
                    }
                } else {
                    generarLlamadaFuncionUsuario(llamada);
                    NodoFuncion funcion = funciones.get(llamada.getMetodo());
                    if (funcion != null && !funcion.getTipoRetorno().equals("vacio")) {
                        methodVisitor.visitInsn(POP);
                    }
                }
            } else {
                generarExpresionComoInstruccion(expr);
            }
        } else if (instruccion instanceof NodoObjeto) {
            objetos.put(((NodoObjeto) instruccion).getNombre(), (NodoObjeto) instruccion);
        }
        else if (instruccion instanceof NodoRetornar) {
            generarRetornar((NodoRetornar) instruccion);

    } else if (instruccion instanceof NodoParaEn) {
        generarParaEn((NodoParaEn) instruccion);
    }
    }

    private void generarAsignacionAmbiente(Asignacion asignacion) {
        String campo = asignacion.getNombre().replace("ambiente.", "");
        NodoObjeto objeto = objetos.get(objetoActual);
        if (objeto == null) {
            throw new RuntimeException("ambiente usado fuera de un objeto");
        }

        // Encontrar tipo del campo
        String tipoCampo = "entero";
        for (NodoAtributo attr : objeto.getAtributos()) {
            if (attr.getNombre().equals(campo)) {
                tipoCampo = attr.getTipo();
                break;
            }
        }

        // ALOAD 0 = this
        methodVisitor.visitVarInsn(ALOAD, 0);
        // Generar valor
        generarExpresion(asignacion.getValor());
        // PUTFIELD
        methodVisitor.visitFieldInsn(PUTFIELD, objetoActual, campo, descriptorTipo(tipoCampo));
    }

    // ════════════════════════════════════════════════════════════════
    //  GENERACIÓN DEL BUCLE PARA
    // ════════════════════════════════════════════════════════════════
    private void generarBuclePara(BuclePara bucle) {
        // Registrar la variable del bucle directamente en la tabla
        // sin entrar/salir scope — el generador vive en scope plano
        DeclaracionVariable init = bucle.getInicializacion();
        TipoDato tipo = convertirTipo(init.getTipo());
        tabla.agregarVariable(init.getNombre(), tipo, 0);

        // Generar la inicialización
        generarExpresion(init.getValor());
        int indice = tabla.obtenerIndice(init.getNombre());
        methodVisitor.visitVarInsn(ISTORE, indice);

        Label labelInicio = new Label();
        Label labelFin    = new Label();

        pilaEtiquetasInicio.push(labelInicio);
        pilaEtiquetasFin.push(labelFin);

        methodVisitor.visitLabel(labelInicio);
        generarExpresion(bucle.getCondicion());
        methodVisitor.visitJumpInsn(IFEQ, labelFin);

        for (Nodo instruccion : bucle.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        generarExpresion(bucle.getIncremento());
        methodVisitor.visitJumpInsn(GOTO, labelInicio);
        methodVisitor.visitLabel(labelFin);

        pilaEtiquetasInicio.pop();
        pilaEtiquetasFin.pop();
    }

    private TipoDato convertirTipo(String tipo) {
        switch (tipo.toLowerCase()) {
            case "entero":  return TipoDato.ENTERO;
            case "numero":  return TipoDato.NUMERO;
            case "texto":   return TipoDato.TEXTO;
            case "log":     return TipoDato.LOG;
            default:        return TipoDato.OBJETO;
        }
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
            Asignacion asignacion = (Asignacion) expr;
            // ← CAMBIO: detectar ambiente.campo antes de ir a generarAsignacion
            if (asignacion.getNombre().startsWith("ambiente.")) {
                generarAsignacionAmbiente(asignacion);
            } else {
                generarAsignacion(asignacion);
            }

        } else if (expr instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            if (llamada.getObjeto().equals("consola")) {
                generarLlamadaConsola(llamada);
            } else {
                generarLlamadaFuncionUsuario(llamada);
                NodoFuncion funcion = funciones.get(llamada.getMetodo());
                if (funcion != null && !funcion.getTipoRetorno().equals("vacio")) {
                    methodVisitor.visitInsn(POP);
                }
            }
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
        // Si la variable no existe en la tabla, registrarla ahora
        if (!tabla.existe(decl.getNombre())) {
            TipoDato tipo = convertirTipo(decl.getTipo());
            tabla.agregarVariable(decl.getNombre(), tipo, 0);
        }

        int indiceVariable = tabla.obtenerIndice(decl.getNombre());

        generarExpresion(decl.getValor());

        TipoDato tipo = tabla.obtenerTipo(decl.getNombre());
        if (tipo == TipoDato.TEXTO) {
            methodVisitor.visitVarInsn(ASTORE, indiceVariable);
        } else if (decl.getTipo().startsWith("lista")) {
            methodVisitor.visitVarInsn(ASTORE, indiceVariable);
        } else {
            methodVisitor.visitVarInsn(ISTORE, indiceVariable);
        }
    }

    private void generarExpresion(Expresion expresion) {
        if (expresion instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expresion);

        } else if (expresion instanceof LiteralString) {
            methodVisitor.visitLdcInsn(((LiteralString) expresion).getValor());

        } else if (expresion instanceof Concatenacion) {
            generarConcatenacion((Concatenacion) expresion);

        } else if (expresion instanceof Variable) {
            generarVariable((Variable) expresion);

        } else if (expresion instanceof OperacionBinaria) {
            generarOperacionBinaria((OperacionBinaria) expresion);

        } else if (expresion instanceof ConversionNumero) {
            generarConversionNumero((ConversionNumero) expresion);

        } else if (expresion instanceof ConversionTexto) {
            Expresion interna = ((ConversionTexto) expresion).getExpresion();
            generarExpresion(interna);
            // Solo convertir si NO es ya un String
            TipoDato tipo = inferirTipoCompleto(interna);
            if (tipo != TipoDato.TEXTO && tipo != TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(I)Ljava/lang/String;", false);
            }

        } else if (expresion instanceof OperacionUnaria) {
            generarOperacionUnaria((OperacionUnaria) expresion);

        } else if (expresion instanceof Asignacion) {
            Asignacion a = (Asignacion) expresion;
            if (a.getNombre().startsWith("ambiente.")) {
                generarAsignacionAmbiente(a);
            } else {
                generarAsignacion(a);
            }

        } else if (expresion instanceof OperacionTernaria) {
            generarTernario((OperacionTernaria) expresion);

        } else if (expresion instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expresion;
            if (llamada.getObjeto() != null && llamada.getObjeto().equals("consola")) {
                generarLlamadaConsola(llamada);
            } else if (llamada.getObjeto() != null && !llamada.getObjeto().isEmpty()
                    && !llamada.getObjeto().equals(llamada.getMetodo())) {
                generarLlamadaMetodoObjeto(llamada);
            } else {
                generarLlamadaFuncionUsuario(llamada);
            }

        } else if (expresion instanceof ExpresionAmbiente) {
            generarAccesoAmbiente((ExpresionAmbiente) expresion);

        } else if (expresion instanceof ExpresionNuevo) {
            generarNuevo((ExpresionNuevo) expresion);
        } else if (expresion instanceof LiteralLista) {
            generarLiteralLista((LiteralLista) expresion);
        } else if (expresion instanceof AccesoLista) {
            generarAccesoLista((AccesoLista) expresion);
        }
    }


    private void generarLiteralLista(LiteralLista lista) {
        // new ArrayList()
        methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

        // agregar cada elemento
        for (Expresion elemento : lista.getElementos()) {
            methodVisitor.visitInsn(DUP); // duplicar referencia al ArrayList
            generarExpresionComoObjeto(elemento);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
            methodVisitor.visitInsn(POP); // descartar resultado booleano de add()
        }
    }

    private void generarExpresionComoObjeto(Expresion expr) {
        TipoDato tipo = inferirTipo(expr);
        if (tipo == TipoDato.TEXTO) {
            generarExpresionString(expr);
        } else {
            generarExpresion(expr);
            // boxear int → Integer
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        }
    }

    private void generarAccesoLista(AccesoLista acceso) {
        generarExpresion(acceso.getLista());
        generarExpresion(acceso.getIndice());
        // list.get(index)
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
        // unboxear Object → int por defecto (asumimos entero)
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
    }

    private void generarParaEn(NodoParaEn nodo) {
        Label inicio = new Label();
        Label fin    = new Label();

        // Slots nuevos para cada bucle — siempre frescos
        int slotLista    = contadorSlots++;
        int slotContador = contadorSlots++;
        int slotVariable = contadorSlots++;

        // Registrar variable del bucle apuntando al slot correcto
        TipoDato tipo = convertirTipo(nodo.getTipoVariable());
        if (!tabla.existe(nodo.getNombreVariable())) {
            tabla.agregarVariable(nodo.getNombreVariable(), tipo, 0);
        }
        // Forzar el índice correcto en la tabla
        tabla.obtener(nodo.getNombreVariable()).setIndice(slotVariable);

        // Generar el iterable
        generarExpresion(nodo.getIterable());
        methodVisitor.visitVarInsn(ASTORE, slotLista);

        // contador = 0
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, slotContador);

        pilaEtiquetasInicio.push(inicio);
        pilaEtiquetasFin.push(fin);

        // inicio del loop
        methodVisitor.visitLabel(inicio);

        // mientras contador < lista.size()
        methodVisitor.visitVarInsn(ALOAD, slotLista);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
        methodVisitor.visitVarInsn(ILOAD, slotContador);
        methodVisitor.visitJumpInsn(IF_ICMPLE, fin);

        // variable = lista.get(contador)
        methodVisitor.visitVarInsn(ALOAD, slotLista);
        methodVisitor.visitVarInsn(ILOAD, slotContador);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        methodVisitor.visitVarInsn(ISTORE, slotVariable);

        // cuerpo
        for (Nodo instruccion : nodo.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        // contador++
        methodVisitor.visitIincInsn(slotContador, 1);
        methodVisitor.visitJumpInsn(GOTO, inicio);
        methodVisitor.visitLabel(fin);

        pilaEtiquetasInicio.pop();
        pilaEtiquetasFin.pop();
    }

    private void generarAccesoAmbiente(ExpresionAmbiente expr) {
        NodoObjeto objeto = objetos.get(objetoActual);
        if (objeto == null) {
            throw new RuntimeException("ambiente usado fuera de un objeto");
        }

        String tipoCampo = "entero";
        for (NodoAtributo attr : objeto.getAtributos()) {
            if (attr.getNombre().equals(expr.getCampo())) {
                tipoCampo = attr.getTipo();
                break;
            }
        }

        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(GETFIELD, objetoActual, expr.getCampo(), descriptorTipo(tipoCampo));
    }

    private void generarNuevo(ExpresionNuevo expr) {
        NodoObjeto objeto = objetos.get(expr.getTipoObjeto());
        if (objeto == null) {
            throw new RuntimeException("Objeto no declarado: " + expr.getTipoObjeto());
        }

        // NEW + DUP
        methodVisitor.visitTypeInsn(NEW, expr.getTipoObjeto());
        methodVisitor.visitInsn(DUP);

        // Generar argumentos
        for (Expresion arg : expr.getArgumentos()) {
            generarExpresion(arg);
        }

        // Construir descriptor del constructor
        StringBuilder descriptor = new StringBuilder("(");
        if (objeto.getConstructor() != null) {
            for (String[] param : objeto.getConstructor().getParametros()) {
                descriptor.append(descriptorTipo(param[0]));
            }
        }
        descriptor.append(")V");

        methodVisitor.visitMethodInsn(INVOKESPECIAL, expr.getTipoObjeto(), "<init>", descriptor.toString(), false);
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

        if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
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

        // Primer pasada: registrar funciones SIN generar su bytecode
        for (Nodo instruccion : programa.getInstrucciones()) {
            if (instruccion instanceof NodoFuncion) {
                funciones.put(((NodoFuncion) instruccion).getNombre(), (NodoFuncion) instruccion);
            }
        }

        // Segunda pasada: generar instrucciones del main (saltando NodoFuncion)
        for (Nodo instruccion : programa.getInstrucciones()) {
            if (!(instruccion instanceof NodoFuncion)) {
                generarInstruccion(instruccion);
            }
        }

        // Generar todas las clases de objetos
        for (NodoObjeto objeto : objetos.values()) {
            generarClaseObjeto(objeto);
        }

        // Cerrar el método main ANTES de generar los métodos función
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        // Generar funciones como métodos estáticos separados
        for (NodoFuncion funcion : funciones.values()) {
            generarFuncion(funcion);
        }

        // Cerrar la clase AL FINAL
        classWriter.visitEnd();

        byte[] bytecode = classWriter.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            fos.write(bytecode);
        }
        System.out.println("Archivo generado: " + archivoSalida);
    }

    private void generarFuncion(NodoFuncion nodo) {
        // Construir descriptor JVM: (params)retorno
        StringBuilder descriptor = new StringBuilder("(");
        for (String[] param : nodo.getParametros()) {
            descriptor.append(descriptorTipo(param[0]));
        }
        descriptor.append(")");
        descriptor.append(descriptorTipo(nodo.getTipoRetorno()));

        // Crear el método
        MethodVisitor mv = classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                nodo.getNombre(),
                descriptor.toString(),
                null,
                null
        );
        mv.visitCode();

        // Guardar el methodVisitor actual y usar el nuevo
        MethodVisitor mvAnterior = methodVisitor;
        methodVisitor = mv;

        // Registrar parámetros en la tabla con índices desde 0
        tabla.entrarScope();
        int indiceParam = 0;
        for (String[] param : nodo.getParametros()) {
            TipoDato tipo = convertirTipo(param[0]);
            tabla.agregarParametro(param[1], tipo, indiceParam);  // ← índice JVM explícito
            indiceParam++;
        }

        tabla.resetearContador(indiceParam);

        // Generar cuerpo
        for (Nodo instruccion : nodo.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        // Si es vacio agregar RETURN al final
        if (nodo.getTipoRetorno().equals("vacio")) {
            mv.visitInsn(RETURN);
        }

        tabla.salirScope();

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Restaurar methodVisitor del main
        methodVisitor = mvAnterior;
    }

    private void generarClaseObjeto(NodoObjeto nodo) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cw.visit(V11, ACC_PUBLIC, nodo.getNombre(), null, "java/lang/Object", null);

        // Generar campos
        for (NodoAtributo attr : nodo.getAtributos()) {
            int acceso = attr.esPublico() ? ACC_PUBLIC : ACC_PRIVATE;
            cw.visitField(acceso, attr.getNombre(), descriptorTipo(attr.getTipo()), null, null).visitEnd();
        }

        // Generar constructor
        if (nodo.getConstructor() != null) {
            generarConstructorObjeto(cw, nodo);
        } else {
            // Constructor vacío por defecto
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Generar métodos
        for (NodoMetodo metodo : nodo.getMetodos()) {
            generarMetodoObjeto(cw, nodo, metodo);
        }

        cw.visitEnd();

        // Escribir el archivo .class
        byte[] bytecode = cw.toByteArray();
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream("output/" + nodo.getNombre() + ".class");
            fos.write(bytecode);
            fos.close();
            System.out.println("Clase generada: output/" + nodo.getNombre() + ".class");
        } catch (Exception e) {
            throw new RuntimeException("Error generando clase: " + nodo.getNombre(), e);
        }
    }

    private void generarConstructorObjeto(ClassWriter cw, NodoObjeto nodo) {
        NodoConstructor constructor = nodo.getConstructor();

        // Construir descriptor: (params)V
        StringBuilder descriptor = new StringBuilder("(");
        for (String[] param : constructor.getParametros()) {
            descriptor.append(descriptorTipo(param[0]));
        }
        descriptor.append(")V");

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", descriptor.toString(), null, null);
        mv.visitCode();

        // Llamar al constructor de Object
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        // Guardar methodVisitor actual
        MethodVisitor mvAnterior = methodVisitor;
        methodVisitor = mv;

        // Registrar parámetros en tabla
        tabla.entrarScope();
        // índice 0 = this, parámetros desde 1
        int idx = 1;
        for (String[] param : constructor.getParametros()) {
            tabla.agregarVariable(param[1], convertirTipo(param[0]), 0);
            // forzar índice correcto
            tabla.obtener(param[1]).setIndice(idx++);
        }

        // Guardar nombre del objeto actual para ambiente
        String objetoAnterior = objetoActual;
        objetoActual = nodo.getNombre();

        // Generar cuerpo
        for (Nodo instruccion : constructor.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        objetoActual = objetoAnterior;
        tabla.salirScope();

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        methodVisitor = mvAnterior;
    }


    private void generarMetodoObjeto(ClassWriter cw, NodoObjeto nodo, NodoMetodo metodo) {
        StringBuilder descriptor = new StringBuilder("(");
        for (String[] param : metodo.getParametros()) {
            descriptor.append(descriptorTipo(param[0]));
        }
        descriptor.append(")");
        descriptor.append(descriptorTipo(metodo.getTipoRetorno()));

        int acceso = metodo.esPublico() ? ACC_PUBLIC : ACC_PRIVATE;
        MethodVisitor mv = cw.visitMethod(acceso, metodo.getNombre(), descriptor.toString(), null, null);
        mv.visitCode();

        MethodVisitor mvAnterior = methodVisitor;
        methodVisitor = mv;

        tabla.entrarScope();
        int idx = 1;
        for (String[] param : metodo.getParametros()) {
            tabla.agregarVariable(param[1], convertirTipo(param[0]), 0);
            tabla.obtener(param[1]).setIndice(idx++);
        }

        String objetoAnterior = objetoActual;
        objetoActual = nodo.getNombre();

        for (Nodo instruccion : metodo.getCuerpo()) {
            generarInstruccion(instruccion);
        }

        objetoActual = objetoAnterior;
        tabla.salirScope();

        if (metodo.getTipoRetorno().equals("vacio")) {
            mv.visitInsn(RETURN);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        methodVisitor = mvAnterior;
    }

    private void generarRetornar(NodoRetornar nodo) {
        if (nodo.tieneValor()) {
            generarExpresion(nodo.getValor());
            TipoDato tipo = inferirTipoCompleto(nodo.getValor());
            if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
                methodVisitor.visitInsn(ARETURN);
            } else {
                methodVisitor.visitInsn(IRETURN);
            }
        } else {
            methodVisitor.visitInsn(RETURN);
        }
    }

    private TipoDato inferirTipoCompleto(Expresion expr) {
        if (expr instanceof Variable) {
            return tabla.obtenerTipo(((Variable) expr).getNombre());
        } else if (expr instanceof LiteralString) {
            return TipoDato.TEXTO;
        } else if (expr instanceof LiteralNumero) {
            return TipoDato.ENTERO;
        } else if (expr instanceof ExpresionAmbiente) {
            // Buscar el tipo del campo en el objeto actual
            String campo = ((ExpresionAmbiente) expr).getCampo();
            NodoObjeto objeto = objetos.get(objetoActual);
            if (objeto != null) {
                for (NodoAtributo attr : objeto.getAtributos()) {
                    if (attr.getNombre().equals(campo)) {
                        return convertirTipo(attr.getTipo());
                    }
                }
            }
            return TipoDato.TEXTO; // default para campos de objeto
        } else if (expr instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            // Buscar tipo de retorno del método
            NodoObjeto objeto = objetos.get(objetoActual);
            if (objeto != null) {
                for (NodoMetodo m : objeto.getMetodos()) {
                    if (m.getNombre().equals(llamada.getMetodo())) {
                        return convertirTipo(m.getTipoRetorno());
                    }
                }
            }
            NodoFuncion funcion = funciones.get(llamada.getMetodo());
            if (funcion != null) {
                return convertirTipo(funcion.getTipoRetorno());
            }
            return TipoDato.ENTERO;
        } else if (expr instanceof OperacionBinaria) {
            TipoDato izq = inferirTipoCompleto(((OperacionBinaria) expr).getIzquierda());
            TipoDato der = inferirTipoCompleto(((OperacionBinaria) expr).getDerecha());
            if (izq == TipoDato.TEXTO || der == TipoDato.TEXTO) return TipoDato.TEXTO;
            return TipoDato.ENTERO;
        }
        return TipoDato.ENTERO;
    }


    private String descriptorTipo(String tipo) {
        switch (tipo.toLowerCase()) {
            case "entero":
            case "log":     return "I";
            case "numero":  return "D";
            case "texto":   return "Ljava/lang/String;";
            case "vacio":   return "V";
            default:
                // lista<entero>, lista<texto>, lista sin tipo
                if (tipo.startsWith("lista")) {
                    return "Ljava/util/ArrayList;";
                }
                return "I";

        }
    }

    private TipoDato inferirTipo(Expresion expr) {
        if (expr instanceof LiteralString) {
            return TipoDato.TEXTO;
        } else if (expr instanceof Variable) {
            return tabla.obtenerTipo(((Variable) expr).getNombre());
        } else if (expr instanceof Concatenacion) {
            return TipoDato.TEXTO;
        } else if (expr instanceof ConversionTexto) {
            return TipoDato.TEXTO;
        } else if (expr instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) expr;
            // Si alguno de los operandos es texto, es concatenación
            TipoDato izq = inferirTipo(op.getIzquierda());
            TipoDato der = inferirTipo(op.getDerecha());
            if (izq == TipoDato.TEXTO || der == TipoDato.TEXTO) {
                return TipoDato.TEXTO;
            }
            return TipoDato.ENTERO;
        } else if (expr instanceof LiteralNumero) {
            return TipoDato.ENTERO;
        }
        return TipoDato.ENTERO;
    }


    private void generarLlamadaFuncionUsuario(LlamadaFuncion llamada) {
        // Verificar si es método de lista
        if (esMetodoLista(llamada.getMetodo())) {
            generarMetodoLista(llamada);
            return;
        }

        // Función de usuario normal
        NodoFuncion funcion = funciones.get(llamada.getMetodo());
        if (funcion == null) {
            throw new RuntimeException("Función no declarada: " + llamada.getMetodo());
        }
        for (Expresion arg : llamada.getArgumentos()) {
            generarExpresion(arg);
        }
        StringBuilder descriptor = new StringBuilder("(");
        for (String[] param : funcion.getParametros()) {
            descriptor.append(descriptorTipo(param[0]));
        }
        descriptor.append(")").append(descriptorTipo(funcion.getTipoRetorno()));
        methodVisitor.visitMethodInsn(INVOKESTATIC, nombreClase, llamada.getMetodo(), descriptor.toString(), false);
    }

    private boolean esMetodoLista(String metodo) {
        switch (metodo) {
            case "longitud": case "agregar": case "texto":
            case "primero":  case "ultimo":  case "sumar":
            case "esta_vacia": case "contiene":
                return true;
            default: return false;
        }
    }

    private void generarMetodoLista(LlamadaFuncion llamada) {
        // Cargar el objeto lista (el objeto antes del punto)
        generarExpresion(new Variable(llamada.getObjeto()));

        switch (llamada.getMetodo()) {
            case "longitud":
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                break;

            case "agregar":
                generarExpresionComoObjeto(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                methodVisitor.visitInsn(POP);
                break;

            case "primero":
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;

            case "ultimo":
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitInsn(ISUB);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;

            case "sumar":
                // Usamos un loop: int suma = 0; for each → suma += elem
                Label inicioLoop = new Label();
                Label finLoop    = new Label();
                int slotLista    = contadorSlots++;
                int slotContador = contadorSlots++;
                int slotSuma     = contadorSlots++;

                methodVisitor.visitVarInsn(ASTORE, slotLista);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotContador);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotSuma);

                methodVisitor.visitLabel(inicioLoop);
                methodVisitor.visitVarInsn(ALOAD, slotLista);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotContador);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finLoop);

                methodVisitor.visitVarInsn(ILOAD, slotSuma);
                methodVisitor.visitVarInsn(ALOAD, slotLista);
                methodVisitor.visitVarInsn(ILOAD, slotContador);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitInsn(IADD);
                methodVisitor.visitVarInsn(ISTORE, slotSuma);
                methodVisitor.visitIincInsn(slotContador, 1);
                methodVisitor.visitJumpInsn(GOTO, inicioLoop);

                methodVisitor.visitLabel(finLoop);
                methodVisitor.visitVarInsn(ILOAD, slotSuma);
                break;

            case "texto":
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "toString", "()Ljava/lang/String;", false);
                break;

            case "esta_vacia":
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false);
                break;

            case "contiene":
                generarExpresionComoObjeto(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "contains", "(Ljava/lang/Object;)Z", false);
                break;
        }
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

        } else if (expr instanceof OperacionBinaria) {
            OperacionBinaria op = (OperacionBinaria) expr;
            if (op.getOperador().equals("+")) {
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                agregarAStringBuilder(op.getIzquierda());
                agregarAStringBuilder(op.getDerecha());
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            }

        } else if (expr instanceof ConversionTexto) {
            generarExpresion(((ConversionTexto) expr).getExpresion());
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(I)Ljava/lang/String;", false);

        } else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo != TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(I)Ljava/lang/String;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(I)Ljava/lang/String;", false);

        } else if (expr instanceof LlamadaFuncion) {
            // ← NUEVO: llamada a método de objeto o función que retorna texto
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            if (llamada.getObjeto().equals("consola")) {
                generarLlamadaConsola(llamada);
            } else if (!llamada.getObjeto().isEmpty()) {
                // persona.obtener_nombre() → INVOKEVIRTUAL
                generarLlamadaMetodoObjeto(llamada);
            } else {
                // función libre que retorna texto
                generarLlamadaFuncionUsuario(llamada);
            }
            // El resultado ya queda en el stack como String
        }
    }

    private void generarLlamadaMetodoObjeto(LlamadaFuncion llamada) {
        String nombreObjeto = llamada.getObjeto();
        String nombreMetodo = llamada.getMetodo();

        // Verificar si es método de lista
        if (esMetodoLista(nombreMetodo)) {
            generarMetodoLista(llamada);
            return;
        }

        // Cargar la instancia
        generarVariable(new Variable(nombreObjeto));

        // Generar argumentos
        for (Expresion arg : llamada.getArgumentos()) {
            generarExpresion(arg);
        }

        // Inferir clase
        String claseObjeto = inferirClaseObjeto(nombreObjeto);

        NodoObjeto nodoObjeto = objetos.get(claseObjeto);
        if (nodoObjeto == null) {
            throw new RuntimeException("Clase no encontrada: " + claseObjeto);
        }

        NodoMetodo metodo = null;
        for (NodoMetodo m : nodoObjeto.getMetodos()) {
            if (m.getNombre().equals(nombreMetodo)) {
                metodo = m;
                break;
            }
        }
        if (metodo == null) {
            throw new RuntimeException("Método no encontrado: " + nombreMetodo + " en " + claseObjeto);
        }

        StringBuilder descriptor = new StringBuilder("(");
        for (String[] param : metodo.getParametros()) {
            descriptor.append(descriptorTipo(param[0]));
        }
        descriptor.append(")");
        descriptor.append(descriptorTipo(metodo.getTipoRetorno()));

        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, claseObjeto, nombreMetodo, descriptor.toString(), false);
    }

    private String inferirClaseObjeto(String nombreVariable) {
        String clase = tabla.obtenerTipoObjeto(nombreVariable);
        if (clase != null) {
            return clase;
        }
        // Fallback: buscar en objetos registrados
        for (String nombreClase : objetos.keySet()) {
            if (tabla.existe(nombreVariable)) {
                return nombreClase;
            }
        }
        throw new RuntimeException("No se puede inferir la clase de: " + nombreVariable);
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
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof ConversionTexto) {
            Expresion interna = ((ConversionTexto) expr).getExpresion();
            generarExpresion(interna);
            TipoDato tipo = inferirTipoCompleto(interna);
            if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(I)Ljava/lang/StringBuilder;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof Concatenacion) {
            agregarAStringBuilder(((Concatenacion) expr).getIzquierda());
            agregarAStringBuilder(((Concatenacion) expr).getDerecha());

        } else if (expr instanceof OperacionBinaria) {
            generarExpresion(expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof OperacionUnaria) {
            generarExpresion(expr);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(I)Ljava/lang/StringBuilder;", false);

        } else if (expr instanceof LlamadaFuncion) {
            // ← NUEVO: persona.obtener_nombre() o funcion() dentro de interpolación
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            TipoDato tipoRetorno;

            if (!llamada.getObjeto().isEmpty() && !llamada.getObjeto().equals("consola")) {
                // Método de objeto: persona.obtener_edad()
                generarLlamadaMetodoObjeto(llamada);
                // Inferir tipo de retorno del método
                String claseObjeto = inferirClaseObjeto(llamada.getObjeto());
                NodoObjeto nodoObjeto = objetos.get(claseObjeto);
                tipoRetorno = TipoDato.ENTERO; // default
                if (nodoObjeto != null) {
                    for (NodoMetodo m : nodoObjeto.getMetodos()) {
                        if (m.getNombre().equals(llamada.getMetodo())) {
                            tipoRetorno = convertirTipo(m.getTipoRetorno());
                            break;
                        }
                    }
                }
            } else {
                // Función libre
                generarLlamadaFuncionUsuario(llamada);
                NodoFuncion funcion = funciones.get(llamada.getMetodo());
                tipoRetorno = funcion != null
                        ? convertirTipo(funcion.getTipoRetorno())
                        : TipoDato.ENTERO;
            }

            if (tipoRetorno == TipoDato.TEXTO || tipoRetorno == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(I)Ljava/lang/StringBuilder;", false);
            }
        }
    }
}