---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# LiveEventWithDVR
This sample first shows how to create a LiveEvent with a full archive up to 25 hours and an filter on the asset with 5 minutes DVR window, then it shows how to use the filter to create a locator for streaming.

## Prerequisites
To run the sample, you need:
  * A camera connected to your compueter.
  * A media encoder. For a recommended encoder, please visit https://docs.microsoft.com/en-us/azure/media-services/latest/recommended-on-premises-live-encoders.
  * Java JDK and Maven installed.
  * An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Build and run
  * Add appropriate values to the src/main/resources/conf/appsettings.json configuration file. For more information, see [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to).
  * To clean and build the project, in a cmd or shell window, go to the root folder of this project, run "mvn clean compile".
  * To run this project, execute "mvn exec:java", then follow the instructions in the output console.
  
