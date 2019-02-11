package uk.gov.ida.verifyserviceprovider.configuration;

import certificates.values.CACertificates;
import keystore.KeyStoreResource;
import keystore.builders.KeyStoreResourceBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.assertj.core.api.Fail.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.ida.verifyserviceprovider.utils.DefaultObjectMapper.OBJECT_MAPPER;

@Ignore
public class EuropeanIdentityConfigurationTest {

    private static final KeyStoreResource keyStore = KeyStoreResourceBuilder.aKeyStoreResource()
            .withCertificate("rootCA", CACertificates.TEST_ROOT_CA).build();

    @Before
    public void setUp() {
        keyStore.create();
    }

    @Test
    public void shouldUseTestTrustStoreWithIntegrationTrustAnchorGivenEidasIsEnabledWithHubEnvironmentSetToIntegration() throws Exception {

        String config = "{" +
                "\"enabled\":\"true\"," +
                "\"trustStore\": {" +
                "\"path\": \"" + keyStore.getAbsolutePath() + "\"," +
                "\"password\": \"" + keyStore.getPassword() + "\"" +
                "}}";

        EuropeanIdentityConfiguration europeanIdentityConfiguration = OBJECT_MAPPER.readValue(config, EuropeanIdentityConfiguration.class);
        europeanIdentityConfiguration.isEnabled();
        europeanIdentityConfiguration.setEnvironment(HubEnvironment.INTEGRATION);

        assertThat(europeanIdentityConfiguration.getTrustStore().containsAlias("rootCA"), is(true));
        assertThat(europeanIdentityConfiguration.getTrustAnchorUri().toString(),is("https://www.integration.signin.service.gov.uk/SAML2/metadata/federation"));
    }

    @Test
    public void shouldUseTestTrustWithProductionTrustAnchorGivenEidasIsEnabledWithHubEnvironmentSetToProduction() throws Exception {

        String config = "{" +
                "\"enabled\":\"true\"," +
                "\"trustStore\": {" +
                "\"path\": \"" + keyStore.getAbsolutePath() + "\"," +
                "\"password\": \"" + keyStore.getPassword() + "\"" +
                "}}";

        EuropeanIdentityConfiguration europeanIdentityConfiguration = OBJECT_MAPPER.readValue(config, EuropeanIdentityConfiguration.class);
        europeanIdentityConfiguration.isEnabled();
        europeanIdentityConfiguration.setEnvironment(HubEnvironment.PRODUCTION);

        assertThat(europeanIdentityConfiguration.getTrustStore().containsAlias("rootCA"), is(true));
        assertThat(europeanIdentityConfiguration.getTrustAnchorUri().toString(),is("https://www.signin.service.gov.uk/SAML2/metadata/federation"));

    }

    @Test
    public void shouldUseTestTrustStoreWithComplianceTrustAnchorGivenEidasIsEnabledWithHubEnvironmentSetToCompliance() throws Exception {

        String config = "{" +
                "\"enabled\":\"false\"," +
                "\"trustStore\": {" +
                "\"path\": \"" + keyStore.getAbsolutePath() + "\"," +
                "\"password\": \"" + keyStore.getPassword() + "\"" +
                "}}";

        EuropeanIdentityConfiguration europeanIdentityConfiguration = OBJECT_MAPPER.readValue(config, EuropeanIdentityConfiguration.class);
        europeanIdentityConfiguration.isEnabled();
        europeanIdentityConfiguration.setEnvironment(HubEnvironment.COMPLIANCE_TOOL);

        assertThat(europeanIdentityConfiguration.getTrustStore().containsAlias("rootCA"), is(true));
        assertThat(europeanIdentityConfiguration.getTrustAnchorUri().toString(),is("https://compliance-tool-reference.ida.digital.cabinet-office.gov.uk/SAML2/metadata/federation"));

    }

    @Test
    public void shouldReadTrustAnchorGivenEidasIsEnabledWithASpecifiedTrustAnchorUri() {
        String config = "{" +
                "\"enabled\":\"false\"," +
                "\"trustAnchorUri\":\"http://example.com\"," +
                "\"trustStore\": {" +
                "\"path\": \"" + keyStore.getAbsolutePath() + "\"," +
                "\"password\": \"" + keyStore.getPassword() + "\"" +
                "}}";
      /*  Client client = appWithEidasEnabled.client();
        ComplianceToolService complianceTool = new ComplianceToolService(client);
        GenerateRequestService generateRequestService = new GenerateRequestService(client);

        complianceTool.initialiseWithDefaultsForV2();

        RequestResponseBody requestResponseBody = generateRequestService.generateAuthnRequest(appWithEidasEnabled.getLocalPort());
        Map<String, String> translateResponseRequestData = ImmutableMap.of(
                "samlResponse", complianceTool.createResponseFor(requestResponseBody.getSamlRequest(), VERIFIED_USER_ON_SERVICE_WITH_NON_MATCH_SETTING_ID),
                "requestId", requestResponseBody.getRequestId(),
                "levelOfAssurance", LEVEL_1.name()
        );

        Response response = client
                .target(String.format("http://localhost:%d/translate-response", appWithEidasEnabled.getLocalPort()))
                .request()
                .buildPost(json(translateResponseRequestData))
                .invoke();

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());

        JSONObject jsonResponse = new JSONObject(response.readEntity(String.class));
        assertThat(jsonResponse.getString("scenario")).isEqualTo(IDENTITY_VERIFIED.name());
        assertThat(jsonResponse.getString("levelOfAssurance")).isEqualTo(LEVEL_1.name());*/
        fail("failed");
    }
}