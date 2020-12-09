package org.kgrid.adapter.proxy;

public class RuntimeDetails {
    private String engine;
    private String version;
    private String address;

    public RuntimeDetails(String engine, String version, String address) {
        this.engine = engine;
        this.version = version;
        this.address = address;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
