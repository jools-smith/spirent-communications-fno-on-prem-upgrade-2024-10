
package com.flexnet.operations.web.actions.fulfillments;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;

import com.flexnet.operations.OperationsInitializer;
import com.flexnet.operations.api.IConsolidatedLicenseRecord;
import com.flexnet.operations.api.IFulfillmentManager;
import com.flexnet.operations.api.IFulfillmentRecord;
import com.flexnet.operations.api.IOperationsQuery;
import com.flexnet.operations.api.IResultsList;
import com.flexnet.operations.bizobjects.PortalConfigBO;
import com.flexnet.operations.publicapi.FulfillmentRecord;
import com.flexnet.operations.publicapi.LicenseFileDefinition;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OperationsServiceFactory;
import com.flexnet.operations.server.dto.LicenseFileDefinitionDTO;
import com.flexnet.operations.server.springbo.trusted.IManageTrustedService;
import com.flexnet.operations.services.SupportLicensesLandingConfigData;
import com.flexnet.operations.services.UtilityService;
import com.flexnet.operations.web.actions.OperationsBaseAction;
import com.flexnet.operations.web.beans.ConsolidatedLicensePropertiesBean;
import com.flexnet.operations.web.beans.EntitlementsStateBean;
import com.flexnet.operations.web.beans.FulfillmentPropertiesBean;
import com.flexnet.operations.web.beans.PortalStateBean;
import com.flexnet.operations.web.beans.SupportLicensesStateBean;
import com.flexnet.operations.web.forms.fulfillments.GeneratedConsolidatedLicensesForm;
import com.flexnet.operations.web.forms.fulfillments.LicenseFileBaseForm;
import com.flexnet.operations.web.forms.fulfillments.SupportLicensesLandingPageForm;
import com.flexnet.operations.web.util.CommonUtils;
import com.flexnet.operations.web.util.SessionUtils;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.exceptions.FlexnetBaseRuntimeException;
import com.flexnet.platform.web.actions.ActionsConstants;
import com.flexnet.platform.web.utils.SecurityUtil;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.exceptions.PRDBaseException;
import com.flexnet.products.publicapi.LicenseFileTypeENC;

public class LicenseFileBaseAction extends OperationsBaseAction {
    public static final String ID_KEY = "id";
    public static final String FORWARD_VIEW = "View";
    public static final String FORWARD_REDIRECT_TO_VIEW = "RedirectToView";
    public static final String FORWARD_SUCCESSFUL = "Successful";
    public static final String FORWARD_UNSUCCESSFUL = "Unsuccessful";
    public static final String FORWARD_REFRESH = "RefreshPage";

    protected void setupStatusForNoItemsSelected(HttpServletRequest request) {
        ActionMessages status = new ActionMessages();
        status.add(ActionsConstants.MSG_INFO, new ActionMessage(
                "packageProducts.Status.NothingSelected"));
        saveErrors(request, status);
    }

    protected void setupStatusForTooManyItemsSelected(HttpServletRequest request) {
        ActionMessages status = new ActionMessages();
        status.add(ActionsConstants.MSG_INFO, new ActionMessage(
                "packageProducts.Status.TooManySelected"));
        saveErrors(request, status);
    }

    protected String getIDKey() {
        return ID_KEY;
    }

    public ActionForward previewEmailFulfillments(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
            IllegalStateException, FlexnetBaseException {
        LicenseFileBaseForm trueForm = (LicenseFileBaseForm)form;
        try {
            if (!SecurityUtil.isValidRequest(request)) {
                return processInvalidRequest(mapping, request);
            }
            String[] selectedIDs = trueForm.getSelectedObjects();
            IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();
            Long soldTo = null;
            if (selectedIDs.length > 0) {
                SupportLicensesStateBean slBean = SessionUtils.getSupportLicensesStateBean(request);
                HashSet s = new HashSet();
                for (int i = 0; i < selectedIDs.length; i++) {
                    String selectedId = selectedIDs[i];
                    IFulfillmentRecord fr = fMgr.getFulfillmentByID(new Long(selectedId));
                    if (fr.isTrustedType()) {
                        String errorKey = "Fulfillment.cannotEmailTrustedFulfillments";
                        throw UtilityService.makeOperationsException(errorKey, new Object[] {});
                    }
                    if (soldTo == null) {
                        soldTo = fr.getLineItem().getParentEntitlement().getSoldTo().getId();
                    }
                    else if (!soldTo.equals(fr.getLineItem().getParentEntitlement().getSoldTo()
                            .getId())) {
                        String errorKey = "Fulfillment.cannotEmailFulfillmentsWithDifferentSoldTo";
                        throw UtilityService.makeOperationsException(errorKey, new Object[] {});
                    }
                    s.add(selectedId);
                }
                slBean.setSelectedFulfillmentsForEmail(s);
                SessionUtils.setSupportLicensesStateBean(request, slBean);
                return mapping.findForward(FORWARD_SUCCESSFUL);
            }
            else {
                setupStatusForNoItemsSelected(request);
                return mapping.findForward(FORWARD_REDIRECT_TO_VIEW);
            }
        }
        catch (FlexnetBaseRuntimeException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_REFRESH);
        }
        catch (OperationsException ex) {
            rollback();
            setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_REFRESH);
        }
    }

    public ActionForward saveToFile(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            LicenseFileBaseForm trueForm = (LicenseFileBaseForm)form;
            IFulfillmentManager fMgr = OperationsServiceFactory.getFulfillmentManager();
            String[] selectedObjects = trueForm.getSelectedObjects();
            if (selectedObjects.length > 0) {
                String fulfillId = selectedObjects[0];
                if (fulfillId != null) {
                    if (trueForm.isTrustedType()) {
                        downloadTrustedResponseFile(response, fulfillId);
                    }
                    else {
                        IFulfillmentRecord fr = fMgr.getFulfillmentByID(new Long(fulfillId));
                        // new code for multiple lic files.
                        Map<LicenseFileDefinition, Object> licenseFiles = fMgr
                                .getDownloadLicenseFiles(fr);
                        Map<LicenseFileDefinition, String> fileNames = fr.getLicenseFileNames();
                        Set<LicenseFileDefinition> keySet = licenseFiles.keySet();
                        Iterator<LicenseFileDefinition> keyIterator = keySet.iterator();
                        if (licenseFiles.values().size() > 1) {
                            ServletOutputStream os = null;
                            ZipOutputStream out = null;
                            response.setContentType("Flexera");
                            String disHeader = "Attachment; Filename=\"license.zip\"";
                            response.setHeader("Content-Disposition", disHeader);
                            os = response.getOutputStream();
                            out = new ZipOutputStream(os);
                            int binCcount = 0;
                            int textCcount = 0;
                            while (keyIterator != null && keyIterator.hasNext()) {
                                LicenseFileDefinitionDTO licFileDefDTO = (LicenseFileDefinitionDTO)keyIterator
                                        .next();
                                LicenseFileTypeENC fileType = licFileDefDTO.getLicenseStorageType();
                                String fileName = fileNames.get(licFileDefDTO);
                                if (fileName == null || fileName.equals("")) {
                                    if (fileType.equals(LicenseFileTypeENC.BINARY)) {
                                        fileName = "binarylicense";
                                        if (binCcount > 0) {
                                            fileName += binCcount;
                                        }
                                        binCcount++;
                                    }
                                    else {
                                        fileName = CommonUtils.getLicenseFileNameWithoutExt();
                                        if (textCcount > 0) {
                                            fileName += textCcount;
                                        }
                                        textCcount++;
                                        if (!fileName.endsWith(".lic"))
                                            fileName = fileName + ".lic";
                                    }
                                }
                                if (fileType.equals(LicenseFileTypeENC.BINARY)) {
                                    byte[] binarylicFile = (byte[])licenseFiles.get(licFileDefDTO);
                                    if (binarylicFile != null && binarylicFile.length > 0) {
                                        ZipEntry e = new ZipEntry(fileName);
                                        out.putNextEntry(e);
                                        out.write(binarylicFile, 0, binarylicFile.length);
                                        out.closeEntry();
                                    }
                                }
                                else {
                                    String textlicFile = (String)licenseFiles.get(licFileDefDTO);
                                    if (textlicFile != null)
                                        textlicFile = textlicFile.replaceAll("\n", "\r\n");
                                    byte[] byteLicFile = textlicFile.getBytes();
                                    if (byteLicFile != null && byteLicFile.length > 0) {
                                        ZipEntry e = new ZipEntry(fileName);
                                        out.putNextEntry(e);
                                        out.write(byteLicFile, 0, byteLicFile.length);
                                        out.closeEntry();
                                    }
                                }
                            }
                            out.close();
                            return null;
                        }
                        else if (licenseFiles != null && licenseFiles.values().size() == 1) {
                            // follow old path
                            while (keyIterator != null && keyIterator.hasNext()) {
                                LicenseFileDefinitionDTO licFileDefDTO = (LicenseFileDefinitionDTO)keyIterator
                                        .next();
                                LicenseFileTypeENC fileType = licFileDefDTO.getLicenseStorageType();
                                String fileName = fileNames.get(licFileDefDTO);
                                if (fileName == null || fileName.equals("")) {
                                    if (fileType.equals(LicenseFileTypeENC.BINARY)) {
                                        fileName = "binarylicense";
                                    }
                                    else {
                                        fileName = CommonUtils.getLicenseFileName();
                                    }
                                }
                                if (fileType.equals(LicenseFileTypeENC.BINARY)) {
                                    byte[] binarylicFile = (byte[])licenseFiles.get(licFileDefDTO);
                                    if (binarylicFile != null && binarylicFile.length > 0) {
                                        response.setContentType("Flexera");
                                        String disHeader = "Attachment; Filename=\"" + fileName
                                                + "\"";
                                        response.setHeader("Content-Disposition", disHeader);
                                        ServletOutputStream os = response.getOutputStream();
                                        os.write(binarylicFile, 0, binarylicFile.length);
                                        os.close();
                                        return null;
                                    }
                                }
                                else {
                                    String textlicFile = (String)licenseFiles.get(licFileDefDTO);
                                    if (textlicFile != null)
                                        textlicFile = textlicFile.replaceAll("\n", "\r\n");
                                    if (textlicFile != null && textlicFile.length() > 0) {
                                        response.setContentType("acresso");
                                        String disHeader = "Attachment; Filename=\"" + fileName
                                                + "\"";
                                        response.setHeader("Content-Disposition", disHeader);
                                        PrintWriter out = response.getWriter();
                                        out.write(textlicFile);
                                        out.close();
                                    }
                                }
                            }
                        }
                        else {
                            setupErrorMessage(request,
                                    "supportLicenses.status.noLicenseForThisFulfillment",
                                    new String[] { fr.getFulfillmentId() });
                            return mapping.findForward(FORWARD_REDIRECT_TO_VIEW);
                        }
                        // end of multiple lic files.
                    }
                }
            }
        }
        catch (OperationsException ex) {
            this.setupErrorMessageForErrorFromBO(request, ex, ActionsConstants.MSG_ERROR);
            return mapping.findForward(FORWARD_REDIRECT_TO_VIEW);
        }
        return mapping.findForward(FORWARD_SUCCESSFUL);
    }

    private void downloadTrustedResponseFile(HttpServletResponse response, String trustedRequestID)
            throws OperationsException, IOException {
        IManageTrustedService trustedMgr = OperationsServiceFactory.getManageTrustedService();
        String responseFile = trustedMgr.downloadResponseFile(new Long(trustedRequestID));
        response.setContentType("Flexera");
        String disHeader = "Attachment; Filename=responseXML.xml";
        response.setHeader("Content-Disposition", disHeader);
        PrintWriter out = response.getWriter();
        out.write(responseFile);
        out.close();
    }

    public ActionForward print(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws OperationsException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException, IllegalStateException {
        try {
            IFulfillmentManager fmtMgr = OperationsServiceFactory.getFulfillmentManager();
            LicenseFileBaseForm trueForm = (LicenseFileBaseForm)form;
            String[] selectedIDs = trueForm.getSelectedObjects();
            if (selectedIDs.length > 0) {
                for (int i = 0; i < selectedIDs.length; i++) {
                    IFulfillmentRecord fmt = fmtMgr.getFulfillmentByID(new Long(selectedIDs[i]));
                    FulfillmentPropertiesBean fulBean = new FulfillmentPropertiesBean(fmt,
                            trueForm.getDateFormat(), ThreadContextUtil.getLocale(), true, true);
                    trueForm.addObject(fulBean);
                }
                return mapping.findForward(FORWARD_SUCCESSFUL);
            }
            else {
                setupStatusForNoItemsSelected(request);
                return mapping.findForward(FORWARD_REDIRECT_TO_VIEW);
            }
        }
        catch (OperationsException ex) {
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

    protected void loadForm(HttpServletRequest request, LicenseFileBaseForm trueForm, List objects)
            throws OperationsException, FlexnetBaseException {
        List beanList = new ArrayList();
        for (Iterator iter = objects.iterator(); iter.hasNext();) {
            IFulfillmentRecord ai = (IFulfillmentRecord)iter.next();
            FulfillmentPropertiesBean fulBean = new FulfillmentPropertiesBean(ai,
                    trueForm.getDateFormat(), trueForm.getUserLocale());
            beanList.add(fulBean);
        }
        loadFormFromBean(request, trueForm, beanList);
    }

    protected void loadFormFromBean(HttpServletRequest request, LicenseFileBaseForm trueForm,
            List objects) throws OperationsException, FlexnetBaseException {
        if (objects.isEmpty()) {
            LicenseFileBaseForm.FulfillmentRecordInfo oInfo = loadObjectInfoFromBean(request,
                    trueForm, null);
            trueForm.addFulfillmentRecord(oInfo);
            trueForm.setHasDataRecords(false);
        }
        else {
            for (Iterator iter = objects.iterator(); iter.hasNext();) {
                FulfillmentPropertiesBean ai = (FulfillmentPropertiesBean)iter.next();
                LicenseFileBaseForm.FulfillmentRecordInfo oInfo = loadObjectInfoFromBean(request,
                        trueForm, ai);
                trueForm.addFulfillmentRecord(oInfo);
            }
            trueForm.setHasDataRecords(true);
        }
    }

    protected void loadConsolidatedLicensesForm(HttpServletRequest request,
            GeneratedConsolidatedLicensesForm trueForm, List consolidatedLicenses)
            throws OperationsException, FlexnetBaseException {
        for (Iterator iter = consolidatedLicenses.iterator(); iter.hasNext();) {
            IConsolidatedLicenseRecord clr = (IConsolidatedLicenseRecord)iter.next();
            ConsolidatedLicensePropertiesBean clbBean = new ConsolidatedLicensePropertiesBean(clr,
                    trueForm.getDateFormat());
            FulfillmentRecord[] records = clr.getFulFillments();
            for (int i = 0; i < records.length; ++i) {
                IFulfillmentRecord ai = (IFulfillmentRecord)records[i];
                FulfillmentPropertiesBean fulBean = new FulfillmentPropertiesBean(ai,
                        trueForm.getDateFormat(), trueForm.getUserLocale());
                trueForm.addObject(clbBean, fulBean);
            }
        }
    }

    protected LicenseFileBaseForm.FulfillmentRecordInfo loadObjectInfoFromBean(
            HttpServletRequest request, LicenseFileBaseForm trueForm,
            FulfillmentPropertiesBean fulfillRec) throws PRDBaseException, OperationsException {
        PortalStateBean portalBean = SessionUtils.getPortalStateBean(request);
        SupportLicensesLandingConfigData configData = OperationsInitializer
                .getPublisherSupportLicensesLandingConfigData();
        List columns = configData.getManageLicensesCols();
        String unlimitedResourceStr = trueForm.getResourceString(
                "createLicenseModel.when.unlimited", new String[0]);
        String ignorePolicyResourceStr = trueForm.getResourceString(
                "portal.lineItemDetails.label.ignorePolicy", new String[0]);
        boolean isEntitlementIdLogin = portalBean.getIsLoggedInWithEntitlementID().booleanValue();
        boolean isLineItemIdLogin = portalBean.getIsLoggedInWithLineItemID().booleanValue();
        LicenseFileBaseForm.FulfillmentRecordInfo oInfo = trueForm.newFulfillmentRecord();
        Iterator colIter = columns.iterator();
        while (colIter.hasNext()) {
            PortalConfigBO column = (PortalConfigBO)colIter.next();
            if (column.isDisplay()) {
                String fieldName = column.getFieldName();

                if ("portal.supportLicenses.TableColumn.OrderableDescription".equals(fieldName)) {
                    trueForm.setShowProductDescription(true);
                    continue;
                }

                if (fieldName.equals("portal.supportLicenses.TableColumn.orderableVersion"))
                    continue;
                if (fieldName.equals("portal.supportLicenses.TableColumn.entitlementlineItem")
                        && isLineItemIdLogin)
                    continue;
                SupportLicensesLandingPageForm.ColumnInfo cInfo = trueForm.newColumnInfo();
                cInfo.setFieldName(fieldName);
                cInfo.setScreenName(column.getScreenName());
                cInfo.setSortable(column.isSortable());
                cInfo.setDisplay(column.isDisplay());
                cInfo.setTrimType(column.getTrimValue());
                cInfo.setCustomAttribute(column.isCustomAttribute());
                cInfo.setCustomHostAttribute(column.isCustomHostAttribute());

                if (fieldName.equals("portal.activations.TableColumn.Type")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.fulfillId")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_FULFILLMENT_ID);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.entitlementlineItem")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_ENTITLEMENT_LINEITEM_ID);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.orderable")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.hostIds")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_HOST_ID);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.nodeLockedId")) {
                    cInfo.setSortColumnName("");
                    cInfo.setSortable(false);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.fulfillAmount")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_FULFILLMENT_COUNT);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.lifecycleaction")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_FULFILLMENT_LIFECYCLE_STATUS);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.activationDate")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_ACTIVATION_DATE);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.expirationDate")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_EXPIRATION_DATE);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.soldTo")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_SOLD_TO_DISPLAY_NAME);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.hostId")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_HOST_ID);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.nodelockedhostId")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.startDate")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_START_DATE);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.licenseText")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.ShipToEmail")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.ShipToAddress")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.OrderId")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.OrderLineNumber")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.VersionDate")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.PartNumber")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName
                        .equals("portal.supportLicenses.TableColumn.PartNumberDescription")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.LicenseModel")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_LICENSE_MODEL_TYPE);
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.Description")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.TotalCopies")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName
                        .equals("portal.supportLicenses.TableColumn.NumberOfCopiesRemaining")) {
                    cInfo.setSortColumnName(cInfo.COLUMN_REMAINING_COPIES);
                }
                else if (fieldName
                        .equals("portal.supportLicenses.TableColumn.extraActivationsAllowed")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName
                        .equals("portal.supportLicenses.TableColumn.remainingExtraActivations")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.overdraftMax")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.overdraftRemaining")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.overdraftCeiling")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.overdraftFloor")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.noOfRehosts")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.noOfReturns")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (fieldName.equals("portal.supportLicenses.TableColumn.noOfRepairs")) {
                    cInfo.setSortable(false);
                    cInfo.setSortColumnName("");
                }
                else if (column.isCustomAttribute()) {
                    if (configData.isStringAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_STRING_LICENSE_MODEL_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isBooleanAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_BOOLEAN_LICENSE_MODEL_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isNumberAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_NUMBER_LICENSE_MODEL_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isDateAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_DATE_LICENSE_MODEL_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else {
                        // no custom attribute by this name exists
                        cInfo.setSortable(false);
                    }
                }
                else if (column.isCustomHostAttribute()) {
                    if (configData.isStringHostAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_STRING_CUSTOM_HOST_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isBooleanHostAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_BOOLEAN_CUSTOM_HOST_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isNumberHostAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_NUMBER_CUSTOM_HOST_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else if (configData.isDateHostAttribute(column.getFieldName())) {
                        cInfo.setSortable(true);
                        cInfo.setSortColumnName(cInfo.COLUMN_DATE_CUSTOM_HOST_ATTRIBUTE
                                + column.getFieldName());
                    }
                    else {
                        // no custom attribute by this name exists
                        cInfo.setSortable(false);
                    }
                }
                cInfo.setDisplayOrder(column.getDisplayOrder());
                cInfo.setDisplaySize(column.getDisplaySize());
                String val = "";
                if (fulfillRec != null) {
                    oInfo.setId(fulfillRec.getId().toString());
                    oInfo.setLineItemObjId(fulfillRec.getLineItemObjId().toString());
                    oInfo.setTrustedReqObjId(fulfillRec.getTrustedRequestId());
                    oInfo.setTrustedType(fulfillRec.isTrustedType());
                    oInfo.setRedundantServerLicense(fulfillRec.isRedundantServerLicense());
                    oInfo.setLineItemStatus(fulfillRec.getLineItemStatus());
                    if (fieldName.equals("portal.activations.TableColumn.Type")) {
                        val = fulfillRec.getType();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.fulfillId")) {
                        val = fulfillRec.getFulfillId();
                    }
                    else if (fieldName
                            .equals("portal.supportLicenses.TableColumn.entitlementlineItem")) {
                        val = fulfillRec.getLineItemId();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.orderable")) {
                        cInfo.setOrderables(fulfillRec.getEntitledOrderables());
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.hostIds")) {
                        val = fulfillRec.getLicenseHost();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.nodeLockedId")) {
                        cInfo.setNodeHostIds(fulfillRec.getNodeLockedHostIds());
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.fulfillAmount")) {
                        val = fulfillRec.getFulfillAmount() + "";
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.lifecycleaction")) {
                        String str = fulfillRec.getLifeCycleStatus();
                        val = trueForm.getResourceString("supportLicenses.lifecycleAction." + str,
                                new String[0]);
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.activationDate")) {
                        val = fulfillRec.getActivationDate();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.expirationDate")) {
                        val = fulfillRec.getExpirationDate();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.soldTo")) {
                        val = fulfillRec.getSoldTo();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.startDate")) {
                        val = fulfillRec.getStartDate();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.ShipToEmail")) {
                        val = fulfillRec.getShipToEmail();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.ShipToAddress")) {
                        val = fulfillRec.getShipToAddress();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.OrderId")) {
                        val = fulfillRec.getOrderId();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.OrderLineNumber")) {
                        val = fulfillRec.getOrderLineNum();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.VersionDate")) {
                        val = fulfillRec.getVersionDate();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.PartNumber")) {
                        val = fulfillRec.getPartNumber();
                    }
                    else if (fieldName
                            .equals("portal.supportLicenses.TableColumn.PartNumberDescription")) {
                        val = fulfillRec.getPartNumberDescription();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.LicenseModel")) {
                        val = fulfillRec.getLicenseModel();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.Description")) {
                        val = fulfillRec.getLineItemDescription();
                    }
                    else if (fieldName
                            .equals("portal.supportLicenses.TableColumn.NumberOfCopiesRemaining")) {
                        val = fulfillRec.getRemainingCount();
                    }
                    else if (fieldName.equals("portal.supportLicenses.TableColumn.TotalCopies")) {
                        val = fulfillRec.getSeatCount();
                    }
                    else if (column.isCustomAttribute()) {
                        val = fulfillRec.getCustomAttr(fieldName);
                    }
                    else if (column.isCustomHostAttribute()) {
                        val = fulfillRec.getCustomHostAttr(fieldName);
                    }
                    cInfo.setVal(val);
                }
                oInfo.addColValue(cInfo);
            }
        }
        return oInfo;
    }

    public String lastView(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) {
        LicenseFileBaseForm trueForm = (LicenseFileBaseForm)form;
        try {
            trueForm.setViewAction("");
            IFulfillmentManager fmtMgr = OperationsServiceFactory.getFulfillmentManager();
            EntitlementsStateBean esBean = SessionUtils.getEntitlementsStateBean(request);
            IResultsList rl = esBean.getFulfillmentsItemSet();
            IOperationsQuery qry = rl.getQuery();
            rl = fmtMgr.getFulfillmentsPropertiesBeans(qry, trueForm.getDateFormat());
            if (rl != null) {
                List activationIns = rl.nextPage();
                loadPagingAttributes(request, trueForm, rl, activationIns,
                        esBean.getFulfillmentsItemSetPaginationAttributes());
                loadFormFromBean(request, trueForm, activationIns);
                trueForm.setSearchType(SEARCHTYPE_RECENT);
                SessionUtils.setEntitlementsStateBean(request, esBean);
                return FORWARD_SUCCESSFUL;
            }
        }
        catch (Exception ex) {}
        return FORWARD_UNSUCCESSFUL;
    }
}
