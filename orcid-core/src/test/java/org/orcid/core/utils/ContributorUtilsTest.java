package org.orcid.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.ehcache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.orcid.core.aop.ProfileLastModifiedAspect;
import org.orcid.core.manager.ActivityManager;
import org.orcid.core.manager.ProfileEntityCacheManager;
import org.orcid.core.manager.ProfileEntityManager;
import org.orcid.jaxb.model.common_v2.Contributor;
import org.orcid.jaxb.model.common_v2.ContributorEmail;
import org.orcid.jaxb.model.common_v2.ContributorOrcid;
import org.orcid.jaxb.model.common_v2.CreditName;
import org.orcid.jaxb.model.common_v2.Title;
import org.orcid.jaxb.model.record_v2.Funding;
import org.orcid.jaxb.model.record_v2.FundingContributor;
import org.orcid.jaxb.model.record_v2.FundingContributors;
import org.orcid.jaxb.model.record_v2.FundingTitle;
import org.orcid.jaxb.model.record_v2.Work;
import org.orcid.jaxb.model.record_v2.WorkBulk;
import org.orcid.jaxb.model.record_v2.WorkContributors;
import org.orcid.jaxb.model.record_v2.WorkTitle;
import org.orcid.persistence.dao.RecordNameDao;
import org.orcid.persistence.jpa.entities.ProfileEntity;
import org.orcid.persistence.jpa.entities.RecordNameEntity;

public class ContributorUtilsTest {
    
    @Mock
    private ProfileEntityCacheManager profileEntityCacheManager;
    
    @Mock
    private ActivityManager cacheManager;
    
    @Mock
    private ProfileEntityManager profileEntityManager;
    
    @Mock
    private RecordNameDao recordNameDao;
    
    @Mock
    private ProfileLastModifiedAspect profileLastModifiedAspect;
    
    @Mock
    private Cache<String, String> contributorsNameCache;

    @InjectMocks
    private ContributorUtils contributorUtils;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        contributorUtils.setCacheManager(cacheManager);
        contributorUtils.setProfileEntityManager(profileEntityManager);
        contributorUtils.setProfileLastModifiedAspect(profileLastModifiedAspect);
        when(profileLastModifiedAspect.retrieveLastModifiedDate(anyString())).thenReturn(new Date());
        when(contributorsNameCache.containsKey(anyString())).thenReturn(false);
    }
    
    @Test
    public void testFilterContributorPrivateDataForFundingWithPrivateName() {
        when(profileEntityManager.orcidExists(anyString())).thenReturn(true);
        when(profileEntityCacheManager.retrieve(anyString())).thenReturn(new ProfileEntity());
        when(cacheManager.getPublicCreditName(any(String.class))).thenReturn(null);
        
        Funding funding = getFundingWithOrcidContributor();
        contributorUtils.filterContributorPrivateData(funding);
        
        FundingContributor contributor = funding.getContributors().getContributor().get(0);
        assertNull(contributor.getContributorEmail());
        assertEquals("", contributor.getCreditName().getContent());
    }
    
    @Test
    public void testFilterContributorPrivateDataForFundingWithPublicName() {
        when(profileEntityManager.orcidExists(anyString())).thenReturn(true);
        when(profileEntityCacheManager.retrieve(anyString())).thenReturn(new ProfileEntity());
        when(cacheManager.getPublicCreditName(any(String.class))).thenReturn("a public name");
        
        Funding funding = getFundingWithOrcidContributor();
        contributorUtils.filterContributorPrivateData(funding);
        
        FundingContributor contributor = funding.getContributors().getContributor().get(0);
        assertNull(contributor.getContributorEmail());
        assertEquals("a public name", contributor.getCreditName().getContent());
    }
    
    @Test
    public void testFilterContributorPrivateDataForFundingWithInvalidOrcidRecord() {
        when(profileEntityManager.orcidExists(anyString())).thenReturn(false);
        
        Funding funding = getFundingWithOrcidContributor();
        contributorUtils.filterContributorPrivateData(funding);
        
        FundingContributor contributor = funding.getContributors().getContributor().get(0);
        assertNull(contributor.getContributorEmail());
        assertEquals("original credit name", contributor.getCreditName().getContent());
    }
    
    @Test
    public void testFilterContributorPrivateDataForFundingWithNoOrcidRecord() {
        Funding funding = getFundingWithContributorWithoutOrcid();
        contributorUtils.filterContributorPrivateData(funding);
        
        FundingContributor contributor = funding.getContributors().getContributor().get(0);
        assertNull(contributor.getContributorEmail());
        assertEquals("original credit name", contributor.getCreditName().getContent());
    }
    
    @Test
    public void testFilterContributorPrivateDataForFundingWithoutContributors() {
        Funding funding = getFundingWithoutContributors();
        contributorUtils.filterContributorPrivateData(funding);
        assertNotNull(funding); // test no failures
    }

    private Work getWorkWithoutContributors() {
        Work work = new Work();
        WorkTitle workTitle = new WorkTitle();
        workTitle.setTitle(new Title("work without contributors"));
        work.setWorkTitle(workTitle);
        return work;
    }

    private Work getWorkWithContributorWithoutOrcid() {
        Work work = new Work();
        WorkTitle workTitle = new WorkTitle();
        workTitle.setTitle(new Title("work with contributor without ORCID record"));
        work.setWorkTitle(workTitle);
        work.setWorkContributors(getWorkContributorWithoutOrcid());
        return work;
    }

    private Work getWorkWithOrcidContributor() {
        Work work = new Work();
        WorkTitle workTitle = new WorkTitle();
        workTitle.setTitle(new Title("work with contributor who has ORCID record"));
        work.setWorkTitle(workTitle);
        work.setWorkContributors(getWorkContributorWithOrcid());
        return work;
    }

    private WorkContributors getWorkContributorWithOrcid() {
        ContributorOrcid contributorOrcid = new ContributorOrcid();
        contributorOrcid.setPath("0000-0003-4902-6327");
        contributorOrcid.setHost("orcid.org");
        contributorOrcid.setUri("http://orcid.org/0000-0003-4902-6327");

        Contributor contributor = new Contributor();
        contributor.setContributorOrcid(contributorOrcid);
        contributor.setContributorEmail(new ContributorEmail("never@show.this"));
        contributor.setCreditName(new CreditName("original credit name"));
        
        return new WorkContributors(Arrays.asList(contributor));
    }
    
    private WorkContributors getWorkContributorWithoutOrcid() {
        Contributor contributor = new Contributor();
        contributor.setContributorEmail(new ContributorEmail("never@show.this"));
        contributor.setCreditName(new CreditName("original credit name"));
        
        return new WorkContributors(Arrays.asList(contributor));
    }
    
    private Funding getFundingWithoutContributors() {
        Funding funding = new Funding();
        FundingTitle fundingTitle = new FundingTitle();
        fundingTitle.setTitle(new Title("funding without contributors"));
        funding.setTitle(fundingTitle);
        return funding;
    }

    private Funding getFundingWithContributorWithoutOrcid() {
        Funding funding = new Funding();
        FundingTitle fundingTitle = new FundingTitle();
        fundingTitle.setTitle(new Title("funding with contributor without ORCID record"));
        funding.setTitle(fundingTitle);
        funding.setContributors(getFundingContributorWithoutOrcid());
        return funding;
    }

    private Funding getFundingWithOrcidContributor() {
        Funding funding = new Funding();
        FundingTitle fundingTitle = new FundingTitle();
        fundingTitle.setTitle(new Title("work with contributor who has ORCID record"));
        funding.setTitle(fundingTitle);
        funding.setContributors(getFundingContributorWithOrcid());
        return funding;
    }

    private FundingContributors getFundingContributorWithOrcid() {
        ContributorOrcid contributorOrcid = new ContributorOrcid();
        contributorOrcid.setPath("0000-0003-4902-6327");
        contributorOrcid.setHost("orcid.org");
        contributorOrcid.setUri("http://orcid.org/0000-0003-4902-6327");

        FundingContributor contributor = new FundingContributor();
        contributor.setContributorOrcid(contributorOrcid);
        contributor.setContributorEmail(new ContributorEmail("never@show.this"));
        contributor.setCreditName(new CreditName("original credit name"));
        
        FundingContributors fundingContributors = new FundingContributors();
        fundingContributors.getContributor().add(contributor);
        return fundingContributors;
    }
    
    private FundingContributors getFundingContributorWithoutOrcid() {
        FundingContributor contributor = new FundingContributor();
        contributor.setContributorEmail(new ContributorEmail("never@show.this"));
        contributor.setCreditName(new CreditName("original credit name"));
        
        FundingContributors fundingContributors = new FundingContributors();
        fundingContributors.getContributor().add(contributor);
        return fundingContributors;
    }
    
    private List<RecordNameEntity> getRecordNameEntities(List<String> orcidIds){
        List<RecordNameEntity> records = new ArrayList<RecordNameEntity>();
        for(String orcid : orcidIds) {
            RecordNameEntity e = new RecordNameEntity();
            e.setOrcid(orcid);
            records.add(e);
        }
        return records;
    }
    
}
