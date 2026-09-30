package com.openbravo.pos.sri;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Classloader "child-first" para el jar de ecopos-sri-connector: primero las
 * clases del JDK, luego las del propio jar del conector, y solo al final las
 * de ECOPos. ECOPos trae en lib/ versiones viejas de varias librerias que el
 * conector tambien usa (wsdl4j, saaj/javax.xml.soap, JavaMail com.sun.mail,
 * commons-*, el driver MySQL 5.1) - con un URLClassLoader normal
 * (parent-first) ganarian siempre las viejas de ECOPos y la firma/SOAP/correo
 * del conector fallarian en tiempo de ejecucion (NoSuchMethodError, etc.).
 *
 * Unica excepcion: {@link EcoPosSriBridge}, el contrato compartido, siempre
 * viene de ECOPos - si no, el cast del lado de ECOPos fallaria.
 */
final class ClassLoaderConector extends URLClassLoader {

    private static final String INTERFAZ_COMPARTIDA = EcoPosSriBridge.class.getName();

    /** Classloader del JDK (bootstrap + plataforma), sin el classpath de la aplicacion. */
    private final ClassLoader jdk;

    ClassLoaderConector(URL[] urls, ClassLoader padre) {
        super(urls, padre);
        this.jdk = ClassLoader.getSystemClassLoader().getParent();
    }

    @Override
    protected Class<?> loadClass(String nombre, boolean resolver) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(nombre)) {
            Class<?> clase = findLoadedClass(nombre);
            if (clase == null && !nombre.equals(INTERFAZ_COMPARTIDA)) {
                try {
                    clase = jdk.loadClass(nombre);
                } catch (ClassNotFoundException e) {
                    // no es del JDK
                }
                if (clase == null) {
                    try {
                        clase = findClass(nombre);
                    } catch (ClassNotFoundException e) {
                        // no esta en el jar del conector - se busca en ECOPos
                    }
                }
            }
            if (clase == null) {
                clase = super.loadClass(nombre, false);
            }
            if (resolver) {
                resolveClass(clase);
            }
            return clase;
        }
    }

    @Override
    public URL getResource(String nombre) {
        URL recurso = jdk.getResource(nombre);
        if (recurso == null) {
            recurso = findResource(nombre);
        }
        return recurso != null ? recurso : super.getResource(nombre);
    }

    @Override
    public Enumeration<URL> getResources(String nombre) throws IOException {
        List<URL> recursos = new ArrayList<URL>();
        recursos.addAll(Collections.list(findResources(nombre)));
        for (URL recurso : Collections.list(getParent().getResources(nombre))) {
            if (!recursos.contains(recurso)) {
                recursos.add(recurso);
            }
        }
        return Collections.enumeration(recursos);
    }
}
