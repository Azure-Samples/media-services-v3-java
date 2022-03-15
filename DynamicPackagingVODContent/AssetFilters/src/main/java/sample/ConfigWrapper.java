// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * This class reads values from local configuration file
 * resources/conf/appsettings.json.
 * Please change the configuration using your account information. For more
 * information, see
 * https://docs.microsoft.com/azure/media-services/latest/access-api-cli-how-to.
 * For security
 * reasons, do not check in the configuration file to source control.
 */
public class ConfigWrapper {
    private static final String AAD_CLIENT_ID = "AZURE_CLIENT_ID";
    private static final String AAD_SECRET = "AZURE_CLIENT_SECRET";
    private static final String AAD_TENANT_ID = "AZURE_TENANT_ID";
    private static final String ACCOUNT_NAME = "AZURE_MEDIA_SERVICES_ACCOUNT_NAME";
    private static final String ARM_AAD_AUDIENCE = "AZURE_ARM_TOKEN_AUDIENCE";
    private static final String ARM_ENDPOINT = "AZURE_ARM_ENDPOINT";
    private static final String REGION = "Region";
    private static final String RESOURCE_GROUP = "AZURE_RESOURCE_GROUP";
    private static final String SUBSCRIPTION_ID = "AZURE_SUBSCRIPTION_ID";
    private static final String CONF_JSON = "conf/appsettings.json";
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
        return (String) jsonObject.get(AAD_CLIENT_ID);
    }

    public String getAadSecret() {
        return (String) jsonObject.get(AAD_SECRET);
    }

    public String getAadTenantId() {
        return (String) jsonObject.get(AAD_TENANT_ID);
    }

    public String getAccountName() {
        return (String) jsonObject.get(ACCOUNT_NAME);
    }

    public String getArmAadAudience() {
        return (String) jsonObject.get(ARM_AAD_AUDIENCE);
    }

    public String getArmEndpoint() {
        return (String) jsonObject.get(ARM_ENDPOINT);
    }

    public String getRegion() {
        return (String) jsonObject.get(REGION);
    }

    public String getResourceGroup() {
        return (String) jsonObject.get(RESOURCE_GROUP);
    }

    public String getSubscriptionId() {
        return (String) jsonObject.get(SUBSCRIPTION_ID);
    }
}
