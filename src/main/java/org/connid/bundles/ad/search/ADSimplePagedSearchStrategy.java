/**
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 * Copyright 2011-2013 Tirasa. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License"). You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at https://oss.oracle.com/licenses/CDDL
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at https://oss.oracle.com/licenses/CDDL.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.connid.bundles.ad.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import org.connid.bundles.ldap.search.SearchResultsHandler;
import org.connid.bundles.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.common.logging.Log;

public class ADSimplePagedSearchStrategy extends SimplePagedSearchStrategy {

    private static final Log LOG =
            Log.getLog(ADSimplePagedSearchStrategy.class);

    private final int pageSize;

    static String searchControlsToString(SearchControls controls) {
        StringBuilder builder = new StringBuilder();
        builder.append("SearchControls: {returningAttributes=");
        String[] attrs = controls.getReturningAttributes();
        builder.append(attrs != null ? Arrays.asList(attrs) : "null");
        builder.append(", scope=");
        switch (controls.getSearchScope()) {
            case SearchControls.OBJECT_SCOPE:
                builder.append("OBJECT");
                break;
            case SearchControls.ONELEVEL_SCOPE:
                builder.append("ONELEVEL");
                break;
            case SearchControls.SUBTREE_SCOPE:
                builder.append("SUBTREE");
                break;
        }
        builder.append('}');
        return builder.toString();
    }

    public ADSimplePagedSearchStrategy(int pageSize) {
        super(pageSize);
        this.pageSize = pageSize;
    }

    @Override
    public void doSearch(
            final LdapContext initCtx,
            final List<String> baseDNs,
            final String query,
            final SearchControls searchControls,
            final SearchResultsHandler handler)
            throws IOException, NamingException {

        if (LOG.isOk()) {
            LOG.ok("Searching in {0} with filter {1} and {2}",
                    baseDNs, query, searchControlsToString(searchControls));
        }

        LdapContext ctx = initCtx.newInstance(null);
        try {
            Iterator<String> baseDNIter = baseDNs.iterator();
            boolean proceed = true;

            while (baseDNIter.hasNext() && proceed) {
                String baseDN = baseDNIter.next();
                byte[] cookie = null;
                do {
                    ctx.setRequestControls(
                            new Control[]{new PagedResultsControl(
                                pageSize, cookie, Control.CRITICAL)});
                    NamingEnumeration<SearchResult> results =
                            ctx.search(baseDN, query, searchControls);
                    try {
                        // hasMore call for referral resolution ... it fails with AD
                        // while (proceed && results.hasMore()) {
                        while (proceed && results.hasMoreElements()) {
                            proceed = handler.handle(baseDN, results.next());
                        }
                    } finally {
                        results.close();
                    }
                    cookie = getResponseCookie(ctx.getResponseControls());
                } while (cookie != null);
            }
        } finally {
            ctx.close();
        }
    }

    private byte[] getResponseCookie(Control[] controls) {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl pagedControl =
                            (PagedResultsResponseControl) control;
                    return pagedControl.getCookie();
                }
            }
        }
        return null;
    }
}
