# Kgrid-Adapter
[![CircleCI](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master.svg?style=shield)](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Adapters provide a common interface to load & execute code in the Knowledge Grid

## Table of Contents

1. [Build This Project](#build-this-project)
1. [KGrid Adapters](#kgrid-adapters) 
   1. [Javascript V8 Adapter](#javascript-v8-adapter)
   1. [Proxy Adapter](#proxy-adapter)
   1. [Resource Adapter](#resource-adapter)
   1. [Javascript Nashorn Adapter](#javascript-nashorn-adapter-no-longer-in-active-development)
1. [Creating a new Adapter](#creating-a-new-adapter)

## Build This Project
These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy these adapters in an activator on a live system.

### Prerequisites
For building and running the application you need:

- [JDK 11](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
- [Maven 3](https://maven.apache.org)

### Clone
To get started you can simply clone this repository using git:
```
git clone https://github.com/kgrid/kgrid-adapter.git
cd kgrid-adapter
```
Install the adapters to your local maven repository where they can then be used as dependencies by the activator:
```
mvn clean install
```
### Running the tests

Unit and Integration tests can be executed via
```
mvn clean test
```

## KGrid Adapters

The KGrid team is currently working on several adapters that demonstrate how to run code in different environments and support running objects that use many languages.

### Javascript v8 Adapter

This javascript engine uses the GraalVM javascript engine to run object payloads written in javascript locally.
Any simple javascript file containing functions can be easily run using this adapter but more complex objects with 
external dependencies should be bundled into one file. (See the guide on creating a bundled KO.)

Example deployment descriptor:
```yaml
  /welcome:
    artifact:
      - 'src/welcome.js'
    engine: 'javascript'
    function: 'welcome'
```
#### Pitfalls and current limitations

Currently the v8 engine cannot return native javascript arrays. You can work around this problem by using javascript objects instead. 
Or use the graal polyglot methods for creating a java array in javascript: 
```javascript
let intArray = Java.type('int[]');
let iarr = new intArray(3);
return iarr;
```

Calling other knowledge grid objects from javascript is supported but limited to passing primitive inputs due to how graal manages contexts.
You can call another knowledge object from within your object using the code:
```javascript
    let executor = context.getExecutor("hello-world/v1.0/welcome");
    return executor.execute(name);
```
Where `hello-world/v1.0/welcome` is the full arkId, version and endpoint name of the endpoint you wish to call and `name` is the input
you wish to pass to it. **Note that the input can only be a primitive(booleans, numbers or strings) and not an object.** You can work around this by using `JSON.stringify()`
to convert objects to strings before passing them between methods and `JSON.parse` to convert the JSON back into a string in the receiving object.
See [issue 631](https://github.com/oracle/graal/issues/631) for the graal project for updates.

**It is important to note, that there can be only one adapter of a particular engine supplied to the activator,
so the V8 Graal Adapter and the Javascript Nashorn Adapter cannot be used simultaneously.

See the [Javascript V8 Adapter](https://github.com/kgrid/javascript-v8-adapter) Readme for more information.

### Proxy Adapter
The proxy adapter exposes endpoints which can be to register external runtime environments and make them accessible to execute object paylods.

There is a [node external runtime](https://github.com/kgrid/kgrid-node-runtime) that runs objects in the NodeJS environment and a [python runtime](https://github.com/kgrid/kgrid-python-runtime) that runs objects in a native python 3 environment.

To run objects in a remote environment download, install and run that environment project after starting the activator. 
Note that the deployment descriptor of a proxy adapter object contains two more fields than a local javascript object:
```yaml
/welcome:
  post:
    artifact:
      - 'src/welcome.js'
    engine: 'node'
    entry: 'src/welcome.js'
    function: 'welcome'
```
The `engine` field tells the adapter which remote environment the code is run in and the `entry` field specifies the file that contains the main function.

The proxy adapter can run any nodejs or python project that you can run locally by communicating with the associated runtimes.

See the [Proxy Adapter Readme](https://github.com/kgrid/kgrid-adapter/tree/main/proxy-adapter) for more information.
### Resource Adapter

Example deployment descriptor:
```yaml
/welcome:
  get:
    artifact: 'src/welcome.js'
    engine: 'resource'
```

- The endpoints that the activator exposes to work with this adapter are:

  `
  GET <activator url>/<naan>/<name>/<api version>/<endpoint>
  `

  and

  `
  GET <activator url>/<naan>/<name>/<api version>/<endpoint>/<filename>
  `

  See the [Resource Adapter Readme](https://github.com/kgrid/resource-adapter) for more information.


#### Pitfalls and current limitations

### Javascript Nashorn Adapter (no longer in active development)
The nashorn engine used by this adapter is being removed and use of this adapter is discouraged. Instead use either the js v8 adapter or the proxy adapter.

Example deployment descriptor:
```yaml
  /welcome:
    artifact: 'src/welcome.js'
    engine: 'javascript'
    function: 'welcome'
```

### Pitfalls and current limitations

The nashorn adapter transforms javascript arrays to a map with the key for each value set to the value's index in the
array when it returns the value. Because of this using javascript arrays is discouraged.

**It is important to note, that there can be only one adapter of a particular engine supplied to the activator,
so the V8 Graal Adapter and the Javascript Nashorn Adapter cannot be used simultaneously.

## Creating a new Adapter
Creating new Adapters requires that you implement the Adapter Java API interface which you can find in the `adapter-api` directory.
For example:

```java
package org.kgrid.adapter.mine;

// Imports...

public class MyAdapter implements Adapter {
  List<String> getEngines() {
    // Must return a list of engines that object developers
    // can use in the deployment descriptor engine node to specify this adapter.
    // For example:
    return new Collections.singletonList("my-engine");
  }

  void initialize(ActivationContext context) {
    // This is called once by the activator at startup and 
    // sets up this adapter using the activation
    // context passed in from the activator.
    // The activation context provides four methods which support
    // accessing other objects and properties in the activator
    // - Get other executors using
    //   Executor getExecutor(String key); 
    //   where the key is the full endpointURI of the other executor.
    // - Get binaries from the associated shelf using
    //   InputStream getBinary(URI pathToBinary);
    // - Get properties using
    //   String getProperty(String key);
    // - Refresh objects associated with your adapter using
    //   void refresh(String engineName);
  }

  Executor activate(URI absoluteLocation, URI endpointURI, JsonNode deploymentSpec) {
    // Called when an object is activated and must
    // return an executor which will be called when
    // the endpoint specified by the endpoint URI is
    // accessed through the activator.
  }

  String status() {
    // Used by the activator to determine the state
    // of this adapter. Return values which are useful
    // in debugging issues with this adapter. Like "up"
    // "down" or "initializing", etc. This value is passed through
    // to the /actuator/health endpoint in the activator.
  }
}
```
For an example check out the [javascript v8 adapter](https://github.com/kgrid/javascript-v8-adapter).

The adapters utilize Java Services and require that you package your adapter with a service identifier the META-INF directory.
Create a file in the `resources/META-INF/services` directory called `org.kgrid.adapter.api.Adapter` with a single line that is the fully qualified class name of your adapter eg: `org.kgrid.adapter.mine.MyAdapter`. 


You can load your adapter by adding the uri of your built adapter jar file to the comma-seperated list of adapter locations property in the activator, ex:
add the line `kgrid.activator.adapter-locations=file:adapters/myadapter.jar` to the activator's `application.properties` file or supply it as an argument when starting the activator.