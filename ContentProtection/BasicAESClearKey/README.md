---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# BasicAESClearKey

This sample illustrates how to create a transform with built-in AdaptiveStreaming preset, submit a job, create a ContentKeyPolicy using a secret key, associate the ContentKeyPolicy with StreamingLocator, get a token and print a url for playback in Azure Media Player. When a stream is requested by a player, Media Services uses the specified key to dynamically encrypt your content with AES-128 and Azure Media Player uses the token to decrypt.

## Prerequisites

* Java JDK 1.8 or newer installed
* Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Build and run

* Add appropriate values to the src/main/resources/conf/appsettings.json configuration file. For more information, see [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to).
* To clean and build the project, in a cmd or shell window, go to the root folder of this project, run "mvn clean compile".
* To run this project, execute "mvn exec:java", then follow the instructions in the output console.
