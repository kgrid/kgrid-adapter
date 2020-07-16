package org.kgrid.adapter.v8;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;
import org.graalvm.polyglot.*;
import org.kgrid.adapter.api.ActivationContext;
import org.kgrid.adapter.api.Adapter;
import org.kgrid.adapter.api.AdapterException;
import org.kgrid.adapter.api.Executor;

import java.nio.file.Path;
import java.nio.file.Paths;

public class JsV8Adapter implements Adapter {

  Engine engine;
  ActivationContext activationContext;

  @Override
  public String getType() {
    return "JAVASCRIPT";
  }

  @Override
  public void initialize(ActivationContext context) {

    this.activationContext = context;
    engine = Engine.newBuilder().build();
  }

  @Override
  @Deprecated
  public Executor activate(Path resource, String entry) {
    return null;
  }

  @Override
  public Executor activate(
      String objectLocation, String arkId, String endpointName, JsonNode deploymentSpec) {

    // Might need to wrap in try with resources to have context close on failure
    Context context =
        Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowExperimentalOptions(true)
            .option("js.experimental-foreign-object-prototype", "true")
            .allowHostClassLookup(className -> true)
            .allowNativeAccess(true)
            .build();
    Value function;

    String artifact = deploymentSpec.get("artifact").asText();
    String artifactLocation = Paths.get(objectLocation, artifact).toString();

    try {
      context.getBindings("js").putMember("context", activationContext);

      // Create the base function from our artifact(s)
      byte[] src = activationContext.getBinary(artifactLocation);
      context.eval("js", new String(src));
      String functionName = deploymentSpec.get("function").asText();
      function = context.getBindings("js").getMember(functionName);

    } catch (Exception e) {
      throw new AdapterException("Error loading source", e);
    }

    return new Executor() {
      @Override
      public Object execute(Object input) {
        try {
          Value result = getWrapper(context).execute(function,input);
          return result.as(Object.class);
        } catch (PolyglotException e) {
          throw new AdapterException("Code execution error", e);
        }
      }
    };
  }

  private Value getWrapper(Context context) {
    Value wrapper;// Put a helper function in the Context
    context.eval("js",
        "function wrapper(baseFunction, arg) { "
            + "/* console.log(baseFunction.name); */"
            + "let parsedArg;"
            + "try {"
            + "   parsedArg = JSON.parse(arg);"
            + "} catch (error) {"
            + "   return baseFunction(arg);"
            + "}"
            + "return baseFunction(parsedArg);"
            + "}");
    wrapper = context.getBindings("js").getMember("wrapper");
    return wrapper;
  }

  @Override
  public String status() {
    if (engine == null) {
      return "DOWN";
    }
    return "UP";
  }
}
