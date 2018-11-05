package unit.uk.gov.ida.verifyserviceprovider.services;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.xmlsec.signature.Signature;
import uk.gov.ida.saml.core.IdaSamlBootstrap;

import uk.gov.ida.saml.core.domain.AuthnContext;
import uk.gov.ida.saml.core.domain.MatchingDataset;
import uk.gov.ida.saml.core.extensions.IdaAuthnContext;
import uk.gov.ida.saml.core.test.TestCredentialFactory;
import uk.gov.ida.saml.core.test.builders.AssertionBuilder;
import uk.gov.ida.saml.core.transformers.AuthnContextFactory;
import uk.gov.ida.saml.core.transformers.VerifyMatchingDatasetUnmarshaller;

import uk.gov.ida.saml.core.validators.assertion.AssertionAttributeStatementValidator;
import uk.gov.ida.saml.security.SamlAssertionsSignatureValidator;
import uk.gov.ida.saml.security.validators.ValidatedAssertions;
import uk.gov.ida.shared.utils.datetime.DateTimeFreezer;
import uk.gov.ida.verifyserviceprovider.domain.AssertionData;
import uk.gov.ida.verifyserviceprovider.dto.LevelOfAssurance;
import uk.gov.ida.verifyserviceprovider.exceptions.SamlResponseValidationException;
import uk.gov.ida.verifyserviceprovider.services.AssertionClassifier;
import uk.gov.ida.verifyserviceprovider.services.NonMatchingAssertionService;
import uk.gov.ida.verifyserviceprovider.validators.SubjectValidator;

import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.ida.saml.core.test.TestCertificateStrings.STUB_IDP_PUBLIC_PRIMARY_CERT;
import static uk.gov.ida.saml.core.test.TestCertificateStrings.STUB_IDP_PUBLIC_PRIMARY_PRIVATE_KEY;
import static uk.gov.ida.saml.core.test.TestEntityIds.STUB_IDP_ONE;
import static uk.gov.ida.saml.core.test.builders.AssertionBuilder.anAssertion;
import static uk.gov.ida.saml.core.test.builders.AttributeStatementBuilder.anAttributeStatement;
import static uk.gov.ida.saml.core.test.builders.AuthnContextBuilder.anAuthnContext;
import static uk.gov.ida.saml.core.test.builders.AuthnContextClassRefBuilder.anAuthnContextClassRef;
import static uk.gov.ida.saml.core.test.builders.AuthnStatementBuilder.anAuthnStatement;
import static uk.gov.ida.saml.core.test.builders.ConditionsBuilder.aConditions;
import static uk.gov.ida.saml.core.test.builders.IPAddressAttributeBuilder.anIPAddress;
import static uk.gov.ida.saml.core.test.builders.IssuerBuilder.anIssuer;
import static uk.gov.ida.saml.core.test.builders.SignatureBuilder.aSignature;
import static uk.gov.ida.saml.core.test.builders.SubjectBuilder.aSubject;
import static uk.gov.ida.saml.core.test.builders.SubjectConfirmationBuilder.aSubjectConfirmation;
import static uk.gov.ida.saml.core.test.builders.SubjectConfirmationDataBuilder.aSubjectConfirmationData;

public class NonMatchingAssertionServiceTest {


    private NonMatchingAssertionService nonMatchingAssertionService;

    @Mock
    private SubjectValidator subjectValidator;

    @Mock
    private SamlAssertionsSignatureValidator hubSignatureValidator;

    @Mock
    private AssertionAttributeStatementValidator attributeStatementValidator;

    @Mock
    private AuthnContextFactory authnContextFactory;

    @Mock
    private VerifyMatchingDatasetUnmarshaller verifyMatchingDatasetUnmarshaller;


    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        IdaSamlBootstrap.bootstrap();
        initMocks(this);

        nonMatchingAssertionService = new NonMatchingAssertionService(
                hubSignatureValidator,
                subjectValidator,
                attributeStatementValidator,
                authnContextFactory,
                verifyMatchingDatasetUnmarshaller,
                new AssertionClassifier()
        );
        doNothing().when(subjectValidator).validate(any(), any());
        when(hubSignatureValidator.validate(any(), any())).thenReturn(mock(ValidatedAssertions.class));


        DateTimeFreezer.freezeTime();
    }

    @After
    public void tearDown() {
        DateTimeFreezer.unfreezeTime();
    }

    @Test
    public void shouldThrowExceptionIfIssueInstantMissingWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setIssueInstant(null);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion IssueInstant is missing.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfAssertionIdIsMissingWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setID(null);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion Id is missing or blank.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfAssertionIdIsBlankWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setID("");

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion Id is missing or blank.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfIssuerMissingWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setIssuer(null);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion with id mds-assertion has missing or blank Issuer.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfIssuerValueMissingWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setIssuer(anIssuer().withIssuerId(null).build());

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion with id mds-assertion has missing or blank Issuer.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfIssuerValueIsBlankWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setIssuer(anIssuer().withIssuerId("").build());

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion with id mds-assertion has missing or blank Issuer.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldThrowExceptionIfMissingAssertionVersionWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setVersion(null);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion with id mds-assertion has missing Version.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }


    @Test
    public void shouldThrowExceptionIfAssertionVersionInvalidWhenValidatingIdpAssertion() {
        Assertion assertion = aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted();
        assertion.setVersion(SAMLVersion.VERSION_10);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("Assertion with id mds-assertion declared an illegal Version attribute value.");
        nonMatchingAssertionService.validateIdpAssertion(assertion, "not-used", IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Test
    public void shouldNotThrowExceptionsWhenAssertionsAreValid() {
        List<Assertion> assertions = asList(
                aMatchingDatasetAssertionWithSignature(emptyList(), anIdpSignature(), "requestId").buildUnencrypted(),
                anAuthnStatementAssertion(IdaAuthnContext.LEVEL_2_AUTHN_CTX, "requestId").buildUnencrypted());

        nonMatchingAssertionService.validate(assertions,"requestId", LevelOfAssurance.LEVEL_1);

        verify(subjectValidator, times(2)).validate(any(), any());
        verify(hubSignatureValidator, times(2)).validate(any(), any());
    }

    @Test
    public void shouldThrowExceptionWhenNoAuthnAssertionProvided() {
        Assertion mdsAssertion1 = aMatchingDatasetAssertion("requestId").buildUnencrypted();
        Assertion mdsAssertion2 = aMatchingDatasetAssertion("requestId").buildUnencrypted();
        List<Assertion> assertions = Arrays.asList(mdsAssertion1, mdsAssertion2);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("No authn assertion found.");
        nonMatchingAssertionService.translate(assertions);
    }

    @Test
    public void shouldThrowExceptionWhenNoMatchingDatasetAssertionProvided() {
        Assertion authnAssertion1 = anAuthnStatementAssertion(IdaAuthnContext.LEVEL_2_AUTHN_CTX, "requestId").buildUnencrypted();
        Assertion authnAssertion2 = anAuthnStatementAssertion(IdaAuthnContext.LEVEL_2_AUTHN_CTX, "requestId").buildUnencrypted();
        List<Assertion> assertions = Arrays.asList(authnAssertion1, authnAssertion2);

        exception.expect(SamlResponseValidationException.class);
        exception.expectMessage("No matchingDataset assertion found");
        nonMatchingAssertionService.translate(assertions);
    }

    @Test
    public void shouldCorrectlyExtractLevelOfAssurance() {
        Assertion authnAssertion = anAuthnStatementAssertion(IdaAuthnContext.LEVEL_2_AUTHN_CTX, "requestId").buildUnencrypted();
        Assertion mdsAssertion = aMatchingDatasetAssertion("requestId").buildUnencrypted();
        List<Assertion> assertions = Arrays.asList(authnAssertion, mdsAssertion);

        when(authnContextFactory.authnContextForLevelOfAssurance(IdaAuthnContext.LEVEL_2_AUTHN_CTX)).thenReturn(AuthnContext.LEVEL_2);
        AssertionData assertionData = nonMatchingAssertionService.translate(assertions);

        assertThat(assertionData.getLevelOfAssurance()).isEqualTo(AuthnContext.LEVEL_2);
    }

    @Test
    public void shouldUseTheMatchingDatasetUnmarshallerToExtractMDS() {
        Assertion authnAssertion = anAuthnStatementAssertion(IdaAuthnContext.LEVEL_2_AUTHN_CTX, "requestId").buildUnencrypted();
        Assertion mdsAssertion = aMatchingDatasetAssertion("requestId").buildUnencrypted();
        List<Assertion> assertions = Arrays.asList(authnAssertion, mdsAssertion);

        MatchingDataset matchingDataset = mock(MatchingDataset.class);
        when(verifyMatchingDatasetUnmarshaller.fromAssertion(any())).thenReturn(matchingDataset);
        AssertionData assertionData = nonMatchingAssertionService.translate(assertions);

        assertThat(assertionData.getMatchingDataset()).isEqualTo(matchingDataset);
    }


    public static AssertionBuilder aMatchingDatasetAssertionWithSignature(List<Attribute> attributes, Signature signature, String requestId) {
        return anAssertion()
                .withId("mds-assertion")
                .withIssuer(anIssuer().withIssuerId(STUB_IDP_ONE).build())
                .withSubject(anAssertionSubject(requestId))
                .withSignature(signature)
                .addAttributeStatement(anAttributeStatement().addAllAttributes(attributes).build())
                .withConditions(aConditions().build());
    }

    public static AssertionBuilder anAuthnStatementAssertion(String authnContext, String inResponseTo) {
        return anAssertion()
                .addAuthnStatement(
                        anAuthnStatement()
                                .withAuthnContext(
                                        anAuthnContext()
                                                .withAuthnContextClassRef(
                                                        anAuthnContextClassRef()
                                                                .withAuthnContextClasRefValue(authnContext)
                                                                .build())
                                                .build())
                                .build())
                .withSubject(
                        aSubject()
                                .withSubjectConfirmation(
                                        aSubjectConfirmation()
                                                .withSubjectConfirmationData(
                                                        aSubjectConfirmationData()
                                                                .withInResponseTo(inResponseTo)
                                                                .build()
                                                ).build()
                                ).build())
                .withIssuer(anIssuer().withIssuerId(STUB_IDP_ONE).build())
                .addAttributeStatement(anAttributeStatement().addAttribute(anIPAddress().build()).build());
    }

    public static Subject anAssertionSubject(final String inResponseTo) {
        return aSubject()
                .withSubjectConfirmation(
                        aSubjectConfirmation()
                                .withSubjectConfirmationData(
                                        aSubjectConfirmationData()
                                                .withNotOnOrAfter(DateTime.now())
                                                .withInResponseTo(inResponseTo)
                                                .build()
                                ).build()
                ).build();
    }

    public static Signature anIdpSignature() {
        return aSignature().withSigningCredential(
                new TestCredentialFactory(STUB_IDP_PUBLIC_PRIMARY_CERT, STUB_IDP_PUBLIC_PRIMARY_PRIVATE_KEY)
                        .getSigningCredential()).build();

    }

    public static AssertionBuilder aMatchingDatasetAssertion(String requestId) {
        return anAssertion()
                .withId("mds-assertion")
                .withIssuer(anIssuer().withIssuerId(STUB_IDP_ONE).build())
                .addAttributeStatement(anAttributeStatement().build());
    }

}