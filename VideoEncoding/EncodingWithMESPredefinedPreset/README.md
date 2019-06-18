---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

#EncodingWithMESPredefinedPreset
This sample shows how to submit a job using a built-in preset and an HTTP URL input, publish output asset for straming, and download results for verification.

## Prerequisites
  * Java JDK and Maven installed.
  * An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## To build and run
  * Add appropriate values to the src/main/resources/conf/appsettings.json configuration file. For more information, see [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to).
  * To clean and build the project, in a cmd or shell window, go to the root folder of this project, run "mvn clean compile".
  * To run this project, execute "mvn exec:java", then follow the instructions in the output console.