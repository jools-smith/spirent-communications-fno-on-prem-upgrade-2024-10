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
 * Created on Jul 8th, 2005
 * Author smajji
 *
 */

package com.flexnet.operations.bizobjects;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flexnet.operations.api.IAttributeSet;
import com.flexnet.operations.bizobjects.entitlements.ActivatableItemBO;
import com.flexnet.operations.bizobjects.entitlements.BulkEntitlementBO;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.bizobjects.entitlements.WebRegKeyBO;
import com.flexnet.operations.exceptions.OPSBaseException;
import com.flexnet.operations.exceptions.activations.ActivationException;
import com.flexnet.operations.publicapi.AttributeWhenENC;
import com.flexnet.operations.publicapi.ConsolidatedLicenseRecord;
import com.flexnet.operations.publicapi.CustomAttributeDescriptor;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.Duration;
import com.flexnet.operations.publicapi.EntitlementLineItem;
import com.flexnet.operations.publicapi.EntityStateENC;
import com.flexnet.operations.publicapi.FulfillmentRecord;
import com.flexnet.operations.publicapi.FulfillmentRequestTypeENC;
import com.flexnet.operations.publicapi.FulfillmentSourceENC;
import com.flexnet.operations.publicapi.HostType;
import com.flexnet.operations.publicapi.LicenseHostId;
import com.flexnet.operations.publicapi.LicenseHostIdPolicy;
import com.flexnet.operations.publicapi.LicenseRedundantServerPolicy;
import com.flexnet.operations.publicapi.NodeLockedHostId;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OrganizationUnit;
import com.flexnet.operations.publicapi.PolicyDeniedException;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.publicapi.StartDateOptionsENC;
import com.flexnet.operations.publicapi.VersionDateOptionsENC;
import com.flexnet.operations.services.FulfillmentService;
import com.flexnet.operations.services.LicenseAttributeService;
import com.flexnet.operations.services.LicenseModelAttributeImpl;
import com.flexnet.operations.services.LicenseModelImpl;
import com.flexnet.operations.services.LicenseTechnologyImpl;
import com.flexnet.operations.services.UtilityService;
import com.flexnet.platform.bizobjects.OrgUnit;
import com.flexnet.platform.bizobjects.OrgUnitType;
import com.flexnet.platform.bizobjects.User;
import com.flexnet.platform.config.AppConfigUtil;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.logging.LogMessage;
import com.flexnet.platform.services.logging.Logger;
import com.flexnet.platform.services.logging.LoggingService;
import com.flexnet.platform.util.PermissionUtil;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.bizobjects.LicenseModelBO;
import com.flexnet.products.exceptions.RequiredModelPropertyMissingException;
import com.flexnet.products.publicapi.IPermissionENC;
import com.flexnet.products.util.LicenseTechnologyTypeENC;

/**
 * Class to manage a fulfillment request. Given a specified product to activate, this class provides
 * methods to:
 * <ol>
 * <li>query what parameters are required to perform an activation</li>
 * <li>set values for those parameters</li>
 * <li>validate the set of parameters defined; this includes validating that the activation is
 * allowed by the Entitlement</li>
 * <li>invoke the underlying license generator with the defined set of parameters and get a status
 * result (successful or not)</li>
 * <li>commit the activation as a fulfillment and obtain an ActivationInstance</li>
 * </ol>
 * A single FulfillmentRequest instance is valid only for a single request. Use of a specific
 * instance must generally flow in the order shown above: query, set, validate, invoke, commit. The
 * query and validate steps are optional (validate will be called automatically before license
 * generation if it was not explicitly called.)
 * 
 * @author csteres
 * @author smajji
 * @author sravuri
 */
public class MultipleLineItemFulfillmentHelperImpl extends MasterFulfillmentHelperImpl {
    protected static Logger logger = LoggingService.getLogger("flexnet.ops.fulfillmentrequest");
    Date startDate;
    Date versionDate;
    Date versionStartDate;
    private List lineItems;
    private Map lineItemsHash = new HashMap(); // key=activationId, value=lineItem
    private Map policyOverrides = new HashMap(); // key = activationId value=boolean
    private Map policyNameOverrides = new HashMap(); // key = activationId name of policy overridden
    private List serverHostIds = new LinkedList();
    private Map countedNodeLockedHosts = new HashMap(); // serverHostId, nockLockedHost[]
    private List nodeLockedHostIds = new LinkedList();
    private Map fulfillCounts = new HashMap(); // key = activationId value = map (serverhostid,
                                               // count)
    private Map requestedOverdraftCounts = new HashMap(); // key = activationId value = map
                                                          // (serverhostid, count)
    private HashSet shipToEmails = new HashSet();
    private HashSet shipToAddresses = new HashSet();
    protected boolean isWebRegKey = false;
    private boolean isAllowPartialFulfillments;
    private List customHostIds = new LinkedList();
    Set<LicenseModelImpl> licModels = new HashSet();

    public MultipleLineItemFulfillmentHelperImpl(
            String[] activationIds, FulfillmentSourceENC inputSource, FulfillmentRequestTypeENC type)
            throws OperationsException{
        try {
            if (activationIds == null || activationIds.length == 0) {
                throw new OPSBaseException("activationIdCannotBeNull");
            }
            if (type == null) {
                throw new OPSBaseException("requestTypeCannotBeNull");
            }
            if ((!type.equals(FulfillmentRequestTypeENC.ACTIVATION) && (!requestType
                    .equals(FulfillmentRequestTypeENC.SHORT_CODE_ACTIVATION)))) {
                throw new OPSBaseException(new Object[] { type },
                        "requestTypeShouldBeActivationType");
            }

            this.lineItems = new LinkedList();
            LicenseModelBO[] licenseModels = new LicenseModelBO[activationIds.length];
            Set<LicenseModelBO> licModelSet = new HashSet();
            IAttributeSet attrs = null;
            LicenseHostIdPolicy serverHostIdPolicy, firstServerHostIdPolicy = null, nlHostIdPolicy, firstNlHostIdPolicy = null;
            LicenseRedundantServerPolicy firstRedundantPolicy = null, redundantPolicy;
            Boolean firstAllowPartial = null, allowPartial;
            WebRegKeyBO wBO = null;
            BulkEntitlementBO be = null;
            boolean needsServer = false, needsNodeLockedHosts = false;
            LicenseHostId[] firstHosts = null, hosts = null;
            boolean hasOverridePolicyPermission = PermissionUtil
                    .hasPermissionAlias(IPermissionENC.OVERRIDE_POLICY.getName());

            for (int i = 0; i < activationIds.length; ++i) {
                String activationId = activationIds[i];
                ActivatableItemBO actBO = ActivatableItemBO.getActivatableItemByActivationID(
                        ActivatableItemBO.class, activationId);
                EntitlementLineItemBO lineitem = null;
                if (actBO instanceof WebRegKeyBO) {
                    isWebRegKey = true;
                    wBO = (WebRegKeyBO)actBO;
                    be = (BulkEntitlementBO)wBO.getParentEntitlement();
                    lineitem = (EntitlementLineItemBO)wBO.getEntitlementLineItem();
                }
                else if (actBO instanceof EntitlementLineItemBO) {
                    lineitem = (EntitlementLineItemBO)actBO;
                }
                if (lineitem.getLineItemState().equals(EntityStateENC.TEST)) {
                    lineitem.checkTestLineItem();
                }
                lineItems.add(lineitem);
                lineItemsHash.put(activationId, lineitem);
                LicenseModelImpl impl = (LicenseModelImpl)lineitem
                        .getLicenseModelByType(EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);

                if (impl == null) {
                    throw new OPSBaseException(new Object[] {},
                            "onlyCertificateOrCustomChildItemsCanBeActivated");
                }

                LicenseModelBO mdl = impl.getLicenseModel();
                licenseModels[i] = mdl;
                licModelSet.add(mdl);
                licModels.add(impl);
                if (licenseTechnology == null) {
                    licenseTechnology = mdl.getLicenseTechnology();
                }
                else if (!licenseTechnology.equals(mdl.getLicenseTechnology())) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameTechnology");
                }
                if (hostType == null) {
                    hostType = lineitem.getHostType();
                }
                else if (!hostType.equals(lineitem.getHostType())) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameHostType");
                }

                if (modelType == null) {
                    modelType = mdl.getModelType();
                    countedModel = mdl.isCounted_();
                }
                else if (!modelType.equals(mdl.getModelType())) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameModelType");
                }
                else if (countedModel != mdl.isCounted_()) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameCountedUncountedModel");
                }

                if (modelType.equals(LicenseModelBO.MODEL_FLOATING)) {
                    needsServer = true;
                }
                else {
                    needsNodeLockedHosts = true;
                    if (countedModel)
                        needsServer = true;
                }

                if (soldTo == null) {
                    soldTo = lineitem.getParentEntitlement().getSoldTo();
                }
                else if (!soldTo.equals(lineitem.getParentEntitlement().getSoldTo())) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameSoldToOrg");
                }

                if (soldTo == null) {
                    soldTo = lineitem.getParentEntitlement().getSoldTo();
                }
                else if (!soldTo.equals(lineitem.getParentEntitlement().getSoldTo())) {
                    throw new OPSBaseException(new Object[] {}, "shouldBeSameSoldToOrg");
                }

                if (isWebRegKey)
                    allowPartial = new Boolean(impl.isAllowPartialFulfillments());
                else
                    allowPartial = new Boolean(impl.isAllowPartialFulfillments());

                if (firstAllowPartial == null) {
                    firstAllowPartial = allowPartial;
                    isAllowPartialFulfillments = allowPartial.booleanValue();
                }
                else if (!allowPartial.equals(firstAllowPartial)) {
                    throw new OPSBaseException(new Object[] {},
                            "shouldBeSamePartialFulfillmentsPolicy");
                }

                // id does not have override policy permission, then we have to make sure that all
                // policies are the same
                if (!hasOverridePolicyPermission) {
                    if (needsServer) {
                        if (isWebRegKey) {
                            if (ThreadContextUtil.isLoggedInFromPortal())
                                serverHostIdPolicy = be.getServerHostIdPolicyForPortal();
                            else
                                serverHostIdPolicy = be.getServerHostIdPolicy();
                            redundantPolicy = be.getRedundantServerPolicy();
                            hosts = null;
                        }
                        else {
                            if (ThreadContextUtil.isLoggedInFromPortal())
                                serverHostIdPolicy = lineitem.getServerHostIdPolicyForPortal();
                            else
                                serverHostIdPolicy = lineitem.getServerHostIdPolicy();
                            redundantPolicy = lineitem.getRedundantServerPolicy();
                            hosts = lineitem.getHostIds();
                        }

                        if (firstServerHostIdPolicy == null)
                            firstServerHostIdPolicy = serverHostIdPolicy;
                        else {
                            if (!firstServerHostIdPolicy.equals(serverHostIdPolicy))
                                throw new OPSBaseException(new Object[] {},
                                        "shouldBeSameServerHostIdPolicy");
                        }

                        if (firstRedundantPolicy == null)
                            firstRedundantPolicy = redundantPolicy;
                        else if (!firstRedundantPolicy.equals(redundantPolicy)) {
                            throw new OPSBaseException(new Object[] {},
                                    "shouldBeSameRedundantServerPolicy");
                        }

                        if (firstHosts == null)
                            firstHosts = hosts;
                        else {
                            if ((firstHosts == null && hosts != null)
                                    || (firstHosts != null && hosts == null))
                                throw new OPSBaseException(new Object[] {},
                                        "shouldBeSameEntitlementHosts");

                            if (hosts != null && firstHosts != null) {
                                List hostList = Arrays.asList(hosts);
                                List firstList = Arrays.asList(firstHosts);
                                if (!hostList.equals(firstList))
                                    throw new OPSBaseException(new Object[] {},
                                            "shouldBeSameEntitlementHosts");
                            }
                        }
                    }

                    if (needsNodeLockedHosts) {
                        if (isWebRegKey) {
                            if (ThreadContextUtil.isLoggedInFromPortal())
                                nlHostIdPolicy = be.getNodeLockedHostIdPolicyForPortal();
                            else
                                nlHostIdPolicy = be.getServerHostIdPolicy();
                        }
                        else {
                            if (ThreadContextUtil.isLoggedInFromPortal())
                                nlHostIdPolicy = lineitem.getNodeLockedHostIdPolicyForPortal();
                            else
                                nlHostIdPolicy = lineitem.getNodeLockedHostIdPolicy();
                        }

                        if (firstNlHostIdPolicy == null)
                            firstNlHostIdPolicy = nlHostIdPolicy;
                        else if (!firstNlHostIdPolicy.equals(nlHostIdPolicy)) {
                            throw new OPSBaseException(new Object[] {},
                                    "shouldBeSameNlHostIdPolicy");
                        }
                    }
                }

                if (lineitem.getParentEntitlement().getShipToEmail() != null
                        && lineitem.getParentEntitlement().getShipToEmail().length() != 0) {
                    shipToEmails.add(lineitem.getParentEntitlement().getShipToEmail());
                }
                if (lineitem.getParentEntitlement().getShipToAddress() != null
                        && lineitem.getParentEntitlement().getShipToAddress().length() != 0) {
                    shipToAddresses.add(lineitem.getParentEntitlement().getShipToAddress());
                }
                if (!isNeedFNPTimeZone()) {
                    if (impl.getTimeZoneWhen() != null
                            && impl.getTimeZoneWhen().equals(AttributeWhenENC.FULFILLMENT_TIME)) {
                        setNeedFNPTimeZone(true);
                    }
                }
            }

            Iterator iter = shipToEmails.iterator();
            while (iter.hasNext()) {
                if (shipToEmail != null) {
                    shipToEmail = shipToEmail + "," + (String)iter.next();
                }
                else {
                    shipToEmail = (String)iter.next();
                }
            }

            Iterator iter1 = shipToAddresses.iterator();
            while (iter1.hasNext()) {
                if (shipToAddress != null) {
                    shipToAddress = shipToAddress + "," + (String)iter1.next();
                }
                else {
                    shipToAddress = (String)iter1.next();
                }
            }

            attrs = LicenseAttributeService.getAttributes(licenseModels,
                    LicenseAttributeService.WHEN_FULFILLMENT);
            initialize(attrs, shipToEmail, shipToAddress, inputSource, type, licModelSet);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public boolean isAllowPartialFulfillments() {
        return isAllowPartialFulfillments;
    }

    public void addNodeLockedHostId(NodeLockedHostId nodeLockId) throws OperationsException {
        try {
            if (!needNodeLockId()) {
                throw new ActivationException(new Object[] { nodeLockId.toString() },
                        "nodeLockIdNotAllowed");
            }
            nodeLockedHostIds.add(nodeLockId);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void addServerId(ServerHostId serverId) throws OperationsException {
        try {
            if (!needServerId()) {
                throw new ActivationException(new Object[] { serverId.toString() },
                        "serverIdNotAllowed");
            }
            serverHostIds.add(serverId);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void removeCountedNodeLockedHostIds(ServerHostId serverId) throws OperationsException {
        if (needCountedNodeLockId()) {
            if (serverId != null && countedNodeLockedHosts.containsKey(serverId)) {
                countedNodeLockedHosts.remove(serverId);
            }
        }
    }

    public void removeNodeLockedHostId(NodeLockedHostId nodeLockId) throws OperationsException {
        if (needNodeLockId()) {
            if (nodeLockId != null && nodeLockedHostIds.contains(nodeLockId)) {
                nodeLockedHostIds.remove(nodeLockId);
            }
        }
    }

    public void removeServerId(ServerHostId serverId) throws OperationsException {
        if (needServerId()) {
            if (serverId != null && serverHostIds.contains(serverId)) {
                serverHostIds.remove(serverId);
            }
        }
    }

    public int getCount(String activationId, ServerHostId serverId) throws OperationsException {
        if (fulfillCounts.get(activationId) != null) {
            Map t = (Map)fulfillCounts.get(activationId);
            if (t.get(serverId) != null) {
                Number count = (Number)t.get(serverId);
                return count.intValue();
            }
            else {
                return 0;
            }
        }
        return 0;
    }

    public Duration getDuration() {
        return null;
    }

    public FulfillmentRecord getFulfillmentRecord() {
        return null;
    }

    public int getRequestedOverdraftCount(String activationId, ServerHostId serverId) {
        if (requestedOverdraftCounts.get(activationId) != null) {
            Map t = (Map)requestedOverdraftCounts.get(activationId);
            if (t.get(serverId) != null) {
                Number count = (Number)t.get(serverId);
                return count.intValue();
            }
            else {
                return 0;
            }
        }
        return 0;
    }

    public boolean needCount() {
        return modelRequiresCount();
    }

    public boolean needDuration() {
        return false;
    }

    public boolean needNodeLockId() {
        return modelType.equals(LicenseModelBO.MODEL_NODE_LOCKED);
    }

    public boolean needOverdraftCount(String activationId) throws OperationsException {
        EntitlementLineItemBO bo = (EntitlementLineItemBO)lineItemsHash.get(activationId);
        if (bo == null)
            return false;
        LicenseModelImpl mdlImpl = (LicenseModelImpl)bo
                .getLicenseModelByType(EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        return mdlImpl.supportsOverdraft() && bo.getAvailableOverdraftCount() > 0;
    }

    public boolean needServerId() {
        return isCountedCertificate() || isFloatingCertificate();
    }

    public boolean needStartDate() {
        Iterator iter = lineItems.iterator();
        while (iter.hasNext()) {
            EntitlementLineItemBO bo = (EntitlementLineItemBO)iter.next();
            if (bo.getStartDate() == null) {
                StartDateOptionsENC startDateOpt = bo.getStartDateOption();
                if (startDateOpt != null) {
                    if (startDateOpt
                            .equals(StartDateOptionsENC.DEFINE_AS_ACTIVATION_DATE_AT_FIRST_ACTIVATION)
                            || startDateOpt
                                    .equals(StartDateOptionsENC.DEFINE_AS_ACTIVATION_DATE_AT_EACH_ACTIVATION)) {
                        continue;
                    }
                    else {
                        return true;
                    }
                }
                else {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean needFulfillmentAttributes() {
        // TODO Auto-generated method stub
        return true;
    }

    public void removeLineItem(String activationId) throws OperationsException {
        if (lineItemsHash.get(activationId) != null) {
            lineItems.remove(lineItemsHash.get(activationId));
            lineItemsHash.remove(activationId);
        }
    }

    public void setCount(String activationId, ServerHostId serverId, int count)
            throws OperationsException {
        try {
            if (!needCount()) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "countNotAllowed");
            }
            if (count <= 0) {
                throw new ActivationException(new Object[] { new Integer(count) }, "invalidCount");
            }
            Map t = null;
            if (fulfillCounts.get(activationId) != null) {
                t = (Map)fulfillCounts.get(activationId);
            }
            else {
                t = new HashMap();
            }
            t.put(serverId, new Integer(count));
            fulfillCounts.put(activationId, t);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setCountedNodeLockedHostIds(ServerHostId serverId, NodeLockedHostId[] nodeLockIdList)
            throws OperationsException {
        try {
            if (!needServerId()) {
                throw new ActivationException(new Object[] { serverId.toString() },
                        "serverIdNotAllowed");
            }
            if (!serverHostIds.contains(serverId)) {
                serverHostIds.add(serverId);
            }
            for (int i = 0; i < nodeLockIdList.length; ++i) {
                if (!nodeLockedHostIds.contains(nodeLockIdList[i])) {
                    nodeLockedHostIds.add(nodeLockIdList[i]);
                }
            }
            countedNodeLockedHosts.put(serverId, nodeLockIdList);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setDuration(Duration duration) throws OperationsException {
        try {
            throw new OPSBaseException(new Object[] { this.requestType }, "cannotSetDuration");
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setExpirationDate(Date expirationDate) throws OperationsException {
        try {
            throw new OPSBaseException(new Object[] { this.requestType }, "cannotSetExpirationDate");
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setNodeLockedHostIds(NodeLockedHostId[] nodeLockIdList) throws OperationsException {
        try {
            if (!needNodeLockId()) {
                throw new ActivationException(nodeLockIdList, "nodeLockIdNotAllowed");
            }
            nodeLockedHostIds.clear();
            for (int i = 0; i < nodeLockIdList.length; ++i) {
                nodeLockedHostIds.add(nodeLockIdList[i]);
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setOverridePolicy(String activationId, boolean overridePolicy)
            throws OperationsException {
        policyOverrides.put(activationId, new Boolean(overridePolicy));
    }

    public void setOverriddenPolicy(String activationId, String policyName) {
        policyNameOverrides.put(activationId, policyName);
    }

    public void setRequestedOverdraftCount(String activationId, ServerHostId serverId, int count)
            throws OperationsException {
        try {
            if (!needOverdraftCount(activationId) && count != 0) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "overdraftCountNotAllowed");
            }
            if (count < 0) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "invalidOverdraftCount");
            }
            Map t = null;
            if (requestedOverdraftCounts.get(activationId) != null) {
                t = (Map)requestedOverdraftCounts.get(activationId);
            }
            else {
                t = new HashMap();
            }
            t.put(serverId, new Integer(count));
            requestedOverdraftCounts.put(activationId, t);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setServerHostIds(ServerHostId[] serverIdList) throws OperationsException {
        try {
            if (needServerId()) {
                serverHostIds.clear();
                for (int i = 0; i < serverIdList.length; ++i) {
                    serverHostIds.add(serverIdList[i]);
                }
            }
            else {
                throw new ActivationException(serverIdList, "serverIdNotAllowed");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public NodeLockedHostId[] getCountedNodeLockedHostIds(ServerHostId serverId)
            throws OperationsException {
        return (NodeLockedHostId[])countedNodeLockedHosts.get(serverId);
    }

    public NodeLockedHostId[] getNodeLockedHostIds() throws OperationsException {
        NodeLockedHostId[] array = new NodeLockedHostId[nodeLockedHostIds.size()];
        nodeLockedHostIds.toArray(array);
        return array;
    }

    public ServerHostId[] getServerHostIds() throws OperationsException {
        ServerHostId[] array = new ServerHostId[serverHostIds.size()];
        serverHostIds.toArray(array);
        return array;
    }

    public void setStartDate(Date startDate) throws OperationsException {
        try {
            if (needStartDate()) {
                this.startDate = startDate;
            }
            else {
                throw new OPSBaseException(new Object[] { this.requestType }, "cannotSetStartDate");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public Date getExpirationDate() throws OperationsException {
        // TODO Auto-generated method stub
        return null;
    }

    public Date getStartDate() throws OperationsException {
        return this.startDate;
    }

    protected void performBasicValidation() throws ActivationException, FlexnetBaseException,
            OperationsException {
        if (needStartDate()) {
            if (this.startDate == null) {
                throw new ActivationException("missingStartDate");
            }
        }
        if (needVersionStartDate()) {
            if (this.versionStartDate == null) {
                throw new ActivationException("missingVersionStartDate");
            }
        }
        if (needVersionDate()) {
            if (this.versionDate == null) {
                throw new ActivationException("missingVersionDate");
            }
        }
        if (needServerId()) {
            if (serverHostIds.size() == 0)
                throw new ActivationException("missingServerId");
        }
        if (needNodeLockId()) {
            if (nodeLockedHostIds.size() == 0)
                throw new ActivationException("missingNodeLockId");
        }
        if (needCustomHost()) {
            if (customHostIds.size() == 0)
                throw new ActivationException("missingCustomHostId");
        }

        ((IAttributeSet)this.getFulfillmentAttributes()).validate();

        /* validate required properties */
        Set<String> modelRequiredProps = new HashSet();
        for (LicenseModelImpl impl : licModels) {
            modelRequiredProps.addAll(impl
                    .getModelRequiredProperties(AttributeWhenENC.FULFILLMENT_TIME.toString()));
        }

        IAttributeSet attrSet = (IAttributeSet)this.getFulfillmentAttributes();
        Set<CustomAttributeDescriptor> descriptors = attrSet.getDescriptors();
        Iterator<CustomAttributeDescriptor> iter = descriptors.iterator();
        while (iter.hasNext()) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)iter.next();
            LicenseModelAttributeImpl impl = (LicenseModelAttributeImpl)desc;
            String val = attrSet.getStringValue(desc);
            if (val != null && val.trim().length() == 0) {
                val = null;
            }
            if (desc.isRequired() && val == null) {
                throw new RequiredModelPropertyMissingException(desc.getName());
            }
            if (modelRequiredProps.contains(impl.uniqueName())) {
                if (val == null) {
                    throw new RequiredModelPropertyMissingException(desc.getName());
                }
            }
        }
    }

    public boolean isPolicyOverriden(String activationId) throws OperationsException {
        if (policyOverrides.get(activationId) != null) {
            return ((Boolean)policyOverrides.get(activationId)).booleanValue();
        }
        return false;
    }

    public ConsolidatedLicenseRecord[] consolidate() throws OperationsException {
        FulfillmentRecord[] fmts = fulfill();
        FulfillmentService svc = new FulfillmentService();
        if (fmts != null && fmts.length > 0) {
            return svc.consolidate(fmts);
        }
        return null;
    }

    private LineItemFulfillmentHelperImpl newLineItemFulfillmentHelper(String activationId)
            throws OperationsException {
        LineItemFulfillmentHelperImpl lineItemHelper = new LineItemFulfillmentHelperImpl(
                activationId, source, requestType,
                EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        lineItemHelper.setFulfillmentAttributes(this.getFulfillmentAttributes());
        if (lineItemHelper.needStartDate()) {
            lineItemHelper.setStartDate(this.startDate);
        }
        if (lineItemHelper.needVersionDate()) {
            lineItemHelper.setVersionDate(this.versionDate);
        }
        if (lineItemHelper.needVersionStartDate()) {
            lineItemHelper.setVersionStartDate(this.versionStartDate);
        }
        if (lineItemHelper.needSoldTo()) {
            lineItemHelper.setSoldTo(this.soldTo);
        }
        if (lineItemHelper.needOwner()) {
            lineItemHelper.setOwner(this.owner);
        }
        if (lineItemHelper.isNeedFNPTimeZone()) {
            lineItemHelper.setFNPTimeZone(this.getFNPTimeZone());
        }

        lineItemHelper.setShipToEmail(getShipToEmail());
        lineItemHelper.setShipToAddress(getShipToAddress());
        lineItemHelper.setOverridePolicy(activationId, isPolicyOverriden(activationId));
        lineItemHelper.setRequestForLicenseGeneration(this.isRequestForLicenseGeneration);

        if (isPolicyOverriden(activationId))
            lineItemHelper.setOverriddenPolicy(activationId,
                    (String)policyNameOverrides.get(activationId));
        return lineItemHelper;
    }

    public FulfillmentRecord[] fulfill() throws OperationsException, PolicyDeniedException {
        try {
            performBasicValidation();
            List records = new LinkedList();
            Iterator lineItemIter = lineItems.iterator();
            while (lineItemIter.hasNext()) {
                EntitlementLineItem bo = (EntitlementLineItem)lineItemIter.next();
                String activationId = bo.getActivationID();
                if (serverHostIds.size() > 0) {
                    /* Floating Counted, Floating Uncounted, Nodelocked Counted */
                    Iterator iter = serverHostIds.iterator();
                    while (iter.hasNext()) {
                        ServerHostId server = (ServerHostId)iter.next();
                        LineItemFulfillmentHelperImpl lineItemHelper = newLineItemFulfillmentHelper(activationId);
                        if (needCount()) {
                            int count = getCount(activationId, server);
                            if (getCount(activationId, server) == 0) {
                                // Ignore this server and activation id
                                continue;
                            }
                            lineItemHelper.setCount(activationId, server, count);
                        }
                        if (needOverdraftCount(activationId)) {
                            int overdraftcount = getRequestedOverdraftCount(activationId, server);
                            lineItemHelper.setRequestedOverdraftCount(activationId, server,
                                    overdraftcount);
                        }
                        if (needNodeLockId()) {
                            NodeLockedHostId[] nodelocks = (NodeLockedHostId[])countedNodeLockedHosts
                                    .get(server);
                            lineItemHelper.setNodeLockedHostIds(nodelocks);
                        }
                        lineItemHelper.setServerHostIds(new ServerHostId[] { server });
                        FulfillmentRecord[] fs = lineItemHelper.fulfill();
                        records.add(fs[0]);
                    }
                }
                else if (nodeLockedHostIds.size() > 0) {
                    /* Nodelocked UnCounted */
                    Iterator iter = nodeLockedHostIds.iterator();
                    while (iter.hasNext()) {
                        NodeLockedHostId node = (NodeLockedHostId)iter.next();
                        LineItemFulfillmentHelperImpl lineItemHelper = newLineItemFulfillmentHelper(activationId);

                        if (needNodeLockId()) {
                            lineItemHelper.addNodeLockedHostId(node);
                        }
                        FulfillmentRecord[] fs = lineItemHelper.fulfill();
                        records.add(fs[0]);
                    }
                }
                /*custom license technologies*/
                else if (customHostIds.size() > 0) {
                    Iterator iter = customHostIds.iterator();
                    while (iter.hasNext()) {
                        CustomHostId cHost = (CustomHostId)iter.next();
                        LineItemFulfillmentHelperImpl lineItemHelper = newLineItemFulfillmentHelper(activationId);
                        if (needCount()) {
                            int count = getCountForCustomHost(activationId, cHost);
                            if (count == 0) {
                                // Ignore this server and activation id
                                continue;
                            }
                            lineItemHelper.setCountForCustomHost(activationId, cHost, count);
                        }
                        lineItemHelper.setCustomHostIds(new CustomHostId[] { cHost });
                        FulfillmentRecord[] fs = lineItemHelper.fulfill();
                        records.add(fs[0]);
                    }
                }
                else {
                    /*custom license technologies -> no host */
                    LineItemFulfillmentHelperImpl lineItemHelper = newLineItemFulfillmentHelper(activationId);
                    if (needCount()) {
                        int count = getCount(activationId, null);
                        if (count == 0) {
                            // Ignore this server and activation id
                            continue;
                        }
                        lineItemHelper.setCount(activationId, null, count);
                    }
                    FulfillmentRecord[] fs = lineItemHelper.fulfill();
                    records.add(fs[0]);
                }
            }
            FulfillmentRecord[] result = new FulfillmentRecord[records.size()];
            records.toArray(result);
            return result;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public String[] getActivationIds() {
        Iterator iter = lineItems.iterator();
        String[] actIds = new String[lineItems.size()];
        int i = 0;
        while (iter.hasNext()) {
            EntitlementLineItem item = (EntitlementLineItem)iter.next();
            actIds[i] = item.getActivationID();
            ++i;
        }
        return actIds;
    }

    public boolean isConsolidateAllowed() {
        try {
            String supersedeOrSupersedeSign = AppConfigUtil
                    .getConfigString("ops.SupersedeOrSupersedeSign");
            boolean supersedeYN = false;
            if (supersedeOrSupersedeSign.equals("SUPERSEDE_UPGRADE")
                    || supersedeOrSupersedeSign.equals("SUPERSEDE_RENEWAL")
                    || supersedeOrSupersedeSign.equals("SUPERSEDE_BOTH"))
                supersedeYN = true;
            if (supersedeYN && licenseTechnology.isFlexNet()) {
                return false;
            }

            if (lineItems.size() <= 1) {
                return false;
            }
            LicenseTechnologyImpl impl = new LicenseTechnologyImpl(licenseTechnology);
            if (impl.getLicenseConsolidatorInterface() == null) {
                return false;
            }
            LicenseTechnologyTypeENC ltType = impl.getLicenseTechnologyType();
            if (LicenseTechnologyTypeENC.MANUAL.equals(ltType)
                    || LicenseTechnologyTypeENC.HANDS_FREE.equals(ltType)) {
                return false;
            }
            return true;
        }
        catch (FlexnetBaseException ex) {
            logger.error(new LogMessage("Got error while getting configuration option "), ex);
            return false;
        }
    }

    public boolean needVersionDate() {
        Iterator iter = lineItems.iterator();
        while (iter.hasNext()) {
            EntitlementLineItemBO bo = (EntitlementLineItemBO)iter.next();
            if (bo.getVersionDateOption() != null
                    && bo.getVersionDateOption().equals(
                            VersionDateOptionsENC.DEFINE_VERSION_DATE_LATER)
                    && bo.getVersionDate() == null) {
                return true;
            }
        }
        return false;
    }

    public boolean needVersionStartDate() {
        Iterator iter = lineItems.iterator();
        while (iter.hasNext()) {
            EntitlementLineItemBO bo = (EntitlementLineItemBO)iter.next();
            if (bo.getVersionDateOption() != null
                    && bo.getVersionDateOption().equals(
                            VersionDateOptionsENC.DEFINE_VERSION_STARTDATE_LATER)
                    && bo.getVersionDate() == null) {
                return true;
            }
        }
        return false;
    }

    public Date getVersionDate() throws OperationsException {
        return this.versionDate;
    }

    public Date getVersionStartDate() throws OperationsException {
        return this.versionStartDate;
    }

    public void setVersionStartDate(Date sd) throws OperationsException {
        try {
            if (needVersionStartDate()) {
                this.versionStartDate = sd;
            }
            else if (sd != null) {
                throw new OPSBaseException(new Object[] { this.requestType },
                        "cannotSetVersionStartDate");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setVersionDate(Date sd) throws OperationsException {
        try {
            if (needVersionDate()) {
                this.versionDate = sd;
            }
            else if (sd != null) {
                throw new OPSBaseException(new Object[] { this.requestType },
                        "cannotSetVersionDate");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public boolean needSoldTo() {
        // user needs to specify soldTo if the logged in user is a Publisher User and is
        // activating a web reg key

        if (ThreadContextUtil.getLineItemID() != null
                || ThreadContextUtil.getEntitlementID() != null)
            return false;

        User usr = ThreadContextUtil.getUser();
        if (usr != null) {
            OrgUnit org = usr.getContactInfo().getBelongsTo();
            if (org.isPublisher() && isWebRegKey) {
                return true;
            }
        }
        return false;
    }

    public boolean needOwner() {
        // user needs to specify owner if the logged in user is a Publisher User and is
        // activating a web reg key

        if (ThreadContextUtil.getLineItemID() != null
                || ThreadContextUtil.getEntitlementID() != null)
            return false;

        User usr = ThreadContextUtil.getUser();
        if (usr != null) {
            OrgUnit org = usr.getContactInfo().getBelongsTo();
            if (org.isPublisher() && isWebRegKey) {
                return true;
            }
        }
        return false;
    }

    public void setSoldTo(OrganizationUnit soldToOrg) throws OperationsException {
        try {
            if (needSoldTo()) {
                this.soldTo = soldToOrg;
            }
            else if (soldToOrg != null) {
                throw new OPSBaseException(new Object[] { this.requestType }, "cannotSetSoldTo");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setOwner(User owner) throws OperationsException {
        try {
            if (needOwner()) {
                this.owner = owner;
            }
            else if (owner != null) {
                throw new OPSBaseException(new Object[] { this.requestType }, "cannotSetSoldTo");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void addCustomHostId(CustomHostId customId) throws OperationsException {
        try {
            if (!needCustomHost()) {
                throw new ActivationException(new Object[] { customId.toString() },
                        "serverIdNotAllowed");
            }
            customHostIds.add(customId);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void removeCustomHostId(CustomHostId cId) throws OperationsException {
        if (needCustomHost()) {
            if (cId != null && customHostIds.contains(cId)) {
                customHostIds.remove(cId);
            }
        }
    }

    public void setCustomHostIds(CustomHostId[] cIdList) throws OperationsException {
        try {
            if (needCustomHost()) {
                customHostIds.clear();
                for (int i = 0; i < cIdList.length; ++i) {
                    customHostIds.add(cIdList[i]);
                }
            }
            else {
                throw new ActivationException(cIdList, "serverIdNotAllowed");
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public CustomHostId[] getCustomHostIds() throws OperationsException {
        CustomHostId[] array = new CustomHostId[customHostIds.size()];
        customHostIds.toArray(array);
        return array;
    }

    public void setCountForCustomHost(String activationId, CustomHostId cId, int count)
            throws OperationsException {
        try {
            if (!needCount()) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "countNotAllowed");
            }
            if (count <= 0) {
                throw new ActivationException(new Object[] { new Integer(count) }, "invalidCount");
            }
            Map t = null;
            if (fulfillCounts.get(activationId) != null) {
                t = (Map)fulfillCounts.get(activationId);
            }
            else {
                t = new HashMap();
            }
            t.put(cId, new Integer(count));
            fulfillCounts.put(activationId, t);
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public int getCountForCustomHost(String activationId, CustomHostId cId)
            throws OperationsException {
        if (fulfillCounts.get(activationId) != null) {
            Map t = (Map)fulfillCounts.get(activationId);
            if (t.get(cId) != null) {
                Number count = (Number)t.get(cId);
                return count.intValue();
            }
            else {
                return 0;
            }
        }
        return 0;
    }

    public HostType getHostType() {

        return hostType;
    }

}
