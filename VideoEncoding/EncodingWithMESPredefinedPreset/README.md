---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Encode files with predefined presets of Media Encoder Standard 

This sample shows how to submit a job using a built-in preset and an HTTP URL input, publish output asset for streaming, and download results for verification.

## Prerequisites

* Java JDK 1.8 or newer installed
* Maven installed
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Running the example

* Configure `appsettings.json` with appropriate access values.

    Get credentials needed to use Media Services APIs by following [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to). Open the `src/main/resources/conf/appsettings.json `configuration file and paste the values in the file.
* Clean and build the project. 

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.
* Run this project. 

    Execute `mvn exec:java`, then follow the instructions in the output console.

## Key concepts

* [Encoding with Media Services](https://docs.microsoft.com/azure/media-services/latest/encoding-concept)
* [Transforms and Jobs](https://docs.microsoft.com/azure/media-services/latest/transforms-jobs-concept)
* [Standard Encoder formats and codecs](https://docs.microsoft.com/azure/media-services/latest/media-encoder-standard-formats)
* [Media Services job error codes](https://docs.microsoft.com/azure/media-services/latest/job-error-codes)

## Next steps

- [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
- [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)


