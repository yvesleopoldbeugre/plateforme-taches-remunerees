package com.platform.campaigns.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps to {@code campaign_eligibility_criteria} (DAT §4.2) — shares the campaign's PK. BR-012. */
@Entity
@Table(name = "campaign_eligibility_criteria")
public class CampaignEligibilityCriteria {

    @Id
    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "min_age")
    private Short minAge;

    @Column(name = "max_age")
    private Short maxAge;

    @Column(length = 16)
    private String gender;

    @Column(name = "gender_justification")
    private String genderJustification;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "countries")
    private List<String> countries;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages")
    private List<String> languages;

    @Column(name = "min_reputation", precision = 5, scale = 2)
    private BigDecimal minReputation = BigDecimal.ZERO;

    protected CampaignEligibilityCriteria() {
        // JPA
    }

    public CampaignEligibilityCriteria(UUID campaignId, Short minAge, Short maxAge, String gender,
                                        String genderJustification, List<String> countries,
                                        List<String> languages, BigDecimal minReputation) {
        this.campaignId = campaignId;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.gender = gender;
        this.genderJustification = genderJustification;
        this.countries = countries;
        this.languages = languages;
        this.minReputation = minReputation == null ? BigDecimal.ZERO : minReputation;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public Short getMinAge() {
        return minAge;
    }

    public Short getMaxAge() {
        return maxAge;
    }

    public String getGender() {
        return gender;
    }

    public String getGenderJustification() {
        return genderJustification;
    }

    public List<String> getCountries() {
        return countries;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public BigDecimal getMinReputation() {
        return minReputation;
    }
}
