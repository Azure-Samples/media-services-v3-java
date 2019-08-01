---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Create a Live Event with transcription

This sample demonstrate how to create a Live Event with transcription setup. The sample then shows how to create a locator for streaming.

## Prerequisites

To run the sample, you need:

* A camera connected to your computer.
* A media encoder. For a recommended encoder, please visit [Recommended live streaming encoders](https://docs.microsoft.com/en-us/azure/media-services/latest/recommended-on-premises-live-encoders).
* Java JDK and Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Running the example

### Configure `appsettings.json` with appropriate access values

    Get credentials needed to use Media Services APIs by following [Access APIs](https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to). Open the `src/main/resources/conf/appsettings.json` configuration file and paste the values in the file.

### Clean and build the project

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.

### Run this project

    Execute `mvn exec:java`, then follow the instructions in the output console.
    To stream with transcription, please start Media Player at [Media Player](https://ampdemo.azureedge.net/). In the Setup tab, copy/paste one of the urls displayed in the output console to the URL field and check Advanced Options. Then click Add Captions Track, type en-US in Caption Locale TextBox and click Update Player. Make sure you have turned on the Closed Captioning in Media Player and you should see transcriptions.

### Optional, do the following steps if you want to use Event Grid for event monitoring

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

* [Live streaming with Azure Media Services v3](https://docs.microsoft.com/azure/media-services/latest/live-streaming-overview)
* [Live Events and Live Outputs](https://docs.microsoft.com/azure/media-services/latest/live-events-outputs-concept)
* [Recommended live streaming encoders](https://docs.microsoft.com/azure/media-services/latest/recommended-on-premises-live-encoders)
* [Using a cloud DVR](https://docs.microsoft.com/azure/media-services/latest/live-event-cloud-dvr)
* [Live Event types comparison](https://docs.microsoft.com/azure/media-services/latest/live-event-types-comparison)
* [Live Event states and billing](https://docs.microsoft.com/azure/media-services/latest/live-event-states-billing)
* [Dynamic packaging](https://docs.microsoft.com/azure/media-services/latest/dynamic-packaging-overview)
* [Streaming Endpoint errors](https://docs.microsoft.com/azure/media-services/latest/streaming-endpoint-error-codes)

## Next steps

* [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
* [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)
