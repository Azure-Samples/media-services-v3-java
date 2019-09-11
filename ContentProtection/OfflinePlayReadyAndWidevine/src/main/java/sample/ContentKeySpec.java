// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ContentKeySpec {
    @JsonProperty("TrackType")
    private String trackType;

    @JsonProperty("SecurityLevel")
    private int securityLevel;

    @JsonProperty("RequiredOutputProtection")
    private OutputProtection requiredOutputProtection;

    public String getTrackType() {
        return trackType;
    }

    public void setTrackType(String trackType) {
        this.trackType = trackType;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(int securityLevel) {
        this.securityLevel = securityLevel;
    }

    public OutputProtection getRequiredOutputProtection() {
        return requiredOutputProtection;
    }

    public void setRequiredOutputProtection(OutputProtection requiredOutputProtection) {
        this.requiredOutputProtection = requiredOutputProtection;
    }
}
