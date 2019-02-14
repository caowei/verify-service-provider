package uk.gov.ida.verifyserviceprovider.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.dropwizard.servlets.assets.ResourceNotFoundException;
import uk.gov.ida.saml.metadata.KeyStoreLoader;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.Arrays;

import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.DEFAULT_TRUST_STORE_PASSWORD;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.PRODUCTION_HUB_TRUSTSTORE;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.PRODUCTION_IDP_TRUSTSTORE;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.PRODUCTION_METADATA_TRUSTSTORE;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.TEST_HUB_TRUSTSTORE;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.TEST_IDP_TRUSTSTORE;
import static uk.gov.ida.verifyserviceprovider.configuration.ConfigurationConstants.TEST_METADATA_TRUSTSTORE;

public enum HubEnvironment {
    PRODUCTION(
            URI.create("https://www.signin.service.gov.uk/SAML2/SSO"),
            URI.create("https://www.signin.service.gov.uk/SAML2/metadata/federation"),
            PRODUCTION_METADATA_TRUSTSTORE, PRODUCTION_HUB_TRUSTSTORE, PRODUCTION_IDP_TRUSTSTORE),
    INTEGRATION(
            URI.create("https://www.integration.signin.service.gov.uk/SAML2/SSO"),
            URI.create("https://www.integration.signin.service.gov.uk/SAML2/metadata/federation"),
            TEST_METADATA_TRUSTSTORE, TEST_HUB_TRUSTSTORE, TEST_IDP_TRUSTSTORE),
    COMPLIANCE_TOOL(
            URI.create("https://compliance-tool-reference.ida.digital.cabinet-office.gov.uk/SAML2/SSO"),
            URI.create("https://compliance-tool-reference.ida.digital.cabinet-office.gov.uk/SAML2/metadata/federation"),
            TEST_METADATA_TRUSTSTORE, TEST_HUB_TRUSTSTORE, TEST_IDP_TRUSTSTORE);

    private URI ssoLocation;
    private URI metadataUri;
    private String metadataTrustStore;
    private String hubTrustStore;
    private String idpTrustStore;

    @JsonCreator
    public static HubEnvironment fromString(String name) {
        return Arrays.stream(values())
            .filter(x -> name.equals(x.name()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                "Unrecognised Hub Environment: " + name + ". \n" +
                "Valid values are: PRODUCTION, INTEGRATION, COMPLIANCE_TOOL"
            ));
    }

    HubEnvironment(URI ssoLocation, URI metadataUri, String metadataTrustStore, String hubTrustStore, String idpTrustStore) {
        this.ssoLocation = ssoLocation;
        this.metadataUri = metadataUri;
        this.metadataTrustStore = metadataTrustStore;
        this.hubTrustStore = hubTrustStore;
        this.idpTrustStore = idpTrustStore;
    }

    public URI getSsoLocation() {
        return this.ssoLocation;
    }

    public URI getMetadataUri() {
        return this.metadataUri;
    }

    public URI getTrustAnchorUri(){
        return null;
    }

    public KeyStore getMetadataTrustStore() {
        return loadTrustStore(metadataTrustStore);
    }

    public KeyStore getHubTrustStore() {
        return loadTrustStore(hubTrustStore);
    }

    public KeyStore getIdpTrustStore() {
        return loadTrustStore(idpTrustStore);
    }

    private KeyStore loadTrustStore(String trustStoreName) {
        InputStream trustStoreStream = getClass().getClassLoader().getResourceAsStream(trustStoreName);
        if (trustStoreStream == null) {
            throw new ResourceNotFoundException(new FileNotFoundException("Could not load resource from path " + trustStoreName));
        }
        return new KeyStoreLoader().load(trustStoreStream, DEFAULT_TRUST_STORE_PASSWORD);
    }
}
