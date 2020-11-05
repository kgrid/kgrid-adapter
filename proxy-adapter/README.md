# Kgrid Proxy Adapter API with Remote Runtime Proxy 


When using the Proxy Adapter the Activator delegates most activation, deployment and request handling for knowledge objects services (KO endpoints) to the remote runtime using the Proxy API.

A Kgrid-enabled runtime can implement a simple API for interacting with the KGrid Activator. Using the REST API, the runtime proxy component can register with an Activator, receive activation messages for specific KO endpoints, fetch code and other artifacts to deploy, and handle requests routed to each deployed endpoint in the runtime.

## Proxy Adapter API (Activator side)

THe `/proxy` base url is reserved for the Proxy Adapter API and can be changed by setting:
```
kgrid.adapter.proxy.base={custom-proxy-base}
```
Note that changing the proxy base means that runtimes should be configured with or discover the new url locations.

### Get `/proxy/environments`
Returns a list of the runtime environments registered with the proxy adapter:
```json
[
    {
        "engine": "node",
        "url": "http://localhost:3000",
        "status": "up"
    },
    {
        "engine": "python",
        "url": "http://example.com/python-runtime",
        "status": "error",
        "error_details": "Remote server address not set, check that the remote environment for node has been set up."
    }
]
```

### Get `/proxy/environments/{type}`
Returns an individual runtime environment registered with the proxy adapter:
```json
{
    "engine": "node",
    "url": "http://localhost:3000",
    "status": "up"
}
```

### Post `/proxy/environments`
To register a remote runtime with the proxy adapter POST to the `/proxy/environments` endpoint with the following json data:
```json
{
    "engine": "node",
    "url": "http://localhost:3000"
}
```
This will return a 200 ok response with a body:
```json
{
    "engine": "node",
    "url": "http://localhost:3000",
    "registered": "success"
}
```

If you register two or more remote runtimes with the same engine then the most recently registered one will be the one the proxy adapter uses, overwriting the previously registered engine.


### Get `/proxy/**`

This returns the specified resource from the activator. The wildcard `**` can be any relative uri pointing to a resource required by the object.
 It should be used to retrieve binary files needed to execute the knowledge object that are specified in the deployment specification.

## Remote Runtime Proxy API

The remote environment's API must contain the following endpoints which will be used by the proxy adapter to activate and execute objects, returning the result to the activator.

### Get `/info`

The remote environment must have an info endpoint which returns a json object with at least a status: up value as shown below. It can also contain other data about the environment as needed for debugging or maintaining the remote runtime.
```json
{
    "status": "up",
    "activatorUrl": "http://localhost:8083",
    "app": "kgrid-python-runtime",
    "engine": "python",
    "url": "http://localhost:5000",
    "version": "0.0.11"
}
```

### Post `/endpoints`
This accepts the body of a deployment specification with added base url, and uri.
The remote runtime should use the base url and artifact list to retrieve the required resources from the proxy adapter.
```json
{
    "baseUrl":"http://localhost:8080/proxy/node/simple/v1.0/",
    "uri": "node/simple/1.0/welcome",
    "artifact":["src/welcome.js"],
    "engine":"node",
    "entry":"src/welcome.js",
    "function":"welcome"
}
```
This should return json containing url that the proxy adapter can then call with execution requests in this format:
```json
{
    "id": "node/simple/1.0/welcome",
    "uri": "-850858857",
    "activated": "Thu Nov 05 2020 14:28:22 GMT-0500 (Eastern Standard Time)",
    "status": "Activated"
}
```

### Post `/{endpoint}`
This accepts an object that contains the inputs to be used when executing the object at that url. For example the hello world
object in the node environment takes a json object in the following format:
```json
{
  "name": "Tom"
}
```

This returns the result of the execution in an object with the key "result" and other corroborating data.
For example the hello world object returns:
```json
{
    "result": "Welcome to Knowledge Grid, Tom",
    "request_id": "4348f1a8-c6bb-435c-9a95-32bce84991f9"
}
``` 
