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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.flexnet.operations.api.IEntitlement;
import com.flexnet.operations.api.IFulfillmentActionENC;
import com.flexnet.operations.api.IFulfillmentRecord;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.exceptions.OPSBaseException;
import com.flexnet.operations.exceptions.activations.ActivationException;
import com.flexnet.operations.interfaces.CustomPolicy.PolicyResult;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.FulfillmentRecord;
import com.flexnet.operations.publicapi.FulfillmentRequestTypeENC;
import com.flexnet.operations.publicapi.FulfillmentSourceENC;
import com.flexnet.operations.publicapi.NodeLockedHostId;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.PolicyDeniedException;
import com.flexnet.operations.publicapi.PolicyTypeENC;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.server.dao.HostTypeDAO;
import com.flexnet.operations.services.CustomPolicyFRImpl;
import com.flexnet.operations.services.LicenseTechnologyHostAttributeImpl;
import com.flexnet.operations.services.LicenseTechnologyImpl;
import com.flexnet.operations.services.PolicyService;
import com.flexnet.operations.services.UtilityService;
import com.flexnet.operations.services.userManagement.FulfillmentAuthorizationService;
import com.flexnet.operations.services.userManagement.OrgAuthorizationService;
import com.flexnet.operations.util.ExceptionUtility;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.logging.LogMessage;
import com.flexnet.platform.web.utils.SpringBeanFactory;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.bizobjects.HostTypeDO;
import com.flexnet.products.bizobjects.LicenseModelBO;
import com.flexnet.products.publicapi.IPermissionENC;

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
public class RehostFulfillmentHelperImpl extends LifecycleFulfillmentHelperImpl {

    LineItemFulfillmentHelperImpl remnantLineItemHelper = null;
    LineItemFulfillmentHelperImpl rehostedLineItemHelper = null;

    public RehostFulfillmentHelperImpl(
            FulfillmentRecord rec, FulfillmentSourceENC source,
            FulfillmentRequestTypeENC requestType)
            throws OperationsException{
        super(rec, source, requestType);
    }

    protected void validateRequestType() throws FlexnetBaseException {
        if (!requestType.equals(FulfillmentRequestTypeENC.REHOST)) {
            throw new OPSBaseException("requestTypeShouldBeRehost");
        }
        if (isTrustedType()) {
            throw new OPSBaseException("trustedLicensesCannotbeRehosted");
        }
        LicenseTechnologyImpl impl = new LicenseTechnologyImpl(licenseTechnology);
        if (!impl.isRehostAllowed()) {
            throw new OPSBaseException(new Object[] { impl.getName() },
                    "rehostNotAllowedByLicenseTechnology");
        }
    }

    public boolean needNodeLockId() {
        return modelType.equals(LicenseModelBO.MODEL_NODE_LOCKED);
    }

    public boolean needServerId() {
        return isCountedCertificate() || isFloatingCertificate();
    }

    public boolean needCount() {
        if (fulfillmentRecord.getFulfillmentCount() > 1) {
            return true;
        }
        return false;
    }

    public boolean needOverdraftCount(String activationId) throws OperationsException {
        if (fulfillmentRecord.getOverdraftCount() > 0) {
            return true;
        }
        return false;
    }

    public void addNodeLockedHostId(NodeLockedHostId nodeLockId) throws OperationsException {
        try {
            if (!needNodeLockId()) {
                throw new ActivationException(new Object[] { nodeLockId.toString() },
                        "nodeLockIdNotAllowed");
            }
            nodelocks.add(nodeLockId);
        }
        catch (FlexnetBaseException ex) {
            UtilityService.makeOperationsException(ex);
        }
    }

    public void addServerId(ServerHostId serverId) throws OperationsException {
        try {
            if (!needServerId()) {
                throw new ActivationException(new Object[] { serverId.toString() },
                        "serverIdNotAllowed");
            }
            this.server = serverId;
        }
        catch (FlexnetBaseException ex) {
            UtilityService.makeOperationsException(ex);
        }
    }

    public void setCount(String activationId, ServerHostId serverId, int count)
            throws OperationsException {
        try {
            if (!needCount() && count != 1) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "countNotAllowed");
            }
            if (count <= 0) {
                throw new ActivationException(new Object[] { new Integer(count) }, "invalidCount");
            }
            this.count = count;
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
            nodelocks.clear();
            for (int i = 0; i < nodeLockIdList.length; ++i) {
                nodelocks.add(nodeLockIdList[i]);
            }
        }
        catch (FlexnetBaseException ex) {
            UtilityService.makeOperationsException(ex);
        }
    }

    public void setRequestedOverdraftCount(String activationId, ServerHostId serverId, int count)
            throws OperationsException {
        try {
            if (!needOverdraftCount(activationId)) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "overdraftCountNotAllowed");
            }
            if (count <= 0) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "invalidOverdraftCount");
            }
            requestedOverdraftCount = count;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void setServerHostIds(ServerHostId[] serverIdList) throws OperationsException {
        try {
            if (!needServerId()) {
                throw new ActivationException(serverIdList, "serverIdNotAllowed");
            }
            if (serverIdList != null && serverIdList.length > 1) {
                throw new ActivationException(serverIdList, "onlyOneServerAllowed");
            }
            this.server = serverIdList[0];
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void removeCountedNodeLockedHostIds(ServerHostId serverId) throws OperationsException {
        if (needCountedNodeLockId()) {
            if (server != null && serverId != null && server.equals(serverId)) {
                server = null;
            }
        }
    }

    public void removeNodeLockedHostId(NodeLockedHostId nodeLockId) throws OperationsException {
        if (needNodeLockId()) {
            if (nodeLockId != null && nodelocks.contains(nodeLockId)) {
                nodelocks.remove(nodeLockId);
            }
        }
    }

    public void removeServerId(ServerHostId serverId) throws OperationsException {
        if (needServerId()) {
            if (server != null && server.equals(serverId)) {
                server = null;
            }
        }
    }

    protected void performBasicValidation() throws ActivationException, FlexnetBaseException {
        // check that requested return count is less than the original fulfillment count
        int origCount = fulfillmentRecord.getFulfillmentCount();
        if (count > origCount) {
            throw new ActivationException(
                    new Object[] { new Integer(count), new Integer(origCount) },
                    "countExceedsAvailableFulfillment");
        }
        // check that the requested returned overdraft count is less than the original fulfillment
        // overdraft count
        int origOverdraft = fulfillmentRecord.getOverdraftCount();
        if (requestedOverdraftCount > origOverdraft) {
            throw new OPSBaseException(
                    "rehostedOverdraftExceedsFulfillmentOverdraft",
                    new Object[] { new Integer(requestedOverdraftCount), new Integer(origOverdraft) });
        }
    }

    private void invokeBeforeRehostPolicy() throws PolicyDeniedException, OperationsException {
        CustomPolicyFRImpl cpImpl = new CustomPolicyFRImpl(this, lineitem, count,
                requestedOverdraftCount, server,
                EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        PolicyResult policyResult = PolicyService.invokeBeforePolicy(cpImpl);
        try {
            if (!policyResult.isPolicySatisfied())
                throw new OPSBaseException("rehostDeniedDueToPolicy",
                        new Object[] { policyResult.getMessage() });
        }
        catch (OPSBaseException ex) {
            throw new PolicyDeniedException(PolicyTypeENC.REHOST_NUMBER_POLICY,
                    lineitem.getActivationID(), ex.getLocalizedMessage(ThreadContextUtil
                            .getLocale()), ex);
        }
    }

    private LineItemFulfillmentHelperImpl newRehostedLineItemFulfillmentHelper()
            throws OperationsException {
        LineItemFulfillmentHelperImpl lineItemHelper = new LineItemFulfillmentHelperImpl(
                getActivationId(), source, requestType,
                EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        lineItemHelper.setOverridePolicy(getActivationId(), policyOverriden);
        lineItemHelper.setShipToEmail(getShipToEmail());
        lineItemHelper.setShipToAddress(getShipToAddress());

        if (server != null)
            lineItemHelper.setServerHostIds(new ServerHostId[] { server });
        if (nodelocks != null && nodelocks.size() > 0) {
            NodeLockedHostId[] hosts = new NodeLockedHostId[nodelocks.size()];
            Iterator iter = nodelocks.iterator();
            int i = 0;
            while (iter.hasNext()) {
                NodeLockedHostId host = (NodeLockedHostId)iter.next();
                hosts[i] = host;
                ++i;
            }
            lineItemHelper.setNodeLockedHostIds(hosts);
        }
        if (customHostId != null)
            lineItemHelper.setCustomHostIds(new CustomHostId[] { customHostId });
        lineItemHelper.setFulfillmentAttributes(fulfillmentRecord.getLicenseModelAttributes());
        lineItemHelper.setFNPTimeZone(this.getFNPTimeZone());

        lineItemHelper.setRequestForLicenseGeneration(this.isRequestForLicenseGeneration);
        if (lineItemHelper.needStartDate()) {
            lineItemHelper.setStartDate(fulfillmentRecord.getStartDate());
        }
        return lineItemHelper;
    }

    protected ActivationInstance createActivationInstance() throws PolicyDeniedException,
            FlexnetBaseException, OperationsException {
        // TODO Auto-generated method stub
        return null;
    }

    public ArrayList createActivationInstances() throws PolicyDeniedException, OperationsException {
        if (!policyOverriden)
            invokeBeforeRehostPolicy();

        // return all of this fulfillment's counts to the entitlement
        MasterFulfillmentHelperImpl.returnFulfillmentRecordCounts(lineitem,
                fulfillmentRecord.getFulfillmentCount(), fulfillmentRecord.getOverdraftCount());

        // now re-fulfill any unreturned count to the original host (if any)

        int countLeft = fulfillmentRecord.getFulfillmentCount() - count;
        int overdraftLeft = fulfillmentRecord.getOverdraftCount() - requestedOverdraftCount;

        if (countLeft == 0)
            /* don't allow orphaned overdrafts */
            requestedOverdraftCount = fulfillmentRecord.getOverdraftCount();
        ArrayList result = new ArrayList();
        // rehost the requested count
        rehostedLineItemHelper = newRehostedLineItemFulfillmentHelper();
        rehostedLineItemHelper.setCount(getActivationId(), server, count);
        rehostedLineItemHelper.setRequestedOverdraftCount(getActivationId(), server,
                requestedOverdraftCount);

        // populate the parentFulfillments map which is used by the Generator Request
        rehostedLineItemHelper.parentFulfillments.put(fulfillmentRecord, new Integer(count));
        ActivationInstance rehostedInst = rehostedLineItemHelper.internalFulfill();
        rehostedInst.setParentFulfillment(fulfillmentRecord);
        result.add(rehostedInst);

        if (countLeft > 0) {
            remnantLineItemHelper = newOriginalLineItemFulfillmentHelper();
            remnantLineItemHelper.setCount(getActivationId(), server, countLeft);
            remnantLineItemHelper.setRequestedOverdraftCount(getActivationId(), server,
                    overdraftLeft);
            remnantLineItemHelper.parentFulfillments.put(fulfillmentRecord, new Integer(count));
            ActivationInstance remnantInst = remnantLineItemHelper.internalFulfill();
            remnantInst.setParentFulfillment(fulfillmentRecord);
            result.add(remnantInst);
        }
        // for a regenerative technology, the custom generator needs to be called even when all
        // licenses
        // are moved from host A to host B so that it can update the license text on the host.
        if (countLeft == 0 && licenseTechnology.isRegenerative()) {
            // populate the parentFulfillments map which is used by the Generator Request
            this.parentFulfillments.put(fulfillmentRecord, new Integer(count));
            generateLicense(fulfillmentRecord, true,
                    EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        }

        return result;
    }

    public void writeFulfillmentHistory(ArrayList results, String errorOccurred)
            throws OperationsException {
        try {
            if (errorOccurred != null)
                return;

            createFulfillmentHistoryRecord(null, fulfillmentRecord, getFulfillmentActionType(),
                    count, policyOverriden, overriddenPolicy, ThreadContextUtil.getUser());

            if (results != null && results.size() > 0) {
                for (int ii = 0; ii < results.size(); ii++) {
                    IFulfillmentRecord rc = (IFulfillmentRecord)results.get(ii);
                    createFulfillmentHistoryRecord(fulfillmentRecord, rc,
                            IFulfillmentActionENC.ACTIVATED, rc.getFulfillmentCount(), false, null,
                            ThreadContextUtil.getUser());
                }
            }
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }

        CustomPolicyFRImpl cpImpl = new CustomPolicyFRImpl(this, lineitem, count,
                requestedOverdraftCount, server,
                EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);
        PolicyService.invokeAfterPolicy(cpImpl, fulfillmentRecord, results, policyOverriden,
                errorOccurred);
    }

    public FulfillmentRecord[] fulfill() throws OperationsException, PolicyDeniedException {
        // throws exception if user does not have permission or access to the fulfillment
        canPerformLifecycleOperationOnFulfillment();
        String errorOccurred = null;
        ArrayList newInstances = null;
        try {
            performBasicValidation();

            // obsolete fulfillment and set lifecycle staus
            fulfillmentRecord = (ActivationInstance)ActivationInstance.getById(
                    ActivationInstance.class, fulfillmentRecord.getId());
            fulfillmentRecord.setStatus(ActivationInstance.STATUS_OBSOLETE);
            fulfillmentRecord.setLifeCycleStatus(ActivationInstance.LIFECYCLE_STATUS_REHOST);
            fulfillmentRecord.persist();

            newInstances = createActivationInstances();

            Iterator iter = newInstances.iterator();
            while (iter.hasNext()) {
                ActivationInstance instance = (ActivationInstance)iter.next();
                instance.persist();
            }
            if (rehostedLineItemHelper != null) {
                rehostedLineItemHelper.updateLineItemCounts();
            }
            if (remnantLineItemHelper != null) {
                remnantLineItemHelper.updateLineItemCounts();
            }
            lineitem.persist();
            FulfillmentRecord[] records = new FulfillmentRecord[newInstances.size()];
            newInstances.toArray(records);
            return records;
        }
        catch (FlexnetBaseException ex) {
            errorOccurred = ExceptionUtility.getMessage(ex, Locale.getDefault());
            throw UtilityService.makeOperationsException(ex);
        }
        catch (OperationsException e) {
            errorOccurred = ExceptionUtility.getMessage(e, Locale.getDefault());
            throw e;
        }
        finally {
            writeFulfillmentHistory(newInstances, errorOccurred);
        }
    }

    public IPermissionENC getRequiredPermission() {
        if (ThreadContextUtil.isLoggedInFromPortal())
            return IPermissionENC.PORTAL_REHOST;
        else
            return IPermissionENC.REHOST_LICENSE;
    }

    public boolean needCustomHost() throws OperationsException {
        if (licenseTechnology.isFlexNet())
            return false;

        if (hostType == null)
            return false;
        HostTypeDAO hostTypeDao = (HostTypeDAO)SpringBeanFactory.getInstance().getBean(
                "hostTypeDAO");

        HostTypeDO hostTypeDo = hostTypeDao.getHostTypeDO(hostType.getId());
        List hostAttrs = LicenseTechnologyHostAttributeImpl.getHostAttributes(licenseTechnology,
                hostTypeDo);
        if (hostAttrs.size() > 0)
            return true;
        else
            return false;
    }

    public void setCustomHostIds(CustomHostId[] customIdList) throws OperationsException {
        try {
            if (!needCustomHost()) {
                throw new ActivationException(customIdList, "serverIdNotAllowed");
            }
            if (customIdList != null && customIdList.length > 1) {
                throw new ActivationException(customIdList, "onlyOneServerAllowed");
            }
            this.customHostId = customIdList[0];
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void addCustomHostId(CustomHostId customHostId) throws OperationsException {
        try {
            if (!needCustomHost()) {
                throw new ActivationException(new Object[] { customHostId.toString() },
                        "serverIdNotAllowed");
            }
            this.customHostId = customHostId;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public void removeCustomHostId(CustomHostId cHostId) throws OperationsException {
        if (needCustomHost()) {
            if (customHostId != null && customHostId.equals(cHostId)) {
                customHostId = null;
            }
        }
    }

    public void setCountForCustomHost(String activationId, CustomHostId cId, int count)
            throws OperationsException {
        try {
            if (!needCount() && count != 1) {
                throw new ActivationException(new Object[] { new Integer(count) },
                        "countNotAllowed");
            }
            if (count <= 0) {
                throw new ActivationException(new Object[] { new Integer(count) }, "invalidCount");
            }
            if (count > fulfillmentRecord.getFulfillmentCount()) {
                throw new ActivationException(new Object[] { new Integer(count),
                        new Integer(fulfillmentRecord.getFulfillmentCount()) },
                        "countExceedsAvailableFulfillment");
            }
            this.count = count;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }

    }

    protected void canPerformLifecycleOperationOnFulfillment() throws OperationsException {
        IPermissionENC perm = getRequiredPermission();
        IPermissionENC childPerm = IPermissionENC.PORTAL_REHOST_CHILD_FULFILLMENT;

        // check for required permission.
        if (!OrgAuthorizationService.hasPermission(((IEntitlement)fulfillmentRecord.getLineItem()
                .getParentEntitlement()).getChannelPartners(), perm.getName(), childPerm.getName())) {
            logger.debug(new LogMessage(
                    "Not enough permissions for operation.  Missing permission = " + perm.getName()));
            throw UtilityService.makeOperationsException("notEnoughPermissions",
                    new Object[] { perm.getName() });
        }
        FulfillmentAuthorizationService.isFulfillmentVisibleToUser(fulfillmentRecord);
    }

}
