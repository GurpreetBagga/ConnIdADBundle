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
package org.connid.bundles.ad.sync;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapContext;
import org.connid.bundles.ad.ADConfiguration;
import org.connid.bundles.ad.ADConnection;
import org.connid.bundles.ad.ADConnector;
import org.connid.bundles.ad.UserTest;
import org.connid.bundles.ad.util.DirSyncUtils;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

public class SyncUserTest extends UserTest {

    @Test
    public void sync() {
        // We need to have several operation in the right sequence in order
        // to verify synchronization ...

        // ----------------------------------
        // Handler specification
        // ----------------------------------

        final List<SyncDelta> updated = new ArrayList<SyncDelta>();
        final List<SyncDelta> deleted = new ArrayList<SyncDelta>();

        final SyncResultsHandler handler = new SyncResultsHandler() {

            @Override
            public boolean handle(final SyncDelta sd) {
                if (sd.getDeltaType() == SyncDeltaType.DELETE) {
                    return deleted.add(sd);
                } else {
                    return updated.add(sd);
                }
            }
        };
        // ----------------------------------

        // Ask just for sAMAccountName
        final OperationOptionsBuilder oob = new OperationOptionsBuilder();
        oob.setAttributesToGet(Arrays.asList(
                new String[]{"sAMAccountName", "givenName", "memberOf"}));

        SyncToken token = null;

        connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
        token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

        assertTrue(deleted.isEmpty());
        assertTrue(updated.isEmpty());

        final Map.Entry<String, String> ids11 = util.getEntryIDs("11");
        final Map.Entry<String, String> ids12 = util.getEntryIDs("12");

        Uid uid11 = null;
        Uid uid12 = null;

        try {
            // ----------------------------------
            // check sync with new user (token updated)
            // ----------------------------------
            // user added sync
            uid11 = connector.create(ObjectClass.ACCOUNT, util.getSimpleProfile(ids11), null);

            updated.clear();
            deleted.clear();

            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());

            // user creation and group modification
            assertEquals(3, updated.size());

            final ConnectorObject obj = updated.get(0).getObject();

            // chek for returned attributes
            assertEquals(5, updated.get(0).getObject().getAttributes().size());
            assertNotNull(obj.getAttributeByName("sAMAccountName"));
            assertNotNull(obj.getAttributeByName("givenName"));
            assertNotNull(obj.getAttributeByName("__NAME__"));
            assertNotNull(obj.getAttributeByName("__UID__"));
            assertNotNull(obj.getAttributeByName("memberOf"));
            assertEquals(ids11.getValue(), updated.get(0).getUid().getUidValue());

            updated.clear();
            deleted.clear();

            // check with updated token and without any modification
            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertTrue(updated.isEmpty());
            // ----------------------------------

            // ----------------------------------
            // check sync with user 'IN' group (token updated)
            // ----------------------------------
            // created a new user without memberships specification
            final ADConfiguration configuration = getSimpleConf(prop);
            configuration.setMemberships();

            if (LOG.isOk()) {
                LOG.ok("\n Configuration: {0}\n Filter: {1}",
                        configuration,
                        DirSyncUtils.createLdapUFilter(configuration));
            }

            final ADConnection connection = new ADConnection(configuration);
            final LdapContext ctx = connection.getInitialContext();

            final Attributes attrs = new BasicAttributes(true);
            attrs.put(new BasicAttribute("cn", ids12.getKey()));
            attrs.put(new BasicAttribute("sn", ids12.getKey()));
            attrs.put(new BasicAttribute("givenName", ids12.getKey()));
            attrs.put(new BasicAttribute("displayName", ids12.getKey()));
            attrs.put(new BasicAttribute("sAMAccountName", ids12.getValue()));
            attrs.put(new BasicAttribute("userPrincipalName", "test@test.org"));
            attrs.put(new BasicAttribute("userPassword", "password"));
            attrs.put(new BasicAttribute("objectClass", "top"));
            attrs.put(new BasicAttribute("objectClass", "person"));
            attrs.put(new BasicAttribute("objectClass", "organizationalPerson"));
            attrs.put(new BasicAttribute("objectClass", "user"));

            try {

                ctx.createSubcontext(
                        "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0], attrs);
                uid12 = new Uid(ids12.getValue());

            } catch (NamingException e) {
                LOG.error(e, "Error creating user {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertTrue(updated.isEmpty());

            ModificationItem[] mod =
                    new ModificationItem[]{new ModificationItem(
                DirContext.ADD_ATTRIBUTE,
                new BasicAttribute("member", 
                    "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0]))
            };

            try {
                ctx.modifyAttributes(conf.getMemberships()[0], mod);
            } catch (NamingException e) {
                LOG.error(e, "Error adding membership to {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertEquals(1, updated.size());

            mod =
                    new ModificationItem[]{new ModificationItem(
                DirContext.ADD_ATTRIBUTE,
                new BasicAttribute("member", 
                    "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0]))
            };

            try {
                ctx.modifyAttributes(conf.getMemberships()[1], mod);
            } catch (NamingException e) {
                LOG.error(e, "Error adding membership to {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertEquals(1, updated.size());
            // ----------------------------------

            // ----------------------------------
            // check sync with user 'OUT' group (token updated)
            // ----------------------------------
            mod =
                    new ModificationItem[]{
                new ModificationItem(
                DirContext.REMOVE_ATTRIBUTE,
                new BasicAttribute("member", 
                    "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0]))
            };

            try {
                ctx.modifyAttributes(conf.getMemberships()[0], mod);
            } catch (NamingException e) {
                LOG.error(e, "Error adding membership to {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            // sync user delete (member out is like a user delete)
            conf.setRetrieveDeletedUser(true);

            final ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();

            final APIConfiguration impl = TestHelpers.createTestConfiguration(ADConnector.class, conf);

            final ConnectorFacade newConnector = factory.newInstance(impl);

            newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = newConnector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertEquals(1, updated.size());

            assertNotNull(
                    updated.get(0).getObject().getAttributeByName("memberOf"));

            assertNotNull(
                    updated.get(0).getObject().getAttributeByName("memberOf").
                    getValue());

            assertEquals(1,
                    updated.get(0).getObject().getAttributeByName("memberOf").
                    getValue().size());

            // add user to a group not involved into the filter
            mod = new ModificationItem[]{
                new ModificationItem(
                DirContext.ADD_ATTRIBUTE,
                new BasicAttribute("member", 
                    "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0]))
            };

            try {
                ctx.modifyAttributes("CN=Cert Publishers,CN=Users," + conf.getBaseContextsToSynchronize()[0], mod);
            } catch (NamingException e) {
                LOG.error(e, "Error adding membership to {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = newConnector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertEquals(1, updated.size());
            assertNotNull(updated.get(0).getObject().getAttributeByName("memberOf"));
            assertNotNull(updated.get(0).getObject().getAttributeByName("memberOf").getValue());
            assertEquals(2, updated.get(0).getObject().getAttributeByName("memberOf").getValue().size());

            mod = new ModificationItem[]{
                new ModificationItem(
                DirContext.REMOVE_ATTRIBUTE,
                new BasicAttribute("member", 
                    "CN=" + ids12.getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0]))
            };

            try {
                ctx.modifyAttributes(conf.getMemberships()[1], mod);
            } catch (NamingException e) {
                LOG.error(e, "Error adding membership to {0}", ids12.getValue());
                assert (false);
            }

            updated.clear();
            deleted.clear();

            newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = newConnector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(updated.isEmpty());
            assertEquals(1, deleted.size());
            // ----------------------------------

            // ----------------------------------
            // check sync with updated user (token updated)
            // ----------------------------------
            // user modify sync
            uid11 = connector.update(
                    ObjectClass.ACCOUNT, uid11,
                    Collections.singleton(AttributeBuilder.build(
                    "givenName", Collections.singleton("changed"))),
                    null);

            updated.clear();
            deleted.clear();

            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertEquals(1, updated.size());

            updated.clear();
            deleted.clear();

            // check with updated token and without any modification
            connector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = connector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());
            assertTrue(updated.isEmpty());
            // ----------------------------------
        } finally {
            if (uid12 != null) {
                connector.delete(ObjectClass.ACCOUNT, uid12, null);
            }

            if (uid11 != null) {
                // user delete sync
                conf.setRetrieveDeletedUser(true);

                final ConnectorFacadeFactory factory =
                        ConnectorFacadeFactory.getInstance();

                final APIConfiguration impl =
                        TestHelpers.createTestConfiguration(
                        ADConnector.class, conf);

                final ConnectorFacade newConnector = factory.newInstance(impl);

                newConnector.delete(ObjectClass.ACCOUNT, uid11, null);

                updated.clear();
                deleted.clear();

                newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());

                assertFalse(deleted.isEmpty());
                assertTrue(deleted.size() <= 2);
                assertTrue(deleted.get(0).getUid().getUidValue().startsWith(util.getEntryIDs("1").getValue()));
            }
        }
    }

    @Test
    public void initialLoading() {
        // We need to have several operation in the right sequence in order
        // to verify synchronization ...

        // ----------------------------------
        // Handler specification
        // ----------------------------------

        final List<SyncDelta> updated = new ArrayList<SyncDelta>();
        final List<SyncDelta> deleted = new ArrayList<SyncDelta>();

        final SyncResultsHandler handler = new SyncResultsHandler() {

            @Override
            public boolean handle(final SyncDelta sd) {
                if (sd.getDeltaType() == SyncDeltaType.DELETE) {
                    return deleted.add(sd);
                } else {
                    return updated.add(sd);
                }
            }
        };
        // ----------------------------------

        // Ask just for sAMAccountName
        final OperationOptionsBuilder oob = new OperationOptionsBuilder();
        oob.setAttributesToGet(Arrays.asList(new String[]{"sAMAccountName", "givenName"}));

        SyncToken token = null;

        conf.setRetrieveDeletedUser(false);
        conf.setLoading(true);

        final ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        final APIConfiguration impl = TestHelpers.createTestConfiguration(ADConnector.class, conf);
        final ConnectorFacade newConnector = factory.newInstance(impl);

        newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
        token = newConnector.getLatestSyncToken(ObjectClass.ACCOUNT);

        assertTrue(deleted.isEmpty());
        assertTrue(updated.isEmpty());

        // ----------------------------------
        // check sync with new user (token updated)
        // ----------------------------------
        Map.Entry<String, String> ids13 = util.getEntryIDs("13");

        Uid uid13 = null;

        try {
            // user added sync
            uid13 = connector.create(ObjectClass.ACCOUNT, util.getSimpleProfile(ids13), null);

            updated.clear();
            deleted.clear();

            newConnector.sync(ObjectClass.ACCOUNT, token, handler, oob.build());
            token = newConnector.getLatestSyncToken(ObjectClass.ACCOUNT);

            assertTrue(deleted.isEmpty());

            assertEquals(1, updated.size());
        } finally {
            if (uid13 != null) {
                connector.delete(ObjectClass.ACCOUNT, uid13, null);
            }
        }
        // ----------------------------------
    }

    @Test
    public void verifyObjectGUID() {
        // Ask just for objectGUID
        final OperationOptionsBuilder oob = new OperationOptionsBuilder();
        oob.setAttributesToGet(Collections.singleton("objectGUID"));

        final ConnectorObject object =
                connector.getObject(ObjectClass.ACCOUNT, new Uid(util.getEntryIDs("4").getValue()), oob.build());

        assertNotNull(object);

        final Attribute objectGUID = object.getAttributeByName("objectGUID");
        assertNotNull(objectGUID);
        assertNotNull(objectGUID.getValue());
        assertEquals(1, objectGUID.getValue().size());

        final String guid = DirSyncUtils.getGuidAsString((byte[]) objectGUID.getValue().get(0));
        assertNotNull(guid);

        if (LOG.isOk()) {
            LOG.ok("ObjectGUID (String): {0}", guid);
        }
    }

    @Test
    public void verifyFilter() {
        // instatiate a new configuration to avoid collisions with sync test
        final ADConfiguration configuration = getSimpleConf(prop);

        final String DN = 
                "CN=" + util.getEntryIDs("5").getKey() + ",CN=Users," + configuration.getUserBaseContexts()[0];

        final ADConnection connection = new ADConnection(configuration);
        final LdapContext ctx = connection.getInitialContext();

        assertTrue(DirSyncUtils.verifyCustomFilter(ctx, DN, configuration));

        configuration.setAccountSearchFilter("(&(Objectclass=user)(cn=" + util.getEntryIDs("5").getKey() + "))");
        assertTrue(DirSyncUtils.verifyCustomFilter(ctx, DN, configuration));

        configuration.setAccountSearchFilter("(&(Objectclass=user)(cn=" + util.getEntryIDs("6").getKey() + "))");
        assertFalse(DirSyncUtils.verifyCustomFilter(ctx, DN, configuration));
    }
}
