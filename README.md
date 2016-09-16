# camunda-nuxeo-connector

Nuxeo connector intended for shared usage by Camunda Workflows.

Contains the connector. The Delegates are in the nuxeo-process-test project.

Configuration: /src/main/resources/camunda-nuxeo-connector.properties

# Installation

1. Import into Eclipse with Maven support
2. This Project is a required Dependency for the nuxeo-process-test project and therefore You need to install this project in Your local maven repository. Otherwise the other project cannot be used (missing Dependency). To do that run `mvn install` on this project and You are good to go! 
3. Go to the nuxeo-process-test project and read the readme for further steps!

## Note

The nuxeo-process-test project is in such a way configured that it will generate two .jar-Files when You run `mvn jar:jar`. One contains all the necessary dependencies and one with the actual code of the nuxeo-process-test project!
