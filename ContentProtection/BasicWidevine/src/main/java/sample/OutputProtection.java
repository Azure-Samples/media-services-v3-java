// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OutputProtection {
    @JsonProperty("HDCP")
    private String hdcp;

    public String getHdcp() {
        return hdcp;
    }

    public void setHdcp(String hdcp) {
        this.hdcp = hdcp;
    }
}
