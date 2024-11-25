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
 */

package com.flexnet.operations.web.actions.activations;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.flexnet.operations.api.IAttributeSet;
import com.flexnet.operations.api.IEntitlementLineItem;
import com.flexnet.operations.api.IEntitlementManager;
import com.flexnet.operations.api.IFulfillmentManager;
import com.flexnet.operations.api.IFulfillmentRecord;
import com.flexnet.operations.api.IOperationsUserManager;
import com.flexnet.operations.api.IOpsUser;
import com.flexnet.operations.api.IUtilityManager;
import com.flexnet.operations.api.IWebRegKey;
import com.flexnet.operations.bizobjects.MasterFulfillmentHelperImpl;
import com.flexnet.operations.publicapi.AttributeSet;
import com.flexnet.operations.publicapi.ConsolidatedLicenseRecord;
import com.flexnet.operations.publicapi.CustomAttributeDescriptor;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.Duration;
import com.flexnet.operations.publicapi.FulfillmentHelper;
import com.flexnet.operations.publicapi.FulfillmentRecord;
import com.flexnet.operations.publicapi.FulfillmentRequestTypeENC;
import com.flexnet.operations.publicapi.FulfillmentSourceENC;
import com.flexnet.operations.publicapi.LicenseHostId;
import com.flexnet.operations.publicapi.NodeLockedHostId;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OperationsServiceFactory;
import com.flexnet.operations.publicapi.OrganizationUnit;
import com.flexnet.operations.publicapi.PolicyDeniedException;
import com.flexnet.operations.publicapi.PolicyTypeENC;
import com.flexnet.operations.publicapi.Product;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.services.OperationsUserService;
import com.flexnet.operations.util.ExceptionUtility;
import com.flexnet.operations.web.actions.OperationsBaseAction;
import com.flexnet.operations.web.actions.product.FlexGeneratorHelper;
import com.flexnet.operations.web.beans.BatchActivationStateBean;
import com.flexnet.operations.web.beans.ConsolidatedLicensesStateBean;
import com.flexnet.operations.web.beans.SupportLicensesStateBean;
import com.flexnet.operations.web.forms.activations.BatchActivationBaseForm;
import com.flexnet.operations.web.forms.product.ProductBean;
import com.flexnet.operations.web.util.SessionUtils;
import com.flexnet.platform.bizobjects.User;
import com.flexnet.platform.config.AppConfigUtil;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.exceptions.FlexnetBaseRuntimeException;
import com.flexnet.platform.exceptions.NoDataFoundException;
import com.flexnet.platform.util.PermissionUtil;
import com.flexnet.platform.web.actions.ActionsConstants;
import com.flexnet.platform.web.utils.SecurityUtil;
import com.flexnet.products.publicapi.IPermissionENC;

public class BatchActivationBaseAction extends OperationsBaseAction {

    protected String getIDKey() {
        return "id";
    }

    protected void loadSelectedLineItemsToForm(HttpServletRequest request,
            BatchActivationBaseForm trueForm) throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, IllegalStateException,
            FlexnetBaseException, OperationsException {

        BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);

        String[] activationIds = baBean.getSelectedActivationIds();
        IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();
        FulfillmentHelper fr = fMgr.getFulfillmentHelper(activationIds,
                FulfillmentSourceENC.APPLICATION, FulfillmentRequestTypeENC.ACTIVATION);

        trueForm.setNeedCount(fr.needCount());
        trueForm.setNeedDuration(fr.needDuration());
        trueForm.setNeedNodeLockId(fr.needNodeLockId());
        trueForm.setNeedServerId(fr.needServerId());
        trueForm.setNeedCustomHost(fr.needCustomHost());
        trueForm.setNeedStartDate(fr.needStartDate());
        trueForm.setNeedVersionDate(((MasterFulfillmentHelperImpl)fr).needVersionDate());
        trueForm.setNeedVersionStartDate(((MasterFulfillmentHelperImpl)fr).needVersionStartDate());
        trueForm.setConsolidateAllowed(fr.isConsolidateAllowed());

        if (request.getSession().getAttribute(
                OperationsBaseAction.OPERATIONS_SHOW_CONSOLIDATE_FEATURE_KEY) != null) {
            String showConsolidateFeature = (String)request.getSession().getAttribute(
                    OperationsBaseAction.OPERATIONS_SHOW_CONSOLIDATE_FEATURE_KEY);
            if (showConsolidateFeature.equals("false"))
                trueForm.setConsolidateAllowed(false);
        }

        String typeStr = "";
        if (fr.needCount())
            typeStr = fr.getModelType() + "_COUNTED";
        else
            typeStr = fr.getModelType() + "_UNCOUNTED";

        trueForm.setLicenseModelType(trueForm.getResourceString(typeStr, new String[0]));
        trueForm.setNeedSoldTo(fr.needSoldTo());
        trueForm.setNeedOwner(fr.needOwner());
        baBean.setFlexnet(fr.getLicenseTechnology().isFlexNet());
        LicenseHostId serverHost = baBean.getCurrentServerHost();

        IEntitlementManager entMgr = (IEntitlementManager)OperationsServiceFactory
                .getEntitlementManager();

        IEntitlementLineItem li = null;
        for (int i = 0; i < activationIds.length; i++) {
            li = null;
            try {
                li = (IEntitlementLineItem)entMgr
                        .getEntitlementLineItemByActivationID(activationIds[i]);
            }
            catch (OperationsException ex1) {
                if (ex1.getCause() instanceof NoDataFoundException) {
                    IWebRegKey wrgKey = (IWebRegKey)entMgr
                            .getWebRegKeyByWebRegKeyID(activationIds[i]);
                    if (wrgKey != null) {
                        li = wrgKey.getEntitlementLineItem();
                    }
                }
            }
            if (li != null) {
                BatchActivationBaseForm.LineItemInfo liInfo = trueForm.newLineItemInfo();
                liInfo.setActivationId(li.getActivationID());
                if (li.getExpirationDate() != null) {
                    liInfo.setExpiry(li.getExpirationDate());
                }
                else {
                    if (li.getDuration() != null) {
                        Duration dur = li.getDuration();
                        String durationStr = dur.getLength()
                                + " "
                                + trueForm
                                        .getResourceString(dur.getDurationUnit().toString(), null);
                        liInfo.setExpirationDate(durationStr);
                    }
                    else if (li.isPermanent()) {
                        String permanentStr = trueForm.getResourceString(
                                "entitlement.expiration.permanent", null);
                        liInfo.setExpirationDate(permanentStr);
                    }
                    else {
                        String unknownStr = trueForm.getResourceString(
                                "entitlement.expiration.unknown", null);
                        liInfo.setExpirationDate(unknownStr);
                    }
                }

                Map<Product, Integer> prods = li.getEntitledProducts();
                if (prods != null && !prods.isEmpty()) {
                    Map<ProductBean, Integer> entitledProds = new HashMap<ProductBean, Integer>();
                    Iterator<Map.Entry<Product, Integer>> iter = prods.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Product, Integer> entry = iter.next();
                        Product p = entry.getKey();
                        ProductBean ordBean = new ProductBean();
                        ordBean.setId(p.getId());
                        ordBean.setName(p.getName());
                        ordBean.setVersion(p.getVersion());
                        ordBean.setDescription(p.getDescription());
                        ordBean.setType(p.getProductType());
                        entitledProds.put(ordBean, entry.getValue());
                    }
                    liInfo.setOrderables(entitledProds);
                }

                if (li.getPartNumber() != null)
                    liInfo.setSKU(li.getPartNumber().getPartNumber());

                liInfo.setAvailableExtraActivations(li.getAvailableExtraActivations() + "");

                if (serverHost != null) {
                    liInfo.setPendingCopies(baBean.getPendingCopies(li.getActivationID()) + "");
                }
                int remainingCopies = li.getNumberOfRemainingCopies();
                if (remainingCopies > 0)
                    liInfo.setRemainingCopies(remainingCopies + "");
                else
                    liInfo.setRemainingCopies("0");

                int copies = li.getTotalUnallocatedCopies();
                if (copies > 0)
                    liInfo.setRemainingUnallocatedCopies(copies + "");
                else
                    liInfo.setRemainingUnallocatedCopies("0");

                liInfo.setNeedOverdraftCount(fr.needOverdraftCount(activationIds[i]));
                if (fr.needOverdraftCount(activationIds[i]) && li.getAvailableOverdraftCount() > 0) {
                    String resourceStr = trueForm.getResourceString(
                            "createLicenseModel.when.unlimited", new String[0]);
                    if (li.getAvailableOverdraftCount() == Integer.MAX_VALUE)
                        liInfo.setRemainingOverdraft(resourceStr);
                    else
                        liInfo.setRemainingOverdraft(li.getAvailableOverdraftCount() + "");
                }
                trueForm.addLineItemInfo(liInfo);
            }
        }
    }

    public ActionForward view(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, IllegalStateException, FlexnetBaseException, OperationsException {
        try {
            BatchActivationBaseForm trueForm = (BatchActivationBaseForm)form;
            loadDataToForm(request, trueForm);
            return mapping.findForward(FORWARD_SUCCESSFUL);
        }
        catch (OperationsException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseRuntimeException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
    }

    protected void loadDataToForm(HttpServletRequest request, BatchActivationBaseForm trueForm)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {}

    public ActionForward verify(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, IllegalStateException, FlexnetBaseException, OperationsException {
        try {
            BatchActivationBaseForm trueForm = (BatchActivationBaseForm)form;
            validateDataFromForm(request, trueForm);
            loadDataFromForm(request, trueForm);
            FulfillmentHelper fr = loadDataFromBeanToFulfillmentHelper(request, trueForm);
            if (fr == null) {
                return mapping.findForward(FORWARD_UNSUCCESSFUL);
            }
            fr.verify();

            setupInfoMessage(request, "batchActivation.infoMessage.ValidLicense", null);
            return mapping.findForward(FORWARD_SUCCESSFUL);
        }
        catch (OperationsException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseRuntimeException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
    }

    protected FulfillmentHelper loadDataFromBeanToFulfillmentHelper(HttpServletRequest request,
            BatchActivationBaseForm trueForm) throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, IllegalStateException,
            FlexnetBaseException, OperationsException {

        IUtilityManager uMgr = OperationsServiceFactory.getUtilityManager();
        BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);

        String[] activationIds = baBean.getSelectedActivationIds();
        IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();
        FulfillmentHelper fr = fMgr.getFulfillmentHelper(activationIds,
                FulfillmentSourceENC.APPLICATION, FulfillmentRequestTypeENC.ACTIVATION);

        // Load the common Attributes
        if (fr.needStartDate()) {
            fr.setStartDate(baBean.getStartDate());
        }
        boolean needsVersionDate = ((MasterFulfillmentHelperImpl)fr).needVersionDate();
        boolean needsVersionStartDate = ((MasterFulfillmentHelperImpl)fr).needVersionStartDate();

        if (needsVersionDate)
            ((MasterFulfillmentHelperImpl)fr).setVersionDate(baBean.getVersionDate());
        if (needsVersionStartDate)
            ((MasterFulfillmentHelperImpl)fr).setVersionStartDate(baBean.getVersionStartDate());

        if (fr.needSoldTo()) {
            fr.setSoldTo(uMgr.getOrgUnitByID(new Long(baBean.getSoldToID())));
        }
        if (fr.isNeedFNPTimeZone()) {
            fr.setFNPTimeZone(baBean.getTimeZone());
        }
        if (fr.needOwner()) {
            String ownerId = baBean.getOwnerID();
            if (StringUtils.isNotBlank(ownerId)) {
                IOperationsUserManager userMgr = OperationsServiceFactory
                        .getOperationsUserManager();
                IOpsUser opsuser = userMgr.getUserByContactId(new Long(ownerId), true);
                User user = OperationsUserService.getUserBO(opsuser);
                if (user != null)
                    fr.setOwner(user);
            }
        }

        fr.setShipToAddress(baBean.getShipToAddress());
        fr.setShipToEmail(baBean.getShipToEmail());

        fr.setFulfillmentAttributes(copyLicenseModelAttributeValues(trueForm,
                fr.getFulfillmentAttributes(), baBean.getFulfillmentAttributesVals()));

        // Load the hosts if needed
        if (fr.needServerId() && fr.needNodeLockId()) {
            List serverhosts = null;
            if (fr.needCount()) {
                serverhosts = baBean.getConfiguredHosts();
            }
            else {
                serverhosts = baBean.getServerHostIds();
            }
            if (serverhosts != null && !serverhosts.isEmpty()) {
                Iterator iter = serverhosts.iterator();
                while (iter.hasNext()) {
                    ServerHostId server = (ServerHostId)iter.next();
                    ArrayList nodelockedHostsList = baBean.getCountedNodeLockedHostIds(server);
                    if (nodelockedHostsList != null) {
                        NodeLockedHostId[] hostIdsArr = new NodeLockedHostId[nodelockedHostsList
                                .size()];
                        fr.setCountedNodeLockedHostIds(server,
                                (NodeLockedHostId[])nodelockedHostsList.toArray(hostIdsArr));
                    }
                }
            }
            else {
                if (fr.needCount()) {
                    setupErrorMessage(request,
                            "batchActivation.error.configureCountsForAtleastOneSelectedHost",
                            new String[] {});
                }
                else {
                    setupErrorMessage(request, "batchActivation.error.selectAtleastOneHost",
                            new String[] {});
                }
                return null;
            }
        }
        else if (fr.needServerId()) {
            List serverHostsList = null;
            if (fr.needCount()) {
                serverHostsList = baBean.getConfiguredHosts();
            }
            else {
                serverHostsList = baBean.getServerHostIds();
            }
            if (serverHostsList != null && !serverHostsList.isEmpty()) {
                ServerHostId[] serverHosts = new ServerHostId[serverHostsList.size()];
                fr.setServerHostIds((ServerHostId[])serverHostsList.toArray(serverHosts));
            }
            else {
                if (fr.needCount()) {
                    setupErrorMessage(request,
                            "batchActivation.error.configureCountsForAtleastOneSelectedHost",
                            new String[] {});
                }
                else {
                    setupErrorMessage(request, "batchActivation.error.selectAtleastOneHost",
                            new String[] {});
                }
                return null;
            }
        }
        else if (fr.needNodeLockId()) {
            List nodelockedHostsList = baBean.getNodeLockedHostIds();
            if (nodelockedHostsList != null) {
                NodeLockedHostId[] hostIdsArr = new NodeLockedHostId[nodelockedHostsList.size()];
                fr.setNodeLockedHostIds((NodeLockedHostId[])nodelockedHostsList.toArray(hostIdsArr));
            }
            else {
                setupErrorMessage(request, "batchActivation.error.selectAtleastOneHost",
                        new String[] {});
                return null;
            }
        }
        else if (!baBean.isFlexnet()) {
            // load hosts for custom technology
            if (fr.needCustomHost()) {
                List customHostsList = null;
                if (fr.needCount()) {
                    customHostsList = baBean.getConfiguredHosts();
                }
                else {
                    customHostsList = baBean.getCustomHostIds();
                }
                if (customHostsList != null && !customHostsList.isEmpty()) {
                    CustomHostId[] customHosts = new CustomHostId[customHostsList.size()];
                    fr.setCustomHostIds((CustomHostId[])customHostsList.toArray(customHosts));
                }
                else {
                    if (fr.needCount()) {
                        setupErrorMessage(request,
                                "batchActivation.error.configureCountsForAtleastOneSelectedHost",
                                new String[] {});
                    }
                    else {
                        setupErrorMessage(request, "batchActivation.error.selectAtleastOneHost",
                                new String[] {});
                    }
                    return null;
                }
            }
            else { // custom technology with no host (backwards compatibility) - no host attributes
                   // defined
                if (fr.needCount()) {
                    String hoststr = "";
                    boolean configuredAtleastOneCount = false;
                    for (int i = 0; i < activationIds.length; i++) {
                        String str = baBean.getFulfillCount(activationIds[i] + "||" + hoststr);
                        if (!"".equals(str.trim())) {
                            Integer countObj = parseIntField(request, str,
                                    trueForm.getResourceString(
                                            "batchActivation.label.FulfillCount", null));
                            if (countObj != null) {
                                int count = countObj.intValue();
                                if (count > 0) {
                                    configuredAtleastOneCount = true;
                                }
                            }
                        }
                    }
                    if (!configuredAtleastOneCount) {
                        setupErrorMessage(
                                request,
                                "batchActivation.error.configureCustomTechCountsForAtleastOneLineItem",
                                new String[] {});
                        return null;
                    }
                }
            }
        }

        if (baBean.isFlexnet()) {
            // Load the counts if needed
            List countsconfiguredserverhosts = baBean.getConfiguredHosts();

            if (fr.needCount() && countsconfiguredserverhosts != null) {
                Iterator iter = countsconfiguredserverhosts.iterator();
                while (iter.hasNext()) {
                    ServerHostId serverid = (ServerHostId)iter.next();
                    String hoststr = serverid.toString();

                    for (int i = 0; i < activationIds.length; i++) {
                        String str = baBean.getFulfillCount(activationIds[i] + "||" + hoststr);
                        if (!"".equals(str.trim())) {
                            Integer countObj = parseIntField(request, str,
                                    trueForm.getResourceString(
                                            "batchActivation.label.FulfillCount", null));
                            if (countObj != null) {
                                int count = countObj.intValue();
                                if (count > 0) {
                                    fr.setCount(activationIds[i], serverid, count);
                                }
                            }
                        }
                        if (fr.needOverdraftCount(activationIds[i])) {
                            str = baBean.getRequestedOverdraftCount(activationIds[i] + "||"
                                    + hoststr);
                            if (!"".equals(str.trim())) {
                                Integer countObj = parseIntField(request, str,
                                        trueForm.getResourceString(
                                                "batchActivation.label.OverdraftCount", null));
                                if (countObj != null) {
                                    int count = countObj.intValue();
                                    if (count > 0) {
                                        fr.setRequestedOverdraftCount(activationIds[i], serverid,
                                                count);
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
        else {
            if (fr.needCustomHost()) {
                // custom technology with hosts
                List countsconfiguredserverhosts = baBean.getConfiguredHosts();

                if (fr.needCount() && countsconfiguredserverhosts != null) {
                    Iterator iter = countsconfiguredserverhosts.iterator();
                    while (iter.hasNext()) {
                        CustomHostId customHid = (CustomHostId)iter.next();
                        String hoststr = customHid.toString();

                        for (int i = 0; i < activationIds.length; i++) {
                            String str = baBean.getFulfillCount(activationIds[i] + "||" + hoststr);
                            if (!"".equals(str.trim())) {
                                Integer countObj = parseIntField(request, str,
                                        trueForm.getResourceString(
                                                "batchActivation.label.FulfillCount", null));
                                if (countObj != null) {
                                    int count = countObj.intValue();
                                    if (count > 0) {
                                        fr.setCountForCustomHost(activationIds[i], customHid, count);
                                    }
                                }
                            }
                        }
                    }
                }

            }
            else {
                if (fr.needCount()) {
                    // custom license technology, no hosts defined
                    String hoststr = "";
                    for (int i = 0; i < activationIds.length; i++) {
                        String str = baBean.getFulfillCount(activationIds[i] + "||" + hoststr);
                        if (!"".equals(str.trim())) {
                            Integer countObj = parseIntField(request, str,
                                    trueForm.getResourceString(
                                            "batchActivation.label.FulfillCount", null));
                            if (countObj != null) {
                                int count = countObj.intValue();
                                if (count > 0) {
                                    fr.setCount(activationIds[i], null, count);
                                }
                            }
                        }
                        if (fr.needOverdraftCount(activationIds[i])) {
                            str = baBean.getRequestedOverdraftCount(activationIds[i] + "||"
                                    + hoststr);
                            if (!"".equals(str.trim())) {
                                Integer countObj = parseIntField(request, str,
                                        trueForm.getResourceString(
                                                "batchActivation.label.OverdraftCount", null));
                                if (countObj != null) {
                                    int count = countObj.intValue();
                                    if (count > 0) {
                                        fr.setRequestedOverdraftCount(activationIds[i], null, count);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return fr;
    }

    public ActionForward generate(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        try {
            if (!SecurityUtil.isValidRequest(request)) {
                return processInvalidRequest(mapping, request);
            }
            BatchActivationBaseForm trueForm = (BatchActivationBaseForm)form;

            IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();

            validateDataFromForm(request, trueForm);
            loadDataFromForm(request, trueForm);

            BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);

            String overridePolicy = trueForm.getOverridePolicy();
            String policyDeniedActivationId = baBean.getPolicyDeniedActivationID();
            if (overridePolicy != null && !"".equals(overridePolicy)
                    && policyDeniedActivationId != null && !"".equals(policyDeniedActivationId)) {

                baBean.addPolicyDeniedActivationId(policyDeniedActivationId, overridePolicy);
                baBean.addPolicyDenied(policyDeniedActivationId, baBean.getPolicyDenied());
            }
            baBean.setPolicyDeniedActivationID("");
            baBean.setPolicyDenied(null);

            Map policyDeniedActivationIds = baBean.getPolicyDeniedActivationIds();
            if (policyDeniedActivationIds != null && !policyDeniedActivationIds.isEmpty()) {
                Iterator iter = policyDeniedActivationIds.keySet().iterator();
                while (iter.hasNext()) {
                    String aid = (String)iter.next();
                    String override = (String)policyDeniedActivationIds.get(aid);
                    if (!"".equals(override)) {
                        if (override.equals("false")) {
                            // baBean.removeFulfillCounts(aid);
                            // baBean.removeRequestedOverdraftCounts(aid);
                        }
                    }
                }
            }

            FulfillmentHelper fr = loadDataFromBeanToFulfillmentHelper(request, trueForm);
            if (fr == null) {
                return mapping.findForward(FORWARD_UNSUCCESSFUL);
            }

            if (policyDeniedActivationIds != null && !policyDeniedActivationIds.isEmpty()) {
                Map policyDeniedMap = baBean.getPolicyDeniedMap();
                Iterator iter = policyDeniedActivationIds.keySet().iterator();
                while (iter.hasNext()) {
                    String aid = (String)iter.next();
                    String override = (String)policyDeniedActivationIds.get(aid);
                    PolicyTypeENC policy = (PolicyTypeENC)policyDeniedMap.get(aid);
                    if (!"".equals(override)) {
                        if (override.equals("true")) {
                            fr.setOverridePolicy(aid, true);
                            fr.setOverriddenPolicy(aid, policy.toString());
                        }
                        else if (override.equals("false")) {
                            fr.removeLineItem(aid);
                            baBean.removeFulfillCounts(aid);
                            baBean.removeRequestedOverdraftCounts(aid);
                        }
                    }
                }
            }

            SupportLicensesStateBean slBean = SessionUtils.getSupportLicensesStateBean(request);
            slBean.reset();

            FulfillmentRecord[] fulfillments = fr.fulfill();
            if (fulfillments != null && fulfillments.length > 0) {
                Set fulfills = new HashSet();
                for (int x = 0; x < fulfillments.length; x++) {
                    fulfills.add(fulfillments[x].getFulfillmentId());
                }
                slBean.setFulfillments(fulfills);
            }
            slBean.setSupportLicensesContext(false);
            slBean.setLicenseLifeCycleContext(false);
            slBean.setActivationNeededHost(fr.needCustomHost() || fr.needServerId()
                    || fr.needNodeLockId());
            slBean.setActivationNeededCount(fr.needCount());

            Set fulfills = slBean.getFulfillments();
            if (fulfills.isEmpty()) {
                // This means there are no fulfillment records generated.
                // So we need to forward the request to the Landing Page.
                return mapping.findForward("ActivatableItemsLanding");
            }
            String consolidate = baBean.getConsolidateLicenses();
            boolean groupByHost = false;
            try {
                String supersedeOrSupersedeSign = AppConfigUtil
                        .getConfigStringNullAsEmpty("ops.SupersedeOrSupersedeSign");
                boolean supersedeYN = false;
                if (supersedeOrSupersedeSign.equals("SUPERSEDE_UPGRADE")
                        || supersedeOrSupersedeSign.equals("SUPERSEDE_RENEWAL")
                        || supersedeOrSupersedeSign.equals("SUPERSEDE_BOTH"))
                    supersedeYN = true;
                if (supersedeYN && baBean.isFlexnet()) {
                    groupByHost = true;
                }
            }
            catch (FlexnetBaseException ex) {
                ex.printStackTrace();
            }

            ActionForward fwd = null;
            if (groupByHost) {
                fwd = mapping.findForward("SuccessfulGroupByHost");
            }
            else if (consolidate != null && consolidate.equals("yes")) {
                slBean.getFulfillments();
                if (fulfills != null && !fulfills.isEmpty()) {
                    IFulfillmentRecord[] fulArray = new IFulfillmentRecord[fulfills.size()];
                    // TODO: instead of querying for the fulfillments again use the existing
                    // fulfills from above.
                    Iterator iter = fulfills.iterator();
                    List list = new ArrayList();
                    while (iter.hasNext()) {
                        String fulfillId = (String)iter.next();
                        IFulfillmentRecord fulRec = fMgr.getFulfillmentByFulfillmentID(fulfillId);
                        if (fulRec != null)
                            list.add(fulRec);
                    }
                    list.toArray(fulArray);
                    ConsolidatedLicenseRecord[] clRecs = fMgr.consolidate(fulArray);
                    ConsolidatedLicensesStateBean clBean = SessionUtils
                            .getConsolidatedLicensesStateBean(request);
                    clBean.resetCache();
                    List conLicList = clBean.getSelectedConsolidatedLicensesList();
                    for (int ii = 0; clRecs != null && ii < clRecs.length; ii++)
                        conLicList.add(clRecs[ii].getConsolidatedLicenseId());
                    clBean.setSelectedConsolidatedLicensesList(conLicList);
                    SessionUtils.setConsolidatedLicensesStateBean(request, clBean);
                }
                fwd = mapping.findForward("SuccessfulConsolidate");
            }
            else {
                fwd = mapping.findForward("SuccessfulFulfill");
            }
            baBean.resetCache();
            SessionUtils.setBatchActivationStateBean(request, baBean);
            SessionUtils.setSupportLicensesStateBean(request, slBean);
            return fwd;
        }
        catch (PolicyDeniedException ex) {
            rollback();
            BatchActivationBaseForm trueForm = (BatchActivationBaseForm)form;
            String activationId = ex.getActivationId();

            BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
            baBean.setPolicyDeniedActivationID(activationId);
            baBean.setPolicyDenied(ex.getPolicy());
            HttpSession session = request.getSession();
            Locale locale = (Locale)request.getSession().getAttribute(
                    org.apache.struts.Globals.LOCALE_KEY);
            Throwable cause = ex;
            String errMsg = ExceptionUtility.getCauseMessage(cause, locale);
            String[] args = new String[2];
            args[0] = activationId;
            args[1] = errMsg;
            setupErrorMessage(request, "batchActivation.message.policyDenied", args);

            boolean hasOverridePermission = false;
            if (PermissionUtil.hasPermissionAlias(IPermissionENC.OVERRIDE_POLICY.getName())) {
                hasOverridePermission = true;
            }
            if (hasOverridePermission) {
                trueForm.setShowOverridePolicyMessage(true);
            }
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (OperationsException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseRuntimeException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
    }

    public ActionForward handleNext(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        try {
            BatchActivationBaseForm trueForm = (BatchActivationBaseForm)form;
            validateDataFromForm(request, trueForm);
            loadDataFromForm(request, trueForm);
            BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
            if (baBean.isFlexnet())
                return mapping.findForward(FORWARD_SUCCESSFUL);
            else {
                if (baBean.needCustomHost())
                    return mapping.findForward(FORWARD_SUCCESSFUL);
                else
                    return mapping.findForward("SuccessfulCustom");
            }
        }
        catch (OperationsException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
        catch (FlexnetBaseRuntimeException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_UNSUCCESSFUL);
        }
    }

    protected void loadDataFromForm(HttpServletRequest request, BatchActivationBaseForm trueForm)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {}

    protected void validateDataFromForm(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {}

    protected ServerHostId getServerHostByName(HttpServletRequest request, OrganizationUnit soldTo,
            String hostname) throws OperationsException, FlexnetBaseException {

        IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();
        ServerHostId serverHost = null;
        if (hostname != null && !"".equals(hostname)) {
            serverHost = fMgr.getServerHostByUniqueName(soldTo, hostname, null);
            if (serverHost == null) {
                String[] arr = hostname.split(",");
                int len = arr.length;
                if (len == 1) {
                    serverHost = fMgr.newServerHostId(soldTo, arr[0]);
                }
                else if (len == 3) {
                    serverHost = fMgr.newServerHostId(soldTo, arr[0], arr[1], arr[2]);
                }
            }
        }
        return serverHost;
    }

    public AttributeSet copyLicenseModelAttributeValues(BatchActivationBaseForm form,
            AttributeSet target, Map valMap) throws OperationsException {
        Iterator iter = target.getDescriptors().iterator();
        while (iter.hasNext()) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)iter.next();
            Object value = valMap.get(desc.getName());
            if (desc.getType().isDateType()) {
                try {
                    value = form.toGMTDate((String)value);
                }
                catch (Exception ex) {}
            }
            IAttributeSet iTarget = (IAttributeSet)target;

            if (FlexGeneratorHelper.VM_PLATFORMS.equals(desc.getName())) {
                if (FlexGeneratorHelper.NOT_USED.equals(value)) {
                    iTarget.setValue(target.getDescriptor(FlexGeneratorHelper.VM_PLATFORMS), null);
                }
                else if (FlexGeneratorHelper.RESTRICTED_TO_PHYSICAL.equals(value)) {
                    iTarget.setValue(target.getDescriptor(FlexGeneratorHelper.VM_PLATFORMS),
                            FlexGeneratorHelper.PHYSICAL);
                }
                else if (FlexGeneratorHelper.RESTRICTED_TO_VIRTUAL.equals(value)) {
                    iTarget.setValue(target.getDescriptor(FlexGeneratorHelper.VM_PLATFORMS),
                            FlexGeneratorHelper.VM_ONLY);
                }
            }
            else if (FlexGeneratorHelper.ALLOW_TERMINAL_SERVER.equals(desc.getName())) {
                if (FlexGeneratorHelper.NOT_USED.equals(value)) {
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER),
                            "false");
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER),
                            "false");
                }
                else if (FlexGeneratorHelper.ONE_CONNECTION.equals(value)) {
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER),
                            "true");
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER),
                            "false");
                }
                else if (FlexGeneratorHelper.MANY_CONNECTIONS.equals(value)) {
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER),
                            "false");
                    iTarget.setValue(
                            target.getDescriptor(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER), "true");
                }
            }
            else if (!FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER.equals(desc.getName())) {
                iTarget.setValue(desc, value);
            }
        }
        return target;
    }

}
