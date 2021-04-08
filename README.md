# Kgrid-Adapter
[![CircleCI](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master.svg?style=shield)](https://circleci.com/gh/kgrid/kgrid-adapter/tree/master)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Adapters provide a common interface to load & execute code in the Knowledge Grid

## Table of Contents

1. [Build This Project](#build-this-project)
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


You can load your adapter by adding the uri of your built adapter jar file to the comma-separated list of adapter locations property in the activator, ex:
add the line `kgrid.activator.adapter-locations=file:adapters/myadapter.jar` to the activator's `application.properties` file or supply it as an argument when starting the activator.