/*
 * Created on Jul 15, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

package com.flexnet.operations.publicapi;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flexnet.products.publicapi.LicenseFileTypeENC;

/**
 * Interface passed to custom license generators to encapsulate the input parameters required to
 * generate a license.
 */

public interface GeneratorRequest {

    /**
     * @return Sold to organization unit of the entitlement.
     */
    public OrganizationUnit getSoldTo();

    /**
     * @return Unique identifier of the line item for which the license is being generated.
     */
    public String getActivationID();

    /**
     * @return Order Id defined on the line item.
     */
    public String getOrderId();

    /**
     * @return Order line number defined on the line item.
     */
    public String getOrderLineNumber();

    /**
     * @return Version date, if defined, on the entitlement line item.
     */
    public Date getVersionDate();

    /**
     * @return Product sold with the line item
     * @deprecated
     */
    public Product getProduct();

    /**
     * @return Product sold with the line item
     */
    public Map<Product, Integer> getEntitledProducts();

    /**
     * @return Part number defined on the line item.
     */
    public PartNumber getPartNumber();

    /**
     * @return License model defined for this line item.
     */
    public LicenseModel getLicenseModel();

    /**
     * @return Requested start date for this license
     */
    public Date getStartDate();

    /**
     * @return Requested fulfill count for this license.
     */
    public int getFulfillCount();

    /**
     * @return Expiration date for this license - null for a permanent license.
     */
    public Date getExpirationDate();

    /**
     * @return All the license model attributes - Model time, entitlement time and fulfillment time.
     *         Any substitutions defined using {} are also resolved.
     */
    public AttributeSet getInputAttributes();

    /**
     * @return Computed overdraft count for this license. This overdraft count is computed using the
     *         request overdraft count, overdraft floor, ceiling and maximum defined for the license
     *         model.
     */
    public int getOverdraftCount();

    /**
     * @return Why is this license being generated - Activation, return, rehost etc.
     */
    public FulfillmentRequestTypeENC getRequestType();

    /**
     * @return The entitlement Id associated with this request
     */
    public String getEntitlementID();

    /*
     * @return the shipToEmail for the fulfillment
     */
    public String getShipToEmail();

    public CustomHostId getCustomHost();

    public String getCurrentLicenseOnHost();

    /**
     * @deprecated
     * @return LicenseFileType (text or binary)
     */
    public LicenseFileTypeENC getLicenseFileType();

    /**
     * @return Map(FulfillmentRecord, Integer) of Parent Fulfillments. the map contains the parent
     *         FulfillmentRecord and the corresponding returned count.
     */
    public Map getParentFulfillments();

    public boolean isVerifyRequest();

    public OrgUnitUser getLoggedInUser();

    public LicenseTechnology getLicenseTechnology();

    public Set<LicenseFileDefinition> getLicenseFileDefinitions();

    public LicenseGeneratorConfig getLicenseGeneratorConfiguration();

    public List<CustomAttribute> getEntitlementCustomAttributes();

    public List<CustomAttribute> getEntitlementLineItemCustomAttributes();

}
