---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Create a Live Event and use time shifting (DVR)

This sample first shows how to create a Live Event with a full archive up to 25 hours and a filter on the asset with 5 minutes DVR window. The sample then shows how to create a locator for streaming and use the filter.

## Prerequisites

To run the sample, you need:

* A camera connected to your computer.
* A media encoder. For a recommended encoder, please visit https://docs.microsoft.com/en-us/azure/media-services/latest/recommended-on-premises-live-encoders.
* Java JDK and Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Running the example

* Configure `appsettings.json` with appropriate access values.

    Get credentials needed to use Media Services APIs by following [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to). Open the `src/main/resources/conf/appsettings.json `configuration file and paste the values in the file.
* Clean and build the project. 

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.
* Run this project. 

    Execute `mvn exec:java`, then follow the instructions in the output console.

## Key concepts

* [Live streaming with Azure Media Services v3](https://docs.microsoft.com/azure/media-services/latest/live-streaming-overview)
* [Live Events and Live Outputs](https://docs.microsoft.com/azure/media-services/latest/live-events-outputs-concept)
* [Recommended live streaming encoders](https://docs.microsoft.com/azure/media-services/latest/recommended-on-premises-live-encoders)
* [Using a cloud DVR](https://docs.microsoft.com/azure/media-services/latest/live-event-cloud-dvr)
* [Live Event types comparison](https://docs.microsoft.com/azure/media-services/latest/live-event-types-comparison)
* [Live Event states and billing](https://docs.microsoft.com/azure/media-services/latest/live-event-states-billing)
* [Dynamic packaging](https://docs.microsoft.com/azure/media-services/latest/dynamic-packaging-overview)
* [Streaming Endpoint errors](https://docs.microsoft.com/azure/media-services/latest/streaming-endpoint-error-codes)

## Next steps

- [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
- [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)



  
