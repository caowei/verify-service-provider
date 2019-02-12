package uk.gov.ida.verifyserviceprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.cli.Command;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.Duration;
import keystore.KeyStoreResource;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.ida.saml.core.IdaSamlBootstrap;
import uk.gov.ida.verifyserviceprovider.Utils.MdsValueChecker;
import uk.gov.ida.verifyserviceprovider.compliance.ComplianceToolMode;
import uk.gov.ida.verifyserviceprovider.compliance.dto.MatchingAddress;
import uk.gov.ida.verifyserviceprovider.compliance.dto.MatchingAttribute;
import uk.gov.ida.verifyserviceprovider.compliance.dto.MatchingDataset;
import uk.gov.ida.verifyserviceprovider.compliance.dto.MatchingDatasetBuilder;
import uk.gov.ida.verifyserviceprovider.configuration.VerifyServiceProviderConfiguration;
import uk.gov.ida.verifyserviceprovider.dto.LevelOfAssurance;
import uk.gov.ida.verifyserviceprovider.dto.RequestGenerationBody;
import uk.gov.ida.verifyserviceprovider.dto.RequestResponseBody;
import uk.gov.ida.verifyserviceprovider.services.ComplianceToolService;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.net.URI;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.client.Entity.json;
import static keystore.builders.KeyStoreResourceBuilder.aKeyStoreResource;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ida.saml.core.test.TestCertificateStrings.METADATA_SIGNING_A_PUBLIC_CERT;
import static uk.gov.ida.saml.core.test.builders.CertificateBuilder.aCertificate;
import static uk.gov.ida.verifyserviceprovider.services.ComplianceToolService.VERIFIED_USER_ON_SERVICE_WITH_NON_MATCH_SETTING_ID;

public class ComplianceToolModeAcceptanceTest {

    private static String COMPLIANCE_TOOL_HOST = "https://compliance-tool-reference.ida.digital.cabinet-office.gov.uk";

    private static final KeyStoreResource KEY_STORE_RESOURCE = aKeyStoreResource()
            .withCertificate("VERIFY-FEDERATION", aCertificate().withCertificate(METADATA_SIGNING_A_PUBLIC_CERT).build().getCertificate())
            .build();
    private ComplianceToolService complianceTool;

    static {
        KEY_STORE_RESOURCE.create();
    }

    private static Command commandLineInitiator(Application<VerifyServiceProviderConfiguration> application, final MatchingDataset matchingDataset) {
        Bootstrap<VerifyServiceProviderConfiguration> bootstrap = new Bootstrap<>(application);
        final ObjectMapper objectMapper = bootstrap.getObjectMapper();
        return new ComplianceToolMode(objectMapper, bootstrap.getValidatorFactory().getValidator(), application) {

        @Override
        public void run(Bootstrap<?> wildcardBootstrap, Namespace defaultNamespace) throws Exception {
            Namespace namespace = applyCommandLineArguments(defaultNamespace,
                    "--url", "http://localhost:8080",
                    "-d", objectMapper.writeValueAsString(matchingDataset),
                    "--host", "127.0.0.1",
                    "-p", "0");
            super.run(wildcardBootstrap, namespace);
        }

        private Namespace applyCommandLineArguments(Namespace defaultNamespace, String... arguments) throws ArgumentParserException {
            final ArgumentParser p = ArgumentParsers.newArgumentParser("Usage:", false);
            final Subparser subparser = p.addSubparsers().addParser(getName(), false);
            configure(subparser);
            HashMap<String, Object> attributes = new HashMap<>();
            attributes.putAll(subparser.parseArgs(arguments).getAttrs());
            attributes.putAll(defaultNamespace.getAttrs());
            return new Namespace(attributes);
        }
    };
    }


    private static MatchingDataset matchingDataset = createMatchingDataset("Bob", "Smith");

    private static MatchingDataset createMatchingDataset(String firstname, String surname) {
        final String laterFromDateString = "2015-10-02T09:32:14.967";
        final String laterToDateString = "2018-03-03T10:20:50.163";
        final DateTime laterFromDate = DateTime.parse(laterFromDateString);
        final DateTime laterToDate = DateTime.parse(laterToDateString);
        return new MatchingDatasetBuilder().withFirstName(firstname, true, MatchingDatasetBuilder.standardFromDate, MatchingDatasetBuilder.standardToDate)
                .withSurnames(
                        new MatchingAttribute(surname, true, MatchingDatasetBuilder.standardFromDate, MatchingDatasetBuilder.standardToDate),
                        new MatchingAttribute("Smythington", true, laterFromDate, laterToDate)
                )
                .build();

    }

    @Rule
    public DropwizardAppRule<VerifyServiceProviderConfiguration> appRule = new DropwizardAppRule<>(
            VerifyServiceProviderApplication.class,
            null,
            Optional.of("dw"),
            (application)-> commandLineInitiator(application, matchingDataset)
    );

    private Client client;

    @BeforeClass
    public static void setUpBeforeClass() {
        IdaSamlBootstrap.bootstrap();
    }

    @Before
    public void setUpBefore() {
        JerseyClientConfiguration configuration = new JerseyClientConfiguration();
        configuration.setTimeout(Duration.seconds(10));
        configuration.setConnectionTimeout(Duration.seconds(10));
        configuration.setConnectionRequestTimeout(Duration.seconds(10));
        client = new JerseyClientBuilder(appRule.getEnvironment()).using(configuration).build(ComplianceToolModeAcceptanceTest.class.getName());
        complianceTool = new ComplianceToolService(client);
    }

    @Test
    public void shouldGenerateARequestToALocalHubService() throws Exception {
        Response authnRequest = client
                .target(appUri("generate-request"))
                .request()
                .post(json(new RequestGenerationBody(LevelOfAssurance.LEVEL_2, null)));

        RequestResponseBody authnSaml = authnRequest.readEntity(RequestResponseBody.class);

        assertThat(authnSaml.getSsoLocation()).isEqualTo(URI.create(COMPLIANCE_TOOL_HOST + "/SAML2/SSO"));
        String responseFor = complianceTool.createResponseFor(authnSaml.getSamlRequest(), VERIFIED_USER_ON_SERVICE_WITH_NON_MATCH_SETTING_ID);

        Map<String, String> translateResponseRequestData = ImmutableMap.of(
                "samlResponse", responseFor,
                "requestId", authnSaml.getRequestId(),
                "levelOfAssurance", LevelOfAssurance.LEVEL_1.name());

        Response response = client
                .target(appUri("translate-response"))
                .request()
                .post(json(translateResponseRequestData));

        assertThat(response.getStatus()).isEqualTo(200);

        JSONObject jsonResponse = new JSONObject(response.readEntity(String.class));

        JSONObject attributes = jsonResponse.getJSONObject("attributes");
        assertThat(attributes.keys()).containsExactlyInAnyOrder("firstName", "middleNames", "surnames", "dateOfBirth", "gender", "addresses");

        checkMatchingDatasetMatches(attributes, matchingDataset);

    }

    @Test
    public void shouldFailToRefreshIfMatchingDatasetPayloadIsInvalid() throws Exception {
        ObjectMapper objectMapper = appRule.getEnvironment().getObjectMapper();

        MatchingDataset matchingDataset = createMatchingDataset("Alice", "Surname");
        String matchingDatasetString = objectMapper.writeValueAsString(matchingDataset);
        JSONObject jsonObject = new JSONObject(matchingDatasetString);
        jsonObject.put("firstName", ImmutableMap.of());


        Response refreshDataset = client
                .target(appUri("refresh-matching-dataset"))
                .request()
                .post(json(jsonObject.toString()));

        assertThat(refreshDataset.getStatus()).isEqualTo(422);
    }

    @Test
    public void shouldLetYouRefreshTheMatchingDataset() throws Exception {
        MatchingDataset newMatchingDataset = createMatchingDataset("Alice", "Surname");

        Response refreshDataset = client
                .target(appUri("refresh-matching-dataset"))
                .request()
                .post(json(newMatchingDataset));

        assertThat(refreshDataset.getStatus()).isEqualTo(200);

        Response authnRequest = client
                .target(appUri("generate-request"))
                .request()
                .post(json(new RequestGenerationBody(LevelOfAssurance.LEVEL_2, null)));

        assertThat(authnRequest.getStatus()).isEqualTo(200);
        RequestResponseBody authnSaml = authnRequest.readEntity(RequestResponseBody.class);

        assertThat(authnSaml.getSsoLocation()).isEqualTo(URI.create(COMPLIANCE_TOOL_HOST + "/SAML2/SSO"));
        String responseFor = complianceTool.createResponseFor(authnSaml.getSamlRequest(), VERIFIED_USER_ON_SERVICE_WITH_NON_MATCH_SETTING_ID);

        Map<String, String> translateResponseRequestData = ImmutableMap.of(
                "samlResponse", responseFor,
                "requestId", authnSaml.getRequestId(),
                "levelOfAssurance", LevelOfAssurance.LEVEL_1.name());

        Response response = client
                .target(appUri("translate-response"))
                .request()
                .post(json(translateResponseRequestData));

        assertThat(response.getStatus()).isEqualTo(200);

        JSONObject jsonResponse = new JSONObject(response.readEntity(String.class));

        JSONObject attributes = jsonResponse.getJSONObject("attributes");
        assertThat(attributes.keys()).containsExactlyInAnyOrder("firstName", "middleNames", "surnames", "dateOfBirth", "gender", "addresses");

        checkMatchingDatasetMatches(attributes, newMatchingDataset);

    }

    private URI appUri(String s) {
        return URI.create(String.format("http://localhost:%d/" + s, appRule.getLocalPort()));
    }

    private void checkMatchingDatasetMatches(JSONObject attributes, MatchingDataset matchingDataset) {
        checkMatchingDatasetAttribute(attributes, "firstName", matchingDataset.getFirstName());
        checkMatchingDatasetListAttribute(attributes, "middleNames", 0, matchingDataset.getMiddleNames());
        checkMatchingDatasetListAttribute(attributes, "surnames", 0, matchingDataset.getSurnames());
        checkMatchingDatasetListAttribute(attributes, "surnames", 1, matchingDataset.getSurnames());
        checkMatchingDatasetAttribute(attributes, "dateOfBirth", matchingDataset.getDateOfBirth());
        checkMatchingDatasetAttribute(attributes, "gender", matchingDataset.getGender());
        checkMatchingDatasetAddress(attributes, 0, matchingDataset.getAddresses());
    }

    private void checkMatchingDatasetAddress(JSONObject attributes, int index, List<MatchingAddress> addresses) {
        MatchingAddress address = addresses.get(index);
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        String fromDate = formatter.print(address.getFrom()).replace(" ", "T");
        String toDate = formatter.print(address.getTo()).replace(" ", "T");
        MdsValueChecker.checkMdsValueOfAddress(index, address.getLines(), address.getPostCode().orElse(null), address.getInternationalPostCode().orElse(""), address.isVerified(), fromDate, toDate, attributes);
    }

    private void checkMatchingDatasetListAttribute(JSONObject attributes, String attributeName, int index, MatchingAttribute expectedAttribute) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        String fromDate = formatter.print(expectedAttribute.getFrom()).replace(" ", "T");
        String toDate = formatter.print(expectedAttribute.getTo()).replace(" ", "T");
        MdsValueChecker.checkMdsValueInArrayAttribute(attributeName, index, expectedAttribute.getValue(), expectedAttribute.isVerified(), fromDate, toDate, attributes);
    }

    private void checkMatchingDatasetListAttribute(JSONObject attributes, String attributeName, int index, List<MatchingAttribute> attributeList) {
        MatchingAttribute expectedAttribute = attributeList.get(index) ;
        checkMatchingDatasetListAttribute(attributes, attributeName, index, expectedAttribute);
    }

    private void checkMatchingDatasetAttribute(JSONObject attributes, String attributeName, MatchingAttribute expectedAttribute) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        String fromDate = formatter.print(expectedAttribute.getFrom()).replace(" ", "T");
        String toDate = formatter.print(expectedAttribute.getTo()).replace(" ", "T");
        MdsValueChecker.checkMdsValueOfAttribute(attributeName, expectedAttribute.getValue(), expectedAttribute.isVerified(), fromDate, toDate, attributes);
    }

}
