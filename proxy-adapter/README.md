# Kgrid Proxy Adapter API with Remote Runtime Proxy 


When using the Proxy Adapter the Activator delegates most activation, deployment and request handling for knowledge objects services (KO endpoints) to the remote runtime using the Proxy API.

A Kgrid-enabled runtime can implement a simple API for interacting with the KGrid Activator. Using a simple REST API, the runtime proxy component can register with an Activator, receive activation messages for specific KO endpoints, fetch code and other artifacts to deploy, and handle requests routed to each deployed endpoint the runtime.

## Proxy Adapter API (Activator side)

THe `/proxy` base url is reserved for the Proxy Adapter API and can be changed by setting:
```
kgrid.adapter.proxy.base={custom-proxy-base}
```
Note that changing the proxy base implies that runtimes can be configured with or discover the new url locations.

### Get `/proxy/runtimes`
Returns a list of the runtime environments registered with the proxy adapter:
```json
[
  {
    "type": "node",
    "url": "http://localhost:3000"
  },
  {
    "type": "example-python",
    "url": "http://example.com/python-runtime"
  }
]
```
Questions: Are multiple types allowed? Shouldn't the same term be used in the deployment descriptor and the proxy (e.g. call both `runtimes:`).

### Create a runtime (register)


```json
POST /proxy/runtimes
Content-type: application/json
{
  "type": "node",
  "url": "http://localhost:3000",
  "running": "true"
}
201 Created
Location: proxy/runtime/node
```

Questions: Should the adapter check if the runtime is up and able to accept execution requests before creating the resource? I'm not sure why...the runtime is trying to register, after all. If the type already exists should it overwrites the existing runtime? If so, what happens to the existing runtime and objects deployed to it?

Questions: What is the unique identifier of a runtime? `"type":`? `"url":`? Both? I'd say `"url":` except we'd be disallowing the use of a gateway with routing by ko-hash in front of multiple runtimes.... And if it is `"type":` then we can have only one of each and multiple type runtimes would be hard. 

Or if the Proxy Adapter returns a unique id to the runtime (or credential) then we could end up with duplicate runtimes reregistering without deleting themselves.

This bears more design work to see how typical service registries handle these concerns.

### Fetch info about a particular runtime

### Get `/proxy/runtimes/{type}`
Returns a list of the runtime environments registered with the proxy adapter:
```json
{
"type": "node",
"url": "http://localhost:3000",
""endpoints": [
  {
    "@id": "/endpoints/naan/name/api-version/endpoint",
    "url": "http://localhost:3000/hfiek78f8"
  }
]
}
```
Question: Think about `@base`, and which things are relative to what. This is a tricky one, but the resource is a runtime with a list of endpoints. Both parts should match other uses (keys, structure, etc) in the Proxy Adapter, Activator, and Runtime Proxy.

## Remote Runtime Proxy API

After registering with the proxy adapter the remote environment must have the following endpoints to accept execution requests and return the proper results to the adapter.

### Get `/info`

The remote environment must have an info endpoint which returns a json object with at least a key/value pair below. It can also contain other data about the environment as needed.
```json
{
  "status": "up"
}
```
Question: Should the /info endpoint return the same object as the Proxy Adapters `/proxy/runtime/{type}` with `"status": "up"` added?

### Post `/deployments`
This accepts the body of a deployment specification and retrieves the required resources from the shelf associated with the proxy adapter.
```json
POST `localhost:3000/deployments`
Content-type: application/json
{
  "baseUrl":"http://127.0.0.1:8082/kos/hello/proxy/v1.0/",
  "artifact":["src/welcome.js"],
  "engine":"node",
  "adapter":"PROXY",
  "entry":"src/welcome.js",
  "function":"welcome",
  "identifier":"ark:/hello/proxy",
  "version":"v1.0",
  "endpoint":"welcome"
}
```
Questions: 
* Should `/deployments` be call `/endpoints` to match the Activator API? 
* Should the `"baseUrl"` be `"@base"` (like json-ld resources)? Should we use a [`Content-location:` header](https://tools.ietf.org/html/rfc7231#section-3.1.4.2)? Both? This would make the `"@base": "/proxy/resources/naan/name/version/deployment.yaml"` and all the artifactss can be resolved against it.
* Can we make the resource being sent effectively a deployment spec, esp. once we update deployment spec to include the endpoint path?

It returns json containing url that the proxy adapter can then call with execution requests in this format:
```json
Location: /knlME7rU6X80
{
  "url": "/knlME7rU6X80",
  "activated": "Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)"
}
```
Questions: As above, can we get to an `"@id": "/knlME7rU6X80"` 

### Fetching resources

```
GET /proxy/naan/name/version/{path-to-artifact}

```

### Post `/{endpoint}`
This accepts an object that contains the inputs for an execution of the object at that url. For example the hello world
object in the node environment takes an object in the following format:
```json
{
  "name": "Hello"
}
```

This returns the result of the execution in an object with the key "result" and other corroborating data.
For example the hello world object returns:
```json
{
  "ko": "ark:/hello/proxy",
  "result": "Welcome to Knowledge Grid, Hello"
}
``` 
