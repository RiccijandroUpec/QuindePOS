package com.openbravo.pos.sri.config;

/**
 * JAXB (xades4j), CXF (SOAP) y jakarta.mail buscan sus implementaciones con
 * el <em>context classloader</em> del hilo actual, no con el que cargo sus
 * propias clases. En el modo fusionado este jar vive en un
 * {@code URLClassLoader} hijo del de ECOPos, y los hilos de ECOPos (el EDT,
 * el pool de {@code SwingWorker}) tienen como contexto el classloader de
 * ECOPos, que NO ve este jar - sin esto la firma falla con "Implementation of
 * Jakarta XML Binding-API has not been found".
 *
 * Fijar el classloader de este jar como contexto es inocuo para ECOPos: es
 * parent-first con ECOPos como padre, asi que ve exactamente lo mismo que
 * ECOPos mas este jar. En el modo standalone es el mismo classloader de
 * siempre (no cambia nada).
 */
public final class ClassLoaderPropio {

    private ClassLoaderPropio() {
    }

    /** Llamar al inicio de cualquier trabajo del conector que corra en un hilo que no creo el conector mismo. */
    public static void fijarEnHiloActual() {
        Thread.currentThread().setContextClassLoader(ClassLoaderPropio.class.getClassLoader());
    }
}
