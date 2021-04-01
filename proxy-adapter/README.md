# Kgrid Proxy Adapter

This proxy adapter communicates with remote runtimes that can execute knowledge object code in a native environment such as python and return json results back to the activator.
This allows knowledge object developers to utilize unique language features which are not available in embedded java runtimes. (See the example node and python objects.)

## Installation

This is an embedded runtime, already pulled in by the activator
as a dependency. If you'd like to include it in your maven project,
do so by adding the following dependency to your pom.xml file:
```
<dependency>
  <groupId>org.kgrid</groupId>
  <artifactId>proxy-adapter</artifactId>
</dependency>
```

## Configuration
There are currently no configurable settings for this adapter.

## Start the runtime
As an embedded adapter, this will automatically be enabled when the activator starts.

##Guidance for Knowledge Object Developers
This adapter is for activating Knowledge Objects written in languages supported by connected remote runtimes. Two such runtimes are currently available: the [nodejs](https://github.com/kgrid/kgrid-node-runtime) and [python](https://github.com/kgrid/kgrid-python-runtime) runtimes.

An example KO with naan of `hello`, a name of `neighbor`, api version of `1.0`, and endpoint `welcome`,
a Deployment Specification might look like this:

```yaml
/welcome:
  post:
    artifact:
      - "src/welcome.py"
      - "src/helper-code.py"
    engine: "python"
    function: "main"
    entry: 'src/welcome.py'
```
Where `engine` is the value specified by the remote runtime environment, `function` is the name of the main entry function in the code and `entry` is the name of the file containing that function.

You would then execute this endpoint to see the code work:

`POST <activator url>/<naan>/<name>/<api version>/<endpoint>`

In this example: `POST <activator url>/hello/neighbor/1.0/welcome`
##Examples
An example KO can be found in our [example collection](https://github.com/kgrid-objects/example-collection/releases/latest) here:
[python/simple/1.0](https://github.com/kgrid-objects/example-collection/releases/latest/download/python-simple-v1.0.zip)

# API for communicating with a remote environment

When using the Proxy Adapter the Activator delegates activation, deployment and request handling for knowledge objects services (KO endpoints) to the remote runtime using the Proxy API.

A Kgrid-enabled runtime can implement a simple API for interacting with the KGrid Activator. Using the REST API, the runtime environment component can register with an Activator, receive activation messages for specific KO endpoints, fetch code and other artifacts to deploy, and handle requests routed to each deployed endpoint in the runtime.

## Proxy Adapter API (Activator side)

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
To register a remote runtime with the proxy adapter POST to the `/proxy/environments` endpoint with at least the following json data:
```json
{
    "engine": "node",
    "url": "http://localhost:3000"
}
```
This will return a 200 ok response with a body reflecting what the runtime sent in:
```json
{
    "engine": "node",
    "url": "http://localhost:3000"
}
```
You can also add any additional information that you'd like to show up in the activator's info endpoint. For example, the runtime version is recommended:
```json
{
    "engine": "node",
    "url": "http://localhost:3000",
    "version":"1.2.3"
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
Also, if the Knowledge Object was packaged with the Kgrid CLI, it will be
available for the remote runtimes to use in their Cache Strategy.
```json
{
    "baseUrl":"http://localhost:8080/proxy/node/simple/v1.0/",
    "uri": "node/simple/1.0/welcome",
    "artifact":["src/welcome.js"],
    "engine":"node",
    "entry":"src/welcome.js",
    "function":"welcome",
    "checksum": "123ac3b3d5e8f2"
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
This is the request endpoint described by the KO, and exposed by the activator. 
This accepts the inputs to be used when executing the object at that url, 
which should be specified in the KO. 

For example, the node/simple/1.0 object in the node environment takes an input of a json object in the 
following format:
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

Results from remote runtimes will be parsed as follows:

####1. If the result is valid JSON, and contains the key "result", 
```json
{
    "result": "Welcome to Knowledge Grid, Tom",
    "someOtherInfo": "This came from the C++ runtime"
}
```
the value of result from the runtime
   will be returned to the activator (The activator, may also wrap this result)
```text
Welcome to Knowledge Grid, Tom
``` 
WARNING: If your object's response is an object that contains the key "result", the rest of the response will be thrown away.
For example if the response contains only information intended to be part of the response, 
but one of the keys is labeled "result":
```json
{
    "result": "Welcome to Knowledge Grid, Tom",
    "alsoPartOfTheAnswer": "You have 5 minutes to move your car, or it will be crushed into a cube",
    "thisIsImportantToo": "Your car has been crushed into a cube",
    "thisAsWell": "You have 5 minutes to move your cube"
}
```
only the field "result" will be returned:
```text
Welcome to Knowledge Grid, Tom
``` 

####2. If the result is valid JSON, but does not contain the key "result",
```json
{
  "greeting": "Welcome to Knowledge Grid, Tom"
}
``` 
   the entire JSON node will be returned to the activator.
```json
{
 "greeting": "Welcome to Knowledge Grid, Tom"
}
``` 

####3. If the result is not valid JSON, the raw result will be returned to the activator.
```text
Welcome to Knowledge Grid, Tom
``` 
