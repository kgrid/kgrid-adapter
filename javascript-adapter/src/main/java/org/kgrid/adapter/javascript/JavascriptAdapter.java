package org.kgrid.adapter.javascript;

import com.fasterxml.jackson.databind.JsonNode;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;

import javax.script.*;
import java.net.URI;
import java.nio.charset.Charset;

public class JavascriptAdapter implements Adapter {

  private ScriptEngine engine;
  private ActivationContext activationContext;

  @Override
  public String getType() {
    return "JAVASCRIPT";
  }

  @Override
  public void initialize(ActivationContext context) {

    activationContext = context;

    engine = new ScriptEngineManager().getEngineByName("JavaScript");
    engine.getBindings(ScriptContext.GLOBAL_SCOPE).put("context", activationContext);
  }

  @Override
  public Executor activate(
      URI objectLocation,
      String naan,
      String name,
      String version,
      String endpointName,
      JsonNode deploymentSpec) {
    JsonNode artifact = deploymentSpec.get("artifact");
    if (artifact == null) {
      throw new AdapterException(
          "No valid artifact specified for object with arkId " + naan + "/" + name + "/" + version);
    }
    String artifactLocation = null;
    if (artifact.isArray()) {
      if (deploymentSpec.has("entry")) {
        for (int i = 0; i < artifact.size(); i++) {
          if (deploymentSpec.get("entry").asText().equals(artifact.get(i).asText())) {
            artifactLocation = artifact.get(i).asText();
            break;
          }
        }
      } else {
        artifactLocation = artifact.get(0).asText();
      }
    } else {
      artifactLocation = artifact.asText();
    }

    // Move to use "function" as the function name instead of "entry" which now
    // specifies the main file. Fall back to entry if function isn't specified
    String functionName;
    if (deploymentSpec.has("function") && deploymentSpec.get("function") != null) {
      functionName = deploymentSpec.get("function").asText();
    } else {
      functionName = deploymentSpec.get("entry").asText();
    }

    return activate(objectLocation.resolve(artifactLocation), functionName);
  }

  private Executor activate(URI artifact, String function) {

    CompiledScript script = getCompiledScript(artifact, function);

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
        return  ((ScriptObjectMirror) bindings).callMember(function, input);
      }
    };
  }

  private CompiledScript getCompiledScript(URI artifact, String entry) {
    byte[] binary;
    try {
      binary = activationContext.getBinary(artifact);
    } catch (Exception e) {
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
