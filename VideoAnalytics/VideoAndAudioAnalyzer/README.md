---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Analyze videos with a VideoAnalyzerPreset transform

This sample illustrates how to create a video analyzer transform, upload a video file to an input asset, submit a job with the transform and download the results for verification.

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

* [Analyzing video and audio files](https://docs.microsoft.com/azure/media-services/latest/analyzing-video-audio-files-concept)
* [Transforms and Jobs](https://docs.microsoft.com/azure/media-services/latest/transforms-jobs-concept)
* [Standard Encoder formats and codecs](https://docs.microsoft.com/azure/media-services/latest/media-encoder-standard-formats)
* [Media Services job error codes](https://docs.microsoft.com/azure/media-services/latest/job-error-codes)

## Next steps

- [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
- [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)


