// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package sample;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PolicyOverrides {
    @JsonProperty("CanPlay")
    private boolean canPlay;

    @JsonProperty("CanPersist")
    private boolean canPersist;

    @JsonProperty("CanRenew")
    private boolean canRenew;

    @JsonProperty("RentalDurationSeconds")
    private int rentalDurationSeconds;

    @JsonProperty("PlaybackDurationSeconds")
    private int playbackDurationSeconds;

    @JsonProperty("LicenseDurationSeconds")
    private int licenseDurationSeconds;

    public boolean isCanPlay() {
        return canPlay;
    }

    public void setCanPlay(boolean canPlay) {
        this.canPlay = canPlay;
    }

    public boolean isCanPersist() {
        return canPersist;
    }

    public void setCanPersist(boolean canPersist) {
        this.canPersist = canPersist;
    }

    public boolean isCanRenew() {
        return canRenew;
    }

    public void setCanRenew(boolean canRenew) {
        this.canRenew = canRenew;
    }

    public int getRentalDurationSeconds() {
        return rentalDurationSeconds;
    }

    public void setRentalDurationSeconds(int rentalDurationSeconds) {
        this.rentalDurationSeconds = rentalDurationSeconds;
    }

    public int getPlaybackDurationSeconds() {
        return playbackDurationSeconds;
    }

    public void setPlaybackDurationSeconds(int playbackDurationSeconds) {
        this.playbackDurationSeconds = playbackDurationSeconds;
    }

    public int getLicenseDurationSeconds() {
        return licenseDurationSeconds;
    }

    public void setLicenseDurationSeconds(int licenseDurationSeconds) {
        this.licenseDurationSeconds = licenseDurationSeconds;
    }
}
