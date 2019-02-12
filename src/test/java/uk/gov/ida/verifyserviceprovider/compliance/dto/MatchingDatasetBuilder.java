package uk.gov.ida.verifyserviceprovider.compliance.dto;

import org.joda.time.DateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;

public class MatchingDatasetBuilder {
    private static final String standardFromDateString = "2013-02-22T14:32:14.064";
    private static final String standardToDateString = "2015-10-02T09:32:14.967";
    public static DateTime standardFromDate = DateTime.parse(standardFromDateString);
    public static DateTime standardToDate = DateTime.parse(standardToDateString);
    private MatchingAttribute firstName = new MatchingAttribute("Alice", true, standardFromDate, standardToDate);
    private MatchingAttribute middleNames = new MatchingAttribute("B", true, standardFromDate, standardToDate);
    private List<MatchingAttribute> surnames = asList(new MatchingAttribute("Montgomery", true, standardFromDate, standardToDate));
    private MatchingAttribute gender = new MatchingAttribute("NOT_SPECIFIED", true, standardFromDate, standardToDate);
    private MatchingAttribute dateOfBirth = new MatchingAttribute("1970-01-01", true, standardFromDate, standardToDate);
    private List<MatchingAddress> addresses = asList(
            new MatchingAddress(
            true,
            standardFromDate, standardToDate,
            "E1 8QS",
            asList("The White Chapel Building", "10 Whitechapel High Street"),
            null,
            null
    ));
    private String persistentId = UUID.randomUUID().toString();

    public MatchingDatasetBuilder withFirstName(MatchingAttribute firstName) {
        this.firstName = firstName;
        return this;
    }

    public MatchingDatasetBuilder withFirstName(String value, boolean verified, DateTime fromDate, DateTime toDate) {
        return withFirstName(new MatchingAttribute(value, verified, fromDate, toDate));
    }

    public MatchingDatasetBuilder withMiddleNames(MatchingAttribute middleNames) {
        this.middleNames = middleNames;
        return this;
    }

    public MatchingDatasetBuilder withMiddleNames(String value, boolean verified, DateTime fromDate, DateTime toDate) {
        withMiddleNames(new MatchingAttribute(value, verified, fromDate, toDate));
        return this;
    }

    public MatchingDatasetBuilder withSurname(MatchingAttribute surname) {
        this.surnames.add(surname);
        return this;
    }

    public MatchingDatasetBuilder withSurname(String value, boolean verified, DateTime fromDate, DateTime toDate) {
        return withSurname(new MatchingAttribute(value, verified, fromDate, toDate));
    }

    public MatchingDatasetBuilder withSurnames(List<MatchingAttribute> surnames) {
        this.surnames = surnames;
        return this;
    }

    public MatchingDatasetBuilder withSurnames(MatchingAttribute... surnames) {
        return withSurnames(asList(surnames));
    }

    public MatchingDatasetBuilder withGender(MatchingAttribute gender) {
        this.gender = gender;
        return this;
    }

    public MatchingDatasetBuilder withGender(String value, boolean verified, DateTime fromDate, DateTime toDate) {
        return withGender(new MatchingAttribute(value, verified, fromDate, toDate));
    }

    public MatchingDatasetBuilder withDateOfBirth(MatchingAttribute dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        return this;
    }

    public MatchingDatasetBuilder withDateOfBirth(String value, boolean verified, DateTime fromDate, DateTime toDate) {
        return withDateOfBirth(new MatchingAttribute(value, verified, fromDate, toDate));
    }

    public MatchingDatasetBuilder withAddresses(List<MatchingAddress> addresses) {
        this.addresses = addresses;
        return this;
    }

    public MatchingDatasetBuilder withAddress(MatchingAddress address) {
        this.addresses.add(address);
        return this;
    }

    public MatchingDatasetBuilder withPersistentId(String persistentId) {
        this.persistentId = persistentId;
        return this;
    }

    public MatchingDataset build() {
        return new MatchingDataset(firstName, middleNames, surnames, gender, dateOfBirth, addresses, persistentId);
    }
}