package org.kgrid.adapter.api;

public class AdapterServerErrorException extends AdapterException{
    public AdapterServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdapterServerErrorException(String message) {
        super(message);
    }
}
