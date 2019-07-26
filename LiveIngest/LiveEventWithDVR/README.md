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
* A media encoder. For a recommended encoder, please visit [Recommended live streaming encoders](https://docs.microsoft.com/en-us/azure/media-services/latest/recommended-on-premises-live-encoders).
* Java JDK and Maven installed.
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Running the example

### Configure `appsettings.json` with appropriate access values.

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
