package org.kgrid.adapter.javascript;

import com.fasterxml.jackson.databind.JsonNode;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;
import org.kgrid.shelf.ShelfResourceNotFound;
import org.kgrid.shelf.domain.ArkId;
import org.kgrid.shelf.repository.CompoundDigitalObjectStore;

import javax.script.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class JavascriptAdapter implements Adapter {

  Map<String, Object> endpoints;
  ScriptEngine engine;
  CompoundDigitalObjectStore cdoStore;
  private ActivationContext activationContext;

  @Override
  public String getType() {
    return "javascript".toUpperCase();
  }

  @Override
  public void initialize(ActivationContext context) {

    activationContext = context;

    engine = new ScriptEngineManager().getEngineByName("JavaScript");
    engine.getBindings(ScriptContext.GLOBAL_SCOPE).put("context", activationContext);
  }

  @Override
  public Executor activate(
      String objectLocation, ArkId arkId, String endpointName, JsonNode deploymentSpec) {
    JsonNode artifacts = deploymentSpec.get("artifact");
    String artifactLocation = null;
    if (artifacts.isArray()) {
      if (deploymentSpec.has("entry")) {
        for (int i = 0; i < artifacts.size(); i++) {
          if (deploymentSpec.get("entry").asText().equals(artifacts.get(i).asText())) {
            artifactLocation = artifacts.get(i).asText();
            break;
          }
        }
      } else {
        artifactLocation = artifacts.get(0).asText();
      }
    } else {
      artifactLocation = artifacts.asText();
    }
    if (artifactLocation == null) {
      throw new AdapterException(
          "No valid artifact specified for object with arkId " + arkId.getSlashArkVersion());
    }

    // Move to use "function" as the function name instead of "entry" which now
    // specifies the main file. Fall back to entry if function isn't specified
    String functionName;
    if (deploymentSpec.has("function")) {
      functionName = deploymentSpec.get("function").asText();
    } else {
      functionName = deploymentSpec.get("entry").asText();
    }

    return activate(Paths.get(objectLocation, artifactLocation), functionName);
  }

  @Override
  public Executor activate(Path artifact, String function) {

    CompiledScript script = getCompiledScript(artifact.toString(), function);

    final ScriptContext context = new SimpleScriptContext();
    context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

    final Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

    return new Executor() {

      @Override
      public synchronized Object execute(Object input) {

        try {
          script.eval(context);
        } catch (ScriptException ex) {
          throw new AdapterException(
              "unable to reset script context " + artifact.toString() + " : " + ex.getMessage(),
              ex);
        }
        Object output = ((ScriptObjectMirror) bindings).callMember(function, input);

        return output;
      }
    };
  }

  private CompiledScript getCompiledScript(String artifact, String entry) {
    byte[] binary;
    try {
      binary = activationContext.getBinary(artifact);
    } catch (ShelfResourceNotFound e) {
      throw new AdapterException(e.getMessage(), e);
    }
    if (binary == null) {
      throw new AdapterException(
          String.format("Can't find endpoint %s in path %s", entry, artifact));
    }
    CompiledScript script;
    try {
      script = ((Compilable) engine).compile(new String(binary, Charset.defaultCharset()));
    } catch (ScriptException e) {
      throw new AdapterException(
          "unable to compile script " + artifact + " : " + e.getMessage(), e);
    }
    return script;
  }

  @Override
  public String status() {
    if (engine == null) {
      return "DOWN";
    }
    if (activationContext == null) {
      return "DOWN";
    }
    return "UP";
  }
}
