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

package com.flexnet.operations.web.forms.activations;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.flexnet.operations.web.forms.OperationsBaseForm;
import com.flexnet.operations.web.forms.product.ProductBean;

public abstract class BatchActivationBaseForm extends OperationsBaseForm {

    private Date startDate = null;
    private Date versionDate = null;
    private Date versionStartDate = null;
    private String soldToDisplayName = "";
    private String licenseModelId = "";
    private String licenseModelName = "";
    private String modelCounted = "";
    private String modelFloating = "";
    private String modelNodeLocked = "";
    private String soldToId = "";
    private String licenseModelType = "";
    private String consolidateTheLicense = "yes";

    private boolean needStartDate;
    private boolean needVersionDate;
    private boolean needVersionStartDate;
    private boolean needCount;
    private boolean needNodeLockId;
    private boolean needDuration;
    private boolean needServerId;
    private boolean consolidateAllowed;
    private boolean needSoldTo;

    private List selectedLineItems = new LinkedList();
    private boolean showOverridePolicyMessage = false;
    private String overridePolicy = "";
    private String nextHost = "";
    private String currentHost = "";

    private String ownerId = "";
    private String ownerName = "";
    private boolean needOwner;

    private boolean needCustomHost;

    public List getSelectedLineItems() {
        return selectedLineItems;
    }

    public void addLineItemInfo(LineItemInfo info) {
        selectedLineItems.add(info);
    }

    public LineItemInfo newLineItemInfo() {
        return new LineItemInfo();
    }

    public class LineItemInfo {
        private String activationId = "";
        private String SKU = "";
        private String expirationDate = "";
        private boolean needOverdraftCount;
        private String remainingCopies = "0";
        private String pendingCopies = "0";
        private String remainingOverdraft = "0";
        private String availableExtraActivations = "0";
        private String remainingUnallocatedCopies = "0";
        private Map<ProductBean, Integer> orderables = new HashMap<ProductBean, Integer>();

        public LineItemInfo(){}

        public String getSKU() {
            return SKU;
        }

        public void setSKU(String sku) {
            SKU = sku;
        }

        public String getActivationId() {
            return activationId;
        }

        public void setActivationId(String activationId) {
            this.activationId = activationId;
        }

        public boolean isNeedOverdraftCount() {
            return needOverdraftCount;
        }

        public void setNeedOverdraftCount(boolean needOverdraftCount) {
            this.needOverdraftCount = needOverdraftCount;
        }

        public String getExpirationDate() {
            return expirationDate;
        }

        public void setExpirationDate(String expirationDate) {
            this.expirationDate = expirationDate;
        }

        public void setExpiry(Date expiry) {
            setExpirationDate(toGMTDateString(expiry));
        }

        public String getRemainingCopies() {
            return remainingCopies;
        }

        public void setRemainingCopies(String remainingCopies) {
            this.remainingCopies = remainingCopies;
        }

        public String getRemainingOverdraft() {
            return remainingOverdraft;
        }

        public void setRemainingOverdraft(String remainingOverdraft) {
            this.remainingOverdraft = remainingOverdraft;
        }

        public String getAvailableExtraActivations() {
            return availableExtraActivations;
        }

        public void setAvailableExtraActivations(String availableExtraActivations) {
            this.availableExtraActivations = availableExtraActivations;
        }

        public String getPendingCopies() {
            return pendingCopies;
        }

        public void setPendingCopies(String pendingCopies) {
            this.pendingCopies = pendingCopies;
        }

        public String getRemainingUnallocatedCopies() {
            return remainingUnallocatedCopies;
        }

        public void setRemainingUnallocatedCopies(String remainingUnallocatedCopies) {
            this.remainingUnallocatedCopies = remainingUnallocatedCopies;
        }

        public Map<ProductBean, Integer> getOrderables() {
            return orderables;
        }

        public void setOrderables(Map<ProductBean, Integer> orderables) {
            this.orderables = orderables;
        }
    }

    public String getLicenseModelId() {
        return licenseModelId;
    }

    public void setLicenseModelId(String licenseModelId) {
        this.licenseModelId = licenseModelId;
    }

    public String getLicenseModelName() {
        return licenseModelName;
    }

    public void setLicenseModelName(String licenseModelName) {
        this.licenseModelName = licenseModelName;
    }

    public String getModelCounted() {
        return modelCounted;
    }

    public void setModelCounted(String modelCounted) {
        this.modelCounted = modelCounted;
    }

    public String getModelFloating() {
        return modelFloating;
    }

    public void setModelFloating(String modelFloating) {
        this.modelFloating = modelFloating;
    }

    public String getModelNodeLocked() {
        return modelNodeLocked;
    }

    public void setModelNodeLocked(String modelNodeLocked) {
        this.modelNodeLocked = modelNodeLocked;
    }

    /**
     * @return Returns the soldTo.
     */
    public String getSoldToDisplayName() {
        return soldToDisplayName;
    }

    /**
     * @param soldTo
     *            The soldTo to set.
     */
    public void setSoldToDisplayName(String soldToDisplayName) {
        this.soldToDisplayName = soldToDisplayName;
    }

    /**
     * @return Returns the shipToOrgId.
     */
    public String getSoldToId() {
        return soldToId;
    }

    /**
     * @param shipToOrgId
     *            The shipToOrgId to set.
     */
    public void setSoldToId(String soldToId) {
        this.soldToId = soldToId;
    }

    public String getLicenseModelType() {
        return licenseModelType;
    }

    public void setLicenseModelType(String licenseModelType) {
        this.licenseModelType = licenseModelType;
    }

    public String getConsolidateTheLicense() {
        return consolidateTheLicense;
    }

    public void setConsolidateTheLicense(String consolidateTheLicense) {
        this.consolidateTheLicense = consolidateTheLicense;
    }

    public boolean isNeedCount() {
        return needCount;
    }

    public void setNeedCount(boolean needCount) {
        this.needCount = needCount;
    }

    public boolean isNeedDuration() {
        return needDuration;
    }

    public void setNeedDuration(boolean needDuration) {
        this.needDuration = needDuration;
    }

    public boolean isNeedNodeLockId() {
        return needNodeLockId;
    }

    public void setNeedNodeLockId(boolean needNodeLockId) {
        this.needNodeLockId = needNodeLockId;
    }

    public boolean isNeedServerId() {
        return needServerId;
    }

    public void setNeedServerId(boolean needServerId) {
        this.needServerId = needServerId;
    }

    public boolean isNeedStartDate() {
        return needStartDate;
    }

    public void setNeedStartDate(boolean needStartDate) {
        this.needStartDate = needStartDate;
    }

    public String getOverridePolicy() {
        return overridePolicy;
    }

    public void setOverridePolicy(String overridePolicy) {
        this.overridePolicy = overridePolicy;
    }

    public boolean isShowOverridePolicyMessage() {
        return showOverridePolicyMessage;
    }

    public void setShowOverridePolicyMessage(boolean showOverridePolicyMessage) {
        this.showOverridePolicyMessage = showOverridePolicyMessage;
    }

    public boolean isConsolidateAllowed() {
        return consolidateAllowed;
    }

    public void setConsolidateAllowed(boolean consolidateAllowed) {
        this.consolidateAllowed = consolidateAllowed;
    }

    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startdate
     *            The startDate to set.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public String getStartDateStr() throws Exception {
        if (startDate != null) {
            return toGMTDateString(startDate);
        }
        else {
            return "";
        }

    }

    public void setStartDateStr(String str) throws Exception {
        if (str != null && !str.equals("")) {
            this.startDate = toGMTDate(str);
        }
    }

    /**
     * Added this method for DatePicker. When a date is displayed in the UI with a datepicker, the
     * jsp should set value to this form variable - startDateCalendarStr
     * 
     * @param str
     *            - the locale specific date string got from Calendar
     * @throws Exception
     */
    public void setStartDateCalendarStr(String str) throws Exception {
        this.setStartDateStr(str);
    }

    /**
     * Added this method for handling dates with datepicker. The Jsp should render the date string
     * using the form variable - startDateCalendarStr whenever the startDate is displayed with a
     * date picker
     * 
     * @return - mapped locale date string that datepicker accepts
     * @throws Exception
     */
    public String getStartDateCalendarStr() throws Exception {
        if (startDate != null) {
            return toDatePickerString(startDate);
        }
        else {
            return "";
        }

    }

    public String getNextHost() {
        return nextHost;
    }

    public void setNextHost(String nextHost) {
        this.nextHost = nextHost;
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public void setCurrentHost(String currentHost) {
        this.currentHost = currentHost;
    }

    public boolean isNeedVersionDate() {
        return needVersionDate;
    }

    public void setNeedVersionDate(boolean needVersionDate) {
        this.needVersionDate = needVersionDate;
    }

    public boolean isNeedVersionStartDate() {
        return needVersionStartDate;
    }

    public void setNeedVersionStartDate(boolean needVersionStartDate) {
        this.needVersionStartDate = needVersionStartDate;
    }

    public Date getVersionDate() {
        return versionDate;
    }

    public void setVersionDate(Date versionDate) {
        this.versionDate = versionDate;
    }

    public String getVersionDateStr() throws Exception {
        if (versionDate != null) {
            return toGMTDateString(versionDate);
        }
        else {
            return "";
        }

    }

    public void setVersionDateStr(String str) throws Exception {
        if (str != null && !str.equals("")) {
            this.versionDate = toGMTDate(str);
        }
    }

    public void setVersionDateCalendarStr(String str) throws Exception {
        this.setVersionDateStr(str);
    }

    public String getVersionDateCalendarStr() throws Exception {
        if (versionDate != null) {
            return toDatePickerString(versionDate);
        }
        else {
            return "";
        }

    }

    public Date getVersionStartDate() {
        return versionStartDate;
    }

    public void setVersionStartDate(Date versionStartDate) {
        this.versionStartDate = versionStartDate;
    }

    public String getVersionStartDateStr() throws Exception {
        if (versionStartDate != null) {
            return toGMTDateString(versionStartDate);
        }
        else {
            return "";
        }

    }

    public void setVersionStartDateStr(String str) throws Exception {
        if (str != null && !str.equals("")) {
            this.versionStartDate = toGMTDate(str);
        }
    }

    public void setVersionStartDateCalendarStr(String str) throws Exception {
        this.setVersionStartDateStr(str);
    }

    public String getVersionStartDateCalendarStr() throws Exception {
        if (versionStartDate != null) {
            return toDatePickerString(versionStartDate);
        }
        else {
            return "";
        }

    }

    public boolean isNeedSoldTo() {
        return needSoldTo;
    }

    public void setNeedSoldTo(boolean needSoldTo) {
        this.needSoldTo = needSoldTo;
    }

    public boolean isNeedOwner() {
        return needOwner;
    }

    public void setNeedOwner(boolean needOwner) {
        this.needOwner = needOwner;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isNeedCustomHost() {
        return needCustomHost;
    }

    public void setNeedCustomHost(boolean needCustomHost) {
        this.needCustomHost = needCustomHost;
    }

}
