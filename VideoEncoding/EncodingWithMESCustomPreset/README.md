---
topic: sample
languages:
  - java
products:
  - azure-media-services
---

# Encode with a custom Transform

This sample shows how to create a custom encoding Transform using the StandardEncoderPreset settings. It shows how to perform the following tasks:

1. Creates a custom encoding transform.
1. Creates an input asset and upload a media file into it.
1. Submits a job and monitoring the job using polling method.
1. Downloads the output asset.
1. prints urls for streaming.

## Prerequisites

* Java JDK 1.8 or newer installed
* Maven installed
* An Azure Media Services account. See the steps described in [Create a Media Services account](https://docs.microsoft.com/azure/media-services/latest/create-account-cli-quickstart).

## Build and run

* Update appsettings.json with your account settings The settings for your account can be retrieved using the following Azure CLI command in the Media Services module. The following bash shell script creates a service principal for the account and returns the json settings.

    `#!/bin/bash`

    `resourceGroup=<your resource group>`\
    `amsAccountName=<your ams account name>`\
    `amsSPName=<your AAD application>`

    `#Create a service principal with password and configure its access to an Azure Media Services account.`
    `az ams account sp create` \\\
    `--account-name $amsAccountName` \\\
    `--name $amsSPName` \\\
    `--resource-group $resourceGroup` \\\
    `--role Owner` \\\
    `--years 2`

* Clean and build the project.

    Open a terminal window, go to the root folder of this project, run `mvn clean compile`.

* Run this project.

    Execute `mvn exec:java`, then follow the instructions in the output console.

## Next steps

* [Streaming videos](https://docs.microsoft.com/en-us/azure/media-services/latest/stream-files-tutorial-with-api)
* [Azure Media Services pricing](https://azure.microsoft.com/pricing/details/media-services/)
* [Azure Media Services v3 Documentation](https://docs.microsoft.com/azure/media-services/latest/)
