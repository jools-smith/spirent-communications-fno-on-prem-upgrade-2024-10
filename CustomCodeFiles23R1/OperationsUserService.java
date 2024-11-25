
package com.flexnet.operations.services;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.persistence.FlushModeType;

import com.flexnet.lfs.webservice.DetachUserRequest;
import com.flexnet.operations.lfs.service.DeviceService;
import com.flexnet.platform.config.FeatureFlag;
import com.flexnet.platform.config.FeatureFlagUtil;
import com.flexnet.platform.config.data.OperationsUserServiceConfig;
import com.flexnet.platform.web.usercache.WebServiceUserCacheEntryKey;
import com.flexnet.platform.web.usercache.WebServiceUserCacheProviderFactory;
import org.apache.axis.MessageContext;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;

import com.flexnet.operations.api.CustomUserAttributeBean;
import com.flexnet.operations.api.ICustomUserAttribute;
import com.flexnet.operations.api.IEntitlement;
import com.flexnet.operations.api.IEntitlementManager;
import com.flexnet.operations.api.IOperationsQuery;
import com.flexnet.operations.api.IOperationsRoleManager;
import com.flexnet.operations.api.IOperationsUserManager;
import com.flexnet.operations.api.IOperatorENC;
import com.flexnet.operations.api.IOpsUser;
import com.flexnet.operations.api.IOrgHierarchyManager;
import com.flexnet.operations.api.IOrgUnitInterface;
import com.flexnet.operations.api.IOrgUnitInterface.SeededOrg;
import com.flexnet.operations.api.IQueryParameter;
import com.flexnet.operations.api.IQueryParameterENC;
import com.flexnet.operations.api.IResultsList;
import com.flexnet.operations.api.IRole;
import com.flexnet.operations.api.IWebRegKey;
import com.flexnet.operations.bizobjects.entitlements.EntitlementBO;
import com.flexnet.operations.bizobjects.entitlements.EntitlementLineItemBO;
import com.flexnet.operations.bizobjects.orghierarchy.OpsOrgUnit;
import com.flexnet.operations.bizobjects.orghierarchy.OrgHierarchy;
import com.flexnet.operations.exceptions.OPSBaseException;
import com.flexnet.operations.notification.EntitlementEntityProcessor;
import com.flexnet.operations.notification.EntityNotificationInvoker;
import com.flexnet.operations.notification.EventHeader.EventType;
import com.flexnet.operations.notification.OrgEntityProcessor;
import com.flexnet.operations.notification.UserEntityProcessor;
import com.flexnet.operations.publicapi.OperationsEntity;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.operations.publicapi.OperationsServiceFactory;
import com.flexnet.operations.publicapi.OrganizationUnit;
import com.flexnet.operations.publicapi.SimpleEntitlement;
import com.flexnet.operations.server.dao.ProductCategoryDAO;
import com.flexnet.operations.services.userManagement.OrgAuthorizationService;
import com.flexnet.operations.util.ParseUser.UserObj;
import com.flexnet.operations.web.util.CommonUtils;
import com.flexnet.operations.webservices.TransactionHelper;
import com.flexnet.operations.webservices.WSCommonUtils;
import com.flexnet.operations.webservices.dto.common.SimpleQueryTypeDTO;
import com.flexnet.operations.webservices.dto.common.SimpleSearchTypeDTO;
import com.flexnet.operations.webservices.dto.useraccount.OrgDetails;
import com.flexnet.operations.webservices.fne.dto.DeviceIdentifierDTO;
import com.flexnet.operations.webservices.fne.dto.DeviceQueryDataTypeDTO;
import com.flexnet.operations.webservices.fne.dto.GetDeviceCountResponseTypeDTO;
import com.flexnet.operations.webservices.fne.dto.GetDevicesCountRequestTypeDTO;
import com.flexnet.operations.webservices.fne.dto.GetDevicesParametersTypeDTO;
import com.flexnet.operations.webservices.fne.dto.GetDevicesRequestTypeDTO;
import com.flexnet.operations.webservices.fne.dto.GetDevicesResponseTypeDTO;
import com.flexnet.operations.webservices.fne.dto.UpdateDevDataTypeDTO;
import com.flexnet.operations.webservices.fne.dto.UpdateDevRequestTypeDTO;
import com.flexnet.operations.webservices.fne.sharedimpl.ManageDeviceServiceWebservice;
import com.flexnet.platform.bizobjects.AuthenticationScheme;
import com.flexnet.platform.bizobjects.Contact;
import com.flexnet.platform.bizobjects.Domain;
import com.flexnet.platform.bizobjects.ExtendedPropertyMetadata;
import com.flexnet.platform.bizobjects.OrgUnit;
import com.flexnet.platform.bizobjects.OrgUnitType;
import com.flexnet.platform.bizobjects.Role;
import com.flexnet.platform.bizobjects.User;
import com.flexnet.platform.bizobjects.UserOrgRole;
import com.flexnet.platform.config.AppConfigUtil;
import com.flexnet.platform.entities.AlertHandlerConfiguration;
import com.flexnet.platform.entities.AlertSubscription;
import com.flexnet.platform.entities.Entity;
import com.flexnet.platform.entities.EntityProperty;
import com.flexnet.platform.entities.EntityPropertySet;
import com.flexnet.platform.exceptions.DuplicateContactException;
import com.flexnet.platform.exceptions.DuplicateUserException;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.exceptions.FlexnetHibernateException;
import com.flexnet.platform.exceptions.MultipleDataFoundException;
import com.flexnet.platform.exceptions.NoDataFoundException;
import com.flexnet.platform.exceptions.ValidationFailedException;
import com.flexnet.platform.server.dao.DaoException;
import com.flexnet.platform.services.alert.AlertService;
import com.flexnet.platform.services.logging.LogMessage;
import com.flexnet.platform.services.logging.Logger;
import com.flexnet.platform.services.logging.LoggingService;
import com.flexnet.platform.services.persistence.FlexTransactional;
import com.flexnet.platform.services.persistence.PersistenceService;
import com.flexnet.platform.services.userManagement.EmptyAuthenticationService;
import com.flexnet.platform.services.userManagement.NativeAuthenticationService;
import com.flexnet.platform.util.DateUtility;
import com.flexnet.platform.util.DbUtil;
import com.flexnet.platform.util.PermissionUtil;
import com.flexnet.platform.util.ThreadContext;
import com.flexnet.platform.util.Validator;
import com.flexnet.platform.web.utils.SpringBeanFactory;
import com.flexnet.platform.web.utils.ThreadContextUtil;
import com.flexnet.products.bizobjects.ProductCategoryDO;
import com.flexnet.products.persistence.AttributeQueryInfo;
import com.flexnet.products.persistence.Expr;
import com.flexnet.products.persistence.QueryBuilder;
import com.flexnet.products.persistence.QueryUtil;
import com.flexnet.products.persistence.SelectQueryBuilder;
import com.flexnet.products.publicapi.IPermissionENC;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.hibernate.transform.Transformers;
import org.springframework.beans.factory.annotation.Autowired;

public class OperationsUserService implements IOperationsUserManager {

    protected static Logger logger = LoggingService.getLogger("flexnet.ops.services");
    protected ProductCategoryDAO productCategoryDAO;
    
    private boolean allowForceDelete = false;
    
    public boolean isAllowForceDelete() {
		return allowForceDelete;
	}

	public void setAllowForceDelete(boolean allowForceDelete) {
		this.allowForceDelete = allowForceDelete;
	}

	public OperationsUserService(){

    }

    @Autowired
    DeviceService deviceService;

    /**
     * This method checks if the logged in user has the specific permission
     * <code>IPermissionENC.VIEW_MANAGE_USERS</code>. It throws an OperationsException if user does
     * not have this permission.
     * 
     * @throws OperationsException
     */
    private void authorizationCheck() throws OperationsException {
        IPermissionENC permission = IPermissionENC.VIEW_MANAGE_USERS;
        if (!PermissionUtil.hasPermissionAlias(permission.getName())) {
            logger.debug(new LogMessage(
                    "Not enough permissions for operation.  Missing permission = "
                            + permission.getName()));
            throw UtilityService.makeOperationsException("notEnoughPermissions",
                    new Object[] { permission.getName() });
        }
    }

    /*
     * Creates a user with FLEXnet domain and FLEXnet authentication scheme
     * @return IOpsUser, an instance of IOpsUser 
     */

    public IOpsUser createUser() throws OperationsException {
        // commenting the method out until we completely understand how to distinguish
        // self-registered user(s) from others.
        // authorizationCheck();
        OpsUserImpl user = new OpsUserImpl();
        user.setSuperAdminCheckForExpiryDate(true);
        return user;
    }

    public User getDefaultPortalUser() throws OperationsException {
        User user = null;
        try {
            HashMap searchValues = new HashMap();
            searchValues.put(User.USERID, User.PORTAL_USER);
            searchValues.put(User.TENANT_ID, ThreadContextUtil.getTenantId());
            user = (User)Entity.getOne(User.class, searchValues);
        }
        catch (FlexnetBaseException e) {
            logger.error(new LogMessage("Operation failed because user \"{0}\" could not be found",
                    User.PORTAL_USER, e));
            throw new OperationsException(e);
        }
        return user;
    }

    /*
     * Returns the roles for the specified user in the given organization.
     * If the user does not have login status, then empty role set is returned.
     * @param uid, the id of the user.
     * @param orgUnitId, the id of the organization 
     * @return list of roles for the user in the given organization.
     */
    public List<IRole> getRoles(Long uid, Long orgUnitId) throws OperationsException {
        PersistenceService ps = PersistenceService.getInstance();
        List<IRole> roles = new ArrayList<IRole>();
        try {
            Contact c = (Contact)ps.load(Contact.class, uid);
            User u = c.getUserInfo();
            if (u == null)
                return roles;
            String hQuery = ps.getQuery("UserService.getRolesInOrgUnit");
            Query hqlQryObj = ps.getTransaction().getHibernateSession().createQuery(hQuery);
            hqlQryObj.setLong(0, u.getId().longValue());
            hqlQryObj.setLong(1, orgUnitId.longValue());
            List rObs = hqlQryObj.list();
            if (rObs != null) {
                Iterator iter = rObs.iterator();
                while (iter.hasNext()) {
                    Role role = (Role)iter.next();
                    RoleImpl rImpl = new RoleImpl(role);
                    roles.add(rImpl);
                }
            }
            return roles;
        }
        catch (FlexnetBaseException fx) {
            logger.debug(new LogMessage(fx.getMessage()));
            throw new OperationsException(fx);
        }
    }

    private void checkUserPermissions(Set orgs) throws OperationsException {
        Iterator<OrgUnit> it = orgs.iterator();
        while (it.hasNext()) {
            OrgUnit org = it.next();
            checkUserPermissions(org.getId());
        }
    }

    private void checkUserPermissions(long orgId) throws OperationsException {
        IPermissionENC perm = null;
        if (ThreadContextUtil.isLoggedInFromPortal())
            perm = IPermissionENC.PORTAL_VIEW_USERS;
        else
            perm = IPermissionENC.VIEW_MANAGE_USERS;

        IPermissionENC childPerm = IPermissionENC.PORTAL_MANAGE_CHILD_USERS;
        if (!OrgAuthorizationService.hasPermission(orgId, perm.getName(),
                IPermissionENC.VIEW_MANAGE_CUSTOMER_USERS.getName(),
                IPermissionENC.VIEW_MANAGE_PARTNER_USERS.getName(),
                childPerm.getName())) {
            logger.debug(new LogMessage(
                    "Not enough permissions for operation.  Missing permission = " + perm.getName()));
            throw UtilityService.makeOperationsException("notEnoughPermissions",
                    new Object[] { perm.getName() });
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<OrgUnit> getOrgsList(Set<Long> ids)
            throws OperationsException {
        String query = PersistenceService.getInstance().getQuery("OrgUnit.getOrgsByIDSet");
        List<OrgUnit> resultList = new ArrayList<OrgUnit>();
        try {
            resultList = PersistenceService.getInstance()
                    .getTransaction()
                    .getHibernateSession()
                    .createQuery(query)
                    .setParameterList("ids", ids)
                    .setParameter("tenantId", ThreadContextUtil.getTenantId())
                    .list();
        }
        catch (HibernateException | FlexnetBaseException e) {
            logger.debug(new LogMessage("Error while fetching the records: " + e));
        }
        catch (Exception e) {
            logger.debug(new LogMessage("Error while fetching the records: " + e));
        }
        return resultList;
    }

    private void checkUserViewPermissions(Set<OrgUnit> orgs) throws OperationsException {
    	Set<Long> orgIds = orgs.stream()
                .map(OrgUnit::getId).collect(Collectors.toSet());
    	
        List<OrgUnit> orgsList = getOrgsList(orgIds);
        checkUserViewPermission(orgsList);
    }

    private void checkUserViewPermission(List<OrgUnit> orgIds) throws OperationsException {
    	for(OrgUnit org : orgIds) {
	        IPermissionENC perm = null;
	        IPermissionENC childPerm = IPermissionENC.PORTAL_VIEW_CHILD_USERS;
	        if (ThreadContextUtil.isLoggedInFromPortal()) {
	            perm = IPermissionENC.PORTAL_VIEW_USERS;
	
	            if (!OrgAuthorizationService.hasPermission(org, perm.getName(), childPerm.getName())) {
	                logger.debug(new LogMessage(
	                        "Not enough permissions for operation.  Missing permission = "
	                                + perm.getName()));
	                throw UtilityService.makeOperationsException("notEnoughPermissions",
	                        new Object[] { perm.getName() });
	            }
	        }
	        else {
	            IPermissionENC[] perms = { IPermissionENC.VIEW_MANAGE_USERS, IPermissionENC.VIEW_USERS };
	            if (!OrgAuthorizationService.hasPermission(org, perms[0].getName(),
	                    childPerm.getName())
	                    && !OrgAuthorizationService.hasPermission(org, perms[1].getName(),
	                            childPerm.getName())) {
	                logger.debug(new LogMessage(
	                        "Not enough permissions for operation.  Missing permission = "
	                                + perms[1].getName()));
	                throw UtilityService.makeOperationsException("notEnoughPermissions",
	                        new Object[] { perms[1].getName() });
	            }
	
	        }
    	}
    }

    /**
     * Get the currently logged in user.
     * 
     * @return The user currently logged in.
     */
    public static IOpsUser getLoggedInUser() {
        return populateOpsUser(ThreadContextUtil.getUser());
    }

    /**
     * Get the first contact matching the specified email address.
     * 
     * @return The first contact unique ID if a match was found, null otherwise.
     * @throws OperationsException
     *             If something goes wrong.
     */
    @Override
    public Contact getContactByEmailAddress(String contactEmail) throws OperationsException {
        HashMap<String, String> searchValues = new HashMap<>();
        searchValues.put(Contact.EMAIL, contactEmail);

        try {
            @SuppressWarnings("unchecked")
            List<Contact> contacts = Entity.getAll(Contact.class, "", true, -1, 0, searchValues);

            if (contacts.isEmpty())
                return null;

            return contacts.get(0);
        }
        catch (FlexnetBaseException fx) {
            logger.debug(new LogMessage(fx.getMessage()));
            throw UtilityService.makeOperationsException(fx);
        }
    }

    @Override
    public IOpsUser getUserByContactIdNoPermCheck(Long contactId) throws OperationsException {
        return getUserByContactId(contactId, false, true);
    }

    private IOpsUser getUserByContactId(Long contactId, boolean forUI, boolean skipPermissionCheck)
            throws OperationsException {
        if (contactId == null) {
            logger.debug(new LogMessage("null uid passed to getUserByContactId"));
            throw UtilityService.makeOperationsException("nullUserIdForRetrieveUser");
        }
        IPermissionENC perm = null;
        if (ThreadContextUtil.isLoggedInFromPortal())
            perm = IPermissionENC.PORTAL_VIEW_USERS;
        else
            perm = IPermissionENC.VIEW_USERS;

        PersistenceService ps = PersistenceService.getInstance();
        Contact c = null;
        User u = null;
        try {
            c = (Contact)ps.load(Contact.class, contactId);
            u = c.getUserInfo();
            Set orgs = c.getBelongsToAll();
            User loggedInUser = ThreadContextUtil.getUser();
            if (!skipPermissionCheck) {
                if (loggedInUser != null && u != null && !loggedInUser.equals(u)) {
                    if (ThreadContextUtil.isPublisherUser()) {
                        if (!PermissionUtil
                                .hasPermissionAlias(IPermissionENC.VIEW_USERS.getName())) {
                            logger.debug(new LogMessage(
                                    "Not enough permissions for operation.  Missing permission = "
                                            + perm.getName()));
                            throw UtilityService.makeOperationsException("notEnoughPermissions",
                                    new Object[] { perm.getName() });
                        }
                    }
                    else {
                        if (!PermissionUtil
                                .hasPermissionAlias(IPermissionENC.PORTAL_VIEW_USERS.getName())) {
                            logger.debug(new LogMessage(
                                    "Not enough permissions for operation.  Missing permission = "
                                            + perm.getName()));
                            throw UtilityService.makeOperationsException("notEnoughPermissions",
                                    new Object[] { perm.getName() });
                        }
                    }
                    checkUserViewPermissions(orgs);
                }
            }
            if (u == null) {
                logger.debug(new LogMessage(
                        "uid passed corresponds to nonlogin user. Populating the contact details..."));
                return OperationsUserService.populateUserFromContact(c);
            }
            else {
                logger.debug(new LogMessage(
                        "uid passed corresponds to login user. Populating the complete user details.."));
                return OperationsUserService.populateOpsUser(u);
            }
        }
        catch (FlexnetBaseException fx) {
            logger.debug(new LogMessage(fx.getMessage()));
            throw UtilityService.makeOperationsException(fx);
        }
    }

    @Override
    public IOpsUser getUserByContactId(Long contactId, boolean forUI) throws OperationsException {
        return getUserByContactId(contactId, forUI, false);
    }

    /* 
     * Retrieve the user object for the specified uid. 
     * @param uid, the id of the user to retrieve.
     */
    public IOpsUser getUserByContactId(Long uid) throws OperationsException {
        return getUserByContactId(uid, false);
    }

    public IOpsUser getUserById(Long uid) throws OperationsException {
        if (uid == null) {
            logger.debug(new LogMessage("null uid passed to getUserById"));
            throw UtilityService.makeOperationsException("nullUserIdForRetrieveUser");
        }
        PersistenceService ps = PersistenceService.getInstance();
        Contact c = null;
        User u = null;
        try {
            u = (User)ps.load(User.class, uid);
            c = u.getContactInfo();
            Set orgs = c.getBelongsToAll();
            User loggedInUser = ThreadContextUtil.getUser();
            if (!loggedInUser.equals(u)) {
                checkUserViewPermissions(orgs);
            }
            return OperationsUserService.populateOpsUser(u);
        }
        catch (FlexnetBaseException fx) {
            logger.debug(new LogMessage(fx.getMessage()));
            throw UtilityService.makeOperationsException(fx);
        }
    }

    /*
     * Retrieves the user with the specified userId
     * @param userId, the userId of the user to retrieve.
     * @return IOpsUser instance.
     * @exception OperationsException is thrown when userId is null or a user could not be found with
     *             the given userId. 
     */

    public IOpsUser getUserByUserId(String userId) throws OperationsException {
        return getUserByUserId(userId, Domain.DEFAULT_DOMAIN);
    }

    public IOpsUser getUserByUserId(String userId, String domainName) throws OperationsException {
        User u = null;
        try {
            HashMap searchValues = new HashMap();
            searchValues.put("obj.userId_", userId);
            searchValues.put(User.DOMAIN_NAME, domainName);
            searchValues.put(User.TENANT_ID, ThreadContextUtil.getTenantId());

            u = (User)Entity.getOne(User.class, searchValues);
            if (u == null) {
                logger.debug(new LogMessage("user retrieved with specified userId is null."));
                throw new OperationsException("Invalid userId passed.");
            }

            User loggedInUser = ThreadContextUtil.getUser();

            // Thread context will not have user when the user is not logged in
            // loggedInUser will be null when this called from disabling user
            if (loggedInUser != null && !loggedInUser.getId().equals(u.getId())) {
                // check permissions
                Set orgs = u.getContactInfo().getBelongsToAll();
                checkUserViewPermissions(orgs);
            }

            return OperationsUserService.populateOpsUser(u);
        }
        catch (FlexnetBaseException ex) {
            logger.debug(new LogMessage(ex.getMessage()));
            throw UtilityService.makeOperationsException(ex);
        }
    }

    /*
     * Retrieves the user with the specified keys, firstName, lastName, emailAddress and phoneNumber
     * @param firstName, the firstName of the user
     * @param firstName, the last name of the user
     * @param firstName, the email address of the user
     * @param firstName, the phone number of the user
     * @return IOpsUser instance. 
     */
    public IOpsUser getUserByPKs(String firstName, String lastName, String emailAddress,
            String phoneNumber) throws OperationsException {
        Contact c = null;
        User u = null;
        try {
            Map<String, String> searchValues = new HashMap<String, String>();
            if (firstName != null)
                searchValues.put("obj.firstName_", firstName);
            if (lastName != null)
                searchValues.put("obj.lastName_", lastName);
            if (emailAddress != null)
                searchValues.put("obj.email_", emailAddress);
            if (phoneNumber != null)
                searchValues.put("obj.phone_", phoneNumber);
            c = (Contact)Entity.getOne(Contact.class, searchValues);
            u = c.getUserInfo();
            // check permissions
            Set orgs = c.getBelongsToAll();
            User loggedInUser = ThreadContextUtil.getUser();
            if (!loggedInUser.equals(u)) {
                checkUserViewPermissions(orgs);
            }
            if (u == null) {
                logger.debug(new LogMessage(
                        "the specified keys for getUserByPKs() resulted in retriveal of nonlogin user"));
                return OperationsUserService.populateUserFromContact(c);
            }
            else {
                logger.debug(new LogMessage(
                        "the specified keys for getUserByPKs() resulted in retriveal of login user"));
                return OperationsUserService.populateOpsUser(u);
            }
        }
        catch (FlexnetBaseException ex) {
            logger.debug(new LogMessage(ex.getMessage()));
            throw UtilityService.makeOperationsException("invalidPKsForRetrieveUser", new Object[] {
                    firstName, lastName, emailAddress, phoneNumber });
        }
    }

    private void updateWebServiceUserCacheAboutModifiedUser(IOpsUser user){
        if(user!=null)
            updateWebServiceUserCacheAboutModifiedUser(user.getUserId(),user.getDomain().getName());
    }

    private void updateWebServiceUserCacheAboutModifiedUser(User user){
        if(user!=null && user.getDomain()!=null)
            updateWebServiceUserCacheAboutModifiedUser(user.getUserId(),user.getDomain().getName());
    }

    private void updateWebServiceUserCacheAboutModifiedUser(String username, String domain){
        if(!FeatureFlagUtil.isFeatureFlagEnabled(FeatureFlag.FLAG_WEB_SERVICE_USER_LOGIN_CACHING))
            return;

        if(StringUtils.isBlank(username) || StringUtils.isBlank(domain))
            return;

        logger.info(new LogMessage("Deleting entry for user "+username+" from login cache"));

        /** Cached entry is no longer valid - hence delete the entry from cache**/
        WebServiceUserCacheProviderFactory.getUserCacheProvider().delete(new WebServiceUserCacheEntryKey(username,
                domain,ThreadContext.getInstance().getTenantId()));
    }

    public void deleteUser(IOpsUser user) throws OperationsException {
        try {
            if (user != null) {
            	setAllowForceDelete(false);
                OpsUserImpl userImpl = (OpsUserImpl)user;
                Contact contact = userImpl.getPlatformContactObject();
                User pltUser = contact.getUserInfo();
                Set orgs = contact.getBelongsToAll();
                checkUserPermissions(orgs);
                User loggedInUser = ThreadContextUtil.getUser();            

                if (CommonUtils.checkIfExists("User.getChannelPartners.Count", contact.getId())) {
                    setAllowForceDelete(true);
                    if (pltUser != null) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " owns entitlements"));
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserOwnsEntitlements",
                                new Object[] { user.getUserId() });
                    }
                    else {
                        logger.debug(new LogMessage("Cannot delete contact "
                                + contact.getDisplayName() + " references entitlements"));
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteContactOwnsEntitlements",
                                new Object[] { contact.getDisplayName() });
                    }
                }

                if (pltUser != null) {
                    if (loggedInUser.equals(pltUser)) {
                        throw UtilityService.makeOperationsException("cannotDeleteYourself",
                                new Object[] { user.getUserId() });
                    }
                    List<Long> seedUserIdz = ThreadContextUtil.getSeedUserIds();
                    if (seedUserIdz.contains(pltUser.getId())) {
                        logger.debug(new LogMessage("Seed users cannot be deleted from the system."));
                        throw UtilityService.makeOperationsException("cannotDeleteSeedUsers");
                    }
                    /* need to check for foreign key constraints */
                    if (CommonUtils.checkIfExists("User.getActivatableItems.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " owns line items"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserOwnsLineItems", new Object[] { user.getUserId() });
                    }
                    // if(checkIfExists("User.existCategories", pltUser.getId()))
                    // {
                    // logger.debug(new LogMessage("Cannot delete user " + user.getUserId() +
                    // " mapped to product categories"));
                    // throw
                    // UtilityService.makeOperationsException("cannotDeleteUserHasProductCategories",
                    // new Object[]{user.getUserId()});
                    // }
                    if (CommonUtils.checkIfExists("User.getEntitlements.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " owns entitlements"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserOwnsEntitlements",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getFulfillmentHistory.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " has fulfillment history records"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserHasFulfillmentHistory",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getHosts.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " owns hosts"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException("cannotDeleteUserOwnsHosts",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getImportJobs.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " created import jobs"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserCreatedImportJobs",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getImportSkuJobs.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " created import part number jobs"));
                        setAllowForceDelete(true);
                        throw UtilityService
                                .makeOperationsException("cannotDeleteUserCreatedSkuJobs",
                                        new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getShipmentRecords.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " has shipment records"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserHasShipmentRecords",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getStateChangeHistory.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " has state change history"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserHasStateChangeHistory",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getImportWRKRequests.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " created import WRK jobs"));
                        setAllowForceDelete(true);
                        throw UtilityService
                                .makeOperationsException("cannotDeleteUserCreatedWRKJobs",
                                        new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getAlertSubscriptions.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " has subscriptions to alerts"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserHasAlertSubscriptions",
                                new Object[] { user.getUserId() });
                    }
                    if (CommonUtils.checkIfExists("User.getAlertInbox.Count", pltUser.getId())) {
                        logger.debug(new LogMessage("Cannot delete user " + user.getUserId()
                                + " has alerts in his inbox"));
                        setAllowForceDelete(true);
                        throw UtilityService.makeOperationsException(
                                "cannotDeleteUserHasAlertInbox", new Object[] { user.getUserId() });
                    }
                    if (!loggedInUser.containsAllPermissions(pltUser.getPermissions())) {
                        throw UtilityService.makeOperationsException(
                                "noAccessPermission");
                    }

                    // remove the assignments of categories to the user (only 'ALL' will be
                    // remaining at this stage)
                    removeProductCategoryAssignments(pltUser); 
                    //remove the relation of user and part number registered through self-registration or TBYB
                    removeUserAndPartNumberMapping(pltUser.getUserId());
                    userImpl.resetUserRoles();
                    updateWebServiceUserCacheAboutModifiedUser(userImpl);
                    pltUser.delete();
                }
                contact.delete();
            }
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    /*
     * Remove all the product category assignments to the given user
     */
    public void removeProductCategoryAssignments(User user) throws OperationsException {
        if (user == null)
            return;

        try {
            List<Long> l = new ArrayList<>(2);
            l.add(user.getId());
            QueryUtil.executeUpdate("User.deleteAllProductCategories", l);
            updateWebServiceUserCacheAboutModifiedUser(user);
        }
        catch (FlexnetBaseException fbe) {
            logger.error(new LogMessage("SQL error on attempt to remove product category entry:"
                    + fbe.toString()));
            throw new OperationsException(fbe);
        }
    }

    /*
     * Unmaps the given user from the given organization. If the user is mapped to only one organization
     * then unmap does not allow the operation to succeed,as it results in existence of a user who is not 
     * associated with any organization.
     * @param uid,  the id of the user to unMap
     * @param orgUnitId, the id of the organization from which to unMap the user.
     * @exception, OperationsException is thrown when the user being unMapped belonfs to only one organization.
     *             or null arguments are passed.
     * 
     */
    public void unMapUserFromOrganization(IOpsUser user, IOrgUnitInterface orgUnit)
            throws OperationsException {
        if (user == null || orgUnit == null) {
            logger.debug(new LogMessage("user id or/and organization id specified is/are null."));
            throw new OperationsException("nullUserAndOrgUnitForUnMap");
        }
        if (ThreadContextUtil.getSeedUserIds().contains(user.getId())) {
            logger.debug(new LogMessage("Seed users cannot be unmapped from the system."));
            throw UtilityService.makeOperationsException("cannotUnmapSeedUsers");
        }

        OpsUserImpl userImpl = (OpsUserImpl)user;
        User loggedInUser = ThreadContextUtil.getUser();
        if (loggedInUser.equals(userImpl.getPlatformUserObject())) {
            throw UtilityService.makeOperationsException("cannotUnmapYourselfFromOrg",
                    new Object[] { user.getUserId() });
        }

        checkUserPermissions(orgUnit.getId());
        // OpsUserImpl userImpl = (OpsUserImpl) user;
        userImpl.removeOrg(orgUnit);
        userImpl.save(false);
        updateWebServiceUserCacheAboutModifiedUser(userImpl);
    }

    /**********
     * Private Utility methods
     ***************************************************************************/
    private Domain getDefaultDomain() throws FlexnetHibernateException, NoDataFoundException,
            MultipleDataFoundException {
        HashMap map = new HashMap();
        Domain domain = null;
        map.put(Domain.NAME, Domain.DEFAULT_DOMAIN);
        domain = (Domain)Entity.getOne(Domain.class, map);
        return domain;
    }

    private AuthenticationScheme getDefaultAuthScheme() throws FlexnetHibernateException,
            NoDataFoundException, MultipleDataFoundException {
        HashMap map = new HashMap();
        AuthenticationScheme authScheme = null;
        map.put(AuthenticationScheme.NAME, AuthenticationScheme.DEFAULT_SCHEME);
        authScheme = (AuthenticationScheme)Entity.getOne(AuthenticationScheme.class, map);
        return authScheme;
    }

    private static IOpsUser populateUser(Contact contact, User user) {
        IOpsUser opsUser = new OpsUserImpl();
        if (contact != null) {
            // TODO:PRASAD: Why are we populating user Row Id with Contact Row Id here !!!!! ?
            ((OpsUserImpl)opsUser).setId(contact.getId());
            opsUser.setCity(contact.getCity());
            opsUser.setCountry(contact.getCountry());
            opsUser.setEmail(contact.getEmail());
            opsUser.setFax(contact.getFax());
            opsUser.setFirstName(contact.getFirstName());
            opsUser.setLastName(contact.getLastName());
            opsUser.setDisplayName(contact.getDisplayName());
            opsUser.setLocale(contact.getLocale());

            opsUser.setOptIn(contact.getOptIn());
            opsUser.setPhone(contact.getPhone());
            opsUser.setPostalCode(contact.getPostalCode());
            opsUser.setState(contact.getState());
            opsUser.setStreetAddress(contact.getStreetAddress());
           
            Set orgs = contact.getBelongsToAll();
            Iterator iter = orgs.iterator();
            Set<IOrgUnitInterface> orgsList = new HashSet();
            while (iter.hasNext()) {
                OrgUnit o = (OrgUnit)iter.next();
                try {
                    /*load the organization details for the corresponding org id*/
                    OrgUnit o1 = (OrgUnit)PersistenceService.getInstance().load(OrgUnit.class,
                            o.getId());
                    orgsList.add(new OrgUnitImpl(o1));
                }
                catch (FlexnetHibernateException e) {
                    logger.debug(new LogMessage("flexnet hibernate exception in populateUser()"));
                }
            }
            ((OpsUserImpl)opsUser).setBelongsToOrgs(orgsList);
        }

        if (user != null) {
            opsUser.setUserId(user.getUserId());
            opsUser.setCanLogin(true);
            opsUser.setIsSharedLogin(user.isSharedLogin());
            opsUser.setActive(user.isActive());
            opsUser.setRenewalSubscription(user.isRenewalSubscription());
            opsUser.setIsPhantom(user.isPhantom());
            setTimeZoneForUser(user, opsUser);
            ((OpsUserImpl)opsUser).setLastAuthenticatedTime(user.getLastAuthenticatedTime());
            ((OpsUserImpl)opsUser).setCreatedDate(user.getCreateDate());
            ((OpsUserImpl)opsUser).setIsSystemAdmin(user.isSystemAdmin());
            if (user.getDomain() != null) {
                ((OpsUserImpl)opsUser).setDomain(user.getDomain());
            }
            // TODO: Set custom user attributes.
            Set uors = user.getOrgUnitRoles();
            if(uors!=null) {
            Iterator uorIter = uors.iterator();
            Map<Long, Set<IRole>> orgRoles = new HashMap();
            Map orgExpiryMap = new HashMap();
            while (uorIter.hasNext()) {
                UserOrgRole uor = (UserOrgRole)uorIter.next();
                IRole role = new RoleImpl(uor.getRole());
                Set rolesForOrg = orgRoles.get(uor.getOrgUnit().getId());
                if (rolesForOrg == null) {
                    rolesForOrg = new HashSet();
                }
                rolesForOrg.add(role);
                orgRoles.put(uor.getOrgUnit().getId(), rolesForOrg);
                 if (uor.getExpiryDate() != null) {
                    orgExpiryMap.put(uor.getOrgUnit().getId().toString(),
                            DateUtility.getDateInUSAFormat(uor.getExpiryDate()));
                }
                else if (uor.getExpiryDate() == null) {
                	orgExpiryMap.put(uor.getOrgUnit().getId().toString(), "");
                }
            }
            ((OpsUserImpl)opsUser).setOrgRoleMap(orgRoles);
            ((OpsUserImpl)opsUser).setAcctExpiryDateMap(orgExpiryMap);
        }
        }

        logger.debug(
                new LogMessage(opsUser.getId() + " with locale as " + opsUser.getLocaleStr()));
        return opsUser;
    }

    public static IOpsUser populateOpsUser(User user) {
        Contact contact = user == null? null : user.getContactInfo();
        return populateUser(contact, user);
    }

    public static IOpsUser populateUserFromContact(Contact contact) {
        User user = contact.getUserInfo();
        return populateUser(contact, user);
    }

    private static void setTimeZoneForUser(User user, IOpsUser opsUser) {
        try {
            TimeZone timezone = TimeZone.getTimeZone(user.getContactInfo().getTimezone());
            opsUser.setTimezone(DateUtility.getTimeZoneID(timezone));
        }
        catch (Exception e) {
            logger.warn(new LogMessage("No time zone for user - {0}", user.getUserId()));
        }
    }

    public static User getUserBO(IOpsUser user) throws OperationsException {
        OpsUserImpl userImpl = (OpsUserImpl)user;
        return userImpl.getPlatformUserObject();
    }

    /*
     * Maps the give user to the specified organization. The user can be mapped to organizations of only one type
     * @param user, the user to be mapped to the organization
     * @param orgUnit, the organization that the user will be mapped to.
     * @roleSet, the set of roles the given user will be granted in the specified organization.
     * @exception, OperationsException is thrown when
     * 	     the arguments are null, 
     * 	     the organization specified is not compatible with the organization that the user already belongs
     */
    public IOpsUser mapUserToOrganization(IOpsUser user, IOrgUnitInterface orgUnit,
            Set<Long> rolesSet) throws OperationsException {
        try {
            if (user == null || orgUnit == null) {
                logger.debug(new LogMessage("user id or/and organization id specified is/are null."));
                throw new OperationsException("nullUserAndOrgUnitForUnMap");
            }
            if (user.canLogin() && (rolesSet == null || rolesSet.size() == 0)) {
                logger.debug(new LogMessage(
                        "For a user {0} with CanLogin Status, the roleSet cannot be null or empty",
                        new Object[] { user.getUserId() }));
                throw UtilityService.makeOperationsException("rolesCannotBeEmpty",
                        new Object[] { user.getUserId() });
            }

            OpsUserImpl userImpl = (OpsUserImpl)user;
            /*Commenting the check below as per the issue:
             * IOA-000070962-Unable to link LDAP users to another organization from create org page 
             * but able to link orgs from create/edit user page 
             * */
            /*
            if (userImpl.getDomainName() != null
                    && !Domain.DEFAULT_DOMAIN.equals(userImpl.getDomainName())) {
                Set<IOrgUnitInterface> orgs = userImpl.getBelongsToOrganizations();
                if (orgs != null && !orgs.isEmpty()) {
                    logger.debug(new LogMessage(
                            "Domain user {0} can not belong to more than one organization.",
                            new Object[] { user.getUserId() }));
                    throw UtilityService.makeOperationsException(
                            "domainUserCannotBelongToMoreThanOneOrg",
                            new Object[] { user.getUserId() });
                }
            }
            */
            User loggedInUser = ThreadContextUtil.getUser();
            checkUserOwnPermission(orgUnit, rolesSet, userImpl, loggedInUser);

            checkUserPermissions(orgUnit.getId());
            userImpl.setAcctExpiryDateMap(((OrgUnitImpl)orgUnit).getAcctExpiryDateMap());
            userImpl.setCurrentOrg(((OrgUnitImpl)orgUnit).getCurrentOrg());
            userImpl.addOrgRoles(orgUnit, rolesSet);
            userImpl.save(true);
            updateWebServiceUserCacheAboutModifiedUser(userImpl);
            return userImpl;
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    public void checkUserOwnPermission(IOrgUnitInterface orgUnit, Set<Long> rolesSet,
            OpsUserImpl userImpl, User loggedInUser) throws OperationsException {
        if (loggedInUser.equals(userImpl.getPlatformUserObject())) {
            if (!areUserRolesSame(userImpl, orgUnit, rolesSet)) {
                logger.debug(new LogMessage("Logged in user cannot update own roles"));
                throw UtilityService.makeOperationsException("cannotUpdateOwnRoles");
            }
            try {
                if (AppConfigUtil.getConfigBoolean("ops.useraccountPage.showexpirydate")
                        && !userImpl.isSharedLogin() && !isExpirySame(userImpl, loggedInUser)) {
                    logger.debug(new LogMessage("Logged in user cannot update own expiry"));
                    throw UtilityService.makeOperationsException("cannotUpdateOwnExpiry");
                }
            }
            catch (FlexnetBaseException e) {
                throw new OperationsException(e);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean isExpirySame(OpsUserImpl userImpl, User loggedInUser) {
        Set<UserOrgRole> orgUnitRoles = loggedInUser.getOrgUnitRoles();
        Map loggedInUserExpiryMap = new HashMap();
        for(UserOrgRole userOrg : orgUnitRoles) {
            if (userImpl.getCurrentOrg() == null) {
                if (userOrg.getExpiryDate() != null && !"".equals(userOrg.getExpiryDate())) {
                    loggedInUserExpiryMap.put(userOrg.getOrgUnit().getId().toString(),
                            DateUtility.getDateInUSAFormat(userOrg.getExpiryDate()));
                }
            }
            else {
                if (userOrg.getExpiryDate() != null && !"".equals(userOrg.getExpiryDate())) {
                loggedInUserExpiryMap.put(loggedInUser.getId().toString(),
                            DateUtility.getDateInUSAFormat(userOrg.getExpiryDate()));
                }
            }
        }
        Map expMap = userImpl.getAcctExpiryDateMap();
        Map expiryMapFromForm = new HashMap();
        if(!expMap.isEmpty()) {
            Iterator it = expMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                if (userImpl.getCurrentOrg() == null
                        || pair.getKey().toString().equals(loggedInUser.getId().toString())) {
                    if (pair.getValue() instanceof String) {
                        //expMap.replace(pair.getKey().toString(), ((String)(pair.getValue())));
                        if (pair.getValue() != null && !"".equals((String)pair.getValue())) {

                            expiryMapFromForm.put(pair.getKey().toString(),
                                    (String)(pair.getValue()));
                        }

                    }
                    else {
                        if (pair.getValue() != null && !"".equals(((String[])pair.getValue())[0])) {
                            expiryMapFromForm.put(pair.getKey().toString(),
                                    (String[])pair.getValue());
                        }
                    }
                }
            }
        }
        return loggedInUserExpiryMap.equals(expiryMapFromForm);
    }

    /**
     * updates the roles of the specified user in the specified org unit. After the successful
     * update the user in the specified org unit will have the roles provided as argument.
     * 
     * @param uid
     *            , the id of the user whose roles in the org unit provided <code>orgId</code>
     * @param orgId
     *            , the id of the org unit for which the roles are to be updated.
     * @param newRoleSet
     *            , the set of new roles to update the user with.
     */
    public void updateRoles(IOpsUser user, IOrgUnitInterface orgUnit, Set<Long> newRoleSet)
            throws OperationsException {
        try {
            OpsUserImpl userImpl = (OpsUserImpl)user;
            User loggedInUser = ThreadContextUtil.getUser();
            checkUserOwnPermission(orgUnit, newRoleSet, userImpl, loggedInUser);
            checkUserPermissions(orgUnit.getId());

            userImpl.addOrgRoles(orgUnit, newRoleSet);
            userImpl.save(false);
            updateWebServiceUserCacheAboutModifiedUser(userImpl);
        }
        catch (FlexnetBaseException fex) {
            logger.debug(new LogMessage(fex.getMessage()));
            throw new OperationsException(fex);
        }
    }

    /*
     * Returns the meta data for the custom attributes associated with User
     */
    public static ICustomUserAttribute[] getCustomUserAttributesMetaData()
            throws OperationsException {
        return getCustomUserAttributesMetaData(false);
    }

    /*
     * Returns the meta data for the custom attributes associated with User
     */
    public static ICustomUserAttribute[] getActiveCustomUserAttributesMetaData()
            throws OperationsException {
        return getCustomUserAttributesMetaData(true);
    }

    /*
     * Returns the meta data for the custom attributes associated with User
     */
    public static ICustomUserAttribute[] getCustomUserAttributesMetaData(boolean activeOnly)
            throws OperationsException {
        try {
            List listExtPropMd = ExtendedPropertyMetadata.searchExtendedPropertiesMetadataList(
                    User.class.getName(), "customattribute");
            if (listExtPropMd != null && listExtPropMd.size() > 0) {
                int listSize = listExtPropMd.size();
                List<ICustomUserAttribute> beanList = new ArrayList<ICustomUserAttribute>();
                for (int i = 0; i < listSize; i++) {
                    ExtendedPropertyMetadata metadata = (ExtendedPropertyMetadata)listExtPropMd
                            .get(i);
                    if (!activeOnly || metadata.getStatus() == 1) {
                        beanList.add(new CustomUserAttributeBean(metadata));
                    }
                }

                return (ICustomUserAttribute[])beanList.toArray(new ICustomUserAttribute[beanList
                        .size()]);
            }

            ICustomUserAttribute[] beanAry = new ICustomUserAttribute[0];
            return beanAry;
        }
        catch (FlexnetBaseException ex) {
            logger.debug(new LogMessage(ex.getMessage()));
            throw new OperationsException(ex.getLocalizedMessage());
        }
    }

    public IOpsUser saveSelfServiceUser(IOpsUser user, String company, String activationId)
            throws OperationsException {
        return saveSelfServiceUser(user, company, activationId, true);
    }

    /*
     * Saves the self service users with given credentials.
     * @param user, the user details as IOpsUser instance.
     * @param company, the company name the user provides.
     * @param activationId, the activationId to be used for authentication
     * @param bSendMail, determines whether email be sent.
     * 		 true, send email after saving the self service user
     * 		 false, do not send email.
     */
    public IOpsUser saveSelfServiceUser(IOpsUser user, String company, String activationId,
            boolean bSendEmail) throws OperationsException {
        try {
            if (StringUtils.isBlank(activationId)) {
                logger.debug(new LogMessage(
                        "Activation ID is null while saving the self service user."));
                throw UtilityService.makeOperationsException("invalidActivationID");
            }
            SelfServiceUserEntitlement ent = SelfServiceUserEntitlement
                    .fromActivationId(activationId);
            return saveSelfServiceUser(user, company, bSendEmail, ent, null);
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    /*
     * Saves the self service users with given credentials.
     * @param user, the user details as IOpsUser instance.
     * @param company, the company name the user provides.
     * @param activationId, the activationId to be used for authentication
     * @param bSendMail, determines whether email be sent.
     * 		 true, send email after saving the self service user
     * 		 false, do not send email.
     */
    public IOpsUser saveTrialUser(IOpsUser user, String company, String[] partNumbers,
            boolean bSendEmail, Map<String, String[]> licenseCustomAttributes) throws OperationsException {
        try {
            if (partNumbers == null || partNumbers.length == 0) {
                logger.debug(new LogMessage(
                        "Product or part number is null while saving the trial user."));
                throw UtilityService.makeOperationsException("invalidProduct");
            }
            SelfServiceUserEntitlement ent = SelfServiceUserEntitlement
                    .trialEntitlementFromPartNumber(partNumbers);
            return saveSelfServiceUser(user, company, bSendEmail, ent,licenseCustomAttributes);
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    private IOpsUser saveSelfServiceUser(IOpsUser user, String company, boolean bSendEmail,
            SelfServiceUserEntitlement ent, Map<String, String[]> licenseCustomAttributes) throws OperationsException, FlexnetBaseException {
        logger.info(new LogMessage("TBYB: ------------ called saveSelfServiceUser"));
        if (user == null) {
            logger.debug(new LogMessage(
                    "Invalid user data specified when saving the self service user."));
            throw new OperationsException("nullUserDataForSave");
        }
        if (user.getUserId() == null) {
            logger.debug(new LogMessage(
                    "userId specified is null while saving the self service user."));
            throw new OperationsException("nullUserIdForSelfServiceUserSave");
        }

        /*FNO-59851 Adding this clause to accommodate GE use case of having multiple self register users under the existing self register account*/ 
        IOrgUnitInterface org = null;
        if(user.isRegisterForExistingAccount()) {
        	IOrgHierarchyManager orgMgr = (IOrgHierarchyManager)OperationsServiceFactory
                    .getOrgHierarchyManager();
        	IOrgUnitInterface existingOrg = orgMgr.getOrganizationByName(company);
        	if(!OrgUnitType.SELFREGISTERED.equals(existingOrg.getType())) {
        		logger.debug(new LogMessage("The organization " + company
                        + " could not be retrieved having self registered type. please inspect the log for more details."));
                throw UtilityService.makeOperationsException("couldNotLoadOrgUnit",
                        new Object[] { company });
        	}
        	else {
        		org=existingOrg;
        	}
        }else {
        org = getOrgForPortalUser(user.getUserId(), company);
        }
        Set roles = new HashSet();
        IOperationsRoleManager roleMgr = (IOperationsRoleManager)OperationsServiceFactory
                .getOperationsRoleManager();
        Long roleId = roleMgr.getRoleByName(IOperationsRoleManager.ROLE_PORTAL_USER).getId();
        roles.add(roleId);
        OpsUserImpl userImpl = (OpsUserImpl)user;

        userImpl.addOrgRoles(org, roles);
        logger.info(new LogMessage("TBYB: ------------ saving user"));
        userImpl.save(bSendEmail);
        logger.info(new LogMessage("TBYB: ------------ saved user"));

        // Map product category 'ALL' to newly created user
        Contact contact = userImpl.getPlatformContactObject();
        User pltUser = contact.getUserInfo();
        ProductCategoryDO pcd = productCategoryDAO.getProductCategoryByName("ALL");
        if (pcd == null) {
            throw new OperationsException("Product Category 'ALL' is missing from seed data");
        }
        logger.info(new LogMessage("TBYB: ------------ adding user to Product Category"));
        assignProductCategory(pcd.getId(), pltUser.getId());
        logger.info(new LogMessage("TBYB: ------------ added user to Product Category"));
        productCategoryDAO.persist(pcd);
        logger.info(new LogMessage("TBYB: ------------ Persisted Product Category"));

        // Create organization notification before finding or creating the entitlement
        if (!org.isPortalOrgUnit()) {
            logger.debug(new LogMessage(
                    "****Begin Notification Call for org.create from OperationsUserService.saveSelfServiceUser ****"));
            EntityNotificationInvoker.notify(new OrgEntityProcessor((OrgUnitImpl)org),
                    EventType.CREATE);
            logger.debug(new LogMessage("****End Notification Call****"));
        }

        OperationsEntity entity = ent.findOrCreateEntitlement(org, licenseCustomAttributes);
        if (entity instanceof IEntitlement && ((IEntitlement)entity).getSoldTo() == null) {
            ((IEntitlement)entity).setSoldTo(org);
        }

        IEntitlementManager eMgr = ent.getEntitlementMgr();
        logger.info(new LogMessage("TBYB: ------------ Redeeming entitlement"));
        eMgr.redeemEntitlement(entity, user, null);
        logger.info(new LogMessage("TBYB: ------------ Redeemed entitlement"));

        logger.debug(new LogMessage(
                "****Begin Notification Call for user.create from OperationsUserService.saveSelfServiceUser ****"));
        EntityNotificationInvoker.notify(new UserEntityProcessor((OpsUserImpl)user),
                EventType.CREATE);
        logger.debug(new LogMessage("****End Notification Call****"));
        // end notification call

        // In the case of a redeemed web reg key, call entitlement notification again after org
        // and user creation
        if (entity instanceof IWebRegKey) {
            logger.debug(new LogMessage(
                    "****Begin Notification Call for entitlement.create from OperationsUserService.saveSelfServiceUser ****"));
            EntitlementLineItemBO eli = (EntitlementLineItemBO)eMgr
                    .getEntitlementLineItemByActivationID(((IWebRegKey)entity).getWebRegKeyID());
            SimpleEntitlement simpleEnt = (SimpleEntitlement)eMgr.getEntitlementByEntitlementID(eli
                    .getParentEntitlementID());
            EntityNotificationInvoker.notify(new EntitlementEntityProcessor(
                    (EntitlementBO)simpleEnt), EventType.UPDATE);
            logger.debug(new LogMessage("****End Notification Call****"));
        }

        // end notification call
        return user;
    }

    private IOrgUnitInterface getOrgForPortalUser(String userName, String company)
            throws OperationsException {
        IOrgHierarchyManager orgService = (IOrgHierarchyManager)SpringBeanFactory.getInstance()
                .getBean("orgHierarchyService");
        if (company != null && company.length() > 0) {
            String orgName = userName + "-" + company;
            String orgDisplayName = company;
            IOrgUnitInterface orgUnitInterface = orgService.newOrgUnit(orgName, orgDisplayName,
                    OrgUnitType.SELFREGISTERED);
            return orgUnitInterface;
        }
        else {
            OrgUnitImpl orgImpl = new OrgUnitImpl();
            IOrgUnitInterface orgUnitInterface = orgService.getOrganizationByID(Long
                    .valueOf(orgImpl.getPortalOrgUnitByTenantId()));
            return orgUnitInterface;
        }
    }

    /*
     * Saves the user with any updates made to the user attributes.
     * @param user, the user instance having the updated attributes. 
     * @exception OperationsException thrown if the user argument is null or does not exist in the system.
     */
    public IOpsUser saveUpdatedUser(IOpsUser user) throws OperationsException {
        // commenting the method out until we completely understand how to distinguish
        // self-registered user(s) from others.
        // authorizationCheck();
        if (user == null || user.getId() == null) {
            logger.debug(new LogMessage(
                    "The specified user is null or does not exist in the system."));
            throw UtilityService.makeOperationsException("nullUserForUpdateUser");
        }
        if (user != null) {
            OpsUserImpl userImpl = (OpsUserImpl)user;
            Set orgs = userImpl.getPlatformContactObject().getBelongsToAll();
            User loggedInUser = ThreadContextUtil.getUser();
            if (!loggedInUser.equals(userImpl.getPlatformUserObject())) {
                checkUserPermissions(orgs);
            }
            userImpl.save(false);
            updateWebServiceUserCacheAboutModifiedUser(userImpl);

        }
        return user;
    }

    /*
     * Resets the password for the specified user. The password can be reset only for uses
     * belonging to FLEXnet Domain.
     * @param opsUser, the user whose password needs to be reset.
     */
    public void resetPassword(IOpsUser opsUser) throws OperationsException {
        if (opsUser == null || opsUser.getId() == null) {
            logger.debug(new LogMessage(
                    "The specified user is null or does not exist in the system."));
            throw new OperationsException("nullUserForUpdateUser");
        }
        PersistenceService ps = PersistenceService.getInstance();
        try {
            Contact c = (Contact)ps.load(Contact.class, opsUser.getId());
            User u = c.getUserInfo();
            if (u.getAuthenticationScheme().getName().equals("NATIVE")) {
                // make sure we reset password only for the user in flexnet domain and using native
                // authentication
                NativeAuthenticationService natAuthSvc = (NativeAuthenticationService)u
                        .getAuthService();
                natAuthSvc.resetPassword(u);
            }
            else {
                logger.debug(new LogMessage(
                        "Reset Password failed, user {0} does not belong to Flexet Domain",
                        new Object[] { opsUser.getUserId() }));
                throw UtilityService.makeOperationsException("resetPasswordFailed",
                        new Object[] { opsUser.getUserId() });
            }
        }
        catch (FlexnetBaseException fex) {
            throw new OperationsException(fex.getLocalizedMessage());
        }
    }

    /*
     * Activate the account of specified user. 
     * 
     * @param opsUser, the user whose account to be activated.
     */
    public void activateAccount(IOpsUser opsUser) throws OperationsException {
        if (opsUser == null || opsUser.getId() == null) {
            logger.debug(new LogMessage(
                    "The specified user is null or does not exist in the system."));
            throw new OperationsException("nullUserForUpdateUser");
        }
        PersistenceService ps = PersistenceService.getInstance();
        try {
            Contact c = (Contact)ps.load(Contact.class, opsUser.getId());
            User u = c.getUserInfo();
            if (u.getAuthenticationScheme().getName().equals("NATIVE")
                    || u.getAuthenticationScheme().getName().equals("LDAP")) {
                EmptyAuthenticationService authSvc = (EmptyAuthenticationService)u.getAuthService();
                authSvc.activateAccount(u);
            }
            else {
                logger.debug(new LogMessage(
                        "Activate account failed, user {0} does not belong to Flexet Domain",
                        new Object[] { opsUser.getUserId() }));
                throw UtilityService.makeOperationsException("",
                        new Object[] { opsUser.getUserId() });
            }
        }
        catch (FlexnetBaseException fex) {
            throw new OperationsException(fex.getLocalizedMessage());
        }
    }

    /*
     * Saves the specified users with in the specified organizations with the roles.
     * If the user already exists then this method updates the user, otherwise a new user
     * is created.
     * @param user,  the user details 
     * @param orgRoleSetHolder, the container for the org units and the corresponding set of roles
     *        for the specified user.
     * @return instance of IOpsUser, contains the saved user object
     * @exception OperationsException, exception is thrown if the orgRoleSetHolder is null or does not contain any 
     *            org units and roles information. 
     * 
     */
    public IOpsUser saveUser(IOpsUser user, OrgUnitRoleSetHolder orgRoleSetHolder)
            throws OperationsException {
        return saveUser(user, orgRoleSetHolder, true);
    }
    
    /*
     * Added by Zhenya Leonov: made sendEmail a parameter
     */
    public IOpsUser saveUser(IOpsUser user, OrgUnitRoleSetHolder orgRoleSetHolder, boolean sendEmail)
            throws OperationsException {
        // commenting the method out until we completely understand how to distinguish
        // self-registered user(s) from others.
        // authorizationCheck();
        try {
            if (user == null) {
                logger.debug(new LogMessage("user argument is null when saving the user."));
                throw UtilityService.makeOperationsException("nullUserDataForSave");
            }
            if (orgRoleSetHolder == null || orgRoleSetHolder.getOrgRoleMapping().size() == 0) {
                logger.debug(new LogMessage(
                        "Organizations and Roles are empty while trying to save the User {0}",
                        new Object[] { user.getUserId() }));
                throw UtilityService.makeOperationsException("orgRoleSetEmptyForSaveUser",
                        new Object[] { user.getUserId() });
            }
            Map<IOrgUnitInterface, Set<Long>> orgRoleMapping = orgRoleSetHolder.getOrgRoleMapping();
            Iterator keyIt = orgRoleMapping.keySet().iterator();
            OpsUserImpl userImpl = (OpsUserImpl)user;
            if (!StringUtils.isEmpty(user.getDomainName()) && !(Domain.DEFAULT_DOMAIN.equals(user.getDomainName())))
                    
                userImpl.setDomain(getDomain(user.getDomainName()));

            while (keyIt.hasNext()) {
                IOrgUnitInterface org = (IOrgUnitInterface)keyIt.next();
                Set<Long> roles = orgRoleMapping.get(org);
                checkUserPermissions(org.getId());
                userImpl.addOrgRoles(org, roles);
            }
            userImpl.save(sendEmail);
            updateWebServiceUserCacheAboutModifiedUser(userImpl);
            assignALLProductCateroy(user);

            return userImpl;
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }


    private void assignALLProductCateroy(IOpsUser user) throws OperationsException {
        // Map product category 'ALL' to newly created user
        // First check if the user has any assignments

        OpsUserImpl userImpl = (OpsUserImpl)user;
        Contact contact = userImpl.getPlatformContactObject();
        User pltUser = contact.getUserInfo();

        List list = getProductCategories(user);
        if (list != null && list.isEmpty()) {
            List<ProductCategoryDO> categories = new ArrayList<ProductCategoryDO>();
            ProductCategoryDO pcd = productCategoryDAO.getProductCategoryByName("ALL");
            if (pcd == null) {
                throw new OperationsException("Product Category 'ALL' is missing from seed data");
            }
            assignProductCategory(pcd.getId(), pltUser.getId());
            updateWebServiceUserCacheAboutModifiedUser(pltUser);
        }
    }

    /*
     *  Promotes a given non-logon user to a logon status so that the user can logon to the system
     *  with the assigned roles. 
     *  @param user,  the non-logon user 
     *  @param holder, the mapping of organizations and the corresponding roles for the promoted user.
     *  @exception, OperationsException is thrown when the promition fails.
     */
    public IOpsUser promoteUser(IOpsUser user, OrgUnitRoleSetHolder holder)
            throws OperationsException {
        if (user == null || user.getId() == null) {
            throw UtilityService.makeOperationsException("nullUserForUpdateUser");
        }
        if (holder == null) {
            logger.debug(new LogMessage(
                    "The organization and roles specified for the user {0} are null or empty.",
                    new Object[] { user.getUserId() }));
            throw UtilityService.makeOperationsException("needOrgsAndRolesForPromote",
                    new Object[] { user.getDisplayName() });
        }
        try {
            Map<IOrgUnitInterface, Set<Long>> orgRoleMapping = holder.getOrgRoleMapping();
            Iterator keyIt = orgRoleMapping.keySet().iterator();
            OpsUserImpl userImpl = (OpsUserImpl)user;

            while (keyIt.hasNext()) {
                IOrgUnitInterface org = (IOrgUnitInterface)keyIt.next();
                Set<Long> roles = orgRoleMapping.get(org);
                checkUserPermissions(org.getId());
                userImpl.addOrgRoles(org, roles);
            }
            user.setCanLogin(true);
            userImpl.promoteContactToUser(true);
            assignALLProductCateroy(user);
            updateWebServiceUserCacheAboutModifiedUser(user);
            return userImpl;
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    /*
     * Returns a list of users qualifying the query criteria specified. 
     * @param qry, IOperationsQuery instance with the query critetia.
     * @return IResultsList instance which contains a paginated list of users upon successful completion.
     */

    public IResultsList getUsers(IOperationsQuery qry) throws OperationsException {
        Class contactClass = null;
        try {
            contactClass = Class.forName(Contact.class.getName());
        }
        catch (ClassNotFoundException ex) {

        }

        // check permissions for non-customer users. for customer user, the permission check is done
        // as part of the query
        IPermissionENC perm = null;
        if (ThreadContextUtil.isLoggedInFromPortal())
            perm = IPermissionENC.PORTAL_VIEW_USERS;
        else
            perm = IPermissionENC.VIEW_USERS;

        if (!ThreadContextUtil.isCustomerUser()) {
            if (!PermissionUtil.hasPermissionAlias(IPermissionENC.VIEW_MANAGE_USERS.getName())
                    && !PermissionUtil.hasPermissionAlias(IPermissionENC.VIEW_USERS.getName())) {
                logger.debug(new LogMessage(
                        "Not enough permissions for operation.  Missing permission = "
                                + perm.getName()));
                throw UtilityService.makeOperationsException("notEnoughPermissions",
                        new Object[] { perm.getName() });
            }
        }

        List domains = new ArrayList();
        try {
            domains = CommonUtils.getDomains();
        }
        catch (FlexnetBaseException fe) {
            throw new OperationsException(fe);
        }
        boolean hasDomains = false;
        if (domains.size() > 0)
            hasDomains = true;

        QueryBuilder builder = new QueryBuilder(contactClass, "contact");
        builder.setSelectDistinct();
        builder.addSelectName("contact.id");
        builder.setDistinctInCount("contact.id");
        builder.addSelectName("contact.firstName_");
        builder.addSelectName("contact.lastName_");
        builder.addSelectName("contact.middleInitial_");
        builder.addSelectName("contact.phone_");
        builder.addSelectName("contact.email_");
        builder.addSelectName("contact.displayName_");
        builder.addSelectName("contact.fax_");
        builder.addSelectName("contact.locale_");
        builder.addSelectName("contact.streetAddress_");
        builder.addSelectName("contact.city_");
        builder.addSelectName("contact.state_");
        builder.addSelectName("contact.postalCode_");
        builder.addSelectName("contact.country_");
        builder.addSelectName("contact.timezone_");
        builder.addSelectName("contact.optIn_");
        builder.addSelectName("user.id");
        builder.addSelectName("user.userId_");
        builder.addSelectName("user.active_");
        builder.addSelectName("user.sharedLogin_");
        builder.addSelectName("user.renewalsubscription_");
        if (hasDomains)
            builder.addSelectName("domain.name_");

        boolean isJoinComplete = false;
        boolean isSecondCustAttr = false;
        boolean isOrgJoinComplete = false;

        boolean getOrgsAndRoles = false;

        List searchData = qry.getQueryParameters();
        IQueryParameterENC sortBy = qry.getSortBy();
        boolean sortAscending = qry.isSortAscending();

        if (searchData != null) {
            for (Iterator it = searchData.iterator(); it.hasNext();) {
                IQueryParameter qp = (IQueryParameter)it.next();
                IQueryParameterENC key = qp.getParameter();
                Object value = qp.getValue();
                AttributeQueryInfo aqi = null;
                if (value instanceof Object[]) {
                    Object[] values = (Object[])value;
                    List valList = new ArrayList();
                    for (int i = 0; i < values.length; ++i) {
                        valList.add(new AttributeQueryInfo.AttributeValueInfo(values[i]));
                    }
                    aqi = new AttributeQueryInfo(valList);
                }
                else {
                    String op = qp.getOperator().toString();
                    aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(value,
                            op));
                }

                if (IOpsUser.USER_CANLOGIN.equals(key)) {
                    if ("true".equals(value)) {
                        builder.innerJoin("contact", "userInfo_", "user");
                        builder.andWhereClause("user.phantom_ = 0");
                        isJoinComplete = true;
                    }
                    else if ("false".equals(value)) {
                        builder.outerJoin("contact", "userInfo_", "user");
                        builder.andWhereClause("user is null");
                        isJoinComplete = true;
                    }
                }
                else
                    builder.outerJoin("contact", "userInfo_", "user");

                if (hasDomains)
                    builder.outerJoin("user", "domain_", "domain");
                // in the user hbm the active flag is defined as int not boolean
                if (IOpsUser.USER_ACTIVE.equals(key)) {
                    AttributeQueryInfo.AttributeValueInfo l = (AttributeQueryInfo.AttributeValueInfo)aqi
                            .getAttributeValues();

                    if (Boolean.TRUE.equals(l.getAttributeValue())) {
                        String op = qp.getOperator().toString();
                        aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                new Integer(1), op));
                    }
                    else {
                        String op = qp.getOperator().toString();
                        aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                new Integer(0), op));
                    }
                }
                if (IOpsUser.USER_USERID.equals(key) || IOpsUser.USER_DISPLAY_NAME.equals(key)
                        || IOpsUser.USER_FIRSTNAME.equals(key)
                        || IOpsUser.USER_LASTNAME.equals(key) || IOpsUser.USER_ACTIVE.equals(key)
                        || IOpsUser.USER_PHONE_NUMBER.equals(key)
                        || IOpsUser.USER_FAX_NUMBER.equals(key) || IOpsUser.USER_STREET.equals(key)
                        || IOpsUser.USER_CITY.equals(key) || IOpsUser.USER_STATE.equals(key)
                        || IOpsUser.USER_POSTAL_CODE.equals(key)
                        || IOpsUser.USER_COUNTRY.equals(key) || IOpsUser.USER_UID.equals(key)
                        || IOpsUser.USER_EMAIL_ADDRESS.equals(key)
                        || IOpsUser.USER_DOMAIN_NAME.equals(key)
                        || IOpsUser.LAST_MODIFIED.equals(key)) {
                    builder.andComparisonIsTrue(key.toString(), aqi);
                }
                else if (IOpsUser.USER_ORGUNIT_ID.equals(key)) {
                    String strQuery = "contact.id IN "
                            + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                            + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit ";
                    Object obj = aqi.getAttributeValues();
                    AttributeQueryInfo.AttributeValueInfo attrVal = (AttributeQueryInfo.AttributeValueInfo)obj;
                    Object orgId = attrVal.getAttributeValue();
                    String operator = attrVal.getOperator();
                    strQuery = strQuery + "where orgUnit.id " + operator + orgId + ")";
                    builder.andWhereClause(strQuery);
                    isOrgJoinComplete = true;
                }
                else if (IOpsUser.USER_ORGUNIT_DISPLAY_NAME.equals(key)) {
                    String strQuery = "contact.id IN "
                            + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                            + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit ";
                    Object obj = aqi.getAttributeValues();
                    AttributeQueryInfo.AttributeValueInfo attrVal = (AttributeQueryInfo.AttributeValueInfo)obj;
                    Object orgDisplayName = attrVal.getAttributeValue();
                    String operator = attrVal.getOperator();
                    if (operator.equals(Expr.STARTS_WITH)) {
                        strQuery = strQuery + "where orgUnit.displayName_ LIKE '" + orgDisplayName
                                + "%')";
                    }
                    else if (operator.equals(Expr.ENDS_WITH)) {
                        strQuery = strQuery + "where orgUnit.displayName_ LIKE '%" + orgDisplayName
                                + "')";
                    }
                    else if (operator.equals(Expr.ANYWHERE)) {
                        strQuery = strQuery + "where orgUnit.displayName_ LIKE '%" + orgDisplayName
                                + "%')";
                    }
                    if (operator.equals(Expr.EQUALS)) {
                        strQuery = strQuery + "where orgUnit.displayName_ = '" + orgDisplayName
                                + "')";
                    }
                    builder.andWhereClause(strQuery);
                    isOrgJoinComplete = true;

                }
                else if (IOpsUser.USER_ORGUNIT_NAME.equals(key)) {
                    String strQuery = "contact.id IN "
                            + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                            + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit ";
                    Object obj = aqi.getAttributeValues();
                    AttributeQueryInfo.AttributeValueInfo attrVal = (AttributeQueryInfo.AttributeValueInfo)obj;
                    Object orgName = attrVal.getAttributeValue();
                    String operator = attrVal.getOperator();
                    if (operator.equals(Expr.STARTS_WITH)) {
                        strQuery = strQuery + "where orgUnit.name_ LIKE '" + orgName + "%')";
                    }
                    else if (operator.equals(Expr.ENDS_WITH)) {
                        strQuery = strQuery + "where orgUnit.name_ LIKE '%" + orgName + "')";
                    }
                    else if (operator.equals(Expr.ANYWHERE)) {
                        strQuery = strQuery + "where orgUnit.name_ LIKE '%" + orgName + "%')";
                    }
                    if (operator.equals(Expr.EQUALS)) {
                        strQuery = strQuery + "where orgUnit.name_ = '" + orgName + "')";
                    }
                    builder.andWhereClause(strQuery);
                    isOrgJoinComplete = true;

                }
                else if (IOpsUser.POPULATE_ORGSANDROLES.equals(key)) {
                    if ("true".equals(value)) {
                        getOrgsAndRoles = true;
                    }
                    else {
                        getOrgsAndRoles = false;
                    }
                }
                else if (IOpsUser.USER_STRING_CUST_ATTRIBUTE.equals(key)) {
                    String attrNameVal = (String)value;
                    int sepIndex = attrNameVal.indexOf(":");
                    String attrName = attrNameVal.substring(0, sepIndex);
                    String attrVal = attrNameVal.substring(sepIndex + 1);
                    if (attrVal == null || attrVal.equals(""))
                        continue;

                    String alias1 = "alias1" + attrName;
                    String alias2 = "alias2" + attrName;
                    builder.outerJoin("user", "extendedPropertySet.properties", alias1);
                    builder.outerJoin(alias1, "metadata_", alias2);
                    aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(attrVal,
                            qp.getOperator().toString()));
                    builder.andComparisonIsTrue(alias1 + ".textValue", aqi);
                    aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                            attrName, IOperatorENC.EQUALS.toString()));
                    // builder.addJoinCondition(alias2, alias2 + ".name = '" + attrName + "'");
                    builder.andComparisonIsTrue(alias2 + ".name", aqi);

                    /*              
                                  builder.outerJoin("user", "extendedPropertySet.properties", "extProp");
                                  builder.outerJoin("extProp", "metadata_", "md");             
                    			  aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(attrName, IOperatorENC.EQUALS.toString()));
                                  if( isSecondCustAttr)
                            	  builder.orComparisonIsTrue("md.name",  aqi);
                                  else {
                                      builder.andComparisonIsTrue("md.name",  aqi);
                                      isSecondCustAttr = true;
                                  }              
                                  aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(attrVal, qp.getOperator().toString()));
                                  builder.andComparisonIsTrue("extProp.textValue",  aqi);
                    */
                }
            }
            try {
                if (ThreadContextUtil.isLoggedInFromPortal() && ThreadContextUtil.isCustomerUser()) {
                    User u = ThreadContextUtil.getUser();
                    String likeClause = CommonUtils.buildOrgLineageQuery(u,
                            IPermissionENC.PORTAL_VIEW_USERS,
                            IPermissionENC.PORTAL_VIEW_CHILD_USERS, true);
                    String strFinalQuery = "";
                    if (!likeClause.equals("")) {
                        String strTempQuery = "contact.id IN "
                                + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                                + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as belongsTo "
                                + "where belongsTo.id IN "
                                + "(select orghierarchy.orgUnit.id "
                                + "from com.flexnet.operations.bizobjects.orghierarchy.OrgHierarchy as orghierarchy where ";
                        strFinalQuery = strTempQuery + likeClause + "))";
                    }
                    if (!strFinalQuery.equals(""))
                        builder.andWhereClause(strFinalQuery);
                    isOrgJoinComplete = true;
                }
                if (ThreadContextUtil.isPublisherUser()) {
                    User u = ThreadContextUtil.getUser();
                    String strWhereClause = "";
                    String likeClause = CommonUtils.buildOrgLineageQuery(u,
                            IPermissionENC.PORTAL_VIEW_USERS,
                            IPermissionENC.PORTAL_VIEW_CHILD_USERS, true);
                    if (!likeClause.equals("")) {
                        String strTempQuery = "contact.id IN "
                                + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                                + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as belongsTo "
                                + "where belongsTo.id IN "
                                + "(select orgUnit.id "
                                + "from com.flexnet.operations.bizobjects.orghierarchy.OrgHierarchy as orghierarchy "
                                + "inner join orghierarchy.orgUnit as orgUnit ";
                        String strCustomerOrgs = " or type.id IN ("
                                + IOrgUnitInterface.CUSTOMER_ORG_UNIT_TYPE_ID + ","
                                + IOrgUnitInterface.SELF_REGISTERED_ORG_UNIT_TYPE_ID + ","
                                + IOrgUnitInterface.UNKNOWN_ORG_UNIT_TYPE_ID + " ) ";
                        strWhereClause = strTempQuery + " inner join orgUnit.types_ as type "
                                + " where (" + likeClause + " and type.id = "
                                + IOrgUnitInterface.PUBLISHER_ORG_UNIT_TYPE_ID + ")"
                                + strCustomerOrgs + "))";
                    }
                    if (!strWhereClause.equals(""))
                        builder.andWhereClause(strWhereClause);
                    isOrgJoinComplete = true;
                }
            }
            catch (OPSBaseException ox) {
                throw new OperationsException(ox);
            }
        }
        if (!isJoinComplete) {
            builder.outerJoin("contact", "userInfo_", "user");
            builder.andWhereClause("(user.phantom_ = 0 or user is null)");
        }

        if (sortBy != null) {
            if (!isOrgJoinComplete
                    && (IOpsUser.USER_ORGUNIT_NAME.equals(sortBy)
                            || IOpsUser.USER_ORGUNIT_ID.equals(sortBy) || IOpsUser.USER_ORGUNIT_DISPLAY_NAME
                                .equals(sortBy))) {
                // builder.innerJoin("contact", "belongsTo_", "orgUnit");
                String strQuery = "contact.id IN "
                        + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                        + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit)";
                builder.andWhereClause(strQuery);
            }
            builder.setOrderBy(sortBy.toString());
            builder.setDescendingSort(sortAscending == false);
        }
        return new ResultsListService(builder, qry, new UserOrgDataCallbackProcessor(
                getOrgsAndRoles, hasDomains), true);
    }

    /**
     * validate userId as per the UserId Validator configured in the Config Service
     * 
     * @param userId
     *            userId to be validated
     * @return boolean true if successful
     * @throws FlexnetBaseException
     *             on validation failure
     */
    private static boolean validateUserIdExpression(String userId)
            throws ValidationFailedException, FlexnetHibernateException {

        String validatorClass = null;

        try {
            validatorClass = AppConfigUtil.getConfigString("user.id.validatorclass");
        }
        catch (Exception e) {
            throw new FlexnetHibernateException("Unable to get the "
                    + "user.id.validatorclass value from config service");
        }

        Validator validator = null;

        /* retrieve the validator object */
        try {
            validator = (Validator)Class.forName(validatorClass).newInstance();
        }
        catch (Exception e) {
            logger.error(new LogMessage("Unable to instantiate "
                    + "the validator class for UserId validation"));
            throw new FlexnetHibernateException("Unable to instantiate "
                    + "the validator class for UserId validation");
        }

        /* add the locale object to params map */
        Map params = new HashMap();
        params.put("locale", ThreadContextUtil.getLocale());

        /* validate the userId */
        validator.validate(userId, params);

        return true;

    }

    private static void validateUniqueKey(String userId, Domain domain, Long id)
            throws DuplicateUserException, FlexnetHibernateException {
        // Find the User object in the database by name (if it exists)
        // and see if it is the same object as the one being validated.
        // If not, then there is going to be a unique name violation,
        // so throw an error.
        HashMap searchValues = new HashMap();
        searchValues.put(User.USERID, userId);
        searchValues.put(User.DOMAIN, domain.getId());

        // Hack to make sure that Hibernate does not flush the cache prematurely.
        // Doing so may cause database constraints to be violated (before we had a chance to stop
        // the data from being set)
        // and then the client would see a database exception instead of a nice, clean validation
        // exception.
        // So we save the existing Flush Mode and then set it explicitly to "COMMIT".
        FlushModeType fm = PersistenceService.getInstance().getTransaction().getHibernateSession()
                .getFlushMode();
        PersistenceService.getInstance().getTransaction().getHibernateSession()
                .setFlushMode(FlushModeType.COMMIT);

        List users = Entity.getAll(User.class, "", true, -1, 0, searchValues);

        // Hack to make Hibernate flush like before.
        // Set the flush mode back to the saved value.
        PersistenceService.getInstance().getTransaction().getHibernateSession().setFlushMode(fm);

        Iterator it = users.iterator();
        User existingUser;
        while (it.hasNext()) {
            // Since we are filtering the list by the unique key set
            // there can be at most one iteration of this loop
            existingUser = (User)it.next();
            if (id == null) {
                throw new DuplicateUserException();
            }
            if (existingUser.getId().longValue() != id.longValue()) {
                throw new DuplicateUserException();
            }
        }
    }

    private static void validateContact(Contact contact, Long id) throws DuplicateContactException,
            FlexnetHibernateException {
        // If the Contact object being validated has an id,
        // find it in the database (if it exists).
        // If found, check if any Users are associated with it.
        // If a User is found (A Contact can only be associated with one User)
        // then verify that the User is the same as the one being validated.
        // If not then the Contact is going to have more than one User association,
        // so throw an error.
        if (contact.getId() != null) {
            Contact existingContact = (Contact)Entity.getById(Contact.class, contact.getId());
            User contactUser = existingContact.getUserInfo();
            if (contactUser != null) {
                if (id == null) {
                    throw new DuplicateContactException();
                }
                if (contactUser.getId().longValue() != id.longValue()) {
                    throw new DuplicateContactException();
                }
            }
        }
    }

    public boolean validateUserAndContactDetails(String userId, String firstName, String lastName,
            String phone, String email, String id) throws OperationsException {
        try {
            // User id is entered only once through UI. After it is created ,it cannot be changed
            if (phone == null || phone.equals("")) {
                phone = " ";
            }
            if (id.equals("") && !userId.equals("")) {
                HashMap searchValues = new HashMap();
                searchValues.put(Domain.NAME, Domain.DEFAULT_DOMAIN);
                Domain domain = (Domain)Entity.getOne(Domain.class, searchValues);
                /* Validate user id expression only for Default "FLEXnet" domain */
                if (domain.isDefaultDomain()) {
                    validateUserIdExpression(userId);
                }
                validateUniqueKey(userId, domain, null);
                validateContactUniqueKey(firstName, lastName, phone, email, null);
            }
            else {
                if (!id.equals(""))
                    validateContactUniqueKey(firstName, lastName, phone, email, new Long(id));
                else
                    validateContactUniqueKey(firstName, lastName, phone, email, null);
            }
            return true;
        }
        catch (FlexnetBaseException fex) {
            throw new OperationsException(fex.getLocalizedMessage());
        }
    }

    /**
     * Validates that no unique key constraints on the entity are violated by the object.
     */

    private static void validateContactUniqueKey(String firstName, String lastName, String phone,
            String email, Long id) throws DuplicateContactException, FlexnetHibernateException {
        // Find the Contact object in the database by name (if it exists)
        // and see if it is the same object as the one being validated.
        // If not, then there is going to be a unique name violation,
        // so throw an error.
        HashMap searchValues = new HashMap();
        searchValues.put(Contact.FIRST_NAME, firstName);
        searchValues.put(Contact.LAST_NAME, lastName);

        searchValues.put(Contact.PHONE, phone);
        searchValues.put(Contact.EMAIL, email);

        // Hack to make sure that Hibernate does not flush the cache prematurely.
        // Doing so may cause database constraints to be violated (before we had a chance to stop
        // the data from being set)
        // and then the client would see a database exception instead of a nice, clean validation
        // exception.
        // So we save the existing Flush Mode and then set it explicitly to "COMMIT".
        FlushModeType fm = PersistenceService.getInstance().getTransaction().getHibernateSession()
                .getFlushMode();
        PersistenceService.getInstance().getTransaction().getHibernateSession()
                .setFlushMode(FlushModeType.COMMIT);

        List contacts = Entity.getAll(Contact.class, "", true, -1, 0, searchValues);

        // Hack to make Hibernate flush like before.
        // Set the flush mode back to the saved value.
        PersistenceService.getInstance().getTransaction().getHibernateSession().setFlushMode(fm);

        Iterator it = contacts.iterator();
        Contact existingContact = null;
        while (it.hasNext()) {
            // Since we are filtering the list by the unique key set
            // there can be at most one iteration of this loop
            existingContact = (Contact)it.next();
            if (id == null) {
                // If the id is null then the object being validated is a new contact.
                // If the unique key already exists, then this new object cannot exist with the
                // existing unique key.
                throw new DuplicateContactException();
            }
            else {
                if (existingContact.getId().longValue() != id.longValue()) {
                    throw new DuplicateContactException();
                }
            }
        }
    }

    public void checkLoggedInUser(IOpsUser user, IOrgUnitInterface orgUnit, Set<Long> newRoleSet)
            throws OperationsException {
            OpsUserImpl userImpl = (OpsUserImpl)user;
            User loggedInUser = ThreadContextUtil.getUser();
            checkUserOwnPermission(orgUnit, newRoleSet, userImpl, loggedInUser);
        }
		
    public void replaceOrgRoles(IOpsUser user, Map<IOrgUnitInterface, Set<IRole>> orgRoleMap)
            throws OperationsException {
        if (user == null) {
            logger.debug(new LogMessage("User can not be null"));
            throw UtilityService.makeOperationsException("nullUserIdForRetrieveUser",
                    new Object[] {});
        }
        if (orgRoleMap == null || orgRoleMap.isEmpty()) {
            logger.debug(new LogMessage(
                    "The organization and roles specified for the user {0} are null or empty.",
                    new Object[] { user.getUserId() }));
            throw UtilityService.makeOperationsException("needOrgsAndRolesForReplaceOrgs",
                    new Object[] { user.getDisplayName() });
        }
        OpsUserImpl userImpl = (OpsUserImpl)user;
        userImpl.setBelongsToOrgs(orgRoleMap.keySet());

        Map<Long, Set<IRole>> orgIdRoleMap = new HashMap<Long, Set<IRole>>();
        Set<Map.Entry<IOrgUnitInterface, Set<IRole>>> entries = orgRoleMap.entrySet();
        Iterator<Map.Entry<IOrgUnitInterface, Set<IRole>>> iter = entries.iterator();
        while (iter.hasNext()) {
            Map.Entry<IOrgUnitInterface, Set<IRole>> entry = iter.next();
            orgIdRoleMap.put(entry.getKey().getId(), entry.getValue());
        }
        userImpl.setOrgRoleMap(orgIdRoleMap);
        userImpl.save(false);
        updateWebServiceUserCacheAboutModifiedUser(userImpl);
    }

    private boolean areUserRolesSame(OpsUserImpl userImpl, IOrgUnitInterface orgUnit, Set rolesSet) {
        // check if the user belongs to the orgUnit
        Set userOrgs = userImpl.getBelongsToOrganizations();
        if (!userOrgs.contains(orgUnit))
            return false;

        Set usersExistingRoles = userImpl.getRoles(orgUnit);
        Set tempRoles = new HashSet();
        Iterator iter = usersExistingRoles.iterator();
        while (iter.hasNext()) {
            IRole role = (IRole)iter.next();
            tempRoles.add(role.getId());
        }
        return tempRoles.equals(rolesSet);
    }

    public IResultsList getUsersForOrg(IOperationsQuery qry, String orgId, boolean includeSubOrgs)
            throws OperationsException {

        if (orgId == null)
            return null;

        // get Org
        IOrgHierarchyManager orgHierarchyMngr = (IOrgHierarchyManager)SpringBeanFactory
                .getInstance().getBean("orgHierarchyService");

        OrgUnitImpl orgUnit = (OrgUnitImpl)orgHierarchyMngr.getOrganizationByID(Long
                .parseLong(orgId));

        // get the Org id list in string format
        String orgListInStr = getOrgIdSubclause(orgUnit, includeSubOrgs);

        // get active user and contact for a list of orgs
        String strQuery = "contact.id IN "
                + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit " + "where orgUnit.id in "
                + orgListInStr + ") ";

        // if the orgs are customer type
        if (orgUnit.isCustomer()) {
            return getCustomerUsers(qry, strQuery, false);
        }

        // more to added for other org types
        if (orgUnit.isChannelPartner())
            return getChannelPartnerUsers(qry, strQuery, false);

        if (orgUnit.isSelfRegisteredOrgUnit())
            return getSelfRegisteredUsers(qry, strQuery, false, false);
        return null;
    }

    private String getOrgIdSubclause(OrgUnitImpl iOrg, boolean includeSubOrgs)
            throws OperationsException {
        Set<Long> allOrgIdzInHierarchy = OperationsServiceFactory.getOrgHierarchyManager()
                .getAllSubOrgsInHierarchy(Arrays.asList(iOrg.getId()));
        String inClauseString = "("
                + allOrgIdzInHierarchy.stream().map(o -> o.toString())
                        .collect(Collectors.joining(",")) + ")";
        return inClauseString;
    }

    private boolean isUnknownOrg(String orgName) {
        return StringUtils.equals(orgName, SeededOrg.UNKNOWN_ORG_UNIT.name());
    }

    private IResultsList getUsers(IOperationsQuery qry, String subQuery, QueryBuilder builder,
            boolean includeInactive) throws OperationsException {
        boolean hasDomains = hasDomains();
        boolean getOrgsAndRoles = getIfOrgsRolesInQuery(qry);

        if (hasDomains) {
            builder.addSelectName("domain.name_");
            builder.outerJoin("user", "domain_", "domain");
        }

        if (subQuery != null && builder != null) {
            builder.andWhereClause(subQuery);
        }

        // Remove users disabled by GDPR
        builder.andWhereClause(" (user.reasonDisabled_ IS NULL or user.reasonDisabled_ not like '"+ User.OBSOLETE_USER +"' )" );

        // An important case to remember for GDPR is that of contacts. For contacts, there is no entry in the USER table and consequently no reasonDisabled field.
        builder.andWhereClause(" ( contact.email_ not like '%"+ User.OBSOLETE_DOMAIN + "') ");

        if (!includeInactive) {
            builder.andWhereClause(" user.active_ <> 0 ");
        }
        
        try {
			List<StringBuffer> list = builder.getHibernateQueries();
			StringBuffer buf = list.get(0);
		} catch (HibernateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FlexnetBaseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return new ResultsListService(builder, qry, new UserOrgDataCallbackProcessor(
                getOrgsAndRoles, hasDomains), true);
    }
    @FlexTransactional(readOnly = true)
    private IResultsList getCustomerUsers(IOperationsQuery qry, String subQuery,
            boolean includeInactive) throws OperationsException {
        QueryBuilder builder = null;
        if (ThreadContextUtil.isCustomerUser() || ThreadContextUtil.isPublisherUser()) {
            qry.createQueryParameter(IOrgUnitInterface.ORG_UNIT_TYPE_ID,
                    IOrgUnitInterface.CUSTOMER_ORG_UNIT_TYPE_ID, IOperatorENC.EQUALS);
            builder = getUsersQuery(qry);
            if (ThreadContextUtil.isCustomerUser()) {
                builder = buildSubQueryForOrgLineage(builder);
            }
        }
        if (ThreadContextUtil.isChannelPartnerUser()) {
            builder = getUsersQuery(qry);
            builder = buildSubQueryForOrgRelations(builder);
        }
        return getUsers(qry, subQuery, builder, includeInactive);
    }

    public IOpsUser getExistingUser(UserObj obj) throws DaoException {

        String query = "";

        if (ThreadContextUtil.isSingleTenantMode()) {
            query = PersistenceService.getInstance().getQuery("Contact.getExistingUsersOnPrem");
        }
        else {
            query = PersistenceService.getInstance().getQuery("Contact.getExistingUsersHosted");
        }

        try {

            Query hqlQuery = PersistenceService.getInstance().getTransaction()
                    .getHibernateSession().createQuery(query);
            hqlQuery.setString(0, obj.getUserName());

            if (!ThreadContextUtil.isSingleTenantMode()) {
                hqlQuery.setString(1, obj.getEmailAddress());
            }

            List result = hqlQuery.list();
            if (result.size() > 0) {
                User u = (User)result.get(0);
                return OperationsUserService.populateOpsUser((User)result.get(0));

            }
        }
        catch (FlexnetHibernateException e) {
            throw new DaoException(e);
        }
        return null;

    }

    public IResultsList getCustomerUsers(IOperationsQuery qry) throws OperationsException {
        return getCustomerUsers(qry, null, true);
    }

    public IResultsList getPublisherUsers(IOperationsQuery qry) throws OperationsException {
        return getPublisherUsers(qry, null, true);
    }
    @FlexTransactional(readOnly = true)
    private IResultsList getPublisherUsers(IOperationsQuery qry, String subQuery,
            boolean includeInactive) throws OperationsException {
        if (ThreadContextUtil.isPublisherUser()) {
            qry.createQueryParameter(IOrgUnitInterface.ORG_UNIT_TYPE_ID,
                    IOrgUnitInterface.PUBLISHER_ORG_UNIT_TYPE_ID, IOperatorENC.EQUALS);
            QueryBuilder builder = getUsersQuery(qry);
            builder = buildSubQueryForOrgLineage(builder);
            return getUsers(qry, subQuery, builder, includeInactive);
        }
        else {
            return null;
        }
    }
    @FlexTransactional(readOnly = true)
    private IResultsList getChannelPartnerUsers(IOperationsQuery qry, String subQuery,
            boolean includeInactive) throws OperationsException {
        qry.createQueryParameter(IOrgUnitInterface.ORG_UNIT_TYPE_ID,
                IOrgUnitInterface.CHANNELPARTNER_ORG_UNIT_TYPE_ID, IOperatorENC.EQUALS);
        QueryBuilder builder = getUsersQuery(qry);
        if (ThreadContextUtil.isCustomerUser()) {
            builder = buildSubQueryForOrgRelations(builder);
        }
        if (ThreadContextUtil.isChannelPartnerUser()) {
            builder = buildSubQueryForOrgLineage(builder);
        }

        return getUsers(qry, subQuery, builder, includeInactive);
    }

    public IResultsList getChannelPartnerUsers(IOperationsQuery qry) throws OperationsException {
        return getChannelPartnerUsers(qry, null, true);
    }
    @FlexTransactional(readOnly = true)
    private IResultsList getSelfRegisteredUsers(IOperationsQuery qry, String subQuery,
            boolean includeInactive, boolean selectForEPDevice) throws OperationsException {
        if (ThreadContextUtil.isPublisherUser() || selectForEPDevice) {
            qry.createQueryParameter(IOrgUnitInterface.ORG_UNIT_TYPE_ID,
                    IOrgUnitInterface.SELF_REGISTERED_ORG_UNIT_TYPE_ID, IOperatorENC.EQUALS);

            if (selectForEPDevice) {
                String usrId = ThreadContextUtil.getUserId();
                qry.createQueryParameter(IOpsUser.USER_USERID, usrId, IOperatorENC.EQUALS);

            }

            QueryBuilder builder = getUsersQuery(qry);
            return getUsers(qry, subQuery, builder, includeInactive);
        }
        else {
            return null;
        }
    }

    public IResultsList getSelfRegisteredUsers(IOperationsQuery qry) throws OperationsException {
        return getSelfRegisteredUsers(qry, null, true, false);
    }

    private QueryBuilder buildSubQueryForOrgRelations(QueryBuilder builder)
            throws OperationsException {
        try {
            QueryBuilder subQueryBuilder1 = new QueryBuilder(Contact.class, "contact1");
            subQueryBuilder1.innerJoin("contact1", "belongsTo_", "contactOrgUnit");
            subQueryBuilder1.innerJoin("contactOrgUnit", "orgUnitId_", "belongsTo");
            subQueryBuilder1.addSelectName("contact1.id");

            QueryBuilder subQueryBuilder2 = new QueryBuilder(OpsOrgUnit.class, "opsorgunit");
            subQueryBuilder2.innerJoin("opsorgunit", "orgUnit_", "orgunit");
            subQueryBuilder2.innerJoin("opsorgunit", "relatedOrgs_", "item");
            subQueryBuilder2.innerJoin("item", "relatedOrg", "relatedOrg");
            subQueryBuilder2.innerJoin("relatedOrg", "orgUnit_", "relatedorgUnit");
            subQueryBuilder2.addSelectName("relatedorgUnit.id");
            String strOrgList = CommonUtils.getUsersRootOrgUnits();
            String strWhereClause = "orgunit.id IN ( " + strOrgList + " )";
            subQueryBuilder2.andWhereClause(strWhereClause);

            subQueryBuilder1.addSubQueryWithIN("belongsTo.id", subQueryBuilder2);
            builder.addSubQueryWithIN("contact.id", subQueryBuilder1);
            return builder;
        }
        catch (FlexnetBaseException e) {
            throw new OperationsException(e);
        }
    }

    private QueryBuilder buildSubQueryForOrgLineage(QueryBuilder builder)
            throws OperationsException {
        try {
            IPermissionENC perm = null;
            if (ThreadContextUtil.isLoggedInFromPortal())
                perm = IPermissionENC.PORTAL_VIEW_USERS;
            else
                perm = IPermissionENC.VIEW_USERS;

            IPermissionENC childPerm = IPermissionENC.PORTAL_VIEW_CHILD_USERS;

            String likeClause = CommonUtils.buildOrgLineageQuery(ThreadContextUtil.getUser(), perm,
                    childPerm, true);

            builder.innerJoin("contact", "belongsTo_", "contactOrgUnit");
            builder.innerJoin("contactOrgUnit", "orgUnitId_", "orgUnit");
            builder.innerJoin("orgUnit", "types_", "orgunitorgunittype");
            builder.innerJoin("orgunitorgunittype", "orgUnitType", "orgunittype");
            builder.addUnMappedInnerJoin(OrgHierarchy.class, "orghierarchy", "orgUnit",
                    "orgUnit", "id");

            builder.andWhereClause("orghierarchy.tenantId_='"
                    + ThreadContextUtil.getQueryableTenantId() + "' ");
            builder.andWhereClause(likeClause);

            return builder;
        }
        catch (FlexnetBaseException e) {
            throw new OperationsException(e);
        }
    }

    private boolean hasDomains() throws OperationsException {
        List domains = new ArrayList();
        try {
            domains = CommonUtils.getDomains();
        }
        catch (FlexnetBaseException fe) {
            throw new OperationsException(fe);
        }
        boolean hasDomains = false;
        if (domains.size() > 0) {
            hasDomains = true;
        }
        return hasDomains;
    }

    private boolean getIfOrgsRolesInQuery(IOperationsQuery qry) throws OperationsException {
        boolean getOrgsAndRoles = false;
        List searchData = qry.getQueryParameters();
        if (searchData != null) {
            for (Iterator it = searchData.iterator(); it.hasNext();) {
                IQueryParameter qp = (IQueryParameter)it.next();
                IQueryParameterENC key = qp.getParameter();
                Object value = qp.getValue();
                if (IOpsUser.POPULATE_ORGSANDROLES.equals(key)) {
                    if ("true".equals(value)) {
                        getOrgsAndRoles = true;
                    }
                    else {
                        getOrgsAndRoles = false;
                    }
                }
            }
        }
        return getOrgsAndRoles;
    }

    private QueryBuilder getUsersQuery(IOperationsQuery qry) throws OperationsException {

        QueryBuilder builder = new QueryBuilder(Contact.class, "contact");

        // check permissions for non-customer users. for customer user, the permission check is done
        // as part of the query
        IPermissionENC perm = null;
        if (ThreadContextUtil.isLoggedInFromPortal())
            perm = IPermissionENC.PORTAL_VIEW_USERS;
        else
            perm = IPermissionENC.VIEW_USERS;

        if (ThreadContextUtil.isPublisherUser()) {
            if (!PermissionUtil.hasPermissionAlias(IPermissionENC.VIEW_MANAGE_USERS.getName())
                    && !PermissionUtil.hasPermissionAlias(IPermissionENC.VIEW_USERS.getName())) {
                logger.debug(new LogMessage(
                        "Not enough permissions for operation.  Missing permission = "
                                + perm.getName()));
                throw UtilityService.makeOperationsException("notEnoughPermissions",
                        new Object[] { perm.getName() });
            }
        }

        builder.setSelectDistinct();
        builder.addSelectName("contact.id");
        builder.setDistinctInCount("contact.id");
        builder.addSelectName("contact.firstName_");
        builder.addSelectName("contact.lastName_");
        builder.addSelectName("contact.middleInitial_");
        builder.addSelectName("contact.phone_");
        builder.addSelectName("contact.email_");
        builder.addSelectName("contact.displayName_");
        builder.addSelectName("contact.fax_");
        builder.addSelectName("contact.locale_");
        builder.addSelectName("contact.streetAddress_");
        builder.addSelectName("contact.city_");
        builder.addSelectName("contact.state_");
        builder.addSelectName("contact.postalCode_");
        builder.addSelectName("contact.country_");
        builder.addSelectName("contact.timezone_");
        builder.addSelectName("contact.optIn_");
        builder.addSelectName("user.id");
        builder.addSelectName("user.userId_");
        builder.addSelectName("user.active_");
        builder.addSelectName("user.sharedLogin_");
        builder.addSelectName("contact.lastUpdated");
        builder.addSelectName("user.renewalsubscription_");
        builder.addSelectName("user.createDate_");
        builder.addSelectName("user.lastAuthenticatedTime_");
        builder.addSelectName("user.createdBy_");
        builder.addSelectName("user.lastModifiedBy_");

        boolean isJoinComplete = false;
        List searchData = qry.getQueryParameters();
        IQueryParameterENC sortBy = qry.getSortBy();
        boolean sortAscending = qry.isSortAscending();

        try {
            if (searchData != null) {
                for (Iterator it = searchData.iterator(); it.hasNext();) {
                    IQueryParameter qp = (IQueryParameter)it.next();
                    IQueryParameterENC key = qp.getParameter();
                    Object value = qp.getValue();
                    AttributeQueryInfo aqi = null;
                    if (value instanceof Object[]) {
                        Object[] values = (Object[])value;
                        List valList = new ArrayList();
                        for (int i = 0; i < values.length; ++i) {
                            valList.add(new AttributeQueryInfo.AttributeValueInfo(values[i]));
                        }
                        aqi = new AttributeQueryInfo(valList);
                    }
                    else {
                        String op = qp.getOperator().toString();
                    aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(value,
                            op));
                    }

                    if (IOpsUser.USER_CANLOGIN.equals(key)) {
                        if ("true".equals(value)) {
                            builder.innerJoin("contact", "userInfo_", "user");
                        builder.andWhereClause("user.phantom_ = 0 ");
                            isJoinComplete = true;
                        }
                        else if ("false".equals(value)) {
                            builder.outerJoin("contact", "userInfo_", "user");
                            builder.andWhereClause("user is null");
                            isJoinComplete = true;
                        }
                    }
                    else
                        builder.outerJoin("contact", "userInfo_", "user");

                    // in the user hbm the active flag is defined as int not boolean
                    if (IOpsUser.USER_ACTIVE.equals(key)) {
                        AttributeQueryInfo.AttributeValueInfo l = (AttributeQueryInfo.AttributeValueInfo)aqi
                                .getAttributeValues();

                        if (Boolean.TRUE.equals(l.getAttributeValue())) {
                            String op = qp.getOperator().toString();
                            aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                    new Integer(1), op));
                        }
                        else {
                            String op = qp.getOperator().toString();
                            aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                    new Integer(0), op));
                        }
                    }
                    if (IOpsUser.USER_USERID.equals(key) || IOpsUser.USER_DISPLAY_NAME.equals(key)
                            || IOpsUser.USER_FIRSTNAME.equals(key)
                        || IOpsUser.USER_LASTNAME.equals(key) || IOpsUser.USER_ACTIVE.equals(key)
                            || IOpsUser.USER_PHONE_NUMBER.equals(key)
                        || IOpsUser.USER_FAX_NUMBER.equals(key) || IOpsUser.USER_STREET.equals(key)
                        || IOpsUser.USER_CITY.equals(key) || IOpsUser.USER_STATE.equals(key)
                            || IOpsUser.USER_POSTAL_CODE.equals(key)
                            || IOpsUser.USER_COUNTRY.equals(key) || IOpsUser.USER_UID.equals(key)
                            || IOpsUser.USER_EMAIL_ADDRESS.equals(key)
                            || IOpsUser.USER_DOMAIN_NAME.equals(key)
                            || IOpsUser.LAST_MODIFIED.equals(key) 
                           || IOpsUser.USER_CREATEDBY.equals(key)
                           || IOpsUser.USER_LASTMODIFIEDBY.equals(key)) {
                        builder.andComparisonIsTrue(key.toString(), aqi);
                    }
                    else if (IOpsUser.USER_ORGUNIT_ID.equals(key)) {
                        String strQuery = "contact.id IN "
                                + "(select contact1.id from com.flexnet.platform.bizobjects.Contact as contact1 "
                                + "inner join contact1.belongsTo_ as orgContact inner join orgContact.orgUnitId_ as orgUnit ";
                        Object obj = aqi.getAttributeValues();
                        AttributeQueryInfo.AttributeValueInfo attrVal = (AttributeQueryInfo.AttributeValueInfo)obj;
                        Object orgId = attrVal.getAttributeValue();
                        String operator = attrVal.getOperator();
                        strQuery = strQuery + "where orgUnit.id " + operator + orgId + ")";
                        builder.andWhereClause(strQuery);
                    }
                    else if(IOpsUser.USER_ROLE.equals(key))
                    {
                     SelectQueryBuilder subBuilder = new SelectQueryBuilder(Contact.class,"contact2");
                     subBuilder.setSelectDistinct();
                     subBuilder.addSelectName("contact2.id");
                     subBuilder.innerJoin("contact2", "userInfo_", "user1");
                     subBuilder.innerJoin("user1", "OrgUnitRoles_", "userOrgRole");
                     subBuilder.innerJoin("userOrgRole", "role", "role1");
                     subBuilder.andComparisonIsTrue(key.toString(), aqi);
                     builder.addSubQueryWithIN("contact.id", subBuilder);
                    }
                    else if (IOpsUser.USER_EXPIRY_DATE.equals(key)) {

                        if (!AppConfigUtil.getConfigBoolean("ops.useraccountPage.showexpirydate")) {
                            String userAccountConfDisabledMessage = WSCommonUtils
                                    .getLocalizedMessage(WSCommonUtils.USER_ACCOUNT_EXPIRY_CONFIGURATION_DISABLED);
                            throw UtilityService
                                    .makeOperationsException(userAccountConfDisabledMessage);
                        }
                        if (value instanceof String) {
                            String attrVal = (String)value;
                            if (attrVal == null || attrVal.equals(""))
                                continue;
                            Date date = new Date(Long.parseLong(attrVal));
                            aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                    date, qp.getOperator().toString()));
                        }
                        SelectQueryBuilder subBuilder = new SelectQueryBuilder(Contact.class,
                                "contact1");
                        subBuilder.addSelectName("contact1.id");
                        subBuilder.innerJoin("contact1", "userInfo_", "user1");
                        subBuilder.innerJoin("user1", "OrgUnitRoles_", "userOrgRole");
                        subBuilder.andComparisonIsTrue(key.toString(), aqi);
                        builder.addSubQueryWithIN("contact.id", subBuilder);
                    }
                    else if (IOpsUser.USER_ORGUNIT_TYPE_ID.equals(key)) {

                    builder.innerJoin("contact", "belongsTo_", "contactOrgUnit");
                    builder.innerJoin("contactOrgUnit", "orgUnitId_", "orgUnit");
                    builder.innerJoin("orgUnit", "types_", "orgunitorgunittype");
                    builder.innerJoin("orgunitorgunittype", "orgUnitType", "orgunittype");
                        Object obj = aqi.getAttributeValues();
                        AttributeQueryInfo.AttributeValueInfo attrVal = (AttributeQueryInfo.AttributeValueInfo)obj;
                        Object orgTypeId = attrVal.getAttributeValue();
                        String operator = attrVal.getOperator();
                    builder.andWhereClause("orgunittype.id " + operator + orgTypeId);
                    
                    }
                    else if (IOpsUser.USER_ORGUNIT_NAME.equals(key)
                            || IOpsUser.USER_ORGUNIT_DISPLAY_NAME.equals(key)) {

                    builder.innerJoin("contact", "belongsTo_", "contactOrgUnit");
                    builder.innerJoin("contactOrgUnit", "orgUnitId_", "orgUnit");

                    builder.andComparisonIsTrue(key.toString(), aqi);
                    builder.andWhereClause("orgUnit.tenantId_='"
                            + ThreadContextUtil.getQueryableTenantId() + "'");
                    }
                    else if (IOpsUser.USER_STRING_CUST_ATTRIBUTE.equals(key)) {
                        String attrNameVal = (String)value;
                        int sepIndex = attrNameVal.indexOf(":");
                        String attrName = attrNameVal.substring(0, sepIndex);
                        String attrVal = attrNameVal.substring(sepIndex + 1);
                        if (attrVal == null || attrVal.equals(""))
                            continue;

                        String alias1 = "alias1" + attrName;
                        String alias2 = "alias2" + attrName;
                        builder.outerJoin("user", "extendedPropertySet.properties", alias1);
                        builder.outerJoin(alias1, "metadata_", alias2);
                    builder.andWhereClause("( " + alias2 + ".tenantId_='"
                            + ThreadContextUtil.getQueryableTenantId() + "' OR " + alias2 + ".tenantId_ is null )");
                    builder.andWhereClause("( "+ alias1 + ".tenantId_='"
                            + ThreadContextUtil.getQueryableTenantId() + "' OR " + alias1 + ".tenantId_ is null )");
                    aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(attrVal,
                            qp.getOperator().toString()));
                        builder.andComparisonIsTrue(alias1 + ".textValue", aqi);
                        aqi = new AttributeQueryInfo(new AttributeQueryInfo.AttributeValueInfo(
                                attrName, IOperatorENC.EQUALS.toString()));
                        builder.andComparisonIsTrue(alias2 + ".name", aqi);
                    }
                }
            }
            if (!isJoinComplete) {
                builder.outerJoin("contact", "userInfo_", "user");
                builder.andWhereClause("(user.phantom_ = 0 ");
                builder.orWhereClause("user is null)");
            }
        builder.andWhereClause("( user.tenantId_='" + ThreadContextUtil.getQueryableTenantId() + "' OR user.tenantId_ is null )");
            if (sortBy != null) {            	
            	if(sortBy.equals(IOpsUser.USER_CANLOGIN)) {
            		builder.addSelectName("user.phantom_");
            		builder.setOrderBy("user.phantom_");
            	}else if (sortBy.equals(IOpsUser.USER_ORGUNIT_NAME) || sortBy.equals(IOpsUser.USER_ORGUNIT_DISPLAY_NAME)) {
            		builder.innerJoin("contact", "belongsTo_", "contactOrgUnit");
            		builder.innerJoin("contactOrgUnit", "orgUnitId_", "orgUnit");
            		builder.addSelectName(sortBy.equals(IOpsUser.USER_ORGUNIT_NAME) ? "orgUnit.name_" : "orgUnit.displayName_");
            		builder.setOrderBy(sortBy.toString());
            	}else{
            	    builder.setOrderBy(sortBy.toString());
              }
              builder.setDescendingSort(sortAscending == false);
            }
        }
        catch (FlexnetBaseException e) {
            throw UtilityService.makeOperationsException(e);
        }
        return builder;
    }

    public void setProductCategoryDAO(ProductCategoryDAO pdtCategoryDAO) {
        this.productCategoryDAO = pdtCategoryDAO;
    }

    public void addProductCategories(String userId, List<ProductCategoryDO> categories)
            throws OperationsException {
        if (userId == null || categories == null)
            return;

        try {
            // validate that the User exists
            IOpsUser usr = getUserByContactId(Long.parseLong(userId));
            OpsUserImpl userImpl = (OpsUserImpl)usr;
            Contact contact = userImpl.getPlatformContactObject();
            User pltUser = contact.getUserInfo();

            Iterator iter = categories.iterator();
            while (iter.hasNext()) {
                ProductCategoryDO cat = (ProductCategoryDO)iter.next();
                // validate that the product category exists
                cat = productCategoryDAO.getProductCategoryById(cat.getId());
                if (cat == null) {
                    throw new OPSBaseException("invalidProductCategory");
                }
                assignProductCategory(cat.getId(), pltUser.getId());
            }
            updateWebServiceUserCacheAboutModifiedUser(pltUser);
        }
        catch (FlexnetBaseException e) {
            throw new OperationsException(e);
        }
    }

    /*
     *  Using native query to avoid performance issues
     */
    private void assignProductCategory(long catId, long userId) throws OperationsException {

		try {
			PersistenceService ps = PersistenceService.getInstance();
			NativeQuery query = ps
					.getTransaction()
					.getHibernateSession()
					.createNativeQuery(
							ps.getQuery("User.assignProductCategory"));

			query.setParameter("productCatId", catId);
			query.setParameter("userId", userId);
			query.setParameter("tenantId", ThreadContextUtil.getTenantId());
			query.executeUpdate();

		}
        catch (FlexnetBaseException fbe) {
            logger.error(new LogMessage("Error on attempt to assign product category to user:"
                    + fbe.toString()));
            throw new OperationsException(fbe);
        }
    }

    public List<ProductCategoryDO> getProductCategoriesPaginated(IOpsUser user, int batchSize,
            int startIndex) throws OperationsException {
        if (user == null)
            return null;

        OpsUserImpl userImpl = (OpsUserImpl)user;
        Contact contact = userImpl.getPlatformContactObject();
        User pltUser = contact.getUserInfo();
        if (pltUser == null)
            return null;
        String query = PersistenceService.getInstance().getQuery("User.getProductCategories");
        try {
            Query hqlQuery = PersistenceService.getInstance().getTransaction()
                    .getHibernateSession().createQuery(query);

            hqlQuery.setLong(0, pltUser.getId().longValue());
            hqlQuery.setFirstResult(startIndex);
            hqlQuery.setMaxResults(batchSize);
            Iterator it = hqlQuery.iterate();
            List<ProductCategoryDO> list = new ArrayList<ProductCategoryDO>();
            while (it.hasNext()) {
                list.add((ProductCategoryDO)it.next());
            }
            return list;
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    public List<ProductCategoryDO> getProductCategories(IOpsUser user) throws OperationsException {
        if (user == null)
            return null;

        OpsUserImpl userImpl = (OpsUserImpl)user;
        Contact contact = userImpl.getPlatformContactObject();
        User pltUser = contact.getUserInfo();
        if (pltUser == null)
            return null;
        String query = PersistenceService.getInstance().getQuery("User.getProductCategories");
        try {
            Query hqlQuery = PersistenceService.getInstance().getTransaction()
                    .getHibernateSession().createQuery(query);

            hqlQuery.setLong(0, pltUser.getId().longValue());
            Iterator it = hqlQuery.iterate();
            List<ProductCategoryDO> list = new ArrayList<ProductCategoryDO>();
            while (it.hasNext()) {
                list.add((ProductCategoryDO)it.next());
            }
            return list;
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    public List<Long> getProductCategoryIdsByUserId(Long userId) throws OperationsException {
        if (userId == null)
            return null;

        String query = PersistenceService.getInstance().getQuery(
                "User.getProductCategoriesIdByUserId");
        try {
            Query hqlQuery = PersistenceService.getInstance().getTransaction()
                    .getHibernateSession().createQuery(query);

            hqlQuery.setLong(0, userId);
            return hqlQuery.list();
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
    }

    public void removeProductCategories(IOpsUser user, List<ProductCategoryDO> categories)
            throws OperationsException {
        if (user == null || categories == null)
            return;

        try {
            // Check if all the categories are being removed from the user.
            // User should always have at least one category assigned.
            // If not, throw proper exception
            List<ProductCategoryDO> existingCategories = getProductCategories(user);
            if (existingCategories.size() == categories.size()) {
                // Just comparing the number of categories already assigned and being removed should
                // be enough
                throw new OPSBaseException("MustHaveOneProductCategory");
            }

            OpsUserImpl userImpl = (OpsUserImpl)user;
            Contact contact = userImpl.getPlatformContactObject();
            User pltUser = contact.getUserInfo();

            Iterator iter = categories.iterator();
            while (iter.hasNext()) {
                ProductCategoryDO cat = (ProductCategoryDO)iter.next();
                // validate that the product category exists
                cat = productCategoryDAO.getProductCategoryById(cat.getId());
                if (cat == null) {
                    throw new OPSBaseException("invalidProductCategory");
                }
                if (cat.getId() != null) {
                    deleteProductCategory(cat.getId(), pltUser.getId());
                }
            }
            updateWebServiceUserCacheAboutModifiedUser(pltUser);
        }
        catch (FlexnetBaseException e) {
            throw new OperationsException(e);
        }
    }

    /*
     *  Using native query to avoid performance issues
     */
    private void deleteProductCategory(long catId, long userId) throws OperationsException {

        try {
            List<Long> l = new ArrayList<>(2);
            l.add(catId);
            l.add(userId);
            QueryUtil.executeUpdate("User.deleteProductCategory", l);
        }
        catch (FlexnetBaseException fbe) {
            logger.error(new LogMessage("Error on attempt to delete product category from user:"
                    + fbe.toString()));
            throw new OperationsException(fbe);
        }
    }

    /*
     *  Return true if product category 'ALL' is assigned to given user
     */
    public boolean isALLCategoryAssigned(long userId) throws OperationsException {
        String query = PersistenceService.getInstance().getQuery("User.getProductCategories");
        try {
            Query hqlQuery = PersistenceService.getInstance().getTransaction()
                    .getHibernateSession().createQuery(query);
            hqlQuery.setLong(0, userId);
            Iterator it = hqlQuery.iterate();
            while (it.hasNext()) {
                ProductCategoryDO pcd = (ProductCategoryDO)it.next();
                if (pcd.getName().equals("ALL"))
                    return true;
            }
        }
        catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        }
        return false;
    }

    private static class SelfServiceUserEntitlement {
        private String activationId = null;
        private IEntitlementManager eMgr = (IEntitlementManager)SpringBeanFactory.getInstance()
                .getBean("entitlementService");
        private String[] partNumbers = null;
        private boolean trialEntitlement = false;

        private SelfServiceUserEntitlement(){}

        static public SelfServiceUserEntitlement fromActivationId(String activationId) {
            SelfServiceUserEntitlement retVal = new SelfServiceUserEntitlement();
            retVal.activationId = activationId;
            return retVal;
        }

        static public SelfServiceUserEntitlement trialEntitlementFromPartNumber(String[] partNumbers) {

            SelfServiceUserEntitlement retVal = new SelfServiceUserEntitlement();
            retVal.partNumbers = partNumbers;
            retVal.trialEntitlement = true;
            return retVal;
        }

        private IEntitlementManager getEntitlementMgr() {
            return eMgr;
        }

        private boolean isTrialEntitlement() {
            return trialEntitlement;
        }

        private OperationsEntity findOrCreateEntitlement(OrganizationUnit org, Map<String, String[]> licenseCustomAttributes)
                throws OperationsException {
            return activationId != null? eMgr.getEntitlementEntityById(activationId) : eMgr
                    .createTrialEntitlement(partNumbers, org, licenseCustomAttributes);
        }
    }

    @Override
    public IOpsUser saveUser(IOpsUser user) throws OperationsException {
        if (user == null) {
            logger.debug(new LogMessage("user argument is null when saving the user."));
            throw UtilityService.makeOperationsException("nullUserDataForSave");
        }

        OpsUserImpl userImpl = (OpsUserImpl)user;

        userImpl.createUser(false);

        return userImpl;

    }

    @Override
    public IResultsList getActiveUsers(IOperationsQuery qry, String orgType)
            throws OperationsException {
        if (OrgUnitType.PUBLISHER.equalsIgnoreCase(orgType)) {
            return getPublisherUsers(qry, null, false);
        }
        else if (OrgUnitType.CHANNELPARTNER.equalsIgnoreCase(orgType)) {
            return getChannelPartnerUsers(qry, null, false);
        }
        else if (OrgUnitType.SELFREGISTERED.equalsIgnoreCase(orgType)) {
            return getSelfRegisteredUsers(qry, null, false, true);
        }
        return getCustomerUsers(qry, null, false);
    }
    
    public List<IOpsUser> getContacts(HashMap searchValues) throws FlexnetHibernateException
 {
		// Find the Contact object in the database by name (if it exists)
		// and see if it is the same object as the one being validated.
		// If not, then there is going to be a unique name violation,
		// so throw an error.

		// Hack to make sure that Hibernate does not flush the cache
		// prematurely.
		// Doing so may cause database constraints to be violated (before we had
		// a chance to stop
		// the data from being set)
		// and then the client would see a database exception instead of a nice,
		// clean validation
		// exception.
		// So we save the existing Flush Mode and then set it explicitly to
		// "COMMIT".
		FlushModeType fm = PersistenceService.getInstance().getTransaction()
				.getHibernateSession().getFlushMode();
		PersistenceService.getInstance().getTransaction().getHibernateSession()
				.setFlushMode(FlushModeType.COMMIT);

		List contacts = Entity.getAll(Contact.class, "", true, -1, 0,
				searchValues);

		// Hack to make Hibernate flush like before.
		// Set the flush mode back to the saved value.
		PersistenceService.getInstance().getTransaction().getHibernateSession()
				.setFlushMode(fm);
		return contacts;
	}
    /* This method is written to register an existing user for a part number.
     * This creates a new entitlement for provided organization.
     * This method would also save the relation of user and part number which will restrict the same user to re-register for a part number. */
    public void createTrialEntitlementForExistingUser(IOpsUser user, String[] partNumbers,
            String company,Map<String, String[]> licenseCustomAttributes) throws OperationsException, FlexnetBaseException, ClassNotFoundException, SQLException {
        SelfServiceUserEntitlement ent = SelfServiceUserEntitlement.trialEntitlementFromPartNumber(partNumbers);
        Set<IOrgUnitInterface> orgSet = user.getBelongsToOrganizations();
        Iterator it = orgSet.iterator();
        if (company == null) {
            IOrgUnitInterface org = (IOrgUnitInterface)it.next();
            ent.findOrCreateEntitlement((OrganizationUnit)org, licenseCustomAttributes);
        }
        else {
            while (it.hasNext()) {
                IOrgUnitInterface org = (IOrgUnitInterface)it.next();
                if (company.equals(org.getName())) {
                    ent.findOrCreateEntitlement((OrganizationUnit)org, licenseCustomAttributes);
                }
            }
        }
        saveUserRelationWithPartNumber(partNumbers, user);
    }
    /*This method would save the relation of user and part number registered through self-registration or TBYB*/
    public static void saveUserRelationWithPartNumber(String[] partNumbers, IOpsUser user) throws SQLException, ClassNotFoundException {
        Connection sqlConn = DbUtil.getDbConnection();
        PreparedStatement saveUserPartMapping = sqlConn.prepareStatement(PersistenceService.getInstance().getQuery("User.saveUserAndPartNumberMapping"));
        for (String part : partNumbers) {
        	 saveUserPartMapping.setString(1, part);
             saveUserPartMapping.setString(2, ThreadContextUtil.getTenantId());
             saveUserPartMapping.setString(3, user.getUserId());
             saveUserPartMapping.setString(4, ThreadContextUtil.getTenantId());
             saveUserPartMapping.setString(5, ThreadContextUtil.getTenantId());
             saveUserPartMapping.addBatch();
        }
        saveUserPartMapping.executeBatch();
        saveUserPartMapping.close();
    }

    private Domain getDomain(String domainName)
            throws FlexnetBaseException, OperationsException {
        Map searchValues = new HashMap();
        Domain domain = null;
        // check if external domain is registered and is active.
        searchValues.clear();
        searchValues.put(Domain.NAME, domainName);
        try {
            domain = (Domain)Entity.getOne(Domain.class, searchValues);
        }
        catch (Exception e) {
            logger.info(
                    new LogMessage("OperationsUserService.validateDomainUsers(): Domain Missing :"
                            + domainName));
        }
       
        return domain;
    }

    private String getDetachedByUser() {
        String detachedByUser = "FNO";
        User user = ThreadContextUtil.getUser();
        if (user != null) {
            detachedByUser = user.getUserId();
        } else if (MessageContext.getCurrentContext() != null) {
            detachedByUser = MessageContext.getCurrentContext().getUsername();
        }
        return detachedByUser;
    }

    private DetachUserRequest populateDetachUserRequest(Contact contact) {
        String detachedByUser = getDetachedByUser();
        DetachUserRequest detachUserRequest = new DetachUserRequest();
        detachUserRequest.setDetachedByUser(detachedByUser);
        detachUserRequest.setTenantName(ThreadContextUtil.getTenantId());
        detachUserRequest.setUserEmail(contact.getEmail());
        detachUserRequest.setUserId(contact.getId());
        return detachUserRequest;
    }
    
    private void detachUserFromDevice(List<Contact> users) throws OperationsException {
        OperationsUserServiceConfig serviceConfig = SpringBeanFactory.getInstance().getBean(OperationsUserServiceConfig.class);
        if (serviceConfig.isDetachUserLFSAPIEnabled()){
            for (Contact contact : users) {
                try {
                    deviceService.detachUser(populateDetachUserRequest(contact));
                } catch (Exception e) {
                    throw new OperationsException("Unable to detach user: "+ contact.getId() + " from owned devices.");
                }
            }
        }else {
            List<String> contactEmails = users.stream().map(Contact::getEmail).collect(Collectors.toList());
            try {
                for (String email : contactEmails) {
                    ManageDeviceServiceWebservice service = new ManageDeviceServiceWebservice();
                    GetDevicesResponseTypeDTO deviceQueryresponse = new GetDevicesResponseTypeDTO();
                    SimpleQueryTypeDTO queryParam = new SimpleQueryTypeDTO(email,
                            SimpleSearchTypeDTO.EQUALS);
                    GetDeviceCountResponseTypeDTO deviceCountresponse = new GetDeviceCountResponseTypeDTO();
                    GetDevicesParametersTypeDTO deviceParam = new GetDevicesParametersTypeDTO();
                    deviceParam.setUserString(queryParam);

                    GetDevicesCountRequestTypeDTO devcountreq = new GetDevicesCountRequestTypeDTO(
                            deviceParam);
                    deviceCountresponse = service.getDeviceCount(devcountreq);

                    if (BigInteger.valueOf(0).compareTo(deviceCountresponse.getResponseData().getCount()) < 0) {

                        GetDevicesRequestTypeDTO msgparameters = new GetDevicesRequestTypeDTO(
                                deviceParam, null, new BigInteger("1"),
                                deviceCountresponse.getResponseData().getCount());

                        deviceQueryresponse = service.getDevicesQuery(msgparameters);

                        DeviceQueryDataTypeDTO[] devices = deviceQueryresponse
                                .getResponseData().getDevice();

                        UpdateDevRequestTypeDTO req = new UpdateDevRequestTypeDTO();
                        UpdateDevDataTypeDTO[] devdata = new UpdateDevDataTypeDTO[devices.length];

                        if (devices.length > 0) {
                            for (int i = 0; i < devices.length; i++) {

                                DeviceIdentifierDTO deviceidentifier = new DeviceIdentifierDTO(
                                        devices[i].getDeviceIdentifier()
                                                .getDeviceType(),
                                        null,
                                        devices[i].getDeviceIdentifier().getDeviceId(),
                                        devices[i].getDeviceIdentifier().getServerIds(),
                                        devices[i].getDeviceIdentifier()
                                                .getDeviceIdType(), devices[i]
                                        .getDeviceIdentifier()
                                        .getPublisherName());

                                UpdateDevDataTypeDTO dev = new UpdateDevDataTypeDTO();
                                dev.setDeviceIdentifier(deviceidentifier);
                                dev.setUser("");
                                devdata[i] = dev;
                            }
                            req.setDevice(devdata);

                            service.updateDevice(req);
                        }
                    }

                }

            } catch (Exception ex) {
                throw new OperationsException(ex);
            }
        }
	}
    
    /* Removes obsoleted user email's from email templates */
	public void removeUsersFromEmailTemplates(List<String> userEmails)
			throws OperationsException {

		String hql = "delete from OPS_EMAIL_TO_TEMPLATE where email_addr in (:values)";

		try {
			NativeQuery query = PersistenceService.getInstance()
					.getTransaction().getHibernateSession()
					.createNativeQuery(hql);

			query.setParameterList("values", userEmails);
			query.executeUpdate();
		} catch (FlexnetBaseException fbe) {
			logger.error(new LogMessage(
					"SQL error on attempt to remove user emails from email templates"
							+ fbe.toString()));
			throw new OperationsException(fbe);
		}

	}
	
	
	 /*This method would remove the relation of user and part number registered through self-registration or TBYB*/
		public void removeUserAndPartNumberMapping(String userEmail)
				throws OperationsException {

			if(!userEmail.isEmpty()){
			String sqlQuery = "DELETE from OPS_SKU_USER"
	        		+ " where USER_ID = ( select ID from PLT_USER where USERID in (:value) and TENANT_ID =:tenantid)";
			try {
				NativeQuery query = PersistenceService.getInstance()
						.getTransaction().getHibernateSession()
						.createNativeQuery(sqlQuery);

				query.setParameter("value", userEmail);
				query.setParameter("tenantid", ThreadContextUtil.getTenantId());
				query.executeUpdate();
			} catch (FlexnetBaseException fbe) {
				logger.error(new LogMessage(
						"SQL error on attempt to remove user part number relation"
								+ fbe.toString()));
				throw new OperationsException(fbe);
			}
			}

		}
   		
	/**
	 * 
	 * Removes all the user email's from alerts
	 * if the template contain's more than one email, we will modify and update the template else Unsubscribe the alert
	 */
	public void removeUsersFromAlertTemplates(List < String > contactEmails) throws OperationsException {

		PersistenceService ps = PersistenceService.getInstance();
	    try {
	        Long userId = ThreadContext.getInstance().getLoginContext().getUser().getId();
	        List < AlertSubscription > activeSubscriptions = AlertService.getInstance().findSubscription(userId);	        
	        Map < String, EntityProperty > propertyMap = new HashMap();
	       
	        if (activeSubscriptions != null) {
				for (AlertSubscription subscription : activeSubscriptions) {

					Set<AlertHandlerConfiguration> handlers = subscription.getHandlers();

					for (AlertHandlerConfiguration handlerconfig  : handlers) {
						if(handlerconfig.getProperties()!=null)
						{
						EntityProperty entityProperty = (EntityProperty) handlerconfig.getProperties().getProperties().get("toAddress");						
						String strVal = entityProperty.getStringValue();
						
						for (String email : contactEmails) {

							strVal = strVal.replaceAll(email + ",", "");
							strVal = strVal.replaceAll("," + email, "");
							strVal = strVal.replaceAll(email, "");
						}
						
						if (strVal.isEmpty()) {
							if (subscription != null) {
								ps.delete(subscription);
							}
						} else {							
							 EntityProperty entryProp = new EntityProperty(entityProperty.getClassName(), false, entityProperty.getStringValue(), entityProperty.getValidator(), ThreadContextUtil.getTenantId());		
							 entryProp.setStringValue(strVal);							
							 EntityPropertySet entryPropSet = new EntityPropertySet(null, ThreadContextUtil.getTenantId(), propertyMap);
							 entryPropSet.setId( handlerconfig.getProperties().getId());
							 propertyMap.put("toAddress", entryProp);
							 entryPropSet.setProperties(propertyMap);
							 
							PersistenceService.getInstance().getTransaction().getHibernateSession().merge(entryPropSet);
						}

					  }
					}
				}
	        }
	    } catch (FlexnetBaseException fbe) {
	        logger.error(new LogMessage(
	            "error on attempt to remove user's email from email templates" +
	            fbe.toString()));
	        throw new OperationsException(fbe);
	    }
	}
	
	
	public void setObsoleteDetails(Contact contact,String randomEmail) throws FlexnetBaseException
	{
		
		contact.setOptIn(false);
		contact.setFirstName(User.OBSOLETE_CONTACT);
		contact.setLastName(User.OBSOLETE_CONTACT);
		contact.setEmail(randomEmail);
		contact.setPhone(User.OBSOLETE_CONTACT);
		contact.setFax(User.OBSOLETE_CONTACT);
		contact.setStreetAddress(User.OBSOLETE_CONTACT);
		contact.setState(User.OBSOLETE_CONTACT);
		contact.setCity(User.OBSOLETE_CONTACT);					
		contact.setDisplayName(User.OBSOLETE_CONTACT);
		contact.setCountry(AppConfigUtil.getConfigString("ops.defaultCountry"));
		contact.setLocale( Locale.US.toString());
		TimeZone timezone = TimeZone.getDefault();
		contact.setTimezone(DateUtility.getTimeZoneID(timezone));
		contact.setPostalCode(User.OBSOLETE_CONTACT);
	}
	

	public void segregateUsersAndDelete(List < IOpsUser > users, List < IOpsUser >unlinkedUsers) throws OperationsException {
        IOperationsUserManager opsUserMgr = OperationsServiceFactory.getOperationsUserManager();
        List<Contact> dependentUsers = new ArrayList<Contact>();
        List<OpsUserImpl> list = (List) unlinkedUsers;
        try {
            if (CollectionUtils.isNotEmpty(unlinkedUsers)) {
                for (IOpsUser unlinkeduser : unlinkedUsers) {
                    OpsUserImpl userImpl = (OpsUserImpl) unlinkeduser;
                    Contact contact = userImpl.getPlatformContactObject();
                    User pltUser = contact.getUserInfo();
                    if (pltUser != null) {
                        removeProductCategoryAssignments(pltUser);
                        userImpl.resetUserRoles();
                        updateWebServiceUserCacheAboutModifiedUser(pltUser);
                        pltUser.delete();
                        contact.delete();
                    } else {
                        contact.delete();
                    }
                    EntityNotificationInvoker.notify(new UserEntityProcessor(
                            (OpsUserImpl) unlinkeduser), EventType.DELETE);
                }
            }
            for (IOpsUser user : users) {
                OpsUserImpl userImpl = (OpsUserImpl) user;
                Contact contact = userImpl.getPlatformContactObject();
                String contactEmail = contact.getEmail();
                User pltUser = contact.getUserInfo();

                if (pltUser != null) {
                    if ((ThreadContextUtil.getSeedUserIds().contains(pltUser.getId()) || !ThreadContextUtil.getUser().containsAllPermissions(pltUser.getPermissions())) || ThreadContextUtil.getUser().equals(pltUser)) {
                        throw UtilityService.makeOperationsException("noAccessPermission");
                    }

                    if (CommonUtils.checkIfExists("User.getChannelPartners.Count", contact.getId()) || CommonUtils.checkIfExists("User.getActivatableItems.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getEntitlements.Count", pltUser.getId()) ||
                            CommonUtils.checkIfExists("User.getFulfillmentHistory.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getHosts.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getImportJobs.Count", pltUser.getId()) ||
                            CommonUtils.checkIfExists("User.getImportSkuJobs.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getStateChangeHistory.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getImportWRKRequests.Count", pltUser.getId()) ||
                            CommonUtils.checkIfExists("User.getAlertSubscriptions.Count", pltUser.getId()) || CommonUtils.checkIfExists("User.getAlertInbox.Count", pltUser.getId())) {
                        setAllowForceDelete(true);
                        dependentUsers.add(contact);
                    } else {
                        removeProductCategoryAssignments(pltUser);
                        userImpl.resetUserRoles();
                        updateWebServiceUserCacheAboutModifiedUser(pltUser);
                        pltUser.delete();
                        contact.delete();
                    }
                } else {
                    if (CommonUtils.checkIfExists("User.getChannelPartners.Count",
                            contact.getId())) {
                        setAllowForceDelete(true);
                        dependentUsers.add(contact);
                    } else {
                        contact.delete();
                    }
                }
                EntityNotificationInvoker.notify(new UserEntityProcessor((OpsUserImpl) user), EventType.DELETE);
            }
            forcedelete(dependentUsers);
        } catch (FlexnetBaseException ex) {
            throw new OperationsException(ex);
        } catch (Exception ex) {
            throw new OperationsException(ex);
        }

    }

	/* gets called when delete operation is performed on users with dependencies. This method will make a user obsolete by setting obsolete details to users   */
	private void forcedelete(List<Contact> dependentUsers) throws OperationsException {
		boolean txnStarted =false;
		List<String> userEmails =dependentUsers.stream().map(Contact::getEmail).collect(Collectors.toList());		
		try{
		detachUserFromDevice(dependentUsers);
		txnStarted = TransactionHelper.startTransaction();	
				
		for(Contact contact:dependentUsers)
		{
			User pltUser = contact.getUserInfo();
			String email=contact.getEmail();
			String randomEmail = RandomStringUtils.random(12, true, true) + User.OBSOLETE_DOMAIN;							
			setObsoleteDetails(contact, randomEmail);		
			if(pltUser!=null)
			{
                updateWebServiceUserCacheAboutModifiedUser(pltUser);
	            pltUser.forceDeleteUser(User.OBSOLETE_USER, randomEmail);
			}
			
			contact.forceDeleteContact(contact);
			
		}						
		removeUsersFromAlertTemplates(userEmails);
		removeUsersFromEmailTemplates(userEmails);
		TransactionHelper.commitTransaction("users removed", txnStarted);
		txnStarted = false;
		
	}
 catch (Exception ex) {
			logger.error(new LogMessage(
					"Error performing force delete operations on selected users"
							+ ex.toString()));
			throw new OperationsException(ex);
		}
 finally {
			TransactionHelper.rollbackTransaction("forcedelete()", txnStarted);
		}		
	}
	
/*
 * Get contact by email address of contact
 * email id not registered in system it will return null
 */	
	public static Contact getContactByEmail(String emailAddress) throws OperationsException {
		Contact c = null;
		User u = null;
		try {
			Map<String, String> searchValues = new HashMap<String, String>();
			if (emailAddress != null)
				searchValues.put("obj.email_", emailAddress);
			c = (Contact) Entity.getOne(Contact.class, searchValues);
			return c;
		} catch (FlexnetBaseException ex) {
			logger.error(new LogMessage("Error occured during fetching contact info " + ex.getMessage()));
			return c;
		}
	}

    public boolean doesUserHavePermissionInAccount(String permission, String accountId, String userId) throws OperationsException {
        try {
            PersistenceService ps = PersistenceService.getInstance();
            String nativeQuery="select count(*) from PLT_USER u " +
                    "inner join PLT_CONTACT c on u.CONTACTINFO_ID = c.ID and u.TENANT_ID=:tenantId and c.TENANT_ID = :tenantId " +
                    "inner join PLT_ORGUNIT_CONTACT oc on oc.CONTACTINFO_ID = c.ID  " +
                    "inner join PLT_ORGUNIT o on o.id=oc.ORG_ID and o.TENANT_ID=:tenantId " +
                    "inner join PLT_USER_ORGUNIT_ROLE puor on puor.ORG_ID = o.ID and puor.USER_ID = u.ID " +
                    "inner join PLT_ROLE r on r.id=puor.ROLE_ID and r.TENANT_ID = :tenantId " +
                    "inner join PLT_ROLE_PERMISSION rp on rp.GRANTEDBY_ID = r.ID " +
                    "inner join PLT_PERMISSION p on rp.GRANTS_ID = p.ID " +
                    "and p.NAME = :permission " +
                    "and u.USERID = :userId AND o.NAME like :accountId";
            Query query = ps.getTransaction().getHibernateSession().createNativeQuery(nativeQuery);
            query.setParameter("userId", userId);
            query.setParameter("accountId", accountId);
            query.setParameter("permission", permission);
            Integer count = (Integer) query.getSingleResult();
            return count > 0;
        } catch(Exception ex){
            ex.printStackTrace();
            throw new OperationsException(ex);
        }
    }

    /**
     * Get the producer ORG User Details Count for Tenant. This result required for registry service requirement.
     * @param tenantid
     * @param permission - optional
     * @return
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unused")
    public long getProducerOrgUserCount(String tenantid, String permission)throws OperationsException {
    	
    	String permissionId = getDPPermissionId(permission);// as this is never be null, i am not doing null check here.
    	
        String sqlQuery = "select count(*)"
                + "from plt_orgunit ou with (nolock) "
                + "inner join PLT_ORGUNIT_ORGUNITTYPE ouout with (nolock) on ou.id=ouout.ORGUNIT_ID and ou.TENANT_ID='" + tenantid
                + "' inner join PLT_ORGUNITTYPE outype with (nolock) on ouout.ORGUNITTYPE_ID = outype.ID and outype.NAME='PUBLISHER' "
                + "inner join plt_orgunit_contact oc with (nolock) on oc.org_id = ou.id "
                + "inner join PLT_CONTACT c with (nolock) on c.id=oc.CONTACTINFO_ID and c.TENANT_ID='" + tenantid
                + "' inner join plt_user u with (nolock) on u.CONTACTINFO_ID=c.id and u.TENANT_ID ='" + tenantid +"'";

        if(StringUtils.isNotEmpty(permission))
            sqlQuery+= " inner join plt_user_orgunit_role puor with (nolock) on puor.ORG_ID=ou.ID and puor.USER_ID=u.ID and puor.TENANT_ID='" + tenantid
                    + "' inner join plt_role r with (nolock) on puor.ROLE_ID = r.id and r.TENANT_ID='" + tenantid
                    + "' inner join PLT_ROLE_PERMISSION rp with (nolock) on rp.GRANTEDBY_ID=r.ID "
                    + " and rp.GRANTS_ID= '"+permissionId+"'";
		
		// Filter only active users, always.
        sqlQuery += "where u.ACTIVE = 1";

        boolean txnStarted =false;
        Query query;
        try {
            txnStarted = TransactionHelper.startTransaction();
            query = PersistenceService.getInstance().getTransaction().getHibernateSession().createNativeQuery(sqlQuery);
            Object o = query.uniqueResult();
            TransactionHelper.commitTransaction("CountOfProducerUsersRest", txnStarted);
            txnStarted = false;
            if(o instanceof Integer)
                return ((Integer)o).longValue();
            else if (o instanceof Long)
                return ((Long)o).longValue();
            else
                return 0l;
        } catch (Exception ex) {
            logger.error(new LogMessage("Not able to fetch producer org user count details : getProducerOrgUserCount() " + ex.getMessage()));
            throw new OperationsException("Some thing wrong while fetching producer org user count details" + ex.getMessage());
        } finally {
            TransactionHelper.rollbackTransaction("CountOfProducerUsersRest", txnStarted);
        }
    }
    
    /**
     * This will return PLT_PERMISSION id. This method is required for performance improvement.
     * @return
     * @throws OperationsException
     */
    private String getDPPermissionId(String dpPermission) throws OperationsException {
    	
    	String sqlQuery = "SELECT ID FROM PLT_PERMISSION WITH(NOLOCK) WHERE NAME='" + dpPermission + "'";
    	Query query = null;
    	String id = null;
		try {
			
           query = PersistenceService.getInstance().getTransaction().getHibernateSession().createNativeQuery(sqlQuery);
           BigDecimal bg =(BigDecimal) query.list().get(0); //This is never be null and always return single value 
           id =  bg.toString();
           
		} catch (Exception ex) {
			logger.error(new LogMessage("Not able to fetch permission id : getDPPermissionId() " + ex.getMessage()));
			throw new OperationsException("Some thing wrong while fetching producer org details" + ex.getMessage());
		} finally {
			logger.debug(new LogMessage("Query execution successfully completed..."));
        }
		
		return id;
    }
    
    /**
     * Get the producer ORG User Details for Tenant. This result required for registry service requirement.
     * @param tenantid
     * @param permission - optional
     * @return
     * @throws Exception
     */
    @Override
    @SuppressWarnings("unused")
	public List<OrgDetails> getProducerOrgUserDetails(String tenantid, String permission,
                                                  int batchSize, int pageNumber)throws OperationsException {

    	String permissionId = getDPPermissionId(permission);
		List<OrgDetails> orgList;

		String sqlQuery = "select u.USERID as userId, " +
                "u.id as id,"+
                "c.EMAIL as email, " +
                "c.firstname as firstName, " +
                "c.lastName as lastName, "+
                "c.city as city, "+
                "c.state as state, "+
                "c.postalcode as postalCode, "+
                "c.country as country, "+
                "ou.id as orgId, " +
                "ou.name as orgName, "+
                "ou.displayname as orgDisplayName, " +
                "ou.addr_city as orgCity,"+
                "ou.addr_state as orgState,"+
                "ou.addr_country as orgCountry,"+
                "ou.addr_zipcode as orgPostalCode, "+
                "ou.addr_region as orgRegion "
				+ "from plt_orgunit ou with (nolock) "
				+ "inner join PLT_ORGUNIT_ORGUNITTYPE ouout with (nolock) on ou.id=ouout.ORGUNIT_ID and ou.TENANT_ID='" + tenantid 
				+ "' inner join PLT_ORGUNITTYPE outype with (nolock) on ouout.ORGUNITTYPE_ID = outype.ID and outype.NAME='PUBLISHER' "
				+ "inner join plt_orgunit_contact oc with (nolock) on oc.org_id = ou.id "
				+ "inner join PLT_CONTACT c with (nolock) on c.id=oc.CONTACTINFO_ID and c.TENANT_ID='" + tenantid 
				+ "' inner join plt_user u with (nolock) on u.CONTACTINFO_ID=c.id and u.TENANT_ID ='" + tenantid +"'";

        if(StringUtils.isNotEmpty(permission))
            sqlQuery+= " inner join plt_user_orgunit_role puor with (nolock) on puor.ORG_ID=ou.ID and puor.USER_ID=u.ID and puor.TENANT_ID='" + tenantid
				+ "' inner join plt_role r with (nolock) on puor.ROLE_ID = r.id and r.TENANT_ID='" + tenantid 
				+ "' inner join PLT_ROLE_PERMISSION rp with (nolock) on rp.GRANTEDBY_ID=r.ID "
                + " and rp.GRANTS_ID= '"+permissionId+"'";
				
		// Filter only active users, always.
        sqlQuery += "where u.ACTIVE = 1";

        sqlQuery +=   " order by u.id";

        boolean txnStarted =false;
		Query query;
		try {
            txnStarted = TransactionHelper.startTransaction();
            query = PersistenceService.getInstance().getTransaction().getHibernateSession()
                    .createNativeQuery(sqlQuery);
			query.setResultTransformer(Transformers.aliasToBean(OrgDetails.class));
			query.setFirstResult((pageNumber-1)*batchSize);
			query.setMaxResults(batchSize);
			orgList= query.list();
            TransactionHelper.commitTransaction("GetProducerUsersREST", txnStarted);
            txnStarted = false;
		} catch (Exception ex) {
			logger.error(new LogMessage("Not able to fetch producer org details : getProducerOrgDetails() " + ex.getMessage()));
			throw new OperationsException("Some thing wrong while fetching producer org details" + ex.getMessage());
		} finally {
            TransactionHelper.rollbackTransaction("GetProducerUsersREST", txnStarted);
        }
		return orgList;
	}
}
