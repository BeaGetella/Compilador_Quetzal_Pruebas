package com.example.generador;

import com.example.parser.ast.*;
import com.example.semantico.enums.TipoDato;
import com.example.semantico.gestores.TablaSimbolos;
import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;


public class GeneradorBytecode {
    private ClassWriter classWriter;
    private MethodVisitor methodVisitor;
    private TablaSimbolos tabla;
    private String nombreClase;
    private String objetoActual = null;
    private Map<String, NodoFuncion> funciones = new java.util.HashMap<>();
    private int contadorSlots = 0;



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
                }
                else if (!llamada.getObjeto().isEmpty()
                        && !llamada.getObjeto().equals(llamada.getMetodo())
                        && (esLista(llamada.getObjeto()) || esMetodoLista(llamada.getMetodo()))) {
                    // método de lista: nums.ordenar(), nums.agregar(5), etc.
                    generarMetodoLista(llamada);
                    // si retorna valor y no se usa, POP
                    TipoDato tipoRet = tipoRetornoMetodoLista(llamada.getMetodo());
                    if (tipoRet != null) methodVisitor.visitInsn(POP);


                } else if (!llamada.getObjeto().isEmpty()
                        && !llamada.getObjeto().equals(llamada.getMetodo())
                        && (esLista(llamada.getObjeto()) || esMetodoLista(llamada.getMetodo()))) {
                    generarMetodoLista(llamada);
                    TipoDato tipoRet = tipoRetornoMetodoLista(llamada.getMetodo());
                    if (tipoRet != null) methodVisitor.visitInsn(POP);
                } else if (!llamada.getObjeto().isEmpty()
                        && !llamada.getObjeto().equals(llamada.getMetodo())
                        && esMetodoJsn(llamada.getMetodo())) {
                    // método de JSN como statement: persona.establecer("x", 1)
                    generarMetodoJsn(llamada);
                    TipoDato tipoRet = tipoRetornoMetodoJsn(llamada.getMetodo());
                    if (tipoRet != null) methodVisitor.visitInsn(POP);
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
        }
        else if (instruccion instanceof NodoObjeto) {
            objetos.put(((NodoObjeto) instruccion).getNombre(), (NodoObjeto) instruccion);
        }
        else if (instruccion instanceof NodoRetornar) {
            generarRetornar((NodoRetornar) instruccion);

        } else if (instruccion instanceof NodoParaEn) {
            generarParaEn((NodoParaEn) instruccion);
        }
    }

    private String tipoElementoLista(String nombreLista) {
        String declarado = tabla.obtenerTipoDeclarado(nombreLista);
        System.out.println("DEBUG tipoElementoLista: nombre=" + nombreLista + ", declarado='" + declarado + "'");
        if (declarado == null || declarado.isEmpty()) return "entero";
        int ini = declarado.indexOf('<');
        int fin = declarado.lastIndexOf('>');
        if (ini >= 0 && fin > ini) {
            return declarado.substring(ini + 1, fin).trim();
        }
        return "entero";
    }

    private void generarUnboxElemento(String tipoElemento) {
        switch (tipoElemento) {
            case "entero":
            case "numero":
            case "log":
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer",
                        "intValue", "()I", false);
                break;
            case "texto":
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                break;
            default:
                // objeto generico, no castear
                break;
        }
    }

    private boolean esLista(String nombreVariable) {
        TipoDato tipo = tabla.obtenerTipo(nombreVariable);
        return tipo == TipoDato.LISTA;
    }

    private boolean esJsn(String nombreVariable) {
        TipoDato tipo = tabla.obtenerTipo(nombreVariable);
        return tipo == TipoDato.JSN || tipo == TipoDato.OBJETO;
    }

    private TipoDato tipoRetornoMetodoJsn(String metodo) {
        switch (metodo) {
            case "contiene_clave":  return TipoDato.LOG;     // int 0/1
            case "claves":          return TipoDato.LISTA;   // ArrayList<String>
            case "valores":         return TipoDato.LISTA;   // ArrayList<Object>
            case "texto":
            case "texto_formateado": return TipoDato.TEXTO;
            case "establecer":
            case "eliminar":
            case "fusionar":        return null;             // void
            default:                return null;
        }
    }

    private boolean esMetodoJsn(String metodo) {
        switch (metodo) {
            case "contiene_clave":
            case "claves":
            case "valores":
            case "establecer":
            case "eliminar":
            case "fusionar":
            case "texto":
            case "texto_formateado":
                return true;
            default:
                return false;
        }
    }

    // Retorna el tipo de retorno de un método de lista (null = void)
    private TipoDato tipoRetornoMetodoLista(String metodo) {
        switch (metodo) {
            // Numéricos puros
            case "longitud":
            case "maximo":
            case "minimo":
            case "buscar":
            case "buscar_ultimo":
            case "contar":
            case "sumar":
            case "promedio":
                return TipoDato.ENTERO;

            // Booleanos (int 0/1 en JVM)
            case "esta_vacia":
            case "logico":
            case "contiene":
                return TipoDato.ENTERO;

            // Texto
            case "unir":
            case "texto":
            case "json":
                return TipoDato.TEXTO;

            // Listas
            case "tomar":
            case "saltar":
            case "sublista":
            case "concatenar":
            case "ordenado":
                return TipoDato.LISTA;


            case "primero":
            case "ultimo":
            case "quitar_en":
                return null;

            // void: agregar, remover, ordenar, ordenar_descendente, invertir,
            //       limpiar, extender, insertar
            default:
                return null;
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
        String tipoBase = tipo.contains("<") ? tipo.substring(0, tipo.indexOf("<")) : tipo;
        switch (tipoBase.toLowerCase()) {
            case "entero":  return TipoDato.ENTERO;
            case "numero":  return TipoDato.NUMERO;
            case "texto":   return TipoDato.TEXTO;
            case "log":     return TipoDato.LOG;
            case "lista":   return TipoDato.LISTA;
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
        int indiceVariable = tabla.obtenerIndice(decl.getNombre());
        generarExpresion(decl.getValor());

        TipoDato tipo = tabla.obtenerTipo(decl.getNombre());
        if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO || tipo == TipoDato.LISTA) {
            methodVisitor.visitVarInsn(ASTORE, indiceVariable);
            if (tipo == TipoDato.OBJETO) {
                tabla.registrarTipoObjeto(decl.getNombre(), decl.getTipo());
            }
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
            TipoDato tipo = inferirTipoCompleto(interna);
            if (tipo == TipoDato.TEXTO) {
                // ya es String, nada que hacer
            } else if (tipo == TipoDato.LISTA) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.JSN) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
            } else {
                // ENTERO, LOG, NUMERO -> int -> String
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
                if (esLista(llamada.getObjeto())) {
                    generarMetodoLista(llamada);
                } else if (esMetodoJsn(llamada.getMetodo())) {
                    generarMetodoJsn(llamada);
                } else {
                    generarLlamadaMetodoObjeto(llamada);
                }
            }
        } else if (expresion instanceof ExpresionAmbiente) {
            generarAccesoAmbiente((ExpresionAmbiente) expresion);

        } else if (expresion instanceof ExpresionNuevo) {
            generarNuevo((ExpresionNuevo) expresion);
        } else if (expresion instanceof LiteralLista) {
            generarLiteralLista((LiteralLista) expresion);
        } else if (expresion instanceof AccesoLista) {
            generarAccesoLista((AccesoLista) expresion);
        } else if (expresion instanceof LiteralJsn) {
            generarLiteralJsn((LiteralJsn) expresion);
        } else if (expresion instanceof AccesoJsn) {
            generarAccesoJsn((AccesoJsn) expresion);
        }
    }

    private void generarLiteralJsn(LiteralJsn jsn) {
        // new LinkedHashMap()
        methodVisitor.visitTypeInsn(NEW, "java/util/LinkedHashMap");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);

        // agregar cada propiedad
        for (Map.Entry<String, Expresion> entrada : jsn.getPropiedades().entrySet()) {
            methodVisitor.visitInsn(DUP);
            // clave
            methodVisitor.visitLdcInsn(entrada.getKey());
            // valor
            generarExpresionComoObjeto(entrada.getValue());
            // map.put(clave, valor)
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                    "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            methodVisitor.visitInsn(POP);
        }
    }


    private void generarAccesoJsn(AccesoJsn acceso) {
        generarExpresion(acceso.getObjeto());

        // Si el objeto es otro AccesoJsn, el resultado es Object → castear a LinkedHashMap
        if (acceso.getObjeto() instanceof AccesoJsn) {
            methodVisitor.visitTypeInsn(CHECKCAST, "java/util/LinkedHashMap");
        }

        methodVisitor.visitLdcInsn(acceso.getPropiedad());
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
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
        if (expr instanceof LiteralJsn) {
            generarLiteralJsn((LiteralJsn) expr);
        } else if (expr instanceof LiteralLista) {
            generarLiteralLista((LiteralLista) expr);
        } else if (expr instanceof LiteralString) {
            methodVisitor.visitLdcInsn(((LiteralString) expr).getValor());
        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer",
                    "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (expr instanceof Variable) {
            TipoDato tipo = inferirTipo(expr);
            if (tipo == TipoDato.TEXTO) {
                generarVariable((Variable) expr);
            } else {
                generarVariable((Variable) expr);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer",
                        "valueOf", "(I)Ljava/lang/Integer;", false);
            }
        } else {
            // Para cualquier otra expresión — evaluar y boxear si es int
            TipoDato tipo = inferirTipo(expr);
            if (tipo == TipoDato.TEXTO) {
                generarExpresionString(expr);
            } else {
                generarExpresion(expr);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer",
                        "valueOf", "(I)Ljava/lang/Integer;", false);
            }
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

        if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO || tipo == TipoDato.LISTA) {
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
        contadorSlots = tabla.getContadorIndices();

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

    private TipoDato inferirTipoExpresion(Expresion expr) {
        if (expr instanceof LiteralString)    return TipoDato.TEXTO;
        if (expr instanceof LiteralNumero)    return TipoDato.ENTERO;
        if (expr instanceof LiteralLista)     return TipoDato.LISTA;
        if (expr instanceof LiteralJsn)       return TipoDato.JSN;
        if (expr instanceof Concatenacion)    return TipoDato.TEXTO;
        if (expr instanceof ConversionTexto)  return TipoDato.TEXTO;
        if (expr instanceof ConversionNumero) return TipoDato.ENTERO;

        if (expr instanceof Variable) {
            return tabla.obtenerTipo(((Variable) expr).getNombre());
        }

        if (expr instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            // método de lista
            if (!llamada.getObjeto().isEmpty() && esLista(llamada.getObjeto())) {
                TipoDato tr = tipoRetornoMetodoLista(llamada.getMetodo());
                if (tr != null) return tr;
                // tipo depende del elemento (primero, ultimo, quitar_en)
                String elem = tipoElementoLista(llamada.getObjeto());
                return elem.equals("texto") ? TipoDato.TEXTO : TipoDato.ENTERO;
            }
            // método de JSN
            if (!llamada.getObjeto().isEmpty() && esMetodoJsn(llamada.getMetodo())) {
                TipoDato tr = tipoRetornoMetodoJsn(llamada.getMetodo());
                return tr != null ? tr : TipoDato.ENTERO;
            }
            // función de usuario
            NodoFuncion funcion = funciones.get(llamada.getMetodo());
            if (funcion != null) return convertirTipo(funcion.getTipoRetorno());
            return TipoDato.ENTERO;
        }

        if (expr instanceof OperacionBinaria) {
            TipoDato izq = inferirTipoExpresion(((OperacionBinaria) expr).getIzquierda());
            TipoDato der = inferirTipoExpresion(((OperacionBinaria) expr).getDerecha());
            if (izq == TipoDato.TEXTO || der == TipoDato.TEXTO) return TipoDato.TEXTO;
            return TipoDato.ENTERO;
        }

        return TipoDato.ENTERO;
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
            // metodo de lista: nums.texto(), nums.longitud(), ...
            if (llamada.getObjeto() != null && !llamada.getObjeto().isEmpty()
                    && esLista(llamada.getObjeto())) {
                TipoDato tr = tipoRetornoMetodoLista(llamada.getMetodo());
                return tr != null ? tr : TipoDato.ENTERO;
            }
            // metodo de JSN: persona.claves(), persona.valores(), persona.contiene_clave(..)
            if (llamada.getObjeto() != null && !llamada.getObjeto().isEmpty()
                    && esMetodoJsn(llamada.getMetodo())) {
                TipoDato tr = tipoRetornoMetodoJsn(llamada.getMetodo());
                return tr != null ? tr : TipoDato.ENTERO;
            }
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

        if (llamada.getMetodo().equals("rango")) {
            List<Expresion> args = llamada.getArgumentos();
            int slotRangoLista = contadorSlots++;
            int slotRangoIdx   = contadorSlots++;
            int inicio, fin2;

            methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            methodVisitor.visitVarInsn(ASTORE, slotRangoLista);

            if (args.size() == 1) {
                methodVisitor.visitInsn(ICONST_0);
            } else {
                generarExpresion(args.get(0));
            }
            methodVisitor.visitVarInsn(ISTORE, slotRangoIdx);

            Label loopR = new Label(); Label finR = new Label();
            methodVisitor.visitLabel(loopR);
            methodVisitor.visitVarInsn(ILOAD, slotRangoIdx);
            generarExpresion(args.size() == 1 ? args.get(0) : args.get(1));
            methodVisitor.visitJumpInsn(IF_ICMPGE, finR);
            methodVisitor.visitVarInsn(ALOAD, slotRangoLista);
            methodVisitor.visitVarInsn(ILOAD, slotRangoIdx);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
            methodVisitor.visitInsn(POP);
            methodVisitor.visitIincInsn(slotRangoIdx, 1);
            methodVisitor.visitJumpInsn(GOTO, loopR);
            methodVisitor.visitLabel(finR);
            methodVisitor.visitVarInsn(ALOAD, slotRangoLista);
            return;
        }

    }

    private boolean esMetodoLista(String metodo) {
        switch (metodo) {
            case "longitud": case "agregar": case "texto":
            case "primero":  case "ultimo":  case "sumar":
            case "esta_vacia": case "contiene": case "remover":
            case "ordenar": case "ordenar_descendente": case "invertir":
            case "buscar": case "buscar_ultimo": case "contar":
            case "promedio": case "maximo": case "minimo":
            case "unir": case "sublista": case "tomar": case "saltar":
            case "limpiar": case "quitar_en": case "insertar": case "ordenado":
            case "concatenar": case "extender": case "json": case "logico":
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

            case "primero": {
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "get", "(I)Ljava/lang/Object;", false);
                generarUnboxElemento(tipoElementoLista(llamada.getObjeto()));
                break;
            }

            case "ultimo": {
                int slotUlt = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotUlt);
                methodVisitor.visitVarInsn(ALOAD, slotUlt);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitInsn(ISUB);
                methodVisitor.visitVarInsn(ALOAD, slotUlt);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "get", "(I)Ljava/lang/Object;", false);
                generarUnboxElemento(tipoElementoLista(llamada.getObjeto()));
                break;
            }

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

            case "remover":
                generarExpresionComoObjeto(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "remove", "(Ljava/lang/Object;)Z", false);
                methodVisitor.visitInsn(POP);
                break;

            case "ordenar": {
                int slotOrd = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotOrd);
                methodVisitor.visitVarInsn(ALOAD, slotOrd);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections",
                        "sort", "(Ljava/util/List;)V", false);
                break;
            }

            case "ordenar_descendente": {
                int slotOrdD = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotOrdD);
                methodVisitor.visitVarInsn(ALOAD, slotOrdD);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections",
                        "reverseOrder", "()Ljava/util/Comparator;", false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections",
                        "sort", "(Ljava/util/List;Ljava/util/Comparator;)V", false);
                break;
            }

            case "invertir": {
                int slotInv = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotInv);
                methodVisitor.visitVarInsn(ALOAD, slotInv);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections",
                        "reverse", "(Ljava/util/List;)V", false);
                break;
            }

            case "buscar":
                generarExpresionComoObjeto(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "indexOf", "(Ljava/lang/Object;)I", false);
                break;

            case "buscar_ultimo":
                generarExpresionComoObjeto(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "lastIndexOf", "(Ljava/lang/Object;)I", false);
                break;

            case "contar": {
                // Stack al entrar: [lista]
                int slotLista2  = contadorSlots++;
                int slotElem    = contadorSlots++;
                int slotCount   = contadorSlots++;
                int slotIdx     = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotLista2); // guardar lista
                generarExpresionComoObjeto(llamada.getArgumentos().get(0)); // generar elem
                methodVisitor.visitVarInsn(ASTORE, slotElem);   // guardar elem
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotCount);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotIdx);
                Label loopC = new Label(); Label finC = new Label();
                methodVisitor.visitLabel(loopC);
                methodVisitor.visitVarInsn(ALOAD, slotLista2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotIdx);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finC);
                methodVisitor.visitVarInsn(ALOAD, slotLista2);
                methodVisitor.visitVarInsn(ILOAD, slotIdx);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitVarInsn(ALOAD, slotElem);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
                Label noIgual = new Label();
                methodVisitor.visitJumpInsn(IFEQ, noIgual);
                methodVisitor.visitIincInsn(slotCount, 1);
                methodVisitor.visitLabel(noIgual);
                methodVisitor.visitIincInsn(slotIdx, 1);
                methodVisitor.visitJumpInsn(GOTO, loopC);
                methodVisitor.visitLabel(finC);
                methodVisitor.visitVarInsn(ILOAD, slotCount);
                break;
            }

            case "promedio": {
                int slotListaP  = contadorSlots++;
                int slotSumaP   = contadorSlots++;
                int slotIdxP    = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaP);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotSumaP);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotIdxP);
                Label loopP = new Label(); Label finP = new Label();
                methodVisitor.visitLabel(loopP);
                methodVisitor.visitVarInsn(ALOAD, slotListaP);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotIdxP);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finP);
                methodVisitor.visitVarInsn(ILOAD, slotSumaP);
                methodVisitor.visitVarInsn(ALOAD, slotListaP);
                methodVisitor.visitVarInsn(ILOAD, slotIdxP);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitInsn(IADD);
                methodVisitor.visitVarInsn(ISTORE, slotSumaP);
                methodVisitor.visitIincInsn(slotIdxP, 1);
                methodVisitor.visitJumpInsn(GOTO, loopP);
                methodVisitor.visitLabel(finP);
                methodVisitor.visitVarInsn(ILOAD, slotSumaP);
                methodVisitor.visitVarInsn(ALOAD, slotListaP);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitInsn(IDIV);
                break;
            }

            case "maximo": {
                int slotListaM = contadorSlots++;
                int slotMax    = contadorSlots++;
                int slotIdxM   = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaM);
                methodVisitor.visitVarInsn(ALOAD, slotListaM);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotMax);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitVarInsn(ISTORE, slotIdxM);
                Label loopM = new Label(); Label finM = new Label();
                methodVisitor.visitLabel(loopM);
                methodVisitor.visitVarInsn(ALOAD, slotListaM);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotIdxM);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finM);
                methodVisitor.visitVarInsn(ALOAD, slotListaM);
                methodVisitor.visitVarInsn(ILOAD, slotIdxM);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotMax);
                Label noMayor = new Label();
                methodVisitor.visitJumpInsn(IF_ICMPLE, noMayor);
                methodVisitor.visitVarInsn(ALOAD, slotListaM);
                methodVisitor.visitVarInsn(ILOAD, slotIdxM);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotMax);
                methodVisitor.visitLabel(noMayor);
                methodVisitor.visitIincInsn(slotIdxM, 1);
                methodVisitor.visitJumpInsn(GOTO, loopM);
                methodVisitor.visitLabel(finM);
                methodVisitor.visitVarInsn(ILOAD, slotMax);
                break;
            }

            case "minimo": {
                int slotListaN = contadorSlots++;
                int slotMin    = contadorSlots++;
                int slotIdxN   = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaN);
                methodVisitor.visitVarInsn(ALOAD, slotListaN);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotMin);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitVarInsn(ISTORE, slotIdxN);
                Label loopN = new Label(); Label finN = new Label();
                methodVisitor.visitLabel(loopN);
                methodVisitor.visitVarInsn(ALOAD, slotListaN);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotIdxN);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finN);
                methodVisitor.visitVarInsn(ALOAD, slotListaN);
                methodVisitor.visitVarInsn(ILOAD, slotIdxN);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotMin);
                Label noMenor = new Label();
                methodVisitor.visitJumpInsn(IF_ICMPGE, noMenor);
                methodVisitor.visitVarInsn(ALOAD, slotListaN);
                methodVisitor.visitVarInsn(ILOAD, slotIdxN);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotMin);
                methodVisitor.visitLabel(noMenor);
                methodVisitor.visitIincInsn(slotIdxN, 1);
                methodVisitor.visitJumpInsn(GOTO, loopN);
                methodVisitor.visitLabel(finN);
                methodVisitor.visitVarInsn(ILOAD, slotMin);
                break;
            }

            case "unir": {
                int slotListaU  = contadorSlots++;
                int slotDelim   = contadorSlots++;
                int slotIdxU    = contadorSlots++;
                int slotSbU     = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaU);
                generarExpresionString(llamada.getArgumentos().get(0));
                methodVisitor.visitVarInsn(ASTORE, slotDelim);
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitVarInsn(ASTORE, slotSbU);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotIdxU);
                Label loopU = new Label(); Label finU = new Label();
                methodVisitor.visitLabel(loopU);
                methodVisitor.visitVarInsn(ALOAD, slotListaU);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ILOAD, slotIdxU);
                methodVisitor.visitJumpInsn(IF_ICMPLE, finU);
                Label noDelim = new Label();
                methodVisitor.visitVarInsn(ILOAD, slotIdxU);
                methodVisitor.visitJumpInsn(IFEQ, noDelim);
                methodVisitor.visitVarInsn(ALOAD, slotSbU);
                methodVisitor.visitVarInsn(ALOAD, slotDelim);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);
                methodVisitor.visitLabel(noDelim);
                methodVisitor.visitVarInsn(ALOAD, slotSbU);
                methodVisitor.visitVarInsn(ALOAD, slotListaU);
                methodVisitor.visitVarInsn(ILOAD, slotIdxU);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);
                methodVisitor.visitIincInsn(slotIdxU, 1);
                methodVisitor.visitJumpInsn(GOTO, loopU);
                methodVisitor.visitLabel(finU);
                methodVisitor.visitVarInsn(ALOAD, slotSbU);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                break;
            }

            case "sublista": {
                int slotListaSub = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaSub); // guardar lista
                methodVisitor.visitVarInsn(ALOAD, slotListaSub);  // recargar lista
                generarExpresion(llamada.getArgumentos().get(0)); // from
                generarExpresion(llamada.getArgumentos().get(1)); // to
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "subList", "(II)Ljava/util/List;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                break;
            }

            case "tomar": {
                int slotListaT = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaT); // guardar lista
                methodVisitor.visitVarInsn(ALOAD, slotListaT);  // recargar lista
                methodVisitor.visitInsn(ICONST_0);               // from = 0
                generarExpresion(llamada.getArgumentos().get(0)); // to = n
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "subList", "(II)Ljava/util/List;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                break;
            }

            case "saltar": {
                // Stack al entrar: [lista]
                int slotListaS = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaS);   // guarda lista
                methodVisitor.visitVarInsn(ALOAD, slotListaS);    // recarga lista
                generarExpresion(llamada.getArgumentos().get(0)); // inicio = n
                methodVisitor.visitVarInsn(ALOAD, slotListaS);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "size", "()I", false);
                // Stack: [lista, n, size]
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "subList", "(II)Ljava/util/List;", false);
                // Stack: [subList]
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                break;
            }

            case "limpiar":
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "clear", "()V", false);
                break;

            case "quitar_en": {
                generarExpresion(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "remove", "(I)Ljava/lang/Object;", false);
                generarUnboxElemento(tipoElementoLista(llamada.getObjeto()));
                break;
            }

            case "insertar": {
                int slotListaI = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaI); // guardar lista
                methodVisitor.visitVarInsn(ALOAD, slotListaI);  // recargar lista
                generarExpresion(llamada.getArgumentos().get(0));         // índice
                generarExpresionComoObjeto(llamada.getArgumentos().get(1)); // valor
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "add", "(ILjava/lang/Object;)V", false);
                break;
            }

            case "ordenado": {
                int slotListaO = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotListaO); // guardar original
                // new ArrayList(original) → copia
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, slotListaO);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                // Stack: [copia]
                // DUP para que sort consuma una copia de la referencia y quede la otra
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Collections",
                        "sort", "(Ljava/util/List;)V", false);
                // Stack: [copia_ordenada] ✓
                break;
            }
            case "concatenar": {
                // lista ya está en stack → guardar
                int slotBase   = contadorSlots++;
                int slotResult = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotBase);
                // new ArrayList(copiaDeBase)
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, slotBase);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                methodVisitor.visitVarInsn(ASTORE, slotResult);
                // result.addAll(argumento)
                methodVisitor.visitVarInsn(ALOAD, slotResult);
                generarExpresion(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "addAll", "(Ljava/util/Collection;)Z", false);
                methodVisitor.visitInsn(POP);
                // dejar resultado en stack
                methodVisitor.visitVarInsn(ALOAD, slotResult);
                break;
            }

            case "extender": {
                // lista (receptor) ya en stack, no retorna nada
                generarExpresion(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "addAll", "(Ljava/util/Collection;)Z", false);
                methodVisitor.visitInsn(POP); // descartar booleano de addAll
                break;
            }

            case "json": {
                // Convierte la lista a su representación toString (suficiente para JSON simple)
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
                break;
            }

            case "logico": {
                // Retorna verdadero (1) si la lista no está vacía
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "isEmpty", "()Z", false);
                // isEmpty devuelve 1 si vacía → necesitamos NOT
                Label esVacia = new Label();
                Label finLog  = new Label();
                methodVisitor.visitJumpInsn(IFNE, esVacia);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitJumpInsn(GOTO, finLog);
                methodVisitor.visitLabel(esVacia);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitLabel(finLog);
                break;
            }



        }
    }


    private void generarMetodoJsn(LlamadaFuncion llamada) {
        // Cargar el LinkedHashMap (la variable jsn)
        generarExpresion(new Variable(llamada.getObjeto()));

        switch (llamada.getMetodo()) {

            // ── contiene_clave(clave) → map.containsKey(k) → boolean → int ──
            case "contiene_clave": {
                generarExpresionString(llamada.getArgumentos().get(0));
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "containsKey", "(Ljava/lang/Object;)Z", false);
                // Z ya es int (0/1) en JVM, no hace falta conversión
                break;
            }

            // ── claves() → new ArrayList(map.keySet()) → lista<texto> ──
            case "claves": {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "keySet", "()Ljava/util/Set;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                break;
            }

            // ── valores() → new ArrayList(map.values()) → lista ──
            case "valores": {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "values", "()Ljava/util/Collection;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                break;
            }

            // ── establecer(clave, valor) → map.put(k, v) → void ──
            case "establecer": {
                int slotMapa = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotMapa);
                methodVisitor.visitVarInsn(ALOAD, slotMapa);
                generarExpresionString(llamada.getArgumentos().get(0)); // clave
                generarExpresionComoObjeto(llamada.getArgumentos().get(1)); // valor
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                methodVisitor.visitInsn(POP); // descartar el valor anterior que retorna put()
                break;
            }

            // ── eliminar(clave) → map.remove(k) → retorna el valor eliminado como Object ──
            case "eliminar": {
                generarExpresionString(llamada.getArgumentos().get(0)); // clave
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                methodVisitor.visitInsn(POP); // ← AGREGAR: descartar el valor eliminado
                break;
            }

            // ── fusionar(otroJsn) → map.putAll(otro) → void ──
            case "fusionar": {
                int slotMapa = contadorSlots++;
                methodVisitor.visitVarInsn(ASTORE, slotMapa);
                methodVisitor.visitVarInsn(ALOAD, slotMapa);
                generarExpresion(llamada.getArgumentos().get(0)); // el otro jsn
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "putAll", "(Ljava/util/Map;)V", false);
                break;
            }

            // ── texto() → serialización JSON compacta: {"k":"v","k2":2} ──
            case "texto": {
                // Construimos el JSON compacto manualmente con StringBuilder
                int slotMapa  = contadorSlots++;
                int slotSb    = contadorSlots++;
                int slotKeys  = contadorSlots++;
                int slotIdx   = contadorSlots++;
                int slotSize  = contadorSlots++;

                methodVisitor.visitVarInsn(ASTORE, slotMapa);

                // sb = new StringBuilder("{")
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitLdcInsn("{");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ASTORE, slotSb);

                // keys = new ArrayList(map.keySet())
                methodVisitor.visitVarInsn(ALOAD, slotMapa);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "keySet", "()Ljava/util/Set;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                methodVisitor.visitVarInsn(ASTORE, slotKeys);

                // size = keys.size()
                methodVisitor.visitVarInsn(ALOAD, slotKeys);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotSize);

                // idx = 0
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotIdx);

                Label loopT = new Label();
                Label finT  = new Label();
                methodVisitor.visitLabel(loopT);
                methodVisitor.visitVarInsn(ILOAD, slotIdx);
                methodVisitor.visitVarInsn(ILOAD, slotSize);
                methodVisitor.visitJumpInsn(IF_ICMPGE, finT);

                // if idx > 0: sb.append(",")
                Label noComa = new Label();
                methodVisitor.visitVarInsn(ILOAD, slotIdx);
                methodVisitor.visitJumpInsn(IFEQ, noComa);
                methodVisitor.visitVarInsn(ALOAD, slotSb);
                methodVisitor.visitLdcInsn(",");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);
                methodVisitor.visitLabel(noComa);

                // key = keys.get(idx)
                int slotKey = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotKeys);
                methodVisitor.visitVarInsn(ILOAD, slotIdx);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitVarInsn(ASTORE, slotKey);

                // sb.append('"').append(key).append('"').append(':')
                methodVisitor.visitVarInsn(ALOAD, slotSb);
                methodVisitor.visitLdcInsn("\"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ALOAD, slotKey);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitLdcInsn("\":");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);

                // val = map.get(key)
                int slotVal = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotMapa);
                methodVisitor.visitVarInsn(ALOAD, slotKey);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                methodVisitor.visitVarInsn(ASTORE, slotVal);

                // si val es String → "\"" + val + "\""  else → val.toString()
                int slotValStr = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotVal);
                Label noEsString = new Label();
                Label finVal    = new Label();
                methodVisitor.visitTypeInsn(INSTANCEOF, "java/lang/String");
                methodVisitor.visitJumpInsn(IFEQ, noEsString);
                // es String → "\"valor\""
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitLdcInsn("\"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ALOAD, slotVal);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitLdcInsn("\"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitVarInsn(ASTORE, slotValStr);
                methodVisitor.visitJumpInsn(GOTO, finVal);
                methodVisitor.visitLabel(noEsString);
                // no es String → toString()
                methodVisitor.visitVarInsn(ALOAD, slotVal);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitVarInsn(ASTORE, slotValStr);
                methodVisitor.visitLabel(finVal);

                // sb.append(valStr)
                methodVisitor.visitVarInsn(ALOAD, slotSb);
                methodVisitor.visitVarInsn(ALOAD, slotValStr);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);

                methodVisitor.visitIincInsn(slotIdx, 1);
                methodVisitor.visitJumpInsn(GOTO, loopT);
                methodVisitor.visitLabel(finT);

                // sb.append("}").toString()
                methodVisitor.visitVarInsn(ALOAD, slotSb);
                methodVisitor.visitLdcInsn("}");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "toString", "()Ljava/lang/String;", false);
                break;
            }

            // ── texto_formateado() → igual que texto() pero con saltos e indentación ──
            case "texto_formateado": {
                int slotMapa2  = contadorSlots++;
                int slotSb2    = contadorSlots++;
                int slotKeys2  = contadorSlots++;
                int slotIdx2   = contadorSlots++;
                int slotSize2  = contadorSlots++;

                methodVisitor.visitVarInsn(ASTORE, slotMapa2);

                // sb = new StringBuilder("{\n")
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitLdcInsn("{\n");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ASTORE, slotSb2);

                // keys = new ArrayList(map.keySet())
                methodVisitor.visitVarInsn(ALOAD, slotMapa2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "keySet", "()Ljava/util/Set;", false);
                methodVisitor.visitTypeInsn(NEW, "java/util/ArrayList");
                methodVisitor.visitInsn(DUP_X1);
                methodVisitor.visitInsn(SWAP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList",
                        "<init>", "(Ljava/util/Collection;)V", false);
                methodVisitor.visitVarInsn(ASTORE, slotKeys2);

                methodVisitor.visitVarInsn(ALOAD, slotKeys2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, slotSize2);

                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ISTORE, slotIdx2);

                Label loopF = new Label();
                Label finF  = new Label();
                methodVisitor.visitLabel(loopF);
                methodVisitor.visitVarInsn(ILOAD, slotIdx2);
                methodVisitor.visitVarInsn(ILOAD, slotSize2);
                methodVisitor.visitJumpInsn(IF_ICMPGE, finF);

                // key = keys.get(idx)
                int slotKey2 = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotKeys2);
                methodVisitor.visitVarInsn(ILOAD, slotIdx2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitVarInsn(ASTORE, slotKey2);

                // sb.append("  \"key\": ")
                methodVisitor.visitVarInsn(ALOAD, slotSb2);
                methodVisitor.visitLdcInsn("  \"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ALOAD, slotKey2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitLdcInsn("\": ");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);

                // val = map.get(key)
                int slotVal2 = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotMapa2);
                methodVisitor.visitVarInsn(ALOAD, slotKey2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                methodVisitor.visitVarInsn(ASTORE, slotVal2);

                int slotValStr2 = contadorSlots++;
                methodVisitor.visitVarInsn(ALOAD, slotVal2);
                Label noEsStr2 = new Label();
                Label finVal2  = new Label();
                methodVisitor.visitTypeInsn(INSTANCEOF, "java/lang/String");
                methodVisitor.visitJumpInsn(IFEQ, noEsStr2);
                // es String
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitLdcInsn("\"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ALOAD, slotVal2);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitLdcInsn("\"");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitVarInsn(ASTORE, slotValStr2);
                methodVisitor.visitJumpInsn(GOTO, finVal2);
                methodVisitor.visitLabel(noEsStr2);
                methodVisitor.visitVarInsn(ALOAD, slotVal2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitVarInsn(ASTORE, slotValStr2);
                methodVisitor.visitLabel(finVal2);

                // sb.append(valStr)
                methodVisitor.visitVarInsn(ALOAD, slotSb2);
                methodVisitor.visitVarInsn(ALOAD, slotValStr2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);

                // coma si no es el último
                Label noComaF = new Label();
                methodVisitor.visitVarInsn(ILOAD, slotIdx2);
                methodVisitor.visitVarInsn(ILOAD, slotSize2);
                methodVisitor.visitInsn(ICONST_1);
                methodVisitor.visitInsn(ISUB);
                methodVisitor.visitJumpInsn(IF_ICMPGE, noComaF);
                methodVisitor.visitVarInsn(ALOAD, slotSb2);
                methodVisitor.visitLdcInsn(",");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);
                methodVisitor.visitLabel(noComaF);

                // sb.append("\n")
                methodVisitor.visitVarInsn(ALOAD, slotSb2);
                methodVisitor.visitLdcInsn("\n");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitInsn(POP);

                methodVisitor.visitIincInsn(slotIdx2, 1);
                methodVisitor.visitJumpInsn(GOTO, loopF);
                methodVisitor.visitLabel(finF);

                // sb.append("}").toString()
                methodVisitor.visitVarInsn(ALOAD, slotSb2);
                methodVisitor.visitLdcInsn("}");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "toString", "()Ljava/lang/String;", false);
                break;
            }
        }
    }

    private TipoDato inferirTipoArgConsola(Expresion arg) {
        if (arg instanceof LiteralNumero) return TipoDato.ENTERO;
        if (arg instanceof LiteralString) return TipoDato.TEXTO;
        if (arg instanceof Concatenacion) return TipoDato.TEXTO;
        if (arg instanceof Variable) {
            TipoDato t = tabla.obtenerTipo(((Variable) arg).getNombre());
            return t;
        }
        if (arg instanceof LlamadaFuncion) {
            LlamadaFuncion lf = (LlamadaFuncion) arg;
            if (!lf.getObjeto().isEmpty() && esLista(lf.getObjeto())) {
                TipoDato tr = tipoRetornoMetodoLista(lf.getMetodo());
                return tr != null ? tr : TipoDato.ENTERO;
            }
            if (!lf.getObjeto().isEmpty() && esMetodoJsn(lf.getMetodo())) {
                TipoDato tr = tipoRetornoMetodoJsn(lf.getMetodo());
                return tr != null ? tr : TipoDato.ENTERO;
            }
        }
        if (arg instanceof OperacionBinaria) return TipoDato.ENTERO;
        // Concatenacion interpolada → TEXTO
        return TipoDato.TEXTO;
    }

    private void generarLlamadaConsola(LlamadaFuncion llamada) {
        String metodo = llamada.getMetodo();

        if (metodo.equals("mostrar")) {
            if (llamada.getArgumentos().isEmpty()) {
                methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
                return;
            }

            Expresion arg = llamada.getArgumentos().get(0);
            // Siempre cargar System.out PRIMERO
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

            // Detectar tipo del argumento para elegir println correcto
            TipoDato tipoArg = inferirTipoArgConsola(arg);

            if (tipoArg == TipoDato.LISTA) {
                // Lista → cargar ArrayList y llamar toString() → println(String)
                generarExpresion(arg);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
                        "println", "(Ljava/lang/String;)V", false);
            } else if (tipoArg == TipoDato.ENTERO || tipoArg == TipoDato.LOG) {
                // Entero directo → println(int)
                generarExpresion(arg);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
                        "println", "(I)V", false);
            } else {
                // Texto, interpolación, objetos → convertir a String → println(String)
                generarExpresionString(arg);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
                        "println", "(Ljava/lang/String;)V", false);
            }
            return;

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

        } else if (expr instanceof OperacionBinaria op) {
            if (op.getOperador().equals("+")) {
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder",
                        "<init>", "()V", false);
                agregarAStringBuilder(op.getIzquierda());
                agregarAStringBuilder(op.getDerecha());
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "toString", "()Ljava/lang/String;", false);
            }

        } else if (expr instanceof ConversionTexto) {
            Expresion interna = ((ConversionTexto) expr).getExpresion();
            generarExpresion(interna);
            TipoDato tipo = inferirTipoCompleto(interna);
            if (tipo == TipoDato.TEXTO) {
                // ya es String, nada que hacer
            } else if (tipo == TipoDato.LISTA) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.JSN) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
            } else {
                // ENTERO, LOG, NUMERO -> int -> String
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(I)Ljava/lang/String;", false);
            }

        } else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
                // ya es String, nada que hacer
            } else if (tipo == TipoDato.LISTA) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.JSN) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "toString", "()Ljava/lang/String;", false);
            } else {
                // ENTERO, LOG, NUMERO -> int -> String
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(I)Ljava/lang/String;", false);
            }

        } else if (expr instanceof LiteralNumero) {
            generarLiteralNumero((LiteralNumero) expr);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(I)Ljava/lang/String;", false);

        } else if (expr instanceof LlamadaFuncion) {
            LlamadaFuncion llamada = (LlamadaFuncion) expr;

            // 1) Metodo de JSN: datos.texto(), persona.claves(), persona.contiene_clave(..), etc.
            if (!llamada.getObjeto().isEmpty()
                    && !llamada.getObjeto().equals("consola")
                    && esJsn(llamada.getObjeto())
                    && esMetodoJsn(llamada.getMetodo())) {
                generarMetodoJsn(llamada);
                TipoDato tr = tipoRetornoMetodoJsn(llamada.getMetodo());
                if (tr == TipoDato.LISTA) {
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                            "toString", "()Ljava/lang/String;", false);
                } else if (tr == TipoDato.LOG) {
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                            "valueOf", "(I)Ljava/lang/String;", false);
                }
                // TEXTO ya queda bien sin conversion

                // 2) Metodo de lista: SOLO si el objeto ES realmente una lista
            } else if (!llamada.getObjeto().isEmpty()
                    && !llamada.getObjeto().equals("consola")
                    && esLista(llamada.getObjeto())) {
                generarMetodoLista(llamada);
                TipoDato tr = tipoRetornoMetodoLista(llamada.getMetodo());
                if (tr == TipoDato.LISTA) {
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                            "toString", "()Ljava/lang/String;", false);
                } else if (tr == TipoDato.ENTERO || tr == TipoDato.LOG || tr == TipoDato.NUMERO) {
                    methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                            "valueOf", "(I)Ljava/lang/String;", false);
                } else if (tr == null) {

                    String elem = tipoElementoLista(llamada.getObjeto());
                    if (elem.equals("entero") || elem.equals("log") || elem.equals("numero")) {
                        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                                "valueOf", "(I)Ljava/lang/String;", false);
                    }

                }

            } else if (!llamada.getObjeto().isEmpty()
                    && !llamada.getObjeto().equals("consola")) {
                generarLlamadaMetodoObjeto(llamada);

                // 4) Funcion global de usuario
            } else {
                generarLlamadaFuncionUsuario(llamada);
            }

        } else if (expr instanceof AccesoJsn) {
            generarAccesoJsn((AccesoJsn) expr);
            // Castear a String porque se usa como texto
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
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
            // Una ConversionTexto dentro de una Concatenacion/interpolacion.
            // Flujo: generar la expresion interna, convertir a String segun su tipo real,
            // y SIEMPRE cerrar con un append(String) al StringBuilder.
            Expresion interna = ((ConversionTexto) expr).getExpresion();
            TipoDato tipo = inferirTipoExpresion(interna);
            generarExpresion(interna);

            if (tipo == TipoDato.TEXTO) {
                // La expresion interna ya dejo un String en la pila, no se convierte.
            } else if (tipo == TipoDato.LISTA) {
                // ArrayList -> toString() (devuelve String)
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.JSN) {
                // LinkedHashMap -> toString() (devuelve String)
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedHashMap",
                        "toString", "()Ljava/lang/String;", false);
            } else if (tipo == TipoDato.OBJETO) {
                // Object generico -> String.valueOf(Object) (maneja null con seguridad)
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
            } else {
                // ENTERO, LOG, NUMERO -> int -> String
                methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(I)Ljava/lang/String;", false);
            }

            // Cierre OBLIGATORIO: append(String) al StringBuilder que ya esta en la pila
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        }

        else if (expr instanceof Variable) {
            String nombre = ((Variable) expr).getNombre();
            generarVariable((Variable) expr);
            TipoDato tipo = tabla.obtenerTipo(nombre);
            if (tipo == TipoDato.TEXTO || tipo == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else if (tipo == TipoDato.LISTA) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
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
            LlamadaFuncion llamada = (LlamadaFuncion) expr;
            TipoDato tipoRetorno;

            if (!llamada.getObjeto().isEmpty() && esLista(llamada.getObjeto())) {
                generarMetodoLista(llamada);
                tipoRetorno = tipoRetornoMetodoLista(llamada.getMetodo());
                if (tipoRetorno == null) {
                    // primero/ultimo/quitar_en: tipo depende del elemento
                    String elem = tipoElementoLista(llamada.getObjeto());
                    if (elem.equals("texto")) {
                        tipoRetorno = TipoDato.TEXTO;
                    } else {
                        tipoRetorno = TipoDato.ENTERO;
                    }
                }
            }
           else if (!llamada.getObjeto().isEmpty() && esMetodoJsn(llamada.getMetodo())) {
                generarMetodoJsn(llamada);
                tipoRetorno = tipoRetornoMetodoJsn(llamada.getMetodo());
                if (tipoRetorno == null) tipoRetorno = TipoDato.ENTERO;
            } else if (!llamada.getObjeto().isEmpty() && !llamada.getObjeto().equals("consola")) {
                generarLlamadaMetodoObjeto(llamada);
                String claseObjeto = inferirClaseObjeto(llamada.getObjeto());
                NodoObjeto nodoObjeto = objetos.get(claseObjeto);
                tipoRetorno = TipoDato.ENTERO;
                if (nodoObjeto != null) {
                    for (NodoMetodo m : nodoObjeto.getMetodos()) {
                        if (m.getNombre().equals(llamada.getMetodo())) {
                            tipoRetorno = convertirTipo(m.getTipoRetorno());
                            break;
                        }
                    }
                }
            } else {
                generarLlamadaFuncionUsuario(llamada);
                NodoFuncion funcion = funciones.get(llamada.getMetodo());
                tipoRetorno = funcion != null
                        ? convertirTipo(funcion.getTipoRetorno())
                        : TipoDato.ENTERO;
            }

            if (tipoRetorno == TipoDato.LISTA) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else if (tipoRetorno == TipoDato.TEXTO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else if (tipoRetorno == TipoDato.OBJETO) {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                        "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                        "append", "(I)Ljava/lang/StringBuilder;", false);
            }
        }

        else if (expr instanceof AccesoJsn) {
            generarAccesoJsn((AccesoJsn) expr);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        }
    }
}