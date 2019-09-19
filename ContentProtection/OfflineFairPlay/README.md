---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Offline playback with FairPlay DRM

This sample demonstrates how to dynamically encrypt your content with FairPlay DRM and play the content without requesting a license from license service. It shows how to perform the following tasks:

1. Creates a transform with built-in AdaptiveStreaming preset
1. Submits a job
1. Creates a ContentKeyPolicy with open restriction and FairPlay persistent configuration
1. Creates a custom streaming policy
1. Associates the ContentKeyPolicy and the StreamingPolicy with a StreamingLocator
1. Prints a url for playback

 When a user requests FairPlay protected content for the first time, the player application requests a license from the Media Services license service. If the player application is authorized, the license service issues a license to the player and the license is persisted. Because the license is persisted, subsequent playback won't send a request to license service again.

> [!TIP]
> The `OfflineFairPlay.java` file has extensive comments.

## Prerequisites

* Java JDK 1.8 or newer installed.
* Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).
* An Apple ASK(Application Secret Key).
* An Apple certificate(.pfx) and password.

## Running the example

### Configure `appsettings.json` with appropriate access values

    Get credentials needed to use Media Services APIs by following [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to). Open the `src/main/resources/conf/appsettings.json` configuration file and paste the values in the file.

### Clean and build the project

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.

### Run this project

    Execute `mvn exec:java`, then follow the instructions in the output console.

### Optional, do the following steps if you want to use Event Grid for job monitoring

Please note, there are costs for using Event Hub. For more details, refer [Event Hubs pricing](https://azure.microsoft.com/en-in/pricing/details/event-hubs/) and [FAQ](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-faq#pricing)

* Enable Event Grid resource provider

  `az provider register --namespace Microsoft.EventGrid`

* To check if registered, run the next command. You should see "Registered"

  `az provider show --namespace Microsoft.EventGrid --query "registrationState"`

* Create an Event Hub

  `namespace=<unique-namespace-name>`\
  `hubname=<event-hub-name>`\
  `az eventhubs namespace create --name $namespace --resource-group <resource-group>`\
  `az eventhubs eventhub create --name $hubname --namespace-name $namespace --resource-group <resource-group>`

* Subscribe to Media Services events

  `hubid=$(az eventhubs eventhub show --name $hubname --namespace-name $namespace --resource-group <resource-group> --query id --output tsv)`\
  `amsResourceId=$(az ams account show --name <ams-account> --resource-group <resource-group> --query id --output tsv)`\
  `az eventgrid event-subscription create --resource-id $amsResourceId --name <event-subscription-name> --endpoint-type eventhub --endpoint $hubid`

* Create a storage account and container for Event Processor Host if you don't have one
  [Create a storage account for Event Processor Host
  ](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-dotnet-standard-getstarted-send#create-a-storage-account-for-event-processor-host)

* Update appsettings.json with your Event Hub and Storage information
  StorageAccountName: The name of your storage account.\
  StorageAccountKey: The access key for your storage account. Navigate to Azure portal, "All resources", search your storage account, then "Access keys", copy key1.\
  StorageContainerName: The name of your container. Click Blobs in your storage account, find you container and copy the name.\
  EventHubConnectionString: The Event Hub connection string. search your namespace you just created. &lt;your namespace&gt; -&gt; Shared access policies -&gt; RootManageSharedAccessKey -&gt; Connection string-primary key.\
  EventHubName: The Event Hub name.  &lt;your namespace&gt; -&gt; Event Hubs.

## Key concepts

* [Dynamic packaging](https://docs.microsoft.com/azure/media-services/latest/dynamic-packaging-overview)
* [Content protection with dynamic encryption](https://docs.microsoft.com/azure/media-services/latest/content-protection-overview)
* [Streaming Policies](https://docs.microsoft.com/azure/media-services/latest/streaming-policy-concept)

## Next steps

* [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
* [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)
