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

### Configure `appsettings.json` with appropriate access values

    Get credentials needed to use Media Services APIs by following [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to). Open the `src/main/resources/conf/appsettings.json` configuration file and paste the values in the file.

### Clean and build the project

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.

### Run this project

    Execute `mvn exec:java`, then follow the instructions in the output console.

### Optional, do the following steps if you want to use Event Grid for job monitoring

Please be noted, there are costs for using Event Hub. For more details, refer [Event Hubs pricing](https://azure.microsoft.com/en-in/pricing/details/event-hubs/) and [FAQ](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-faq#pricing)

* Enable Event Grid resource provider

  `az provider register --namespace Microsoft.EventGrid`

* To check if registered, run the next command. You should see "Registered"

  `az provider show --namespace Microsoft.EventGrid --query "registrationState"`

* Create an Event Hub

  `namespace=&lt;unique-namespace-name&gt;`\
  `hubname=&lt;event-hub-name&gt;`\
  `az eventhubs namespace create --name $namespace --resource-group &lt;resource-group&gt;`\
  `az eventhubs eventhub create --name $hubname --namespace-name $namespace --resource-group &lt;resource-group&gt;`

* Subscribe to Media Services events

  `hubid=$(az eventhubs eventhub show --name $hubname --namespace-name $namespace --resource-group &lt;resource-group&gt; --query id --output tsv)`\
  `amsResourceId=$(az ams account show --name &lt;ams-account&gt; --resource-group &lt;resource-group&gt; --query id --output tsv)`\
  `az eventgrid event-subscription create --resource-id $amsResourceId --name &lt;event-subscription-name&gt; --endpoint-type eventhub --endpoint $hubid`

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
