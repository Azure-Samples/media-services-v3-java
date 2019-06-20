---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# VideoAndAudioAnalyzer

This sample illustrates how to create a video analyzer transform, upload a video file to an input asset, submit a job with the transform and download the results for verification.

## Prerequisites

* Java JDK and Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Build and run

* Add appropriate values to the src/main/resources/conf/appsettings.json configuration file. For more information, see [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to).
* To clean and build the project, in a cmd or shell window, go to the root folder of this project, run "mvn clean compile".
* To run this project, execute "mvn exec:java", then follow the instructions in the output console.
