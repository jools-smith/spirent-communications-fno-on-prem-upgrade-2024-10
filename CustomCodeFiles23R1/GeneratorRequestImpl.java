
package com.flexnet.operations.services;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flexnet.operations.api.ICustomHostId;
import com.flexnet.operations.api.IEntitlementLineItem;
import com.flexnet.operations.api.IProduct;
import com.flexnet.operations.bizobjects.ActivationInstance;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.bizobjects.entitlements.EntitlementProductBO;
import com.flexnet.operations.exceptions.OPSBaseException;
import com.flexnet.operations.publicapi.AttributeSet;
import com.flexnet.operations.publicapi.AttributeWhenENC;
import com.flexnet.operations.publicapi.CustomAttribute;
import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.EntitlementLineItem;
import com.flexnet.operations.publicapi.Feature;
import com.flexnet.operations.publicapi.FulfillmentRequestTypeENC;
import com.flexnet.operations.publicapi.GeneratorRequest;
import com.flexnet.operations.publicapi.GeneratorResponse;
import com.flexnet.operations.publicapi.LicenseFileDefinition;
import com.flexnet.operations.publicapi.LicenseGeneratorConfig;
import com.flexnet.operations.publicapi.LicenseModel;
import com.flexnet.operations.publicapi.LicenseTechnology;
import com.flexnet.operations.publicapi.LicensedItem;
import com.flexnet.operations.publicapi.NodeLockedHostId;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OrgUnitUser;
import com.flexnet.operations.publicapi.OrganizationUnit;
import com.flexnet.operations.publicapi.PartNumber;
import com.flexnet.operations.publicapi.Product;
import com.flexnet.operations.publicapi.ServerHostId;
import com.flexnet.operations.services.bulkOperations.FulfillmentRecordProxy;
import com.flexnet.operations.webservices.WSCommonUtils;
import com.flexnet.platform.bizobjects.User;
import com.flexnet.platform.customattribute.CustomAttributeService;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.exceptions.FlexnetHibernateException;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.publicapi.FNPTimeZone;
import com.flexnet.products.publicapi.LicenseFileTypeENC;

import clover.org.apache.commons.lang3.StringUtils;

public class GeneratorRequestImpl implements GeneratorRequest {
	private static final String VENDOR_STRING = "VENDOR_STRING";
    private static final String FEATURE_VERSION="{Feature.version}";
    public final static String SOLD_TO_DISPLAY_NAME = "SoldTo.displayName";
    public final static String SOLD_TO_NAME = "SoldTo.name";
    public final static String ORDER_ID = "EntitlementLineItem.orderId";
    public final static String ORDER_LINE_NUMBER = "EntitlementLineItem.orderLineNumber";
    public final static String ACTIVATION_ID = "EntitlementLineItem.activationId";
    public final static String SHIP_TO_ADDRESS = "Entitlement.shipToAddress";
    public final static String PRODUCT_NAME = "EntitlementLineItem.productName";
    public final static String PRODUCT_VERSION = "EntitlementLineItem.productVersion";
    public final static String PART_NUMBER = "EntitlementLineItem.partNumber";
    public final static String FULFILLMENT_ID = "Fulfillment.id";
    public final static String ENTITLEMENT_ID = "Entitlement.id";
    public final static String FEATURE_VER = "Feature.version";

    private Date startDate, expirationDate;
    private int fulfillCount, overdraftCount;
    private AttributeSet inputAttributes;
    NodeLockedHostId[] nodeLockHostIds;
    private ServerHostId serverIds;

    private String activationID;
    private String orderId;
    private String orderLineNumber;
    private String shipToAddress;
    private String fulfillmentId;
    private AttributeSet orderAttributes;
    private AttributeSet fulfillAttributes;
    private Date versionDate;
    private Product product;
    private PartNumber partNumber;
    private LicenseModel licenseModel;
    private OrganizationUnit soldTo;
    private FulfillmentRequestTypeENC requestType;
    private Map bizValues;
    private Set supersededFeatures;
    private String entitlementId;
    private String shipToEmail;
    private Set supersedeSignSet;
    private ActivationInstance ai;
    private CustomHostId customHost;
    private LicenseFileTypeENC licenseFileType;
    private boolean isVerifyRequest;
    private LicenseTechnology licenseTechnology;
    private Map<Product, Integer> entitledProducts;
    private FNPTimeZone timeZone;
    private boolean isVendorStringReplaceWithFeatureVersion = false;
    private String featureVersion;

    private LicenseGeneratorConfig lcg;

    private Map parentFulfillments = new HashMap();
    private Set licenseFileDefinitions = new HashSet();

    public GeneratorRequestImpl(
            ActivationInstance fulfillment, FulfillmentRequestTypeENC request,
            EntitlementLineItemBO.modelType mdlt)
            throws OperationsException{
        ai = fulfillment;
        EntitlementLineItem lineItem = fulfillment.getLineItem();
        EntitlementLineItemBO lineItemBO = (EntitlementLineItemBO)lineItem;
        fulfillmentId = fulfillment.getFulfillmentId();
        setEntitlementId(lineItem.getParentEntitlement().getEntitlementID());
        setActivationID(lineItem.getActivationID());
        setSoldTo(lineItem.getParentEntitlement().getSoldTo());
        setStartDate(fulfillment.getStartDate());
        setExpirationDate(fulfillment.getExpirationDate());
        setOrderAttributes(lineItem.getLicenseModelAttributes());
        if(lineItem.getLicenseModelAttributes()!= null && lineItem.getLicenseModelAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion(lineItem.getLicenseModelAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
        setOrderId(lineItem.getOrderId());
        setOrderLineNumber(lineItem.getOrderLineNumber());
        setVersionDate(lineItem.getVersionDate());
        setProduct(lineItem.getProduct());
        setEntitledProducts(lineItem.getEntitledProducts());
        setPartNumber(lineItem.getPartNumber());
        setLicenseGeneratorConfiguration(((IEntitlementLineItem)lineItem)
                .getLicenseGeneratorConfiguration());
        setLicenseModel(lineItemBO.getLicenseModelByType(mdlt));
        if( getLicenseModel().getModelTimeAttributes()!= null &&  getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion( getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
        setShipToAddress(lineItem.getParentEntitlement().getShipToAddress());
        setRequestType(request);
        setFulfillAttributes(fulfillment.getLicenseModelAttributes());
        if(fulfillment.getLicenseModelAttributes()!= null && fulfillment.getLicenseModelAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion(fulfillment.getLicenseModelAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
       
        setSupersededFeatures(fulfillment.getSupersededFeaturesSet());
        setShipToEmail(fulfillment.getShipToEmail());
        setSupersedeSignSet(fulfillment.getSupersedeSignSet());
        setCustomHost(fulfillment.getCustomHostId());
        setLicenseFileType(fulfillment.getLicenseFileType());
        setLicenseFileDefinitions(fulfillment.getLicenseTechnology().getLicenseFileDefinitions());
        setLicenseTechnology(fulfillment.getLicenseTechnology());

        setStartDate(fulfillment.getStartDate());
        setExpirationDate(fulfillment.getExpirationDate());
        setFulfillCount(fulfillment.getFulfillmentCount());
        setOverdraftCount(fulfillment.getOverdraftCount());
        if (fulfillment.getNodeLockedHostIds() != null) {
            NodeLockedHostId[] hosts = fulfillment.getNodeLockedHostIds();
            setNodeLockHostIds(hosts);
        }
        if (fulfillment.getServerHostId() != null) {
            setServerIds(fulfillment.getServerHostId());
        }
        if (fulfillment.getCustomHostId() != null) {
            setCustomHost(fulfillment.getCustomHostId());
        }
        LicenseModel mdl = getLicenseModel();
        if (mdl.getTimeZoneWhen() != null
                && mdl.getTimeZoneWhen().equals(AttributeWhenENC.MODEL_TIME)) {
            setTimeZone(mdl.getTimeZoneValue());
        }
        else if (mdl.getTimeZoneWhen() != null
                && mdl.getTimeZoneWhen().equals(AttributeWhenENC.ENTITLEMENT_TIME)) {
            setTimeZone(lineItemBO.getTimeZone());
        }
        else if (mdl.getTimeZoneWhen() != null
                && mdl.getTimeZoneWhen().equals(AttributeWhenENC.FULFILLMENT_TIME)) {
            setTimeZone(fulfillment.getTimeZoneValue());
        }

    }
    public boolean isVendorStringReplaceWithFeatureVersion() {
  		return isVendorStringReplaceWithFeatureVersion;
  	}

  	public void setVendorStringReplaceWithFeatureVersion(boolean isVendorStringReplaceWithFeatureVersion) {
  		this.isVendorStringReplaceWithFeatureVersion = isVendorStringReplaceWithFeatureVersion;
  	}

    public String getFeatureVersion() {
		return featureVersion;
	}
	public void setFeatureVersion(String featureVersion) {
		this.featureVersion = featureVersion;
	}
	/**
     * Bulk Operation Activation Constructor
     * 
     * @param fulfillment
     * @param request
     * @param licenseModel
     * @throws OperationsException
     */
    public GeneratorRequestImpl(
            FulfillmentRecordProxy fulfillment, FulfillmentRequestTypeENC request,
            LicenseModel licenseModel)
            throws OperationsException{
        IEntitlementLineItem lineItem = (IEntitlementLineItem)fulfillment.getLineItem();
        // EntitlementLineItemBO lineItemBO = (EntitlementLineItemBO) lineItem;
        fulfillmentId = fulfillment.getFulfillmentId();
        setEntitlementId(lineItem.getParentEntitlement().getEntitlementID());
        setActivationID(lineItem.getActivationID());
        setSoldTo(lineItem.getParentEntitlement().getSoldTo());
        setStartDate(fulfillment.getStartDate());
        setExpirationDate(fulfillment.getExpirationDate());
        setOrderAttributes(lineItem.getLicenseModelAttributes());
        if(lineItem.getLicenseModelAttributes()!= null && lineItem.getLicenseModelAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion(lineItem.getLicenseModelAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
    
        setOrderId(lineItem.getOrderId());
        setOrderLineNumber(lineItem.getOrderLineNumber());
        setVersionDate(lineItem.getVersionDate());
        setProduct(lineItem.getProduct());
        setEntitledProducts(lineItem.getEntitledProducts());
        setPartNumber(lineItem.getPartNumber());
        setLicenseGeneratorConfiguration(((IEntitlementLineItem)lineItem)
                .getLicenseGeneratorConfiguration());
        setLicenseModel(licenseModel);
        if( getLicenseModel().getModelTimeAttributes()!= null &&  getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion( getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
      
        setShipToAddress(lineItem.getParentEntitlement().getShipToAddress());
        setRequestType(request);
        setFulfillAttributes(fulfillment.getLicenseModelAttributes());
        if(fulfillment.getLicenseModelAttributes()!= null && fulfillment.getLicenseModelAttributes().getValue(VENDOR_STRING) != null)
        	setVendorStringReplaceWithFeatureVersion(fulfillment.getLicenseModelAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION));
     
        setSupersededFeatures(fulfillment.getSupersededFeaturesSet());
        setShipToEmail(fulfillment.getShipToEmail());
        setSupersedeSignSet(fulfillment.getSupersedeSignSet());

        setCustomHost(fulfillment.getCustomHostId());
        setLicenseFileType(fulfillment.getLicenseFileType());
        setLicenseFileDefinitions(fulfillment.getLicenseTechnology().getLicenseFileDefinitions());
        setLicenseTechnology(fulfillment.getLicenseTechnology());

        setStartDate(fulfillment.getStartDate());
        setExpirationDate(fulfillment.getExpirationDate());
        setFulfillCount(fulfillment.getFulfillmentCount());
        setOverdraftCount(fulfillment.getOverdraftCount());
        if (fulfillment.getNodeLockedHostIds() != null) {
            NodeLockedHostId[] hosts = fulfillment.getNodeLockedHostIds();
            setNodeLockHostIds(hosts);
        }
        if (fulfillment.getServerHostId() != null) {
            setServerIds(fulfillment.getServerHostId());
        }
        if (fulfillment.getCustomHostId() != null) {
            setCustomHost(fulfillment.getCustomHostId());
        }

        AttributeWhenENC timeZoneWhen = licenseModel.getTimeZoneWhen();
        if (timeZoneWhen != null) {
            if (timeZoneWhen.equals(AttributeWhenENC.MODEL_TIME)) {
                setTimeZone(licenseModel.getTimeZoneValue());
            }
            else if (timeZoneWhen.equals(AttributeWhenENC.ENTITLEMENT_TIME)) {
                setTimeZone(lineItem.getTimeZone());
            }
            else if (timeZoneWhen.equals(AttributeWhenENC.FULFILLMENT_TIME)) {
                setTimeZone(fulfillment.getTimeZoneValue());
            }
        }

    }

    public Map<Product, Integer> getEntitledProducts() {
        if (entitledProducts == null) {
            entitledProducts = new HashMap<Product, Integer>();
            entitledProducts.put(product, new Integer(1));
        }
        return entitledProducts;
    }

    public void setEntitledProducts(Map<Product, Integer> entitledProducts) {
        this.entitledProducts = entitledProducts;
    }

    private void resolveAttributes() throws OperationsException {
        AttributeSet entitlementParams = getOrderAttributes();
        AttributeSet fulfillmentParams = getFulfillAttributes();
        if (ai != null)
            bizValues = ai.getValuesForMacroSubstitution();
        else
            bizValues = getBizValues();
        if (bizValues.get("Feature.version") != null)
            setFeatureVersion(bizValues.get("Feature.version").toString());
        inputAttributes = LicenseAttributeService.resolveAttributes(this.getLicenseModel(),
                entitlementParams, fulfillmentParams, bizValues);
    }

    /**
     * ctor used for ASR license generation.
     * 
     * @throws OPSBaseException
     * @throws OperationsException
     */
    public GeneratorRequestImpl(
            EntitlementProductBO entProduct, IProduct prod)
            throws OPSBaseException,
            OperationsException{
        setProduct(prod);
        setLicenseModel(entProduct.getLicenseModel());
        AttributeSet params = entProduct.getAttributeSet();
        setStartDate(getGMTAdjustedDate(new Date()));
        setVersionDate(entProduct.getVersionDate());
        fulfillmentId = "";
        setActivationID("");
        setEntitlementId("");
        setOrderAttributes(params);
        setFulfillAttributes(params);
        if(params!= null && params.getValue(VENDOR_STRING) != null) {
        	if(params.getValue(VENDOR_STRING).contains(FEATURE_VERSION)) {
        		isVendorStringReplaceWithFeatureVersion = true;
        	}
        }
        if( getLicenseModel().getModelTimeAttributes()!= null &&  getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING) != null) {
        	if(getLicenseModel().getModelTimeAttributes().getValue(VENDOR_STRING).contains(FEATURE_VERSION)) {
        		isVendorStringReplaceWithFeatureVersion = true;
        	}
        }
        setLicenseGeneratorConfiguration(prod.getLicenseGeneratorConfiguration());
    }

    /**
     * Zero arg constructor for use only by unit tests.
     */
    protected GeneratorRequestImpl(){

    }

    /*
     * Added this method to consume the parseException
     */
    private Date getGMTAdjustedDate(Date date) {
        try {
            date = WSCommonUtils.adjustRequestDate(date);
        }
        catch (ParseException e) {
            // this should never happen
        }
        return date;
    }

    public Set getSupersededFeatures() {
        return supersededFeatures;
    }

    public void setSupersededFeatures(Set supersededFeatures) {
        this.supersededFeatures = supersededFeatures;
    }

    public Map getBizValues() {
        Map result = new HashMap();
        String soldToDisplayName = "";
        String soldToName = "";
        if (soldTo != null) {
            if (soldTo.getName() != null)
                soldToName = soldTo.getName();

            if (soldTo.getDisplayName() != null)
                soldToDisplayName = soldTo.getDisplayName();
            else
                soldToDisplayName = soldToName;
        }
        result.put(SOLD_TO_NAME, soldToName);
        result.put(SOLD_TO_DISPLAY_NAME, soldToDisplayName);

        String activationID = getActivationID();
        if (activationID == null)
            activationID = "";
        result.put(ACTIVATION_ID, activationID);

        String orderId = getOrderId();
        if (orderId == null)
            orderId = "";
        result.put(ORDER_ID, orderId);

        String orderLineNumber = getOrderLineNumber();
        if (orderLineNumber == null)
            orderLineNumber = "";
        result.put(ORDER_LINE_NUMBER, orderLineNumber);

        result.put(SHIP_TO_ADDRESS, sanitizeShipTo());

        String productName = getProduct().getName();
        if (productName == null)
            productName = "";
        result.put(PRODUCT_NAME, productName);

        String productVersion = getProduct().getVersion();
        if (productVersion == null)
            productVersion = "";
        result.put(PRODUCT_VERSION, productVersion);
        
        LicensedItem prod = (LicensedItem)getProduct();
        Feature[] features = prod.getUniqueFeatures();
        List<String> featureVersion = new ArrayList<>();
        for (int i = 0; i < features.length; ++i) {
            if(!StringUtils.isEmpty(features[i].getVersion()))
            featureVersion.add(features[i].getVersion());
        }
        result.put(FEATURE_VER, featureVersion.toString().replaceAll("[\\[\\](){}]",""));

        result.put(FULFILLMENT_ID, fulfillmentId);

        String entitlementId = getEntitlementId();
        if (entitlementId == null)
            entitlementId = "";
        result.put(ENTITLEMENT_ID, entitlementId);

        String productPartNumber = "";
        if (partNumber != null)
            productPartNumber = partNumber.getPartNumber();
        result.put(PART_NUMBER, productPartNumber);

        return result;

    }

    private String sanitizeShipTo() {
        if (getShipToAddress() == null)
            return "";
        return getShipToAddress().replaceAll("\r?\n", ",");
    }

    public GeneratorResponse generate() throws OperationsException {
        resolveAttributes();
        GeneratorResponse response = LicenseTechnologyImpl.generateLicense(this);
        if (!response.isComplete()) {
            if (response.getErrorThrown() != null) {
                Throwable t = response.getErrorThrown();
                if (t instanceof OperationsException)
                    throw (OperationsException)t;
                else if (t instanceof FlexnetBaseException)
                    throw UtilityService.makeOperationsException((FlexnetBaseException)t);
                else
                    throw new OperationsException(t);
            }
            else if (response.getMessage() != null) {
                throw UtilityService.makeOperationsException(response.getMessage());
            }
        }
        return response;
    }

    // public API methods of the GeneratorRequest interface
    public Date getStartDate() {
        return startDate;
    }

    public int getFulfillCount() {
        return fulfillCount;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public AttributeSet getInputAttributes() {
        return inputAttributes;
    }

    public int getOverdraftCount() {
        return overdraftCount;
    }

    // end public API methods

    public AttributeSet getFulfillAttributes() {
        return fulfillAttributes;
    }

    public FulfillmentRequestTypeENC getRequestType() {
        return requestType;
    }

    public void setRequestType(FulfillmentRequestTypeENC requestType) {
        this.requestType = requestType;
    }

    public LicenseFileTypeENC getLicenseFileType() {
        return licenseFileType;
    }

    public void setLicenseFileType(LicenseFileTypeENC licenseFileType) {
        this.licenseFileType = licenseFileType;
    }

    /**
     * Get feature overrides
     */
    public AttributeSet getFeatureOverrides(Feature f) throws OperationsException {
        AttributeSet result = ((ProductFeatureImpl)f).getAdvancedProperties();
        LicenseAttributeService.resolveAttributeSet(result, inputAttributes, bizValues);
        return result;
    }

    /**
     * Node lock host ID; used only when generating FLEX licenses
     */
    public NodeLockedHostId[] getNodeLockHostIds() {
        return nodeLockHostIds;
    }

    /**
     * Server host ID; used only when generating FLEX licenses
     * 
     * @return
     */
    public ServerHostId getServerIds() {
        return serverIds;
    }

    public OrganizationUnit getSoldTo() {
        return soldTo;
    }

    public String getShipToAddress() {
        return shipToAddress;
    }

    public String getActivationID() {
        return activationID;
    }

    public String getEntitlementId() {
        return entitlementId;
    }

    public LicenseModel getLicenseModel() {
        return licenseModel;
    }

    public AttributeSet getOrderAttributes() {
        return orderAttributes;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderLineNumber() {
        return orderLineNumber;
    }

    public PartNumber getPartNumber() {
        return partNumber;
    }

    public Product getProduct() {
        return product;
    }

    public Date getVersionDate() {
        return versionDate;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    public void setFulfillCount(int fulfillCount) {
        this.fulfillCount = fulfillCount;
    }

    public void setNodeLockHostIds(NodeLockedHostId[] hosts) {
        this.nodeLockHostIds = hosts;

    }

    public void setOverdraftCount(int overdraftCount) {
        this.overdraftCount = overdraftCount;
    }

    public void setServerIds(ServerHostId id) {
        this.serverIds = id;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void setActivationID(String activationID) {
        this.activationID = activationID;
    }

    public void setEntitlementId(String entitlementId) {
        this.entitlementId = entitlementId;
    }

    public void setInputAttributes(AttributeSet inputAttributes) {
        this.inputAttributes = inputAttributes;
    }

    public void setLicenseModel(LicenseModel licenseModel) {
        this.licenseModel = licenseModel;
    }

    public void setOrderAttributes(AttributeSet orderAttributes) {
        this.orderAttributes = orderAttributes;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setOrderLineNumber(String orderLineNumber) {
        this.orderLineNumber = orderLineNumber;
    }

    public void setPartNumber(PartNumber partNumber) {
        this.partNumber = partNumber;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public void setVersionDate(Date versionDate) {
        this.versionDate = versionDate;
    }

    public void setSoldTo(OrganizationUnit soldTo) {
        this.soldTo = soldTo;
    }

    public void setShipToAddress(String shipToAddress) {
        this.shipToAddress = shipToAddress;
    }

    public void setFulfillAttributes(AttributeSet fulfillAttributes) {
        this.fulfillAttributes = fulfillAttributes;
    }

    public String getEntitlementID() {
        return entitlementId;
    }

    public String getShipToEmail() {
        return shipToEmail;
    }

    public void setShipToEmail(String email) {
        shipToEmail = email;
    }

    public Set getSupersedeSignSet() {
        return supersedeSignSet;
    }

    public void setSupersedeSignSet(Set supersedeSignSet) {
        this.supersedeSignSet = supersedeSignSet;
    }

    public CustomHostId getCustomHost() {
        return customHost;
    }

    public void setCustomHost(CustomHostId customHost) {
        this.customHost = customHost;
    }

    public String getCurrentLicenseOnHost() {
        String strLicenseText = "";
        ICustomHostId host = (ICustomHostId)getCustomHost();
        if (host != null) {
            strLicenseText = host.getLicenseText();
        }
        return strLicenseText;
    }

    public Map getParentFulfillments() {
        return parentFulfillments;
    }

    public void setParentFulfillments(Map parentFulfillments) {
        this.parentFulfillments = parentFulfillments;
    }

    public boolean isVerifyRequest() {
        return isVerifyRequest;
    }

    public void setVerifyRequest(boolean isVerifyRequest) {
        this.isVerifyRequest = isVerifyRequest;
    }

    public OrgUnitUser getLoggedInUser() {
        User user = ThreadContextUtil.getUser();
        if (user != null) {
            /* FNO-15246 : LazyInitializationException if the user belong to multiple Org
             * Populate the user from DB
             */
            try {
                user = (User)User.getById(User.class, user.getId());
            }
            catch (FlexnetHibernateException e) {
                // The user already logged in, so user record should exist in DB
            }
            return OperationsUserService.populateOpsUser(user);
        }
        return null;

    }

    public Set<LicenseFileDefinition> getLicenseFileDefinitions() {
        return licenseFileDefinitions;
    }

    public void setLicenseFileDefinitions(Set<LicenseFileDefinition> licenseFileDefinitions) {
        this.licenseFileDefinitions = licenseFileDefinitions;
    }

    public LicenseTechnology getLicenseTechnology() {
        return licenseTechnology;
    }

    public void setLicenseTechnology(LicenseTechnology licenseTechnology) {
        this.licenseTechnology = licenseTechnology;
    }

    public LicenseGeneratorConfig getLicenseGeneratorConfiguration() {
        return lcg;
    }

    public void setLicenseGeneratorConfiguration(LicenseGeneratorConfig lcg) {
        this.lcg = lcg;
    }

    public FNPTimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(FNPTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public List<CustomAttribute> getEntitlementCustomAttributes() {
        if (ai == null)
            return null;
        return CustomAttributeService.getCustomAttributeValues(ai.getLineItem()
                .getParentEntitlement());
    }

    public List<CustomAttribute> getEntitlementLineItemCustomAttributes() {
        if (ai == null)
            return null;
        return CustomAttributeService.getCustomAttributeValues(ai.getLineItem());
    }

}
