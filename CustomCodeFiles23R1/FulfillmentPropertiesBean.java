package com.flexnet.operations.web.beans;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.flexnet.operations.api.IAttributeSet;
import com.flexnet.operations.api.IEntitlement;
import com.flexnet.operations.api.IEntitlementLineItem;
import com.flexnet.operations.api.IFulfillmentRecord;
import com.flexnet.operations.api.ILicenseTechnologyHostAttribute;
import com.flexnet.operations.api.IPartNumber;
import com.flexnet.operations.bizobjects.ActivationInstance;
import com.flexnet.operations.bizobjects.ActivationNodelockedHostUnit;
import com.flexnet.operations.bizobjects.CertificateActivationInstance;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.publicapi.ActivationTypeENC;
import com.flexnet.operations.publicapi.AttributeSet;
import com.flexnet.operations.publicapi.AttributeWhenENC;
import com.flexnet.operations.publicapi.CustomAttributeDescriptor;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.EntityStateENC;
import com.flexnet.operations.publicapi.FlexnetFulfillmentRecord;
import com.flexnet.operations.publicapi.FulfillmentLifeCycleStatusENC;
import com.flexnet.operations.publicapi.FulfillmentTypeENC;
import com.flexnet.operations.publicapi.HostType;
import com.flexnet.operations.publicapi.LicenseHostId;
import com.flexnet.operations.publicapi.LicenseModel;
import com.flexnet.operations.publicapi.LicenseTechnology;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OrganizationUnit;
import com.flexnet.operations.publicapi.Product;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.publicapi.TrustedHostId;
import com.flexnet.operations.services.FulfillmentInfoBean;
import com.flexnet.operations.services.LicenseAttributeService;
import com.flexnet.operations.services.LicenseTechnologyHostAttributeImpl;
import com.flexnet.operations.services.LicenseTechnologyImpl;
import com.flexnet.operations.trusted.TrustedActivationInstance;
import com.flexnet.operations.web.actions.product.FlexGeneratorHelper;
import com.flexnet.operations.web.forms.product.ProductBean;
import com.flexnet.operations.web.util.CommonUtils;
import com.flexnet.platform.bizobjects.Contact;
import com.flexnet.platform.bizobjects.ExtendedProperty;
import com.flexnet.platform.bizobjects.User;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.util.DateUtility;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.bizobjects.FNPTimeZoneDO;

public class FulfillmentPropertiesBean extends FulfillmentInfoBean implements java.io.Serializable {
    private static final int FULFILLID_LENGTH = 8;
    private static final int ORDERABLE_NAME_LENGTH = 16;

    private String id = "";
    private String fulfillId = "";
    private String fulfillmentType = "";
    private String fulfillmentSource = "";
    private String migrationId = "";
    private String vendorDaemonName = "";
    private String esn = "";
    private String lineItemObjId = "";
    private String orderableDescriptionFormatted = "";

    private String versionDate = "";
    private String activationDate = "";
    private String activationDateTime = "";
    private String startDate = "";
    private String expirationDate = "";
    private Map licenseFiles = new HashMap();
    private List nodeLockedHostIds = new ArrayList();
    private String lifeCycleStatus = "";
    private boolean trustedType = false;
    private boolean redundantServerLicense = false;
    private String status = "active";
    private String licenseHost = "";
    private String createdBy = "";
    private String createdByFirstName = "";
    private String createdByLastName = "";
    private String createdByOrgDisplayName = "";
    private List lmAttributes = new LinkedList();
    private Map lmAttributeVals = new HashMap();
    private Map lmAttributeHasValidVals = new HashMap();

    private String remainingCount = "";
    private String lastModifiedDateTime = "";
    private String activationType = "";
    // only for trusted activations
    private String osInfo = "";
    private String platformType = "";
    private String licenseTechnology = "";
    private String licenseTechnologyId = "";
    private String licenseFileType = "";

    private String entitlementObjId = "";
    private String entitlementId = "";
    private String parentFulfillmentObjId = "";
    private String parentFulfillmentId = "";

    private List hostAttributes = new LinkedList();
    private Map hostAttributeVals = new HashMap();

    private Boolean allowOneTS;
    private Boolean allowManyTS;

    private String trustedRequestId;

    public FulfillmentPropertiesBean(){}

    public FulfillmentPropertiesBean(
            IFulfillmentRecord ai, DateFormat dateFormat, Locale locale, boolean loadLicenseText){
        this(ai, dateFormat, locale);
        if (loadLicenseText) {
            this.licenseFiles = ai.getLicenseFiles();
        }
    }

	public FulfillmentPropertiesBean(IFulfillmentRecord ai,
			DateFormat dateFormat, Locale locale, boolean loadLicenseText,
			boolean loadLMAttributes) {
		this(ai, dateFormat, locale, loadLicenseText, loadLMAttributes, true);

	}
    
    public FulfillmentPropertiesBean(
            IFulfillmentRecord ai, DateFormat dateFormat, Locale locale, boolean loadLicenseText,
            boolean loadLMAttributes,boolean validateLMAttributes){
        this(ai, dateFormat, locale);
        if (loadLicenseText) {
            this.licenseFiles = ai.getLicenseFiles();
        }
        try {
            if (loadLMAttributes) {
                IEntitlementLineItem lineItem = (IEntitlementLineItem)ai.getLineItem();
                EntitlementLineItemBO liBO = (EntitlementLineItemBO)lineItem;
                LicenseModel lm = null;
                if (ai.isTrustedType()) {
                    lm = liBO.getLicenseModelByType(EntitlementLineItemBO.modelType.TRUSTED);
                }
                else {
                    lm = liBO
                            .getLicenseModelByType(EntitlementLineItemBO.modelType.CERTIFICATE_OR_CUSTOM);

                }
                licenseModel = lm.getName();

                Map macroValues = ((ActivationInstance)ai).getValuesForMacroSubstitution();
                // AttributeSet allAttrs = getAllAttributes(lm,
                // lineItem.getLicenseModelAttributes(), ai.getLicenseModelAttributes());
                AttributeSet allAttrs = LicenseAttributeService.resolveAttributes(lm,
                        lineItem.getLicenseModelAttributes(), ai.getLicenseModelAttributes(),null,
                        macroValues, validateLMAttributes);
                if (allAttrs != null) {
                    Iterator iter = allAttrs.getDescriptors().iterator();
                    while (iter.hasNext()) {
                        CustomAttributeDescriptor ldesc = (CustomAttributeDescriptor)iter.next();
                        String name = ldesc.getName();
                        if (!FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER.equals(name)) {
                            lmAttributes.add(name);
                        }
                        if (ldesc.getValidValues() != null && !ldesc.getValidValues().isEmpty())
                            lmAttributeHasValidVals.put(name, "true");
                        else
                            lmAttributeHasValidVals.put(name, "false");
                        if (ldesc.getType().isDateType()) {
                            Date d = allAttrs.getDateValue(ldesc);
                            String str = "";
                            if (d != null) {
                                dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                                str = dateFormat.format(d);
                            }
                            lmAttributeVals.put(name, str);
                        }
                        else if (ldesc.getType().isMultiValuedTextType()) {
                            List<String> lst = allAttrs.getListValue(ldesc);
                            if (lst != null && !lst.isEmpty()) {
                                List<String> newlst = new ArrayList<String>();
                                for (String valstr : lst) {
                                    String resourceStr = CommonUtils.getResourceString(name + "."
                                            + valstr + ".label", null, locale);
                                    if (resourceStr.startsWith("???"))
                                        resourceStr = valstr;
                                    newlst.add(resourceStr);
                                }
                                if (!newlst.isEmpty()) {
                                    lmAttributeVals.put(name,
                                            ExtendedProperty.getStringFromStringList(newlst));
                                }
                                else {
                                    lmAttributeVals.put(ldesc.getName(),
                                            allAttrs.getStringValue(ldesc));
                                }
                            }
                            else {
                                lmAttributeVals
                                        .put(ldesc.getName(), allAttrs.getStringValue(ldesc));
                            }
                        }
                        else if (FlexGeneratorHelper.OVERDRAFT_MAX.equals(ldesc.getName())) {
                            if (allAttrs.getStringValue(ldesc) != null) {
                                lmAttributeVals.put(ldesc.getName(),
                                        Integer.toString(ai.getLineItem().getOverdraftMax()));
                            }
                            else if (ai.getLineItem().getOverdraftMax() == Integer.MAX_VALUE) {
                                lmAttributeVals.put(ldesc.getName(), CommonUtils.getResourceString(
                                        FlexGeneratorHelper.WHEN_UNLIMITED, null,
                                        ThreadContextUtil.getLocale()));
                            }
                        }
                        else if (FlexGeneratorHelper.OVERDRAFT_CEILING.equals(ldesc.getName())) {
                            if (allAttrs.getStringValue(ldesc) != null) {
                                if (ai.getLineItem().isOverdraftCeilingPercent()) {
                                    lmAttributeVals.put(ldesc.getName(),
                                            (allAttrs.getStringValue(ldesc) + " %"));
                                }
                                else {
                                    lmAttributeVals.put(ldesc.getName(),
                                            allAttrs.getStringValue(ldesc));
                                }
                            }
                            else if (!(ai.getLineItem().getOverdraftMax() == 0)
                                    && (ai.getLineItem().getOverdraftCeiling() == Integer.MAX_VALUE)) {
                                lmAttributeVals.put(ldesc.getName(), CommonUtils.getResourceString(
                                        FlexGeneratorHelper.WHEN_UNLIMITED, null,
                                        ThreadContextUtil.getLocale()));
                            }
                        }
                        else if (FlexGeneratorHelper.OVERDRAFT_FLOOR.equals(ldesc.getName())) {
                            if (allAttrs.getStringValue(ldesc) != null) {
                                if (ai.getLineItem().isOverdraftFloorPercent()) {
                                    lmAttributeVals.put(ldesc.getName(),
                                            (allAttrs.getStringValue(ldesc) + " %"));
                                }
                                else {
                                    lmAttributeVals.put(ldesc.getName(),
                                            allAttrs.getStringValue(ldesc));
                                }
                            }
                            else if (!(ai.getLineItem().getOverdraftMax() == 0)
                                    && ai.getLineItem().getOverdraftFloor() == 0) {
                                lmAttributeVals.put(ldesc.getName(), "0");
                            }
                        }
                        else if (FlexGeneratorHelper.VM_PLATFORMS.equals(ldesc.getName())) {
                            if (allAttrs.getStringValue(ldesc) != null) {
                                if (FlexGeneratorHelper.PHYSICAL.equals(allAttrs
                                        .getStringValue(ldesc))) {
                                    lmAttributeVals.put(FlexGeneratorHelper.VM_PLATFORMS,
                                            FlexGeneratorHelper.RESTRICTED_TO_PHYSICAL);
                                }
                                else if (FlexGeneratorHelper.VM_ONLY.equals(allAttrs
                                        .getStringValue(ldesc))) {
                                    lmAttributeVals.put(FlexGeneratorHelper.VM_PLATFORMS,
                                            FlexGeneratorHelper.RESTRICTED_TO_VIRTUAL);
                                }
                            }
                            else
                                lmAttributeVals.put(FlexGeneratorHelper.VM_PLATFORMS,
                                        FlexGeneratorHelper.NOT_USED);
                        }
                        else if (FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER.equals(ldesc
                                .getName())) {
                            allowOneTS = new Boolean("false");
                            if ("true".equals(allAttrs.getStringValue(ldesc))) {
                                allowOneTS = new Boolean("true");
                            }
                        }
                        else if (FlexGeneratorHelper.ALLOW_TERMINAL_SERVER.equals(ldesc.getName())) {
                            allowManyTS = new Boolean("false");
                            if ("true".equals(allAttrs.getStringValue(ldesc))) {
                                allowManyTS = new Boolean("true");
                            }
                        }
                        else {
                            lmAttributeVals.put(ldesc.getName(), allAttrs.getStringValue(ldesc));
                        }
                        if (allowOneTS != null && allowManyTS != null) {
                            if (!allowOneTS && !allowManyTS) {
                                lmAttributeVals.put(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER,
                                        FlexGeneratorHelper.NOT_USED);
                            }
                            else if (allowOneTS && !allowManyTS) {
                                lmAttributeVals.put(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER,
                                        FlexGeneratorHelper.ONE_CONNECTION);
                            }
                            else if (!allowOneTS && allowManyTS) {
                                lmAttributeVals.put(FlexGeneratorHelper.ALLOW_TERMINAL_SERVER,
                                        FlexGeneratorHelper.MANY_CONNECTIONS);
                            }
                        }
                    }
                }
            }
            /*AttributeSet modelTimeAttrs = lm.getModelTimeAttributes();
            if (modelTimeAttrs != null) {
                Iterator iter = modelTimeAttrs.getDescriptors().iterator();
                while (iter.hasNext()) {
                    CustomAttributeDescriptor ldesc = (CustomAttributeDescriptor)iter.next();
                    String name = ldesc.getName();
                    lmAttributes.add(name);
                    if (ldesc.getType().isDateType()) {
                        Date d = modelTimeAttrs.getDateValue(ldesc);
                        String str = "";
                        if (d != null) {
                            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                            str = dateFormat.format(d);
                        }
                        lmAttributeVals.put(name, str);
                    } else {
                        String val = modelTimeAttrs.getStringValue(ldesc);
                        if(val != null) {
                            String value = LicenseAttributeService.substituteMacros(val, allAttrs, macroValues);
                            lmAttributeVals.put(name, value);
                        }
                    }
                }
            }
            AttributeSet entTimeAttrs = lineItem.getLicenseModelAttributes();
            if (entTimeAttrs != null) {
                Iterator iter = entTimeAttrs.getDescriptors().iterator();
                while (iter.hasNext()) {
                    CustomAttributeDescriptor ldesc = (CustomAttributeDescriptor)iter.next();
                    String name = ldesc.getName();
                    lmAttributes.add(name);
                    if (ldesc.getType().isDateType()) {
                        Date d = entTimeAttrs.getDateValue(ldesc);
                        String str = "";
                        if (d != null) {
                            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                            str = dateFormat.format(d);
                        }
                        lmAttributeVals.put(name, str);
                    } else {
                        String val = entTimeAttrs.getStringValue(ldesc);
                        if(val != null) {
                            String value = LicenseAttributeService.substituteMacros(val, allAttrs, macroValues);
                            lmAttributeVals.put(name, value);
                        }
                    }
                }
            }
            AttributeSet fulfillTimeAttrs = ai.getLicenseModelAttributes();
            if (fulfillTimeAttrs != null) {
                Iterator iter = fulfillTimeAttrs.getDescriptors().iterator();
                while (iter.hasNext()) {
                    CustomAttributeDescriptor ldesc = (CustomAttributeDescriptor)iter.next();
                    String name = ldesc.getName();
                    lmAttributes.add(name);
                    if (ldesc.getType().isDateType()) {
                        Date d = fulfillTimeAttrs.getDateValue(ldesc);
                        String str = "";
                        if (d != null) {
                            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                            str = dateFormat.format(d);
                        }
                        lmAttributeVals.put(name, str);
                    } else {
                        String val = fulfillTimeAttrs.getStringValue(ldesc);
                        if(val != null) {
                            String value = LicenseAttributeService.substituteMacros(val, allAttrs, macroValues);
                            lmAttributeVals.put(name, value);
                        }
                    }
                }
            }
            }*/
        }
        catch (OperationsException ex) {
            ex.printStackTrace();
        }
    }

    public FulfillmentPropertiesBean(
            IFulfillmentRecord ai, DateFormat dateFormat, Locale locale){
        try {
            id = ai.getId().toString();
            fulfillId = ai.getFulfillmentId();
            migrationId = ai.getMigrationID();
            ActivationTypeENC activationTypeENC = ai.getActivationTypeENC();
            if (activationTypeENC != null) {
                activationType = activationTypeENC.getDesc();
            }

            IEntitlementLineItem lineItem = (IEntitlementLineItem)ai.getLineItem();
            IEntitlement et = (IEntitlement)lineItem.getParentEntitlement();
            if (CommonUtils.canShowEntitlementId(et))
                esn = et.getEntitlementID();
            else
                esn = "";

            lineItemId = lineItem.getActivationID();
            lineItemObjId = lineItem.getId().toString();
            lineItemState = lineItem.getLineItemState();

            Map<Product, Integer> prods = lineItem.getEntitledProducts();
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
                entitledOrderables = entitledProds;
            }
            if (lineItem.getPartNumber() != null) {
                IPartNumber sku = (IPartNumber)lineItem.getPartNumber();
                partNumber = sku.getPartNumber();
                if (sku.getDescription() != null) {
                    partNumberDescription = sku.getDescription();
                }
            }
            shipToEmail = ai.getShipToEmail();
            shipToAddress = ai.getShipToAddress();

            OrganizationUnit soldtoorg = lineItem.getParentEntitlement().getSoldTo();
            soldTo = soldtoorg.getDisplayName();
            soldToName = soldtoorg.getName();
            soldToObjId = soldtoorg.getId().toString();
            LicenseTechnology licTech = ai.getLineItem().getLicenseTechnology();
            licenseTechnology = licTech.getName();
            licenseModel = lineItem.getLicenseModel().getName();
            // DO NOT LOAD licenseText for the Landing Pages
            if (licTech.isFlexNet()) {
                FlexnetFulfillmentRecord flexFulfillment = (FlexnetFulfillmentRecord)ai;
                // if (!flexFulfillment.isTrustedType())
                licenseHost = flexFulfillment.getLicenseHostId().getUniqueHostName();
                licenseHostId = flexFulfillment.getLicenseHostId().getUniqueId().toString();
                if (flexFulfillment.getLicenseHostId() instanceof ServerHostId) {
                    ServerHostId serverHost = (ServerHostId)flexFulfillment.getLicenseHostId();
                    if (serverHost.isRedundantServer()) {
                        redundantServerLicense = true;
                    }
                }
                if (flexFulfillment.isTrustedType()) {
                    TrustedHostId tHost = (TrustedHostId)flexFulfillment.getLicenseHostId();
                    platformType = tHost.getPlatformType();
                    osInfo = tHost.getOSInfo();
                }

                Iterator iter = ((ActivationInstance)flexFulfillment).getNodelockedHostUnits()
                        .iterator();
                while (iter.hasNext()) {
                    ActivationNodelockedHostUnit actNodeUnit = (ActivationNodelockedHostUnit)iter
                            .next();
                    nodeLockedHostIds.add(actNodeUnit.getNodelockedHostUnit().getHostId());
                }

                /*              NodeLockedHostId[] ids = flexFulfillment.getNodeLockedHostIds();
                                for(int i = 0; i < ids.length; ++i)
                                {
                                    NodeLockedHostId hid = ids[i];
                                    nodeLockedHostIds.add(hid.getUniqueHostName());
                                }
                */
            }
            else {
                LicenseHostId licHost = ai.getLicenseHostId();
                if (licHost != null) {
                    licenseHost = licHost.getUniqueHostName();
                    licenseHostId = licHost.getUniqueId().toString();

                    // populate the host attributes and values
                    CustomHostId customHost = (CustomHostId)licHost;
                    Map attrVals = customHost.getHostAttributeValues();
                    HostType ht = customHost.getLicenseTechnologyHostType();
                    Iterator iter = attrVals.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry entry = (Map.Entry)iter.next();
                        String name = (String)entry.getKey();
                        String val = (String)entry.getValue();
                        hostAttributes.add(name);

                        LicenseTechnologyImpl ltimpl = (LicenseTechnologyImpl)licTech;
                        ILicenseTechnologyHostAttribute hAttr = LicenseTechnologyHostAttributeImpl
                                .findHostAttribute(ltimpl.getLicenseTechnology(), ht, name);

                        if (hAttr != null && hAttr.getType().isDateType()) {
                            try {
                                DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                                        DateFormat.MEDIUM);
                                Date dt = df.parse(val);
                                dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
                                val = dateFormat.format(dt);
                            }
                            catch (Exception ex) {}
                        }
                        hostAttributeVals.put(name, val);
                    }
                }
            }
            lifeCycleStatus = "master";

            if (ai instanceof TrustedActivationInstance)
                trustedType = true;
            else if (ai instanceof CertificateActivationInstance)
                trustedType = false;

            FulfillmentLifeCycleStatusENC lifecycleStatus = ai.getLifeCycleStatus();

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
                    && lifecycleStatus.equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_RENEW)) {
                lifeCycleStatus = "renew";
            }
            else if (lifecycleStatus != null
                    && lifecycleStatus
                            .equals(FulfillmentLifeCycleStatusENC.LIFECYCLE_STATUS_REINSTALL)) {
                lifeCycleStatus = "reinstall";
            }
            DateFormat df = DateFormat
                    .getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, locale);
            df.setTimeZone(dateFormat.getTimeZone());
            activationDateTime = df.format(ai.getFulfillDate());

            lastModifiedDateTime = df.format(ai.getLastModified());

            activationDate = dateFormat.format(ai.getFulfillDate());
            /*
             * startdate and ExpirationDate always stored as GMT 12:00:00 AM
             * in the database and they should be displayed without applying
             * user's timezone.
             */
            dateFormat.setTimeZone(DateUtility.getGMTTimeZone());
            if (ai.getStartDate() != null) {
                startDate = dateFormat.format(ai.getStartDate());
            }
            if (ai.getExpirationDate() != null) {
                expirationDate = dateFormat.format(ai.getExpirationDate());
            }
            else {
                if (lineItem.isPermanent()) {
                    String permanentStr = CommonUtils.getResourceString(
                            "entitlement.expiration.permanent", null, locale);
                    expirationDate = permanentStr;
                }
                else {
                    String unknownStr = CommonUtils.getResourceString(
                            "entitlement.expiration.unknown", null, locale);
                    expirationDate = unknownStr;
                }
            }
            fulfillAmount = ai.getFulfillmentCount();
            overdraftCount = ai.getOverdraftCount();
            fulfillmentType = ((ActivationInstance)ai).getFulfillmentType();
            if (ai.getState().equals(EntityStateENC.OBSOLETE)) {
                status = "obsolete";
            }
            else if (ai.getState().equals(EntityStateENC.ON_HOLD)) {
                status = "hold";
            }
            else if (ai.getState().equals(EntityStateENC.ACTIVE)) {
                status = "active";
            }
            createdBy = ai.getCreatedBy();
            if (createdBy != null) {
                ActivationInstance ainst = (ActivationInstance)ai;
                User user = ainst.getCreatedUser();
                if (user != null) {
                    Contact contact = user.getContactInfo();
                    this.createdByFirstName = contact.getFirstName();
                    this.createdByLastName = contact.getLastName();
                    if (contact.getBelongsTo() != null)
                        this.createdByOrgDisplayName = contact.getBelongsTo().getDisplayName();
                }
            }
            if (!fulfillmentType.equals(ActivationInstance.TRUSTED_TYPE)) {
                ActivationInstance ainst = (ActivationInstance)ai;
                if (ainst.getTimeZoneValue() != null) {
                	if(ainst.isViewTimeZoneUI()){
                		FNPTimeZoneDO tz1= (FNPTimeZoneDO)ainst.getTimeZoneValue();
                		 setFNPTimeZone(ainst.getTimeZoneValue().getName()+" ( Server: "+tz1.getIsServed_()+", Client: "+ tz1.getIsClient_()+")");
                	}else{
                    setFNPTimeZone(ainst.getTimeZoneValue().getName());
                	}
                }
                else if (lineItem.getTimeZone() != null) {
                	if(ainst.isViewTimeZoneUI()){
                    	FNPTimeZoneDO tz1= (FNPTimeZoneDO)lineItem.getTimeZone();
                        setFNPTimeZone(lineItem.getTimeZone().getName()+" ( Server: "+tz1.getIsServed_()+", Client: "+ tz1.getIsClient_()+")");
                	}else{
                		setFNPTimeZone(lineItem.getTimeZone().getName());
                	}
                }
                else if (lineItem.getLicenseModel().getTimeZoneValue() != null) {
                	if(ainst.isViewTimeZoneUI()){
                	FNPTimeZoneDO tz1= (FNPTimeZoneDO)lineItem.getLicenseModel().getTimeZoneValue();
                    setFNPTimeZone(lineItem.getLicenseModel().getTimeZoneValue().getName()+" ( Server: "+tz1.getIsServed_()+", Client: "+ tz1.getIsClient_()+")");
                	}
                	else{
                	setFNPTimeZone(lineItem.getLicenseModel().getTimeZoneValue().getName());
                	}
                }
            }
        }
        catch (OperationsException ex) {
            ex.printStackTrace();
        }
        catch (FlexnetBaseException e) {
            e.printStackTrace();
        }
    }

    public void setCustomAttr(String attrName, String value) {
        customAttrs.put(attrName, value);
    }

    public void setCustomHostAttr(String attrName, String value) {
        customHostAttrs.put(attrName, value);
    }

    public void setShipToEmail(String email) {
        shipToEmail = email;
    }

    public void setShipToAddress(String addr) {
        shipToAddress = addr;
    }

    public String getLicenseHost() {
        return licenseHost;
    }

    public void setLicenseHost(String licenseHost) {
        this.licenseHost = licenseHost;
    }

    public void setNodeLockedHostIds(List nodeLockedHostIds) {
        this.nodeLockedHostIds = nodeLockedHostIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setActivationDate(String activationDate) {
        this.activationDate = activationDate;
    }

    public void setVersionDate(String activationDate) {
        this.versionDate = activationDate;
    }

    public boolean isTrustedType() {
        return trustedType;
    }

    public void setTrustedType(boolean trustedType) {
        this.trustedType = trustedType;
    }

    public String getType() {
        if (getStatus().equals("obsolete") | getStatus().equals("inactive")) {
            return "I";
        }
        else if (getStatus().equals("hold")) {
            return "H";
        }
        else {
            if (FulfillmentTypeENC.NF.getName().equals(getFulfillmentType()))
                return FulfillmentTypeENC.NF.toString();
            else if (isTrustedType()) {
                return FulfillmentTypeENC.T.toString();
            }
            else {
                return FulfillmentTypeENC.C.toString();
            }
        }
    }

    public String getSoldTo() {
        return soldTo;
    }

    public void setSoldTo(String soldTo) {
        this.soldTo = soldTo;
    }

    public String getEsn() {
        return esn;
    }

    public void setEsn(String esn) {
        this.esn = esn;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("id=[").append(id).append("]");
        buf.append("esn=[").append(esn).append("]");
        return buf.toString();
    }

    public void setFulfillAmount(int fulfillAmount) {
        this.fulfillAmount = fulfillAmount;
    }

    public String getMigrationId() {
        return migrationId;
    }

    public void setMigrationId(String id) {
        migrationId = id;
    }

    public String getFulfillId() {
        return fulfillId;
    }

    public void setFulfillId(String fulfillId) {
        this.fulfillId = fulfillId;
    }

    public String getActivationDate() {
        return activationDate;
    }

    public String getVersionDate() {
        return versionDate;
    }

    public void setActivationDateStr(String actDate) {
        this.activationDate = actDate;
    }

    public String getActivationDateStr() throws Exception {
        return activationDate;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getStartDateStr() throws Exception {
        return startDate;

    }

    /**
     * @return Returns the lineItemId.
     */
    public String getLineItemId() {
        return lineItemId;
    }

    /**
     * @param lineItemId
     *            The lineItemId to set.
     */
    public void setLineItemId(String lineItemId) {
        this.lineItemId = lineItemId;
    }

    /**
     * @return Returns the lifeCycleStatus.
     */
    public String getLifeCycleStatus() {
        return lifeCycleStatus;
    }

    /**
     * @param lifeCycleStatus
     *            The lifeCycleStatus to set.
     */
    public void setLifeCycleStatus(String lifeCycleStatus) {
        this.lifeCycleStatus = lifeCycleStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLineItemObjId() {
        return lineItemObjId;
    }

    public void setLineItemObjId(String lineItemObjId) {
        this.lineItemObjId = lineItemObjId;
    }

    public List getNodeLockedHostIds() {
        return nodeLockedHostIds;
    }

    public void addNodeLockedHostId(String hostId) {
        this.nodeLockedHostIds.add(hostId);
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public void setPartNumberDescription(String partNumberDescription) {
        this.partNumberDescription = partNumberDescription;
    }

    public void setLicenseModel(String licenseModel) {
        this.licenseModel = licenseModel;
    }

    public List getLmAttributes() {
        return lmAttributes;
    }

    public void setLmAttributes(List lmAttributes) {
        this.lmAttributes = lmAttributes;
    }

    public Map getLmAttributeVals() {
        return lmAttributeVals;
    }

    public void setLmAttributeVals(Map lmAttributeVals) {
        this.lmAttributeVals = lmAttributeVals;
    }

    public String getLmAttributeVal(String name) {
        if (this.lmAttributeVals.get(name) != null)
            return (String)this.lmAttributeVals.get(name);
        return "";
    }

    public List getHostAttributes() {
        return hostAttributes;
    }

    public void setHostAttributes(List hostAttributes) {
        this.hostAttributes = hostAttributes;
    }

    public Map getHostAttributeVals() {
        return hostAttributeVals;
    }

    public void setHostAttributeVals(Map hostAttributeVals) {
        this.hostAttributeVals = hostAttributeVals;
    }

    public String getHostAttributeVal(String name) {
        if (this.hostAttributeVals.get(name) != null)
            return (String)this.hostAttributeVals.get(name);
        return "";
    }

    public void setLicenseHostId(String licenseHostId) {
        this.licenseHostId = licenseHostId;
    }

    public String getOrderableDescriptionFormatted() {
        return orderableDescriptionFormatted;
    }

    public void setOrderableDescriptionFormatted(String orderableDescriptionFormatted) {
        this.orderableDescriptionFormatted = orderableDescriptionFormatted;
    }

    public void setOrderId(String id) {
        orderId = id;
    }

    public void setOrderLineNum(String id) {
        orderLineNum = id;
    }

    public void setLineItemDescription(String id) {
        lineItemDescription = id;
    }

    public void setSeatCount(String count) {
        seatCount = count;
    }

    public String getSeatCount() {
        return seatCount;
    }

    public String getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(String count) {
        remainingCount = count;
    }

    public String getCreatedByFirstName() {
        return createdByFirstName;
    }

    public void setCreatedByFirstName(String createdByFirstName) {
        this.createdByFirstName = createdByFirstName;
    }

    public String getCreatedByLastName() {
        return createdByLastName;
    }

    public void setCreatedByLastName(String createdByLastName) {
        this.createdByLastName = createdByLastName;
    }

    public String getCreatedByOrgDisplayName() {
        return createdByOrgDisplayName;
    }

    public void setCreatedByOrgDisplayName(String createdByOrgDisplayName) {
        this.createdByOrgDisplayName = createdByOrgDisplayName;
    }

    public String getActivationDateTime() {
        return activationDateTime;
    }

    public void setActivationDateTime(String activationDateTime) {
        this.activationDateTime = activationDateTime;
    }

    public String getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public String getActivationType() {
        return activationType;
    }

    public void setActivationType(String activationType) {
        this.activationType = activationType;
    }

    public boolean isRedundantServerLicense() {
        return redundantServerLicense;
    }

    public void setRedundantServerLicense(boolean redundantServerLicense) {
        this.redundantServerLicense = redundantServerLicense;
    }

    public static AttributeSet getAllAttributes(LicenseModel modelImpl,
            AttributeSet entitlementParams, AttributeSet fulfillmentParams)
            throws OperationsException {
        IAttributeSet result = (IAttributeSet)modelImpl.getLicenseTechnology()
                .getLicenseAttributes();
        result.setAllowEmptyValues(true);
        AttributeSet modelAttrs = modelImpl.getModelTimeAttributes();

        Iterator iter = result.getDescriptors().iterator();
        while (iter.hasNext()) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)iter.next();
            AttributeWhenENC when = modelImpl.getAttributeWhen(desc);
            if (when.isModelTime())
                result.setValue(desc, modelAttrs.getStringValue(desc));
            else if (when.isEntitlementTime())
                result.setValue(desc, entitlementParams.getStringValue(desc));
            else if (when.isFulfillmentTime())
                result.setValue(desc, fulfillmentParams.getStringValue(desc));
        }
        return result;
    }

    public void setLineItemState(EntityStateENC lineItemState) {
        this.lineItemState = lineItemState;
    }

    public String getLineItemStatus() {
        if (lineItemState != null) {
            if (lineItemState.equals(EntityStateENC.DRAFT)) {
                return "draft";
            }
            else if (lineItemState.equals(EntityStateENC.DEPLOYED)) {
                return "deployed";
            }
            else if (lineItemState.equals(EntityStateENC.INACTIVE)) {
                return "inactive";
            }
            else if (lineItemState.equals(EntityStateENC.OBSOLETE)) {
                return "obsolete";
            }
            else if (lineItemState.equals(EntityStateENC.TEST)) {
                return "test";
            }
            else if (lineItemState.equals(EntityStateENC.ACTIVE)) {
                return "active";
            }
        }
        return "";
    }

    public String getLicenseTechnology() {
        return licenseTechnology;
    }

    public void setLicenseTechnology(String licenseTechnology) {
        this.licenseTechnology = licenseTechnology;
    }

    public String getPlatformType() {
        return platformType;
    }

    public String getOperatingSystemInfo() {
        return osInfo;
    }

    public String getFulfillmentType() {
        if (ActivationInstance.CERTIFICATE_TYPE.equals(fulfillmentType))
            return "CERTIFICATE";
        else if (ActivationInstance.TRUSTED_TYPE.equals(fulfillmentType))
            return "TRUSTED";
        else if (ActivationInstance.CUSTOM_TYPE.equals(fulfillmentType))
            return "CUSTOM";
        else
            return fulfillmentType;
    }

    public void setFulfillmentType(String fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public String getEntitlementId() {
        return entitlementId;
    }

    public void setEntitlementId(String entitlementId) {
        this.entitlementId = entitlementId;
    }

    public String getEntitlementObjId() {
        return entitlementObjId;
    }

    public void setEntitlementObjId(String entitlementObjId) {
        this.entitlementObjId = entitlementObjId;
    }

    public String getLicenseTechnologyId() {
        return licenseTechnologyId;
    }

    public void setLicenseTechnologyId(String licenseTechnologyId) {
        this.licenseTechnologyId = licenseTechnologyId;
    }

    public String getFulfillmentSource() {
        return fulfillmentSource;
    }

    public void setFulfillmentSource(String fulfillmentSource) {
        this.fulfillmentSource = fulfillmentSource;
    }

    public String getVendorDaemonName() {
        return vendorDaemonName;
    }

    public void setVendorDaemonName(String vendorDaemonName) {
        this.vendorDaemonName = vendorDaemonName;
    }

    public String getParentFulfillmentId() {
        return parentFulfillmentId;
    }

    public void setParentFulfillmentId(String parentFulfillmentId) {
        this.parentFulfillmentId = parentFulfillmentId;
    }

    public String getParentFulfillmentObjId() {
        return parentFulfillmentObjId;
    }

    public void setParentFulfillmentObjId(String parentFulfillmentObjId) {
        this.parentFulfillmentObjId = parentFulfillmentObjId;
    }

    public Map getLicenseFiles() {
        return licenseFiles;
    }

    public void setLicenseFiles(Map licenseFiles) {
        this.licenseFiles = licenseFiles;
    }

    public String getLicenseFileType() {
        return licenseFileType;
    }

    public void setLicenseFileType(String licenseFileType) {
        this.licenseFileType = licenseFileType;
    }

    public String getTrustedRequestId() {
        return trustedRequestId;
    }

    public void setTrustedRequestId(String trustedRequestId) {
        this.trustedRequestId = trustedRequestId;
    }

    public Map getLmAttributeHasValidVals() {
        return lmAttributeHasValidVals;
    }

    public void setLmAttributeHasValidVals(Map lmAttributeHasValidVals) {
        this.lmAttributeHasValidVals = lmAttributeHasValidVals;
    }
}
