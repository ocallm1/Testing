package com.clearstream.hydrogen.messagetransform.converttohydrogendata;

public class RedexDefinitions {
   public static final String SEC_REFDATA_UPDATED = "SecRefDataUpdated";
   public static final String INST_REFDATA_UPDATED = "InstRefDataUpdated";

   public static final String REDEX = "REDEX";
   public static final String DEPOSITORY = "DEPOSITORY";
   // REDEX TABLES TO MAP
   public static final String TABLE_RD_FIN_SEC_CMC_FUND = "RD_FIN_SEC_CMC_FUND";
   public static final String TABLE_RD_INST = "RD_INST";
   public static final String TABLE_TECH_RD_FIN_SEC_REP_DRVR = "TECH_RD_FIN_SEC_REP_DRVR";
   public static final String TABLE_RD_FIN_SEC_FUND = "RD_FIN_SEC_FUND";
   public static final String TABLE_RD_FIN_SEC = "RD_FIN_SEC";
   public static final String TABLE_RD_FIN_SEC_GEN_OPT = "RD_FIN_SEC_GEN_OPT";
   public static final String TABLE_RD_FIN_SEC_CMC_DETL = "RD_FIN_SEC_CMC_DETL";
   public static final String TABLE_RD_CONTACT_ADDR = "RD_CONTACT_ADDR";
   public static final String TABLE_RD_INST_ROLE_STRC_FIN_SEC = "RD_INST_ROLE_STRC_FIN_SEC";
   public static final String TABLE_RD_INST_ROLE_STRC_FIN_SEC_CMC = "RD_INST_ROLE_STRC_FIN_SEC_CMC";
   public static final String TABLE_RD_FIN_SEC_PSK_INST_ROLE = "RD_FIN_SEC_PSK_INST_ROLE";
   public static final String TABLE_RD_INST_ADDR = "RD_INST_ADDR";

   // REDEX Field definitions for table: REDEX_TABLE_TECH_RD_FIN_SEC_REP_DRVR
   public static final String TRFSRD_CBL_FULL_NM = "cblFullNm";
   public static final String TRFSRD_LEG_FORM_TYP_ID = "legFormTypId";
   public static final String TRFSRD_ASSET_TYP_ID = "assetTypId";
   public static final String TRFSRD_NOM_ISO_CURR_CD = "nomIsoCurrCd";
   public static final String TRFSRD_PROC_PURP_TYP_ID = "procPurpTypId";
   public static final String TRFSRD_TAXBLTY_TYP_ID = "taxbltyTypId";
   public static final String TRFSRD_MIN_DLV_DENOM_AMNT = "minDlvDenomAmnt";
   public static final String TRFSRD_MAT_DT = "matDt";
   public static final String TRFSRD_ISO_TAX_CTRY_CD = "isoTaxCtryCd";
   public static final String TRFSRD_ISIN_CD = "isinCd";
   public static final String TRFSRD_COMMOM_CD = "commonCd";
   public static final String TRFSRD_TECH_DEP_ID = "techDepId";
   public static final String TRFSRD_NM = "nm";
   public static final String TRFSRD_INST_ROLE_START_DT = "instRoleStartDt";
   public static final String TRFSRD_INST_ROLE_END_DT = "instRoleEndDt";
   public static final String TRFSRD_INST_ROLE_TYP_ID = "instRoleTypId";
   public static final String TRFSRD_SHT_CD = "shtCd";

   // REDEX Field definitions for table: RD_FIN_SEC_CMC_DETL
   public static final String RFSCD_LEG_FORM_TYP_ID = "legFormTypId";

   // REDEX Field definitions for table: REDEX_TABLE_RD_FIN_SEC_FUND
   public static final String RFSF_NAV_FREQ_SPAN_TYP_ID = "navFreqSpanTypId";
   public static final String RFSF_NAV_FREQ_NBR = "navFreqNbr";
   public static final String RFSF_NAV_FREQ_INFO = "navFreqInfo";

   // REDEX Field definitions for table: RD_FIN_SEC_CMC_FUND
   public static final String RFSCF_UMBR_NM = "umbrNm";

   // REDEX Field definitions for table: REDEX_TABLE_RD_INST
   public static final String RTRI_NM = "nm";

   // REDEX Field definitions for table: RD_FIN_SEC
   public static final String RFS_CLOSE_OPEN_END_TYP_ID = "closeOpenEndTypId";
   public static final String RFS_DIST_POLICY_TYP_ID = "distPolicyTypId";

   // REDEX Field definitions for table: RD_FIN_SEC_GEN_OPT
   public static final String RFSGO_EXER_FREQ_TYP_ID = "exerFreqTypId";

   // REDEX Field definitions for table: RD_INST_ADDR
   public static final String RIA_STREET_NM_AND_NBR = "streetNmAndNbr";
   public static final String ADDR_TYP_ID = "addrTypId";

   // REDEX Field definitions for tables:
   // RD_INST_ROLE_STRC_FIN_SEC, RD_FIN_SEC_PSK_INST_ROLE
   public static final String RIRSFS_INST_ROLE_TYP_ID = "instRoleTypId";
   public static final String RIRSFS_INST_SHT_CD = "instShtCd";

   ////////////////////////////////////////////////////////////////////////////
   // REDEX FIELD VALUES
   public static final String CLOSED_END = "CLOSED-END";
   public static final String CLOSED_MAY_BE_OPEN = "CLOSED_MAY_BE-OPEN";
   public static final String OPEN_END = "OPEN-END";
   public static final String ADMSTRTR = "ADMSTRTR";
   public static final String CUSTODIAN = "CUSTODIAN";
   public static final String LOCAGNT = "LOCAGNT";
   public static final String PAYAGNT = "PAYAGNT";
   public static final String PROMOTER = "PROMOTER";
   public static final String TRAGENT = "TRAGENT";
   public static final String FUNDMGR = "FUNDMGR";
   public static final String INVMGR = "INVMGR";
   public static final String ORDCONCN = "ORDCONCN";
   public static final String OTHRAGNT = "OTHRAGNT";
   public static final String REGIST = "REGIST";
   public static final String TRUSTEE = "TRUSTEE";
   public static final String SETAGNT = "SETAGNT";
   public static final String NNAONLY = "NNAONLY";
   public static final String SETTLANDCUST = "SETTLANDCUST";
   public static final String BEARER = "BEARER";
   public static final String BEARREGIS = "BEARREGIS";
   public static final String SICAV = "SICAV";
   public static final String OPEN_ENDED_INVESTMENT_COMPANY = "OPEN ENDED INVESTMENT COMPANY";
   public static final String SICAF = "SICAF";
   public static final String INVESTMENT_TRUST = "INVESTMENT TRUST";
   public static final String FCP = "FCP";
   public static final String FCP_FIS_SIF = "FCP - FIS/SIF";
   public static final String INVESTMENT_COMPANY = "INVESTMENT COMPANY";
   public static final String MUTUAL_FUND = "MUTUAL FUND";
   public static final String OTHER = "OTHER";
   public static final String PARTNERSHIP = "PARTNERSHIP";
   public static final String SICAR = "SICAR";
   public static final String SICAV_FIS_SIF = "SICAV - FIS/SIF";
   public static final String GRTHFUNDS = "GRTHFUNDS";
   public static final String SINCFUNDS = "SINCFUNDS";
   public static final String DIST = "DIST";
   public static final String CAP = "CAP";
   public static final String M = "M";
   public static final String ANNUALLY = "ANNUALLY";
   public static final String DAILY = "DAILY";
   public static final String FORTNIGHTLY = "FORTNIGHTLY";
   public static final String MONHTLY = "MONHTLY";
   public static final String QUARTERLY = "QUARTERLY";
   public static final String SEMI_ANNUALLY = "SEMI ANNUALLY";
   public static final String WEEKLY = "WEEKLY";
   public static final String MONTHLY = "MONTHLY";
   public static final String FUNDSUBFUND = "FUNDSUBFUND";
   public static final String FPG_VESTIMA = "V";
   public static final String  FPG_VESTIMAPRIME = "VP";
   public static final String  FPG_DUAL = "D";
   public static final String  FPG_NONQUALIFIED = "NQ";
   public static final String  DEPOT_NONQUALIFIED = "00";
   public static final String  DEPOT_7H = "7H";
   public static final String  DEPOT_7T = "7T";
   public static final String  DEPOT_MI = "MI";
   public static final String  DEPOT_FO = "FO";

   public static final String UNKNOWN_UMBRELLA ="Unknown Umbrella";
   public static final String UNKNOWN_SUBFUND ="Unknown Subfund";
   public static final String INVESTFND ="INVESTFND";

}

