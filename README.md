# Kgrid-Adapter
[![CircleCI](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master.svg?style=shield)](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Adapters provide a common interface to load & execute code in the Knowledge Grid

## Table of Contents

1. [Build and Test Adapters](#build-adpaters)
1. [Javascript V8 Adapter](#javascript-v8-adapter)
1. [Javascript Nashorn Adapter](#javascript-nashorn-adapter)
1. [Proxy Adapter](#proxy-adapter)
1. [Additional Information](#additional-information)

## Build Adpaters
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
## Running the tests

#### Automated tests 
Unit and Integration tests can be executed via
```
mvn clean test
```

## Javascript v8 Adapter

This javascript engine uses the GraalVM javascript engine to run object payloads written in javascript locally.
Any simple javascript file containing functions can be easily run using this adapter but more complex objects with 
external dependencies should be bundled into one file. (See the guide on creating a bundled KO.)

### Pitfalls and current limitations

Currently the v8 engine cannot return native javascript arrays. You can work around this problem by using javascript objects instead. 
Or use the graal polyglot methods for creating a java array in javascript: 
```javascript
var intArray = Java.type('int[]');
var iarr = new intArray(3);
```

Calling other objects from within a javascript object is supported but limited to passing primitives due to how graal manages contexts.
You can call another knowledge object from within your object using the code:
```javascript
    let executor = context.getExecutor("hello-world/v1.0/welcome");
    return executor.execute(name);
```
Where `hello-world/v1.0/welcome` is the full arkId, version and endpoint name of the endpoint you wish to call and `name` is the input
you wish to pass to it. **Note that the input can only be a primitive(booleans, numbers or strings) and not an object.** You can work around this by using `JSON.stringify()`
to convert objects to strings before passing them between methods and `JSON.parse` to convert the JSON back into a string in the receiving object.
See [issue 631](https://github.com/oracle/graal/issues/631) for the graal project for updates.

## Javascript Nashorn Adapter
The nashorn engine used by this adapter is being removed and use of this adapter is discouraged. Instead use either the js v8 adapter or the proxy adapter.

## Proxy Adapter
The proxy adapter exposes endpoints which can be to register external runtime environments and make them accessible to execute object paylods.

Currently there is a [node external runtime](https://github.com/kgrid/kgrid-node-runtime) that runs objects in the NodeJS environment.
To run objects in the remove environment download, install and run that project after starting the activator. 
Note that the deployment descriptor of a proxy adapter object contains two more fields than a local javascript object:
```yaml
endpoints:
  /welcome:
    artifact:
      - 'src/welcome.js'
    adapter: 'PROXY'
    engine: 'node'
    entry: 'src/welcome.js'
    function: 'welcome'
```
The `engine` field tells the adapter which remote environment the code is run in and the `entry` field specifies the file that contains the main function.

The proxy adapter can run any NodeJS project that you can run locally.

## Additional Information

### Adding a new Adapter
Creating new Adapters requires you implement the Adapter API interface. The Adapter's utilize Java Services and require that you package your adapter with a service identifer the META-INF.
Create a file in the `resources/META-INF/services` directory called `org.kgrid.adapter` with a single line that is the fully qualified class name of your adapter. 
```
resources
  META-INF
     services
        org.kgrid.adapter
```
File contents:
`org.kgrid.adapter.v8.JsV8Adapter`

### Proposed adapters

We are expanding the functionality of the new graal-based javascript v8 adapter and hope to move on to other graalvm-supported languages.
The resource adapter is still under development and not included in the current activator.
