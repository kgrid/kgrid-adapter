#Kgrid Proxy Adapter with Remote Runtime Registration

This adapter provides a way to execute knowledge objects in any runtime that is connected to the activator through the 
service interface provided by this adapter.

## Registration API
There are two endpoints that are used when registering a new environment with the adapter and querying the list of environments
already registered with the adapter:

### Get `/proxy/environments`
Returns a list of the execution environments registered with the proxy adapter in the format:
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

### Post `/proxy/environments`
First checks if the environemt is up and able to accept execution requests then adds the environment specified in the
body of the request to the list. If the type already exists in the list it overwrites the existing environment url with
the new one.

The body of the request is identical to the form presented in the list of environments:
```json
{
  "type": "node",
  "url": "http://localhost:3000",
  "running": "true"
}
```

## Remote Executor's API

After registering with the proxy adapter the remote environment must have the following endpoints to accept execution requests and return the proper results to the adapter.

### Get `/info`

The remote environment must have an info endpoint which returns a json object with at least a key/value pair below.
It can also contain other data about the environment as needed.
```json
{
  "Status": "Up"
}
```


### Post `/deployments`
This accepts the body of a deployment specification and retrieves the required resources from the shelf associated with the proxy adapter.
```json
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

It returns json containing url that the proxy adapter can then call with execution requests in this format:
```json
{
  "endpoint_url": "knlME7rU6X80",
  "activated": "Tue Feb 18 2020 16:44:15 GMT-0500 (Eastern Standard Time)"
}
```

### Post `/{endpoint_url}`
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