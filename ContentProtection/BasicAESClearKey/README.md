---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Dynamically encrypt your content with AES-128 

This sample demonstrates how to dynamically encrypt your content with AES-128. It shows how to perform the following tasks:

1. Create a transform with built-in AdaptiveStreaming preset
1. Submit a job
1. Create a ContentKeyPolicy using a secret key
1. Associate the ContentKeyPolicy with StreamingLocator
1. Get a token and print a URL for playback 
 
When a stream is requested by a player, Media Services uses the specified key to dynamically encrypt your content with AES-128 and Azure Media Player uses the token to decrypt.

> [!TIP]
> The `BasicAESClearKey.java` file (in the `BasicAESClearKey\src\main\java\sample` folder) has extensive comments.

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

* [Dynamic packaging](https://docs.microsoft.com/azure/media-services/latest/dynamic-packaging-overview)
* [Content protection with dynamic encryption](https://docs.microsoft.com/azure/media-services/latest/content-protection-overview)
* [Streaming Policies](https://docs.microsoft.com/azure/media-services/latest/streaming-policy-concept)

## Next steps

- [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
- [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)

