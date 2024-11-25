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
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.flexnet.operations.api.IEntitlementLineItem;
import com.flexnet.operations.api.IEntitlementManager;
import com.flexnet.operations.api.IFulfillmentManager;
import com.flexnet.operations.api.IWebRegKey;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.LicenseHostId;
import com.flexnet.operations.publicapi.NodeLockedHostId;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OperationsServiceFactory;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.services.UtilityService;
import com.flexnet.operations.web.beans.BatchActivationStateBean;
import com.flexnet.operations.web.beans.SupportLicensesStateBean;
import com.flexnet.operations.web.forms.activations.BatchActivationBaseForm;
import com.flexnet.operations.web.forms.activations.BatchActivationConfigureHostsPageForm;
import com.flexnet.operations.web.util.SessionUtils;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.exceptions.FlexnetBaseRuntimeException;
import com.flexnet.platform.exceptions.NoDataFoundException;
import com.flexnet.platform.web.actions.ActionsConstants;
import com.flexnet.platform.web.utils.SecurityUtil;

public class BatchActivationConfigureHostsPageAction extends BatchActivationBaseAction {

    protected void loadDataToForm(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
        trueForm.reset();
        loadReadOnlyFormData(request, trueForm);
    }

    protected void loadReadOnlyFormData(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalStateException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, FlexnetBaseException, OperationsException {
        BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
        BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
        trueForm.setSoldToId(baBean.getSoldToID());
        trueForm.setOwnerId(baBean.getOwnerID());
        loadSelectedLineItemsToForm(request, trueForm);

        String[] activationIds = baBean.getSelectedActivationIds();
        IEntitlementManager entMgr = (IEntitlementManager)OperationsServiceFactory
                .getEntitlementManager();
        IEntitlementLineItem li = null;

        li = null;
        try {
            li = (IEntitlementLineItem)entMgr
                    .getEntitlementLineItemByActivationID(activationIds[0]);
            if (trueForm.getFirstLineItemID().equals(""))
                trueForm.setFirstLineItemID(li.getId().toString());
        }
        catch (OperationsException ex1) {
            if (ex1.getCause() instanceof NoDataFoundException) {
                IWebRegKey wrgKey = (IWebRegKey)entMgr.getWebRegKeyByWebRegKeyID(activationIds[0]);
                if (wrgKey != null) {
                    if (trueForm.getFirstLineItemID().equals("")) {
                        trueForm.setFirstLineItemID(wrgKey.getId().toString());
                    }
                }
            }
        }

        SupportLicensesStateBean supportBean = SessionUtils.getSupportLicensesStateBean(request);
        Set serverHosts = supportBean.getSelectedServerHosts();
        Iterator iter = serverHosts.iterator();
        while (iter.hasNext()) {
            ServerHostId server = (ServerHostId)iter.next();
            baBean.addServerHostId(server);
        }

        List serverList = baBean.getServerHostIds();
        trueForm.setServerLicenseHosts(new HashSet(serverList));

        Set nlHosts = supportBean.getSelectedNodeLockedHosts();
        iter = nlHosts.iterator();
        while (iter.hasNext()) {
            NodeLockedHostId nlHost = (NodeLockedHostId)iter.next();
            baBean.addNodeLockedHostId(nlHost);
        }
        List nlList = baBean.getNodeLockedHostIds();
        trueForm.setNlLicenseHosts(new HashSet(nlList));

        Set customHosts = supportBean.getSelectedCustomHosts();
        iter = customHosts.iterator();
        while (iter.hasNext()) {
            CustomHostId custom = (CustomHostId)iter.next();
            baBean.addCustomHostId(custom);
        }
        List customList = baBean.getCustomHostIds();
        trueForm.setCustomLicenseHosts(new HashSet(customList));

        supportBean.resetSelectedHosts();
        SessionUtils.setBatchActivationStateBean(request, baBean);
        SessionUtils.setSupportLicensesStateBean(request, supportBean);
    }

    public ActionForward selectExistingHost(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws IllegalStateException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        try {

            IFulfillmentManager fmtMgr = (IFulfillmentManager)OperationsServiceFactory
                    .getFulfillmentManager();
            BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
            loadReadOnlyFormData(request, trueForm);
            if (!trueForm.getSelectedHostId().equals("")) {
                BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
                LicenseHostId licHost = fmtMgr.getLicenseHostByID(new Long(trueForm
                        .getSelectedHostId()));
                if (licHost instanceof ServerHostId) {
                    trueForm.addServerLicenseHosts(licHost);
                    baBean.addServerHostId((ServerHostId)licHost);
                }
                else if (licHost instanceof NodeLockedHostId) {
                    trueForm.addNlLicenseHost((NodeLockedHostId)licHost);
                    baBean.addNodeLockedHostId((NodeLockedHostId)licHost);
                }
                else {
                    trueForm.addCustomLicenseHost((CustomHostId)licHost);
                    baBean.addCustomHostId((CustomHostId)licHost);
                }
                trueForm.setSelectedHostId("");
                trueForm.setSelectedHostName("");
                SessionUtils.setBatchActivationStateBean(request, baBean);
            }
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

    public ActionForward createHost(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws IllegalStateException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        try {
            if (!SecurityUtil.isValidRequest(request)) {
                return processInvalidRequest(mapping, request);
            }
            BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
            loadReadOnlyFormData(request, trueForm);
            BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
            LicenseHostId licHost = (LicenseHostId)request.getSession().getAttribute(
                    SessionUtils.CREATED_LICENSE_HOST);
            if (licHost instanceof ServerHostId) {
                trueForm.addServerLicenseHosts(licHost);
                baBean.addServerHostId((ServerHostId)licHost);
            }
            else if (licHost instanceof NodeLockedHostId) {
                trueForm.addNlLicenseHost((NodeLockedHostId)licHost);
                baBean.addNodeLockedHostId((NodeLockedHostId)licHost);
            }
            else {
                trueForm.addCustomLicenseHost((CustomHostId)licHost);
                baBean.addCustomHostId((CustomHostId)licHost);
            }
            SessionUtils.setBatchActivationStateBean(request, baBean);
            request.getSession().removeAttribute(SessionUtils.CREATED_LICENSE_HOST);
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

    public ActionForward deleteSelectedHost(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws IllegalStateException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        try {
            BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
            BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
            loadReadOnlyFormData(request, trueForm);
            String[] delSelected = trueForm.getToDeleteServerHosts();
            if (delSelected != null && delSelected.length > 0) {
                for (int ii = 0; ii < delSelected.length; ii++) {
                    Iterator iter2 = trueForm.getServerLicenseHosts().iterator();
                    while (iter2.hasNext()) {
                        LicenseHostId host = (LicenseHostId)iter2.next();
                        if (host.getUniqueId().toString().equals(delSelected[ii])) {
                            iter2.remove();
                            baBean.removeServerHostId((ServerHostId)host);
                        }
                    }
                }
            }
            delSelected = trueForm.getToDeleteNlHosts();
            if (delSelected != null && delSelected.length > 0) {
                for (int ii = 0; ii < delSelected.length; ii++) {
                    Iterator iter2 = trueForm.getNlLicenseHosts().iterator();
                    while (iter2.hasNext()) {
                        LicenseHostId host = (LicenseHostId)iter2.next();
                        if (host.getUniqueId().toString().equals(delSelected[ii])) {
                            iter2.remove();
                            baBean.removeNodeLockedHostId((NodeLockedHostId)host);
                        }
                    }
                }
            }
            delSelected = trueForm.getToDeleteCustomHosts();
            if (delSelected != null && delSelected.length > 0) {
                for (int ii = 0; ii < delSelected.length; ii++) {
                    Iterator iter2 = trueForm.getCustomLicenseHosts().iterator();
                    while (iter2.hasNext()) {
                        LicenseHostId host = (LicenseHostId)iter2.next();
                        if (host.getUniqueId().toString().equals(delSelected[ii])) {
                            iter2.remove();
                            baBean.removeCustomHostId((CustomHostId)host);
                        }
                    }
                }
            }

            SessionUtils.setBatchActivationStateBean(request, baBean);
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

    public ActionForward refresh(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        try {
            BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
            loadReadOnlyFormData(request, trueForm);
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

    protected void loadDataFromForm(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        BatchActivationConfigureHostsPageForm trueForm = (BatchActivationConfigureHostsPageForm)form;
        populateDataFromForm(request, trueForm);
    }

    protected void validateDataFromForm(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        validateAndPopulateDataFromForm(request, form, true, false);
    }

    protected void populateDataFromForm(HttpServletRequest request, BatchActivationBaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        validateAndPopulateDataFromForm(request, form, false, true);
    }

    protected void validateAndPopulateDataFromForm(HttpServletRequest request,
            BatchActivationBaseForm form, boolean bValidate, boolean bPopulate)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException, OperationsException {
        BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
        boolean needServerId = baBean.needServerId();
        boolean needNodeLockId = baBean.needNodeLockId();
        boolean needCustomHost = baBean.needCustomHost();
        boolean needCount = baBean.needCount();

        if (needServerId) {
            List serverHosts = baBean.getServerHostIds();
            if (bValidate) {
                if (serverHosts.size() == 0)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.serverHostsRequired");

                if (!baBean.isAllowPartialFulfillments() && serverHosts.size() > 1)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.multipleHostsNotAllowed");
            }

            if (bPopulate)
                refreshTheCountsCacheInTheBean(request);
        }
        else if (needNodeLockId) {
            List nlHosts = baBean.getNodeLockedHostIds();

            if (bValidate) {
                if (nlHosts.size() == 0)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.nodelockedHostsRequired");

                if (!baBean.isAllowPartialFulfillments() && nlHosts.size() > 1)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.multipleHostsNotAllowed");

            }
        }
        else if (needCustomHost) {
            List customHosts = baBean.getCustomHostIds();
            if (bValidate) {
                if (customHosts.size() == 0)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.serverHostsRequired");

                if (!baBean.isAllowPartialFulfillments() && customHosts.size() > 1)
                    throw UtilityService
                            .makeOperationsException("batchActivation.ConfigureHosts.errorMessage.multipleHostsNotAllowed");

            }
            if (bPopulate && needCount)
                refreshTheCountsCacheInTheBean(request);
        }
    }

    private void refreshTheCountsCacheInTheBean(HttpServletRequest request) {
        BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
        List serverhosts = null;
        if (baBean.isFlexnet()) {
            serverhosts = baBean.getServerHostIds();
        }
        else {
            serverhosts = baBean.getCustomHostIds();
        }

        // adjust the counts configured hosts
        List countsConfiguredhosts = baBean.getConfiguredHosts();
        if (!countsConfiguredhosts.isEmpty()) {
            List newCountsConfiguredhosts = new ArrayList();
            List removedHosts = new ArrayList();
            Iterator iter = countsConfiguredhosts.iterator();
            while (iter.hasNext()) {
                LicenseHostId server = (LicenseHostId)iter.next();
                if (serverhosts.contains(server)) {
                    newCountsConfiguredhosts.add(server);
                }
                else {
                    removedHosts.add(server);
                }
            }
            baBean.setConfiguredHosts(newCountsConfiguredhosts);
            if (!removedHosts.isEmpty()) {
                String[] activationIds = baBean.getSelectedActivationIds();
                Iterator iter1 = removedHosts.iterator();
                while (iter1.hasNext()) {
                    LicenseHostId removedserver = (LicenseHostId)iter1.next();
                    String hoststr = removedserver.toString();
                    for (int i = 0; i < activationIds.length; i++) {
                        baBean.removeFulfillCount(activationIds[i] + "||" + hoststr);
                        baBean.removeRequestedOverdraftCount(activationIds[i] + "||" + hoststr);
                    }
                }
            }
        }
        // adjust the nodelockedCountedHosts cache
        Map countedNodeLockedHostIds = baBean.getCountedNodeLockedHostIds();
        if (!countedNodeLockedHostIds.isEmpty()) {
            Map newMap = new HashMap();
            Set keys = countedNodeLockedHostIds.keySet();
            Iterator keysiter = keys.iterator();
            while (keysiter.hasNext()) {
                ServerHostId server = (ServerHostId)keysiter.next();
                if (serverhosts.contains(server)) {
                    newMap.put(server, countedNodeLockedHostIds.get(server));
                }
            }
            baBean.setCountedNodeLockedHostIds(newMap);
        }
        baBean.setCurrentServerHost(null);
        baBean.setPolicyDeniedActivationIds(null);

        SessionUtils.setBatchActivationStateBean(request, baBean);
    }

    /*
    private OrganizationUnit getSoldTo(FulfillmentHelper fr, HttpServletRequest request) throws FlexnetBaseException, OperationsException
    {
    	
    	IUtilityManager uMgr = mgr.getUtilityManager();
    	BatchActivationStateBean baBean = SessionUtils.getBatchActivationStateBean(request);
    	
    	if (fr.needSoldTo()) {
    		return uMgr.getOrgUnitByID(new Long(baBean.getSoldToID()));
    	}
    	else {
    		return fr.getSoldTo();
    	}
    }
    */

}
