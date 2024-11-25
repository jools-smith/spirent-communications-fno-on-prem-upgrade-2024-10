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

import com.flexnet.operations.api.IEntitlement;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.exceptions.OPSBaseException;
import com.flexnet.operations.exceptions.activations.ActivationException;
import com.flexnet.operations.interfaces.CustomPolicy.PolicyResult;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.FulfillmentRecord;
import com.flexnet.operations.publicapi.FulfillmentRequestTypeENC;
import com.flexnet.operations.publicapi.FulfillmentSourceENC;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.PolicyDeniedException;
import com.flexnet.operations.publicapi.PolicyTypeENC;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.services.CustomPolicyFRImpl;
import com.flexnet.operations.services.LicenseTechnologyImpl;
import com.flexnet.operations.services.PolicyService;
import com.flexnet.operations.services.UtilityService;
import com.flexnet.operations.services.userManagement.FulfillmentAuthorizationService;
import com.flexnet.operations.services.userManagement.OrgAuthorizationService;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.logging.LogMessage;
import com.flexnet.platform.web.utils.ThreadContextUtil;
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
public class ReturnFulfillmentHelperImpl extends LifecycleFulfillmentHelperImpl {
    LineItemFulfillmentHelperImpl lineItemHelper = null;
    EntitlementLineItemBO.modelType mdlType;
    protected boolean partialReturn = false;

    public ReturnFulfillmentHelperImpl(
            FulfillmentRecord rec, FulfillmentSourceENC source,
            FulfillmentRequestTypeENC requestType, EntitlementLineItemBO.modelType mdlType)
            throws OperationsException{
        super(rec, source, requestType);
        this.mdlType = mdlType;
    }

    protected void validateRequestType() throws FlexnetBaseException {
        if (!requestType.equals(FulfillmentRequestTypeENC.RETURN)
                && !requestType.equals(FulfillmentRequestTypeENC.SHORT_CODE_RETURN)) {
            throw new OPSBaseException("requestTypeShouldBeReturn");
        }
        LicenseTechnologyImpl impl = new LicenseTechnologyImpl(licenseTechnology);
        if (!impl.isReturnAllowed()) {
            throw new OPSBaseException(new Object[] { impl.getName() },
                    "returnNotAllowedByLicenseTechnology");
        }
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

    public void setRequestedOverdraftCount(String activationId, ServerHostId serverId, int overdraft)
            throws OperationsException {
        try {
            if (!needOverdraftCount(activationId)) {
                throw new ActivationException(new Object[] { new Integer(overdraft) },
                        "overdraftCountNotAllowed");
            }
            if (overdraft <= 0) {
                throw new ActivationException(new Object[] { new Integer(overdraft) },
                        "invalidOverdraftCount");
            }
            if (overdraft > fulfillmentRecord.getOverdraftCount()) {
                throw new OPSBaseException("returnedOverdraftExceedsFulfillmentOverdraft",
                        new Object[] { new Integer(overdraft),
                                new Integer(fulfillmentRecord.getOverdraftCount()) });
            }
            requestedOverdraftCount = overdraft;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    protected void performBasicValidation() throws ActivationException, FlexnetBaseException {
        // validation is already done by this time
    }

    protected void invokeBeforeReturnPolicy() throws PolicyDeniedException, OperationsException {
        CustomPolicyFRImpl cpImpl = new CustomPolicyFRImpl(this, lineitem, count,
                requestedOverdraftCount, server, mdlType);
        PolicyResult policyResult = PolicyService.invokeBeforePolicy(cpImpl);
        try {
            if (!policyResult.isPolicySatisfied())
                throw new OPSBaseException("returnDeniedDueToPolicy",
                        new Object[] { policyResult.getMessage() });
        }
        catch (OPSBaseException ex) {
            throw new PolicyDeniedException(PolicyTypeENC.RETURN_NUMBER_POLICY,
                    lineitem.getActivationID(), ex.getLocalizedMessage(ThreadContextUtil
                            .getLocale()), ex);
        }
    }

    public ActivationInstance createActivationInstance() throws PolicyDeniedException,
            OperationsException, FlexnetBaseException {
        if (!policyOverriden)
            invokeBeforeReturnPolicy();

        int countLeft = fulfillmentRecord.getFulfillmentCount() - count;
        int overdraftLeft = fulfillmentRecord.getOverdraftCount() - requestedOverdraftCount;

        // if the cancel license policy is true (indicating that counts should not be credited back
        // to the entitlement) then return the countLeft or remnant count to the entitlement
        // if the cancel license policy is false (indicating that counts should be credited back to
        // the entitlement) then return all of this fulfillment's counts to the entitlement
        if (lineitem.getCancelLicensePolicy(mdlType).isCancelLicense()) {
            MasterFulfillmentHelperImpl.returnFulfillmentRecordCounts(lineitem, countLeft,
                    overdraftLeft);
            lineitem.setCancelledCount(lineitem.getCancelledCount() + count);
        }
        else {
            MasterFulfillmentHelperImpl.returnFulfillmentRecordCounts(lineitem,
                    fulfillmentRecord.getFulfillmentCount(), fulfillmentRecord.getOverdraftCount());
        }

        // now re-fulfill any unreturned count to the original host (if any)
        if (!isTrustedType() && countLeft > 0) {
            return createUnreturnedFulfillmentInstance(countLeft, overdraftLeft);
        }
        // for a regenerative technology, the custom generator needs to be called even when all the
        // count in a fulfillment
        // record is returned so that it can update the license text on the host.
        if (countLeft == 0 && licenseTechnology.isRegenerative()) {
            // populate the parentFulfillments map which is used by the Generator Request
            this.parentFulfillments.put(fulfillmentRecord, new Integer(count));
            generateLicense(fulfillmentRecord, true, mdlType);
        }
        return null;
    }

    /**
     * Create fulfillment instance for unreturned count.
     * 
     * @param countLeft
     * @param overdraftLeft
     * @return
     * @throws OperationsException
     * @throws PolicyDeniedException
     * @throws FlexnetBaseException
     */
    protected ActivationInstance createUnreturnedFulfillmentInstance(int countLeft,
            int overdraftLeft) throws OperationsException, PolicyDeniedException,
            FlexnetBaseException {

        lineItemHelper = newOriginalLineItemFulfillmentHelper();
        if (fulfillmentRecord.isFlexnet()) {
            lineItemHelper.setCount(getActivationId(), server, countLeft);
            lineItemHelper.setRequestedOverdraftCount(getActivationId(), server, overdraftLeft);
        }
        else {
            lineItemHelper.setCountForCustomHost(getActivationId(), customHostId, countLeft);
        }

        // populate the parentFulfillments map which is used by the Generator Request
        lineItemHelper.parentFulfillments.put(fulfillmentRecord, new Integer(count));
        ActivationInstance inst = lineItemHelper.internalFulfill();
        lineItemHelper.updateLineItemCounts();
        inst.setParentFulfillment(fulfillmentRecord);
        return inst;
    }

    public FulfillmentRecord[] fulfill() throws OperationsException, PolicyDeniedException {
        try {
            // throws exception if user does not have permission or access to the fulfillment
            canPerformLifecycleOperationOnFulfillment();
            checkPartialFulfillmentsPolicy();
            FulfillmentRecord[] records = null;
            if (partialReturn) {
                records = super.fulfillLicenseLifecycleRequest(this, false, true);
            }
            else {
                /* set the lifecycle status on parent fulfillment*/
                fulfillmentRecord.setLifeCycleStatus(ActivationInstance.LIFECYCLE_STATUS_RETURN);
                records = super.fulfillLicenseLifecycleRequest(this, true, true);
            }
            return records;
        }
        catch (FlexnetBaseException ex) {
            throw UtilityService.makeOperationsException(ex);
        }
    }

    public IPermissionENC getRequiredPermission() {
        if (ThreadContextUtil.isLoggedInFromPortal())
            return IPermissionENC.PORTAL_RETURN;
        else
            return IPermissionENC.RETURN_LICENSE;
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
        IPermissionENC childPerm = IPermissionENC.PORTAL_RETURN_CHILD_FULFILLMENT;

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