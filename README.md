---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Azure Media Services v3 samples using Java

The samples in this repo show how to encode, package, protect your videos with Azure Media Services using Java SDK. The repo also contains samples that demonstrate how to analyze videos and perform live ingest in order to broadcast your events.  

## Contents

| Folder | Description |
|-------------|-------------|
| VideoEncoding/EncodingWithMESPredefinedPreset|The sample shows how to submit a job using a built-in preset and an HTTP URL input, publish output asset for streaming, and download results for verification.|
| LiveIngest/LiveEventWithDVR|This sample first shows how to create a LiveEvent with a full archive up to 25 hours and an filter on the asset with 5 minutes DVR window, then it shows how to use the filter to create a locator for streaming.|
| VideoAnalytics/VideoAndAudioAnalyzer|This sample illustrates how to create a video analyzer transform, upload a video file to an input asset, submit a job with the transform and download the results for verification.|
| ContentProtection/BasicAESClearKey|This sample demonstrates how to create a transform with built-in AdaptiveStreaming preset, submit a job, create a ContentKeyPolicy using a secret key, associate the ContentKeyPolicy with StreamingLocator, get a token and print a url for playback in Azure Media Player. When a stream is requested by a player, Media Services uses the specified key to dynamically encrypt your content with AES-128 and Azure Media Player uses the token to decrypt.|
| ContentProtection/BasicWidevine|This sample demonstrates how to create a transform with built-in AdaptiveStreaming preset, submit a job, create a ContentKeyPolicy with Widevine configuration using a secret key, associate the ContentKeyPolicy with StreamingLocator, get a token and print a url for playback in a Widevine Player. When a user requests Widevine-protected content, the player application requests a license from the Media Services license service. If the player application is authorized, the Media Services license service issues a license to the player. A Widevine license contains the decryption key that can be used by the client player to decrypt and stream the content.|
| ContentProtection/BasicPlayReady|This sample demonstrates how to create a transform with built-in AdaptiveStreaming preset, submit a job, create a ContentKeyPolicy with PlayReady configuration using a secret key, associate the ContentKeyPolicy with StreamingLocator, get a token and print a url for playback in a Azure Media Player. When a user requests PlayReady-protected content, the player application requests a license from the Media Services license service. If the player application is authorized, the Media Services license service issues a license to the player. A PlayReady license contains the decryption key that can be used by the client player to decrypt and stream the content.|

## Prerequisites

### Installation

- Install Maven from https://maven.apache.org/download.cgi.
- Update environment variable PATH to include Maven binaries location e.g. "c:\apache-maven-3.6.1\bin".
- Install Java JDK 1.8 or higher from http://openjdk.java.net/
- Update environment variable PATH to include JDK binaries location e.g. "C:\Program Files\Java\jdk-11.0.3\bin".

### Documentation

Review the **Concepts** section of the [Media Services documentation](https://docs.microsoft.com/en-us/azure/media-services/latest/).
 
## Setup

In CMD or other shell window:

1. Clone or download this sample repository
2. cd [respository name]
3. cd [a sample folder], e.g. "LiveIngest/LiveEventWithDVR"
4. Read README.md in the sample folder to see what key concepts to review and how to set up and run the sample.

## Next steps

- Azure Media Services pricing: https://azure.microsoft.com/en-in/pricing/details/media-services/
- .Net samples: https://github.com/Azure-Samples/media-services-v3-dotnet
