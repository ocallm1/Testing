package com.clearstream.hydrogen.messagetransform.converttohydrogendata;

import com.clearstream.hydrogen.dataaccess.AddressRepository;
import com.clearstream.hydrogen.dataaccess.CountryDataRepository;
import com.clearstream.hydrogen.dataaccess.FinancialInstitutionRepository;
import com.clearstream.hydrogen.dataaccess.LegalStructureRepository;
import com.clearstream.hydrogen.dataaccess.LocationRepository;
import com.clearstream.hydrogen.dataaccess.MarketNavFrequencyRepository;
import com.clearstream.hydrogen.dataaccess.RolesRepository;
import com.clearstream.hydrogen.dataaccess.ShareClassIdentifierRepostitory;
import com.clearstream.hydrogen.dataaccess.ShareClassPriipRepository;
import com.clearstream.hydrogen.dataaccess.ShareClassRepository;
import com.clearstream.hydrogen.dataaccess.SubfundRepository;
import com.clearstream.hydrogen.dataaccess.TaxCharacteristicsRepository;
import com.clearstream.hydrogen.dataaccess.UmbrellaRepository;
import com.clearstream.hydrogen.database.*;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans.MessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.util.CollectionUtils;

import javax.transaction.Transactional;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CreateOrUpdateService {
    public static final String NO_UPDATE_DATA_FOUND_FOR = "No update data found for";
    @Autowired
    ShareClassRepository shareClassRepository;
    @Autowired
    SubfundRepository subfundRepository;
    @Autowired
    UmbrellaRepository umbrellaRepository;
    @Autowired
    MarketNavFrequencyRepository marketNavFrequencyRepository;
    @Autowired
    LegalStructureRepository legalStructureRepository;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    FinancialInstitutionRepository financialInstitutionRepository;
    @Autowired
    ShareClassPriipRepository shareClassPriipRepository;
    @Autowired
    TaxCharacteristicsRepository taxCharacteristicsRepository;
    @Autowired
    ShareClassIdentifierRepostitory shareClassIdentifierRepostitory;
    @Autowired
    RolesRepository rolesRepository;
    @Autowired
    CountryDataRepository countryDataRepository;
    @Autowired
    LocationRepository locationRepository;

    /**
     * Need to roll back all after any failure to save or update any entity
     * @param objectsToSaveMap
     * @param messageHeaders
        */
    @Transactional
    public void save(Map objectsToSaveMap,MessageHeaders messageHeaders) {
        try {
            // ACTIVITY_NM=SecRefDataUpdated (Security Updates))
            if (RedexDefinitions.SEC_REFDATA_UPDATED.equalsIgnoreCase(messageHeaders.getActivityName())) {
                saveSecurity(objectsToSaveMap,  messageHeaders);
            }else{
                // ACTIVITY_NM=InstRefDataUpdated (Financial Institution Updates)
                // Note always use Common Code from Header here, that is existing Fund/Security.
                saveFinancialInstitution(objectsToSaveMap, messageHeaders.getInstituteCode());
            }
        }
        // Message roll back onto queue and by default after 4 retired activemq moves to a DLQ
        // Rollback all if any individual save fails, failed messages need to go on DLQ
        catch(UnexpectedRollbackException unexpectedRollbackException){
            if (unexpectedRollbackException.getMostSpecificCause() instanceof SQLIntegrityConstraintViolationException){
                log.error(unexpectedRollbackException.getMessage(),unexpectedRollbackException);
                throw new HydrogenSaveMessageException(unexpectedRollbackException.getMessage(),unexpectedRollbackException);
            }
        }
        catch(Exception exception) {
            log.error(exception.getMessage(),exception);
            throw new HydrogenSaveMessageException(exception.getMessage(), exception);
        }
    }

    private void saveSecurity(Map objectsToSaveMap, MessageHeaders messageHeaders) {
        CreUmbrellaFund creUmbrellaFund= (CreUmbrellaFund) objectsToSaveMap.get(CreUmbrellaFund.class.getName());
        CreSubfund creSubfund = (CreSubfund) objectsToSaveMap.get(CreSubfund.class.getName());
        CreShareClass creShareClass = (CreShareClass) objectsToSaveMap.get(CreShareClass.class.getName());

        creUmbrellaFund=saveUmbrellaFundChanges(creUmbrellaFund);


        FndLegalStructure fndLegalStructureFromMap = (FndLegalStructure) objectsToSaveMap.get(FndLegalStructure.class.getName());


        creSubfund = saveCreSubfundChanges(creUmbrellaFund, creSubfund, fndLegalStructureFromMap, creShareClass);



        MrkNavFrequency mrkNavFrequency = (MrkNavFrequency) objectsToSaveMap.get(MrkNavFrequency.class.getName());
        saveMrkNavFrequencyFromChanges(mrkNavFrequency, creShareClass);


        RglShareClassPriip rglShareClassPriipFromMap = (RglShareClassPriip) objectsToSaveMap.get(RglShareClassPriip.class.getName());
        saveRglShareClassPriipChanges(rglShareClassPriipFromMap, creShareClass);


        creShareClass = saveShareClassChanges(messageHeaders.isNewObj(),creSubfund, creShareClass);

        saveNewCreShareClassIdentifiers(messageHeaders, creShareClass);

        TxsTaxCharacteristics txsTaxCharacteristicsFromMap =(TxsTaxCharacteristics) objectsToSaveMap.get(TxsTaxCharacteristics.class.getName());
        saveTxsTaxCharacteristics( messageHeaders.isNewObj(), txsTaxCharacteristicsFromMap, creShareClass);
    }

    private void saveFinancialInstitution(final Map objectsToSaveMap, final String headerInstituteCodeValue)
    {
        FnnAddress fnnAddress = (FnnAddress) objectsToSaveMap.get(FnnAddress.class.getName());
        fnnAddress=saveFnnAddressChanges(fnnAddress);

        FnnFinancialInstitution fnnFinancialInstitutionFromMap = (FnnFinancialInstitution) objectsToSaveMap.get(FnnFinancialInstitution.class.getName());
        saveFinancialInstitutionChanges(headerInstituteCodeValue, fnnFinancialInstitutionFromMap,fnnAddress);
    }

    public void saveCommonCode(String commonCode, CreShareClass creShareClass ) {
        Instant now=Instant.now();
        CreShareClassIdentifier creShareClassIdentifierCOMC = new CreShareClassIdentifier();
        creShareClassIdentifierCOMC.setType("COMC");
        creShareClassIdentifierCOMC.setValue(commonCode);
        creShareClassIdentifierCOMC.setCreatedBy(RedexDefinitions.REDEX);
        creShareClassIdentifierCOMC.setCreateTs(now);
        creShareClassIdentifierCOMC.setUpdatedBy(RedexDefinitions.REDEX);
        creShareClassIdentifierCOMC.setUpdateTs(now);
        creShareClassIdentifierCOMC.setCreShareClass(creShareClass);
        shareClassIdentifierRepostitory.save(creShareClassIdentifierCOMC);

        // Apply the common code share class identifier to the role(s).
        if (!CollectionUtils.isEmpty(creShareClass.getFnnRoles())){
            for (FnnRole fnnRole : creShareClass.getFnnRoles()){
                if (fnnRole.getCreShareClassIdentifier() == null){
                    fnnRole.setCreShareClassIdentifier(creShareClassIdentifierCOMC);
                }
            }
            rolesRepository.saveAll(creShareClass.getFnnRoles());
        }
    }

    private void saveNewCreShareClassIdentifiers(MessageHeaders messageHeaders,CreShareClass newCreShareClass) {
        if (!messageHeaders.isNewObj()){
            return;

        }

        Instant now=Instant.now();

        saveCommonCode(messageHeaders.getCommonCode(), newCreShareClass);

        if (!messageHeaders.isHome()){
            return;
        }

        CreShareClassIdentifier creShareClassIdentifierISIN = new CreShareClassIdentifier();
        creShareClassIdentifierISIN.setType("ISIN");
        creShareClassIdentifierISIN.setValue(messageHeaders.getIsin());
        creShareClassIdentifierISIN.setCreatedBy(RedexDefinitions.REDEX);
        creShareClassIdentifierISIN.setCreateTs(now);
        creShareClassIdentifierISIN.setUpdatedBy(RedexDefinitions.REDEX);
        creShareClassIdentifierISIN.setUpdateTs(now);
        creShareClassIdentifierISIN.setCreShareClass(newCreShareClass);
        shareClassIdentifierRepostitory.save(creShareClassIdentifierISIN);
    }

    /**
     * @param newFund
     * @param txsTaxCharacteristics
     * @param creShareClassNew
     */
    private void saveTxsTaxCharacteristics(boolean newFund, TxsTaxCharacteristics txsTaxCharacteristics,CreShareClass creShareClassNew) {
        if (txsTaxCharacteristics==null){
            return;
        }

        txsTaxCharacteristics.setUpdatedBy(RedexDefinitions.REDEX);
        txsTaxCharacteristics.setUpdateTs(Instant.now());
        if (txsTaxCharacteristics.getCreShareClass()==null){
            txsTaxCharacteristics.setCreShareClass(creShareClassNew);
        }


        if ( txsTaxCharacteristics.getFnnCountry() ==null) {
            Optional<FnnCountry> fnnCountrybyId = countryDataRepository.findByIsoCodeAlpha3("XX");
            if (fnnCountrybyId.isPresent()) {
                txsTaxCharacteristics.setFnnCountry(fnnCountrybyId.get());
            }
        }

        taxCharacteristicsRepository.save(txsTaxCharacteristics);
    }

    /**

     * @param rglShareClassPriip
     * @param creShareClassNew
     */
    private void saveRglShareClassPriipChanges(RglShareClassPriip rglShareClassPriip,CreShareClass creShareClassNew) {
        if(rglShareClassPriip==null){
            return;
        }

        rglShareClassPriip.setUpdatedBy(RedexDefinitions.REDEX);
        rglShareClassPriip.setUpdateTs(Instant.now());
        if (creShareClassNew!=null) {
            creShareClassNew.setRglShareClassPriip(rglShareClassPriip);
        }
        shareClassPriipRepository.save(rglShareClassPriip);
    }



    /**
     * @param fndLegalStructure
     * @param creSubfundNew
     */
    private void saveFndLegalStructureFromChanges(FndLegalStructure fndLegalStructure,CreSubfund creSubfundNew) {
        if (fndLegalStructure == null){
            return;
        }

        fndLegalStructure.setUpdatedBy(RedexDefinitions.REDEX);
        fndLegalStructure.setUpdateTs(Instant.now());
        if (fndLegalStructure.getCreSubfund()==null){
            fndLegalStructure.setCreSubfund(creSubfundNew);
        }

        legalStructureRepository.save(fndLegalStructure);
    }

    private void saveMrkNavFrequencyFromChanges(MrkNavFrequency mrkNavFrequency, CreShareClass creShareClass) {
        if (mrkNavFrequency==null){
            return;
        }

        mrkNavFrequency.setUpdatedBy(RedexDefinitions.REDEX);
        mrkNavFrequency.setUpdateTs(Instant.now());

        if (creShareClass!=null) {
            creShareClass.setMrkNavFrequency(mrkNavFrequency);
        }
        marketNavFrequencyRepository.save(mrkNavFrequency);
    }

    /**
     *
     * @param newCreUmbrellaFund
     * @param creSubfund
     * @return
     */
    private CreSubfund saveCreSubfundChanges(CreUmbrellaFund newCreUmbrellaFund, CreSubfund creSubfund, FndLegalStructure fndLegalStructureFromMap, CreShareClass creShareClass) {

        //existing subfund has already been assigned to share class so no processing needed in here
       if (creShareClass!=null && creShareClass.getCreSubfund()!=null && creShareClass.getCreSubfund().getId()!=null && StringUtils.isNotEmpty(creShareClass.getCreSubfund().getShortCode())){
           return creShareClass.getCreSubfund();
       }

        if (creSubfund == null) {
            return null;
        }

        if (creSubfund.getCreUmbrellaFund()==null) {
            if (newCreUmbrellaFund == null) {
                newCreUmbrellaFund = umbrellaRepository.findByUmbrellaName(RedexDefinitions.UNKNOWN_UMBRELLA);
            }
            creSubfund.setCreUmbrellaFund(newCreUmbrellaFund);
        }

        if (StringUtils.isEmpty(creSubfund.getShortCode())){
            //already have default subfund wth same form of share under umbrella
            CreSubfund existingCreSubfund=null;
            if (fndLegalStructureFromMap!=null &&  StringUtils.isNotEmpty(fndLegalStructureFromMap.getOpenendedOrClosedendedFundStructure())){
                existingCreSubfund=subfundRepository.findByUmbrellaIdAndLegalFundNameOnlyAndFormOfShareAndFundStructure(creSubfund.getCreUmbrellaFund().getId(),creSubfund.getLegalFundNameOnly(),creSubfund.getFormOfShare(),fndLegalStructureFromMap.getOpenendedOrClosedendedFundStructure());
            }else{
                existingCreSubfund=subfundRepository.findByUmbrellaIdAndLegalFundNameOnlyAndFormOfShare(creSubfund.getCreUmbrellaFund().getId(),creSubfund.getLegalFundNameOnly(),creSubfund.getFormOfShare());
            }

            if (existingCreSubfund!=null){
                creShareClass.setCreSubfund(existingCreSubfund);
                return existingCreSubfund;
            }
        }

        creSubfund.setUpdatedBy(RedexDefinitions.REDEX);
        creSubfund.setUpdateTs(Instant.now());

        creSubfund= subfundRepository.save(creSubfund);

        saveFndLegalStructureFromChanges(fndLegalStructureFromMap, creSubfund);

        return creSubfund;
    }

    /**
     *
     * @param creUmbrellaFund
     * @return
     */
    private CreUmbrellaFund saveUmbrellaFundChanges(CreUmbrellaFund creUmbrellaFund) {
        if (creUmbrellaFund==null){
            return null;
        }


        creUmbrellaFund.setUpdatedBy(RedexDefinitions.REDEX);
        creUmbrellaFund.setUpdateTs(Instant.now());

        return umbrellaRepository.save(creUmbrellaFund);
}


    private CreShareClass saveShareClassChanges(Boolean newFund, CreSubfund creSubfund, CreShareClass creShareClass) {
        if (creShareClass==null){
            return null;
        }

        creShareClass.setUpdatedBy(RedexDefinitions.REDEX);
        creShareClass.setUpdateTs(Instant.now());

        if (!newFund){
            return shareClassRepository.save(creShareClass);
        }


        if ( creShareClass.getCreSubfund()==null) {
            if (creSubfund == null) {
                //get default subfund over default umbrella
                CreUmbrellaFund creUmbrellaFund = umbrellaRepository.findByUmbrellaName(RedexDefinitions.UNKNOWN_UMBRELLA);
                creSubfund = subfundRepository.findByUmbrellaIdAndLegalFundNameOnly(creUmbrellaFund.getId(), RedexDefinitions.UNKNOWN_SUBFUND);
            }

            creShareClass.setCreSubfund(creSubfund);
        }
        if (creShareClass.getFpgOverridden() == null) {
            creShareClass.setFpgOverridden(Boolean.FALSE);
        }

        creShareClass= shareClassRepository.save(creShareClass);
        if (creShareClass.getFnnRoles()!=null && !creShareClass.getFnnRoles().isEmpty()){
            rolesRepository.saveAll(creShareClass.getFnnRoles());
        }

        return creShareClass;
    }

    //=========================================================================
    // Process Financial Institutions (Roles) - Updates and Creates
    //=========================================================================
    /**
     *
     * @param headerInstituteShortCodeValue
     * @param fnnFinancialInstitution
     */
    private void saveFinancialInstitutionChanges(String headerInstituteShortCodeValue, FnnFinancialInstitution fnnFinancialInstitution,FnnAddress fnnAddress) {
        if (fnnFinancialInstitution == null) {
            if (fnnAddress==null){
                return;
            }
            //just address change if in here
            FnnFinancialInstitution chnageAddressInstitiute = financialInstitutionRepository.findByshortCodeWithLocations(headerInstituteShortCodeValue);
            saveInstituteLocations(chnageAddressInstitiute.getFnnLocations(),chnageAddressInstitiute,fnnAddress);
            return;
        }

        //update, so copy original properties first
        if (fnnFinancialInstitution != null) {
            // When update is made to Financial Institution Name associated to Role Type "FUNDSUBFUND"
            // Update is required to the CRE_SUBFUND Legal Name as well as to the actual Financial Institution Name .
            if (StringUtils.isNotEmpty(fnnFinancialInstitution.getName())) {
                List<CreSubfund> creSubfunds = subfundRepository.listByShortCode(headerInstituteShortCodeValue);
                // Update is required to the CRE_SUBFUND Legal Name
                Instant now = Instant.now();
                creSubfunds.stream().forEach(creSubfund -> {
                    creSubfund.setLegalFundNameOnly(fnnFinancialInstitution.getName());
                    creSubfund.setUpdatedBy(RedexDefinitions.REDEX);
                    creSubfund.setUpdateTs(Instant.now());
                    subfundRepository.save(creSubfund);
                });
            }


            fnnFinancialInstitution.setUpdatedBy(RedexDefinitions.REDEX);
            fnnFinancialInstitution.setUpdateTs(Instant.now());
            FnnFinancialInstitution savedFnnFinancialInstitution = financialInstitutionRepository.save(fnnFinancialInstitution);

            if (fnnAddress == null) {
                return;
            }

            saveInstituteLocations(fnnFinancialInstitution.getFnnLocations(), savedFnnFinancialInstitution, fnnAddress);
        }
    }


    private void saveInstituteLocations(Set<FnnLocation> fnnLocationList,FnnFinancialInstitution savedFnnFinancialInstitution,FnnAddress fnnAddress){
        Instant createTime=Instant.now();

        if (fnnLocationList==null || fnnLocationList.size()==0){
            FnnLocation fnnLocation=new FnnLocation();

            fnnLocation.setCreatedBy(RedexDefinitions.REDEX);

            fnnLocation.setCreateTs(createTime);
            fnnLocation.setAddressType("LEGAL");
            fnnLocation.setFnnAddress(fnnAddress);

            fnnLocationList=new HashSet();
            fnnLocationList.add(fnnLocation);
        }

        fnnLocationList.stream().forEach(fnnLocation->{
            fnnLocation.setUpdateTs(createTime);
            fnnLocation.setUpdatedBy(RedexDefinitions.REDEX);
            fnnLocation.setFnnFinancialInstitution(savedFnnFinancialInstitution);
            fnnLocation.setFnnAddress(fnnAddress);
        });

        locationRepository.saveAll(fnnLocationList);
    }
    private FnnAddress saveFnnAddressChanges(final FnnAddress fnnAddress)
    {
        if (fnnAddress == null) {
            return null;
        }


        FnnAddress addressToFind=new FnnAddress();
        addressToFind.setStreet(fnnAddress.getStreet());
        Optional<FnnAddress> optional= addressRepository.findOne(Example.of(addressToFind));
        if (optional.isPresent()){
            return optional.get();
        }

        fnnAddress.setUpdatedBy(RedexDefinitions.REDEX);
        fnnAddress.setUpdateTs(Instant.now());
        if (fnnAddress.getFnnCountry()==null){
            Optional<FnnCountry> fnnCountrybyId = countryDataRepository.findByIsoCodeAlpha3("XX");
            fnnAddress.setFnnCountry(fnnCountrybyId.get());
        }
        return addressRepository.save(fnnAddress);
     }

    private void saveFnnRoleChanges(String headerInstituteShortCodeValue, CreShareClass creShareClassByCreShareClassIdentifiers,
                                    FnnRole fnnRoleFromMap) {

        //update, so copy original properties first
        if (fnnRoleFromMap != null) {
            // Also need to cross reference on header short code(should be unique) for the Financial Institute
            // TYPE [use CRE_SHRE_CLASS_ID stamped on FNN_ROLE table]
            Long id = creShareClassByCreShareClassIdentifiers.getId();
            List<FnnRole> fnnRoleList = rolesRepository.findByShareClassIdAndInstituteShortCode(
                    id, headerInstituteShortCodeValue);
            if (!CollectionUtils.isEmpty(fnnRoleList)){
                FnnRole roleByShareClass = fnnRoleList.get(0);
                // Copy any new values to change to final object
                FnnRole fnnRoleToSave = new FnnRole();
                BeanUtils.copyProperties(roleByShareClass,fnnRoleToSave);
                // Copy any new values to change to final object
                BeanUtils.copyProperties(fnnRoleFromMap,fnnRoleToSave,
                        ConvertToHydrogenDataHelper.getNullPropertyNames(fnnRoleFromMap));
                fnnRoleToSave.setCreShareClass(creShareClassByCreShareClassIdentifiers);
                // Set mandatory fields in final object
                fnnRoleToSave.setUpdatedBy(RedexDefinitions.REDEX);
                fnnRoleToSave.setUpdateTs(Instant.now());

                // Save changes from message and mandatory fields to DB
                rolesRepository.save(fnnRoleToSave);
                log.debug("fnnRole updated");

            }
        }
    }

}
