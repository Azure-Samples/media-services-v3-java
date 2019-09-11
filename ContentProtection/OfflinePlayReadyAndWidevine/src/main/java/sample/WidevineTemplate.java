// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WidevineTemplate {
    @JsonProperty("AllowedTrackTypes")
    private String allowedTrackTypes;

    @JsonProperty("ContentKeySpecs")
    private List<ContentKeySpec> contentKeySpecs;

    @JsonProperty("PolicyOverrides")
    private PolicyOverrides policyOverrides;

    public String getAllowedTrackTypes() {
        return allowedTrackTypes;
    }

    public void setAllowedTrackTypes(String allowedTrackTypes) {
        this.allowedTrackTypes = allowedTrackTypes;
    }

    public List<ContentKeySpec> getContentKeySpecs() {
        return contentKeySpecs;
    }

    public void setContentKeySpecs(List<ContentKeySpec> contentKeySpecs) {
        this.contentKeySpecs = contentKeySpecs;
    }

    public PolicyOverrides getPolicyOverrides() {
        return policyOverrides;
    }

    public void setPolicyOverrides(PolicyOverrides policyOverrides) {
        this.policyOverrides = policyOverrides;
    }
}
