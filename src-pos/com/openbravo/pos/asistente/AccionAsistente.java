package com.openbravo.pos.asistente;

import com.openbravo.data.gui.MessageInf;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.BeanFactoryApp;
import com.openbravo.pos.forms.BeanFactoryException;
import com.openbravo.pos.forms.ProcessAction;

/** Opcion de menu Sistema -> Asistente de configuracion. */
public class AccionAsistente implements ProcessAction, BeanFactoryApp {

    private AppView app;

    @Override
    public void init(AppView app) throws BeanFactoryException {
        this.app = app;
    }

    @Override
    public Object getBean() {
        return this;
    }

    @Override
    public MessageInf execute() {
        java.awt.Component padre = app instanceof java.awt.Component ? (java.awt.Component) app : null;
        Asistente.abrir(padre, app, null);
        return null;
    }
}
