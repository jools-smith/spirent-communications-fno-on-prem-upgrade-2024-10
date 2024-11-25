
package com.flexnet.operations.services;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.hibernate.query.Query;

import com.flexnet.operations.api.ICallbackListProcessor;
import com.flexnet.operations.api.IOperationsQuery;
import com.flexnet.operations.api.IOpsUser;
import com.flexnet.operations.api.IOrgUnitInterface;
import com.flexnet.operations.api.IRole;
import com.flexnet.operations.publicapi.OperationsException;
import com.flexnet.platform.exceptions.FlexnetBaseException;
import com.flexnet.platform.services.logging.LogMessage;
import com.flexnet.platform.services.persistence.PersistenceService;
import com.flexnet.platform.util.DateUtility;
import com.flexnet.platform.util.FlexnetTimer;

public class UserOrgDataCallbackProcessor implements ICallbackListProcessor, java.io.Serializable {

    private transient Map<Map<Long,Long>, IOrgUnitInterface> gOrgIdMap = new LinkedHashMap<Map<Long,Long>, IOrgUnitInterface>();
    private boolean populateOrgsAndRoles;
    private boolean populateDomain;

    public UserOrgDataCallbackProcessor(){

    }

    public UserOrgDataCallbackProcessor(
            boolean getOrgsAndRoles, boolean hasDomains){
        populateOrgsAndRoles = getOrgsAndRoles;
        populateDomain = hasDomains;
    }

    @SuppressWarnings("unchecked")
    public List process(Iterator resultIterator) throws OperationsException {
        List<Long> userIdList = new ArrayList<Long>();
        List<Long> contactIdList = new ArrayList<Long>();        
        Map<Long, IOpsUser> opsContactMap = new LinkedHashMap<Long, IOpsUser>();
        Map<Long, IOpsUser> opsUserMap = new LinkedHashMap<Long, IOpsUser>();
        Map<Long, Map<Long, Set<IRole>>> userDataMap = new LinkedHashMap<Long, Map<Long, Set<IRole>>>();
        Map<Long, IOpsUser> sortedUserMap = new LinkedHashMap<Long, IOpsUser>();
        
        ProcessUserListResponse userDataMapResponse = new ProcessUserListResponse();
        userDataMapResponse.setUserToOrgToRoleRelationShip(userDataMap);
        try {
        	if (resultIterator != null) {
                FlexnetTimer timer = new FlexnetTimer();
                timer.start();
                while (resultIterator.hasNext()) {
                    Object[] tuples = (Object[])resultIterator.next();

                    IOpsUser opsUser = new OpsUserImpl();

                    ((OpsUserImpl)opsUser).setId((Long)tuples[0]);      
                    opsUser.setFirstName((String)tuples[1]);
                    opsUser.setLastName((String)tuples[2]);
                    opsUser.setMiddleInitial((String)tuples[3]);
                    opsUser.setPhone((String)tuples[4]);
                    opsUser.setEmail((String)tuples[5]);
                    opsUser.setDisplayName((String)tuples[6]);
                    opsUser.setFax((String)tuples[7]);
                    opsUser.setLocale((String)tuples[8]);
                    opsUser.setStreetAddress((String)tuples[9]);
                    opsUser.setCity((String)tuples[10]);
                    opsUser.setState((String)tuples[11]);
                    opsUser.setPostalCode((String)tuples[12]);
                    opsUser.setCountry((String)tuples[13]);
                    String dbTimeZone = (String)tuples[14];
                    if (dbTimeZone != null) {
                        TimeZone timezone = TimeZone.getTimeZone(dbTimeZone);
                        opsUser.setTimezone(DateUtility.getTimeZoneID(timezone));
                    }
                    Object optInObj = tuples[15];
                    if (optInObj != null) {
                        int optIn = ((Integer)optInObj).intValue();
                        if (optIn == 1)
                            opsUser.setOptIn(true);
                        else
                            opsUser.setOptIn(false);
                    }

                    Long uid = (Long)tuples[16];
                    Long cid = (Long)tuples[0];
                    String userId = (String)tuples[17];
                    if (userId != null && userId.length() > 0)
                        opsUser.setCanLogin(true);
                    if (tuples[18] != null && ((Integer)tuples[18]).intValue() == 0)
                        opsUser.setActive(false);
                    else
                        opsUser.setActive(true);
                    Object sharedObj = tuples[19];
                    if (sharedObj != null) {
                        int sharedLogin = ((Integer)tuples[19]).intValue();
                        if (sharedLogin == 1)
                            opsUser.setIsSharedLogin(true);
                        else
                            opsUser.setIsSharedLogin(false);
                    }
                    Date lastModified = (Date)tuples[20];
                    if (lastModified != null) {
                        ((OpsUserImpl)opsUser).setLastModifiedDate(lastModified);
                    }

                    if (userId == null) {
                        opsUser.setCanLogin(false);
                        contactIdList.add(cid);
                        opsContactMap.put(cid, opsUser);
                        sortedUserMap.put(opsUser.getId(), opsUser);
                    }
                    else {
                        opsUser.setUserId(userId);
                        opsUser.setCanLogin(true);
                        boolean b = Boolean.valueOf(tuples[21].toString());
                        opsUser.setRenewalSubscription(b);
                        userIdList.add(uid);
                        opsUserMap.put(uid, opsUser);
                        sortedUserMap.put(opsUser.getId(), opsUser);
                    }
                    Date createdDate = (Date)tuples[22];
                    if (createdDate != null) {
                        ((OpsUserImpl)opsUser).setCreatedDate(createdDate);
                    }

                    Date lastLogin = (Date)tuples[23];
                    if (lastLogin != null) {
                        ((OpsUserImpl)opsUser).setLastAuthenticatedTime(lastLogin);
                    }
					if (populateDomain) {

						//updating two new values in response for createdBy and LastModifiedBy
                        opsUser.setCreatedBy((String)tuples[24]);
                        opsUser.setLastModifiedBy((String)tuples[25]);
                        opsUser.setDomainName((String)tuples[26]);
					}
					else {
						opsUser.setCreatedBy((String)tuples[24]);
						opsUser.setLastModifiedBy((String)tuples[25]);
					}
                }
                timer.logElapsedTimeFromLastMeasured("MANAGE_USERS", new LogMessage(
                        "Finished loading domain values "));
                Map<Long, Set<IOrgUnitInterface>> contactOrgMap = processContactList(contactIdList);
                timer.logElapsedTimeFromLastMeasured("MANAGE_USERS", new LogMessage(
                        "Finished processing contact list "));
                userDataMapResponse = processUserList(userIdList, userDataMapResponse);
                timer.logElapsedTimeFromLastMeasured("MANAGE_USERS", new LogMessage(
                        "Finished processing user list "));
                // Process the whole stuff now
                List<IOpsUser> userData = new ArrayList<IOpsUser>();
                Iterator userKeysIt = userIdList.iterator();
                OperationsUserService userService = new OperationsUserService();

                while (userKeysIt.hasNext()) {
                    Long uid = (Long)userKeysIt.next();
                    Set<IOrgUnitInterface> orgList = new HashSet<IOrgUnitInterface>();
                    Map<Long, Set<IRole>> orgRoleMap = userDataMapResponse.getUserToOrgToRoleRelationShip().get(uid);
                    if (orgRoleMap != null) {
                        Iterator orgsIt = orgRoleMap.keySet().iterator();
                        while (orgsIt.hasNext()) {
                            Long orgId = (Long)orgsIt.next();
                            HashMap<Long,Long> hm=new HashMap<Long,Long>();
                            hm.put(uid,orgId);
                            IOrgUnitInterface iOrg = gOrgIdMap.get(hm);
                            iOrg.setExpiryUser(userDataMapResponse.getExpiryDateMap().get(uid).get(orgId));
                            orgList.add(iOrg);
                        }
                    }
                    Map userExpMap = userDataMapResponse.getExpiryDateMap();
                    Map userMap = (Map)userExpMap.get(uid);
                    if (userMap != null) {
                        Set expDateSet = userMap.keySet();
                        for (Object o : expDateSet) {
                            Long key = (Long)o;
                            if (userMap.get(key) != null && !"".equals(userMap.get(key))) {
                                userMap.put(key, DateUtility.parseDateStringFormate(
                                        new Date(((Timestamp)userMap.get(key)).getTime())));
                            }
                            else {
                                userMap.put(key, null);
                            }
                        }
                    }
                    IOpsUser opsUser = opsUserMap.get(uid);
                    ((OpsUserImpl)opsUser)
                            .setAcctExpiryDateMap(userMap);
                    ((OpsUserImpl)opsUser).setBelongsToOrgs(orgList);
                    ((OpsUserImpl)opsUser).setOrgRoleMap(orgRoleMap);
                    userData.add(opsUser);
                }
                // Now process the contacts.
                Iterator cIt = contactIdList.iterator();
                while (cIt.hasNext()) {
                    Long cid = (Long)cIt.next();
                    Set<IOrgUnitInterface> orgList = contactOrgMap.get(cid);
                    IOpsUser opsUser = opsContactMap.get(cid);
                    Map<Long, Set<IRole>> orgRoleMap = new HashMap<Long, Set<IRole>>();
                    ((OpsUserImpl)opsUser).setBelongsToOrgs(orgList);
                    ((OpsUserImpl)opsUser).setOrgRoleMap(orgRoleMap);
                    userData.add(opsUser);
                }
                for(IOpsUser user:userData) {
                	if(sortedUserMap.containsKey(user.getId())) {
                		sortedUserMap.put(user.getId(), user);
                	}
                }
                //updating the list with the sorted data
                userData = new ArrayList<IOpsUser>(sortedUserMap.values());
                return userData;
            }
            return new ArrayList();
        }
        catch (Exception e) {
            throw new OperationsException(e);
        }

    }
    private Map<Long, Set<IOrgUnitInterface>> processContactList(List<Long> contactIdList)
            throws OperationsException {
        Map<Long, Set<IOrgUnitInterface>> contactOrgMap = new LinkedHashMap<Long, Set<IOrgUnitInterface>>();
        if (contactIdList.size() == 0 || !populateOrgsAndRoles)
            return new HashMap<Long, Set<IOrgUnitInterface>>();
        try {
            PersistenceService ps = PersistenceService.getInstance();
            IOperationsQuery qry = new IOperationsQuery();
            qry.setBatchSize(-1);
            String hQuery = ps.getQuery("UserService.getContactOrgUnits");
            Query hqlQryObj = ps.getTransaction().getHibernateSession().createQuery(hQuery);
            hqlQryObj.setParameterList("idList", contactIdList);
            List results = hqlQryObj.list();
            /*
            contact.id,
            org.id,
            org.name_,
            org.displayName_,
            og.description_ 
             */
            Iterator it = results.iterator();
            while (it.hasNext()) {
                Object[] tuples = (Object[])it.next();
                Long cid = (Long)tuples[0];
                Long oid = (Long)tuples[1];
                String oName = (String)tuples[2];
                String oDispName = (String)tuples[3];
                String oDesc = (String)tuples[4];
                String orgType = (String)tuples[5];

                Long anchor = cid;
                IOrgUnitInterface org = new OrgUnitImpl();
                ((OrgUnitImpl)org).setId(oid);
                org.setName(oName);
                org.setDisplayName(oDispName);
                org.setDescription(oDesc);
                ((OrgUnitImpl)org).setType(orgType);

                Set<IOrgUnitInterface> orgList = contactOrgMap.get(anchor);
                if (orgList == null) {
                    orgList = new HashSet<IOrgUnitInterface>();
                    orgList.add(org);
                    contactOrgMap.put(cid, orgList);
                }
                else {
                    orgList.add(org);
                    contactOrgMap.put(cid, orgList);
                }
            }
        }
        catch (FlexnetBaseException fx) {
            throw new OperationsException(fx);
        }
        return contactOrgMap;
    }
    
    class ProcessUserListResponse{
    	private Map<Long, Map<Long, Set<IRole>>> userToOrgToRoleRelationShip =  new HashMap<Long, Map<Long, Set<IRole>>>();
    	private Map<Long, Map<Long, Date>> expiryDateMap = new HashMap<Long, Map<Long,Date>>();
    	
		public Map<Long, Map<Long, Set<IRole>>> getUserToOrgToRoleRelationShip() {
			return userToOrgToRoleRelationShip;
		}
		public void setUserToOrgToRoleRelationShip(Map<Long, Map<Long, Set<IRole>>> userToOrgToRoleRelationShip) {
			this.userToOrgToRoleRelationShip = userToOrgToRoleRelationShip;
		}
		public Map<Long, Map<Long, Date>> getExpiryDateMap() {
			return expiryDateMap;
		}
		public void setExpiryDateMap(Map<Long, Map<Long, Date>> expiryDateMap) {
			this.expiryDateMap = expiryDateMap;
		}		 	
    }

    private ProcessUserListResponse processUserList(List<Long> userIdList,
    		ProcessUserListResponse userDataMap) throws OperationsException {
        if (userIdList.size() == 0 || !populateOrgsAndRoles)
            return new ProcessUserListResponse();
        Map<Long, Map<Long, Set<IRole>>> userOrgMap = new LinkedHashMap<Long, Map<Long, Set<IRole>>>();
        ProcessUserListResponse obj = new ProcessUserListResponse();
        try {
            PersistenceService ps = PersistenceService.getInstance();
            IOperationsQuery qry = new IOperationsQuery();
            qry.setBatchSize(-1);
            String hQuery = ps.getQuery("UserService.getUserOrgRolesTuplesAndExpiryDate");
            Query hqlQryObj = ps.getTransaction().getHibernateSession().createQuery(hQuery);
            hqlQryObj.setParameterList("idList", userIdList);
            List results = hqlQryObj.list();

            /*
            uor.user.id, uor.orgUnit.id, orgUnit.name_, orgUnit.displayName_, orgUnit.description_,uor.role.name_,uor.role.id,uor.expiryDate
             */
            Iterator it = results.iterator();
            while (it.hasNext()) {
                Object[] tuples = (Object[])it.next();
                Long uid = (Long)tuples[0];
                Long oid = (Long)tuples[1];
                String oName = (String)tuples[2];
                String oDispName = (String)tuples[3];
                String oDesc = (String)tuples[4];

                String orgType = (String)tuples[5];

                Long roleId = (Long)tuples[6];
                String rName = (String)tuples[7];
                
                Date expiryDate = (Date)tuples[8];                
                if(obj!=null)
                {
                	Map<Long,Date> mapOfOrgsForUser=obj.getExpiryDateMap().get(uid);
                	if(mapOfOrgsForUser==null)
                	{
                		Map<Long,Date> orgForUserMap = new HashMap<Long,Date>();
                		orgForUserMap.put(oid, expiryDate);
                		obj.getExpiryDateMap().put(uid, orgForUserMap);
                		
                	}
                	else {
                		mapOfOrgsForUser.put(oid, expiryDate);
                	}
                }
               
                IRole role = new RoleImpl();
                ((RoleImpl)role).setId(roleId);
                ((RoleImpl)role).setName(rName);

                IOrgUnitInterface org = new OrgUnitImpl();
                ((OrgUnitImpl)org).setId(oid);

                org.setName(oName);
                org.setDisplayName(oDispName);
                org.setDescription(oDesc);
                ((OrgUnitImpl)org).setType(orgType);
                HashMap<Long,Long> hm=new HashMap<Long,Long>();
                hm.put(uid, oid);
                gOrgIdMap.put(hm, org);

                Map<Long, Set<IRole>> orgRoleMap = userOrgMap.get(uid);
                if (orgRoleMap == null) {
                    Set<IRole> rolesList = new HashSet<IRole>();
                    orgRoleMap = new LinkedHashMap<Long, Set<IRole>>();
                    rolesList.add(role);
                    orgRoleMap.put(oid, rolesList);
                    userOrgMap.put(uid, orgRoleMap);
                    obj.setUserToOrgToRoleRelationShip(userOrgMap);
                }
                else {
                    Set rolesList = orgRoleMap.get(oid);
                    if (rolesList == null)
                        rolesList = new HashSet<IRole>();
                    rolesList.add(role);
                    orgRoleMap.put(oid, rolesList);
                    userOrgMap.put(uid, orgRoleMap);
                    obj.setUserToOrgToRoleRelationShip(userOrgMap);
                }
            }
        }
        catch (FlexnetBaseException fx) {
            throw new OperationsException(fx);
        }
        return obj;
    }

    public List processPrimaryEntity(Iterator resultIterator) throws OperationsException {
        throw new OperationsException("NO SUCH method Implemented");
    }
}
