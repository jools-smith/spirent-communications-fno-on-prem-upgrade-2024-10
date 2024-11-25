/*
 * COPYRIGHT (C) 2009 by Flexera Software LLC.
 * This software has been provided pursuant to a License Agreement
 * containing restrictions on its use.  This software contains
 * valuable trade secrets and proprietary information of
 * Flexera Software LLC and is protected by law.  It may
 * not be copied or distributed in any form or medium, disclosed
 * to third parties, reverse engineered or used in any manner not
 * provided for in said License Agreement except with the prior
 * written authorization from Flexera Software LLC.
 *
 * Created on Jun 08, 2004
 * Author sravuri
 *
 */

package com.flexnet.operations.services;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.hibernate.query.Query;

import com.flexnet.operations.OperationsInitializer;
import com.flexnet.operations.api.IOperationsQuery;
import com.flexnet.operations.publicapi.EntityStateENC;
import com.flexnet.operations.publicapi.FulfillmentLifeCycleStatusENC;
import com.flexnet.operations.publicapi.LicenseFileDefinition;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.server.dto.LicenseFileDefinitionDTO;
import com.flexnet.operations.web.beans.FulfillmentPropertiesBean;
import com.flexnet.operations.web.forms.product.ProductBean;
import com.flexnet.operations.web.util.CommonUtils;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.persistence.PersistenceService;
import com.flexnet.platform.util.DateUtility;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.bizobjects.LicenseTechnologyBO;
import com.flexnet.products.persistence.QueryResult;
import com.flexnet.products.publicapi.LicenseFileTypeENC;

public class FulfillmentsResultsListService extends ResultsListService {
    private DateFormat dateFormat;
    private SupportLicensesLandingConfigData configData;

    public FulfillmentsResultsListService(
            String queryStr, String countStr, ArrayList parameters, IOperationsQuery qry,
            DateFormat df, SupportLicensesLandingConfigData cData){
        super(queryStr, countStr, parameters, qry);
        dateFormat = df;
        if (cData != null) {
            configData = cData;
        }
        else {
            if (ThreadContextUtil.isLoggedInFromPortal())
                configData = OperationsInitializer.getSupportLicensesLandingConfigData();
            else
                configData = OperationsInitializer.getPublisherSupportLicensesLandingConfigData();
        }
    }

    protected List getItems(int startRow) throws OperationsException {
        try {
            if (startRow >= 0) {
                Map<Long, List<FulfillmentPropertiesBean>> orderableSetMap = new HashMap();
                Map<Long, List<FulfillmentPropertiesBean>> licFilesSetMap = new HashMap();

                QueryResult qr = getQueryResult(" RECOMPILE ");
                List l = qr.getResults(query.getBatchSize(), startRow);
                List resultsList = new ArrayList();
                Iterator iter = l.iterator();
                while (iter.hasNext()) {
                    FulfillmentPropertiesBean bean = new FulfillmentPropertiesBean();
                    Object[] array = (Object[])iter.next();
                    resultsList.add(bean);

                    int colIndex = 0;

                    Long id = (Long)array[colIndex++];
                    Long actInstId = id;
                    bean.setId(id.longValue() + "");
                    String type = (String)array[colIndex++];
                    boolean isTrustedType = false;
                    if (type.equals("bo.constants.fulfillment.type.trusted")) {
                        bean.setTrustedType(true);
                        isTrustedType = true;
                    }
                    else
                        bean.setTrustedType(false);
                    bean.setFulfillmentType(type);
                    bean.setFulfillmentSource((String)array[colIndex++]);
                    bean.setFulfillId((String)array[colIndex++]);
                    Integer count = (Integer)array[colIndex++];
                    bean.setFulfillAmount(count.intValue());
                    Integer odcount = (Integer)array[colIndex++];
                    if (odcount != null)
                        bean.setOverdraftCount(odcount.intValue());

                    Boolean isPermanent = (Boolean)array[colIndex++];
                    bean.setPermanent(isPermanent.booleanValue());

                    Timestamp tt = (Timestamp)array[colIndex++];
                    String expirationDate = "";
                    if (tt != null) {
                        bean.setFulfillmentExpirationDate((Date)tt);

                        TimeZone tz = dateFormat.getTimeZone();
                        dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                        expirationDate = dateFormat.format(tt);
                        dateFormat.setTimeZone(tz);
                    }
                    else {
                        if (isPermanent.booleanValue()) {
                            String permanentStr = CommonUtils.getResourceString(
                                    "entitlement.expiration.permanent", null,
                                    ThreadContextUtil.getLocale());
                            expirationDate = permanentStr;
                        }
                        else {
                            String unknownStr = CommonUtils.getResourceString(
                                    "entitlement.expiration.unknown", null,
                                    ThreadContextUtil.getLocale());
                            expirationDate = unknownStr;
                        }
                    }
                    bean.setExpirationDate(expirationDate);

                    String lifeCycleStatus = "master";
                    FulfillmentLifeCycleStatusENC lifecycleStatus = null;
                    if (array[colIndex] != null)
                        lifecycleStatus = FulfillmentLifeCycleStatusENC
                                .valueOf((String)array[colIndex]);

                    colIndex++;
                    if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_STOPGAP)) {
                        lifeCycleStatus = "stopgap";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_EMERGENCY)) {
                        lifeCycleStatus = "emergency";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_PUBLISHER_ERROR)) {
                        lifeCycleStatus = "publisher_error";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_REHOST)) {
                        lifeCycleStatus = "rehost";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_REPAIR)) {
                        lifeCycleStatus = "repair";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_RETURN)) {
                        lifeCycleStatus = "return";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_UPGRADE)) {
                        lifeCycleStatus = "upgrade";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_TRANSFER)) {
                        lifeCycleStatus = "transfer";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_UPSELL)) {
                        lifeCycleStatus = "upsell";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_RENEW)) {
                        lifeCycleStatus = "renew";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_REINSTALL)) {
                        lifeCycleStatus = "reinstall";
                    }
                    else if (lifecycleStatus != null
                            && lifecycleStatus
                                    .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_DELETE)) {
                        lifeCycleStatus = "delete";
                    }

                    bean.setLifeCycleStatus(lifeCycleStatus);
                    bean.setFulfillmentLifeCycleStatus(lifecycleStatus);

                    tt = (Timestamp)array[colIndex++];
                    if (tt != null) {
                        bean.setFulfillmentActivationDate((Date)tt);
                        bean.setActivationDate(dateFormat.format(tt));
                    }
                    tt = (Timestamp)array[colIndex++];
                    if (tt != null) {
                        bean.setFulfillmentLastModifiedDate((Date)tt);
                    }

                    String status = (String)array[colIndex++];
                    bean.setFulfillmentStatus(EntityStateENC.valueOf(status));
                    if (status.equals(EntityStateENC.OBSOLETE.toString())) {
                        bean.setStatus("obsolete");
                    }
                    else if (status.equals(EntityStateENC.ON_HOLD.toString())) {
                        bean.setStatus("hold");
                    }
                    else if (status.equals(EntityStateENC.ACTIVE.toString())) {
                        bean.setStatus("active");
                    }

                    bean.setShipToEmail((String)array[colIndex++]);
                    bean.setShipToAddress((String)array[colIndex++]);
                    // for start date
                    tt = (Timestamp)array[colIndex++];
                    if (tt != null) {
                        bean.setFulfillmentStartDate((Date)tt);
                        TimeZone tz = dateFormat.getTimeZone();
                        dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                        String startDate = dateFormat.format(tt);
                        bean.setStartDate(startDate);
                        dateFormat.setTimeZone(tz);
                    }
                    bean.setMigrationId((String)array[colIndex++]);
                    bean.setVendorDaemonName((String)array[colIndex++]);

                    id = (Long)array[colIndex++];
                    bean.setLineItemObjId(id + "");
                    bean.setLineItemId((String)array[colIndex++]);

                    EntityStateENC lineItemState = null;
                    if (array[colIndex] != null)
                        lineItemState = EntityStateENC.valueOf((String)array[colIndex]);
                    colIndex++;
                    bean.setLineItemState(lineItemState);

                    // seat count
                    Integer seatCount = (Integer)array[colIndex++];
                    bean.setSeatCount(seatCount.intValue() + "");

                    // fulfilled count
                    Integer usedCount = (Integer)array[colIndex++];
                    Integer remainingCount = (Integer)array[colIndex++];
                    bean.setRemainingCount(remainingCount.intValue() + "");

                    String description = (String)array[colIndex++];
                    bean.setLineItemDescription(description);

                    String orderId = (String)array[colIndex++];
                    bean.setOrderId(orderId);
                    String orderLn = (String)array[colIndex++];
                    bean.setOrderLineNum(orderLn);

                    // for version date
                    tt = (Timestamp)array[colIndex++];
                    if (tt != null) {
                        bean.setFulfillmentVersionDate((Date)tt);
                        String versionDate = dateFormat.format(tt);
                        bean.setVersionDate(versionDate);
                    }

                    // get orderable set
                    Long orderableSetId = (Long)array[colIndex++];
                    if (orderableSetId != null) {
                        List<FulfillmentPropertiesBean> beanList = orderableSetMap
                                .get(orderableSetId);
                        if (beanList == null) {
                            beanList = new LinkedList();
                        }
                        beanList.add(bean);
                        orderableSetMap.put(orderableSetId, beanList);
                    }
                    // get activation type for trusted activations
                    String activationType = (String)array[colIndex++];
                    bean.setActivationType(activationType);

                    // license files
                    if (actInstId != null) {
                        List<FulfillmentPropertiesBean> beanList = licFilesSetMap.get(actInstId);
                        if (beanList == null) {
                            beanList = new LinkedList();
                        }
                        beanList.add(bean);
                        licFilesSetMap.put(actInstId, beanList);
                    }

                    if (configData.showParentFulfillmentProperties()) {
                        Long fid = (Long)array[colIndex++];
                        if (fid != null)
                            bean.setParentFulfillmentObjId(fid.toString());
                        bean.setParentFulfillmentId((String)array[colIndex++]);
                    }

                    if (configData.showHostInfo()) {
                        String hostId = (String)array[colIndex++];
                        Boolean redundant = (Boolean)array[colIndex++];
                        String umn1 = (String)array[colIndex++];
                        String umn2 = (String)array[colIndex++];
                        String umn3 = (String)array[colIndex++];
                        String mid = (String)array[colIndex++];
                        if (redundant != null) {
                            bean.setRedundantServerLicense(redundant.booleanValue());
                        }
                        bean.setLicenseHostId(hostId);
                        bean.setLicenseHost(hostId);
                        if (isTrustedType) {
                            bean.setUmn1(umn1);
                            bean.setUmn2(umn2);
                            bean.setMachineId(mid);
                            bean.setUmn3(umn3);
                        }
                    }

                    if (configData.showSoldTo()) {
                        Long entObjId = (Long)array[colIndex++];
                        String entId = (String)array[colIndex++];
                        String soldToDisplayName = (String)array[colIndex++];
                        String soldToName = (String)array[colIndex++];
                        Long soldToId = (Long)array[colIndex++];
                        if (entObjId != null)
                            bean.setEntitlementObjId(entObjId.toString());
                        bean.setEntitlementId(entId);
                        bean.setSoldTo(soldToDisplayName);
                        bean.setSoldToName(soldToName);
                        if (soldToId != null)
                            bean.setSoldToObjId(soldToId.toString());
                    }
                    /*                	if (configData.showOrderableProperties())  {
                                    		Long ordid = (Long)array[colIndex++];
                                    		if (ordid != null)
                                    			bean.setOrderableId(ordid.toString());
                                    		bean.setOrderableName((String)array[colIndex++]);
                                    		bean.setOrderableVersion((String)array[colIndex++]);
                                    		bean.setOrderableDescription((String)array[colIndex++]);
                                    	}  */
                    if (configData.showLicenseModelProperties()) {
                        Long lmId = (Long)array[colIndex++];
                        String lmName = (String)array[colIndex++];
                        if (lmId != null)
                            bean.setLicenseModelId(lmId.toString());
                        bean.setLicenseModel(lmName);
                    }
                    if (configData.showLicenseTechnology()) {
                        Long ltId = (Long)array[colIndex++];
                        String ltName = (String)array[colIndex++];
                        if (ltId != null)
                            bean.setLicenseTechnologyId(ltId.toString());
                        bean.setLicenseTechnology(ltName);
                        // bean.setLicenseFileType((String)array[colIndex++]);
                    }

                    if (configData.showSkuProperties()) {
                        Long skuId = (Long)array[colIndex++];
                        if (skuId != null)
                            bean.setPartNumberId(skuId.toString());
                        String skuName = (String)array[colIndex++];
                        String skuDesc = (String)array[colIndex++];
                        bean.setPartNumber(skuName);
                        bean.setPartNumberDescription(skuDesc);
                    }

                    /*        			if (configData.showLicense())  {
                            				bean.setLicenseText((String)array[colIndex++]);
                            				byte[] binaryLicense = ((byte[])array[colIndex++]);
                            				if (binaryLicense != null)
                            					bean.setBinaryLicense(binaryLicense);
                            			}
                    */
                    List stringAttrs = configData.getStringAttributeList();
                    Iterator attrIter = stringAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        String val = (String)array[colIndex++];
                        bean.setCustomAttr(attrName, val);
                    }
                    List boolAttrs = configData.getBooleanAttributeList();
                    attrIter = boolAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Boolean val = (Boolean)array[colIndex++];
                        String boolVal = "";
                        if (val != null)
                            boolVal = val.toString();
                        bean.setCustomAttr(attrName, boolVal);
                    }
                    List numberAttrs = configData.getNumberAttributeList();
                    attrIter = numberAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Double val = (Double)array[colIndex++];
                        String numberVal = "";
                        if (val != null)
                            numberVal = val.toString();
                        bean.setCustomAttr(attrName, numberVal);
                    }
                    List dateAttrs = configData.getDateAttributeList();
                    attrIter = dateAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Timestamp val = (Timestamp)array[colIndex++];
                        String dateVal = "";
                        if (val != null) {
                            TimeZone tz = dateFormat.getTimeZone();
                            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                            dateVal = dateFormat.format(val);
                            dateFormat.setTimeZone(tz);
                        }
                        bean.setCustomAttr(attrName, dateVal);
                    }

                    // custom host attributes
                    List stringHostAttrs = configData.getStringHostAttributeList();
                    attrIter = stringHostAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        String val = (String)array[colIndex++];
                        bean.setCustomHostAttr(attrName, val);
                    }
                    List boolHostAttrs = configData.getBooleanHostAttributeList();
                    attrIter = boolHostAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Boolean val = (Boolean)array[colIndex++];
                        String boolVal = "";
                        if (val != null)
                            boolVal = val.toString();
                        bean.setCustomHostAttr(attrName, boolVal);
                    }
                    List numberHostAttrs = configData.getNumberHostAttributeList();
                    attrIter = numberHostAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Double val = (Double)array[colIndex++];
                        String numberVal = "";
                        if (val != null)
                            numberVal = val.toString();
                        bean.setCustomHostAttr(attrName, numberVal);
                    }
                    List dateHostAttrs = configData.getDateHostAttributeList();
                    attrIter = dateHostAttrs.iterator();
                    while (attrIter.hasNext()) {
                        String attrName = (String)attrIter.next();
                        Timestamp val = (Timestamp)array[colIndex++];
                        String dateVal = "";
                        if (val != null) {
                            TimeZone tz = dateFormat.getTimeZone();
                            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                            dateVal = dateFormat.format(val);
                            dateFormat.setTimeZone(tz);
                        }
                        bean.setCustomHostAttr(attrName, dateVal);
                    }
                }
                if (configData.showOrderableProperties() && !orderableSetMap.isEmpty()) {
                    populateOrderableProperties(orderableSetMap);
                }
                if (configData.showLicense() && !licFilesSetMap.isEmpty()) {
                    populateLicenseFiles(licFilesSetMap);
                }
                if (configData.showNodeLockedHostIds() && !licFilesSetMap.isEmpty()) {
                    populateNodeLockedHosts(licFilesSetMap);
                }
                return resultsList;
            }
            else {
                return new ArrayList();
            }
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex.getLocalizedMessage(ThreadContextUtil.getLocale()), ex);
        }
    }

    public void populateOrderableProperties(Map<Long, List<FulfillmentPropertiesBean>> beanMap)
            throws FlexnetBaseException {
    	PersistenceService ps = PersistenceService.getInstance();
        Query query = ps.getTransaction()
   			 		.getHibernateSession()
   			 		.createNativeQuery(ps.getQuery("ActivatableItemBO.getOrderablesByOrderableSet"));

        Set<Long> s = beanMap.keySet();
        Iterator<Long> iter = s.iterator();
        ArrayList orderableSetList = new ArrayList();
        while (iter.hasNext()) {
            Long id = iter.next();
            orderableSetList.add(id);
        }
        query.setParameter("tenantId", ThreadContextUtil.getTenantId());
        query.setParameterList("idList", orderableSetList);
        List rows = query.list();

        for (Object row : rows) {
        	Object[] array = (Object[])row;

        	Long ordSetId = ((BigDecimal)array[0]).longValue();
            Long ordId = ((BigDecimal)array[1]).longValue();
            String ordName = (String)array[2];
            String ordVersion = (String)array[3];
            String ordDesc = (String)array[4];
            Integer ordCount = (Integer)array[5];

            ProductBean ordBean = new ProductBean();
            ordBean.setId(ordId);
            ordBean.setName(ordName);
            ordBean.setVersion(ordVersion);
            ordBean.setDescription(ordDesc);

            List<FulfillmentPropertiesBean> beanList = beanMap.get(ordSetId);
            Iterator<FulfillmentPropertiesBean> beanIter = beanList.iterator();
            while (beanIter.hasNext()) {
                FulfillmentPropertiesBean actBean = (FulfillmentPropertiesBean)beanIter.next();
                Map<ProductBean, Integer> ordCountMap = actBean.getEntitledOrderables();
                ordCountMap.put(ordBean, ordCount);
            }
        }
    }

    public void populateLicenseFiles(Map<Long, List<FulfillmentPropertiesBean>> beanMap)
            throws FlexnetBaseException {
        String query = PersistenceService.getInstance().getQuery(
                "ActivationInstance.getLicenseFiles");
        Query hqlQuery = PersistenceService.getInstance().getTransaction().getHibernateSession()
                .createQuery(query);

        Set<Long> s = beanMap.keySet();
        Iterator<Long> iter = s.iterator();
        ArrayList licFilesSetList = new ArrayList();
        while (iter.hasNext()) {
            Long id = iter.next();
            licFilesSetList.add(id);
        }
        hqlQuery.setParameterList("idList", licFilesSetList);
        Iterator it = hqlQuery.iterate();

        while (it.hasNext()) {
            Object[] array = (Object[])it.next();

            Long actInsId = (Long)array[0];
            Long licFileDefId = (Long)array[1];
            String licFileName = (String)array[2];
            String licFileDescription = (String)array[3];
            String licFileType = (String)array[4];
            Long techId = (Long)array[5];
            String licText = (String)array[6];
            byte[] binLic = (byte[])array[7];

            LicenseFileTypeENC fileType = LicenseFileTypeENC.valueOf(licFileType);
            LicenseTechnologyBO tech = LicenseTechnologyBO.getByID(techId);
            LicenseTechnologyImpl techImpl = new LicenseTechnologyImpl(tech);
            LicenseFileDefinitionDTO dto = new LicenseFileDefinitionDTO(licFileDefId, licFileName,
                    licFileDescription, fileType, techImpl);
            List<FulfillmentPropertiesBean> beanList = beanMap.get(actInsId);
            Iterator<FulfillmentPropertiesBean> beanIter = beanList.iterator();
            while (beanIter.hasNext()) {
                FulfillmentPropertiesBean actBean = (FulfillmentPropertiesBean)beanIter.next();
                Map<LicenseFileDefinition, Object> licFiles = actBean.getLicenseFiles();
                if (LicenseFileTypeENC.TEXT.equals(fileType)) {
                    licFiles.put(dto, licText);
                }
                else {
                    licFiles.put(dto, binLic);
                }
            }

        }

    }

    public void populateNodeLockedHosts(Map<Long, List<FulfillmentPropertiesBean>> beanMap)
            throws FlexnetBaseException {
        String query = PersistenceService.getInstance().getQuery(
                "ActivationInstance.getNodeLockedHosts");
        Query hqlQuery = PersistenceService.getInstance().getTransaction().getHibernateSession()
                .createQuery(query);

        Set<Long> s = beanMap.keySet();
        Iterator<Long> iter = s.iterator();
        ArrayList licFilesSetList = new ArrayList();
        while (iter.hasNext()) {
            Long id = iter.next();
            licFilesSetList.add(id);
        }
        hqlQuery.setParameterList("idList", licFilesSetList);
        Iterator it = hqlQuery.iterate();

        while (it.hasNext()) {
            Object[] array = (Object[])it.next();

            Long actInsId = (Long)array[0];
            String nlhostId = (String)array[1];

            List<FulfillmentPropertiesBean> beanList = beanMap.get(actInsId);
            Iterator<FulfillmentPropertiesBean> beanIter = beanList.iterator();
            while (beanIter.hasNext()) {
                FulfillmentPropertiesBean actBean = (FulfillmentPropertiesBean)beanIter.next();
                List nlHosts = actBean.getNodeLockedHostIds();
                nlHosts.add(nlhostId);
            }

        }

    }

}
