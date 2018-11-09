package uk.gov.ida.verifyserviceprovider.services;

import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import uk.gov.ida.saml.core.transformers.AuthnContextFactory;
import uk.gov.ida.saml.core.transformers.MatchingDatasetUnmarshaller;
import uk.gov.ida.saml.core.validators.assertion.AssertionAttributeStatementValidator;
import uk.gov.ida.saml.security.SamlAssertionsSignatureValidator;
import uk.gov.ida.verifyserviceprovider.domain.AssertionData;
import uk.gov.ida.verifyserviceprovider.dto.LevelOfAssurance;
import uk.gov.ida.verifyserviceprovider.dto.NonMatchingAttributes;
import uk.gov.ida.verifyserviceprovider.dto.TranslatedNonMatchingResponseBody;
import uk.gov.ida.verifyserviceprovider.exceptions.SamlResponseValidationException;
import uk.gov.ida.verifyserviceprovider.mappers.MatchingDatasetToNonMatchingAttributesMapper;
import uk.gov.ida.verifyserviceprovider.services.AssertionClassifier.AssertionType;
import uk.gov.ida.verifyserviceprovider.factories.saml.UserIdHashFactory;
import uk.gov.ida.verifyserviceprovider.validators.SubjectValidator;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static uk.gov.ida.saml.core.validation.errors.GenericHubProfileValidationSpecification.MISMATCHED_ISSUERS;
import static uk.gov.ida.saml.core.validation.errors.GenericHubProfileValidationSpecification.MISMATCHED_PIDS;
import static uk.gov.ida.verifyserviceprovider.dto.NonMatchingScenario.IDENTITY_VERIFIED;

public class NonMatchingAssertionService implements AssertionService<TranslatedNonMatchingResponseBody> {

    private final SamlAssertionsSignatureValidator assertionsSignatureValidator;
    private final SubjectValidator subjectValidator;
    private final AssertionAttributeStatementValidator attributeStatementValidator;
    private final AuthnContextFactory authnContextFactory;
    private final MatchingDatasetUnmarshaller matchingDatasetUnmarshaller;
    private final AssertionClassifier assertionClassifierService;
    private final UserIdHashFactory userIdHashFactory;
    private final MatchingDatasetToNonMatchingAttributesMapper mdsMapper;

    public NonMatchingAssertionService(
            SamlAssertionsSignatureValidator assertionsSignatureValidator,
            SubjectValidator subjectValidator,
            AssertionAttributeStatementValidator attributeStatementValidator,
            AuthnContextFactory authnContextFactory,
            MatchingDatasetUnmarshaller matchingDatasetUnmarshaller,
            AssertionClassifier assertionClassifierService,
            UserIdHashFactory userIdHashFactory,
            MatchingDatasetToNonMatchingAttributesMapper mdsMapper
    ) {
        this.assertionsSignatureValidator = assertionsSignatureValidator;
        this.subjectValidator = subjectValidator;
        this.attributeStatementValidator = attributeStatementValidator;
        this.authnContextFactory = authnContextFactory;
        this.matchingDatasetUnmarshaller = matchingDatasetUnmarshaller;
        this.assertionClassifierService = assertionClassifierService;
        this.userIdHashFactory = userIdHashFactory;
        this.mdsMapper = mdsMapper;
    }


    @Override
    public TranslatedNonMatchingResponseBody translateSuccessResponse(List<Assertion> assertions, String expectedInResponseTo, LevelOfAssurance expectedLevelOfAssurance, String entityId) {
        Assertion authnAssertion = getAuthnAssertion(assertions);
        Assertion mdsAssertion = getMatchingDatasetAssertion(assertions);

        validate(authnAssertion, mdsAssertion, expectedInResponseTo, expectedLevelOfAssurance);

        String nameID = mdsAssertion.getSubject().getNameID().getValue();
        LevelOfAssurance levelOfAssurance = extractLevelOfAssuranceFrom(authnAssertion);
        NonMatchingAttributes attributes = translateAttributes(authnAssertion, mdsAssertion);

        return new TranslatedNonMatchingResponseBody(IDENTITY_VERIFIED, nameID, levelOfAssurance, attributes);
    }

    @Override
    public TranslatedNonMatchingResponseBody translateNonSuccessResponse(StatusCode statusCode) {
        return null;
    }


    public void validate(Assertion authnAssertion, Assertion mdsAssertion, String requestId, LevelOfAssurance expectedLevelOfAssurance) {

        validateIdpAssertion(authnAssertion, requestId, IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        validateIdpAssertion(mdsAssertion, requestId, IDPSSODescriptor.DEFAULT_ELEMENT_NAME);


        if (!mdsAssertion.getIssuer().getValue().equals(authnAssertion.getIssuer().getValue())) {
            throw new SamlResponseValidationException(MISMATCHED_ISSUERS);
        }

        if (!mdsAssertion.getSubject().getNameID().getValue().equals(authnAssertion.getSubject().getNameID().getValue())) {
            throw new SamlResponseValidationException(MISMATCHED_PIDS);
        }
    }

    public void validateIdpAssertion(Assertion assertion,
                                      String expectedInResponseTo,
                                      QName role) {

        if (assertion.getIssueInstant() == null) {
            throw new SamlResponseValidationException("Assertion IssueInstant is missing.");
        }

        if (assertion.getID() == null || assertion.getID().length() == 0) {
            throw new SamlResponseValidationException("Assertion Id is missing or blank.");
        }

        if (assertion.getIssuer() == null || assertion.getIssuer().getValue() == null || assertion.getIssuer().getValue().length() == 0) {
            throw new SamlResponseValidationException("Assertion with id " + assertion.getID() + " has missing or blank Issuer.");
        }

        if (assertion.getVersion() == null) {
            throw new SamlResponseValidationException("Assertion with id " + assertion.getID() + " has missing Version.");
        }

        if (!assertion.getVersion().equals(SAMLVersion.VERSION_20)) {
            throw new SamlResponseValidationException("Assertion with id " + assertion.getID() + " declared an illegal Version attribute value.");
        }

        assertionsSignatureValidator.validate(singletonList(assertion), role);
        subjectValidator.validate(assertion.getSubject(), expectedInResponseTo);
        attributeStatementValidator.validate(assertion);
    }


    public NonMatchingAttributes translateAttributes(Assertion authnAssertion, Assertion mdsAssertion) {
        String levelOfAssurance = extractLevelOfAssuranceStringFrom(authnAssertion);

        AssertionData assertionData = new AssertionData(
                authnContextFactory.authnContextForLevelOfAssurance(levelOfAssurance),
                matchingDatasetUnmarshaller.fromAssertion(mdsAssertion)
        );

        return mdsMapper.mapToNonMatchingAttributes(assertionData.getMatchingDataset());
    }


    private Assertion getAuthnAssertion(Collection<Assertion> assertions) {
        Map<AssertionType, List<Assertion>> assertionMap = assertions.stream()
                .collect(Collectors.groupingBy(assertionClassifierService::classifyAssertion));

        List<Assertion> authnAssertions = assertionMap.get(AssertionType.AUTHN_ASSERTION);
        if (authnAssertions == null || authnAssertions.size() != 1) {
            throw new SamlResponseValidationException("Exactly one authn statement is expected.");
        }

        return authnAssertions.get(0);
    }

    private Assertion getMatchingDatasetAssertion(Collection<Assertion> assertions) {
        Map<AssertionType, List<Assertion>> assertionMap = assertions.stream()
                .collect(Collectors.groupingBy(assertionClassifierService::classifyAssertion));

        List<Assertion> mdsAssertions = assertionMap.get(AssertionType.MDS_ASSERTION);
        if (mdsAssertions == null || mdsAssertions.size() != 1) {
            throw new SamlResponseValidationException("Exactly one matching dataset assertion is expected.");
        }

        return mdsAssertions.get(0);
    }

    public LevelOfAssurance extractLevelOfAssuranceFrom(Assertion authnAssertion) {
        String levelOfAssuranceString = extractLevelOfAssuranceStringFrom(authnAssertion);

        try {
            return LevelOfAssurance.fromSamlValue(levelOfAssuranceString);
        } catch (Exception ex) {
            throw new SamlResponseValidationException(String.format("Level of assurance '%s' is not supported.", levelOfAssuranceString));
        }
    }

    private String extractLevelOfAssuranceStringFrom(Assertion authnAssertion) {
        AuthnStatement authnStatement = authnAssertion.getAuthnStatements().get(0);
        return authnStatement.getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef();
    }
}
