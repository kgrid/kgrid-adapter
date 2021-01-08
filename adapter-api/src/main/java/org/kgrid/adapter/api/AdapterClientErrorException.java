package org.kgrid.adapter.api;

public class AdapterClientErrorException extends AdapterException {
    public AdapterClientErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdapterClientErrorException(String message) {
        super(message);
    }
}
