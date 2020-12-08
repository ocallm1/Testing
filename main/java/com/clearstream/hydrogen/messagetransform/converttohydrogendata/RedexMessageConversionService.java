package com.clearstream.hydrogen.messagetransform.converttohydrogendata;


import com.clearstream.hydrogen.dataaccess.*;
import com.clearstream.hydrogen.database.*;
import com.clearstream.hydrogen.entity.FundDefinitions;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans.MessageHeaders;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans.RoleTypeAndShortCode;
import com.clearstream.hydrogen.util.Util;
import com.clearstream.ifs.hydrogen.redex.ChangeNotification;
import com.clearstream.ifs.hydrogen.redex.Column;
import com.clearstream.ifs.hydrogen.redex.PrimaryKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.xerces.dom.ElementImpl;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Map.entry;


/**
 * Class to process incoming Message off the Q and convert to appropriate Entity Object in Hydrogen,
 * saving to the Hydrogen database and new objects or updating existing based on Common Code
 */
@Slf4j
@Service
@Transactional
    public class RedexMessageConversionService{

    public static final String FOUND_THE_COLUMN = "Found the column: ";
    public static final String NODE_VALUE_NEW_VALUE = "nodeValue new value: ";
    public static final String FIELD_NOT_RECOGNISED = "Field not recognised";
    public static final String PROCESSING_COLUMN = "Processing column: ";
    public static final String SECURITIES_REFERENCE_DATA_UPDATED = "SecRefDataUpdated";
    public static final String INSTITUTIONS_REFERENCE_DATA_UPDATED = "InstRefDataUpdated";
    private static Map<String, String> redexConvertionsLookupMap;

    @Autowired
    CreateOrUpdateService createOrUpdateService;
    @Autowired
    EntityObjectAccessService entityObjectAccessService;
    @Autowired
    RedexConversionRepository redexConversionRepository;
    @Autowired
    FinancialInstitutionRepository financialInstitutionRepository;
    @Autowired
    RolesRepository rolesRepository;
    @Autowired
    ShareClassRepository shareClassRepository;
    @Autowired
    SubfundRepository subfundRepository;
    @Autowired
    LegalStructureRepository legalStructureRepository;
    @Autowired
    TaxCharacteristicsRepository taxCharacteristicsRepository;
    @Autowired
    ShareClassPriipRepository shareClassPriipRepository;
    @Autowired
    ShareClassIdentifierRepostitory shareClassIdentifierRepostitory;
    @Autowired
    UmbrellaRepository umbrellaRepository;
    @Autowired
    MarketNavFrequencyRepository marketNavFrequencyRepository;
    @Autowired
    LocationRepository locationRepository;
//    @Autowired
//    NavF


    @PostConstruct
    private void postConstruct() {
        if (redexConvertionsLookupMap==null){
            List<AncRedexConversion> ancRedexConversionList = redexConversionRepository.getItemsForSystem(RedexDefinitions.REDEX);
            redexConvertionsLookupMap = new HashMap();
            ancRedexConversionList.forEach((AncRedexConversion ancRedexConversion) -> {
                redexConvertionsLookupMap.put(ancRedexConversion.getTableColumnKey()+"."+ancRedexConversion.getForeignValue(),
                        ancRedexConversion.getHydrogenValue());
            });
        }
    }

    private Long getShareClassIdentifier(MessageHeaders messageHeaders){
        CreShareClass creShareClassByIdentifier = null;

        if (messageHeaders.getActivityName().equalsIgnoreCase("SecRefDataUpdated")) {
            creShareClassByIdentifier = shareClassRepository.findCreShareClassByCreShareClassIdentifiers(FundDefinitions.COMMON_CODE, messageHeaders.getCommonCode());

            // its an existing fund so use head identifier passed in
            if (creShareClassByIdentifier != null) {
                if (messageHeaders.isNewObj()) {
                    throw new HydrogenMessageException("Already have common code " + messageHeaders.getCommonCode());
                }
                return creShareClassByIdentifier.getId();

            } else if (!messageHeaders.isHome()) {
                creShareClassByIdentifier = shareClassRepository.findCreShareClassByCreShareClassIdentifiers(FundDefinitions.ISIN, messageHeaders.getIsin());
                if (creShareClassByIdentifier != null) {
                  return  creShareClassByIdentifier.getId();
                }
            }
        }

        return null;
    }

    private String getInstitutionShortCode(final MessageHeaders messageHeaders)
    {
        String result = null;
        if (messageHeaders.getActivityName().equalsIgnoreCase(INSTITUTIONS_REFERENCE_DATA_UPDATED)) {
            result = messageHeaders.getInstituteCode();
        }
        return result;
    }

    private CreUmbrellaFund umbrellaSetup(ChangeNotification changeNotification,Long shareClassUpdateId, Map<String, BaseEntity> propertiesToSaveMap){
        List<Column>sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(RedexDefinitions.TABLE_RD_FIN_SEC_CMC_FUND, changeNotification);
        return convertFromRdFinSecCmcFundTable(shareClassUpdateId, sourceColumnListChanges, propertiesToSaveMap);
    }





    /**
     * Beginning of the processing aka converting of the message to Hydrogen structure
     * @param changeNotification
     */
    public void processNotificationForHydrogen(ChangeNotification changeNotification,MessageHeaders messageHeaders) throws Exception {
        try {
            List<PrimaryKey> primaryKeysForChange=null;
            Long shareClassUpdateId = getShareClassIdentifier(messageHeaders);
            String institutionShortCode = getInstitutionShortCode(messageHeaders);

            // Note: The following map will contain a single row for each class to be created or updated
            // It will only be added to once we have the first property to save.
            Map<String, BaseEntity> propertiesToSaveMap = new HashMap();

            if( !messageHeaders.isHome() &&
                    RedexDefinitions.SEC_REFDATA_UPDATED.equalsIgnoreCase(messageHeaders.getActivityName())){
                // scenario 7, New Fund or Additional Common Code/Depository added on existing Fund
                addNonHomeDepotsAndRoles(shareClassUpdateId, messageHeaders, propertiesToSaveMap, changeNotification);
                return;
            }

            if (RedexDefinitions.SEC_REFDATA_UPDATED.equalsIgnoreCase(messageHeaders.getActivityName())) {
                CreUmbrellaFund creUmbrellaFund = umbrellaSetup(changeNotification, shareClassUpdateId, propertiesToSaveMap);
                primaryKeysForChange = ConvertToHydrogenDataHelper.getRowChangesFor(RedexDefinitions.TABLE_RD_INST_ROLE_STRC_FIN_SEC, changeNotification);
                subFundSetup(primaryKeysForChange, propertiesToSaveMap, messageHeaders.getCommonCode(), messageHeaders.isNewObj(), creUmbrellaFund);
            }


            List<Column> sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_TECH_RD_FIN_SEC_REP_DRVR, changeNotification);
            convertFromTechRdFinSecRepDrvrTable(shareClassUpdateId, sourceColumnListChanges, propertiesToSaveMap);

            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_FIN_SEC_CMC_DETL, changeNotification);
            convertFromLegalFormTypeIdTable(shareClassUpdateId, RedexDefinitions.TABLE_RD_FIN_SEC_CMC_DETL, sourceColumnListChanges, propertiesToSaveMap);



            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_INST, changeNotification);
            convertFromRdInstTable(shareClassUpdateId, institutionShortCode, sourceColumnListChanges, propertiesToSaveMap);

            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_FIN_SEC_FUND, changeNotification);
            convertFromRdFinSecFundTable(shareClassUpdateId, RedexDefinitions.TABLE_RD_FIN_SEC_FUND, sourceColumnListChanges, propertiesToSaveMap);

            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_FIN_SEC, changeNotification);
            convertFromRdFinSecTable(shareClassUpdateId, RedexDefinitions.TABLE_RD_FIN_SEC, sourceColumnListChanges, propertiesToSaveMap);

            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_FIN_SEC_GEN_OPT, changeNotification);
            convertFromRdFinSecGenOptTable(shareClassUpdateId, RedexDefinitions.TABLE_RD_FIN_SEC_GEN_OPT, sourceColumnListChanges, propertiesToSaveMap);

            sourceColumnListChanges = ConvertToHydrogenDataHelper.getSourceColumnListChangesFor(
                    RedexDefinitions.TABLE_RD_INST_ADDR, changeNotification);
            convertFromRdInstAddrTable(shareClassUpdateId, institutionShortCode, sourceColumnListChanges, propertiesToSaveMap);


            primaryKeysForChange = ConvertToHydrogenDataHelper.getRowChangesFor(RedexDefinitions.TABLE_RD_INST_ROLE_STRC_FIN_SEC_CMC, changeNotification);
            convertFromRdInstRoleStrcFinSecCmcForPKs(primaryKeysForChange, propertiesToSaveMap, messageHeaders.getCommonCode(), messageHeaders.isNewObj(),false);

            // do same as above
            primaryKeysForChange = ConvertToHydrogenDataHelper.getRowChangesFor(RedexDefinitions.TABLE_RD_FIN_SEC_PSK_INST_ROLE, changeNotification);
            convertFromRdInstRoleStrcFinSecCmcForPKs(primaryKeysForChange, propertiesToSaveMap, messageHeaders.getCommonCode(), messageHeaders.isNewObj(),true);

            // Finished processing, so save changes to database.
            createOrUpdateService.save(propertiesToSaveMap, messageHeaders);
        }
        catch(HydrogenSaveMessageException hydrogenSaveMessageException) {
           log.error(hydrogenSaveMessageException.getMessage(),hydrogenSaveMessageException);
           throw hydrogenSaveMessageException;
        }
        catch(Exception exception) {
            log.error(exception.getMessage(),exception);
            throw new HydrogenMessageException(exception.getMessage(), exception);
        }
    }

    private void addNonHomeDepotsAndRoles(Long shareClassUpdateId, MessageHeaders messageHeaders, Map<String, BaseEntity> propertiesToSaveMap, ChangeNotification changeNotification) {
        CreShareClass creShareClass = shareClassRepository.getOne(shareClassUpdateId);
        // since getShareClassIdentifier() above throws up in case of existing common code,
        // it's safe to just add the common code here
        createOrUpdateService.saveCommonCode(messageHeaders.getCommonCode(), creShareClass);
        List<PrimaryKey> primaryKeysForChange = ConvertToHydrogenDataHelper.getRowChangesFor(RedexDefinitions.TABLE_RD_INST_ROLE_STRC_FIN_SEC_CMC, changeNotification);
        convertFromRdInstRoleStrcFinSecCmcForPKs(primaryKeysForChange, propertiesToSaveMap, messageHeaders.getCommonCode(), messageHeaders.isNewObj(),false);
        primaryKeysForChange = ConvertToHydrogenDataHelper.getRowChangesFor(RedexDefinitions.TABLE_RD_FIN_SEC_PSK_INST_ROLE, changeNotification);
        convertFromRdInstRoleStrcFinSecCmcForPKs(primaryKeysForChange, propertiesToSaveMap, messageHeaders.getCommonCode(), messageHeaders.isNewObj(),true);
    }

    private Map convertFromRdInstAddrTable(final Long shareClassUpdateId, final String financialInstitutionShortCode, final List<Column> changes, final Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                switch (column.getName()) {
                    case RedexDefinitions.RIA_STREET_NM_AND_NBR: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        FnnAddress fnnAddressFromMap = manageMapForFinancialInstitutionAddress(propertiesToSaveMap);
                        fnnAddressFromMap.setStreet(nodeValue);
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });

        return propertiesToSaveMap;
    }

    /**
     * Convert from Redex table data to Hydrogen table data
     *
     *
     * @param shareClassUpdateId
     * @param tableName
     * @param changes - list of columns changed for this Redex table
     * @param propertiesToSaveMap
     * @return
     */
    private Map convertFromLegalFormTypeIdTable(Long shareClassUpdateId, String tableName, List<Column> changes, Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                switch (column.getName()) {
                    case RedexDefinitions.RFSCD_LEG_FORM_TYP_ID: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        FndLegalStructure newLegalStructureMapOrUpdateFromDB =
                                getNewLegalStructureMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
                        String lookupValue= tableName+"."+column.getName()+"."+nodeValue;
                        newLegalStructureMapOrUpdateFromDB.setLegalForm(redexConvertionsLookupMap.get(lookupValue));
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });
        return propertiesToSaveMap;
    }

    private Map convertFromRdInstTable(Long shareClassUpdateId, String institutionShortCode, List<Column> changes, Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            log.debug(PROCESSING_COLUMN +column.getName());
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                switch (column.getName()) {
                    case RedexDefinitions.RTRI_NM: {
                        FnnFinancialInstitution fnnFinancialInstitution = manageMapForFinancialInstitution(propertiesToSaveMap, institutionShortCode);
                        fnnFinancialInstitution.setName(nodeValue);
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });

        return propertiesToSaveMap;
    }

    private CreUmbrellaFund convertFromRdFinSecCmcFundTable(Long shareClassUpdateId, List<Column> changes, Map propertiesToSaveMap) {
        CreUmbrellaFund creUmbrellaFund[]= new CreUmbrellaFund[1];
        creUmbrellaFund[0]=null;
        changes.forEach((Column column) -> {
            log.debug(PROCESSING_COLUMN + column.getName());
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (newValue != null) {
                switch (column.getName()) {
                    case RedexDefinitions.RFSCF_UMBR_NM: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        creUmbrellaFund[0]=umbrellaRepository.findUmbrellaFundByUmbrellaName(nodeValue);

                        if( creUmbrellaFund[0]==null) {
                            creUmbrellaFund[0] = getNewCreUmbrellaFundMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
                            creUmbrellaFund[0].setUmbrellaName(nodeValue);
                        }
                        break;
                    }
                    default: {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });
        return  creUmbrellaFund[0];
    }

    private Map<String, RoleTypeAndShortCode> getRoleTypeAndShortCode(final List<PrimaryKey> changes){
        final Map<String, RoleTypeAndShortCode> roleTypeAndShortCodeMap = new HashMap<>();
        // Put the columns we care about into a list.
        for (PrimaryKey primaryKey : changes){
            RoleTypeAndShortCode roleTypeAndShortCode = roleTypeAndShortCodeMap.get(primaryKey.getValue());
            if (roleTypeAndShortCode == null){
                roleTypeAndShortCode = new RoleTypeAndShortCode();
                roleTypeAndShortCodeMap.put(primaryKey.getValue(), roleTypeAndShortCode);
            }
            for (Column column : primaryKey.getColumn()){
                if (column.getName().equals(RedexDefinitions.RIRSFS_INST_ROLE_TYP_ID)) {
                    roleTypeAndShortCode.setOldRoleType(ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl)column.getOldValue()));
                    roleTypeAndShortCode.setNewRoleType(ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl)column.getNewValue()));
                }
                else if (column.getName().equals(RedexDefinitions.RIRSFS_INST_SHT_CD)) {
                    roleTypeAndShortCode.setOldShortCode(ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl)column.getOldValue()));
                    roleTypeAndShortCode.setNewShortCode(ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl)column.getNewValue()));
                }
            }
        }

        return roleTypeAndShortCodeMap;
    }

    private void convertFromRdInstRoleStrcFinSecCmcForPKs(final List<PrimaryKey> changes, final Map<String,BaseEntity>  propertiesToSaveMap, final String headerCommonCodeValue, final Boolean newObjectValue,final boolean isDepotChange)
    {
        final Map<String, RoleTypeAndShortCode> roleTypeAndShortCodeMap = getRoleTypeAndShortCode(changes);
        final CreShareClass creShareClass = shareClassRepository.findCreShareClassByCreShareClassIdentifiers(FundDefinitions.COMMON_CODE, headerCommonCodeValue);


        // Process the list of columns we care about.
        for (RoleTypeAndShortCode roleTypeAndShortCode : roleTypeAndShortCodeMap.values()){
            if (RedexDefinitions.FUNDSUBFUND.equalsIgnoreCase(roleTypeAndShortCode.getOldRoleType())
                    || RedexDefinitions.FUNDSUBFUND.equalsIgnoreCase(roleTypeAndShortCode.getNewRoleType())) {
                continue;
            }

             processFinancialRolesInstitiutions( creShareClass , roleTypeAndShortCode ,headerCommonCodeValue, newObjectValue,isDepotChange,propertiesToSaveMap);
        }
    }


    private void subFundSetup(final List<PrimaryKey> changes, final Map<String,BaseEntity>  propertiesToSaveMap, final String headerCommonCodeValue, final Boolean newObjectValue, CreUmbrellaFund creUmbrellaFund){
        CreShareClass creShareClass =null;
        CreSubfund creSubfund=null;
        Long umbrellaId=null;
        final Map<String, RoleTypeAndShortCode> roleTypeAndShortCodeMap = getRoleTypeAndShortCode(changes);




        if (newObjectValue) {
            if (creUmbrellaFund != null && creUmbrellaFund.getId() != null) {
                umbrellaId = creUmbrellaFund.getId();
                if (roleTypeAndShortCodeMap==null || roleTypeAndShortCodeMap.values().size()==0){
                    creShareClass=getNewShareClassMapOrUpdateFromDB(null,propertiesToSaveMap);
                    creSubfund=getNewCreSubfundMapOrUpdateFromDB(null,propertiesToSaveMap);
                    creSubfund.setCreUmbrellaFund(creUmbrellaFund);
                    creShareClass.setCreSubfund(creSubfund);
                    return;
                }

            }
        }else{
            creShareClass=shareClassRepository.findCreShareClassByCreShareClassIdentifiers(FundDefinitions.COMMON_CODE, headerCommonCodeValue);
            CreSubfund currentSubFund = subfundRepository.findByShareClassEagerFetchUmbrella(creShareClass.getId());
            umbrellaId=currentSubFund.getCreUmbrellaFund().getId();

        }



        // Process the list of columns we care about.
        for (RoleTypeAndShortCode roleTypeAndShortCode : roleTypeAndShortCodeMap.values()){
            if (!RedexDefinitions.FUNDSUBFUND.equalsIgnoreCase(roleTypeAndShortCode.getNewRoleType()) || StringUtils.isEmpty(roleTypeAndShortCode.getNewShortCode())) {
                    continue;
            }


            if (newObjectValue){
                creShareClass=getNewShareClassMapOrUpdateFromDB(null,propertiesToSaveMap);
            }else{
                propertiesToSaveMap.put(CreShareClass.class.getName(),creShareClass);
            }

            FnnFinancialInstitution newFinancialInstitution = financialInstitutionRepository.findByShortCode(roleTypeAndShortCode.getNewShortCode());
            if (newFinancialInstitution==null){
                createFinancialInstitution(roleTypeAndShortCode.getNewShortCode(),roleTypeAndShortCode.getNewShortCode());
            }

            if (umbrellaId!=null){
                creSubfund=subfundRepository.findByUmbrellaIdAndShortCode(umbrellaId, roleTypeAndShortCode.getNewShortCode());
            }else{
                List<CreSubfund> subfundList=subfundRepository.listByShortCode(roleTypeAndShortCode.getNewShortCode());
                if (subfundList!=null && subfundList.size()>0){
                    creSubfund=subfundList.get(0);
                }
            }

            if (creSubfund!=null){
                creShareClass.setCreSubfund(creSubfund);
                return;
            }

            creSubfund=getNewCreSubfundMapOrUpdateFromDB(null,propertiesToSaveMap);
            creSubfund.setShortCode(roleTypeAndShortCode.getNewShortCode());
            creSubfund.setCreUmbrellaFund(creUmbrellaFund);
            if (newFinancialInstitution!=null){
                creSubfund.setLegalFundNameOnly(newFinancialInstitution.getName());
            }else {
                creSubfund.setLegalFundNameOnly(roleTypeAndShortCode.getNewShortCode());
            }
            creShareClass.setCreSubfund(creSubfund);
        }


    }

    private void processFinancialRolesInstitiutions( CreShareClass creShareClass , RoleTypeAndShortCode roleTypeAndShortCode,final String commonCode, final Boolean newObjectValue,final boolean isDepotChange,Map<String,BaseEntity>  propertiesToSaveMap){
        FnnFinancialInstitution newFinancialInstitution = null;
        if (StringUtils.isNotEmpty(roleTypeAndShortCode.getNewShortCode())){
            newFinancialInstitution = financialInstitutionRepository.findByShortCode(roleTypeAndShortCode.getNewShortCode());
            if (newFinancialInstitution == null){
                if (isDepotChange){
                    newFinancialInstitution = createFinancialInstitution(roleTypeAndShortCode.getNewShortCode(),roleTypeAndShortCode.getNewShortCode());
                }else {
                    newFinancialInstitution = createFinancialInstitution(roleTypeAndShortCode.getNewShortCode());
                }
            }
        }

        if (newObjectValue){
            if (StringUtils.isNotEmpty(roleTypeAndShortCode.getNewShortCode()) &&
                    StringUtils.isNotEmpty(roleTypeAndShortCode.getNewRoleType())){
                CreShareClass newShareClass =(CreShareClass)propertiesToSaveMap.get(CreShareClass.class.getName());
                FnnRole fnnRole = createRole(roleTypeAndShortCode.getNewRoleType(), commonCode, newFinancialInstitution,newShareClass);
            }
        }else{
            List<FnnRole> oldRoleList=null;
            if (StringUtils.isNotEmpty(roleTypeAndShortCode.getOldShortCode())) {
                oldRoleList = rolesRepository.findByShareClassIdAndInstituteShortCode(creShareClass.getId(), roleTypeAndShortCode.getOldShortCode());
            }

            if (StringUtils.isNotEmpty(roleTypeAndShortCode.getNewShortCode())){

                if (StringUtils.isNotEmpty(roleTypeAndShortCode.getNewRoleType())){
                    updateRole(roleTypeAndShortCode.getOldRoleType(), roleTypeAndShortCode.getNewRoleType(), commonCode, newFinancialInstitution,oldRoleList);
                }else if (isDepotChange){
                    updateRole(RedexDefinitions.DEPOSITORY, RedexDefinitions.DEPOSITORY, commonCode, newFinancialInstitution,oldRoleList);
                }
            }else{
                updateRole(roleTypeAndShortCode.getOldRoleType(), roleTypeAndShortCode.getNewRoleType(), commonCode, newFinancialInstitution,oldRoleList);
            }
        }
    }

    private void createAndSaveNewLinkingRole(String headerCommonCodeValue, List<String> roleTypesAndFiShortCodes,
                                             int i) {
        FnnRole fnnRole = new FnnRole();
        fnnRole.setCreatedBy(RedexDefinitions.REDEX);
        fnnRole.setCreateTs(Instant.now());
        fnnRole.setType(roleTypesAndFiShortCodes.get(i));
        CreShareClass creShareClassFind = shareClassRepository.findCreShareClassByCreShareClassIdentifiers(
                FundDefinitions.COMMON_CODE, headerCommonCodeValue);
        fnnRole.setCreShareClass(creShareClassFind);
        rolesRepository.save(fnnRole);
    }

    private FnnRole createRole(final String type, final String headerCommonCodeValue, final FnnFinancialInstitution fnnFinancialInstitution) {
        return createRole ( type,  headerCommonCodeValue,  fnnFinancialInstitution,null);
    }
    private FnnRole createRole(final String type, final String headerCommonCodeValue, final FnnFinancialInstitution fnnFinancialInstitution,CreShareClass creShareClass) {
        FnnRole fnnRole = new FnnRole();
        fnnRole.setCreatedBy(RedexDefinitions.REDEX);
        fnnRole.setCreateTs(Instant.now());
        fnnRole.setStartingDate(LocalDate.now());
        fnnRole.setType(type);
        fnnRole.setFnnFinancialInstitution(fnnFinancialInstitution);

        if (creShareClass == null){
            creShareClass = shareClassRepository.findCreShareClassByCreShareClassIdentifiers(FundDefinitions.COMMON_CODE, headerCommonCodeValue);
            Optional<CreShareClassIdentifier> creShareClassIdentifierOptional = creShareClass.getCreShareClassIdentifiers().stream().filter(identifier -> identifier.getType().equals(FundDefinitions.COMMON_CODE)).filter(identifier -> identifier.getValue().equals(headerCommonCodeValue)).findFirst();
            if (creShareClassIdentifierOptional.isPresent()){
                fnnRole.setCreShareClassIdentifier(creShareClassIdentifierOptional.get());
            }
            fnnRole.setCreShareClass(creShareClass);
            fnnRole = rolesRepository.save(fnnRole);
        }else{
            //dont have id here for new fund so save later when saving share class
            fnnRole.setCreShareClass(creShareClass);
            creShareClass.getFnnRoles().add(fnnRole);
        }
        return fnnRole;
    }

    private void updateRole(final String oldType, final String newType, final String headerCommonCodeValue, final FnnFinancialInstitution fnnFinancialInstitution, final List<FnnRole> oldRoles)
    {
        // expire the existing role.
        if (oldRoles!=null) {
            Optional<FnnRole> oldFnnRoleOptional = oldRoles.stream().filter(fnnRole -> fnnRole.getType().equalsIgnoreCase(oldType)).filter(fnnRole -> fnnRole.getEndingDate() == null).findFirst();
            oldFnnRoleOptional.ifPresent(oldFnnRole -> {
                oldFnnRole.setUpdateTs(Instant.now());
                oldFnnRole.setEndingDate(LocalDate.now());
                oldFnnRole.setUpdatedBy(RedexDefinitions.REDEX);
                rolesRepository.save(oldFnnRole);
            });
        }
        if (StringUtils.isNotEmpty(newType)) {
            // Create the new role.
            createRole(newType, headerCommonCodeValue, fnnFinancialInstitution);
        }
    }

    private FnnFinancialInstitution createFinancialInstitution(final String shortCode)
    {
        return  createFinancialInstitution(shortCode,null);
    }

    private FnnFinancialInstitution createFinancialInstitution(final String shortCode,final String depotCode)
    {
        FnnFinancialInstitution fnnFinancialInstitution = new FnnFinancialInstitution();
        fnnFinancialInstitution.setName(shortCode);
        fnnFinancialInstitution.setCreatedBy(RedexDefinitions.REDEX);
        fnnFinancialInstitution.setCreateTs(Instant.now());
        fnnFinancialInstitution.setShortCode(shortCode);
        fnnFinancialInstitution.setDepoCode(depotCode);
        fnnFinancialInstitution = financialInstitutionRepository.save(fnnFinancialInstitution);
        return fnnFinancialInstitution;
    }


    private Map convertFromRdFinSecGenOptTable(Long shareClassUpdateId, String tableName, List<Column> changes, Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            log.debug(PROCESSING_COLUMN +column.getName());
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                switch (column.getName()) {
                    case RedexDefinitions.RFSGO_EXER_FREQ_TYP_ID: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
                        String lookupValue= tableName+"."+column.getName()+"."+nodeValue;
                        creShareClass.setFrequencyOfDistributionDeclaration(redexConvertionsLookupMap.get(lookupValue));
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });
        return propertiesToSaveMap;
    }

    private Map convertFromRdFinSecTable(Long shareClassUpdateId, String tableName, List<Column> changes, Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            log.debug(PROCESSING_COLUMN +column.getName());
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                switch (column.getName()) {
                    case RedexDefinitions.RFS_CLOSE_OPEN_END_TYP_ID: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        convertFundLegalStructureOpenOrClosedEnd(shareClassUpdateId, nodeValue, propertiesToSaveMap);
                        break;
                    }
                    case RedexDefinitions.RFS_DIST_POLICY_TYP_ID: {
                        log.debug(FOUND_THE_COLUMN + column.getName());
                        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
                        String lookupValue= tableName+"."+column.getName()+"."+nodeValue;
                        String valueFromMap = redexConvertionsLookupMap.get(lookupValue);
                        if(valueFromMap!=null) {
                            creShareClass.setDistributionPolicy(valueFromMap);
                        }
                        else {
                            log.warn("REDEX FOREIGN-VALUE: "+ valueFromMap + " does not exist in ANC_REDEX_CONVERSION table");
                        }
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });

        return propertiesToSaveMap;
    }

    private Map convertFundLegalStructureOpenOrClosedEnd(Long shareClassUpdateId, String nodeValue, Map propertiesToSaveMap) {
        if(nodeValue.equalsIgnoreCase(RedexDefinitions.CLOSED_END) ||
                nodeValue.equalsIgnoreCase(RedexDefinitions.CLOSED_MAY_BE_OPEN) ||
                nodeValue.equalsIgnoreCase(RedexDefinitions.OPEN_END)) {
            FndLegalStructure newLegalStructureMapOrUpdateFromDB = getNewLegalStructureMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
            newLegalStructureMapOrUpdateFromDB.setOpenendedOrClosedendedFundStructure(nodeValue);
        }
        else {
            log.warn("Ignoring closeOpenEndTypId value as wasn't any of the expected.");
        }
        return propertiesToSaveMap;
    }

    private Map convertFromRdFinSecFundTable(Long shareClassUpdateId, String tableName, List<Column> changes, Map propertiesToSaveMap) {
        changes.forEach((Column column) -> {
            log.debug(PROCESSING_COLUMN +column.getName());
            Object oldValue = column.getOldValue();
            ConvertToHydrogenDataHelper.debugOldValue(oldValue);
            Object newValue = column.getNewValue();
            String nodeValue = ConvertToHydrogenDataHelper.getFirstNodeValueAsString((ElementImpl) newValue);
            log.debug(NODE_VALUE_NEW_VALUE + nodeValue);

            if (null != newValue) {
                MrkNavFrequency newMrkNavFrequencyMapOrUpdateFromDB =
                        getNewMrkNavFrequencyMapOrUpdateFromDB(shareClassUpdateId, propertiesToSaveMap);
                switch (column.getName()) {
                    case RedexDefinitions.RFSF_NAV_FREQ_SPAN_TYP_ID: {
                        String lookupValue= tableName+"."+column.getName()+"."+nodeValue;
                        newMrkNavFrequencyMapOrUpdateFromDB.setValuationFrequency(redexConvertionsLookupMap.get(lookupValue));
                        break;
                    }
                    case RedexDefinitions.RFSF_NAV_FREQ_NBR: {
                        newMrkNavFrequencyMapOrUpdateFromDB.setPeriodicity(nodeValue);
                        break;
                    }
                    case RedexDefinitions.RFSF_NAV_FREQ_INFO: {
                        newMrkNavFrequencyMapOrUpdateFromDB.setDescription(nodeValue);
                        break;
                    }
                    default : {
                        log.warn(FIELD_NOT_RECOGNISED);
                        break;
                    }
                }
            }
        });
        return propertiesToSaveMap;
    }

    // CHECKME is this just a data object or a business object?
    public class Change {
        private Long id;
        private Map properties;
        private Column column;
        private String value;
        private String oldValue;
        public Change( Long id, Map properties, Column column ) {
            this.id = id;
            this.properties = properties;
            this.column = column;
            this.value = ConvertToHydrogenDataHelper.getNodeValue( column );
            this.oldValue = ConvertToHydrogenDataHelper.getOldValue( column );
        }
        // CHECKME this shouldn't be used, introduced during refactoring for backwards compatibility
        public String nameAndValue() {
            return column.getName()+"."+value;
        }
        public String getValue() {
            return value;
        }
        public String getOldValue() {
            return oldValue;
        }
        public Long getId() {
            return id;
        }
        public Map getProperties() {
            return properties;
        }
        public Column getColumn() {
            return column;
        }
    }

    // Map of redex entity types and respective processing methods
    private Map<String, Consumer<Change>> repDrvrChangesMapping = Map.ofEntries(
            entry( RedexDefinitions.TRFSRD_LEG_FORM_TYP_ID, this::convertTrfsrdSubFundFormOfShare ),
            entry( RedexDefinitions.TRFSRD_ASSET_TYP_ID, this::setAssetTypId ),
            entry( RedexDefinitions.TRFSRD_CBL_FULL_NM, this::convertTrfsrdCblFullNm ),
            entry( RedexDefinitions.TRFSRD_NOM_ISO_CURR_CD, this::convertTrfsrdNomIsoCurrCd ),
            entry( RedexDefinitions.TRFSRD_PROC_PURP_TYP_ID, this::convertTrfsrdProcPurpTypId ),
            entry( RedexDefinitions.TRFSRD_TAXBLTY_TYP_ID, this::convertTrfsrdTaxbltyTypId ),
            entry( RedexDefinitions.TRFSRD_MIN_DLV_DENOM_AMNT, this::convertTrfsrdMinDlvDenomAmnt ),
            entry( RedexDefinitions.TRFSRD_MAT_DT, this::convertTrfsrdMatDt ),
            entry( RedexDefinitions.TRFSRD_ISO_TAX_CTRY_CD, this::convertTrfsrdIsoCtryCd ),
            entry( RedexDefinitions.TRFSRD_ISIN_CD, (change) -> updateIdentifier(change, "ISIN") ),
            entry( RedexDefinitions.TRFSRD_COMMOM_CD, (change) -> updateIdentifier(change, "COMC") ),
            entry( RedexDefinitions.TRFSRD_NM, this::convertTrfsrdNm ),
            entry( RedexDefinitions.TRFSRD_TECH_DEP_ID, this::convertTrfsrdIntDepCd ),
            entry( RedexDefinitions.TRFSRD_INST_ROLE_START_DT, this::convertTrfsrdInstRoleStartDt ),
            entry( RedexDefinitions.TRFSRD_INST_ROLE_END_DT, this::convertTrfsrdInstRoleEndDt ),
            entry( RedexDefinitions.TRFSRD_INST_ROLE_TYP_ID, this::convertTrfsrdInstRoleTypId ),
            entry( RedexDefinitions.TRFSRD_SHT_CD, this::convertTrfsrdShtCd ),

            entry( "ifHeaderFieldLikeCommonCode", ( change ) -> { } )//already set from headers
    );

    private void mapChanges(List<Column> changes,
                            Long shareClassUpdateId,
                            Map<String, Consumer<Change>> mapping,
                            Map propertiesToSaveMap) {
        for ( Column column : changes ) {
            if ( mapping.get( column.getName() ) != null ) {
                //mapping.get( column.getName() ).accept( shareClassUpdateId, propertiesToSaveMap, column.getName()+"."+ConvertToHydrogenDataHelper.getNodeValue( column ) );
                Change change = new Change(shareClassUpdateId, propertiesToSaveMap, column);
                mapping.get( column.getName() ).accept( change );
            } else {
                log.warn( "Unknown column '{}'", column.getName() );
            }
        }
    }

    /**
     * Method to convert values from Redex to a new or existing ShareClass(Fund) object in Hydrogen
     *
     * @return
     */
    private Map convertFromTechRdFinSecRepDrvrTable(Long shareClassUpdateId, List<Column> changes, Map<String, BaseEntity> propertiesToSaveMap) {
        mapChanges( changes, shareClassUpdateId, repDrvrChangesMapping,  propertiesToSaveMap);

        return propertiesToSaveMap;
    }

    private void convertTrfsrdInstRoleStartDt(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        FnnRole fnnRoleFromMap = entityObjectAccessService.getObjectFromMap(FnnRole.class,change.getProperties());

        LocalDate nodeValueAsLocalDate = Util.getValueAsLocalDate(change.getValue(), Util.DD_MM_YYYY_HH_MM_SS_A);
        LocalDate valueAsLocalDate = null;
        if(nodeValueAsLocalDate!=null) {
            valueAsLocalDate = Util.getValueAsLocalDate(nodeValueAsLocalDate.toString(), Util.YYYY_MM_DD);
        }
        fnnRoleFromMap.setStartingDate(valueAsLocalDate);
    }

    private void convertTrfsrdInstRoleEndDt(Change change) {
        log.debug("ColumnAndNode value: " + change.nameAndValue());
        FnnRole fnnRoleFromMap = entityObjectAccessService.getObjectFromMap(FnnRole.class, change.getProperties());

        LocalDate nodeValueAsLocalDate = Util.getValueAsLocalDate(change.getValue(), Util.DD_MM_YYYY_HH_MM_SS_A);
        LocalDate valueAsLocalDate = null;
        if (nodeValueAsLocalDate != null) {
            valueAsLocalDate = Util.getValueAsLocalDate(nodeValueAsLocalDate.toString(), Util.YYYY_MM_DD);
        }
        fnnRoleFromMap.setEndingDate(valueAsLocalDate);
    }

    private void convertTrfsrdInstRoleTypId(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        FnnRole fnnRoleFromMap = entityObjectAccessService.getObjectFromMap(FnnRole.class,change.getProperties());
        fnnRoleFromMap.setType(change.getValue());
    }

    private void convertTrfsrdShtCd(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        FnnFinancialInstitution fnnFinancialInstitutionFromMap =
                entityObjectAccessService.getObjectFromMap(FnnFinancialInstitution.class,change.getProperties());
        fnnFinancialInstitutionFromMap.setShortCode(change.getValue());
    }

    /**
     * Note: can only init propertiesToSaveMap when we know we have an item to save as
     * later when saving to the DB we check if its null or not
     *
     */
    private void convertTrfsrdCblFullNm(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());

        creShareClass.setId(change.getId());
        creShareClass.setFullName(change.getValue());
    }

    /**
     * Note: can only init propertiesToSaveMap when we know we have an item to save as
     * later when saving to the DB we check if its null or not
     *
     */
    private void convertTrfsrdNomIsoCurrCd(Change change) {
        log.debug("ColumnAndNode value: " +change.nameAndValue());
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());

        creShareClass.setCurrency(change.getValue());
    }

    private void convertTrfsrdIntDepCd(Change change) {
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());
        creShareClass.setDepoCode(change.getValue());
        updateFPGCodeString(creShareClass);
    }

    private void updateFPGCodeString (CreShareClass creShareClass){

        if ( StringUtils.isNotEmpty(creShareClass.getFundProcessingGroup())) {

            if (RedexDefinitions.FPG_DUAL.equalsIgnoreCase(creShareClass.getFundProcessingGroup())) {
                return;
            }

            if ((creShareClass.getFpgOverridden()!=null && creShareClass.getFpgOverridden()) || StringUtils.isNotEmpty(creShareClass.getOverrideReason())) {
                return;
            }
        }

        switch (creShareClass.getDepoCode()){
            case RedexDefinitions.DEPOT_NONQUALIFIED:
                creShareClass.setFundProcessingGroup(RedexDefinitions.FPG_NONQUALIFIED);
                break;

            case RedexDefinitions.DEPOT_7H:
            case RedexDefinitions.DEPOT_7T:
            case RedexDefinitions.DEPOT_MI:
            case RedexDefinitions.DEPOT_FO:
                creShareClass.setFundProcessingGroup(RedexDefinitions.FPG_VESTIMAPRIME);
                break;

            default:
                creShareClass.setFundProcessingGroup(RedexDefinitions.FPG_VESTIMA);
                break;
        }
    }

    private void convertTrfsrdNm(Change change) {
        FnnFinancialInstitution fnnFinancialInstitution =
                entityObjectAccessService.getObjectFromMap(FnnFinancialInstitution.class, change.getProperties());
        fnnFinancialInstitution.setName(change.getValue());
    }

    private void convertTrfsrdCommonCd(Map<String, BaseEntity> propertiesToSaveMap, String columnAndNodeValue) {
        convertIdentifier(propertiesToSaveMap, columnAndNodeValue, FundDefinitions.COMMON_CODE);
    }

    private void convertTrfsrdIsinCd(Map<String, BaseEntity> propertiesToSaveMap, String columnAndNodeValue) {
        convertIdentifier(propertiesToSaveMap, columnAndNodeValue, FundDefinitions.ISIN);
    }

    private void convertIdentifier(Map<String, BaseEntity> propertiesToSaveMap, String columnAndNodeValue, String identifierType) {
        CreShareClassIdentifier creShareClassIdentifier =
                entityObjectAccessService.getObjectFromMap(CreShareClassIdentifier.class, propertiesToSaveMap);
        // Save or update the Common Code type here and not overwrite the ISIN type if both exist at the same time in a
        // message, map only caters for a single class type as key
        if (creShareClassIdentifier.getType() != null) {
            createUniqueIdentifier(columnAndNodeValue, identifierType);
        } else {
            creShareClassIdentifier.setType(identifierType);
            String nodeValue = columnAndNodeValue.substring(columnAndNodeValue.indexOf(".") + 1);
            creShareClassIdentifier.setValue(nodeValue);
        }
    }

    private void createUniqueIdentifier(String columnAndNodeValue, String identifierType) {
        String nodeValue = columnAndNodeValue.substring(columnAndNodeValue.indexOf(".") + 1);
        CreShareClassIdentifier creShareClassIdentifierByTypeAndValue =
                shareClassIdentifierRepostitory.findByTypeAndValue(identifierType, nodeValue);
        // Common Code type doesn't exist already so create
        if (null == creShareClassIdentifierByTypeAndValue) {
            CreShareClassIdentifier creShareClassIdentifierTwo = new CreShareClassIdentifier();
            creShareClassIdentifierTwo.setType(identifierType);
            creShareClassIdentifierTwo.setValue(nodeValue);
            ///set the share class later once created
            creShareClassIdentifierTwo.setCreatedBy(RedexDefinitions.REDEX);
            creShareClassIdentifierTwo.setCreateTs(Instant.now());
            log.debug("Saving New Identifier directly in conversion method:  createUniqueIdentifier");
            shareClassIdentifierRepostitory.save(creShareClassIdentifierTwo);
        }
    }

    private void updateIdentifier( Change change, String type ) {
        // FIXME: we're changing identifier only if object exists
        // that's specified in message header field isNewObj
        // and that information is only available in MessageHeaders class
        // which is unavailable here
        // TODO: refactor so required information is available in Change class
        // WORKAROUND: deduce from existence of old value
        if ( StringUtils.isNotEmpty(change.getOldValue()) ) {
            // look up share class identifier by old value, apply new value
            CreShareClassIdentifier identifier = shareClassIdentifierRepostitory.findByTypeAndValue(type, change.getOldValue());
            if ( identifier == null ) {
                // CHECKME: throw exception?
                log.error("Requested change to non-existing identifier: "+type+" "+change.getOldValue());
                return;
            }
            identifier.setValue(change.getValue());
            identifier.setUpdateTs(Instant.now());
            identifier.setUpdatedBy(RedexDefinitions.REDEX);
            // now we're supposed to add this value to change properties, and they're going to be written sometimes later
            // but in the case same message specifies change to multiple entities of the same class, changes might get overwritten
            // i.e. ISIN and COMC are both of the same class
            // so we simply store the changed object
            shareClassIdentifierRepostitory.save(identifier);
        }
    }

    private void convertTrfsrdIsoCtryCd(Change change) {
        TxsTaxCharacteristics newTxsTaxCharacteristicsMapOrUpdateFromDB =
                getNewTxsTaxCharacteristicsMapOrUpdateFromDB(change.getId(), change.getProperties());

        newTxsTaxCharacteristicsMapOrUpdateFromDB.setTaxCountryDefault(change.getValue());
    }

    private void convertTrfsrdMatDt(Change change) {
        RglShareClassPriip newRglShareClassPriipMapOrUpdateFromDB = getNewRglShareClassPriipMapOrUpdateFromDB(change.getId(), change.getProperties());

        LocalDate nodeValueAsLocalDate = Util.getValueAsLocalDate(change.getValue(), Util.DD_MMM_YYYY_HH_MM_SS_SSSSSS);
        LocalDate valueAsLocalDate = null;
        if(nodeValueAsLocalDate!=null) {
            valueAsLocalDate = Util.getValueAsLocalDate(nodeValueAsLocalDate.toString(), Util.YYYY_MM_DD);
        }
        newRglShareClassPriipMapOrUpdateFromDB.setMaturityDate(valueAsLocalDate);
    }

    private void convertTrfsrdMinDlvDenomAmnt(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());

        BigDecimal nodeValueBD = Util.getValueAsBigDecimal(change.getValue());
        creShareClass.setDenominationBase(nodeValueBD);
    }

    private void convertTrfsrdTaxbltyTypId(Change change) {
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());;
        String lookupValue= RedexDefinitions.TABLE_TECH_RD_FIN_SEC_REP_DRVR + "." + change.nameAndValue();
        creShareClass.setTaxFlag(redexConvertionsLookupMap.get(lookupValue));
    }

    private void convertTrfsrdProcPurpTypId(Change change) {
        log.debug("ColumnAndNode value: "+change.nameAndValue());
        CreShareClass creShareClass = getNewShareClassMapOrUpdateFromDB(change.getId(), change.getProperties());
        String lookupValue= RedexDefinitions.TABLE_TECH_RD_FIN_SEC_REP_DRVR+"."+change.nameAndValue();
        creShareClass.setProcessPurpose(redexConvertionsLookupMap.get(lookupValue));
    }

    private void convertTrfsrdSubFundFormOfShare(Change change) {
        CreSubfund creSubFund = getNewCreSubfundMapOrUpdateFromDB(change.getId(), change.getProperties());
        String lookupValue= RedexDefinitions.TABLE_TECH_RD_FIN_SEC_REP_DRVR+"."+change.nameAndValue();
        String transformedValue=redexConvertionsLookupMap.get(lookupValue);
        creSubFund.setFormOfShare(transformedValue);
        if (RedexDefinitions.UNKNOWN_SUBFUND.equalsIgnoreCase(creSubFund.getLegalFundNameOnly())
                && StringUtils.isNotEmpty(transformedValue)) {
            creSubFund.setLegalFundNameOnly(RedexDefinitions.UNKNOWN_SUBFUND + " " + transformedValue);
        }
    }

    private void setAssetTypId(Change change) {
        CreSubfund newCreSubfundMapOrUpdateFromDB = getNewCreSubfundMapOrUpdateFromDB(change.getId(), change.getProperties());
        newCreSubfundMapOrUpdateFromDB.setFundClassification(change.getValue());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Access methods to add or retrieve objects to be saved from Object Map
    ///////////////////////////////////////////////////////////////////////////

    //TODO related code needs to be in fnnAddressToUpdate, all need to handle FinacialInstitute and Roles
    private FnnAddress getNewFnnAddressMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(FnnAddress.class.getName())==null){
                CreShareClass creShareClass = shareClassRepository.getOne(shareClassUpdateId);
                //TODO move code from save method to here
                // update
//                propertiesToSaveMap.put(FnnAddress.class.getName(), creUmbrellaFund);
            }
        }
        return entityObjectAccessService.getObjectFromMap(FnnAddress.class, propertiesToSaveMap);
    }

    private CreUmbrellaFund getNewCreUmbrellaFundMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(CreUmbrellaFund.class.getName())==null){
                CreUmbrellaFund creUmbrellaFund =umbrellaRepository.findUmbrellaFundByShareClassId(shareClassUpdateId);
                propertiesToSaveMap.put(CreUmbrellaFund.class.getName(), creUmbrellaFund);
            }
        }
        return entityObjectAccessService.getObjectFromMap(CreUmbrellaFund.class, propertiesToSaveMap);
    }

    private CreSubfund getNewCreSubfundMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(CreSubfund.class.getName())==null){
                CreSubfund creSubfund = subfundRepository.findByShareClassId(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(CreSubfund.class.getName(), creSubfund);
                return creSubfund;
            }
        }

        CreSubfund creSubfund= entityObjectAccessService.getObjectFromMap(CreSubfund.class, propertiesToSaveMap);
       if (StringUtils.isEmpty(creSubfund.getLegalFundNameOnly())) {
           creSubfund.setLegalFundNameOnly(RedexDefinitions.UNKNOWN_SUBFUND);
       }
        return creSubfund;
    }

    private TxsTaxCharacteristics getNewTxsTaxCharacteristicsMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(TxsTaxCharacteristics.class.getName())==null){
                TxsTaxCharacteristics txsTaxCharacteristics = taxCharacteristicsRepository.findByShareClassId(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(TxsTaxCharacteristics.class.getName(), txsTaxCharacteristics);
            }
        }
        return entityObjectAccessService.getObjectFromMap(TxsTaxCharacteristics.class, propertiesToSaveMap);
    }

    private MrkNavFrequency getNewMrkNavFrequencyMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(MrkNavFrequency.class.getName())==null){
                MrkNavFrequency mrkNavFrequencyByShareClassId = marketNavFrequencyRepository.findByShareClassId(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(MrkNavFrequency.class.getName(), mrkNavFrequencyByShareClassId);
            }
        }
        return entityObjectAccessService.getObjectFromMap(MrkNavFrequency.class, propertiesToSaveMap);
    }

    private RglShareClassPriip getNewRglShareClassPriipMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(RglShareClassPriip.class.getName())==null){
                RglShareClassPriip rglShareClassPriip = shareClassPriipRepository.findByShareClassId(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(RglShareClassPriip.class.getName(), rglShareClassPriip);
            }
        }
        return entityObjectAccessService.getObjectFromMap(RglShareClassPriip.class, propertiesToSaveMap);
    }

    private FndLegalStructure getNewLegalStructureMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        if(shareClassUpdateId!=null) {
            // check we don't already have Object in map from DB
            if (propertiesToSaveMap.get(FndLegalStructure.class.getName())==null){
                FndLegalStructure fndLegalStructureByShareClassId =
                        legalStructureRepository.findByShareClassId(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(FndLegalStructure.class.getName(), fndLegalStructureByShareClassId);
            }
        }
        return entityObjectAccessService.getObjectFromMap(FndLegalStructure.class, propertiesToSaveMap);
    }

    private CreShareClass getNewShareClassMapOrUpdateFromDB(Long shareClassUpdateId, Map propertiesToSaveMap) {
        CreShareClass creShareClass;
        if(shareClassUpdateId!=null) {
            // check we don't already have shareclass in map from DB
            if (propertiesToSaveMap.get(CreShareClass.class.getName())==null){
                creShareClass = shareClassRepository.getOne(shareClassUpdateId);
                // update
                propertiesToSaveMap.put(CreShareClass.class.getName(), creShareClass);
            }
        }
        return entityObjectAccessService.getShareClassFromMap(propertiesToSaveMap);
    }

    private final FnnFinancialInstitution manageMapForFinancialInstitution(final Map propertiesToSaveMap, final String pSearchTerm)
    {
        FnnFinancialInstitution fnnFinancialInstitution = (FnnFinancialInstitution)propertiesToSaveMap.get(FnnFinancialInstitution.class.getName());
        if (fnnFinancialInstitution == null){
            fnnFinancialInstitution = financialInstitutionRepository.findByShortCode(pSearchTerm);
            if (fnnFinancialInstitution != null){
                propertiesToSaveMap.put(FnnFinancialInstitution.class.getName(), fnnFinancialInstitution);
                return fnnFinancialInstitution;
            }
        }

        fnnFinancialInstitution= entityObjectAccessService.getObjectFromMap(FnnFinancialInstitution.class, propertiesToSaveMap);
        fnnFinancialInstitution.setShortCode(pSearchTerm);
        return fnnFinancialInstitution;
    }


    private final FnnAddress manageMapForFinancialInstitutionAddress(final Map propertiesToSaveMap)
    {
        FnnFinancialInstitution fnnFinancialInstitution = (FnnFinancialInstitution)propertiesToSaveMap.get(FnnFinancialInstitution.class.getName());
        FnnAddress fnnAddress = (FnnAddress)propertiesToSaveMap.get(FnnAddress.class.getName());
        if (fnnAddress == null){
            if (fnnFinancialInstitution!=null && fnnFinancialInstitution.getId()!=null) {
                List<FnnLocation> fnnLocations = locationRepository.findFnnLocationByFnnFinancialInstitution(fnnFinancialInstitution.getId());
                if (fnnLocations!=null && fnnLocations.size()>0) {
                    fnnAddress = fnnLocations.get(0).getFnnAddress();
                    propertiesToSaveMap.put(FnnAddress.class.getName(), fnnAddress);
                    return fnnAddress;
                }
            }
        }
        if (fnnAddress == null){
            fnnAddress=entityObjectAccessService.getObjectFromMap(FnnAddress.class, propertiesToSaveMap);
            if (fnnFinancialInstitution!=null) {
                Optional<FnnLocation>  fnnLocationOptional=fnnFinancialInstitution.getFnnLocations()
                                        .stream()
                                        .filter(fnnLocation ->
                                                    "LEGAL".equalsIgnoreCase(fnnLocation.getAddressType())
                                                )
                                        .findFirst();

               if (fnnLocationOptional.isPresent()){
                   fnnLocationOptional.get().setFnnAddress(fnnAddress);
                   return fnnAddress;
               }


            }
        }

        return fnnAddress;
    }
}
