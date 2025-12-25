package com.jarida.jadxfrida.model;

public class ScriptOptions {
    private boolean logArgs = true;
    private boolean logReturn = true;
    private boolean logThread = true;
    private boolean printStack = false;
    private boolean printThis = false;
    private boolean prettyPrint = true;

    public boolean isLogArgs() {
        return logArgs;
    }

    public void setLogArgs(boolean logArgs) {
        this.logArgs = logArgs;
    }

    public boolean isLogReturn() {
        return logReturn;
    }

    public void setLogReturn(boolean logReturn) {
        this.logReturn = logReturn;
    }

    public boolean isLogThread() {
        return logThread;
    }

    public void setLogThread(boolean logThread) {
        this.logThread = logThread;
    }

    public boolean isPrintStack() {
        return printStack;
    }

    public void setPrintStack(boolean printStack) {
        this.printStack = printStack;
    }

    public boolean isPrintThis() {
        return printThis;
    }

    public void setPrintThis(boolean printThis) {
        this.printThis = printThis;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
