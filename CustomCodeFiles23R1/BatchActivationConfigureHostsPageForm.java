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

import java.util.HashSet;
import java.util.Set;

import com.flexnet.operations.publicapi.CustomHostId;
import com.flexnet.operations.publicapi.LicenseHostId;
import com.flexnet.operations.publicapi.NodeLockedHostId;

public class BatchActivationConfigureHostsPageForm extends BatchActivationBaseForm {
    private String firstLineItemID = "";
    private String selectedHostId = "";
    private String selectedHostName = "";
    private String isServerSelectedHost = "";
    private Set serverLicenseHosts = new HashSet();
    private Set nlLicenseHosts = new HashSet();
    private String[] toDeleteServerHosts;
    private String[] toDeleteNlHosts;

    private Set customLicenseHosts = new HashSet();
    private String[] toDeleteCustomHosts;

    public String getFirstLineItemID() {
        return firstLineItemID;
    }

    public void setFirstLineItemID(String str) {
        firstLineItemID = str;
    }

    public String[] getToDeleteServerHosts() {
        return toDeleteServerHosts;
    }

    public void setToDeleteServerHosts(String[] sel) {
        toDeleteServerHosts = sel;
    }

    public String[] getToDeleteNlHosts() {
        return toDeleteNlHosts;
    }

    public void setToDeleteNlHosts(String[] str) {
        toDeleteNlHosts = str;
    }

    public String getIsServerSelectedHost() {
        return isServerSelectedHost;
    }

    public void setIsServerSelectedHost(String str) {
        isServerSelectedHost = str;
    }

    public String getSelectedHostId() {
        return selectedHostId;
    }

    public void setSelectedHostId(String id) {
        selectedHostId = id;
    }

    public String getSelectedHostName() {
        return selectedHostName;
    }

    public void setSelectedHostName(String name) {
        selectedHostName = name;
    }

    public Set getServerLicenseHosts() {
        return serverLicenseHosts;
    }

    public void setServerLicenseHosts(Set hosts) {
        serverLicenseHosts = hosts;
    }

    public void addServerLicenseHosts(LicenseHostId host) {
        serverLicenseHosts.add(host);
    }

    public Set getNlLicenseHosts() {
        return nlLicenseHosts;
    }

    public void setNlLicenseHosts(Set l) {
        nlLicenseHosts = l;
    }

    public void addNlLicenseHost(NodeLockedHostId host) {
        nlLicenseHosts.add(host);
    }

    public void reset() {
        serverLicenseHosts = new HashSet();
        nlLicenseHosts = new HashSet();
        customLicenseHosts = new HashSet();
    }

    public String getShowServerHosts() {
        if (isNeedServerId())
            return "true";
        else
            return "false";
    }

    public void setShowServerHosts(String bool) {
        if (bool.equals("true"))
            setNeedServerId(true);
        else
            setNeedServerId(false);
    }

    public String getShowNodeLockedHosts() {
        if (isNeedNodeLockId())
            return "true";
        else
            return "false";
    }

    public void setShowNodeLockedHosts(String bool) {
        if (bool.equals("true"))
            setNeedNodeLockId(true);
        else
            setNeedNodeLockId(false);
    }

    public Set getCustomLicenseHosts() {
        return customLicenseHosts;
    }

    public void setCustomLicenseHosts(Set l) {
        customLicenseHosts = l;
    }

    public void addCustomLicenseHost(CustomHostId host) {
        customLicenseHosts.add(host);
    }

    public String[] getToDeleteCustomHosts() {
        return toDeleteCustomHosts;
    }

    public void setToDeleteCustomHosts(String[] str) {
        toDeleteCustomHosts = str;
    }

    public String getShowCustomHosts() {
        if (isNeedCustomHost())
            return "true";
        else
            return "false";
    }

    public void setShowCustomHosts(String bool) {
        if (bool.equals("true"))
            setNeedCustomHost(true);
        else
            setNeedCustomHost(false);
    }

}
