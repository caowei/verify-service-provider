package uk.gov.ida.verifyserviceprovider.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.client.JerseyClientConfiguration;

import uk.gov.ida.saml.metadata.EidasMetadataConfiguration;
import uk.gov.ida.saml.metadata.TrustStoreConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.security.KeyStore;

import static java.util.Optional.ofNullable;

public class EuropeanIdentityConfiguration extends EidasMetadataConfiguration {

    private final String hubConnectorEntityId;
    private final Boolean enabled;
    private HubEnvironment environment = HubEnvironment.COMPLIANCE_TOOL;

    @JsonCreator
    public EuropeanIdentityConfiguration(
            @JsonProperty("trustAnchorUri") URI trustAnchorUri,
            @JsonProperty("minRefreshDelay") Long minRefreshDelay,
            @JsonProperty("maxRefreshDelay") Long maxRefreshDelay,
            @JsonProperty("trustAnchorMaxRefreshDelay") Long trustAnchorMaxRefreshDelay,
            @JsonProperty("trustAnchorMinRefreshDelay") Long trustAnchorMinRefreshDelay,
            @JsonProperty("client") JerseyClientConfiguration client,
            @JsonProperty("jerseyClientName") String jerseyClientName,
            @JsonProperty("trustStore") TrustStoreConfiguration trustStore,
            @JsonProperty("metadataSourceUri") URI metadataSourceUri,
            @JsonProperty("hubConnectorEntityId") String hubConnectorEntityId,
            @NotNull @Valid @JsonProperty("enabled") Boolean enabled
    ) {
        super(trustAnchorUri, minRefreshDelay, maxRefreshDelay, trustAnchorMaxRefreshDelay, trustAnchorMinRefreshDelay, client, jerseyClientName, trustStore, metadataSourceUri);
        this.enabled = enabled;
        this.hubConnectorEntityId = hubConnectorEntityId;
    }

    @JsonIgnore
    public void setEnvironment(HubEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public URI getMetadataSourceUri() {
        return ofNullable(super.getMetadataSourceUri()).orElseGet(() -> environment.getMetadataUri());
    }

    @Override
    public KeyStore getTrustStore() {
        return ofNullable(super.getTrustStore()).orElseGet(() -> environment.getMetadataTrustStore());
    }

    @Override
    public URI getTrustAnchorUri() {
        return ofNullable(super.getTrustAnchorUri()).orElseGet(() -> environment.getTrustAnchorUri());
    }

    public Boolean isEnabled() {
        return enabled;
    }
}
