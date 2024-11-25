/*
 * COPYRIGHT (C) 2002-2012 by Flexera Software LLC.
 * This software has been provided pursuant to a License Agreement
 * containing restrictions on its use.  This software contains
 * valuable trade secrets and proprietary information of
 * Flexera Software LLC and is protected by law.  It may
 * not be copied or distributed in any form or medium, disclosed
 * to third parties, reverse engineered or used in any manner not
 * provided for in said License Agreement except with the prior
 * written authorization from Flexera Software LLC.
 *
 * Created on Sep 16, 2004
 */

package com.flexnet.operations.web.forms;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.apache.struts.validator.ValidatorForm;

import com.flexnet.operations.api.IAttributeSet;
import com.flexnet.operations.api.ICustomAttributeDescriptor;
import com.flexnet.operations.publicapi.AttributeSet;
import com.flexnet.operations.publicapi.AttributeTypeENC;
import com.flexnet.operations.publicapi.AttributeWhenENC;
import com.flexnet.operations.publicapi.CustomAttributeDescriptor;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.services.LicenseModelAttributeImpl;
import com.flexnet.operations.web.actions.OperationsBaseAction;
import com.flexnet.operations.web.actions.UIRuntimeException;
import com.flexnet.operations.web.actions.product.FlexGeneratorHelper;
import com.flexnet.operations.web.beans.MaintenanceItemPropertiesBean;
import com.flexnet.operations.web.forms.product.ProductBean;
import com.flexnet.operations.web.util.CommonUtils;
import com.flexnet.platform.Constants;
import com.flexnet.platform.config.AppConfigEntryDTO;
import com.flexnet.platform.config.AppConfigService;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.config.InvalidConversionException;
import com.flexnet.platform.services.internationalization.InternationalizationService;
import com.flexnet.platform.services.logging.Logger;
import com.flexnet.platform.services.logging.LoggingService;
import com.flexnet.platform.util.DateUtility;
import com.flexnet.platform.web.actions.ActionsConstants;
import com.flexnet.platform.web.forms.NewPaging;

/**
 * @author schan STEVEDO need class purpose
 */
public abstract class OperationsBaseForm extends ValidatorForm {
    public static String NEWLINE = System.getProperty("line.separator");
    private static final String DEFAULT_FIND = OperationsBaseAction.REQUEST_PARAM_FIND_VALUE_ALL;
    private static final String PUBLISHER_DEFINED_MESSAGES_BUNDLE = "PublisherDefinedAttributesText";
    private String find = DEFAULT_FIND;
    private String searchPhrase = "";
    private String searchCategory = "";
    private String searchQualifier = "";
    private List searchCategorys = new LinkedList();
    private List permissions = new LinkedList();
    private boolean configMode = false;
    private static boolean loadedProductHierarchies = false;
    private String productHierarchyLevel1 = "";
    private String productHierarchyLevel2 = "";
    private String productHierarchyLevel3 = "";
    private String productHierarchyLevel4 = "";
    private NewPaging paging = new NewPaging();
    protected static AppConfigService configService = null;
    protected static final Logger logger = LoggingService.getLogger(Constants.MODULE_OPERATIONS);

    private boolean hasPrevious = false;
    private boolean hasNext = false;
    private int currentPage = 0;
    private int numberOfPages = 0;
    private int numberOfRecords = 0;
    private int pageNumber = -1;
    private String sortColumnKey = "";
    private String sortDirection = "";
    private String language;
    private String[] versionDateOptions = new String[0];
    private String htmlEmail = "false";

    private String[] startDateOptions = new String[0];
    private List customAttributes = new ArrayList();
    private boolean showProductInfoInSelectHostPageByDefault = true;
    private String defaultSimpleSearchOperator = "CONTAINS";
    public static final String DEFAULT_SEARCH_TYPE = OperationsBaseAction.SEARCHTYPE_RECENT;
    private String searchType = null;
    private String viewAction = "";

    public class ColumnInfo {

        private String screenName = "";
        private String fieldName = "";
        private boolean sortable = false;
        private boolean display = false;
        private int displayOrder = 0;
        private int displaySize = 0;
        private String val = "";
        private String soldToName = "";
        private String sortColumnName = "";
        private String trimType = "";
        private boolean customAttribute = false;
        private boolean customHostAttribute = false;
        private boolean customLineItemAttribute = false;
        private Map<ProductBean, Integer> orderables = new HashMap<ProductBean, Integer>();
        private List<MaintenanceItemPropertiesBean> maintenanceItems = new LinkedList<MaintenanceItemPropertiesBean>();
        private List<String[]> channelPartners = new ArrayList<String[]>();
        private List<String> nodeHostIds = new ArrayList<String>();

        public static final String COLUMN_FULFILLMENT_ID = "FULFILLMENT_ID";
        public static final String COLUMN_ENTITLEMENT_LINEITEM_ID = "ENTITLEMENT_LINEITEM_ID";
        public static final String COLUMN_HOST_ID = "HOST_ID";
        public static final String COLUMN_FULFILLMENT_COUNT = "FULFILLMENT_COUNT";
        public static final String COLUMN_FULFILLMENT_LIFECYCLE_STATUS = "FULFILLMENT_LIFECYCLE_STATUS";
        public static final String COLUMN_ACTIVATION_DATE = "ACTIVATION_DATE";
        public static final String COLUMN_EXPIRATION_DATE = "EXPIRATION_DATE";
        public static final String COLUMN_SOLD_TO_DISPLAY_NAME = "SOLD_TO_DISPLAY_NAME";
        public static final String COLUMN_START_DATE = "START_DATE";
        public static final String COLUMN_LICENSE_MODEL_TYPE = "LICENSE_MODEL_TYPE";
        public static final String COLUMN_REMAINING_COPIES = "REMAINING_COPIES";
        public static final String COLUMN_STRING_LICENSE_MODEL_ATTRIBUTE = "STRING_LICENSE_MODEL_ATTRIBUTE:";
        public static final String COLUMN_BOOLEAN_LICENSE_MODEL_ATTRIBUTE = "BOOLEAN_LICENSE_MODEL_ATTRIBUTE:";
        public static final String COLUMN_NUMBER_LICENSE_MODEL_ATTRIBUTE = "NUMBER_LICENSE_MODEL_ATTRIBUTE:";
        public static final String COLUMN_DATE_LICENSE_MODEL_ATTRIBUTE = "DATE_LICENSE_MODEL_ATTRIBUTE:";
        public static final String COLUMN_STRING_CUSTOM_HOST_ATTRIBUTE = "STRING_CUSTOM_HOST_ATTRIBUTE:";
        public static final String COLUMN_BOOLEAN_CUSTOM_HOST_ATTRIBUTE = "BOOLEAN_CUSTOM_HOST_ATTRIBUTE:";
        public static final String COLUMN_NUMBER_CUSTOM_HOST_ATTRIBUTE = "NUMBER_CUSTOM_HOST_ATTRIBUTE:";
        public static final String COLUMN_DATE_CUSTOM_HOST_ATTRIBUTE = "DATE_CUSTOM_HOST_ATTRIBUTE:";
        public static final String COLUMN_STRING_LINEITEM_ATTRIBUTE = "STRING_LINE_ITEM_ATTRIBUTE:";
        public static final String COLUMN_BOOLEAN_LINEITEM_ATTRIBUTE = "BOOLEAN_LINE_ITEM_ATTRIBUTE:";
        public static final String COLUMN_NUMBER_LINEITEM_ATTRIBUTE = "NUMBER_LINE_ITEM_ATTRIBUTE:";
        public static final String COLUMN_DATE_LINEITEM_ATTRIBUTE = "DATE_LINE_ITEM_ATTRIBUTE:";

        public ColumnInfo(){}

        
        public String getSoldToName() {
			return soldToName;
		}

		public void setSoldToName(String soldToName) {
			this.soldToName = soldToName;
		}


		public boolean isDisplay() {
            return display;
        }

        public void setDisplay(boolean display) {
            this.display = display;
        }

        public int getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(int displayOrder) {
            this.displayOrder = displayOrder;
        }

        public int getDisplaySize() {
            return displaySize;
        }

        public void setDisplaySize(int displaySize) {
            this.displaySize = displaySize;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getScreenName() {
            return screenName;
        }

        public void setScreenName(String screenName) {
            this.screenName = screenName;
        }

        public boolean isSortable() {
            return sortable;
        }

        public void setSortable(boolean sortable) {
            this.sortable = sortable;
        }

        public String getVal() {
            return val;
        }

        public void setVal(String val) {
            this.val = val;
        }

        public String getSortColumnName() {
            return sortColumnName;
        }

        public void setSortColumnName(String sortColumnName) {
            this.sortColumnName = sortColumnName;
        }

        public String getTrimType() {
            return trimType;
        }

        public void setTrimType(String trimType) {
            this.trimType = trimType;
        }

        public Map<ProductBean, Integer> getOrderables() {
            return orderables;
        }

        public void setOrderables(Map<ProductBean, Integer> orderables) {
            this.orderables = orderables;
        }

        public List<MaintenanceItemPropertiesBean> getMaintenanceItems() {
            return maintenanceItems;
        }

        public void setMaintenanceItems(List<MaintenanceItemPropertiesBean> maintenanceItems) {
            this.maintenanceItems = maintenanceItems;
        }

        public boolean isCustomAttribute() {
            return customAttribute;
        }

        public void setCustomAttribute(boolean customAttribute) {
            this.customAttribute = customAttribute;
        }

        public boolean isCustomHostAttribute() {
            return customHostAttribute;
        }

        public void setCustomHostAttribute(boolean customHostAttribute) {
            this.customHostAttribute = customHostAttribute;
        }

        public boolean isCustomLineItemAttribute() {
            return customLineItemAttribute;
        }

        public void setCustomLineItemAttribute(boolean customLineItemAttribute) {
            this.customLineItemAttribute = customLineItemAttribute;
        }

        public List<String[]> getChannelPartners() {
            return channelPartners;
        }

        public void setChannelPartners(List<String[]> channelPartners) {
            this.channelPartners = channelPartners;
        }

        public List<String> getNodeHostIds() {
            return nodeHostIds;
        }

        public void setNodeHostIds(List<String> nodeHostIds) {
            this.nodeHostIds = nodeHostIds;
        }

        public void addNodeHostId(String host) {
            if (this.nodeHostIds == null)
                this.nodeHostIds = new ArrayList<String>();
            this.nodeHostIds.add(host);
        }

        public String toString() {
            String answer = "";
            answer += "fieldName=\"" + fieldName + "\", ";
            answer += "sortable=\"" + String.valueOf(sortable) + "\", ";
            answer += "display=\"" + String.valueOf(display) + "\", ";
            answer += "displayOrder=\"" + String.valueOf(displayOrder) + "\", ";
            answer += "val=\"" + val + "\"";
            return (answer);
        }
    };

    public ColumnInfo newColumnInfo() {
        return new ColumnInfo();
    }

    public class CustomAttributeInfo {

        private String name = "";
        private String type = "";
        private boolean allowEntitlementTime = false;
        private boolean allowLicenseModelTime = false;
        private boolean allowFulfillmentTime = false;
        private List whenChoices = new ArrayList();
        private List validValues = new ArrayList();
        private List sortedValidValues = new ArrayList();
        private boolean licenseAttribute = false;
        private boolean required = false;
        private Integer maxLength = 0;
        private String licenseTechnology = "";
        private String displayType = "";
        private boolean needForReporting = false;
        private String nameSpace = "";

        public CustomAttributeInfo(){}

        public boolean isAllowEntitlementTime() {
            return allowEntitlementTime;
        }

        public void setAllowEntitlementTime(boolean allowEntitlementTime) {
            this.allowEntitlementTime = allowEntitlementTime;
        }

        public boolean isAllowFulfillmentTime() {
            return allowFulfillmentTime;
        }

        public void setAllowFulfillmentTime(boolean allowFulfillmentTime) {
            this.allowFulfillmentTime = allowFulfillmentTime;
        }

        public boolean isAllowLicenseModelTime() {
            return allowLicenseModelTime;
        }

        public void setAllowLicenseModelTime(boolean allowLicenseModelTime) {
            this.allowLicenseModelTime = allowLicenseModelTime;
        }

        public String getDisplayType() {
            return displayType;
        }

        public void setDisplayType(String displayType) {
            this.displayType = displayType;
        }

        public boolean isLicenseAttribute() {
            return licenseAttribute;
        }

        public void setLicenseAttribute(boolean licenseAttribute) {
            this.licenseAttribute = licenseAttribute;
        }

        public String getLicenseTechnology() {
            return licenseTechnology;
        }

        public void setLicenseTechnology(String licenseTechnology) {
            this.licenseTechnology = licenseTechnology;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isNeedForReporting() {
            return needForReporting;
        }

        public void setNeedForReporting(boolean needForReporting) {
            this.needForReporting = needForReporting;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List getValidValues() {
            return validValues;
        }

        public void setValidValues(List validValues) {
            this.validValues = validValues;
        }

        public List getWhenChoices() {
            return whenChoices;
        }

        public void setWhenChoices(List whenChoices) {
            this.whenChoices = whenChoices;
        }

        public String getNameSpace() {
            return nameSpace;
        }

        public void setNameSpace(String nameSpace) {
            this.nameSpace = nameSpace;
        }

        public List getSortedValidValues() {
            return sortedValidValues;
        }

        public void setSortedValidValues(List sortedValidValues) {
            this.sortedValidValues = sortedValidValues;
        }

    }

    public CustomAttributeInfo newCustomAttributeInfo() {
        return new CustomAttributeInfo();
    }

    public OperationsBaseForm(){}

    private static String[] getMultiValueListFromString(String value) {
        // Get rid of the leading & trailing quote characters.
        value = value.substring(1);
        value = value.substring(0, value.length() - 1);
        // Now parse the rest of the string for the multiple values that may be
        // there.
        StringTokenizer st = new StringTokenizer(value, "\",\"");
        String token;
        ArrayList valuesList = new ArrayList();
        int counter = 0;
        while (st.hasMoreTokens()) {
            token = st.nextToken();
            valuesList.add(token);
            counter++;
        }
        String[] values = new String[counter];
        counter = 0;
        for (Iterator iter = valuesList.iterator(); iter.hasNext();) {
            values[counter] = (String)iter.next();
            counter++;
        }
        return values;
    }

    private static String getStringFromMultiValueList(String[] values) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < values.length; ++i) {
            if (i > 0)
                sb.append(",");
            sb.append('"');
            sb.append(values[i]);
            sb.append('"');
        }
        return sb.toString();
    }

    public String getResourceString(String key, String args[]) {
        return CommonUtils.getResourceString(key, args, locale);
    }

    public String getCancelMessage() {
        return getResourceString("operations.warning.cancel", new String[0]);
    }

    public String getDeleteMessage() {
        return getResourceString("operations.warning.delete", new String[0]);
    }

    public String getDraftMessage() {
        return getResourceString("operations.warning.draft", new String[0]);
    }

    public String getDeployMessage() {
        return getResourceString("operations.warning.deploy", new String[0]);
    }

    public String getInactiveMessage() {
        return getResourceString("operations.warning.inactive", new String[0]);
    }

    public String getObsoleteMessage() {
        return getResourceString("operations.warning.obsolete", new String[0]);
    }

    public String getResetMessage() {
        return getResourceString("operations.warning.reset", new String[0]);
    }

    public NewPaging getPaging() {
        return paging;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void addPermission(String permission) {
        permissions.add(permission);
    }

    public void reset() {
        find = DEFAULT_FIND;
        permissions.clear();
        configMode = false;
        searchPhrase = "";
        searchCategory = "";
        searchCategorys.clear();
        searchType = null;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("ProductHierarchyLevel1=[").append(getProductHierarchyLevel1()).append("]");
        buf.append(NEWLINE).append("ProductHierarchyLevel2=[").append(getProductHierarchyLevel2())
                .append("]");
        buf.append(NEWLINE).append("ProductHierarchyLevel3=[").append(getProductHierarchyLevel3())
                .append("]");
        buf.append(NEWLINE).append("ProductHierarchyLevel4=[").append(getProductHierarchyLevel4())
                .append("]");
        buf.append(NEWLINE).append("find=[").append(find).append("]");
        buf.append(NEWLINE).append("permissions=[");
        for (Iterator it = permissions.iterator(); it.hasNext();) {
            buf.append(NEWLINE).append(it.next());
            if (it.hasNext()) {
                buf.append(",");
            }
        }
        buf.append(NEWLINE).append("]");
        buf.append(NEWLINE).append("configMode=[").append(configMode).append("]");
        buf.append(NEWLINE).append("searchPhrase=[").append(searchPhrase).append("]");
        buf.append(NEWLINE).append("searchCategory=[").append(searchCategory).append("]");
        buf.append(NEWLINE).append("searchCategorys=[");
        for (Iterator it = searchCategorys.iterator(); it.hasNext();) {
            buf.append(NEWLINE).append(it.next());
            if (it.hasNext()) {
                buf.append(",");
            }
        }
        buf.append(NEWLINE).append("]");
        return buf.toString();
    }

    public String getFind() {
        return find;
    }

    public void setFind(String find) {
        this.find = find;
    }

    public boolean isConfigMode() {
        return configMode;
    }

    public void setConfigMode(boolean configMode) {
        this.configMode = configMode;
    }

    public String getProductHierarchyLevel1() {
        productHierarchyLevel1 = getResourceString("packageProducts.label.suite", new String[0]);
        return productHierarchyLevel1;
    }

    public void setProductHierarchyLevel1(String productHierarchyLevel1) {
        this.productHierarchyLevel1 = productHierarchyLevel1;
    }

    public String getProductHierarchyLevel2() {
        productHierarchyLevel2 = getResourceString("packageProducts.label.product", new String[0]);
        return productHierarchyLevel2;
    }

    public void setProductHierarchyLevel2(String productHierarchyLevel2) {
        this.productHierarchyLevel2 = productHierarchyLevel2;
    }

    public String getProductHierarchyLevel3() {
        productHierarchyLevel3 = getResourceString("packageProducts.label.featureBundle",
                new String[0]);
        return productHierarchyLevel3;
    }

    public void setProductHierarchyLevel3(String productHierarchyLevel3) {
        this.productHierarchyLevel3 = productHierarchyLevel3;
    }

    public String getProductHierarchyLevel4() {
        productHierarchyLevel4 = getResourceString("packageProducts.label.feature", new String[0]);
        return productHierarchyLevel4;
    }

    public void setProductHierarchyLevel4(String productHierarchyLevel4) {
        this.productHierarchyLevel4 = productHierarchyLevel4;
    }

    public List getSearchCategorys() {
        return searchCategorys;
    }

    public void setSearchCategorys(List searchCategorys) {
        this.searchCategorys = searchCategorys;
    }

    public String getSearchPhrase() {
        if (searchPhrase != null)
            searchPhrase = searchPhrase.trim();
        return searchPhrase;
    }

    public void setSearchPhrase(String searchPhrase) {
        if (searchPhrase != null)
            searchPhrase = searchPhrase.trim();
        this.searchPhrase = searchPhrase;
    }

    public String getSearchCategory() {
        return searchCategory;
    }

    public void setSearchCategory(String searchCategory) {
        this.searchCategory = searchCategory;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getProductHierarchyLevelName(String level) throws FlexnetBaseException {
        String configKey = level;
        if (configKey == null) {
            throw new UIRuntimeException("The specified level=[" + level + "] is not valid");
        }
        AppConfigEntryDTO configEntry = null;
        try {
            configEntry = configService.getConfigEntry(configKey);
            return configEntry.getValueAsString();
        }
        catch (InvalidConversionException doh) {
            Object actualClass = null;
            if (configEntry != null) {
                Object actual = configEntry.getValue();
                if (actual != null) {
                    actualClass = actual.getClass().toString();
                }
            }
            String errorMsg = "The configuration=[" + level
                    + "] was expected to be configured as a String but instead was configured as=["
                    + actualClass + "]";
            throw new UIRuntimeException(errorMsg, doh);
        }
        catch (Exception doh) {
            throw new UIRuntimeException("Error reading configuration entries", doh);
        }
    }

    private List licenseModelAttributes = new LinkedList();
    private Map licenseModelAttributesMap = new HashMap();

    private Map licenseModelAttrValues = new HashMap();
    private Set<String> licenseModelRequiredAttributes = new HashSet();
    private String needToDefineTimeZone = "false";
    private List<String> timeZoneValues = new LinkedList<String>();
    private String selectedFNPTimeZone = "";

    // used to obtain the TimeZone from the user info in the session
    private TimeZone tz;
    private Locale locale;

    public void setUserTimeZone(TimeZone tz) {
        this.tz = tz;
    }

    public void setUserLocale(Locale locale) {
        this.locale = locale;
    }

    public Locale getUserLocale() {
        return this.locale;
    }

    public String toDateString(Date date) {
        if (date == null)
            return "unknown";
        return getDateFormat().format(date);
    }

    /**
     * Method to be used to display in the logged in user locale format for the following dates
     * START_DATE, EXPIRY_DATE, VERSION_DATE the above Dates are always stored in GMT date format in
     * the database. We should not apply timezone of the logged in user, These days should be
     * displayed in GMT only
     * 
     * @param date
     * @return formatted date string in GMT tz
     */
    public String toGMTDateString(Date date) {
        if (date == null)
            return "unknown";
        DateFormat df = getDateFormat();
        df.setTimeZone(DateUtility.getGMTTimeZone());
        return df.format(date);
    }

    /**
     * Show the Formatted Date Strings in GMT timezone only This method is used to display the
     * selected date
     * 
     * @param date
     * @return
     */
    public String toDatePickerString(Date date) {
        String dateStr = "";
        if (date == null)
            return dateStr;
        try {
            dateStr = DateUtility.formatToDatePicker(date, this.locale,
                    DateUtility.getGMTTimeZone());
        }
        catch (Exception e) {
            // This should never happen
            dateStr = "";
        }
        return dateStr;
    }

    public String toDateTimeString(Date date) {
        if (date == null)
            return "unknown";
        return getDateTimeFormat().format(date);
    }

    public Date toDateTime(String dateTimeString) throws Exception {
        return getDateTimeFormat().parse(dateTimeString);
    }

    public Date toDate(String dateString) throws Exception {
        Date date = null;
        try {
            date = DateUtility.parseFromDatePicker(dateString, this.locale, this.tz);
        }
        catch (Exception ex) {}

        return date;
    }

    public Date toGMTDate(String dateString) throws Exception {
        Date date = null;

        if (null == dateString || "".equals(dateString.trim())) {
            return date;
        }

        try {
            date = DateUtility.parseFromDatePicker(dateString, this.locale,
                    DateUtility.getGMTTimeZone());
        }
        catch (Exception ex) {}

        return date;
    }

    public DateFormat getGMTDateFormat() {
        DateFormat df;
        if (locale != null) {
            df = DateFormat.getDateInstance(DateUtility.getApplicationDateStyle(), locale);
        }
        else {
            df = DateFormat.getDateInstance(DateUtility.getApplicationDateStyle());
        }
        df.setTimeZone(DateUtility.getGMTTimeZone());
        return df;
    }

    public DateFormat getDateFormat() {
        DateFormat df;
        if (locale != null) {
            df = DateFormat.getDateInstance(DateUtility.getApplicationDateStyle(), locale);
        }
        else {
            df = DateFormat.getDateInstance(DateUtility.getApplicationDateStyle());
        }
        df.setTimeZone(this.tz);
        return df;
    }

    /**
     * Get the application configured dateStyle from the Internationalization service
     * 
     * @param dateStyle
     * @param timeStyle
     * @return
     */
    public DateFormat getDateTimeFormat() {
        DateFormat df;

        // ignoring the method parameters and getting it from application
        // configuration

        if (locale != null) {
            df = DateFormat.getDateTimeInstance(DateUtility.getApplicationDateStyle(),
                    getApplicationTimeStyle(), locale);
        }
        else {
            df = DateFormat.getDateTimeInstance(DateUtility.getApplicationDateStyle(),
                    getApplicationTimeStyle());
        }
        df.setTimeZone(tz);
        return df;
    }

    private int getApplicationTimeStyle() {
        // get the style for the dates supported in the installed application.
        String strTimeStyle = InternationalizationService.getInstance().getString(
                "common.format.timeStyle");

        // default is Dateformat.MEDIUM
        int timeStyleFormat = 2;

        if (strTimeStyle != null) {
            if (strTimeStyle.equals("short")) {
                timeStyleFormat = DateFormat.SHORT;
            }
            else if (strTimeStyle.equals("medium")) {
                timeStyleFormat = DateFormat.MEDIUM;
            }
            else if (strTimeStyle.equals("long")) {
                timeStyleFormat = DateFormat.LONG;
            }
            else if (strTimeStyle.equals("full")) {
                timeStyleFormat = DateFormat.FULL;
            }
        }
        return timeStyleFormat;
    }

    /**
     * Internal abstract class to represent information about the business objects that are
     * contained with this form. Each Sub-class of OperationsBaseForm can define a sub-class of
     * BizObjectInfo and provided methods to create, add, and manipulate the objects it contains.
     * This class implements the fields that are common to all objects: name, createdOn and
     * lastModified; and accessors for each.
     */
    public class BizObjectInfo {
        private Date createdOn;
        private Date lastModified;
        private String name = "";

        protected BizObjectInfo(){}

        public Date getCreatedOn() {
            return createdOn;
        }

        public void setCreatedOn(Date createdOn) {
            this.createdOn = createdOn;
        }

        public String getCreatedOnStr() {
            return toDateString(getCreatedOn());
        }

        public String getCreatedTimeStr() {
            return toDateTimeString(getCreatedOn());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Date getLastModified() {
            return lastModified;
        }

        public void setLastModified(Date lastModified) {
            this.lastModified = lastModified;
        }

        public String getLastModifiedStr() {
            return toDateString(getLastModified());
        }

        public String getLastModifiedTimeStr() {
            return toDateTimeString(getLastModified());
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("name=[").append(getName()).append("]");
            buf.append(", createdOn=[").append(getCreatedOn()).append("]");
            buf.append(", lastModified=[").append(getLastModified()).append("]");
            return buf.toString();
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }

    public int getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setSingleValueLMAttribute(String name, String value) {
        licenseModelAttrValues.put(name, value);
    }

    public void setMultiValueLMAttribute(String name, String[] values) {
        String value = "";
        if (values.length > 0) {
            value = getStringFromMultiValueList(values);
        }
        licenseModelAttrValues.put(name, value);
    }

    public List getSortedValidValues(String desc) {
        CustomAttributeDescriptor descAttr = (CustomAttributeDescriptor)licenseModelAttributesMap
                .get(desc);
        if (descAttr == null) {
            return new LinkedList();
        }
        List values = descAttr.getValidValues();
        Iterator iter = values.iterator();
        TreeMap m = new TreeMap();
        while (iter.hasNext()) {
            String plt = (String)iter.next();
            String label = getResourceString(plt + ".label", null);
            if ("".equals(label))
                label = plt;
            m.put(label, plt);
        }
        List c = new LinkedList(m.values());
        return c;
    }

    /*
     * This method returns just the value of the Single Valued attribute.
     * This attribute can be mapped with custom defined message bundle.
     * This method is required to preselect the <html:select> based only on
     * values.
     */
    public String getSingleValueLMAttribute(String name) {
        String value = (String)licenseModelAttrValues.get(name);
        if (value != null)
            return value;
        return "";
    }

    public void setSingleValueLMAttributeValue(String name, String value) {
        licenseModelAttrValues.put(name, value);
    }

    /*
     * Single Valued attribute can be mapped with custom defined message bundle.
     * This method returns the custom defined message mapped on a 
     * particular value of this Single Valued attribute. If such custom defined
     * message bundle is available, the message is returned. Else the 
     * value is returned.
     */
    public String getSingleValueLMAttributeValue(String name) {
        String value = (String)licenseModelAttrValues.get(name);
        if (value == null)
            return "";
        value = CommonUtils.getPublisherDefinedMessageForAttribute(name, value);
        return value;
    }

    public String[] getMultiValueLMAttribute(String name) {
        String value = (String)licenseModelAttrValues.get(name);
        if (value != null && !value.equals(""))
            return getMultiValueListFromString(value);
        else
            return new String[0];
    }

    public String getLMAttribute(String name) {
        String value = (String)licenseModelAttrValues.get(name);
        if (value != null)
            return value;
        else
            return "";
    }

    public List<String> getLMAttributeAsCollection(String name) {
        String value = (String)licenseModelAttrValues.get(name);
        if (value == null) {
            List<String> list = new ArrayList<String>();
            return list;
        }
        String valueNoDoubleQuotes = value.replaceAll("\"", "");
        String[] splitValues = valueNoDoubleQuotes.split(",");
        return Arrays.asList(splitValues);
    }

    public List getLicenseModelAttributes() {
        return licenseModelAttributes;
    }

    public void addLicenseModelAttributeInfo(CustomAttributeDescriptor desc) {
        if (!licenseModelAttributes.contains(desc)) {
            licenseModelAttributes.add(desc);
            if (!FlexGeneratorHelper.ALLOW_ONE_TERMINAL_SERVER.equals(desc.getName())) {
                addCustomAttributeMetadataToForm(desc);
            }
        }
        licenseModelAttributesMap.put(desc.getName(), desc);
    }

    public Map getLicenseModelAttrValues() {
        return licenseModelAttrValues;
    }

    public void setLicenseModelAttrValues(Map valsMap) {
        licenseModelAttrValues = valsMap;
    }

    public void setLicenseModelRequiredAttributes(Set valsMap) {
        licenseModelRequiredAttributes = valsMap;
    }

    public boolean getLicenseModelRequiredForAttribute(String name) {
        if (licenseModelAttributesMap != null) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)licenseModelAttributesMap
                    .get(name);
            if (desc.isRequired()) {
                return true;
            }
            else {
                LicenseModelAttributeImpl impl = (LicenseModelAttributeImpl)desc;
                if (licenseModelRequiredAttributes != null
                        && licenseModelRequiredAttributes.contains(impl.uniqueName())) {
                    return true;
                }
            }
        }
        return false;
    }

    // copy the license model attribute values from the form into the target
    // AttributeSet
    // return false if there are no attributes in this form; true if there are
    // some
    public boolean copyLicenseModelAttributes(AttributeSet target) throws OperationsException {
        Iterator iter = target.getDescriptors().iterator();
        while (iter.hasNext()) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)iter.next();
            Object value = licenseModelAttrValues.get(desc.getName());
            if (desc.getType().isDateType()) {
                try {
                    value = toGMTDate((String)value);
                }
                catch (Exception ex) {}
                // commented by Punit after discussing with rama. Causes class cast
                // exception issue IOA-000032319
                // licenseModelAttrValues.put(desc.getName(), value);
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
        return true;
    }

    public int getNumberOfLicenseModelAttributes() {

        if (licenseModelAttributes != null && licenseModelAttributes.size() > 0) {
            return licenseModelAttributes.size();
        }
        else if (getNeedToDefineTimeZone().equals("true")) {
            return 1;
        }

        return 0;
    }

    /**
     * @return Returns the bOOLEAN_TYPE.
     */
    public String getBooleanType() {
        return AttributeTypeENC.BOOLEAN_TYPE.toString();
    }

    /**
     * @return Returns the dATE_TYPE.
     */
    public String getDateType() {
        return AttributeTypeENC.DATE_TYPE.toString();
    }

    /**
     * @return Returns the mULTI_VALUED_TEXT_TYPE.
     */
    public String getMultiValuedTextType() {
        return AttributeTypeENC.MULTI_VALUED_TEXT_TYPE.toString();
    }

    /**
     * @return Returns the nUMBER_TYPE.
     */
    public String getNumberType() {
        return AttributeTypeENC.NUMBER_TYPE.toString();
    }

    public String getLongtextType() {
        return AttributeTypeENC.LONGTEXT.toString();
    }

    /**
     * @return Returns the tEXT_TYPE.
     */
    public String getTextType() {
        return AttributeTypeENC.TEXT_TYPE.toString();
    }

    public String getSortColumnKey() {
        return sortColumnKey;
    }

    public void setSortColumnKey(String sortColumnKey) {
        this.sortColumnKey = sortColumnKey;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getProductPackagingQueryParamsStr(String newSortOrder) {
        String retStr = "";
        if (this.paging != null) {
            Map params = this.paging.getSortParams(newSortOrder);
            Set entryset = params.entrySet();
            Iterator iter = entryset.iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                Object key = entry.getKey();
                Object val = entry.getValue();
                if (key != null && val != null) {
                    retStr = retStr + key.toString() + "=" + val.toString() + "&";
                }
            }
        }
        return retStr;
    }

    /**
     * This method takes care of setting the locale and timezone before populating the fields with
     * values.
     * 
     * @author smajji
     */
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        super.reset(mapping, request);

        // now get the user locale and timezone from the session
        this.locale = (Locale)request.getSession().getAttribute(
                org.apache.struts.Globals.LOCALE_KEY);
        this.tz = (TimeZone)request.getSession().getAttribute(ActionsConstants.ATTR_TIMEZONE);

    }

    public String[] getVersionDateOptions() {
        return versionDateOptions;
    }

    public void setVersionDateOptions(String[] versionDateOptions) {
        this.versionDateOptions = versionDateOptions;
    }

    /**
     * Method to convert the date picker string value to a long value representation of the GMT
     * date.
     * 
     * @param trueForm
     * @param dateVal
     * @return
     */
    public long convertToDateLongValue(String dateVal) {
        long customDateVal = -1;
        try {
            Date date = toGMTDate(dateVal);
            if (date != null) {
                customDateVal = date.getTime();
            }
        }
        catch (Exception pe) {}
        return customDateVal;
    }

    public int getNumberOfRecords() {
        return numberOfRecords;
    }

    public void setNumberOfRecords(int numberOfRecords) {
        this.numberOfRecords = numberOfRecords;
    }

    public String getHtmlEmail() {
        return htmlEmail;
    }

    public void setHtmlEmail(String htmlEmail) {
        this.htmlEmail = htmlEmail;
    }

    public String[] getStartDateOptions() {
        return startDateOptions;
    }

    public void setStartDateOptions(String[] startDateOptions) {
        this.startDateOptions = startDateOptions;
    }

    public List getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(List customAttributes) {
        this.customAttributes = customAttributes;
    }

    public void addCustomAttribute(CustomAttributeInfo attr) {
        this.customAttributes.add(attr);
    }

    public void addCustomAttributeMetadataToForm(AttributeSet attrset) {
        if (attrset == null)
            return;
        Set s = attrset.getDescriptors();
        addCustomAttributeMetadataToForm(s);
    }

    public void addCustomAttributeMetadataToForm(Collection c) {
        addCustomAttributeMetadataToForm(c, true);
    }

    public void addCustomAttributeMetadataToForm(Collection c, boolean showFulfillmentTimeOption) {
        if (c == null) {
            return;
        }
        Iterator iter = c.iterator();
        while (iter.hasNext()) {
            CustomAttributeDescriptor desc = (CustomAttributeDescriptor)iter.next();
            addCustomAttributeMetadataToForm(desc, showFulfillmentTimeOption);
        }
    }

    public void addCustomAttributeMetadataToForm(CustomAttributeDescriptor desc) {
        addCustomAttributeMetadataToForm(desc, true);
    }

    public void addCustomAttributeMetadataToForm(CustomAttributeDescriptor desc,
            boolean showFulfillmentTimeOption) {
        CustomAttributeInfo attrInfo = newCustomAttributeInfo();
        AttributeWhenENC[] choices = desc.getWhenChoices();
        List whenChoices = new ArrayList();
        if (choices != null) {
            for (int i = 0; i < choices.length; i++) {
                AttributeWhenENC choice = choices[i];
                if (!choice.isFulfillmentTime() || showFulfillmentTimeOption) {
                    whenChoices.add(choices[i].toString());
                }
            }
        }
        if (desc.isLicenseAttribute() && whenChoices.isEmpty())
            return;
        attrInfo.setWhenChoices(whenChoices);
        attrInfo.setName(desc.getName());
        if (FlexGeneratorHelper.VM_PLATFORMS.equals(desc.getName())) {
            attrInfo.setType(desc.getType().toString());
            // attrInfo.setDisplayType(desc.getDisplayType());
            List<String> validValues = new ArrayList();
            validValues.add(FlexGeneratorHelper.NOT_USED);
            validValues.add(FlexGeneratorHelper.RESTRICTED_TO_PHYSICAL);
            validValues.add(FlexGeneratorHelper.RESTRICTED_TO_VIRTUAL);
            attrInfo.setValidValues(validValues);
        }
        else if (FlexGeneratorHelper.ALLOW_TERMINAL_SERVER.equals(desc.getName())) {
            attrInfo.setType(AttributeTypeENC.TEXT_TYPE.toString());
            // attrInfo.setDisplayType(desc.getDisplayType());
            List<String> validValues = new ArrayList();
            validValues.add(FlexGeneratorHelper.NOT_USED);
            validValues.add(FlexGeneratorHelper.ONE_CONNECTION);
            validValues.add(FlexGeneratorHelper.MANY_CONNECTIONS);
            attrInfo.setValidValues(validValues);
        }
        else {
            attrInfo.setType(desc.getType().toString());
            attrInfo.setDisplayType(desc.getDisplayType());
            if (desc.getValidValues() != null) {
                List vvals = desc.getValidValues();
                attrInfo.setValidValues(vvals);

                Iterator iter = vvals.iterator();
                TreeMap m = new TreeMap();
                while (iter.hasNext()) {
                    String plt = (String)iter.next();
                    String label = getResourceString(plt + ".label", null);
                    if ("".equals(label))
                        label = plt;
                    m.put(label, plt);
                }
                List c = new LinkedList(m.values());
                attrInfo.setSortedValidValues(c);
            }
        }
        attrInfo.setAllowEntitlementTime(desc.isAllowEntitlementTime());
        attrInfo.setAllowLicenseModelTime(desc.isAllowLicenseModelTime());
        attrInfo.setAllowFulfillmentTime(showFulfillmentTimeOption && desc.isAllowFulfillmentTime());

        attrInfo.setLicenseAttribute(desc.isLicenseAttribute());
        attrInfo.setRequired(desc.isRequired());
        if (desc.getMaxLength() != null) {
            attrInfo.setMaxLength(desc.getMaxLength());
        }
        attrInfo.setLicenseTechnology(desc.getLicenseTechnology().getName());
        attrInfo.setNeedForReporting(desc.isNeededForReporting());
        attrInfo.setNameSpace(((ICustomAttributeDescriptor)desc).getNameSpace());
        addCustomAttribute(attrInfo);
    }

    public boolean isShowProductInfoInSelectHostPageByDefault() {
        return showProductInfoInSelectHostPageByDefault;
    }

    public void setShowProductInfoInSelectHostPageByDefault(
            boolean showProductInfoInSelectHostPageByDefault) {
        this.showProductInfoInSelectHostPageByDefault = showProductInfoInSelectHostPageByDefault;
    }

    public String getNeedToDefineTimeZone() {
        return needToDefineTimeZone;
    }

    public void setNeedToDefineTimeZone(String needToDefineTimeZone) {
        this.needToDefineTimeZone = needToDefineTimeZone;
    }

    public List<String> getTimeZoneValues() {
        return timeZoneValues;
    }

    public void setTimeZoneValues(List<String> timeZoneValues) {
        this.timeZoneValues = timeZoneValues;
    }

    public String getSelectedFNPTimeZone() {
        return selectedFNPTimeZone;
    }

    public void setSelectedFNPTimeZone(String selectedFNPTimeZone) {
        this.selectedFNPTimeZone = selectedFNPTimeZone;
    }

    public String getSearchQualifier() {
        if (searchQualifier == null || "".equals(searchQualifier.trim()))
            searchQualifier = getDefaultSimpleSearchOperator();
        return searchQualifier;
    }

    public void setSearchQualifier(String searchQualifier) {
        this.searchQualifier = searchQualifier;
    }

    public String getDefaultSimpleSearchOperator() {
        return defaultSimpleSearchOperator;
    }

    public void setDefaultSimpleSearchOperator(String defaultSimpleSearchOperator) {
        this.defaultSimpleSearchOperator = defaultSimpleSearchOperator;
    }

    public String getSearchType() {
        if (searchType == null)
            return DEFAULT_SEARCH_TYPE;
        return searchType;
    }

    public String getViewType() {
        if (searchType == null)
            return "";
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getViewAction() {
        return viewAction;
    }

    public void setViewAction(String viewAction) {
        this.viewAction = viewAction;
    }

}
