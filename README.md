# QoS Analysis of Smart Distribution Grids with Network Calculus

This framework was created as part of the master thesis of Matthias Vietz.
The framework enables the user to analyze arbitrary smart grids to be analyzed with Network Calculus.
In a standard configuration, a Py4J server is started when the JAR is started.
This enables the user to interact with the library from either Java or Python.

## Requirements
For the compilation, a JDK 16 (tested with OpenJDK 16) is required.  
Currently, the java part is developed under IntelliJ (2022.3.2).  
The main file to be compiled is [NCEntryPoint.java](src/main/java/NCEntryPoint/NCEntryPoint.java).
You can find the pre-compiled libs needed for compilation in the [libs_compiled folder](libs_compiled/).


## How to Run
### 1. Start the java part
Either run the java project in IntelliJ directly, or compile a JAR file and execute it in the shell with:
````commandline
java -jar <<JAR_NAME>>
````

### 2. Use the library
You can now use the library via a Py4J connector. (Example tbd).