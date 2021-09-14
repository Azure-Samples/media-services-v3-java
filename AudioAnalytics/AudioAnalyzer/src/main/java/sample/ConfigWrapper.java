// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * This class reads values from local configuration file resources/conf/appsettings.json
 * Please change the configuration using your account information. For more information, see
 * https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to. For security
 * reasons, do not check in the configuration file to source control.
 */
public class ConfigWrapper {
    private static final String AAD_CLIENT_ID = "AadClientId";
    private static final String AAD_ENDPOINT = "AadEndpoint";
    private static final String AAD_SECRET = "AadSecret";
    private static final String AAD_TENANT_ID = "AadTenantId";
    private static final String ACCOUNT_NAME = "AccountName";
    private static final String ARM_AAD_AUDIENCE = "ArmAadAudience";
    private static final String ARM_ENDPOINT = "ArmEndpoint";
    private static final String REGION = "Region";
    private static final String RESOURCE_GROUP = "ResourceGroup";
    private static final String SUBSCRIPTION_ID = "SubscriptionId";
    private static final String CONF_JSON = "conf/appsettings.json";
    private static final String NAMESPACE_NAME = "NamespaceName";
    private static final String EVENT_HUB_CONNECTION_STRING = "EventHubConnectionString";
    private static final String EVENT_HUB_NAME = "EventHubName";
    private static final String STORAGE_CONTAINER_NAME = "StorageContainerName";
    private static final String STORAGE_ACCOUNT_NAME = "StorageAccountName";
    private static final String STORAGE_ACCOUNT_KEY = "StorageAccountKey";
    private static final String STORAGE_CONNECTION_STRING = "StorageConnectionString";

    private final JSONObject jsonObject;
    private final InputStreamReader isReader;

    public ConfigWrapper() {
        InputStream inStream = ConfigWrapper.class.getClassLoader().getResourceAsStream(CONF_JSON);
        isReader = new InputStreamReader(inStream);

        JSONParser parser = new JSONParser();
        Object obj = null;
        try {
            obj = parser.parse(isReader);
        } catch (Exception ioe) {
            System.err.println(ioe);
            System.exit(1);
        }

        try {
            inStream.close();
        } catch (IOException e) { }
        jsonObject = (JSONObject) obj;
    }

    public void close() {
        try {
            if (isReader != null) {
                isReader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getAadClientId() {
        return (String)jsonObject.get(AAD_CLIENT_ID);
    }

    public String getAadEndpoint() {
        return (String)jsonObject.get(AAD_ENDPOINT);
    }

    public String getAadSecret() {
        return (String)jsonObject.get(AAD_SECRET);
    }

    public String getAadTenantId() {
        return (String)jsonObject.get(AAD_TENANT_ID);
    }

    public String getAccountName() {
        return (String)jsonObject.get(ACCOUNT_NAME);
    }

    public String getArmAadAudience() {
        return (String)jsonObject.get(ARM_AAD_AUDIENCE);
    }

    public String getArmEndpoint() {
        return (String)jsonObject.get(ARM_ENDPOINT);
    }

    public String getRegion() {
        return (String)jsonObject.get(REGION);
    }

    public String getResourceGroup() {
        return (String)jsonObject.get(RESOURCE_GROUP);
    }

    public String getSubscriptionId() {
        return (String)jsonObject.get(SUBSCRIPTION_ID);
    }

    public String getNamespaceName() {
        return (String)jsonObject.get(NAMESPACE_NAME);
    }

    public String getEventHubConnectionString() {
        return (String)jsonObject.get(EVENT_HUB_CONNECTION_STRING);
    }

    public String getEventHubName() {
        return (String)jsonObject.get(EVENT_HUB_NAME);
    }

    public String getStorageContainerName() {
        return (String)jsonObject.get(STORAGE_CONTAINER_NAME);
    }

    public String getStorageAccountName() {
        return (String)jsonObject.get(STORAGE_ACCOUNT_NAME);
    }

    public String getStorageAccountKey() {
        return (String)jsonObject.get(STORAGE_ACCOUNT_KEY);
    }

    public String getStorageConnectionString() {return (String)jsonObject.get(STORAGE_CONNECTION_STRING);}
}
