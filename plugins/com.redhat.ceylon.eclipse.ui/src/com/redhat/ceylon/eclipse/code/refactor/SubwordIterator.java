package com.redhat.ceylon.eclipse.code.refactor;

import static java.lang.Character.isUpperCase;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Text;

final class SubwordIterator implements KeyListener {
    private final Text text;
    private static final int MOD; 
    static {
        String platform = SWT.getPlatform();
        if ("carbon".equals (platform) || 
            "cocoa".equals (platform)) {
            MOD = SWT.MOD3;
        }
        else {
            MOD = SWT.MOD1;
        }
    }

    SubwordIterator(Text text) {
        this.text = text;
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.keyCode==SWT.ARROW_RIGHT && 
                e.stateMask==MOD) {
            int len = text.getText().length();
            for (int i = text.getSelection().y+1; i<len; i++) {
                if (isUpperCase(text.getText().charAt(i))) {
                    text.setSelection(i);
                    e.doit = false;
                    break;
                }
            }
        }
        if (e.keyCode==SWT.ARROW_LEFT && 
                e.stateMask==MOD) {
            for (int i = text.getSelection().x-1; i>=0; i--) {
                if (isUpperCase(text.getText().charAt(i))) {
                    text.setSelection(i);
                    e.doit = false;
                    break;
                }
            }
        }
        if (e.keyCode==SWT.ARROW_RIGHT && 
                e.stateMask==(MOD|SWT.SHIFT)) {
            int len = text.getText().length();
            for (int i = text.getSelection().y+1; i<len; i++) {
                if (isUpperCase(text.getText().charAt(i))) {
                    text.setSelection(text.getSelection().x, i);
                    e.doit = false;
                    break;
                }
            }
        }
        if (e.keyCode==SWT.ARROW_LEFT && 
                e.stateMask==(MOD|SWT.SHIFT)) {
            for (int i = text.getSelection().x-1; i>=0; i--) {
                if (isUpperCase(text.getText().charAt(i))) {
                    text.setSelection(i, text.getSelection().y);
                    e.doit = false;
                    break;
                }
            }
        }
    }
}