package uk.gov.ida.verifyserviceprovider.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.ida.saml.metadata.EidasMetadataConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class EuropeanIdentityConfiguration {

    private String hubConnectorEntityId;
    private boolean enabled;
    private EidasMetadataConfiguration aggregatedMetadata;

    @JsonCreator
    public EuropeanIdentityConfiguration(
            @JsonProperty("hubConnectorEntityId") String hubConnectorEntityId,
            @NotNull @Valid @JsonProperty("enabled") boolean enabled,
            @Valid @JsonProperty("aggregatedMetadata") EidasMetadataConfiguration aggregatedMetadata
    ){
        this.enabled = enabled;
        this.hubConnectorEntityId = hubConnectorEntityId;
        this.aggregatedMetadata = aggregatedMetadata;
    }

    public String getHubConnectorEntityId() {
        return hubConnectorEntityId;
    }

    public EidasMetadataConfiguration getAggregatedMetadata() {
        return aggregatedMetadata;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnvironment(HubEnvironment environment) {
    }
}
